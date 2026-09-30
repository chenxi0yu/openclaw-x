package ai.openclaw.app.ui.chat

import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Reasoning rows kept visible while a thinking block is streaming. */
internal const val CHAT_THINKING_PREVIEW_LINES = 3

/** Preview panel animation: grows downward on the way in, folds upward on the way out. */
private const val CHAT_THINKING_PREVIEW_ENTER_MS = 260
private const val CHAT_THINKING_PREVIEW_EXIT_MS = 200

/**
 * Keeps only the newest reasoning lines. Showing the head instead would freeze
 * on the model's first idea while it has already moved on.
 */
internal fun chatThinkingPreview(
  text: String,
  maxLines: Int = CHAT_THINKING_PREVIEW_LINES,
): String {
  val lines = text.lineSequence().map { it.trimEnd() }.filter { it.isNotBlank() }.toList()
  if (lines.size <= maxLines) return text.trim()
  return lines.takeLast(maxLines).joinToString("\n")
}

/**
 * Single-line reasoning row, visually identical to the tool rows so the
 * thinking -> tool -> answer chain reads as one list.
 *
 * - While streaming: title shows "思考中…" and the newest 3 reasoning lines
 *   stay visible inline underneath; tapping opens the detail sheet which
 *   keeps streaming.
 * - Once finished: collapses to one line ("思考完成"); tapping opens the
 *   detail sheet with the full reasoning.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatThinkingBlock(
  text: String,
  streaming: Boolean,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val colors = ClawTheme.colors
  val preview = remember(text) { chatThinkingPreview(text) }
  val showPreview = streaming && preview.isNotBlank()

  // Same as the tool rows: skip Material's 48dp minimum-height stamp so the
  // thinking row sits as tight as a tool row.
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(
      modifier = modifier.fillMaxWidth(),
      onClick = onClick,
      shape = RoundedCornerShape(4.dp),
      color = Color.Transparent,
      contentColor = colors.textMuted,
    ) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(vertical = 3.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LucideThinkingIcon(
            // Orbits only while reasoning is still arriving; a finished block
            // keeps the same glyph at rest, so the row does not change shape.
            running = streaming,
            modifier = Modifier.size(16.dp),
            // Same muted ink as the tool rows: the thinking -> tool chain reads
            // as one list, so one colored glyph would break the line up.
            tint = colors.textMuted,
          )
          Text(
            text = if (streaming) "思考中…" else "思考完成",
            style = ClawTheme.type.caption,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.textSubtle,
          )
        }
        // The preview used to pop in at full height the moment the first
        // reasoning line arrived. It now unrolls downward from the title row
        // (and dissolves out of focus), and folds back upward — bottom lines
        // first — once the block finishes and the row becomes "思考完成".
        AnimatedVisibility(
          visible = showPreview,
          enter =
            expandVertically(
              animationSpec = tween(CHAT_THINKING_PREVIEW_ENTER_MS, easing = FastOutSlowInEasing),
              expandFrom = Alignment.Top,
            ) +
              fadeIn(animationSpec = tween(CHAT_THINKING_PREVIEW_ENTER_MS, easing = FastOutSlowInEasing)),
          exit =
            shrinkVertically(
              animationSpec = tween(CHAT_THINKING_PREVIEW_EXIT_MS, easing = FastOutSlowInEasing),
              shrinkTowards = Alignment.Top,
            ) +
              fadeOut(animationSpec = tween(CHAT_THINKING_PREVIEW_EXIT_MS, easing = FastOutSlowInEasing)),
        ) {
          val progress by transition.animateFloat(
            transitionSpec = { tween(CHAT_THINKING_PREVIEW_ENTER_MS, easing = FastOutSlowInEasing) },
            label = "thinkingPreviewBlur",
          ) { state -> if (state == EnterExitState.Visible) 1f else 0f }
          Text(
            text = preview,
            style = ClawTheme.type.caption,
            color = colors.textSubtle,
            maxLines = CHAT_THINKING_PREVIEW_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier =
              Modifier
                .padding(start = 24.dp)
                .chainRevealBlur(progress),
          )
        }
      }
    }
  }
}
