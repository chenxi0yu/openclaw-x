package ai.openclaw.app.ui.design

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop

/**
 * Geometry shared by every progressive-blur settings bar. The bar is taller
 * than the old 52dp strip so the blur has more room to decay, and the glass
 * controls grow with it instead of floating in the extra space.
 */
internal val ClawTopBarContentHeight = 58.dp
internal val ClawTopBarButtonSize = 46.dp
internal val ClawTopBarButtonTopInset = 6.dp

/**
 * HyperOS top-bar control used by the Settings landing, About and Share pages:
 * a bare icon that fades a circular liquid-glass disc in behind itself once
 * content can slide under the progressive-blur bar.
 */
@Composable
internal fun ClawGlassTopBarButton(
  contentDescription: String,
  chromeAlpha: Float,
  backdrop: Backdrop?,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val haptics = rememberClawHaptics()
  val rim = if (dark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.55f)
  Box(
    modifier =
      modifier
        .size(ClawTopBarButtonSize)
        .clickable(enabled = enabled, role = Role.Button) {
          haptics.tap()
          onClick()
        }
        .semantics {
          role = Role.Button
          this.contentDescription = contentDescription
        },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier.matchParentSize()
          .graphicsLayer { alpha = chromeAlpha }
          .clip(CircleShape)
          .clawLiquidButton(backdrop, colors.surfaceRaised, dark)
          .border(0.5.dp, rim, CircleShape),
    )
    content()
  }
}
