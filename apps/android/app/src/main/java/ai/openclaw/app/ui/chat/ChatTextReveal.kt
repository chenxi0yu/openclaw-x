package ai.openclaw.app.ui.chat

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Entrance for a newly arrived Markdown block. Reasoning-mode gateways hand over
 * whole paragraphs, so a short front-loaded blur reads as a flash: the fade is
 * long, keeps most of its curve ahead of the alpha, and drops blur for large
 * blocks (an offscreen buffer over a full answer costs frames).
 */
private const val CHAT_TEXT_REVEAL_MIN_MS = 260
private const val CHAT_TEXT_REVEAL_MAX_MS = 420
private const val CHAT_TEXT_REVEAL_MS_PER_CHAR = 0.12
private const val CHAT_TEXT_REVEAL_BLUR_CHAR_LIMIT = 240
private const val CHAT_TEXT_REVEAL_BLUR_DP = 4f
private const val CHAT_TEXT_REVEAL_START_ALPHA = 0.35f
private const val CHAT_TEXT_REVEAL_LARGE_START_ALPHA = 0.55f

/** Alpha rises fast then settles slowly, so the text is readable early but never snaps. */
private val ChatRevealEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** Blur clears ahead of alpha; a block must be sharp before the fade finishes. */
private fun blurFraction(progress: Float): Float = (1f - progress * 1.4f).coerceIn(0f, 1f)

internal fun chatTextRevealDurationMs(contentChars: Int): Int =
  (CHAT_TEXT_REVEAL_MIN_MS + contentChars * CHAT_TEXT_REVEAL_MS_PER_CHAR)
    .roundToInt()
    .coerceIn(CHAT_TEXT_REVEAL_MIN_MS, CHAT_TEXT_REVEAL_MAX_MS)

/**
 * Fades and sharpens one newly arrived block once. The wrapper only exists while
 * the block is the newest one, so a growing paragraph keeps its settled state and
 * fully opaque history never replays the entrance.
 */
@Composable
internal fun ChatTextBlockReveal(
  revealKey: String,
  animate: Boolean,
  contentChars: Int,
  content: @Composable () -> Unit,
) {
  var revealed by remember(revealKey) { mutableStateOf(!animate) }
  LaunchedEffect(revealKey) { if (!revealed) revealed = true }
  val useBlur = contentChars <= CHAT_TEXT_REVEAL_BLUR_CHAR_LIMIT
  val startAlpha = if (useBlur) CHAT_TEXT_REVEAL_START_ALPHA else CHAT_TEXT_REVEAL_LARGE_START_ALPHA
  val progress by animateFloatAsState(
    targetValue = if (revealed) 1f else 0f,
    animationSpec = tween(chatTextRevealDurationMs(contentChars), easing = ChatRevealEasing),
    label = "textBlockReveal",
  )
  // The layer is mounted only while the entrance runs. Keeping it attached
  // afterwards would leave every answer block on its own offscreen buffer.
  val revealModifier =
    if (progress < 1f) {
      val modifier = Modifier.graphicsLayer {
        alpha = startAlpha + (1f - startAlpha) * progress
      }
      if (useBlur) modifier.textBlockRevealBlur(blurFraction(progress)) else modifier
    } else {
      Modifier
    }
  Box(modifier = revealModifier) {
    content()
  }
}

/** Blur needs RenderEffect, i.e. API 31+; older devices just get the fade. */
private fun Modifier.textBlockRevealBlur(progress: Float): Modifier {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return this
  val radius = progress * CHAT_TEXT_REVEAL_BLUR_DP
  return if (radius >= 0.5f) blur(radius.dp) else this
}
