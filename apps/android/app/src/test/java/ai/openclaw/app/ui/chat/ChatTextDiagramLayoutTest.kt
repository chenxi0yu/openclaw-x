package ai.openclaw.app.ui.chat

import ai.openclaw.app.ui.design.ClawDesignTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-420dpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ChatTextDiagramLayoutTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun diagramKeepsSourceLineBreaksAndTerminalColumns() {
    composeRule.setContent {
      ClawDesignTheme {
        Box(Modifier.size(360.dp, 800.dp).clipToBounds()) {
          ChatTextDiagramBlock(cocoaDiagramFixture)
        }
      }
    }

    val layouts = mutableListOf<TextLayoutResult>()
    composeRule
      .onNode(hasText("可可果", substring = true), useUnmergedTree = true)
      .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action -> assertTrue(action(layouts)) }
    val layout = layouts.single()
    assertEquals("A diagram must render one visual line per source line", 9, layout.lineCount)

    val text = layout.layoutInput.text.text
    val pressureLineStart = layout.getLineStart(5)
    val useLineStart = layout.getLineStart(6)
    val rightArrow = layout.getBoundingBox(text.indexOf('↘', pressureLineStart)).left
    val directUse = layout.getBoundingBox(text.indexOf('直', useLineStart)).left
    // Both glyphs sit at column 44 in the source; width normalization must keep
    // them on the same vertical line even though the left line mixes CJK and arrows.
    assertEquals("CJK cells must occupy two terminal columns", rightArrow, directUse, 1f)
  }
}
