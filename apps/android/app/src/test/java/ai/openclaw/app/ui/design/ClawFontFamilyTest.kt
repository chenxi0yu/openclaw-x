package ai.openclaw.app.ui.design

import androidx.compose.ui.text.font.FontListFontFamily
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ClawFontFamilyTest {
  @Test
  fun declaredWeightsCancelThePlatformFontWeightAdjustment() {
    fun weights(adjustment: Int): List<Int> =
      (clawFontFamilyFor(adjustment) as FontListFontFamily).fonts.map { font ->
        font.weight.weight
      }

    assertEquals(listOf(400, 500, 600, 700), weights(adjustment = 0))
    assertEquals(listOf(700, 800, 900, 1000), weights(adjustment = 300))
    assertEquals(listOf(100, 200, 300, 400), weights(adjustment = -300))
  }
}
