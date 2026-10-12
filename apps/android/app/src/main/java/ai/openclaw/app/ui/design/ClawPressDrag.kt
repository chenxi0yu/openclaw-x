// Portions of this file are derived from AndroidLiquidGlass
// (https://github.com/Kyant0/AndroidLiquidGlass), catalog sources
// `utils/InteractiveHighlight.kt`, `utils/DragGestureInspector.kt` and
// `components/LiquidButton.kt`:
//   Copyright 2025 Kyant
//   Licensed under the Apache License, Version 2.0
package ai.openclaw.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tanh
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Press-and-drag state for one glass surface: how hard it is being held, and how
 * far the finger has travelled from where it landed.
 *
 * The gesture half never consumes anything, so it sits *beside* a normal
 * `clickable` rather than replacing it — the surface keeps its tap behavior and
 * gains follow-the-finger motion. The glow half draws over the glass and under
 * the children, which is why both are applied late in the modifier chain.
 *
 * @param radiusBase glow radius cap, in dp. The radius is
 *   `min(radiusBase, minDimension * 1.5)`: small surfaces get a glow sized to
 *   cover them (upstream's rule), while a tall panel is capped so its glow does
 *   not scale with its height.
 * @param glow whether to paint the press light at all. An additive white glow is
 *   the upstream behavior and reads well on dark glass; over light glass the
 *   surface is already near white, so it only smudges. Light themes carry the
 *   press on the squish alone.
 */
internal class ClawPressDrag(
  private val animationScope: CoroutineScope,
  private val radiusBase: Dp,
  private val glow: Boolean,
) {
  private val pressSpring =
    spring<Float>(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
  private val positionSpring =
    spring(
      dampingRatio = 0.5f,
      stiffness = 300f,
      visibilityThreshold = Offset.VisibilityThreshold,
    )

  private val pressAnimation = Animatable(0f, 0.001f)
  private val positionAnimation =
    Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)
  private var startPosition = Offset.Zero

  /** 0 → 1 while held. Underdamped, so it overshoots for a live squish. */
  val pressProgress: Float get() = pressAnimation.value

  /** Finger displacement from where it landed, in this surface's coordinates. */
  val offset: Offset get() = positionAnimation.value - startPosition

  private val shader: RuntimeShader? =
    if (glow && isRuntimeShaderSupported()) {
      RuntimeShader(
        """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;

half4 main(float2 coord) {
    float dist = distance(coord, position);
    float intensity = smoothstep(radius, radius * 0.5, dist);
    return color * intensity;
}""",
      )
    } else {
      null
    }

  /** Radial light that tracks the finger. Apply after the glass, before content. */
  val modifier: Modifier =
    if (!glow) {
      Modifier
    } else {
      Modifier.drawWithContent {
        val progress = pressAnimation.value
        if (progress > 0f) {
          if (shader != null) {
            drawRect(Color.White.copy(0.08f * progress), blendMode = BlendMode.Plus)
            shader.apply {
              setFloatUniform("size", size.width, size.height)
              setColorUniform("color", Color.White.copy(0.15f * progress))
              setFloatUniform("radius", radiusBase.toPx().coerceAtMost(size.minDimension * 1.5f))
              setFloatUniform(
                "position",
                positionAnimation.value.x.fastCoerceIn(0f, size.width),
                positionAnimation.value.y.fastCoerceIn(0f, size.height),
              )
            }
            drawRect(ShaderBrush(shader.asComposeShader()), blendMode = BlendMode.Plus)
          } else {
            drawRect(Color.White.copy(0.25f * progress), blendMode = BlendMode.Plus)
          }
        }
        drawContent()
      }
    }

  val gestureModifier: Modifier =
    Modifier.pointerInput(animationScope) {
      inspectDragGestures(
        onDragStart = { down ->
          startPosition = down.position
          animationScope.launch {
            launch { pressAnimation.animateTo(1f, pressSpring) }
            launch { positionAnimation.snapTo(startPosition) }
          }
        },
        onDragEnd = { springHome() },
        onDragCancel = { springHome() },
      ) { change, _ ->
        animationScope.launch { positionAnimation.snapTo(change.position) }
      }
    }

  private fun springHome() {
    animationScope.launch {
      launch { pressAnimation.animateTo(0f, pressSpring) }
      launch { positionAnimation.animateTo(startPosition, positionSpring) }
    }
  }
}

@Composable
internal fun rememberClawPressDrag(
  radiusBase: Dp = 150.dp,
  glow: Boolean = true,
): ClawPressDrag {
  val animationScope = rememberCoroutineScope()
  return remember(animationScope, radiusBase, glow) {
    ClawPressDrag(animationScope, radiusBase, glow)
  }
}

/**
 * Apply the press transform to a whole node. Put it *before* the shadow and the
 * glass so the entire surface — elevation included — swells and slides together.
 *
 * @param pressTravel how far the surface grows at full press. Both the swell and
 *   the drag stretch are expressed in this one distance, so the *pixel* delta is
 *   the same on a 48dp disc and a 136dp panel; a large surface needs a larger
 *   value to read as strongly.
 * @param dragResponse how much of the finger's travel the surface follows.
 *   Upstream ships 0.05, which is a parallax hint rather than a follow.
 */
@Composable
internal fun Modifier.clawPressDrag(
  pressDrag: ClawPressDrag,
  pressTravel: Dp = 4.dp,
  dragResponse: Float = 0.16f,
): Modifier =
  graphicsLayer {
    applyClawPressDrag(pressDrag.pressProgress, pressDrag.offset, pressTravel, dragResponse)
  }

/**
 * Pressing does not shrink the surface — it swells slightly and stretches along
 * the drag axis, which is what reads as soft material rather than a pressed
 * bitmap. Translation is `tanh`-damped so the surface keeps answering a long
 * drag without ever racing the finger.
 *
 * Written against `GraphicsLayerScope` so one copy serves both a plain layer and
 * a `drawBackdrop` `layerBlock`.
 */
internal fun GraphicsLayerScope.applyClawPressDrag(
  pressProgress: Float,
  offset: Offset,
  pressTravel: Dp = 4.dp,
  dragResponse: Float = 0.16f,
) {
  if (pressProgress <= 0f && offset == Offset.Zero) return
  val width = size.width
  val height = size.height
  if (width <= 0f || height <= 0f) return

  val travel = pressTravel.toPx()
  val pressScale = lerp(1f, 1f + travel / height, pressProgress)

  val maxOffset = size.minDimension
  translationX = maxOffset * tanh(dragResponse * offset.x / maxOffset)
  translationY = maxOffset * tanh(dragResponse * offset.y / maxOffset)

  // Stretch only along the axis the finger actually moved, damped for elongated
  // surfaces: a wide row dragged sideways must not grow as far as a round button
  // dragged the same distance.
  val angle = atan2(offset.y, offset.x)
  scaleX =
    pressScale +
      travel / height * abs(cos(angle) * offset.x / size.maxDimension) *
      (width / height).coerceAtMost(1f)
  scaleY =
    pressScale +
      travel / height * abs(sin(angle) * offset.y / size.maxDimension) *
      (height / width).coerceAtMost(1f)
}

/**
 * Non-consuming drag observer: it reads the initial pass and never marks a
 * change consumed, so a `clickable` on the same node still fires. A null return
 * means "cancelled", and the caller springs the surface home.
 */
private suspend fun PointerInputScope.inspectDragGestures(
  onDragStart: (down: PointerInputChange) -> Unit = {},
  onDragEnd: (change: PointerInputChange) -> Unit = {},
  onDragCancel: () -> Unit = {},
  onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
  awaitEachGesture {
    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val down = awaitFirstDown(requireUnconsumed = false)
    onDragStart(down)
    onDrag(down, Offset.Zero)
    val upEvent = drag(pointerId = down.id) { change -> onDrag(change, change.positionChange()) }
    if (upEvent == null) onDragCancel() else onDragEnd(upEvent)
  }
}

private suspend inline fun AwaitPointerEventScope.drag(
  pointerId: PointerId,
  onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
  var pointer = pointerId
  while (true) {
    val change = awaitDragOrUp(pointer) ?: return null
    if (change.isConsumed) return null
    if (change.changedToUpIgnoreConsumed()) return change
    onDrag(change)
    pointer = change.id
  }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(
  pointerId: PointerId,
): PointerInputChange? {
  var pointer = pointerId
  while (true) {
    val event = awaitPointerEvent()
    val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
    if (dragEvent.changedToUpIgnoreConsumed()) {
      val otherDown = event.changes.fastFirstOrNull { it.pressed }
      if (otherDown == null) return dragEvent
      pointer = otherDown.id
    } else if (dragEvent.previousPosition != dragEvent.position) {
      return dragEvent
    }
  }
}
