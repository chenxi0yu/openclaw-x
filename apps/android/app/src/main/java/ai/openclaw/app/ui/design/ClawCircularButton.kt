package ai.openclaw.app.ui.design

import ai.openclaw.app.i18n.nativeString
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import dev.chrisbanes.haze.HazeState

internal object ClawCircularButtonDefaults {
  val Size: Dp = 48.dp
  val IconSize: Dp = 26.dp
  val Elevation: Dp = 8.dp
  val RimElevation: Dp = 0.dp
  val GroupSpacing: Dp = 8.dp

  /** Resting glass defocus; the turn pill animates around this value. */
  val GlassBlur: Dp = 3.dp
}

/**
 * Glass-look background stack shared by the header buttons. A semi-transparent
 * white base lifts the button off the transcript, then a subtle top-down
 * highlight gradient adds the "raised" feel. No RenderEffect / hazeBlur —
 * those crash `libhwui` on emulators and aren't worth it for a 48dp target.
 */
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
  backdrop: Backdrop? = null,
) {
  val haptics = rememberClawHaptics()
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val surface = colors.surfaceRaised
  val spotShadow = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f)
  val ambientShadow = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f)
  val pressDrag = rememberClawPressDrag(glow = dark)

  Box(
    modifier =
      modifier
        // First in the chain so the elevation shadow swells with the glass
        // instead of staying a static disc behind a moving button.
        .clawPressDrag(pressDrag)
        .size(ClawTheme.spacing.touchTarget)
        .shadow(
          elevation = ClawCircularButtonDefaults.Elevation,
          shape = CircleShape,
          ambientColor = ambientShadow,
          spotColor = spotShadow,
          clip = false,
        )
        .clip(CircleShape)
        .clawLiquidButton(backdrop, surface, dark)
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
        }
        // After `clickable`: the drag observer never consumes, so the tap still
        // lands, and the glow paints over the glass but under the icon.
        .then(if (enabled) pressDrag.modifier.then(pressDrag.gestureModifier) else Modifier),
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

private val TurnTopPillHeight = 30.dp
private const val TurnTopPillSinkDp = 7f
private const val TurnTopPillRiseDp = 10f
private const val TurnTopPillBlurInDp = 10f
private const val TurnTopPillBlurOutDp = 14f
private const val TurnTopPillEnterMs = 240
private const val TurnTopPillExitMs = 300
private const val TurnTopPillExitHoldMs = 40

/**
 * Small glass label that offers to park the prompt of the turn being read back
 * under the top bar. It is a hint, not a control: it shows up while the
 * transcript is moving and leaves once reading settles.
 *
 * The entrance sinks a few dp while its blur resolves. The exit holds for a beat
 * and then defocuses and drifts up as a single dissolve. The motion layer only
 * exists while the pill is moving, so a standing pill keeps no offscreen buffer
 * of its own.
 *
 * Glass is sampled from the pane backdrop, exactly like the header controls, so
 * the pill really refracts the transcript sliding underneath it.
 */
@Composable
internal fun ClawTurnTopPill(
  visible: Boolean,
  label: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: Backdrop? = null,
) {
  val haptics = rememberClawHaptics()
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val pressDrag = rememberClawPressDrag(glow = dark)
  val progress = remember { Animatable(0f) }
  var mounted by remember { mutableStateOf(visible) }
  var exiting by remember { mutableStateOf(false) }
  LaunchedEffect(visible) {
    if (visible) {
      exiting = false
      mounted = true
      progress.animateTo(
        targetValue = 1f,
        animationSpec = tween(durationMillis = TurnTopPillEnterMs, easing = FastOutSlowInEasing),
      )
    } else {
      exiting = true
      // Hold for a beat before dissolving: the old 170ms fade read as the pill
      // being dropped rather than dismissed.
      progress.animateTo(
        targetValue = 0f,
        animationSpec =
          tween(
            durationMillis = TurnTopPillExitMs,
            delayMillis = TurnTopPillExitHoldMs,
            easing = FastOutLinearInEasing,
          ),
      )
      // Unmount at rest: a hidden pill must neither hit-test nor hold a layer.
      mounted = false
    }
  }
  if (!mounted) return
  // Defocus the glass itself rather than stacking a second RenderEffect on the
  // backdrop: nested layers abort libhwui on the emulator.
  val glassBlur =
    ClawCircularButtonDefaults.GlassBlur +
      ((1f - progress.value) * (if (exiting) TurnTopPillBlurOutDp else TurnTopPillBlurInDp)).dp
  val motion =
    if (progress.value < 1f) {
      val p = progress.value
      val travel = (1f - p) * (if (exiting) TurnTopPillRiseDp else TurnTopPillSinkDp)
      // Out: the glass keeps its body while it defocuses, so the tail is a
      // dissolve rather than a flat fade. In: opacity follows the motion.
      val alphaValue = if (exiting) p * p else p
      val scale = if (exiting) 0.96f + 0.04f * p else 1f
      Modifier.graphicsLayer {
        alpha = alphaValue
        // Starts above and sinks into place; the exit drifts back up.
        translationY = -travel.dp.toPx()
        scaleX = scale
        scaleY = scale
      }
    } else {
      Modifier
    }
  val shape = RoundedCornerShape(percent = 50)
  Row(
    modifier =
      modifier
        .then(motion)
        .clawPressDrag(pressDrag)
        .height(TurnTopPillHeight)
        .shadow(
          elevation = ClawCircularButtonDefaults.Elevation,
          shape = shape,
          ambientColor = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f),
          spotColor = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f),
          clip = false,
        )
        .clip(shape)
        .clawLiquidButton(
          backdrop = backdrop,
          surface = colors.surfaceRaised,
          dark = dark,
          blurRadius = glassBlur,
        )
        .clickable(role = Role.Button) {
          haptics.tap()
          onClick()
        }
        .semantics {
          role = Role.Button
          contentDescription = label
        }
        .then(pressDrag.modifier)
        .then(pressDrag.gestureModifier)
        .padding(horizontal = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.captionSmall.copy(fontWeight = FontWeight.Medium),
      color = colors.text,
      maxLines = 1,
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
  backdrop: Backdrop? = null,
) {
  val haptics = rememberClawHaptics()
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val surface = colors.surfaceRaised
  val spotShadow = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f)
  val ambientShadow = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f)

  val pillShape = RoundedCornerShape(percent = 50)
  val iconSize = 22.dp
  // One press state for the whole capsule: either half swells the same surface,
  // and the glow tracks the finger across both.
  val pressDrag = rememberClawPressDrag(glow = dark)

  Row(
    modifier =
      modifier
        .clawPressDrag(pressDrag)
        .height(ClawTheme.spacing.touchTarget)
        .shadow(
          elevation = ClawCircularButtonDefaults.Elevation,
          shape = pillShape,
          ambientColor = ambientShadow,
          spotColor = spotShadow,
          clip = false,
        )
        .clip(pillShape)
        .clawLiquidButton(backdrop, surface, dark)
        .then(pressDrag.modifier)
        .then(pressDrag.gestureModifier),
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