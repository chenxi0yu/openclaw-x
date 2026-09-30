package ai.openclaw.app.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * The backdrop source that floating layers such as [ClawGlassMenu] blur.
 *
 * `null` means nothing was marked, and the menu falls back to an opaque card
 * instead of trying to blur empty space.
 */
internal val LocalClawHazeState = compositionLocalOf<HazeState?> { null }

/**
 * Marks [content] as the thing floating menus sample.
 *
 * Only wrap the subtree that actually sits behind a menu. A marked source keeps a
 * graphics layer alive for the whole time an effect is open, so marking the app
 * root would make every screen pay for the sidebar's glass.
 */
@Composable
internal fun ClawGlassHost(
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  val hazeState = rememberHazeState()
  CompositionLocalProvider(LocalClawHazeState provides hazeState) {
    Box(modifier = modifier.hazeSource(hazeState), content = content)
  }
}

/**
 * Frosted treatment for a bar that content scrolls underneath.
 *
 * The blur ramps vertically: full strength where the bar meets the status bar,
 * zero at its bottom edge, so the bar dissolves into the list instead of ending
 * on a hard line. A matching gradient scrim rides on top of the blur and fades
 * out the same way — the blur alone is what makes text unreadable, so the scrim
 * can stay light enough for the bar to read as glass rather than as a panel.
 *
 * Requires the caller to have marked the scrolling content with
 * [dev.chrisbanes.haze.hazeSource]; the bar itself must stay outside that
 * subtree (Haze refuses to blur a source from inside it).
 */
@Composable
internal fun Modifier.clawTopBarGlass(hazeState: HazeState?): Modifier {
  if (hazeState == null) return this
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val style =
    remember(dark, colors.canvas) {
      HazeBlurStyle {
        blurEnabled(true)
        blurRadius(24.dp)
        // The bar is a thin strip: noise just makes it look grainy.
        noiseFactor(0f)
        backgroundColor(colors.canvas)
        progressive(
          HazeProgressive.verticalGradient(
            // Keep the lower edge nearly clear and increase blur continuously
            // toward the top so the boundary remains visible without a hard line.
            startIntensity = 1f,
            endIntensity = 0f,
            preferPerformance = false,
          ),
        )
      }
    }
  val scrim =
    remember(dark, colors.canvas) {
      Brush.verticalGradient(
        0f to colors.canvas.copy(alpha = if (dark) 0.20f else 0.24f),
        0.38f to colors.canvas.copy(alpha = if (dark) 0.07f else 0.09f),
        0.72f to colors.canvas.copy(alpha = if (dark) 0.015f else 0.02f),
        1f to Color.Transparent,
      )
    }
  // `background` sits after `hazeBlur` in the chain, so it paints over the blurred
  // result rather than under it.
  return this
    .hazeBlur(
      input = HazeInput.Sources(hazeState, selection = HazeSourceSelection.All),
      style = style,
    ).background(brush = scrim)
}
