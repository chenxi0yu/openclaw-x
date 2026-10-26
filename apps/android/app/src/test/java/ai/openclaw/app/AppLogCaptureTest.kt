package ai.openclaw.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime

class AppLogCaptureTest {
  @Test
  fun logTailKeepsShortDumpsVerbatim() {
    val lines = listOf("line-1", "line-2")
    assertEquals(lines, trimAppLogTail(lines, maxLines = 100))
  }

  @Test
  fun logTailTrimsToTheLastLinesOnly() {
    val lines = (1..10).map { "line-$it" }
    assertEquals(listOf("line-9", "line-10"), trimAppLogTail(lines, maxLines = 2))
  }

  @Test
  fun snapshotDocumentCarriesProvenanceHeaderBeforeTheLog() {
    val document =
      appLogSnapshotDocument(
        versionName = "2026.10.13",
        versionCode = 2026101300,
        gitCommit = "abcdef0123456789abcdef0123456789abcdef01",
        buildTimestamp = "2026-10-21T09:00:00.000Z",
        deviceModel = "Xiaomi 24 Pro",
        androidRelease = "16",
        androidSdk = 36,
        capturedAt = Instant.parse("2026-10-22T09:30:00Z"),
        lines = listOf("10-22 09:29:59.000 E AndroidRuntime: FATAL EXCEPTION: main"),
      )

    assertTrue(document.startsWith("OpenClaw-X log snapshot"))
    assertTrue(document.contains("version: 2026.10.13 (2026101300)"))
    assertTrue(document.contains("commit: abcdef0123456789abcdef0123456789abcdef01"))
    assertTrue(document.contains("device: Xiaomi 24 Pro, Android 16 (SDK 36)"))
    assertTrue(document.contains("captured: 2026-10-22T09:30:00Z"))
    assertTrue(document.contains("FATAL EXCEPTION: main"))
  }

  @Test
  fun lastHourKeepsOnlyRecentTimestampedRowsAndTheirContinuations() {
    val now = LocalDateTime.of(2026, 10, 23, 12, 0, 0)
    val lines =
      listOf(
        "--------- beginning of main",
        "10-23 10:30:00.000  100  100 I Claw: too old",
        "10-23 11:30:00.000  100  100 I Claw: just inside",
        "    at com.example.Continuation",
        "10-23 11:59:00.000  100  100 E AndroidRuntime: FATAL EXCEPTION: main",
      )

    assertEquals(
      listOf(
        "--------- beginning of main",
        "10-23 11:30:00.000  100  100 I Claw: just inside",
        "    at com.example.Continuation",
        "10-23 11:59:00.000  100  100 E AndroidRuntime: FATAL EXCEPTION: main",
      ),
      filterAppLogLines(lines, AppLogRange.LastHour, now),
    )
  }

  @Test
  fun todayKeepsOnlyTheCurrentCalendarDay() {
    val now = LocalDateTime.of(2026, 10, 23, 0, 30, 0)
    val lines =
      listOf(
        "10-22 23:59:59.000  100  100 I Claw: yesterday",
        "10-23 00:00:00.500  100  100 I Claw: today",
      )

    assertEquals(
      listOf("10-23 00:00:00.500  100  100 I Claw: today"),
      filterAppLogLines(lines, AppLogRange.Today, now),
    )
  }

  @Test
  fun fullRangeLeavesTheDumpUntouched() {
    val lines = listOf("10-22 10:00:00.000  1  1 I Tag: old", "not a stamped line")
    assertEquals(lines, filterAppLogLines(lines, AppLogRange.Full))
  }

  @Test
  fun snapshotDocumentRecordsTheChosenRange() {
    val document =
      appLogSnapshotDocument(
        versionName = "dev",
        versionCode = 1,
        gitCommit = "unknown",
        buildTimestamp = "unknown",
        deviceModel = "device",
        androidRelease = "16",
        androidSdk = 36,
        capturedAt = Instant.EPOCH,
        lines = listOf("line"),
        rangeTitle = AppLogRange.Today.title,
      )

    assertTrue(document.contains("range: 今日日志"))
  }

  @Test
  fun snapshotDocumentTrimsOversizedDumpsToTheTail() {
    val total = APP_LOG_SNAPSHOT_MAX_LINES + 5
    val document =
      appLogSnapshotDocument(
        versionName = "dev",
        versionCode = 1,
        gitCommit = "unknown",
        buildTimestamp = "unknown",
        deviceModel = "device",
        androidRelease = "16",
        androidSdk = 36,
        capturedAt = Instant.EPOCH,
        lines = (1..total).map { "line-$it" },
      )

    assertTrue(document.contains("line-$total"))
    assertFalse(document.contains("line-1\n"))
    assertFalse(document.contains("line-4\n"))
  }
}
