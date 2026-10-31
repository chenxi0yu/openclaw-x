package ai.openclaw.app.ui.chat

import org.commonmark.node.FencedCodeBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatTextDiagramTest {
  private val cocoaDiagram = cocoaDiagramFixture

  @Test
  fun spacedUnicodeFlowchartIsRecognized() {
    assertTrue(looksLikeChatTextDiagram(cocoaDiagram))
  }

  @Test
  fun diagramLinesUseTerminalCellWidths() {
    assertEquals(
      listOf(41, 37, 46, 42, 60, 45, 52, 32, 52),
      chatDiagramLines(cocoaDiagram).map { it.cells },
    )
    assertEquals(13, chatDiagramCellWidth("可可果 → 取豆"))
  }

  @Test
  fun tabsAndCombiningMarksFollowTerminalColumns() {
    assertEquals(9, chatDiagramCellWidth("a\tb"))
    assertEquals(9, chatDiagramCellWidth("abcd\te"))
    assertEquals(1, chatDiagramCellWidth("e\u0301"))
  }

  @Test
  fun proseAndCodeStayInTheCodeRenderer() {
    assertFalse(looksLikeChatTextDiagram("第一种：生豆 → 烘焙 → 研磨\n第二种：可可脂 → 巧克力\n第三种：可可粉 → 冲饮"))
    assertFalse(looksLikeChatTextDiagram("val x = 1\nval y = 2\nval z = 3"))
    assertFalse(looksLikeChatTextDiagram("--- a/file.kt\n+++ b/file.kt\n@@ -1 +1 @@\n-val a = 1\n+val a = 2"))
    assertFalse(looksLikeChatTextDiagram("a → b"))
  }

  @Test
  fun untaggedFenceWithDiagramIsRecognizedOnlyWhenClosed() {
    val open = parseChatMarkdown("```\n$cocoaDiagram").firstChild as FencedCodeBlock
    assertNull(open.closingFenceLength)
    assertFalse(isChatTextDiagramFence(open, isStreaming = true))
    assertTrue(isChatTextDiagramFence(open, isStreaming = false))

    val closed = parseChatMarkdown("```\n$cocoaDiagram\n```").firstChild as FencedCodeBlock
    assertTrue(isChatTextDiagramFence(closed, isStreaming = true))
  }

  @Test
  fun languageTaggedCodeNeverBecomesADiagram() {
    val fenced = parseChatMarkdown("```kotlin\n$cocoaDiagram\n```").firstChild as FencedCodeBlock
    assertFalse(isChatTextDiagramFence(fenced, isStreaming = false))
  }

  @Test
  fun asciiBoxFlowchartIsRecognized() {
    val diagram =
      """
      +------+     +------+
      | A    | --> | B    |
      +------+     +------+
         |
         v
      +------+
      | C    |
      +------+
      """.trimIndent()
    assertTrue(looksLikeChatTextDiagram(diagram))
  }
}

internal val cocoaDiagramFixture =
  """
  可可果 → 取豆 → 发酵 → 晾晒 → 烘焙 → 去壳
                                      ↓
                                可可豆碎（nibs）
                                      ↓ 研磨
                            可可液块（cocoa liquor，含脂≈50%）
                               ↙              ↘
                        压榨分离              直接使用
                          ↙      ↘
                    可可脂      可可饼 → 磨粉 → 可可粉
  """.trimIndent()
