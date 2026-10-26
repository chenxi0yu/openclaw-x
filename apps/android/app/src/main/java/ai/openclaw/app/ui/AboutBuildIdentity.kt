package ai.openclaw.app.ui

import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure build-identity formatting for the HyperOS-style About screen. No
 * composables live here; the screen reads the values into its own rows.
 */
internal data class AboutBuildIdentity(
  val version: String,
  val commit: String,
  val fullCommit: String?,
  val built: String,
  val buildTimestamp: String?,
)

private val fullGitCommitPattern = Regex("^[a-f0-9]{40}$")

internal fun aboutBuildIdentity(
  versionName: String,
  versionCode: Int,
  gitCommit: String,
  buildTimestamp: String,
  locale: Locale,
  unknownLabel: String,
): AboutBuildIdentity {
  val normalizedCommit = gitCommit.trim().lowercase().takeIf(fullGitCommitPattern::matches)
  val normalizedBuildTimestamp =
    buildTimestamp.trim().takeIf { it.endsWith("Z") }?.takeIf { timestamp ->
      runCatching { Instant.parse(timestamp) }.isSuccess
    }
  val buildInstant = normalizedBuildTimestamp?.let(Instant::parse)
  val built =
    buildInstant?.let { instant ->
      DateFormat.getDateInstance(DateFormat.MEDIUM, locale).run {
        timeZone = TimeZone.getTimeZone("UTC")
        format(Date.from(instant))
      }
    } ?: unknownLabel

  return AboutBuildIdentity(
    version = "${versionName.trim()} ($versionCode)",
    commit = normalizedCommit?.take(12) ?: unknownLabel,
    fullCommit = normalizedCommit,
    built = built,
    buildTimestamp = normalizedBuildTimestamp,
  )
}

internal fun aboutCommitAccessibilityValue(
  fullCommit: String?,
  unknownLabel: String,
): String =
  fullCommit?.let { commit ->
    commit.toCharArray().joinToString(" ")
  } ?: unknownLabel
