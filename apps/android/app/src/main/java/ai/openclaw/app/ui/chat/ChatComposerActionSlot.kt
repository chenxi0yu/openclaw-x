package ai.openclaw.app.ui.chat

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.dp

/**
 * The composer's trailing control, swapped through a blur-fade: the outgoing
 * control defocuses while the incoming one sharpens, so the slot reads as one
 * morphing control instead of two hard-swapping icons.
 *
 * Shared by both input-bar styles so the send/stop/preparing controls stay
 * identical.
 */
@Composable
internal fun ChatComposerPrimaryActionSlot(
  action: ChatComposerPrimaryAction,
  sendEnabled: Boolean,
  onSend: () -> Unit,
  onAbort: () -> Unit,
  onToggleTalk: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val transition = updateTransition(targetState = action, label = "composerPrimaryAction")
  transition.AnimatedContent(
    modifier = modifier,
    transitionSpec = {
      (fadeIn(animationSpec = tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)))
        .togetherWith(fadeOut(animationSpec = tween(200)) + scaleOut(targetScale = 1.08f, animationSpec = tween(200)))
    },
  ) { target ->
    val blurFraction by
      transition.animateFloat(
        transitionSpec = { tween(200) },
        label = "composerPrimaryBlur",
      ) { state -> if (state == target) 0f else 1f }
    Box(modifier = Modifier.blur(8.dp * blurFraction)) {
      when (target) {
        ChatComposerPrimaryAction.Send -> SendButton(enabled = sendEnabled, onClick = onSend)
        ChatComposerPrimaryAction.Preparing -> PreparingSendButton()
        ChatComposerPrimaryAction.StartTalk -> LiveTalkButton(active = false, onClick = onToggleTalk)
        ChatComposerPrimaryAction.Stop -> StopButton(onClick = onAbort)
        ChatComposerPrimaryAction.None -> Unit
      }
    }
  }
}
