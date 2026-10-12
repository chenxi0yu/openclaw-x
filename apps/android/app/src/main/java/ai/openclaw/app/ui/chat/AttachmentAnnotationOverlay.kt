package ai.openclaw.app.ui.chat

import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassSurface
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.LucideCheckIcon
import ai.openclaw.app.ui.design.LucideXIcon
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import dev.chrisbanes.haze.HazeState
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val AnnotationRed = Color(0xFFE5322D)
private const val AnnotationMaxZoom = 4f

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

private fun lerpRect(a: Rect, b: Rect, t: Float): Rect =
  Rect(
    left = lerp(a.left, b.left, t),
    top = lerp(a.top, b.top, t),
    right = lerp(a.right, b.right, t),
    bottom = lerp(a.bottom, b.bottom, t),
  )

/** Largest rect inside [area] that keeps the bitmap's aspect ratio, centered. */
private fun fitRect(area: Rect, bitmap: ImageBitmap): Rect {
  val ratio = bitmap.width.toFloat() / bitmap.height.toFloat()
  var width = area.width
  var height = width / ratio
  if (height > area.height) {
    height = area.height
    width = height * ratio
  }
  return Rect(
    left = area.center.x - width / 2f,
    top = area.center.y - height / 2f,
    right = area.center.x + width / 2f,
    bottom = area.center.y + height / 2f,
  )
}

/**
 * Full-screen annotation editor that morphs out of the tapped chip's thumbnail.
 *
 * A single spring-driven progress interpolates the image rect from the chip
 * thumbnail to its fitted rect inside a glass card covering the lower three
 * quarters of the screen, so opening and closing read as one continuous
 * gesture. The image is cropped while travelling and lands exactly on its fit
 * rect, where it is whole and pinch-zoomable; one finger draws red strokes.
 * Cancel and confirm float on the card's top edge.
 */
@Composable
internal fun AttachmentAnnotationOverlay(
  attachment: PendingAttachment,
  chipBounds: Rect,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit,
  hazeState: HazeState? = null,
) {
  // 0f = collapsed onto the chip thumbnail, 1f = fully expanded into the card.
  val progress = remember { Animatable(0f) }
  val scope = rememberCoroutineScope()
  var cardBounds by remember { mutableStateOf(Rect.Zero) }
  val strokes = remember(attachment.id) { mutableStateListOf<List<Offset>>() }
  var currentStroke by remember(attachment.id) { mutableStateOf<List<Offset>?>(null) }
  var zoom by remember { mutableStateOf(1f) }
  var pan by remember { mutableStateOf(Offset.Zero) }

  fun close(after: () -> Unit = {}) {
    scope.launch {
      progress.animateTo(0f, tween(durationMillis = 180))
      onDismiss()
      after()
    }
  }

  LaunchedEffect(Unit) {
    progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f))
  }

  Popup(
    onDismissRequest = { close() },
    properties = PopupProperties(focusable = true),
  ) {
    val popupView = LocalView.current
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
      val density = LocalDensity.current
      val p = progress.value
      val imageState = rememberBase64ImageState(attachment.base64)
      val bitmap = imageState.image
      // The popup window insets its content below the status bar while the chip
      // bounds arrive in activity-root (screen) coords; shift them into this
      // root or the morph starts a status bar away from the thumbnail.
      val popupOrigin =
        remember(popupView) {
          val loc = IntArray(2)
          popupView.getLocationOnScreen(loc)
          Offset(loc[0].toFloat(), loc[1].toFloat())
        }
      val startRect = chipBounds.translate(-popupOrigin.x, -popupOrigin.y)

      LaunchedEffect(imageState.failed) {
        if (imageState.failed) onDismiss()
      }

      val area =
        if (cardBounds == Rect.Zero || bitmap == null) {
          Rect.Zero
        } else {
          val side = with(density) { 14.dp.toPx() }
          val top = with(density) { 34.dp.toPx() }
          Rect(
            left = cardBounds.left + side,
            top = cardBounds.top + top,
            right = cardBounds.right - side,
            bottom = cardBounds.bottom - side,
          )
        }
      val endRect = if (area.width > 0f && area.height > 0f && bitmap != null) fitRect(area, bitmap) else Rect.Zero
      val imageRect = if (endRect == Rect.Zero) startRect else lerpRect(startRect, endRect, p)

      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 0.45f * p }
            .background(Color.Black),
      )

      if (bitmap == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator(color = ClawTheme.colors.primary)
        }
      }

      Box(
        modifier =
          Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
            .widthIn(max = 460.dp)
            .fillMaxWidth(0.92f)
            .height(maxHeight * 0.75f)
            .onGloballyPositioned { cardBounds = it.boundsInRoot() }
            .graphicsLayer { alpha = p },
      ) {
        ClawGlassSurface(hazeState = hazeState, fillAlpha = 0.72f, modifier = Modifier.fillMaxSize()) {}
      }

      if (bitmap != null && endRect != Rect.Zero) {
        val heroWidth = with(density) { imageRect.width.toDp() }
        val heroHeight = with(density) { imageRect.height.toDp() }
        Box(
          modifier =
            Modifier
              .offset { IntOffset(imageRect.left.roundToInt(), imageRect.top.roundToInt()) }
              .size(heroWidth, heroHeight)
              .clip(RoundedCornerShape(lerp(8f, 14f, p).dp)),
        ) {
          Box(
            modifier =
              Modifier
                .fillMaxSize()
                .graphicsLayer {
                  scaleX = zoom
                  scaleY = zoom
                  translationX = pan.x
                  translationY = pan.y
                },
          ) {
            Image(
              bitmap = bitmap,
              contentDescription = null,
              contentScale = ContentScale.Crop,
              modifier = Modifier.fillMaxSize(),
            )
            Canvas(
              modifier =
                Modifier
                  .fillMaxSize()
                  .pointerInput(attachment.id) {
                    if (progress.value < 0.95f) return@pointerInput
                    awaitEachGesture {
                      val down = awaitFirstDown(requireUnconsumed = false)
                      val stroke = mutableListOf(down.position)
                      var prevCentroid = down.position
                      var prevSpan = 0f
                      var pinching = false
                      do {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.size >= 2) {
                          if (!pinching) {
                            pinching = true
                            stroke.clear()
                          }
                          val first = pressed[0].position
                          val second = pressed[1].position
                          val centroid = (first + second) * 0.5f
                          val span = (first - second).getDistance()
                          if (prevSpan > 0f) {
                            zoom = (zoom * (span / prevSpan)).coerceIn(1f, AnnotationMaxZoom)
                          }
                          pan = pan + (centroid - prevCentroid)
                          prevCentroid = centroid
                          prevSpan = span
                          event.changes.forEach { it.consume() }
                        } else if (pressed.size == 1 && !pinching) {
                          stroke.add(pressed[0].position)
                          currentStroke = stroke.toList()
                          pressed[0].consume()
                        }
                      } while (pressed.isNotEmpty())
                      if (zoom <= 1.001f) pan = Offset.Zero
                      currentStroke = null
                      if (!pinching && stroke.size > 1) strokes.add(stroke.toList())
                    }
                  },
            ) {
              val all = strokes + listOfNotNull(currentStroke)
              all.forEach { stroke ->
                if (stroke.size < 2) return@forEach
                val path = Path()
                stroke.forEachIndexed { index, point ->
                  if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                drawPath(
                  path = path,
                  color = AnnotationRed,
                  style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
              }
            }
          }
        }
      }

      val buttonAlpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
      if (buttonAlpha > 0f && endRect != Rect.Zero) {
        Box(
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = 24.dp)
              .widthIn(max = 460.dp)
              .fillMaxWidth(0.92f)
              .height(maxHeight * 0.75f),
        ) {
          AnnotationActionButton(
            icon = LucideXIcon,
            contentDescription = nativeString("Cancel"),
            container = AnnotationRed,
            modifier =
              Modifier
                .align(Alignment.TopStart)
                .offset(y = (-23).dp)
                .graphicsLayer {
                  alpha = buttonAlpha
                  scaleX = 0.8f + 0.2f * buttonAlpha
                  scaleY = 0.8f + 0.2f * buttonAlpha
                },
            onClick = { close() },
          )
          AnnotationActionButton(
            icon = LucideCheckIcon,
            contentDescription = nativeString("Confirm"),
            container = ClawTheme.colors.primary,
            modifier =
              Modifier
                .align(Alignment.TopEnd)
                .offset(y = (-23).dp)
                .graphicsLayer {
                  alpha = buttonAlpha
                  scaleX = 0.8f + 0.2f * buttonAlpha
                  scaleY = 0.8f + 0.2f * buttonAlpha
                },
            onClick = {
              val annotated =
                annotateBase64Image(
                  base64 = attachment.base64,
                  strokes =
                    strokes.map { stroke ->
                      ImageAnnotationStroke(stroke.map { ImageAnnotationPoint(it.x, it.y) })
                    },
                  canvasWidth = endRect.width,
                  canvasHeight = endRect.height,
                  imageLeft = 0f,
                  imageTop = 0f,
                  imageWidth = endRect.width,
                  imageHeight = endRect.height,
                )
              if (annotated != null) {
                close()
                onConfirm(annotated)
              }
            },
          )
        }
      }
    }
  }
}

@Composable
private fun AnnotationActionButton(
  icon: ImageVector,
  contentDescription: String,
  container: Color,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = container,
    contentColor = Color.White,
    shadowElevation = 6.dp,
    modifier = modifier.size(46.dp),
  ) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
      Icon(imageVector = icon, contentDescription = contentDescription, modifier = Modifier.size(20.dp))
    }
  }
}
