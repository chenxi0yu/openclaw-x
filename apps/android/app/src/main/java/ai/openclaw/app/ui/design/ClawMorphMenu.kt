package ai.openclaw.app.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Same-layer morphing menu: one piece of glass grows out of the header's
 * actions capsule into the menu panel.
 *
 * Architecture (each point is a scar from the previous attempt):
 * - No `Popup`. A popup is a separate window: the backdrop cannot sample the
 *   shared transcript layer across the window boundary, and two windows cannot
 *   hand one glass surface back and forth. The host renders this in a pane-wide
 *   overlay slot placed above the header.
 * - The trigger capsule stays where it lives in the header — the real
 *   component, real clicks, real glass. This overlay only draws the growing
 *   glass anchored at the capsule's [boundsInRoot]; collapsed it draws nothing
 *   at all, so it can never swallow the capsule's clicks.
 * - Size, anchor migration, and exit bounce are three decoupled springs. The
 *   anchor migrates toward the panel center faster than the size grows, which
 *   is what makes the growth read as a morph instead of a corner zoom.
 * - The dismiss scrim exists only while expanded. It sits beside the glass in
 *   this overlay, never above the capsule's slot.
 *
 * Behavior language follows NexioSchedule's LiquidGlassDropdownMenu (AGPL-3.0,
 * studied for interaction design only — no code copied).
 */
internal object ClawMorphMenuDefaults {
  /**
   * Open panel width; height comes from the content measured at full size.
   *
   * Narrower than [ClawGlassMenuDefaults.MaxWidth]: the rows fill whatever width
   * they are given, so at 224dp a three-item chat menu is mostly empty space to
   * the right of four-glyph labels. The other glass menus are placed by a popup
   * that can shrink to its parent, this one cannot.
   */
  val PanelWidth: Dp = 168.dp

  /** Same family as [ClawGlassMenuDefaults.CornerRadius]. */
  val CornerRadius: Dp = ClawGlassMenuDefaults.CornerRadius

  /**
   * Blur ramps collapsed → open in 8 discrete steps. `drawBackdrop` re-runs
   * its effects when the effects lambda identity changes, so a per-frame blur
   * value would either pin a stale blur (same lambda) or rebuild the
   * RenderEffect every frame (new lambda). Quantizing gives 8 rebuilds total.
   */
  const val BlurSteps: Int = 8
  val CollapsedBlur: Dp = 8.dp

  /**
   * Open blur matches the other glass menus: the panel now carries the same
   * light tint they do, so it needs the same radius to keep the transcript
   * underneath from staying readable.
   */
  val OpenBlur: Dp = ClawGlassMenuDefaults.BlurRadius

  /**
   * Exit recoil travel, along the collapse direction. The glass fades out at
   * `fraction * 5`, so by the time the bounce spring overshoots the glass is
   * already invisible — the recoil only reads because the header capsule moves
   * by the same offset. Both sites must use this value or the capsule detaches
   * from the glass mid-recoil.
   */
  val BounceTravelDp: Dp = 12.dp
}

/**
 * Shared morph state: the overlay animates it, the trigger control reads it.
 * The trigger can live in a different slot from the overlay (the header capsule
 * does, the composer buttons do), so the geometry and the anchor both have to
 * come from here.
 */
internal class ClawMorphMenuState {
  val fraction: Animatable<Float, *> = Animatable(0f)

  /**
   * Exit recoil, driven by the overlay and read by the trigger. It cannot live
   * in the overlay: the trigger has to move with it.
   */
  val settleBounce: Animatable<Float, *> = Animatable(1f)

  var expanded by mutableStateOf(false)

  /** Panel natural height in px, published by the overlay's content measure. */
  var panelHeightPx by mutableIntStateOf(0)

  /**
   * Trigger bounds in root coordinates, published by [clawMorphTrigger] — or by
   * a long-press handler, for menus that grow out of the finger rather than out
   * of a control. The overlay converts them into its own space.
   */
  var anchor by mutableStateOf<Rect?>(null)

  /**
   * Which point of the panel stays glued to the matching point of the anchor:
   * `TransformOrigin(1f, 0f)` pins top-right (the header actions menu),
   * `TransformOrigin(0f, 1f)` pins bottom-left (the composer menus, which grow
   * up over their button). It also sets the direction the exit recoil overshoots.
   */
  var anchorOrigin by mutableStateOf(TransformOrigin(1f, 0f))

  /**
   * Menu body currently claimed by this host, or null while nothing is open.
   * Callers hand it to [open] so the items can stay where their data lives while
   * the glass is drawn by a single overlay higher up the tree.
   */
  var content by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null)

  /** Open panel width, set per menu by [open]. */
  var panelWidth by mutableStateOf(ClawMorphMenuDefaults.PanelWidth)

  /**
   * Surface tint alpha, set per menu. A menu that lands on top of another glass
   * surface (the composer menus over the input bar) raises this so the surface
   * below does not read through as a second, unsynced layer.
   */
  var tintAlpha by mutableFloatStateOf(-1f)

  /**
   * The control that currently owns this surface. Only it may glide, fade, or
   * recoil: a pane shares one morph surface across several triggers, and if they
   * all read `fraction` the ＋ button would drag the model picker along with it
   * and every other control would fade out when one menu opens.
   *
   * Stays set after [dismiss] so the owner keeps gliding back with the collapse;
   * the next [open] replaces it.
   */
  var owner by mutableStateOf<ClawMorphTrigger?>(null)

  /**
   * The overlay's own coordinates. Triggers convert their bounds into this node's
   * space with [LayoutCoordinates.localPositionOf] rather than through root
   * offsets: root coordinates are layout-only, so a subtree moved by a
   * `graphicsLayer` — the chat pane while the sidebar pushes it right — reported
   * a position that did not match where it was drawn, and the glass landed in the
   * wrong place.
   */
  var hostCoords by mutableStateOf<LayoutCoordinates?>(null)

  /** Claim the shared morph surface for one menu and open it at [anchor]. */
  fun open(
    origin: TransformOrigin = TransformOrigin(1f, 0f),
    panelWidth: Dp = ClawMorphMenuDefaults.PanelWidth,
    tintAlpha: Float = -1f,
    anchor: Rect? = null,
    content: @Composable ColumnScope.() -> Unit,
  ) {
    // A trigger that already published its bounds through [clawMorphTrigger]
    // omits `anchor`; a long-press menu passes the point under the finger.
    if (anchor != null) this.anchor = anchor
    this.anchorOrigin = origin
    this.panelWidth = panelWidth
    this.tintAlpha = tintAlpha
    this.content = content
    expanded = true
  }

  fun dismiss() {
    expanded = false
  }

  /** Release the claimed body once the glass is gone, so a closed menu holds no composable. */
  fun clearContent() {
    content = null
  }
}

/**
 * The pane's single morph surface, provided by whoever renders the
 * [ClawMorphMenuOverlay]. Any descendant can claim it with
 * [ClawMorphMenuState.open] — which is how menus that live deep inside a list (a
 * message bubble, a sidebar row) get the same morph without owning an overlay:
 * the overlay has to sit above everything it covers, and a scrolling list clips
 * anything placed inside it.
 */
internal val LocalClawMorphMenuHost = compositionLocalOf<ClawMorphMenuState?> { null }

@Composable
internal fun rememberClawMorphMenuState(): ClawMorphMenuState = remember { ClawMorphMenuState() }

/**
 * One control that can drive a shared [ClawMorphMenuState]. It owns its own
 * bounds and only moves while it holds the surface, so opening the attachment
 * menu cannot make the model picker bounce or fade.
 *
 * Build it where the menu body lives and pass [modifier] down to the control —
 * the trigger is a plain object, so a `ChatScreen`-level menu can hand its
 * capsule a trigger without threading anchors and flags through every layer.
 */
internal class ClawMorphTrigger internal constructor(val state: ClawMorphMenuState) {
  /** This control's bounds in root coordinates: the morph's collapsed shape. */
  var anchor by mutableStateOf<Rect?>(null)
    private set

  /**
   * Publishes the bounds only. For surfaces that must stay put while their menu
   * grows out of them — a long-pressed message bubble, a long-pressed sidebar row
   * — the card appearing on top of a message that flew away reads as the message
   * being deleted.
   */
  val anchorModifier: Modifier =
    Modifier.onGloballyPositioned { coords ->
      val host = state.hostCoords
      if (host == null || !host.isAttached || !coords.isAttached) {
        anchor = Rect(coords.positionInRoot(), Size(coords.size.width.toFloat(), coords.size.height.toFloat()))
        return@onGloballyPositioned
      }
      val topLeft = host.localPositionOf(coords, Offset.Zero)
      anchor = Rect(
          topLeft,
          Size(coords.size.width.toFloat(), coords.size.height.toFloat()),
        )
    }

  /**
   * Publishes the bounds and glides the control toward the growing panel while
   * fading it out on the same curve the glass fades in, so the swap reads as one
   * continuous element rather than two crossfading cards. Draw-phase reads only.
   */
  val modifier: Modifier =
    anchorModifier.graphicsLayer {
      if (state.owner !== this@ClawMorphTrigger) return@graphicsLayer
      val f = state.fraction.value
      // Enter clears fast (2.5x) so the control is gone before the glass covers
      // it; exit returns early (1.7x) so it is back before the glass is gone.
      val p = (f * if (state.expanded) 2.5f else 1.7f).coerceIn(0f, 1f)
      alpha = 1f - p
      val panelW = state.panelWidth.toPx()
      val panelH = if (state.panelHeightPx > 0) state.panelHeightPx.toFloat() else size.height
      val px = state.anchorOrigin.pivotFractionX
      val py = state.anchorOrigin.pivotFractionY
      // The glass fades at `fraction * 5`, so the exit recoil lands while the
      // glass is already invisible; this control is what is still on screen, so
      // it has to carry the same offset or the bounce is thrown away.
      val bouncePx =
        (-state.settleBounce.value).coerceAtLeast(0f) *
          ClawMorphMenuDefaults.BounceTravelDp.toPx()
      val sx = (0.5f - px) * 2f
      val sy = (0.5f - py) * 2f
      // Travel from this control's center to the panel's center, honoring the
      // pin corner: a bottom-left pin grows the panel up and to the right, so the
      // control has to follow right and up. The old width-lerp form assumed the
      // panel always grew leftward from the control's right edge, which threw
      // the composer buttons off screen.
      translationX = p * (px * (size.width - panelW) + panelW / 2f - size.width / 2f) - sx * bouncePx
      translationY =
        p * (py * (size.height - panelH) + panelH / 2f - size.height / 2f) - sy * bouncePx
    }

  /**
   * Open this control's menu on the shared surface. [overrideAnchor] replaces the
   * control's bounds — a long-press menu passes the point under the finger.
   * Returns false when nothing has laid this control out yet, so the caller can
   * skip rather than open a menu at a stale anchor.
   */
  fun open(
    origin: TransformOrigin = TransformOrigin(1f, 0f),
    panelWidth: Dp = ClawMorphMenuDefaults.PanelWidth,
    tintAlpha: Float = -1f,
    overrideAnchor: Rect? = null,
    content: @Composable ColumnScope.() -> Unit,
  ): Boolean {
    val rect = overrideAnchor ?: anchor ?: return false
    state.owner = this
    state.open(
      origin = origin,
      panelWidth = panelWidth,
      tintAlpha = tintAlpha,
      anchor = rect,
      content = content,
    )
    return true
  }

  fun dismiss() {
    state.dismiss()
  }
}

/**
 * A trigger for the pane's morph surface. Passes through [rememberClawMorphTrigger]
 * on the state it resolves to, so a control rendered without an overlay around it
 * (a composer preview) still composes — its menu simply has nothing to draw it.
 */
@Composable
internal fun clawMorphTrigger(host: ClawMorphMenuState?): ClawMorphTrigger {
  val standalone = remember { ClawMorphMenuState() }
  return rememberClawMorphTrigger(host ?: standalone)
}

@Composable
internal fun rememberClawMorphTrigger(state: ClawMorphMenuState): ClawMorphTrigger =
  remember(state) { ClawMorphTrigger(state) }

/**
 * Pane-wide overlay host for a morph menu. Render from a root-level overlay slot
 * above the surface it samples, and hand the same [state] to every menu that
 * wants in (see [LocalClawMorphMenuHost]). It draws nothing while collapsed and
 * is fully click-through.
 *
 * @param state carries the anchor, the pin corner, the panel width, the tint and
 *   the claimed body — everything [ClawMorphMenuState.open] recorded.
 * @param backdrop the layer the glass samples.
 */
@Composable
internal fun ClawMorphMenuOverlay(
  state: ClawMorphMenuState,
  onDismissRequest: () -> Unit,
  backdrop: Backdrop?,
  modifier: Modifier = Modifier,
) {
  val anchor = state.anchor
  val expanded = state.expanded
  val fraction = state.fraction
  val originProgress = remember { Animatable(0f) }
  val settleBounce = state.settleBounce
  // First-frame guard: playing the exit animation before any open happened
  // would nudge the glass sideways on first composition.
  var hasOpened by remember { mutableStateOf(false) }
  val currentOnDismiss by rememberUpdatedState(onDismissRequest)

  LaunchedEffect(expanded) {
    if (expanded) {
      hasOpened = true
      launch {
        fraction.animateTo(
          1f,
          spring(dampingRatio = 0.77f, stiffness = 220f, visibilityThreshold = 0.0001f),
        )
      }
      launch {
        originProgress.animateTo(
          1f,
          // Stiffer than the size spring: the anchor reaches the panel center
          // while the glass is still small — that is the morph's read.
          spring(dampingRatio = 0.77f, stiffness = 420f, visibilityThreshold = 0.0001f),
        )
      }
      // Drive the bounce back rather than snapping it. Only values below zero
      // displace the glass, so from a settled open this is a no-op visually —
      // but when the collapse is interrupted mid-overshoot a snap would teleport
      // the glass back by up to the full bounce offset, which is exactly the
      // jump that makes an interrupted collapse read as a new animation.
      launch {
        settleBounce.animateTo(1f, spring(dampingRatio = 0.77f, stiffness = 420f))
      }
    } else if (hasOpened) {
      val exitEasing = CubicBezierEasing(0.0f, 0.0f, 0.0f, 1.0f)
      launch {
        fraction.animateTo(
          0f,
          spring(dampingRatio = 0.85f, stiffness = 650f, visibilityThreshold = 0.0001f),
        )
      }
      // Exit bounce: its own under-damped spring that overshoots past zero;
      // only the overshoot displaces the glass along the collapse direction.
      launch {
        settleBounce.snapTo(1f)
        settleBounce.animateTo(
          0f,
          spring(dampingRatio = 0.52f, stiffness = 220f, visibilityThreshold = 0.0001f),
        )
      }
      // Wait for the springs instead of snapping: an early snap cancels them
      // mid-flight and the glass teleports to the capsule.
      originProgress.animateTo(0f, tween(340, easing = exitEasing))
      fraction.snapTo(0f)
      originProgress.snapTo(0f)
    }
  }

  // Back closes the menu. Registered only while expanded so a collapsed menu
  // never competes with the shell's own back handling.
  if (expanded) {
    BackHandler(onBack = { currentOnDismiss() })
  }

  // Early-out only when fully settled: the exit springs land at zero
  // asynchronously and the bounce spring overshoots *negative*, so a bare
  // `< 0.001` test would unmount the glass mid-recoil. abs() on both.
  val settled =
    !hasOpened ||
      (!expanded && abs(fraction.value) < 0.001f && abs(settleBounce.value) < 0.001f)

  // The claimed body is only needed while the glass is on screen. Dropping it
  // once settled stops a closed menu from keeping its caller's composables and
  // their captured state alive.
  LaunchedEffect(settled) {
    if (settled) state.clearContent()
  }

  Box(
    modifier =
      modifier.fillMaxSize().onGloballyPositioned { state.hostCoords = it },
  ) {
    val content = state.content
    if (!settled && anchor != null && backdrop != null && content != null) {
      if (expanded) {
        // Dismiss scrim: beside the glass, only while expanded. It dismisses on
        // the press itself rather than on release — a drawer swipe that starts
        // under it is an ancestor gesture that never reaches a click, so a
        // release-based dismiss left the menu open while the pane slid away.
        Box(
          modifier =
            Modifier
              .matchParentSize()
              .pointerInput(Unit) {
                awaitEachGesture {
                  awaitFirstDown(requireUnconsumed = false)
                  currentOnDismiss()
                  // Swallow subsequent gestures for this event sequence so the
                  // press does not also trigger whatever is behind the menu.
                  waitForUpOrCancellation()
                }
              },
        )
      }
      ClawMorphMenuGlass(
        anchor = anchor,

        state = state,
        fraction = fraction.value,
        originProgress = originProgress.value,
        settleBounce = settleBounce.value,
        backdrop = backdrop,
        panelWidth = state.panelWidth,
        onDismissRequest = { currentOnDismiss() },
        content = content,
      )
    }
  }
}

@Composable
private fun ClawMorphMenuGlass(
  anchor: Rect,

  state: ClawMorphMenuState,
  fraction: Float,
  originProgress: Float,
  settleBounce: Float,
  backdrop: Backdrop,
  panelWidth: Dp,
  onDismissRequest: () -> Unit,
  content: @Composable ColumnScope.() -> Unit,
) {
  val density = LocalDensity.current
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val shape = RoundedCornerShape(ClawMorphMenuDefaults.CornerRadius)
  // Which point of the panel is glued to the matching point of the anchor. It
  // also decides which way the morph travels and which way the recoil overshoots.
  val pivotX = state.anchorOrigin.pivotFractionX
  val pivotY = state.anchorOrigin.pivotFractionY
  val sx = (0.5f - pivotX) * 2f
  val sy = (0.5f - pivotY) * 2f

  // Panel natural height: the content reports it measured at full panel size,
  // published to the shared state so the trigger can interpolate with the same
  // geometry. Until then the trigger height keeps the first open sane.
  val panelHeightPx = state.panelHeightPx
  val targetHeight =
    with(density) { if (panelHeightPx > 0) panelHeightPx.toDp() else anchor.height.toDp() }

  // Interpolation starts at the trigger's *measured* size (from the anchor), so
  // the glass covers it exactly at fraction 0 whatever the layout does with it.
  val collapsedWidth = with(density) { anchor.width.toDp() }
  val collapsedHeight = with(density) { anchor.height.toDp() }
  val width = lerp(collapsedWidth, panelWidth, fraction)
  val height = lerp(collapsedHeight, targetHeight, fraction)
  // Content scales with the container so items emerge from the trigger sized
  // up, matching the glass growth instead of popping in at full size.
  val contentScale = (width / panelWidth).coerceAtLeast(0f)
  // Clear at both ends, blurred mid-morph: reinforces the glass re-forming.
  val contentBlur = 6.dp * (1f - abs(2f * fraction - 1f)).coerceIn(0f, 1f)
  // Material appears early and disappears late (saturates at f=0.2).
  val materialVisible = (fraction * 5f).coerceIn(0f, 1f)

  // Quantized blur ramp (see Defaults for why the steps must be discrete).
  val blurStep = (fraction.coerceIn(0f, 1f) * ClawMorphMenuDefaults.BlurSteps).roundToInt()
  val blurDp =
    lerp(
      ClawMorphMenuDefaults.CollapsedBlur,
      ClawMorphMenuDefaults.OpenBlur,
      blurStep.toFloat() / ClawMorphMenuDefaults.BlurSteps,
    )
  // Remembered on the quantized step: the identity change is what makes
  // drawBackdrop rebuild its RenderEffect, and it happens 8 times, not 60.
  val glassEffects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
    remember(blurStep) {
      {
        vibrancy()
        blur(blurDp.toPx())
        // Constant lens on the edge field: growing it with the panel would
        // distort the edge as the surface expands.
        lens(8.dp.toPx(), 24.dp.toPx())
      }
    }

  // Press/drag lives on the panel only while it is open. Collapsed, the glass
  // sits exactly on the trigger capsule, which has its own press state — both
  // observers are non-consuming, so leaving this one armed would stack two
  // squishes on the same surface during the handover.
  val pressDrag = rememberClawPressDrag(glow = dark)

  Box(
    modifier =
      Modifier
        .offset {
          // Glue the panel's pivot point to the anchor's matching point.
          IntOffset(
            (anchor.left + pivotX * (anchor.width - width.toPx())).roundToInt(),
            (anchor.top + pivotY * (anchor.height - height.toPx())).roundToInt(),
          )
        }
        .graphicsLayer {
          val panelW = panelWidth.toPx()
          val targetH = targetHeight.toPx()
          // Anchor migration, zero at both ends: with the pivot pinned, this
          // shifts the box so it scales about the *final panel center* once
          // originProgress has raced ahead.
          val k = 0.5f * originProgress * (1f - size.width / panelW)
          // Exit bounce: only the overshoot below zero displaces, and it
          // overshoots toward the pinned corner — the collapse direction.
          val pulse = (-settleBounce).coerceAtLeast(0f)
          val bouncePx = pulse * ClawMorphMenuDefaults.BounceTravelDp.toPx()
          translationX = sx * (panelW * k - bouncePx)
          translationY = sy * (targetH * k - bouncePx)
        }
        // Its own layer, nested inside the migration layer: the two transforms
        // compose, and the press squish scales the clipped silhouette with it.
        // A little more swell than a 48dp disc because the delta is a fixed
        // distance, and less follow because a 168dp sheet has room to travel.
        .then(
          if (state.expanded) {
            Modifier.clawPressDrag(pressDrag, pressTravel = 6.dp, dragResponse = 0.14f)
          } else {
            Modifier
          },
        )
        .size(width, height)
        .clip(shape)
        .then(
          if (state.expanded) {
            pressDrag.modifier.then(pressDrag.gestureModifier)
          } else {
            Modifier
          },
        ),
  ) {
    Box(
      modifier =
        Modifier
          .matchParentSize()
          .graphicsLayer { alpha = materialVisible }
          .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = glassEffects,
            // The same material the other glass menus use: blurred backdrop +
            // a 0.26/0.30 tint. A chat transcript is white paper with black
            // ink, so a heavy surface fill (this started at 0.78, copied from
            // Nexio's colourful course cards) flattens the sample into a plain
            // white card and the panel reads as opaque paper instead of glass.
            // Blur carries the legibility; the tint only removes the last of
            // the structure.
            onDrawSurface = {
              // A menu that lands on another glass surface raises this via
              // ClawMorphMenuState.open so the surface below does not read
              // through as a second, unsynced layer.
              val alpha =
                if (state.tintAlpha >= 0f) {
                  state.tintAlpha
                } else if (dark) {
                  ClawGlassMenuDefaults.TintAlphaDark
                } else {
                  ClawGlassMenuDefaults.TintAlphaLight
                }
              drawRect(
                (if (dark) colors.surfaceRaised else androidx.compose.ui.graphics.Color.White)
                  .copy(alpha = alpha),
              )
            },
          )
          // Rim + inner highlight: without them a lightly tinted panel over a
          // light canvas has no silhouette at all. After `drawBackdrop` so the
          // strokes land on top of the glass, not under it.
          .clawGlassEdge(cornerRadius = ClawMorphMenuDefaults.CornerRadius, dark = dark),
    )
    // Menu content: measured at full panel width so the natural height is the
    // real one, centered, then scaled to the current glass size and clipped.
    Column(
      modifier =
        Modifier
          .align(Alignment.Center)
          .requiredWidth(panelWidth)
          .wrapContentHeight(align = Alignment.CenterVertically, unbounded = true)
          .onSizeChanged { size: IntSize -> state.panelHeightPx = size.height }
          .graphicsLayer {
            alpha = ((fraction - 0.3f) / 0.4f).coerceIn(0f, 1f)
            scaleX = contentScale
            scaleY = contentScale
            transformOrigin = TransformOrigin(0.5f, 0.5f)
          }
          .blur(contentBlur)
          .padding(vertical = 8.dp),
    ) {
      CompositionLocalProvider(
        LocalClawGlassMenuDismiss provides onDismissRequest,
        // Items stay non-interactive while the menu is closing: the overlay
        // outlives the visible animation (the springs settle asynchronously),
        // and invisible items must not swallow taps aimed at the capsule.
        LocalClawGlassMenuInteractive provides state.expanded,
      ) {
        content()
      }
    }
  }
}
