package ai.openclaw.app.update

import ai.openclaw.app.ui.image.executeCancellable
import ai.openclaw.app.ui.image.safePublicHttpClient
import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.util.concurrent.TimeUnit

/**
 * Release mirror the app checks for updates.
 *
 * Gitee is the domestic default: its release API answers without a token and attaches the
 * APK, so the app can queue the package on the system downloader and never leave for a
 * netdisk. GitHub stays available as the upstream-facing mirror.
 */
internal enum class AppUpdateSource(
  val displayName: String,
  val repository: String,
) {
  Gitee(displayName = "Gitee", repository = "xiaolaiddj/openclaw-x"),
  GitHub(displayName = "GitHub", repository = "chenxi0yu/openclaw-x"),
  ;

  fun releasePageUrl(tag: String): String =
    when (this) {
      Gitee -> "https://gitee.com/$repository/releases/tag/$tag"
      GitHub -> "https://github.com/$repository/releases/tag/$tag"
    }

  /**
   * GitHub's asset list for a tag: an unauthenticated HTML fragment with no request quota,
   * same as the Atom feed. Null for Gitee, whose JSON API already carries the assets inline.
   */
  fun releaseAssetListUrl(tag: String): String? =
    when (this) {
      Gitee -> null
      GitHub -> "https://github.com/$repository/releases/expanded_assets/$tag"
    }

  companion object {
    /** Unknown or retired stored values fall back to the domestic mirror. */
    fun fromStorage(raw: String?): AppUpdateSource =
      entries.firstOrNull { it.name.equals(raw?.trim(), ignoreCase = true) } ?: Gitee
  }
}

private const val UPDATE_CHECK_TIMEOUT_MILLIS = 12_000L
private const val UPDATE_CHECK_MAX_BODY_BYTES = 1024 * 1024

// The public Atom feed is used instead of api.github.com deliberately: the REST API is
// rate-limited per source IP and returns 403 for unauthenticated clients behind shared
// or carrier NAT egress, which would make launch checks fail for most operators. The
// feed is quota-free and still carries the release title and full notes body.
private const val GITHUB_RELEASES_FEED = "https://github.com/%s/releases.atom"

// Gitee's public OpenAPI is token-free for reads and returns the release's attached
// assets, which is what makes a direct APK download possible without a netdisk.
private const val GITEE_LATEST_RELEASE_API = "https://gitee.com/api/v5/repos/%s/releases/latest"

// GitHub's asset fragment is quota-free like the feed and is the only token-free way to learn
// a release's attachment names: the Atom body carries no asset list. Anchoring on
// `/releases/download/` also ignores the CDN `<link>`s of the full page GitHub serves back
// when a tag has no assets, which carry no filename at all.
private val githubApkAssetHref = Regex("href=\"(/[^\"]*?/releases/download/[^\"]+?\\.apk)\"", RegexOption.IGNORE_CASE)

/** A published release the app can offer to install. */
internal data class AppUpdateRelease(
  val version: String,
  val tag: String,
  val title: String,
  /**
   * Release body in the shape the dialog's Markdown renderer consumes: Gitee's JSON API
   * returns the authored Markdown and is passed through untouched, while GitHub's Atom body
   * is HTML that no token-free endpoint serves as Markdown, so it arrives normalized to text.
   */
  val notes: String,
  /** The release's own APK attachment; null when the release carries none. */
  val downloadUrl: String?,
  /** Always set: the release page is the fallback when no attachment was found. */
  val pageUrl: String,
)

/** Why a check produced no release. Closed set so the UI owns every message. */
internal enum class AppUpdateFailure {
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

/** Transport outcome, kept apart from parsing so each failure names its own cause. */
private sealed interface ReleaseBodyFetch {
  data class Loaded(val body: String) : ReleaseBodyFetch

  data object Unreachable : ReleaseBodyFetch

  data object NonePublished : ReleaseBodyFetch
}

private sealed interface ReleaseFetch {
  data class Loaded(val release: AppUpdateRelease) : ReleaseFetch

  data object NonePublished : ReleaseFetch

  data class Failed(val reason: AppUpdateFailure) : ReleaseFetch
}

/**
 * Fetches the newest published release from [source] and reports it only when it
 * outranks the installed build.
 */
internal suspend fun checkForAppUpdate(
  source: AppUpdateSource,
  currentVersion: String,
): AppUpdateCheck {
  val fetched =
    when (source) {
      AppUpdateSource.Gitee -> fetchGiteeRelease(source, currentVersion)
      AppUpdateSource.GitHub -> fetchGitHubRelease(source, currentVersion)
    }
  return when (fetched) {
    is ReleaseFetch.Loaded ->
      if (isNewerAppVersion(fetched.release.version, currentVersion)) {
        AppUpdateCheck.Available(fetched.release)
      } else {
        AppUpdateCheck.UpToDate
      }

    ReleaseFetch.NonePublished -> AppUpdateCheck.Failed(AppUpdateFailure.NoReleases)
    is ReleaseFetch.Failed -> AppUpdateCheck.Failed(fetched.reason)
  }
}

private suspend fun fetchGiteeRelease(
  source: AppUpdateSource,
  currentVersion: String,
): ReleaseFetch {
  val url = GITEE_LATEST_RELEASE_API.format(source.repository)
  return when (val fetched = fetchReleaseBody(url, currentVersion, accept = "application/json")) {
    is ReleaseBodyFetch.Loaded ->
      parseGiteeRelease(fetched.body, source)?.let(ReleaseFetch::Loaded)
        ?: ReleaseFetch.Failed(AppUpdateFailure.UnexpectedResponse)

    ReleaseBodyFetch.NonePublished -> ReleaseFetch.NonePublished
    ReleaseBodyFetch.Unreachable -> ReleaseFetch.Failed(AppUpdateFailure.Network)
  }
}

private suspend fun fetchGitHubRelease(
  source: AppUpdateSource,
  currentVersion: String,
): ReleaseFetch {
  val url = GITHUB_RELEASES_FEED.format(source.repository)
  return when (val fetched = fetchReleaseBody(url, currentVersion, accept = "application/atom+xml")) {
    is ReleaseBodyFetch.Loaded ->
      parseNewestRelease(fetched.body, source)
        ?.let { release -> ReleaseFetch.Loaded(release.withAttachedApkDownload(source, currentVersion)) }
        ?: ReleaseFetch.Failed(AppUpdateFailure.UnexpectedResponse)

    ReleaseBodyFetch.NonePublished -> ReleaseFetch.NonePublished
    ReleaseBodyFetch.Unreachable -> ReleaseFetch.Failed(AppUpdateFailure.Network)
  }
}

/**
 * Resolves the newest release's APK attachment so the download step can skip the browser.
 *
 * The Atom feed carries no asset list, so the tag's asset fragment is fetched separately.
 * Best-effort by design: a tag with no attachment, or a host that stops serving the fragment,
 * leaves the release on its page URL instead of failing the update check.
 */
private suspend fun AppUpdateRelease.withAttachedApkDownload(
  source: AppUpdateSource,
  currentVersion: String,
): AppUpdateRelease {
  val assetListUrl = source.releaseAssetListUrl(tag) ?: return this
  val body =
    (fetchReleaseBody(assetListUrl, currentVersion, accept = "text/html") as? ReleaseBodyFetch.Loaded)?.body
      ?: return this
  return copy(downloadUrl = parseGitHubApkAssetUrl(body))
}

/**
 * Picks the release attachment to install out of GitHub's asset fragment, preferring arm64.
 *
 * Null when the fragment carries no `.apk` href, which is how a notes-only release and the
 * full-page fallback GitHub serves for an asset-less tag both read.
 */
internal fun parseGitHubApkAssetUrl(html: String): String? {
  val hrefs = githubApkAssetHref.findAll(html).map { match -> match.groupValues[1] }.toList()
  if (hrefs.isEmpty()) return null
  val preferred = hrefs.firstOrNull { it.contains("arm64", ignoreCase = true) } ?: hrefs.first()
  return "https://github.com$preferred"
}

private suspend fun fetchReleaseBody(
  url: String,
  currentVersion: String,
  accept: String,
): ReleaseBodyFetch {
  val request =
    Request
      .Builder()
      .url(url)
      // Both hosts serve a generic error page to requests without a User-Agent.
      .header("User-Agent", "OpenClaw-X-Android/$currentVersion (app-update-check)")
      .header("Accept", accept)
      .get()
      .build()

  return withContext(Dispatchers.IO) {
    val client =
      safePublicHttpClient
        .newBuilder()
        .callTimeout(UPDATE_CHECK_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
        .build()
    val response = client.newCall(request).executeCancellable() ?: return@withContext ReleaseBodyFetch.Unreachable
    response.use { call ->
      when {
        // 404 is the API's answer for "no such repository or no published release",
        // which reads as nothing published.
        call.code == 404 -> ReleaseBodyFetch.NonePublished
        !call.isSuccessful -> ReleaseBodyFetch.Unreachable
        call.body.contentLength() > UPDATE_CHECK_MAX_BODY_BYTES -> ReleaseBodyFetch.Unreachable
        else ->
          runCatching { call.body.string() }.fold(
            { body ->
              if (body.length > UPDATE_CHECK_MAX_BODY_BYTES) {
                ReleaseBodyFetch.Unreachable
              } else {
                ReleaseBodyFetch.Loaded(body)
              }
            },
            { ReleaseBodyFetch.Unreachable },
          )
      }
    }
  }
}

private val appUpdateJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

@Serializable
private data class GiteeReleasePayload(
  @SerialName("tag_name") val tagName: String? = null,
  val name: String? = null,
  val body: String? = null,
  val assets: List<GiteeAssetPayload>? = null,
)

@Serializable
private data class GiteeAssetPayload(
  val name: String? = null,
  @SerialName("browser_download_url") val downloadUrl: String? = null,
)

/**
 * Reads a Gitee release into the same shape the Atom feed produces.
 *
 * Only the release's own APK attachment is a download target: notes links are display text,
 * since a share link needs interactive input the downloader cannot supply. The body stays
 * authored Markdown so the dialog renders it instead of showing markup.
 */
internal fun parseGiteeRelease(
  json: String,
  source: AppUpdateSource,
): AppUpdateRelease? {
  val payload =
    runCatching { appUpdateJson.decodeFromString<GiteeReleasePayload>(json) }.getOrNull()
      ?: return null
  val tag = payload.tagName?.trim().orEmpty()
  val version = normalizeAppVersion(tag)
  if (version.isEmpty()) return null
  return AppUpdateRelease(
    version = version,
    tag = tag,
    title = payload.name?.trim().orEmpty().ifEmpty { tag },
    notes = payload.body.orEmpty(),
    downloadUrl = payload.assets.orEmpty().apkDownloadUrl(),
    pageUrl = source.releasePageUrl(tag),
  )
}

/** Picks the arm64 build when a release carries several APKs. */
private fun List<GiteeAssetPayload>.apkDownloadUrl(): String? {
  val apks =
    filter { asset ->
      asset.name?.endsWith(".apk", ignoreCase = true) == true &&
        asset.downloadUrl?.startsWith("https://") == true
    }
  val preferred =
    apks.firstOrNull { it.name.orEmpty().contains("arm64", ignoreCase = true) }
      ?: apks.firstOrNull()
  return preferred?.downloadUrl
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
 *
 * The feed has no asset list, so `downloadUrl` stays null here and is filled by the
 * tag's asset fragment fetch.
 */
internal fun parseNewestRelease(
  xml: String,
  source: AppUpdateSource,
): AppUpdateRelease? {
  val entry = readNewestEntry(xml) ?: return null
  val version = normalizeAppVersion(entry.tag)
  if (version.isEmpty()) return null
  return AppUpdateRelease(
    version = version,
    tag = entry.tag,
    title = entry.title.ifBlank { entry.tag },
    notes = plainTextFromHtml(entry.notesHtml),
    downloadUrl = null,
    pageUrl = source.releasePageUrl(entry.tag),
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
 * Converts GitHub's HTML release body into readable dialog text.
 *
 * The Atom body arrives XML-escaped and `XmlPullParser` has already resolved those
 * entities by the time `nextText()` returns, so this only has to lay out and drop
 * markup — decoding entities again here would corrupt any `&` the author typed.
 * No token-free GitHub endpoint returns the authored Markdown, so this mirror ships
 * normalized text while Gitee's JSON API feeds the renderer real Markdown.
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
