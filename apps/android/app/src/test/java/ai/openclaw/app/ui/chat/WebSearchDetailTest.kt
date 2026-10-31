package ai.openclaw.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchDetailTest {
  private val output =
    """
    {"kind":"results","provider":"serper","query":"ZCode 智谱 免费 5 天","count":2,"tookMs":1602,
      "results":[
        {"title":"\n<<<EXTERNAL_UNTRUSTED_CONTENT id=\"a\">>>\nSource: Web Search\n---\n连接模型与套餐 - ZCode\n<<<END_EXTERNAL_UNTRUSTED_CONTENT id=\"a\">>>",
         "url":"https://zcode.z.ai/cn/docs/configuration",
         "snippet":"\n<<<EXTERNAL_UNTRUSTED_CONTENT id=\"b\">>>\nSource: Web Search\n---\n新用户连接 BigModel 账号后，会自动获得\n<<<END_EXTERNAL_UNTRUSTED_CONTENT id=\"b\">>>"},
        {"title":"另一个结果","url":"https://example.com/page","snippet":"第二行摘要"}
      ]}
    """.trimIndent()

  @Test
  fun parsesEnvelopeAndStripsUntrustedWrappers() {
    val info = parseWebSearchInfo(output = output, arguments = null)!!

    assertEquals("ZCode 智谱 免费 5 天", info.query)
    assertEquals("serper", info.provider)
    assertEquals(2, info.count)
    assertEquals(1602L, info.tookMillis)
    assertEquals(2, info.results.size)
    assertEquals("连接模型与套餐 - ZCode", info.results[0].title)
    assertEquals("新用户连接 BigModel 账号后，会自动获得", info.results[0].snippet)
    assertEquals("https://zcode.z.ai/cn/docs/configuration", info.results[0].url)
    assertEquals("另一个结果", info.results[1].title)
    assertEquals("第二行摘要", info.results[1].snippet)
  }

  @Test
  fun fallsBackToArgumentsForTheQuery() {
    val info =
      parseWebSearchInfo(
        output = """{"kind":"results","provider":"serper","results":[]}""",
        arguments = """{"query":"fallback query"}""",
      )!!

    assertEquals("fallback query", info.query)
    assertTrue(info.results.isEmpty())
  }

  @Test
  fun keepsOnlyHttpResults() {
    val info =
      parseWebSearchInfo(
        output =
          """{"kind":"results","query":"q","results":[
             {"title":"ok","url":"https://example.com/a","snippet":"a"},
             {"title":"relative","url":"/local","snippet":"b"}
           ]}""",
        arguments = null,
      )!!

    assertEquals(1, info.results.size)
    assertEquals("https://example.com/a", info.results[0].url)
  }

  @Test
  fun returnsNullForOtherToolOutputs() {
    assertNull(parseWebSearchInfo(output = """{"kind":"other","query":"q"}""", arguments = null))
    assertNull(parseWebSearchInfo(output = "not json", arguments = null))
    assertNull(parseWebSearchInfo(output = null, arguments = null))
  }
}
