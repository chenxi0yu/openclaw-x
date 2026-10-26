package ai.openclaw.app.ui.chat

import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** One sweep takes 1.4s, then the label rests for half a second before the next. */
private const val SHIMMER_SWEEP_MS = 1400
private const val SHIMMER_REST_MS = 500
private const val SHIMMER_BAND_DP = 52f

/**
 * Label for a row that is still working.
 *
 * A light band sweeps left to right through the text (the "AI 打字中" shimmer
 * every assistant app uses). Miuix icons are static, so this shimmer is the
 * row's only motion: it keeps the running state legible without swapping the
 * glyph halfway through a tool call.
 *
 * The brush needs the measured text width, hence [onSizeChanged]; before the
 * first measure the band sits off-screen, which renders the plain base colour.
 */
@Composable
internal fun ShimmeringLabel(
  text: String,
  style: TextStyle,
  color: Color,
  shimmering: Boolean,
  modifier: Modifier = Modifier,
  maxLines: Int = 1,
  overflow: TextOverflow = TextOverflow.Ellipsis,
) {
  if (!shimmering) {
    Text(
      text = text,
      modifier = modifier,
      style = style,
      color = color,
      maxLines = maxLines,
      overflow = overflow,
    )
    return
  }

  var widthPx by remember { mutableIntStateOf(0) }
  val dark = ClawTheme.colors.canvas.luminance() < 0.5f
  val highlight = if (dark) Color.White.copy(alpha = 0.95f) else ClawTheme.colors.text
  val progress by
    rememberInfiniteTransition(label = "processShimmer").animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation =
            keyframes {
              durationMillis = SHIMMER_SWEEP_MS + SHIMMER_REST_MS
              0f at 0
              1f at SHIMMER_SWEEP_MS
            },
          repeatMode = RepeatMode.Restart,
        ),
      label = "processShimmerSweep",
    )
  val bandPx = with(LocalDensity.current) { SHIMMER_BAND_DP.dp.toPx() }
  val startX = -bandPx + progress * (widthPx + 2f * bandPx)
  val brush =
    Brush.linearGradient(
      colors = listOf(color, highlight, color),
      start = Offset(startX, 0f),
      end = Offset(startX + bandPx, 0f),
    )

  Text(
    text = text,
    modifier = modifier.onSizeChanged { widthPx = it.width },
    style = style.copy(brush = brush),
    color = Color.Unspecified,
    maxLines = maxLines,
    overflow = overflow,
  )
}
