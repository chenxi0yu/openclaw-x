package ai.openclaw.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers which download target may be handed to the system downloader.
 *
 * The protected behavior: a release's own APK asset queues unattended on both mirrors, while
 * any link that needs a human — a netdisk page and its access code — is never queued, and the
 * file that lands in Downloads is named after the asset, not after the signed redirect.
 */
class AppUpdateDownloadTest {
  @Test
  fun isDirectApkDownloadUrl_acceptsReleaseAssetsFromBothMirrors() {
    assertTrue(
      isDirectApkDownloadUrl(
        "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.26.1/openclawX-2026.10.26.1.apk",
      ),
    )
    assertTrue(
      isDirectApkDownloadUrl(
        "https://github.com/chenxi0yu/openclaw-x/releases/download/v2026.10.26/openclawX-2026.10.26.apk",
      ),
    )
    // Both hosts bounce to a token-signed CDN URL that keeps the filename in the path.
    assertTrue(
      isDirectApkDownloadUrl(
        "https://foruda.gitee.com/attach_file/1792989558678864318/openclawx-2026.10.26.1.apk?token=8b1e&ts=1793195894",
      ),
    )
  }

  @Test
  fun isDirectApkDownloadUrl_rejectsPagesAndPlainHttp() {
    assertFalse("a netdisk page needs its access code", isDirectApkDownloadUrl("https://wwbty.lanzouv.com/iUyhN4df4ini"))
    assertFalse(isDirectApkDownloadUrl("https://github.com/chenxi0yu/openclaw-x/releases/tag/v2026.10.26"))
    assertFalse("an unsigned http package is not an install path", isDirectApkDownloadUrl("http://mirror.example.com/openclawX.apk"))
    assertFalse(isDirectApkDownloadUrl(""))
  }

  @Test
  fun apkDownloadFileName_usesTheAssetNameAndDropsQueryAndFragment() {
    assertEquals(
      "openclawx-2026.10.26.1.apk",
      apkDownloadFileName("https://foruda.gitee.com/attach_file/1792989558678864318/openclawx-2026.10.26.1.apk?token=8b1e&attname=x.apk"),
    )
    assertEquals(
      "openclawX-2026.10.26.1.apk",
      apkDownloadFileName("https://gitee.com/x/releases/download/v2026.10.26.1/openclawX-2026.10.26.1.apk#sha=1"),
    )
  }

  @Test
  fun apkDownloadFileName_fallsBackWhenTheUrlCarriesNoUsableName() {
    assertEquals("openclaw-update.apk", apkDownloadFileName("https://example.com/download?id=7"))
    assertEquals("openclaw-update.apk", apkDownloadFileName("https://example.com/"))
  }

  @Test
  fun apkDownloadFileName_keepsOnlyCharactersTheDownloadsProviderAccepts() {
    assertEquals(
      "openclaw-2Fx-2026.10.28.apk",
      apkDownloadFileName("https://example.com/path/openclaw%2Fx@2026.10.28.apk"),
    )
  }
}
