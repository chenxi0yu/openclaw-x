package ai.openclaw.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Shared geometry and motion for the floating action menu.
 *
 * A frosted card: the panel blurs whatever [ClawGlassHost] marked behind it, with
 * one soft shadow, a hairline on the silhouette and a bright highlight along the
 * top inner edge. When no host was marked it degrades to the same shape drawn as
 * an opaque sheet. Every screen that needs a floating menu should reuse
 * [ClawGlassMenu] instead of hand-rolling a `DropdownMenu` so the motion and the
 * spacing stay identical across the app.
 */
internal object ClawGlassMenuDefaults {
  // The panel hugs its widest row instead of stretching across the sidebar.
  val MinWidth: Dp = 128.dp
  val MaxWidth: Dp = 224.dp
  // Matches the composer capsule so the floating cards and the input bar read as
  // one family of glass surfaces.
  val CornerRadius: Dp = 26.dp
  val ItemHeight: Dp = 40.dp
  val ItemShapeRadius: Dp = 11.dp
  /** Backdrop blur strength. Big enough that text behind the card stops being readable. */
  val BlurRadius: Dp = 32.dp
  /**
   * How much of the blurred backdrop the tint hides. Lower = more see-through:
   * the whole point of the card is that colours and shapes bleed through it, and
   * [BlurRadius] alone is what keeps the text unreadable.
   */
  const val TintAlphaLight: Float = 0.30f
  const val TintAlphaDark: Float = 0.26f
  /**
   * Fill painted under the glass tint. The default keeps menus airy; a menu
   * that floats directly over another glass surface (the composer capsule)
   * passes a higher value so the surface below stays hidden instead of
   * showing through as a second, unsynced layer.
   */
  const val FillAlpha: Float = 0.58f
  /**
   * Enter fade. Deliberately much faster than the scale spring: the panel is
   * translucent, so while the fade is still running the surface underneath
   * shows through and the pop reads as two stacked layers. Reaching full
   * opacity early leaves the spring to carry the motion on its own.
   */
  const val EnterMillis: Int = 80
  // Exit fade is short — the view has already settled and the popup stays alive
  // until the exit transition finishes, so a longer spec would just stall
  // the next open.
  const val ExitMillis: Int = 110
  // "Grow from the center with a spring": the panel starts much smaller, flies
  // through 1f with visible overshoot and settles back — a bouncy pop.
  const val EnterFromScale: Float = 0.62f
  const val ExitToScale: Float = 0.85f
  /**
   * Under-damped, medium-soft spring for the enter scale. The bounce has to be
   * readable on a ~200dp panel: a short 0.14 travel (the old 0.86 start) kept
   * the overshoot under half a percent of the size, which read as plain ease.
   */
  const val EnterSpringDamping: Float = 0.52f
  const val EnterSpringStiffness: Float = 380f
}

/**
 * Lets a menu item close the menu it belongs to without every call site threading
 * a dismiss callback through its own state. Items read it through [ClawGlassMenuItem],
 * which closes the menu before running the caller's action.
 */
internal val LocalClawGlassMenuDismiss = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * A rounded-rectangle floating menu anchored to the composable it is called from.
 *
 * Content is placed in a [Popup] so the menu can escape the sidebar's scrolling
 * container and its rounded clipping, and so that taps outside the panel, focus
 * loss and back-press all dismiss it without extra wiring.
 *
 * The entrance animation uses a spring-driven scale from a configurable
 * [transformOrigin] with `clip = true` on the render layer. This clips the
 * spring overshoot (scale > 1) to the panel's layout bounds, eliminating the
 * directional white-edge artifact that plain [AnimatedVisibility] produces
 * through Haze's unclipped RenderEffect layer.
 */
@Composable
internal fun ClawGlassMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  offset: DpOffset = DpOffset(0.dp, 0.dp),
  focusable: Boolean = true,
  /**
   * Corner the scale animation grows from.
   *
   * - `Center` — the menu scales in around its own centre (every other menu).
   * - `(0, 1)` — bottom‑left, for a menu that sprouts upward from a left‑hand
   *   button (the + attachment button).
   * - `(1, 0)` — top‑right, for a menu that sprouts downward from a right‑hand
   *   button (the ⋯ chat‑action button).
   */
  transformOrigin: TransformOrigin = TransformOrigin.Center,
  /**
   * Point the gesture happened at, in the anchor's own coordinates. When it is
   * set the panel opens at that point (the finger) instead of at a fixed corner
   * of the anchor; [offset] still supplies the gap around it. `null` keeps the
   * anchor-relative placement, which is what icon-button callers want.
   */
  pressOffset: Offset? = null,
  /**
   * Backdrop to blur. Callers that already own a source (a screen that marked
   * itself with `hazeSource`) pass it here; everyone else inherits
   * [LocalClawHazeState] from the nearest [ClawGlassHost].
   */
  hazeState: HazeState? = null,
  /**
   * Optional blur ramp across the card (0 intensity = no blur, 1 = full
   * [ClawGlassMenuDefaults.BlurRadius]). On Android 13+ this runs as a single
   * runtime-shader pass; on Android 12 it degrades to a stack of layers, which
   * is why it stays opt-in.
   */
  progressive: HazeProgressive? = null,
  /**
   * Fill under the glass tint. Raise it for a menu that covers another glass
   * surface (the composer capsule): at the airy default the covered surface
   * stays visible through the panel and the pop reads as two stacked layers.
   */
  fillAlpha: Float = ClawGlassMenuDefaults.FillAlpha,
  content: @Composable ColumnScope.() -> Unit,
) {
  val scale = remember { Animatable(ClawGlassMenuDefaults.EnterFromScale) }
  val alpha = remember { Animatable(0f) }
  var showContent by remember { mutableStateOf(expanded) }

  LaunchedEffect(expanded) {
    if (expanded) {
      showContent = true
      // Start both animations in parallel so fade and scale run concurrently.
      coroutineScope {
        launch {
          alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(
              durationMillis = ClawGlassMenuDefaults.EnterMillis,
              easing = LinearOutSlowInEasing,
            ),
          )
        }
        launch {
          scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
              dampingRatio = ClawGlassMenuDefaults.EnterSpringDamping,
              stiffness = ClawGlassMenuDefaults.EnterSpringStiffness,
            ),
          )
        }
      }
    } else {
      coroutineScope {
        launch {
          alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(
              durationMillis = ClawGlassMenuDefaults.ExitMillis,
              easing = LinearOutSlowInEasing,
            ),
          )
        }
        launch {
          scale.animateTo(
            targetValue = ClawGlassMenuDefaults.ExitToScale,
            animationSpec = tween(
              durationMillis = ClawGlassMenuDefaults.ExitMillis,
              easing = LinearOutSlowInEasing,
            ),
          )
        }
      }
      showContent = false
    }
  }

  if (!showContent) return

  val density = LocalDensity.current
  val positionProvider =
    remember(offset, density, pressOffset) { ClawGlassMenuPositionProvider(offset, density, pressOffset) }

  Popup(
    popupPositionProvider = positionProvider,
    onDismissRequest = onDismissRequest,
    properties = PopupProperties(focusable = focusable),
  ) {
    Box(
      modifier =
        modifier.graphicsLayer {
          scaleX = scale.value
          scaleY = scale.value
          this.alpha = alpha.value
          this.transformOrigin = transformOrigin
          // Clip the spring overshoot to the panel's layout bounds: when
          // dampingRatio < 1 the spring exceeds 1f briefly, and without
          // clipping the card's Haze RenderEffect output paints beyond the
          // visible boundary as a translucent white edge.
          clip = true
        },
    ) {
      ClawGlassSurface(
        hazeState = hazeState,
        progressive = progressive,
        fillAlpha = fillAlpha,
        modifier =
          Modifier
            .widthIn(
              min = ClawGlassMenuDefaults.MinWidth,
              max = ClawGlassMenuDefaults.MaxWidth,
            )
            // Hugs the widest row; the min/max above only clamp the result.
            .width(IntrinsicSize.Max),
      ) {
        CompositionLocalProvider(LocalClawGlassMenuDismiss provides onDismissRequest) {
          Column(modifier = Modifier.fillMaxWidth(), content = content)
        }
      }
    }
  }
}

/**
 * Frosted rounded card used as the body of [ClawGlassMenu].
 *
 * When a [ClawGlassHost] is above it in the tree the panel blurs that backdrop
 * and washes it with a translucent tint; otherwise it draws the same shape as an
 * opaque sheet. Either way the hairline and the top highlight keep it reading as
 * a lifted layer rather than a flat block.
 */
@Composable
internal fun ClawGlassSurface(
  modifier: Modifier = Modifier,
  cornerRadius: Dp = ClawGlassMenuDefaults.CornerRadius,
  contentPadding: PaddingValues = PaddingValues(vertical = 7.dp),
  hazeState: HazeState? = null,
  progressive: HazeProgressive? = null,
  fillAlpha: Float = 0.58f,
  content: @Composable BoxScope.() -> Unit,
) {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val backdrop = hazeState ?: LocalClawHazeState.current
  val shape = RoundedCornerShape(cornerRadius)
  val fill = if (dark) colors.surfaceRaised else Color.White
  val outerStroke = if (dark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
  // Hairline-bright edge highlight: thin stroke, restrained alpha. The previous
  // wide bright stroke read as a fat glowing rim rather than a bevel.
  val highlight = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.45f)
  val blurStyle = rememberClawGlassBlurStyle(
    dark = dark,
    fill = fill,
    backdropBase = colors.canvas,
    progressive = progressive,
  )

  Box(
    modifier =
      modifier
        .clip(shape)
        .then(
          if (backdrop == null) {
            Modifier.background(fill.copy(alpha = fillAlpha.coerceIn(0f, 1f)))
          } else {
            // An almost-opaque floor under the blur. It is completely hidden as
            // soon as the effect has a frame, and it is what keeps the card (and
            // its labels) on screen if the backdrop capture never produces one.
            Modifier
              .background(fill.copy(alpha = fillAlpha.coerceIn(0f, 1f)))
              .hazeBlur(
                input = HazeInput.Sources(backdrop, selection = HazeSourceSelection.All),
                style = blurStyle,
              )
          },
        )
        .clawGlassEdge(cornerRadius = cornerRadius, outer = outerStroke, highlight = highlight)
        .padding(contentPadding),
    content = content,
  )
}

/**
 * Blur program for the menu card.
 *
 * `backgroundColor` is the fix for the card looking merely translucent: Haze
 * captures the source sub-tree, and that sub-tree never paints the app canvas
 * (an ancestor does). Every pixel it leaves unpainted stays transparent through
 * the blur, so the card used to composite mostly nothing over the real screen.
 * Filling the capture with the canvas colour gives the blur something to smear
 * and makes the panel opaque, which is what reads as frosted glass.
 *
 * The tint listed in `colorEffects` is applied on top of the blur, so it is the
 * knob that decides how frosted the card is. `fallbackColorEffect` covers
 * devices where no blur delegate exists at all — without it the card would draw
 * nothing and vanish.
 */
@Composable
private fun rememberClawGlassBlurStyle(
  dark: Boolean,
  fill: Color,
  backdropBase: Color,
  progressive: HazeProgressive?,
): HazeBlurStyle =
  remember(dark, fill, backdropBase, progressive) {
    HazeBlurStyle {
      blurEnabled(true)
      blurRadius(ClawGlassMenuDefaults.BlurRadius)
      // Noise makes the panel look dirty and kills the sense of depth, so it stays low.
      noiseFactor(if (dark) 0.07f else 0.05f)
      backgroundColor(backdropBase)
      colorEffects(
        listOf(
          HazeColorEffect.tint(
            fill.copy(
              alpha =
                if (dark) {
                  ClawGlassMenuDefaults.TintAlphaDark
                } else {
                  ClawGlassMenuDefaults.TintAlphaLight
                },
            ),
          ),
        ),
      )
      fallbackColorEffect(HazeColorEffect.tint(fill.copy(alpha = 0.98f)))
      progressive(progressive)
    }
  }

/**
 * Draws the two strokes that lift the card off the canvas: a hairline on the
 * silhouette and a brighter inner highlight concentrated near the top edge.
 */
private fun Modifier.clawGlassEdge(
  cornerRadius: Dp,
  outer: Color,
  highlight: Color,
): Modifier =
  drawBehind {
    val outerWidth = 1.dp.toPx()
    drawRoundRect(
      color = outer,
      topLeft = Offset(outerWidth / 2f, outerWidth / 2f),
      size = Size(size.width - outerWidth, size.height - outerWidth),
      cornerRadius = CornerRadius((cornerRadius.toPx() - outerWidth / 2f).coerceAtLeast(0f)),
      style = Stroke(outerWidth),
    )
    val inset = outerWidth * 1.5f
    drawRoundRect(
      brush =
        Brush.verticalGradient(
          0f to highlight,
          0.22f to highlight.copy(alpha = highlight.alpha * 0.38f),
          0.6f to Color.Transparent,
          1f to Color.Transparent,
        ),
      topLeft = Offset(inset, inset),
      size = Size(size.width - inset * 2f, size.height - inset * 2f),
      cornerRadius = CornerRadius((cornerRadius.toPx() - inset).coerceAtLeast(0f)),
      style = Stroke(1.dp.toPx()),
    )
  }

/**
 * One row of a [ClawGlassMenu].
 */
@Composable
internal fun ClawGlassMenuItem(
  label: String,
  onClick: () -> Unit,
  icon: ImageVector? = null,
  trailing: String? = null,
  selected: Boolean = false,
  danger: Boolean = false,
  enabled: Boolean = true,
) {
  val colors = ClawTheme.colors
  val dismissMenu = LocalClawGlassMenuDismiss.current
  val contentColor =
    when {
      !enabled -> colors.textSubtle
      danger -> colors.danger
      selected -> colors.primary
      else -> colors.text
    }
  val shape = RoundedCornerShape(ClawGlassMenuDefaults.ItemShapeRadius)
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = ClawGlassMenuDefaults.ItemHeight)
        .padding(horizontal = 6.dp)
        .clip(shape)
        .background(if (selected) colors.accentSoft else Color.Transparent)
        .clickable(
          enabled = enabled,
          role = Role.Button,
          onClick = {
            dismissMenu()
            onClick()
          },
        ).padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    if (icon != null) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = contentColor,
        modifier = Modifier.size(18.dp),
      )
    }
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp),
      color = contentColor,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    if (trailing != null) {
      Text(
        text = trailing,
        style = ClawTheme.type.caption,
        color = colors.textSubtle,
        maxLines = 1,
      )
    }
    if (selected) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = null,
        tint = colors.primary,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

/**
 * Menu placement.
 *
 * With a press point: centred on the finger, sitting above it when there is
 * room (the finger covers what is below it) and flipped below otherwise, then
 * clamped inside the window.
 *
 * Without one: aligned to the anchor's trailing edge, below it when there is
 * room, otherwise flipped above, and finally clamped inside the window.
 */
private class ClawGlassMenuPositionProvider(
  private val offset: DpOffset,
  private val density: Density,
  private val pressOffset: Offset? = null,
) : PopupPositionProvider {
  override fun calculatePosition(
    anchorBounds: IntRect,
    windowSize: IntSize,
    layoutDirection: LayoutDirection,
    popupContentSize: IntSize,
  ): IntOffset {
    val offsetX = with(density) { offset.x.roundToPx() }
    val offsetY = with(density) { offset.y.roundToPx() }
    val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
    val maxY = (windowSize.height - popupContentSize.height).coerceAtLeast(0)

    if (pressOffset != null) {
      val pressX = anchorBounds.left + pressOffset.x.toInt()
      val pressY = anchorBounds.top + pressOffset.y.toInt()
      val x = pressX - popupContentSize.width / 2
      var y = pressY - popupContentSize.height - offsetY
      if (y < 0) y = pressY + offsetY
      return IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
    }

    // The panel hangs off the anchor's trailing edge: long presses land on the
    // right side of a row, and a right-aligned card sits under the finger.
    val trailingAligned =
      if (layoutDirection == LayoutDirection.Ltr) {
        anchorBounds.right - popupContentSize.width
      } else {
        anchorBounds.left
      }
    var x = trailingAligned - offsetX
    if (x < 0) {
      // Narrow anchors near the start edge (the composer's add button, for one)
      // have no room to hang leftwards, which would clamp the card to x=0 and
      // detach it from its anchor. Flip to the leading edge instead.
      x =
        (
          if (layoutDirection == LayoutDirection.Ltr) {
            anchorBounds.left
          } else {
            anchorBounds.right - popupContentSize.width
          }
          ) + offsetX
    }

    var y = anchorBounds.bottom + offsetY
    if (y > maxY) {
      val above = anchorBounds.top - popupContentSize.height - offsetY
      if (above >= 0) y = above
    }

    return IntOffset(x.coerceIn(0, maxX), y.coerceIn(0, maxY))
  }
}