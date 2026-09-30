package ai.openclaw.app.ui.chat

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
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

/** Reveal duration shared by every chain row (thinking, tools). */
internal const val CHAIN_REVEAL_MS = 220

/** Blur radius applied to a row at reveal progress 0; it clears as the row lands. */
private const val CHAIN_REVEAL_BLUR_DP = 10f

/**
 * How many rows may appear at once and still earn the reveal. Anything larger is
 * a history load or a session switch, where animating would be noise.
 */
private const val CHAIN_REVEAL_MAX_BATCH = 3

/** Blur needs RenderEffect, i.e. API 31+; older devices just get the fade. */
private val blurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Blur that dissolves as [progress] goes 0 -> 1. Rows do not animate their
 * height here: the transcript is reverse-laid-out and pinned to the bottom,
 * so a growing row would shove the live answer around.
 */
internal fun Modifier.chainRevealBlur(progress: Float): Modifier {
  if (!blurSupported) return this
  val radius = (1f - progress.coerceIn(0f, 1f)) * CHAIN_REVEAL_BLUR_DP
  return if (radius >= 0.5f) blur(radius.dp) else this
}

/**
 * Fades a freshly added chain row in, blurred first and sharp once it lands.
 * Rows that were already on screen never replay it.
 */
@Composable
internal fun ChatChainReveal(
  revealKey: String,
  animate: Boolean,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  var revealed by remember(revealKey) { mutableStateOf(!animate) }
  LaunchedEffect(revealKey) { if (!revealed) revealed = true }
  val progress by animateFloatAsState(
    targetValue = if (revealed) 1f else 0f,
    animationSpec = tween(CHAIN_REVEAL_MS, easing = FastOutSlowInEasing),
    label = "chainReveal",
  )
  // The layer is only mounted while the reveal runs. Leaving `graphicsLayer`
  // attached afterwards would keep every chain row on its own offscreen buffer
  // for good, which is exactly what stalls a long transcript.
  val revealModifier =
    if (progress < 1f) {
      Modifier
        .graphicsLayer { alpha = progress }
        .chainRevealBlur(progress)
    } else {
      Modifier
    }
  Box(modifier = modifier.then(revealModifier)) {
    content()
  }
}

/**
 * Decides which rows deserve the reveal animation: only rows whose key shows up
 * for the first time, and only for small increments. Bulk changes (history load,
 * session switch) must not turn the whole transcript into a fireworks show.
 *
 * The batch allowance matters because a run rarely lands one row at a time: a
 * transcript refresh after a tool turn typically adds two or three rows at
 * once (thinking + tool rows), and those are exactly the rows the reader wants
 * to see land one after another.
 */
internal class ChatChainRevealTracker {
  private val seen = HashSet<String>()
  private val pending = HashSet<String>()
  var primed = false
    private set

  fun isFresh(key: String): Boolean = key !in seen

  /** Seeds the rows allowed to animate this round; bulk changes seed nothing. */
  fun animateKeys(keys: Collection<String>): Set<String> {
    pending.clear()
    val fresh = keys.filter(::isFresh)
    if (primed && fresh.size in 1..CHAIN_REVEAL_MAX_BATCH) pending.addAll(fresh)
    return pending.toSet()
  }

  /**
   * True only for the first composition of a freshly added row. LazyColumn
   * recycles rows, so without consuming the key the reveal would replay every
   * time an old row scrolls back in.
   */
  fun consume(key: String): Boolean = pending.remove(key)

  fun mark(keys: Collection<String>) {
    seen.addAll(keys)
    primed = true
  }
}
