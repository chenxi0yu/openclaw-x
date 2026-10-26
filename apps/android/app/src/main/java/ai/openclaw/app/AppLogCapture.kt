package ai.openclaw.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.content.FileProvider
import ai.openclaw.app.i18n.nativeString
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** Caps the shared dump to its tail; a huge ring buffer must not bloat the payload. */
internal const val APP_LOG_SNAPSHOT_MAX_LINES = 4000

/** How much of the ring buffer a snapshot should carry. */
internal enum class AppLogRange(val title: String) {
  LastHour("最近一小时"),
  Today("今日日志"),
  Full("完整日志"),
}

/** `logcat -v time` prefix: `MM-DD HH:MM:SS.mmm` (no year). */
private val APP_LOG_TIME_PREFIX = Regex("""^(\d{2})-(\d{2}) (\d{2}):(\d{2}):(\d{2})\.(\d{3})""")

/**
 * Keeps only rows inside [range] by parsing the `-v time` prefix.
 *
 * The prefix carries no year, so the current year is assumed and a December
 * stamp seen just after New Year is rolled back one year. Continuation rows
 * without a stamp (crash banners, wrapped stack lines) inherit the last
 * timestamped row's verdict instead of being dropped.
 */
internal fun filterAppLogLines(
  lines: List<String>,
  range: AppLogRange,
  now: LocalDateTime = LocalDateTime.now(),
): List<String> {
  if (range == AppLogRange.Full) return lines
  val cutoff = now.minusHours(1)
  var keepUnstamped = true
  return lines.filter { line ->
    val match = APP_LOG_TIME_PREFIX.find(line)
    if (match == null) {
      val banner = line.startsWith("--------- beginning")
      keepUnstamped || banner
    } else {
      val (month, day, hour, minute, second, millis) = match.destructured
      var stamp =
        LocalDateTime.of(
          now.year,
          month.toInt(),
          day.toInt(),
          hour.toInt(),
          minute.toInt(),
          second.toInt(),
          millis.toInt() * 1_000_000,
        )
      if (stamp.isAfter(now.plusDays(1))) stamp = stamp.minusYears(1)
      keepUnstamped =
        when (range) {
          AppLogRange.Today -> stamp.toLocalDate() == now.toLocalDate()
          else -> !stamp.isBefore(cutoff)
        }
      keepUnstamped
    }
  }
}

/** Trims the raw logcat dump to its last [maxLines] lines. */
internal fun trimAppLogTail(
  lines: List<String>,
  maxLines: Int = APP_LOG_SNAPSHOT_MAX_LINES,
): List<String> = if (lines.size <= maxLines) lines else lines.takeLast(maxLines)

/**
 * Builds the shareable document: a small provenance header (version, commit,
 * device) followed by the log tail, so a shared file is analysable on its own.
 */
internal fun appLogSnapshotDocument(
  versionName: String,
  versionCode: Int,
  gitCommit: String,
  buildTimestamp: String,
  deviceModel: String,
  androidRelease: String,
  androidSdk: Int,
  capturedAt: Instant,
  lines: List<String>,
  rangeTitle: String = "",
): String = buildString {
  appendLine("OpenClaw-X log snapshot")
  appendLine("version: $versionName ($versionCode)")
  appendLine("commit: $gitCommit")
  appendLine("built: $buildTimestamp")
  appendLine("device: $deviceModel, Android $androidRelease (SDK $androidSdk)")
  appendLine("captured: ${DateTimeFormatter.ISO_INSTANT.format(capturedAt)}")
  if (rangeTitle.isNotBlank()) appendLine("range: $rangeTitle")
  appendLine()
  appendLine(trimAppLogTail(lines).joinToString("\n"))
}

/**
 * Dumps the app's own logcat (main + crash buffers) into a file under
 * `cache/exports/` — the cache path the app's FileProvider already exposes.
 * Untrusted apps can only read their own UID's logs, so no permission is
 * needed, and the crash buffer carries the FATAL EXCEPTION of past crashes.
 */
internal fun captureAppLogSnapshot(
  context: Context,
  range: AppLogRange = AppLogRange.Full,
): File? =
  try {
    val process = ProcessBuilder("logcat", "-d", "-v", "time", "-b", "main", "-b", "crash").start()
    val output = process.inputStream.bufferedReader().readText()
    val finished = process.waitFor(5, TimeUnit.SECONDS)
    if (!finished) process.destroy()
    if (output.isBlank()) {
      null
    } else {
      val lines = filterAppLogLines(output.lineSequence().toList(), range)
      val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC).format(Instant.now())
      val document =
        appLogSnapshotDocument(
          versionName = BuildConfig.VERSION_NAME,
          versionCode = BuildConfig.VERSION_CODE,
          gitCommit = BuildConfig.GIT_COMMIT,
          buildTimestamp = BuildConfig.BUILD_TIMESTAMP,
          deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
          androidRelease = Build.VERSION.RELEASE,
          androidSdk = Build.VERSION.SDK_INT,
          capturedAt = Instant.now(),
          lines = lines,
          rangeTitle = range.title,
        )
      val dir = File(context.cacheDir, "exports").apply { mkdirs() }
      File(dir, "openclaw-x-log-$stamp.txt").apply { writeText(document) }
    }
  } catch (_: IOException) {
    null
  } catch (_: InterruptedException) {
    null
  }

/** Hands the snapshot to the system share sheet as text/plain. */
internal fun shareAppLogSnapshot(
  context: Context,
  file: File,
) {
  val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
  val send =
    Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_STREAM, uri)
      .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
  val chooser = Intent.createChooser(send, nativeString("分享日志"))
  if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(chooser) }
    .onFailure {
      Toast.makeText(context, nativeString("No app can share this message"), Toast.LENGTH_SHORT).show()
    }
}
