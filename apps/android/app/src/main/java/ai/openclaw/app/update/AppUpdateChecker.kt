package ai.openclaw.app.update

import ai.openclaw.app.ui.image.executeCancellable
import ai.openclaw.app.ui.image.isPubliclyRoutableHost
import ai.openclaw.app.ui.image.safePublicHttpClient
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Matches a Lanzou Cloud share link, the default distribution channel for this
 * fork's Android builds. Users who publish elsewhere replace it in Settings.
 */
internal const val DEFAULT_APP_UPDATE_LINK_PATTERN = "https?://[A-Za-z0-9.-]*lanzou[A-Za-z0-9.-]*/[A-Za-z0-9_-]+"

/** Fork release repository offered as the out-of-box update source. */
internal const val DEFAULT_APP_UPDATE_REPOSITORY = "https://github.com/chenxi0yu/openclaw-x"

private const val UPDATE_CHECK_TIMEOUT_MILLIS = 12_000L
private const val UPDATE_CHECK_MAX_BODY_BYTES = 1024 * 1024

// The public Atom feed is used instead of api.github.com deliberately: the REST API is
// rate-limited per source IP and returns 403 for unauthenticated clients behind shared
// or carrier NAT egress, which would make launch checks fail for most operators. The
// feed is quota-free and still carries the release title and full notes body.
private const val GITHUB_RELEASES_FEED = "https://github.com/%s/releases.atom"

// Release notes pair the link with a share password under any of these labels; the
// update dialog copies the password to the clipboard so the browser step is one paste.
private val DOWNLOAD_PASSWORD_LABELS =
  Regex(
    "(?:密码|口令|访问码|提取码|验证码|password|passcode|code)\\s*[:：]?\\s*([A-Za-z0-9]{2,16})",
    RegexOption.IGNORE_CASE,
  )

/** A published release the app can offer to install. */
internal data class AppUpdateRelease(
  val version: String,
  val tag: String,
  val title: String,
  val notes: String,
  val downloadUrl: String?,
  val downloadPassword: String?,
)

/** Why a check produced no release. Closed set so the UI owns every message. */
internal enum class AppUpdateFailure {
  InvalidRepository,
  Network,
  UnexpectedResponse,
  NoReleases,
}

internal sealed interface AppUpdateCheck {
  data class Available(val release: AppUpdateRelease) : AppUpdateCheck

  data object UpToDate : AppUpdateCheck

  data class Failed(val reason: AppUpdateFailure) : AppUpdateCheck
}

/**
 * What the update surface should show right now.
 *
 * `result` keeps the latest explicit outcome so a manual check can say "already up
 * to date" or explain a failure; automatic checks only fill `checking` and leave
 * the previous outcome alone.
 */
internal data class AppUpdateUiState(
  val checking: Boolean = false,
  val result: AppUpdateCheck? = null,
)

/**
 * Resolves any accepted repository spelling to GitHub's `owner/repo` slug.
 *
 * Accepts a full URL, a bare `github.com/owner/repo`, or `owner/repo`, with or
 * without a `.git` suffix. Returns null when the input cannot name a repository or
 * names a literal non-public host. The address is user-editable and the shared
 * client already refuses DNS answers inside private ranges, so this is the early
 * rejection that turns a bad address into a settings error instead of a timeout.
 */
internal fun gitHubRepositorySlug(input: String): String? {
  val trimmed = input.trim().removeSuffix("/").removeSuffix(".git").removeSuffix("/")
  if (trimmed.isEmpty()) return null

  val segments =
    if (trimmed.contains("://") || trimmed.startsWith("github.com/")) {
      val normalized = if (trimmed.contains("://")) trimmed else "https://$trimmed"
      val url = normalized.toHttpUrlOrNull() ?: return null
      if (!isPubliclyRoutableHost(url)) return null
      url.pathSegments
    } else {
      trimmed.split("/")
    }

  val meaningful = segments.map { it.trim() }.filter { it.isNotEmpty() }
  if (meaningful.size < 2) return null
  // Trailing pair so a leading host segment and a bare slug collapse to the same answer.
  return meaningful.takeLast(2).joinToString("/")
}

/** Transport outcome, kept apart from parsing so each failure names its own cause. */
private sealed interface ReleaseFeedFetch {
  data class Loaded(val xml: String) : ReleaseFeedFetch

  data object Unreachable : ReleaseFeedFetch

  data object NonePublished : ReleaseFeedFetch
}

/**
 * Fetches the newest published release and reports it only when it outranks the installed build.
 */
internal suspend fun checkForAppUpdate(
  repository: String,
  currentVersion: String,
  linkPattern: String = DEFAULT_APP_UPDATE_LINK_PATTERN,
): AppUpdateCheck {
  val slug = gitHubRepositorySlug(repository) ?: return AppUpdateCheck.Failed(AppUpdateFailure.InvalidRepository)

  return when (val fetched = fetchReleaseFeed(slug, currentVersion)) {
    ReleaseFeedFetch.Unreachable -> AppUpdateCheck.Failed(AppUpdateFailure.Network)
    ReleaseFeedFetch.NonePublished -> AppUpdateCheck.Failed(AppUpdateFailure.NoReleases)
    is ReleaseFeedFetch.Loaded -> {
      val release = parseNewestRelease(fetched.xml, linkPattern) ?: return AppUpdateCheck.Failed(AppUpdateFailure.UnexpectedResponse)
      if (isNewerAppVersion(release.version, currentVersion)) AppUpdateCheck.Available(release) else AppUpdateCheck.UpToDate
    }
  }
}

private suspend fun fetchReleaseFeed(
  slug: String,
  currentVersion: String,
): ReleaseFeedFetch {
  val request =
    Request
      .Builder()
      .url(GITHUB_RELEASES_FEED.format(slug))
      // GitHub serves no User-Agent requests a generic error page instead of the feed.
      .header("User-Agent", "OpenClaw-Android/$currentVersion (app-update-check)")
      .header("Accept", "application/atom+xml")
      .get()
      .build()

  return withContext(Dispatchers.IO) {
    val client =
      safePublicHttpClient
        .newBuilder()
        .callTimeout(UPDATE_CHECK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()
    val response = client.newCall(request).executeCancellable() ?: return@withContext ReleaseFeedFetch.Unreachable
    response.use { call ->
      when {
        // 404 is GitHub's answer for "no such repository", which reads as nothing published.
        call.code == 404 -> ReleaseFeedFetch.NonePublished
        !call.isSuccessful -> ReleaseFeedFetch.Unreachable
        call.body.contentLength() > UPDATE_CHECK_MAX_BODY_BYTES -> ReleaseFeedFetch.Unreachable
        else -> runCatching { call.body.string() }.fold({ ReleaseFeedFetch.Loaded(it) }, { ReleaseFeedFetch.Unreachable })
      }
    }
  }
}

/** Raw `<entry>` values as GitHub writes them, before link extraction. */
private data class FeedEntry(
  val tag: String,
  val title: String,
  val notesHtml: String,
)

/**
 * Reads the newest `<entry>` from an Atom release feed.
 *
 * GitHub orders entries newest-first and excludes drafts, so the first entry is the
 * current release. Returns null when the document parses but carries no usable entry
 * or no version-bearing tag, which the caller reports as an unexpected response.
 */
internal fun parseNewestRelease(
  xml: String,
  linkPattern: String,
): AppUpdateRelease? {
  val entry = readNewestEntry(xml) ?: return null
  val version = normalizeAppVersion(entry.tag)
  if (version.isEmpty()) return null
  val notes = plainTextFromHtml(entry.notesHtml)
  val download = extractDownloadLink(notes, linkPattern)
  return AppUpdateRelease(
    version = version,
    tag = entry.tag,
    title = entry.title.ifBlank { entry.tag },
    notes = notes,
    downloadUrl = download?.url,
    downloadPassword = download?.password,
  )
}

private fun readNewestEntry(xml: String): FeedEntry? =
  runCatching {
    val parser = Xml.newPullParser()
    parser.setInput(StringReader(xml))

    var insideEntry = false
    var href = ""
    var title = ""
    var content = ""
    var event = parser.eventType

    while (event != XmlPullParser.END_DOCUMENT) {
      if (event == XmlPullParser.START_TAG) {
        val name = parser.name
        if (!insideEntry) {
          // The feed-level <title>/<link> precede any entry, so only an <entry> starts collection.
          if (name == "entry") insideEntry = true
        } else {
          when (name) {
            "link" -> href = parser.getAttributeValue(null, "href").orEmpty()
            // nextText() consumes through this element's END_TAG; the trailing next() resumes at
            // the sibling after it, which is why entry-end detection reads END_TAG only.
            "title" -> title = parser.nextText().trim()
            "content" -> content = parser.nextText()
          }
        }
      } else if (event == XmlPullParser.END_TAG && parser.name == "entry" && insideEntry) {
        val tag = href.substringAfterLast("/tag/", "").ifBlank { href.substringAfterLast('/', "") }
        return@runCatching tag.takeIf { it.isNotBlank() }?.let { FeedEntry(tag = it, title = title, notesHtml = content) }
      }
      event = parser.next()
    }
    null
  }.getOrNull()

/**
 * Converts a release-notes HTML body into readable dialog text.
 *
 * The feed body arrives XML-escaped and `XmlPullParser` has already resolved those
 * entities by the time `nextText()` returns, so this only has to lay out and drop
 * markup — decoding entities again here would corrupt any `&` the author typed.
 */
internal fun plainTextFromHtml(html: String): String {
  if (html.isBlank()) return ""
  return html
    .replace(Regex("(?is)<br\\s*/?>"), "\n")
    .replace(Regex("(?is)</(p|div|li|ul|ol|h[1-6]|blockquote|pre|tr|table)>"), "\n")
    .replace(Regex("(?is)<[^>]+>"), "")
    .replace(Regex("[ \t]*\n[ \t]*"), "\n")
    .replace(Regex("\n{3,}"), "\n\n")
    .trim()
}

/** A download link lifted from release notes, with its share password when one is printed. */
internal data class AppUpdateDownload(
  val url: String,
  val password: String?,
)

/**
 * Pulls the download link and its share password out of the release notes.
 *
 * A pattern with a capture group yields that group, so a rule can match surrounding
 * Markdown and still hand back the bare URL. An unusable pattern falls back to the
 * Lanzou default rather than silently disabling updates.
 */
internal fun extractDownloadLink(
  notes: String,
  linkPattern: String,
): AppUpdateDownload? {
  if (notes.isBlank()) return null
  val regex =
    runCatching { Regex(linkPattern) }.getOrNull()?.takeIf { it.pattern.isNotBlank() }
      ?: Regex(DEFAULT_APP_UPDATE_LINK_PATTERN)

  val match = regex.find(notes) ?: return null
  val url = (match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value).trim()
  if (url.isEmpty()) return null
  return AppUpdateDownload(url = url, password = DOWNLOAD_PASSWORD_LABELS.find(notes)?.groupValues?.getOrNull(1))
}

/**
 * Reduces a version string to its comparable release number.
 *
 * Build metadata and pre-release suffixes are dropped, so the tag `v2026.10.5` and
 * the installed `2026.10.5-debug` compare on release numbers alone.
 */
internal fun normalizeAppVersion(raw: String): String {
  val trimmed = raw.trim().removePrefix("v").removePrefix("V")
  return trimmed.substringBefore('-').substringBefore('+').trim().trim('.')
}

private fun appVersionSegments(version: String): List<Int> =
  normalizeAppVersion(version).split('.').map { segment -> segment.toIntOrNull() ?: 0 }

/**
 * True when [candidate] is a strictly newer release than [current].
 *
 * A tag with no numeric content never wins, so a non-version tag on the repository
 * cannot put every install into a permanent update loop.
 */
internal fun isNewerAppVersion(
  candidate: String,
  current: String,
): Boolean {
  val left = appVersionSegments(candidate)
  if (left.all { it == 0 }) return false
  val right = appVersionSegments(current)
  val width = maxOf(left.size, right.size)
  for (index in 0 until width) {
    val newer = left.getOrElse(index) { 0 }
    val installed = right.getOrElse(index) { 0 }
    if (newer != installed) return newer > installed
  }
  return false
}
