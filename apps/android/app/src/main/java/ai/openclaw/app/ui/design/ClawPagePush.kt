package ai.openclaw.app.ui.design

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import kotlin.math.roundToInt

/**
 * In-page push for route swaps inside one surface, such as Settings Home and its details.
 *
 * The incoming page travels the full width while the outgoing one parallax-slides a fraction of
 * it and fades, so the hand-off reads as two stacked sheets instead of one layer dissolving into
 * another. `forward` mirrors the travel so Back returns the way it came. Durations sit in the
 * platform's activity range, and Compose scales them by the system animation setting, so
 * "remove animations" cuts instantly without a branch at the call site.
 */
internal fun clawPagePush(forward: Boolean): ContentTransform {
  val direction = if (forward) 1 else -1
  return slideInHorizontally(
    animationSpec = tween(PAGE_PUSH_MS, easing = FastOutSlowInEasing),
    initialOffsetX = { fullWidth -> fullWidth * direction },
  ) togetherWith
    slideOutHorizontally(
      animationSpec = tween(PAGE_POP_MS, easing = FastOutSlowInEasing),
      targetOffsetX = { fullWidth -> (-fullWidth * PAGE_PARALLAX * direction).roundToInt() },
    ) +
    fadeOut(animationSpec = tween(PAGE_POP_MS, easing = LinearOutSlowInEasing))
}

/**
 * Tab hand-off: the leaving page is dropped on the spot and the arriving page fades in over it.
 *
 * Horizontal travel is the sidebar's job — the drawer is already sliding while a destination
 * change happens, and a second slide on top of it reads as two panels fighting. Dropping the
 * outgoing layer instead of fading it also keeps hosted WebViews out of an animated alpha group,
 * which is where they flicker; every page paints the same canvas color, so the gap between them
 * is invisible.
 */
internal fun clawPageFade(): ContentTransform =
  fadeIn(animationSpec = tween(PAGE_FADE_MS, easing = LinearOutSlowInEasing)) togetherWith
    fadeOut(animationSpec = tween(0))

/**
 * Travel curve for the sidebar's selection card.
 *
 * Emphasized decelerate: the card leaves fast and settles long, which reads as weight. A linear
 * or stiff spring rate is what made the switch look like a snap between rows.
 */
internal fun clawSelectionTravel(): AnimationSpec<Float> =
  tween(SELECTION_TRAVEL_MS, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f))

/**
 * Sidebar destination hand-off budget: selection travel and the page fade run together, and the
 * drawer retracts at the end of this window so it never reveals a page still fading in.
 */
internal const val SIDEBAR_HANDOFF_MS = 360L

/**
 * Blur the hand-off starts with, in dp, and how long it takes to pull into focus. The decay runs
 * past SIDEBAR_HANDOFF_MS on purpose: the drawer is still retracting when the page goes sharp, so
 * the focus pull is the last thing you see rather than something hidden behind the sheet.
 */
internal const val SIDEBAR_BLUR_START_DP = 16f
internal const val SIDEBAR_BLUR_MS = 480

private const val PAGE_PUSH_MS = 300
private const val PAGE_POP_MS = 260
private const val PAGE_FADE_MS = 220
private const val SELECTION_TRAVEL_MS = 300

// Outgoing pages recede a quarter of the screen; a full-width exit would read as a plain slide
// and hide the depth cue that makes the push legible.
private const val PAGE_PARALLAX = 0.25f
