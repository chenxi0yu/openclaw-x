package ai.openclaw.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the two release mirrors the update check depends on.
 *
 * The protected behavior: a release's own APK attachment is the only download target, so the
 * app can queue it on the system downloader; notes links are display text (the netdisk form the
 * fork retired needs interactive input), and the quota-free Atom feed behind shared NAT keeps
 * producing the same prompt.
 */
@RunWith(RobolectricTestRunner::class)
class AppUpdateCheckerTest {
  /** Mirrors github.com/<owner>/<repo>/releases.atom: newest entry first, body HTML-escaped. */
  private fun feed(
    newestTag: String,
    notesHtml: String,
  ) =
    """
    <?xml version="1.0" encoding="UTF-8"?>
    <feed xmlns="http://www.w3.org/2005/Atom">
      <id>tag:github.com,2008:https://github.com/chenxi0yu/openclaw-x/releases</id>
      <title>Release notes from openclaw-x</title>
      <updated>2026-10-04T10:09:12Z</updated>
      <link type="text/html" rel="alternate" href="https://github.com/chenxi0yu/openclaw-x/releases"/>
      <entry>
        <id>tag:github.com,2008:Repository/1397591894/$newestTag</id>
        <updated>2026-10-12T01:00:00Z</updated>
        <link rel="alternate" type="text/html" href="https://github.com/chenxi0yu/openclaw-x/releases/tag/$newestTag"/>
        <title>OpenClawX Android ${newestTag.removePrefix("v")}</title>
        <content type="html">$notesHtml</content>
      </entry>
      <entry>
        <id>tag:github.com,2008:Repository/1397591894/v2026.9.29</id>
        <updated>2026-09-29T01:00:00Z</updated>
        <link rel="alternate" type="text/html" href="https://github.com/chenxi0yu/openclaw-x/releases/tag/v2026.9.29"/>
        <title>Older release</title>
        <content type="html">&lt;p&gt;older-entry-marker&lt;/p&gt;</content>
      </entry>
    </feed>
    """.trimIndent()

  private val plainNotes = "&lt;p&gt;本次只修了文案&lt;/p&gt;"

  /** GitHub's `releases/expanded_assets/<tag>` fragment, written exactly as the host serves it. */
  private val assetFragment =
    """
    <div class="Box-body">
      <a href="/chenxi0yu/openclaw-x/releases/download/v2026.10.26.2/openclawX-2026.10.26.2-universal.apk" rel="nofollow">openclawX-universal.apk</a>
      <a href="/chenxi0yu/openclaw-x/releases/download/v2026.10.26.2/openclawX-2026.10.26.2-arm64.apk" rel="nofollow">openclawX-arm64.apk</a>
      <a href="/chenxi0yu/openclaw-x/archive/refs/tags/v2026.10.26.2.zip" rel="nofollow">Source code (zip)</a>
    </div>
    """.trimIndent()

  private val giteeReleaseWithApk =
    """
    {
      "tag_name": "v2026.10.24",
      "name": "OpenClaw-X 2026.10.24",
      "body": "<p>修复</p>\n\n**重点**\n\n- 字重\n\n[完整说明](https://gitee.com/xiaolaiddj/openclaw-x/compare/v2026.10.13...v2026.10.24)",
      "assets": [
        {
          "name": "openclawX-2026.10.24.apk",
          "browser_download_url": "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.24/openclawX-2026.10.24.apk"
        }
      ]
    }
    """.trimIndent()

  @Test
  fun parseGiteeRelease_usesTheAttachedApkAsTheDownloadTarget() {
    val release = parseGiteeRelease(giteeReleaseWithApk, AppUpdateSource.Gitee)

    assertEquals("v2026.10.24", release?.tag)
    assertEquals("2026.10.24", release?.version)
    assertEquals("OpenClaw-X 2026.10.24", release?.title)
    assertEquals(
      "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.24/openclawX-2026.10.24.apk",
      release?.downloadUrl,
    )
    assertEquals(
      "https://gitee.com/xiaolaiddj/openclaw-x/releases/tag/v2026.10.24",
      release?.pageUrl,
    )
    // The dialog hands this body to the chat Markdown renderer, so authored structure has to
    // survive the parse; flattening it to text was what made notes look broken.
    val notes = release!!.notes
    assertTrue("heading emphasis survives", notes.contains("**重点**"))
    assertTrue("list markers survive", notes.contains("- 字重"))
    assertTrue("link destinations survive", notes.contains("](https://gitee.com/"))
  }

  @Test
  fun parseGiteeRelease_picksArm64WhenSeveralApksAreAttached() {
    val json =
      """
      {
        "tag_name": "v2026.10.24",
        "name": "OpenClaw-X 2026.10.24",
        "assets": [
          {
            "name": "openclawX-2026.10.24-universal.apk",
            "browser_download_url": "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.24/universal.apk"
          },
          {
            "name": "openclawX-2026.10.24-arm64.apk",
            "browser_download_url": "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.24/arm64.apk"
          }
        ]
      }
      """.trimIndent()

    val release = parseGiteeRelease(json, AppUpdateSource.Gitee)

    assertEquals(
      "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.24/arm64.apk",
      release?.downloadUrl,
    )
  }

  @Test
  fun parseGiteeRelease_ignoresNotesLinksWhenNoApkIsAttached() {
    val json =
      """
      {
        "tag_name": "v2026.10.5",
        "name": "OpenClaw-X 2026.10.5",
        "body": "下载：https://wwbty.lanzouv.com/ivCVM4bf5uvc 密码: 9j9f",
        "assets": [
          {
            "name": "source.zip",
            "browser_download_url": "https://gitee.com/xiaolaiddj/openclaw-x/releases/download/v2026.10.5/source.zip"
          }
        ]
      }
      """.trimIndent()

    val release = parseGiteeRelease(json, AppUpdateSource.Gitee)

    // A netdisk share needs interactive input, so it is display text only: the dialog
    // routes to the release page instead of queueing a link the downloader cannot finish.
    assertNull(release?.downloadUrl)
    assertEquals(
      "https://gitee.com/xiaolaiddj/openclaw-x/releases/tag/v2026.10.5",
      release?.pageUrl,
    )
  }

  @Test
  fun parseGiteeRelease_withoutAssetsStillHasReleasePage() {
    val release =
      parseGiteeRelease(
        """{"tag_name":"v2026.10.24","name":"OpenClaw-X","body":"只改了文案","assets":[]}""",
        AppUpdateSource.Gitee,
      )

    assertNull(release?.downloadUrl)
    assertEquals(
      "https://gitee.com/xiaolaiddj/openclaw-x/releases/tag/v2026.10.24",
      release?.pageUrl,
    )
  }

  @Test
  fun parseGiteeRelease_rejectsErrorAndTaglessPayloads() {
    assertNull(parseGiteeRelease("""{"message":"404 Not Found"}""", AppUpdateSource.Gitee))
    assertNull(parseGiteeRelease("""{"name":"no tag"}""", AppUpdateSource.Gitee))
    assertNull(parseGiteeRelease("not json", AppUpdateSource.Gitee))
  }

  @Test
  fun parseNewestRelease_readsNewestEntryAndLeavesTheDownloadToTheAssetList() {
    val release = parseNewestRelease(feed("v2026.10.5", plainNotes), AppUpdateSource.GitHub)

    assertEquals("v2026.10.5", release?.tag)
    assertEquals("2026.10.5", release?.version)
    // The Atom body carries no asset list, so the package URL comes from the tag's fragment.
    assertNull(release?.downloadUrl)
    assertEquals(
      "https://github.com/chenxi0yu/openclaw-x/releases/tag/v2026.10.5",
      release?.pageUrl,
    )
    assertFalse("older entry must not win", release!!.notes.contains("older-entry-marker"))
  }

  @Test
  fun parseGitHubApkAssetUrl_prefersArm64Attachment() {
    assertEquals(
      "https://github.com/chenxi0yu/openclaw-x/releases/download/v2026.10.26.2/openclawX-2026.10.26.2-arm64.apk",
      parseGitHubApkAssetUrl(assetFragment),
    )
  }

  @Test
  fun parseGitHubApkAssetUrl_ignoresSourceArchivesAndCdnLinks() {
    // GitHub answers an asset-less tag with a full HTML page whose only hrefs are asset-host
    // CDN links; those must not become a download target.
    val page =
      """
      <link rel="search" href="https://github.githubassets.com/opensearch.xml">
      <a href="https://github-cloud.s3.amazonaws.com">uploads</a>
      <a href="/chenxi0yu/openclaw-x/archive/refs/tags/v2026.10.26.zip">Source code (zip)</a>
      """.trimIndent()

    assertNull(parseGitHubApkAssetUrl(page))
  }

  @Test
  fun releaseAssetListUrl_existsForGitHubOnly() {
    assertNull(AppUpdateSource.Gitee.releaseAssetListUrl("v2026.10.26"))
    assertEquals(
      "https://github.com/chenxi0yu/openclaw-x/releases/expanded_assets/v2026.10.26",
      AppUpdateSource.GitHub.releaseAssetListUrl("v2026.10.26"),
    )
  }

  @Test
  fun parseNewestRelease_decodesEscapedMarkupInsteadOfShowingIt() {
    val release =
      parseNewestRelease(
        feed("v2026.10.5", "&lt;h2&gt;修复闪退&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;玻璃表面&lt;/li&gt;&lt;/ul&gt;"),
        AppUpdateSource.GitHub,
      )

    assertEquals("修复闪退\n玻璃表面", release?.notes)
  }

  @Test
  fun parseNewestRelease_returnsNullWhenNoEntryCarriesATag() {
    val noEntries = feed("v2026.10.5", plainNotes).replace(Regex("<entry>[\\s\\S]*</entry>"), "")

    assertNull(parseNewestRelease(noEntries, AppUpdateSource.GitHub))
  }

  @Test
  fun appUpdateSource_unknownStoredValueFallsBackToGitee() {
    assertEquals(AppUpdateSource.Gitee, AppUpdateSource.fromStorage(null))
    assertEquals(AppUpdateSource.Gitee, AppUpdateSource.fromStorage("gitee"))
    assertEquals(AppUpdateSource.GitHub, AppUpdateSource.fromStorage(" GitHub "))
    assertEquals(AppUpdateSource.Gitee, AppUpdateSource.fromStorage("retired-mirror"))
  }

  @Test
  fun isNewerAppVersion_comparesIgnoringTagPrefixAndBuildSuffix() {
    val installed = "2026.10.4-debug"

    assertTrue(isNewerAppVersion("v2026.10.5", installed))
    assertFalse("same release must not prompt", isNewerAppVersion("v2026.10.4", installed))
    assertFalse("older release must not prompt", isNewerAppVersion("v2026.9.29", installed))
    assertFalse("deeper segment wins", isNewerAppVersion("2026.10", installed))
    assertTrue(isNewerAppVersion("2027.1.0", installed))
  }

  @Test
  fun isNewerAppVersion_nonNumericTagNeverPrompts() {
    // Guards against a release process that tags "latest" or "nightly" and puts every
    // install into an undismissible update loop.
    assertFalse(isNewerAppVersion("latest", "2026.10.4-debug"))
    assertFalse(isNewerAppVersion("release-publish/abc123", "2026.10.4-debug"))
  }

  @Test
  fun normalizeAppVersion_stripsPrefixBuildSuffixAndPadding() {
    assertEquals("2026.10.5", normalizeAppVersion("v2026.10.5"))
    assertEquals("2026.10.5", normalizeAppVersion("2026.10.5-debug"))
    assertEquals("2026.10.5", normalizeAppVersion("2026.10.5+build7"))
    assertEquals("2026.10.5", normalizeAppVersion("  v2026.10.5  "))
  }
}
