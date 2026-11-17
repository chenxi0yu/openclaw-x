package ai.openclaw.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInlineLatexBareTest {
  private fun mathOf(text: String): List<String> =
    splitInlineLatex(text).filterIsInstance<InlineLatexSegment.Math>().map { it.latex }

  @Test
  fun `bare parenthesised formula is recovered`() {
    assertEquals(listOf("(e^x)'=e^x"), mathOf("特例：((e^x)'=e^x)。"))
  }

  @Test
  fun `bare token with tex commands is recovered`() {
    assertEquals(listOf("2^{3x}\\ln2\\cdot 3=3\\cdot 2^{3x}\\ln2"), mathOf("例如 (2^{3x}\\ln2\\cdot 3=3\\cdot 2^{3x}\\ln2)。"))
    assertTrue(mathOf("底和指数都是变量 → 取对数：x^x=e^{x\\ln x}").isNotEmpty())
  }

  @Test
  fun `ordinary prose and paths stay literal`() {
    assertEquals(emptyList<String>(), mathOf("C:\\Users\\me\\repo"))
    assertEquals(emptyList<String>(), mathOf("这是（注意）普通说明"))
    assertEquals(emptyList<String>(), mathOf("函数 foo_bar 与 snake_case 名字"))
    assertEquals(emptyList<String>(), mathOf("(see below)"))
  }

  @Test
  fun `delimited math still wins`() {
    assertEquals(listOf("a^x\\ln a"), mathOf("公式 $(a^x)' = a^x\\ln a$ 成立"))
  }
}
