package ai.openclaw.app.ui.chat

import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassMenuDefaults
import ai.openclaw.app.ui.design.ClawMorphMenuDefaults
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.clawGlassEdge
import ai.openclaw.app.ui.design.clawPressDrag
import ai.openclaw.app.ui.design.rememberClawPressDrag
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.isActive

/** A long-press point resolved to window coordinates while the bubble was still attached. */
internal data class ChatTextPress(
  val windowOffset: Offset,
  /** Window-space bounds of the pressed bubble, used to keep the selection inside it. */
  val hostBounds: Rect?,
)

internal data class ChatTextPosition(
  val target: ChatTextHitTarget,
  val offset: Int,
)

internal data class ChatTextSelection(
  val start: ChatTextPosition,
  val end: ChatTextPosition,
)

internal enum class ChatSelectionHandleKind {
  Start,
  End,
}

internal data class ChatSelectionHandles(
  /** Cursor position of the start edge, in the pane overlay's coordinates. */
  val start: Offset,
  /** Cursor position of the end edge, in the pane overlay's coordinates. */
  val end: Offset,
  /** Bounding box of the selection, with a little breathing room for the toolbar. */
  val bounds: Rect,
)

/**
 * Owns the transcript's one active text selection. Compose's public selection container cannot
 * be driven programmatically, so the text widgets register their layouts here and the
 * controller paints/extends the selection itself.
 */
internal class ChatTextSelectionController {
  var activeMessageId by mutableStateOf<String?>(null)
    private set

  var toolbarVisible by mutableStateOf(false)
    private set

  /** Pane overlay host; selection handles and the toolbar live in its coordinate space. */
  var overlayCoordinates by mutableStateOf<LayoutCoordinates?>(null)

  val registry = ChatTextHitRegistry()

  /**
   * Scrolls the transcript by [delta] and reports how much was consumed.
   *
   * A handle dragged past the edge has to keep extending the selection, so the transcript has to
   * move under the finger; the list owns that scroll, not this controller.
   */
  var autoScroll: ((Float) -> Float)? = null

  private var press: ChatTextPress? = null
  private var dragFixed: ChatTextPosition? = null
  private var selectGeneration = 0

  val active: Boolean get() = activeMessageId != null

  fun selectedText(): String = registry.selectedText()

  fun enter(
    messageId: String,
    press: ChatTextPress,
  ) {
    if (activeMessageId == messageId) return
    exit()
    activeMessageId = messageId
    this.press = press
    selectGeneration++
    registry.clearSelection()
    // The pressed bubble is chosen once the select step can see its text blocks; a rect
    // captured here would go stale as soon as the transcript scrolled.
    registry.activeTargets = null
  }

  fun exit() {
    activeMessageId = null
    toolbarVisible = false
    press = null
    dragFixed = null
    selectGeneration++
    registry.clearSelection()
    registry.activeTargets = null
    overlayCoordinates = null
  }

  /**
   * Selects the sentence around the original long-press point. The registry fills on the first
   * measure after the selection mode provides it, so this waits a bounded number of frames.
   */
  internal suspend fun selectInitialSelection() {
    val generation = selectGeneration
    val press = press ?: return
    var entries = emptyList<ChatTextHitEntry>()
    var attempts = 0
    while (attempts < 90) {
      entries = registry.entries()
      if (entries.isNotEmpty()) break
      withFrameNanos { }
      attempts++
    }
    if (generation != selectGeneration || !active) return
    if (entries.isEmpty()) {
      exit()
      return
    }

    val windowPoint = press.windowOffset
    val allEntries = entries
    // Lock the selection to the pressed bubble by identity, not by its rectangle: the transcript
    // scrolls under a drag, and a rect captured at press time would drop every text block once
    // the bubble moved (which silently killed both the highlight and the copied text).
    val hostBounds = press.hostBounds
    val bubbleTargets =
      hostBounds
        ?.let { bounds -> allEntries.filter { entry -> entry.intersects(bounds) }.mapTo(mutableSetOf()) { it.target } }
        ?.takeIf(Set<ChatTextHitTarget>::isNotEmpty)
    registry.activeTargets = bubbleTargets
    entries = if (bubbleTargets == null) allEntries else allEntries.filter { it.target in bubbleTargets }
    val entry =
      entries
        .filter { it.contains(windowPoint) }
        .minByOrNull { it.width * it.height }
        ?: entries.minByOrNull { it.distanceTo(windowPoint) }
        ?: run {
          exit()
          return
        }
    val raw = entry.coordinates.windowToLocal(windowPoint)
    val local =
      Offset(
        raw.x.coerceIn(0f, entry.layout.size.width.toFloat()),
        raw.y.coerceIn(0f, entry.layout.size.height.toFloat()),
      )
    val offset = entry.layout.getOffsetForPosition(local)
    val range = chatQuoteSentenceRange(entry.text, offset)
    if (range.end <= range.start) {
      exit()
      return
    }
    registry.setSelection(
      ChatTextPosition(entry.target, range.start),
      ChatTextPosition(entry.target, range.end),
    )
    toolbarVisible = false
  }

  internal fun beginHandleDrag(kind: ChatSelectionHandleKind) {
    val selection = registry.selection ?: return
    dragFixed = if (kind == ChatSelectionHandleKind.Start) selection.end else selection.start
    toolbarVisible = false
  }

  internal fun dragHandle(
    kind: ChatSelectionHandleKind,
    windowPoint: Offset,
  ) {
    val fixed = dragFixed ?: return
    val entry = registry.hitTest(windowPoint) ?: registry.nearest(windowPoint) ?: return
    val local = entry.coordinates.windowToLocal(windowPoint)
    val offset = entry.layout.getOffsetForPosition(local).coerceIn(0, entry.text.length)
    registry.setSelection(ChatTextPosition(entry.target, offset), fixed)
  }

  internal fun endHandleDrag() {
    dragFixed = null
    val selection = registry.selection
    if (selection == null || selection.start == selection.end) {
      exit()
      return
    }
    toolbarVisible = true
  }
}

internal val LocalChatTextSelectionController = compositionLocalOf<ChatTextSelectionController?> { null }

internal val LocalChatTextHitRegistry = compositionLocalOf<ChatTextHitRegistry?> { null }


internal class ChatTextHitEntry(
  val target: ChatTextHitTarget,
  val text: AnnotatedString,
  val layout: TextLayoutResult,
  val coordinates: LayoutCoordinates,
) {
  val top: Float get() = coordinates.localToWindow(Offset.Zero).y
  val left: Float get() = coordinates.localToWindow(Offset.Zero).x
  val width: Float get() = coordinates.size.width.toFloat()
  val height: Float get() = coordinates.size.height.toFloat()

  fun contains(windowPoint: Offset): Boolean =
    windowPoint.x >= left && windowPoint.x <= left + width &&
      windowPoint.y >= top && windowPoint.y <= top + height

  fun intersects(bounds: Rect): Boolean =
    left < bounds.right && bounds.left < left + width &&
      top < bounds.bottom && bounds.top < top + height

  fun distanceTo(windowPoint: Offset): Float {
    val dx = (left - windowPoint.x).coerceAtLeast(windowPoint.x - (left + width)).coerceAtLeast(0f)
    val dy = (top - windowPoint.y).coerceAtLeast(windowPoint.y - (top + height)).coerceAtLeast(0f)
    return dx * dx + dy * dy
  }
}

internal class ChatTextHitRegistry {
  private val targets = mutableListOf<ChatTextHitTarget>()

  /**
   * Text blocks that belong to the pressed bubble, chosen by identity when the select step
   * resolves the press. Null outside select mode, and never a rectangle: the transcript scrolls
   * under a handle drag, so any captured bounds would exclude the very blocks being selected.
   */
  var activeTargets: Set<ChatTextHitTarget>? = null

  var selection by mutableStateOf<ChatTextSelection?>(null)
    private set

  fun register(target: ChatTextHitTarget) {
    if (target !in targets) targets += target
  }

  fun unregister(target: ChatTextHitTarget) {
    targets -= target
  }

  fun clearSelection() {
    selection = null
  }

  fun setSelection(
    first: ChatTextPosition,
    second: ChatTextPosition,
  ) {
    selection = normalized(first, second)
  }

  fun entries(): List<ChatTextHitEntry> =
    targets
      .mapNotNull(ChatTextHitTarget::snapshot)
      .filter { entry -> activeTargets?.let { it.contains(entry.target) } ?: true }
      .sortedWith(compareBy(ChatTextHitEntry::top, ChatTextHitEntry::left))

  fun hitTest(windowPoint: Offset): ChatTextHitEntry? =
    entries()
      .filter { it.contains(windowPoint) }
      .minByOrNull { it.width * it.height }

  fun nearest(windowPoint: Offset): ChatTextHitEntry? =
    entries().minByOrNull { it.distanceTo(windowPoint) }

  fun selectionSpanFor(target: ChatTextHitTarget): IntRange? {
    val selection = selection ?: return null
    val ordered = entries()
    val startIndex = ordered.indexOfFirst { it.target === selection.start.target }
    val endIndex = ordered.indexOfFirst { it.target === selection.end.target }
    val index = ordered.indexOfFirst { it.target === target }
    if (startIndex < 0 || endIndex < 0 || index < 0) return null
    if (index < startIndex || index > endIndex) return null
    val length = ordered[index].text.length
    val start =
      when (index) {
        startIndex -> selection.start.offset.coerceIn(0, length)
        else -> 0
      }
    val end =
      when (index) {
        endIndex -> selection.end.offset.coerceIn(0, length)
        else -> length
      }
    return if (end > start) start until end else null
  }

  fun selectedText(): String {
    val selection = selection ?: return ""
    val ordered = entries()
    val startIndex = ordered.indexOfFirst { it.target === selection.start.target }
    val endIndex = ordered.indexOfFirst { it.target === selection.end.target }
    if (startIndex < 0 || endIndex < 0) return ""
    val parts = ArrayList<String>(endIndex - startIndex + 1)
    for (index in startIndex..endIndex) {
      val entry = ordered[index]
      val span = selectionSpanFor(entry.target) ?: continue
      parts += entry.text.text.substring(span.first, span.last + 1)
    }
    return parts.joinToString("\n").trim()
  }

  fun handles(overlay: LayoutCoordinates?): ChatSelectionHandles? {
    val selection = selection ?: return null
    val overlayCoordinates = overlay?.takeIf { it.isAttached } ?: return null
    val startEntry = selection.start.target.snapshot() ?: return null
    val endEntry = selection.end.target.snapshot() ?: return null
    if (!startEntry.coordinates.isAttached || !endEntry.coordinates.isAttached) return null
    val startCursor =
      startEntry.layout.getCursorRect(selection.start.offset.coerceIn(0, startEntry.text.length))
    val endCursor =
      endEntry.layout.getCursorRect(selection.end.offset.coerceIn(0, endEntry.text.length))
    val startTop =
      overlayCoordinates.localPositionOf(startEntry.coordinates, Offset(startCursor.left, startCursor.top))
    val startBottom =
      overlayCoordinates.localPositionOf(startEntry.coordinates, Offset(startCursor.left, startCursor.bottom))
    val endTop =
      overlayCoordinates.localPositionOf(endEntry.coordinates, Offset(endCursor.right, endCursor.top))
    val endBottom =
      overlayCoordinates.localPositionOf(endEntry.coordinates, Offset(endCursor.right, endCursor.bottom))
    val startPoint = Offset(startTop.x, startBottom.y)
    val endPoint = Offset(endBottom.x, endBottom.y)
    val bounds =
      Rect(
        left = min(startTop.x, endTop.x) - 8f,
        top = min(startTop.y, endTop.y) - 10f,
        right = max(startBottom.x, endBottom.x) + 8f,
        bottom = max(startBottom.y, endBottom.y) + 10f,
      )
    return ChatSelectionHandles(start = startPoint, end = endPoint, bounds = bounds)
  }

  private fun normalized(
    first: ChatTextPosition,
    second: ChatTextPosition,
  ): ChatTextSelection {
    val ordered = entries().map(ChatTextHitEntry::target)
    val firstIndex = ordered.indexOfFirst { it === first.target }
    val secondIndex = ordered.indexOfFirst { it === second.target }
    val firstBefore =
      firstIndex < secondIndex || (firstIndex == secondIndex && first.offset <= second.offset)
    return if (firstBefore) {
      ChatTextSelection(start = first, end = second)
    } else {
      ChatTextSelection(start = second, end = first)
    }
  }
}

internal class ChatTextHitTarget(
  private val registry: ChatTextHitRegistry?,
  private val highlightColor: Color,
) {
  private var layoutResult: TextLayoutResult? = null
  private var coordinates: LayoutCoordinates? = null

  val modifier: Modifier =
    Modifier
      .onGloballyPositioned { coordinates = it }
      .drawBehind {
        val layout = layoutResult ?: return@drawBehind
        val span = registry?.selectionSpanFor(this@ChatTextHitTarget) ?: return@drawBehind
        drawChatTextSelection(layout, span.first, span.last + 1, highlightColor)
      }

  fun onTextLayout(result: TextLayoutResult) {
    layoutResult = result
  }

  fun snapshot(): ChatTextHitEntry? {
    val layout = layoutResult ?: return null
    val coords = coordinates?.takeIf { it.isAttached } ?: return null
    val text = layout.layoutInput.text
    if (text.length == 0) return null
    return ChatTextHitEntry(target = this, text = text, layout = layout, coordinates = coords)
  }
}

private fun DrawScope.drawChatTextSelection(
  layout: TextLayoutResult,
  start: Int,
  end: Int,
  color: Color,
) {
  if (start >= end) return
  val startLine = layout.getLineForOffset(start)
  val endLine = layout.getLineForOffset((end - 1).coerceAtLeast(start))
  for (line in startLine..endLine) {
    val lineStart = layout.getLineStart(line)
    val lineEnd = layout.getLineEnd(line, visibleEnd = true)
    val from = maxOf(start, lineStart)
    val to = minOf(end, lineEnd)
    if (from >= to) continue
    val lineLeft = layout.getLineLeft(line)
    val lineRight = layout.getLineRight(line)
    // getHorizontalPosition at a soft-wrap boundary reports the previous line's trailing
    // edge, so a continued line's start must clamp to its own left edge.
    val left =
      if (from == lineStart) {
        lineLeft
      } else {
        layout.getHorizontalPosition(from, usePrimaryDirection = true).coerceIn(lineLeft, lineRight)
      }
    val right =
      if (to == lineEnd) {
        lineRight
      } else {
        layout.getHorizontalPosition(to, usePrimaryDirection = true).coerceIn(lineLeft, lineRight)
      }.coerceAtLeast(left)
    val top = layout.getLineTop(line) + 1f
    val bottom = layout.getLineBottom(line) - 1f
    if (right <= left || bottom <= top) continue
    drawRoundRect(
      color = color,
      topLeft = Offset(left, top),
      size = Size(right - left, bottom - top),
      cornerRadius = CornerRadius(2.dp.toPx()),
    )
  }
}

@Composable
internal fun rememberChatTextHitTarget(): ChatTextHitTarget {
  val registry = LocalChatTextHitRegistry.current
  val colors = ClawTheme.colors
  val highlight =
    colors.accent.copy(
      alpha = if (colors.canvas.luminance() < 0.5f) 0.30f else 0.22f,
    )
  val target = remember(registry, highlight) { ChatTextHitTarget(registry, highlight) }
  DisposableEffect(registry, target) {
    registry?.register(target)
    onDispose { registry?.unregister(target) }
  }
  return target
}

/**
 * Pane-level selection chrome: the outside-tap scrim, the two draggable handles, and the glass
 * copy/follow-up bar. The handles and bar sit above the scrim so their touches win.
 */
@Composable
internal fun ChatTextSelectionOverlay(
  controller: ChatTextSelectionController,
  backdrop: Backdrop?,
  onCopy: (String) -> Unit,
  onFollowUp: (ChatQuoteDraft) -> Unit,
) {
  val active = controller.active
  if (!active) return
  var overlaySize by remember { mutableStateOf(IntSize.Zero) }
  var handles by remember { mutableStateOf<ChatSelectionHandles?>(null) }
  // Handle drags that reach the edge of the transcript scroll it, like a text field's
  // selection does; the drag stays parked on the finger, so the next tick re-selects whatever
  // slid under it.
  var draggingKind by remember { mutableStateOf<ChatSelectionHandleKind?>(null) }
  var lastDragWindowPoint by remember { mutableStateOf<Offset?>(null) }
  LaunchedEffect(controller) {
    snapshotFlow { controller.registry.selection to controller.overlayCoordinates }
      .collect { (selection, coordinates) ->
        handles = if (selection == null || coordinates == null) null else controller.registry.handles(coordinates)
      }
  }
  val density = LocalDensity.current
  val autoScrollStep = with(density) { 8.dp.toPx() }
  val edgeBand = with(density) { 140.dp.toPx() }
  val topEdgeBand = with(density) { 72.dp.toPx() }
  LaunchedEffect(draggingKind) {
    val kind = draggingKind ?: return@LaunchedEffect
    var previous = controller.registry.selection
    var stalledTicks = 0
    while (true) {
      val point = lastDragWindowPoint
      // The bands live in this pane's own space; the finger arrives in window space.
      val local = point?.let { controller.overlayCoordinates?.windowToLocal(it) }
      val height = overlaySize.height.toFloat()
      val direction =
        when {
          local == null || height <= 0f -> 0
          // The transcript is a reverseLayout list (newest at the bottom), so reaching toward
          // later text is a negative raw delta: positive would drag older rows in.
          local.y > height - edgeBand -> -1
          local.y < topEdgeBand -> 1
          else -> 0
        }
      if (direction != 0 && point != null) {
        val consumed = controller.autoScroll?.invoke(autoScrollStep * direction) ?: 0f
        controller.dragHandle(kind, point)
        val current = controller.registry.selection
        // Scroll and selection land on different frames, so one unchanged tick means nothing.
        // Only a long run without either moving means the drag is pinned (the bubble's text
        // ends, or the list is at its end) and further scrolling would just move the page.
        val moved = consumed != 0f || current != previous
        stalledTicks = if (moved) 0 else stalledTicks + 1
        previous = current
        if (stalledTicks > 12 || consumed == 0f) return@LaunchedEffect
      } else {
        stalledTicks = 0
      }
      withFrameNanos { }
    }
  }
  Box(
    modifier =
      Modifier
        .fillMaxSize()
        .onGloballyPositioned { controller.overlayCoordinates = it }
        .onSizeChanged { overlaySize = it },
  ) {
    Box(
      modifier =
        Modifier
          .matchParentSize()
          .pointerInput(Unit) {
            detectTapGestures { controller.exit() }
          },
    )
    val current = handles
    if (current != null) {
      val handleColor = chatSelectionHandleColor()
      val onHandleDrag: (ChatSelectionHandleKind, Offset?) -> Unit = { kind, windowPoint ->
        draggingKind = if (windowPoint == null) null else kind
        lastDragWindowPoint = windowPoint
      }
      ChatSelectionHandleDot(
        controller = controller,
        kind = ChatSelectionHandleKind.Start,
        point = current.start,
        color = handleColor,
        onDrag = onHandleDrag,
      )
      ChatSelectionHandleDot(
        controller = controller,
        kind = ChatSelectionHandleKind.End,
        point = current.end,
        color = handleColor,
        onDrag = onHandleDrag,
      )
    }
    ChatTextSelectionToolbar(
      visible = controller.toolbarVisible,
      anchor = current?.bounds,
      overlaySize = overlaySize,
      selected = controller.selectedText(),
      backdrop = backdrop,
      onCopy = {
        val text = controller.selectedText()
        if (text.isNotBlank()) {
          onCopy(text)
          controller.exit()
        }
      },
      onFollowUp = {
        val text = controller.selectedText()
        if (text.isNotBlank()) {
          val quote = ChatQuoteDraft(text, controller.activeMessageId)
          controller.exit()
          onFollowUp(quote)
        }
      },
    )
  }
}

@Composable
private fun chatSelectionHandleColor(): Color = ClawTheme.colors.accent

@Composable
private fun ChatSelectionHandleDot(
  controller: ChatTextSelectionController,
  kind: ChatSelectionHandleKind,
  point: Offset,
  color: Color,
  onDrag: (ChatSelectionHandleKind, Offset?) -> Unit,
) {
  var coordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  Box(
    modifier =
      Modifier
        .offset {
          // The teardrop's tip sits on the selection point and its bulb hangs below, the same
          // shape the platform selection handles use; the box below is the touch target.
          IntOffset((point.x - 18.dp.toPx()).roundToInt(), point.y.roundToInt())
        }
        .size(36.dp)
        .onGloballyPositioned { coordinates = it }
        .pointerInput(controller, kind) {
          detectDragGestures(
            onDragStart = { start ->
              controller.beginHandleDrag(kind)
              onDrag(kind, coordinates?.localToWindow(start))
            },
            onDrag = { change, _ ->
              change.consume()
              val host = coordinates ?: return@detectDragGestures
              val windowPoint = host.localToWindow(change.position)
              controller.dragHandle(kind, windowPoint)
              onDrag(kind, windowPoint)
            },
            onDragEnd = {
              controller.endHandleDrag()
              onDrag(kind, null)
            },
            onDragCancel = {
              controller.endHandleDrag()
              onDrag(kind, null)
            },
          )
        },
    contentAlignment = Alignment.TopCenter,
  ) {
    Canvas(modifier = Modifier.size(width = 22.dp, height = 30.dp)) {
      val centerX = size.width / 2f
      val radius = size.width * 0.42f
      val centerY = size.height - radius
      // A round bulb with a tapered neck: the tail merges into the circle, so the pair reads as
      // one teardrop instead of the spike a narrow path alone produced.
      drawCircle(color = color, radius = radius, center = Offset(centerX, centerY))
      val neck =
        androidx.compose.ui.graphics.Path().apply {
          moveTo(centerX, 0f)
          quadraticTo(
            x1 = centerX - radius * 0.16f,
            y1 = centerY - radius * 1.25f,
            x2 = centerX - radius * 0.62f,
            y2 = centerY - radius * 0.78f,
          )
          lineTo(centerX + radius * 0.62f, centerY - radius * 0.78f)
          quadraticTo(
            x1 = centerX + radius * 0.16f,
            y1 = centerY - radius * 1.25f,
            x2 = centerX,
            y2 = 0f,
          )
          close()
        }
      drawPath(neck, color)
    }
  }
}

@Composable
private fun ChatTextSelectionToolbar(
  visible: Boolean,
  anchor: Rect?,
  overlaySize: IntSize,
  selected: String,
  backdrop: Backdrop?,
  onCopy: () -> Unit,
  onFollowUp: () -> Unit,
) {
  if (anchor == null || backdrop == null) return
  var toolbarSize by remember { mutableStateOf(IntSize.Zero) }
  val enabled = selected.isNotBlank()
  AnimatedVisibility(
    visible = visible,
    modifier =
      Modifier.offset {
        val gap = 10.dp.roundToPx()
        val edge = 8.dp.roundToPx()
        val width = toolbarSize.width
        val height = toolbarSize.height
        val maxX = (overlaySize.width - width - edge).coerceAtLeast(edge)
        val x = (anchor.center.x - width / 2f).roundToInt().coerceIn(edge, maxX)
        var y = (anchor.top - height - gap).roundToInt()
        if (y < edge) y = (anchor.bottom + gap).roundToInt()
        val maxY = (overlaySize.height - height - edge).coerceAtLeast(edge)
        IntOffset(x, y.coerceIn(edge, maxY))
      },
    enter =
      fadeIn(animationSpec = tween(80, easing = LinearOutSlowInEasing)) +
        scaleIn(
          initialScale = 0.92f,
          animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        ),
    exit =
      fadeOut(animationSpec = tween(90, easing = LinearOutSlowInEasing)) +
        scaleOut(targetScale = 0.96f, animationSpec = tween(90)),
  ) {
    SelectionGlass(backdrop = backdrop, modifier = Modifier.onSizeChanged { toolbarSize = it }) {
      SelectionAction(
        label = nativeString("Copy"),
        icon = Icons.Default.ContentCopy,
        enabled = enabled,
        onClick = onCopy,
      )
      Box(
        modifier =
          Modifier
            .size(width = 1.dp, height = 18.dp)
            .background(ClawTheme.colors.border),
      )
      SelectionAction(
        label = nativeString("Follow up"),
        icon = Icons.AutoMirrored.Filled.Reply,
        enabled = enabled,
        onClick = onFollowUp,
      )
    }
  }
}

@Composable
internal fun SelectionGlass(
  backdrop: Backdrop,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val colors = ClawTheme.colors
  val density = LocalDensity.current
  val dark = colors.canvas.luminance() < 0.5f
  val shape = RoundedCornerShape(18.dp)
  val effects: BackdropEffectScope.() -> Unit =
    remember(density, dark) {
      {
        vibrancy()
        blur(ClawMorphMenuDefaults.OpenBlur.toPx())
        lens(8.dp.toPx(), 24.dp.toPx())
      }
    }
  val tint = if (dark) ClawGlassMenuDefaults.TintAlphaDark else ClawGlassMenuDefaults.TintAlphaLight
  // The press belongs to the whole glass bar: it swells and stretches as one piece, the way a
  // single sheet of material would, instead of each label squashing on its own.
  val pressDrag = rememberClawPressDrag(glow = false)
  Row(
    modifier =
      modifier
        .clawPressDrag(pressDrag)
        .drawBackdrop(
          backdrop = backdrop,
          shape = { shape },
          effects = effects,
          onDrawSurface = {
            drawRect(
              (if (dark) colors.surfaceRaised else Color.White).copy(alpha = tint),
            )
          },
        )
        .clawGlassEdge(cornerRadius = 18.dp, dark = dark)
        .clip(shape)
        .padding(horizontal = 4.dp, vertical = 3.dp)
        // After the items: the drag observer never consumes, so taps still land.
        .then(pressDrag.modifier.then(pressDrag.gestureModifier)),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    content()
  }
}

@Composable
internal fun SelectionAction(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val colors = ClawTheme.colors
  val tint = if (enabled) colors.text else colors.textSubtle
  Row(
    modifier =
      Modifier
        .heightIn(min = 40.dp)
        .clip(RoundedCornerShape(14.dp))
        .clickable(enabled = enabled, onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(7.dp),
  ) {
    icon?.let { iconVector ->
      Icon(
        imageVector = iconVector,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp),
      )
    }
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 13.sp, fontWeight = FontWeight.Medium),
      color = tint,
    )
  }
}

internal data class ChatQuoteSentenceRange(
  val start: Int,
  /** Exclusive. */
  val end: Int,
)

private val cjkSentenceBoundaries = setOf('。', '！', '？', '；', '：', '，', '、', '…', '\n', '\r')
private val asciiSentenceBoundaries = setOf('!', '?', ';', ':', '.', ',')
private val sentenceClosingMarks = setOf('”', '’', '」', '』', '）', '》', '〉', '】', '"', '\'')

/**
 * Sentence/clause range around [offset]. A link is taken whole; otherwise Chinese sentence and
 * clause punctuation ends the range. A block with no punctuation falls back to its line so a
 * long code fence cannot select the whole fence from one press.
 */
internal fun chatQuoteSentenceRange(
  text: AnnotatedString,
  offset: Int,
): ChatQuoteSentenceRange {
  if (text.length == 0) return ChatQuoteSentenceRange(0, 0)
  val safeOffset = offset.coerceIn(0, text.length - 1)
  val link =
    text
      .getLinkAnnotations(safeOffset, (safeOffset + 1).coerceAtMost(text.length))
      .firstOrNull()
  if (link != null) {
    return ChatQuoteSentenceRange(
      start = link.start.coerceIn(0, text.length),
      end = link.end.coerceIn(0, text.length),
    )
  }
  return chatQuoteSentenceRange(text.text, safeOffset)
}

private fun chatQuoteSentenceRange(
  text: String,
  offset: Int,
): ChatQuoteSentenceRange {
  if (text.isEmpty()) return ChatQuoteSentenceRange(0, 0)
  if (text.length == 1) return ChatQuoteSentenceRange(0, 1)
  var anchor = offset.coerceIn(0, text.length - 1)
  if (text[anchor].isWhitespace()) {
    var forward = anchor
    while (forward < text.length && text[forward].isWhitespace()) forward++
    var backward = anchor
    while (backward >= 0 && text[backward].isWhitespace()) backward--
    anchor = if (forward < text.length) forward else backward.coerceAtLeast(0)
  }
  // Pressing on punctuation selects the clause before it, not the punctuation alone.
  var guard = 0
  while (guard++ < 8 && (isSentenceBoundaryAt(text, anchor) || text[anchor] in sentenceClosingMarks)) {
    var back = anchor - 1
    while (back >= 0 && text[back].isWhitespace()) back--
    if (back < 0) break
    anchor = back
  }

  var start = anchor
  var foundBefore = false
  while (start > 0) {
    if (isSentenceBoundaryAt(text, start - 1)) {
      foundBefore = true
      break
    }
    start--
  }
  var end = anchor + 1
  var foundAfter = false
  while (end < text.length) {
    if (isSentenceBoundaryAt(text, end - 1)) {
      foundAfter = true
      break
    }
    end++
  }
  if (end == text.length && isSentenceBoundaryAt(text, end - 1)) foundAfter = true

  if (!foundBefore && !foundAfter && text.contains('\n')) {
    start = if (anchor == 0) 0 else text.lastIndexOf('\n', anchor - 1).let { if (it < 0) 0 else it + 1 }
    end = text.indexOf('\n', anchor).let { if (it < 0) text.length else it }
  } else {
    // Keep trailing closing quotes and repeated marks with the clause they terminate.
    while (true) {
      var moved = false
      while (end < text.length && text[end] in sentenceClosingMarks) {
        end++
        moved = true
      }
      while (end < text.length && text[end] != '\n' && text[end] != '\r' && isSentenceBoundaryAt(text, end)) {
        end++
        moved = true
      }
      if (!moved) break
    }
  }

  while (start < end && text[start].isWhitespace()) start++
  while (end > start && text[end - 1].isWhitespace()) end--
  if (start >= end) {
    val lineStart = if (anchor == 0) 0 else text.lastIndexOf('\n', anchor - 1).let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', anchor).let { if (it < 0) text.length else it }
    start = lineStart.coerceAtMost(anchor)
    end = lineEnd.coerceAtLeast(anchor + 1).coerceAtMost(text.length)
  }
  return ChatQuoteSentenceRange(start = start, end = end)
}

private fun isSentenceBoundaryAt(
  text: String,
  index: Int,
): Boolean {
  if (index !in text.indices) return false
  val char = text[index]
  if (char in cjkSentenceBoundaries) return true
  if (char !in asciiSentenceBoundaries) return false
  val next = text.getOrNull(index + 1)
  val previous = text.getOrNull(index - 1)
  if (
    (char == '.' || char == ',') &&
    previous?.isDigit() == true &&
    next?.isDigit() == true
  ) {
    return false
  }
  return next == null || next.isWhitespace() || next in sentenceClosingMarks
}
