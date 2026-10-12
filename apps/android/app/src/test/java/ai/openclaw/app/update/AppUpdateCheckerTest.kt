package ai.openclaw.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Covers the release feed the update check depends on.
 *
 * The protected behavior is that a fork release published with the documented note
 * format — a Lanzou link and a password line — turns into an installable prompt on a
 * device whose egress IP cannot use GitHub's rate-limited REST API.
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
        <content type="html">&lt;p&gt;https://old.lanzouv.com/shouldNotWin&lt;/p&gt;</content>
      </entry>
    </feed>
    """.trimIndent()

  // The exact note format the fork's release process documents, as GitHub escapes it.
  private val lanzouNotes =
    "&lt;p&gt;https://wwbty.lanzouv.com/ivCVM4bf5uvc&lt;br&gt;密码:9j9f&lt;/p&gt;"

  @Test
  fun parseNewestRelease_readsNewestEntryAndLiftsLanzouLinkAndPassword() {
    val release = parseNewestRelease(feed("v2026.10.5", lanzouNotes), DEFAULT_APP_UPDATE_LINK_PATTERN)

    assertEquals("v2026.10.5", release?.tag)
    assertEquals("2026.10.5", release?.version)
    assertEquals("https://wwbty.lanzouv.com/ivCVM4bf5uvc", release?.downloadUrl)
    assertEquals("9j9f", release?.downloadPassword)
    assertFalse("older entry must not win", release!!.notes.contains("shouldNotWin"))
  }

  @Test
  fun parseNewestRelease_decodesEscapedMarkupInsteadOfShowingIt() {
    val release = parseNewestRelease(feed("v2026.10.5", "&lt;h2&gt;修复闪退&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;玻璃表面&lt;/li&gt;&lt;/ul&gt;"), DEFAULT_APP_UPDATE_LINK_PATTERN)

    assertEquals("修复闪退\n玻璃表面", release?.notes)
  }

  @Test
  fun parseNewestRelease_returnsNullWhenNoEntryCarriesATag() {
    val noEntries = feed("v2026.10.5", lanzouNotes).replace(Regex("<entry>[\\s\\S]*</entry>"), "")

    assertNull(parseNewestRelease(noEntries, DEFAULT_APP_UPDATE_LINK_PATTERN))
  }

  @Test
  fun extractDownloadLink_customPatternWithCaptureGroupYieldsTheGroup() {
    val notes = "下载：[蓝奏云](https://wwbty.lanzouv.com/ivCVM4bf5uvc) 密码: 9j9f"

    val markdownRule = Regex("""\[(?:[^\]]*)\]\((https?://[^)]+)\)""")
    val download = extractDownloadLink(notes, markdownRule.pattern)

    assertEquals("https://wwbty.lanzouv.com/ivCVM4bf5uvc", download?.url)
    assertEquals("9j9f", download?.password)
  }

  @Test
  fun extractDownloadLink_brokenPatternFallsBackToLanzouDefault() {
    val download = extractDownloadLink("见 https://wwbty.lanzouv.com/ivCVM4bf5uvc 密码:9j9f", "https?://[unclosed(")

    assertEquals("https://wwbty.lanzouv.com/ivCVM4bf5uvc", download?.url)
  }

  @Test
  fun extractDownloadLink_absentLinkReturnsNull() {
    assertNull(extractDownloadLink("本次只修了文案，没有安装包。", DEFAULT_APP_UPDATE_LINK_PATTERN))
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
  fun gitHubRepositorySlug_acceptsEveryDocumentedSpelling() {
    val expected = "chenxi0yu/openclaw-x"

    assertEquals(expected, gitHubRepositorySlug("https://github.com/chenxi0yu/openclaw-x"))
    assertEquals(expected, gitHubRepositorySlug("http://github.com/chenxi0yu/openclaw-x/"))
    assertEquals(expected, gitHubRepositorySlug("github.com/chenxi0yu/openclaw-x"))
    assertEquals(expected, gitHubRepositorySlug("chenxi0yu/openclaw-x"))
    assertEquals(expected, gitHubRepositorySlug("https://github.com/chenxi0yu/openclaw-x.git"))
    assertEquals(expected, gitHubRepositorySlug("  chenxi0yu / openclaw-x  "))
  }

  @Test
  fun gitHubRepositorySlug_rejectsNonRepositoryInput() {
    assertNull(gitHubRepositorySlug(""))
    assertNull(gitHubRepositorySlug("chenxi0yu"))
    assertNull(gitHubRepositorySlug("not a url at all"))
    // A user-editable address must never point the check at the loopback or a LAN host.
    assertNull(gitHubRepositorySlug("https://127.0.0.1/owner/repo"))
    assertNull(gitHubRepositorySlug("https://localhost/owner/repo"))
    assertNull(gitHubRepositorySlug("https://192.168.1.20/owner/repo"))
  }

  @Test
  fun normalizeAppVersion_stripsPrefixBuildSuffixAndPadding() {
    assertEquals("2026.10.5", normalizeAppVersion("v2026.10.5"))
    assertEquals("2026.10.5", normalizeAppVersion("2026.10.5-debug"))
    assertEquals("2026.10.5", normalizeAppVersion("2026.10.5+build7"))
    assertEquals("2026.10.5", normalizeAppVersion("  v2026.10.5  "))
  }
}
