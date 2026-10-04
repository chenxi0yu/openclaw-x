package ai.openclaw.app.ui.design

import ai.openclaw.app.i18n.nativeString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

internal object ClawCircularButtonDefaults {
  val Size: Dp = 48.dp
  val IconSize: Dp = 26.dp
  val Elevation: Dp = 8.dp
  val RimElevation: Dp = 0.dp
  val GroupSpacing: Dp = 8.dp
}

/**
 * Glass-look background stack shared by the header buttons. A semi-transparent
 * white base lifts the button off the transcript, then a subtle top-down
 * highlight gradient adds the "raised" feel. No RenderEffect / hazeBlur —
 * those crash `libhwui` on emulators and aren't worth it for a 48dp target.
 */
private fun Modifier.glassButtonBackground(dark: Boolean, surfaceRaised: Color): Modifier =
  this
    .background(
      surfaceRaised,
    )
    .background(
      Brush.verticalGradient(
        0f to if (dark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.55f),
        0.5f to Color.Transparent,
        1f to Color.Transparent,
      ),
    )

/**
 * Raised circular button with a soft glass-look background.
 */
@Composable
internal fun ClawCircularIconButton(
  icon: ImageVector,
  contentDescription: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  haptic: Boolean = true,
  size: Dp = ClawCircularButtonDefaults.Size,
  hazeState: HazeState? = null,
) {
  val haptics = rememberClawHaptics()
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val spotShadow = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f)
  val ambientShadow = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f)

  Box(
    modifier =
      modifier
        .size(ClawTheme.spacing.touchTarget)
        .shadow(
          elevation = ClawCircularButtonDefaults.Elevation,
          shape = CircleShape,
          ambientColor = ambientShadow,
          spotColor = spotShadow,
          clip = false,
        )
        .clip(CircleShape)
        .glassButtonBackground(dark, colors.surfaceRaised)
        .then(
          if (enabled) {
            Modifier.clickable(role = Role.Button) {
              if (haptic) haptics.tap()
              onClick()
            }
          } else Modifier,
        )
        .semantics {
          role = Role.Button
          this.contentDescription = contentDescription
        },
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      modifier = Modifier.size(ClawCircularButtonDefaults.IconSize),
      tint = if (enabled) colors.text else colors.textSubtle,
    )
  }
}

/**
 * Floating "jump to latest" pill.
 */
@Composable
internal fun ClawJumpToLatestButton(
  visible: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  hazeState: HazeState? = null,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter =
      fadeIn(animationSpec = tween(durationMillis = 170, easing = LinearOutSlowInEasing)) +
        scaleIn(
          initialScale = 0.82f,
          transformOrigin = TransformOrigin.Center,
          animationSpec = tween(durationMillis = 170, easing = FastOutSlowInEasing),
        ),
    exit =
      fadeOut(animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing)) +
        scaleOut(
          targetScale = 0.86f,
          transformOrigin = TransformOrigin.Center,
          animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing),
        ),
  ) {
    ClawCircularIconButton(
      icon = Icons.Default.ArrowDownward,
      contentDescription = nativeString("Jump to latest"),
      onClick = onClick,
    )
  }
}

/**
 * Capsule-shaped button holding two icon actions side by side.
 * Each icon is an independent touch target.
 */
@Composable
internal fun ClawCapsuleIconButton(
  leftIcon: ImageVector,
  leftContentDescription: String,
  onLeftClick: () -> Unit,
  rightIcon: ImageVector,
  rightContentDescription: String,
  onRightClick: () -> Unit,
  modifier: Modifier = Modifier,
  leftEnabled: Boolean = true,
  rightEnabled: Boolean = true,
  hazeState: HazeState? = null,
) {
  val haptics = rememberClawHaptics()
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val spotShadow = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f)
  val ambientShadow = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f)

  val pillShape = RoundedCornerShape(percent = 50)
  val iconSize = 22.dp

  Row(
    modifier =
      modifier
        .height(ClawTheme.spacing.touchTarget)
        .shadow(
          elevation = ClawCircularButtonDefaults.Elevation,
          shape = pillShape,
          ambientColor = ambientShadow,
          spotColor = spotShadow,
          clip = false,
        )
        .clip(pillShape)
        .glassButtonBackground(dark, colors.surfaceRaised),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    // Left icon
    Box(
      modifier =
        Modifier
          .padding(start = 6.dp)
          .size(36.dp)
          .clip(pillShape)
          .then(
            if (leftEnabled) {
              Modifier.clickable(role = Role.Button) {
                haptics.tap()
                onLeftClick()
              }
            } else Modifier,
          )
          .semantics {
            role = Role.Button
            contentDescription = leftContentDescription
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = leftIcon,
        contentDescription = null,
        modifier = Modifier.size(iconSize),
        tint = if (leftEnabled) colors.text else colors.textSubtle,
      )
    }
    // Right icon
    Box(
      modifier =
        Modifier
          .padding(end = 6.dp)
          .size(36.dp)
          .clip(pillShape)
          .then(
            if (rightEnabled) {
              Modifier.clickable(role = Role.Button) {
                haptics.tap()
                onRightClick()
              }
            } else Modifier,
          )
          .semantics {
            role = Role.Button
            contentDescription = rightContentDescription
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = rightIcon,
        contentDescription = null,
        modifier = Modifier.size(iconSize),
        tint = if (rightEnabled) colors.text else colors.textSubtle,
      )
    }
  }
}