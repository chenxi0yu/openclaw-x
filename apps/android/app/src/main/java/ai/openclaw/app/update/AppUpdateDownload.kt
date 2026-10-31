package ai.openclaw.app.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
private const val FALLBACK_APK_FILE_NAME = "openclaw-update.apk"

/**
 * True when the URL serves the package itself instead of a page that has to be visited.
 *
 * Both mirrors hand out release assets as `…/releases/download/<tag>/<name>.apk` and redirect
 * to a signed CDN URL that keeps the filename, so the system downloader can follow them
 * unattended. Anything else needs interactive input and stays a browser step.
 */
internal fun isDirectApkDownloadUrl(url: String): Boolean =
  url.startsWith("https://", ignoreCase = true) &&
    url.substringBefore('#').substringBefore('?').endsWith(".apk", ignoreCase = true)

/** The asset's own filename, kept to characters the Downloads provider accepts. */
internal fun apkDownloadFileName(url: String): String =
  url
    .substringBefore('#')
    .substringBefore('?')
    .substringAfterLast('/', "")
    .takeIf { it.endsWith(".apk", ignoreCase = true) }
    ?.map { char -> if (char.isLetterOrDigit() || char in ".-_") char else '-' }
    ?.joinToString("")
    ?.takeIf(String::isNotBlank)
    ?: FALLBACK_APK_FILE_NAME

/**
 * Queues [url] on the system downloader, which owns progress, retry, and the notification that
 * opens the installer.
 *
 * Returns false when the device has no downloader or rejected the request, so the caller keeps
 * a route to the release page instead of ending the tap on nothing.
 */
internal fun enqueueApkDownload(
  context: Context,
  url: String,
  version: String,
): Boolean {
  val manager = context.getSystemService(DownloadManager::class.java) ?: return false
  val fileName = apkDownloadFileName(url)
  val request =
    DownloadManager
      .Request(Uri.parse(url))
      .apply {
        setMimeType(APK_MIME_TYPE)
        setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        setTitle(fileName)
        setDescription("OpenClawX $version")
        // Downloads is the one public directory the system downloader can write without a
        // storage permission, and its completed notification is what launches the installer.
        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
      }
  return runCatching { manager.enqueue(request) }.isSuccess
}
