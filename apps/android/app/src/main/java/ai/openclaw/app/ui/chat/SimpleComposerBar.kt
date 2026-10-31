package ai.openclaw.app.ui.chat

import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.clawGlassEdge
import ai.openclaw.app.ui.design.clawPressDrag
import ai.openclaw.app.ui.design.rememberClawHaptics
import ai.openclaw.app.ui.design.rememberClawPressDrag
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import top.yukonga.miuix.kmp.icon.extended.ExpandMore as MiuixExpandMore
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.TransformOrigin
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add as MiuixAdd

internal val SimpleComposerBarHeight = 48.dp
private val SimpleComposerExpandButtonSize = 34.dp
private val SimpleComposerExpandStackHeight =
  SimpleComposerBarHeight + SimpleComposerExpandButtonSize + 4.dp

/**
 * The "simple" input bar: a standalone glass disc for the ＋ card and one glass
 * capsule holding the editor and the shared send/stop control. Both float over
 * the transcript with nothing painted behind them.
 *
 * The material deliberately mirrors the chat header buttons (same 0.28/0.38
 * tint, tiny 3dp blur, rim, shadow and press-drag). It cannot use
 * [ai.openclaw.app.ui.design.clawLiquidButton] here: that samples the pane
 * backdrop, and the composer lives inside the pane backdrop's own recorded
 * layer, so a sampler there would recurse through the RenderNode tree. Haze
 * already owns the transcript capture for the classic bar, so this shape reads
 * as the same glass without a second full-pane capture.
 */
@Composable
internal fun SimpleComposerBar(
  inputEnabled: Boolean,
  gatewayConnecting: Boolean,
  value: String,
  onValueChange: (String) -> Unit,
  focusRequestId: Int,
  hasContent: Boolean,
  sendEnabled: Boolean,
  onSend: () -> Unit,
  runActive: Boolean,
  sendInFlight: Boolean,
  onAbort: () -> Unit,
  talkActive: Boolean,
  onToggleTalk: () -> Unit,
  onOpenMenu: () -> Unit,
  /** Opens the full-screen writing mode. */
  onExpandEditor: () -> Unit,
  /** The header capsule's own trigger modifier, applied at the chain head. */
  plusTriggerModifier: Modifier,
  hazeState: HazeState?,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val haptics = rememberClawHaptics()
  val plusPress = rememberClawPressDrag(glow = dark)
  val capsulePress = rememberClawPressDrag(glow = dark)
  val capsuleShape = RoundedCornerShape(24.dp)
  val spotShadow = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f)
  val ambientShadow = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f)
  val draftStyle = chatDraftStyle()
  // Grows with its text up to a quarter of the screen, then scrolls inside the
  // bar. A fixed 24dp radius keeps the capsule silhouette on one line and reads
  // as a rounded rectangle once it is taller.
  var lineCount by remember { mutableIntStateOf(1) }
  val lineHeightDp = with(LocalDensity.current) { draftStyle.lineHeight.toDp() }
  val maxBarHeight = LocalConfiguration.current.screenHeightDp.dp * 0.25f
  val showExpand = lineCount >= 2
  // From the second line on the bar also has to hold the expand control stacked
  // over the send target; growing for it keeps the send button at its full size
  // instead of squeezing it. Controls stay pinned to the bottom edge and only
  // the top of the capsule travels.
  val naturalBarHeight =
    SimpleComposerBarHeight + lineHeightDp * (lineCount - 1).coerceAtLeast(0)
  val targetBarHeight =
    (if (showExpand) maxOf(naturalBarHeight, SimpleComposerExpandStackHeight) else naturalBarHeight)
      .coerceAtMost(maxOf(maxBarHeight, SimpleComposerExpandStackHeight))
  val barHeight by
    animateDpAsState(
      targetValue = targetBarHeight,
      animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f),
      label = "simpleComposerHeight",
    )
  val plusInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
  val openMenuWithTap: () -> Unit = {
    haptics.tap()
    onOpenMenu()
  }

  Box(modifier = modifier.fillMaxWidth()) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    // Bottom-pinned: the disc and the send target keep the screen-side edge
    // while the capsule above them grows upward with the text.
    verticalAlignment = Alignment.Bottom,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier
          // Same position in the chain as the header capsule: the trigger
          // modifier must wrap the disc's own drawing, so it is applied before
          // the size, shadow, clip and glass.
          .zIndex(1f)
          .then(plusTriggerModifier)
          .clawPressDrag(plusPress)
          .size(SimpleComposerBarHeight)
          .shadow(elevation = 8.dp, shape = CircleShape, ambientColor = ambientShadow, spotColor = spotShadow, clip = false)
          .clip(CircleShape)
          .simpleComposerGlass(shape = CircleShape, cornerRadius = 24.dp, hazeState = hazeState)
          .then(plusPress.modifier)
          .then(plusPress.gestureModifier)
          .clickable(
            interactionSource = plusInteraction,
            indication = null,
            role = Role.Button,
            onClick = openMenuWithTap,
          )
          .semantics {
            role = Role.Button
            contentDescription = nativeString("Add attachment")
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = MiuixIcons.Medium.MiuixAdd,
        contentDescription = null,
        tint = colors.text,
        modifier = Modifier.size(22.dp),
      )
    }

    Row(
      modifier =
        Modifier
          .weight(1f)
          .height(barHeight)
          .clawPressDrag(capsulePress)
          .shadow(elevation = 8.dp, shape = capsuleShape, ambientColor = ambientShadow, spotColor = spotShadow, clip = false)
          .clip(capsuleShape)
          .simpleComposerGlass(shape = capsuleShape, cornerRadius = 24.dp, hazeState = hazeState)
          .then(capsulePress.modifier)
          .then(capsulePress.gestureModifier),
      verticalAlignment = Alignment.Bottom,
    ) {
      Box(
        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 18.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.TopStart,
      ) {
        if (value.isEmpty()) {
          if (gatewayConnecting) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.8.dp,
                color = colors.textMuted,
              )
              Text(
                text = nativeString("Connecting…"),
                style = draftStyle,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          } else {
            Text(
              text = nativeString("Message OpenClaw"),
              style = draftStyle,
              color = colors.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
        }
        ChatComposerEditText(
          value = value,
          onValueChange = onValueChange,
          enabled = inputEnabled,
          textStyle = draftStyle.copy(color = colors.text),
          cursorColor = colors.primary,
          sendEnabled = sendEnabled,
          onSend = onSend,
          focusRequestId = focusRequestId,
          gravityCenterVertical = false,
          onLineCountChanged = { count -> lineCount = count.coerceAtLeast(1) },
          modifier = Modifier.fillMaxSize(),
        )
      }
      // One column owns the right edge: the expand control reveals against the
      // capsule's top edge while the send target stays pinned to the bottom, so
      // a growing bar never squeezes the send button.
      Box(modifier = Modifier.fillMaxHeight().width(SimpleComposerBarHeight)) {
        SimpleComposerExpandControl(
          visible = showExpand,
          onClick = {
            haptics.tap()
            onExpandEditor()
          },
          modifier = Modifier.align(Alignment.TopCenter),
        )
        Box(
          modifier = Modifier.align(Alignment.BottomCenter).size(SimpleComposerBarHeight),
          contentAlignment = Alignment.Center,
        ) {
          if (talkActive) {
            // Talk can be started elsewhere; the simple bar has no dedicated mic,
            // but an active session must still be endable here.
            LiveTalkButton(active = true, onClick = onToggleTalk)
          } else {
            ChatComposerPrimaryActionSlot(
              action =
                resolveChatComposerPrimaryAction(
                  talkActive = false,
                  runActive = runActive,
                  sendInFlight = sendInFlight,
                  hasContent = hasContent,
                  liveTalkVisible = false,
                ),
              sendEnabled = inputEnabled && sendEnabled,
              onSend = onSend,
              onAbort = onAbort,
              onToggleTalk = onToggleTalk,
              modifier = Modifier.size(ClawTheme.spacing.touchTarget),
            )
          }
        }
      }
    }
  }
  }
}

/**
 * The expand control lives inside the capsule's action column and reveals
 * against its top edge; kept out of the bar's Row scope so the plain
 * [AnimatedVisibility] overload is used instead of the row-container one.
 */
@Composable
private fun SimpleComposerExpandControl(
  visible: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter =
      fadeIn(animationSpec = tween(140)) +
        scaleIn(
          initialScale = 0.55f,
          transformOrigin = TransformOrigin(0.5f, 0f),
          animationSpec = spring(dampingRatio = 0.72f, stiffness = 340f),
        ),
    exit =
      fadeOut(animationSpec = tween(90)) +
        scaleOut(
          targetScale = 0.55f,
          transformOrigin = TransformOrigin(0.5f, 0f),
          animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
        ),
  ) {
    Box(
      modifier =
        Modifier
          .size(SimpleComposerExpandButtonSize)
          .clip(CircleShape)
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            role = Role.Button,
            onClick = onClick,
          )
          .semantics {
            role = Role.Button
            contentDescription = nativeString("Expand input")
          },
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = MiuixIcons.Medium.MiuixExpandMore,
        contentDescription = null,
        tint = ClawTheme.colors.textMuted,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

/**
 * Header-button glass expressed through Haze: the header's 0.28/0.38 tint over
 * a shallow blur, with the shared rim highlight. Falls back to a translucent
 * fill when no transcript source is available (preview/tests).
 */
@Composable
private fun Modifier.simpleComposerGlass(
  shape: Shape,
  cornerRadius: Dp,
  hazeState: HazeState?,
): Modifier {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val fill = if (dark) colors.surfaceRaised else Color.White
  val tintAlpha = if (dark) 0.28f else 0.38f
  val canvas = colors.canvas
  val clipped = this.clip(shape)
  val glass =
    if (hazeState == null) {
      clipped.background(fill.copy(alpha = tintAlpha))
    } else {
      val style =
        remember(dark, fill, canvas) {
          HazeBlurStyle {
            blurEnabled(true)
            blurRadius(3.dp)
            noiseFactor(if (dark) 0.07f else 0.05f)
            backgroundColor(canvas)
            colorEffects(listOf(HazeColorEffect.tint(fill.copy(alpha = tintAlpha))))
            fallbackColorEffect(HazeColorEffect.tint(fill.copy(alpha = 0.92f)))
          }
        }
      clipped.hazeBlur(
        input = HazeInput.Sources(hazeState, selection = HazeSourceSelection.All),
        style = style,
      )
    }
  return glass.clawGlassEdge(cornerRadius = cornerRadius, dark = dark)
}
