package ai.openclaw.app.ui.chat

import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawCircularIconButton
import ai.openclaw.app.ui.design.ClawTheme
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess as MiuixExpandLess

/**
 * Full-screen writing mode for the simple bar. The sheet springs open from the
 * bar's side of the screen and collapses back the same way; the glass button in
 * the corner is the only chrome, mirroring the header controls.
 */
@Composable
internal fun SimpleComposerFullScreenEditor(
  visible: Boolean,
  value: String,
  onValueChange: (String) -> Unit,
  focusRequestId: Int,
  backdrop: Backdrop?,
  onCollapse: () -> Unit,
) {
  BackHandler(enabled = visible, onBack = onCollapse)
  AnimatedVisibility(
    visible = visible,
    enter =
      fadeIn(animationSpec = tween(180)) +
        scaleIn(
          initialScale = 0.88f,
          transformOrigin = TransformOrigin(0f, 1f),
          animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        ),
    exit =
      fadeOut(animationSpec = tween(160)) +
        scaleOut(
          targetScale = 0.94f,
          transformOrigin = TransformOrigin(0f, 1f),
          animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        ),
  ) {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Box(modifier = Modifier.fillMaxSize().background(ClawTheme.colors.canvas)) {
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = statusBar + 10.dp, bottom = 16.dp),
      ) {
        // Room for the collapse control, then the writing surface.
        Spacer(modifier = Modifier.height(52.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
          if (value.isEmpty()) {
            Text(
              text = nativeString("Message OpenClaw"),
              style = chatDraftStyle(),
              color = ClawTheme.colors.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          ChatComposerEditText(
            value = value,
            onValueChange = onValueChange,
            enabled = true,
            textStyle = chatDraftStyle().copy(color = ClawTheme.colors.text),
            cursorColor = ClawTheme.colors.primary,
            sendEnabled = false,
            onSend = {},
            focusRequestId = focusRequestId,
            gravityTop = true,
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
      ClawCircularIconButton(
        icon = MiuixIcons.Medium.MiuixExpandLess,
        contentDescription = nativeString("Collapse input"),
        onClick = onCollapse,
        backdrop = backdrop,
        modifier = Modifier.align(Alignment.TopEnd).padding(top = statusBar + 6.dp, end = 16.dp),
      )
    }
  }
}
