package ai.openclaw.app.ui.chat

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.math.abs

internal enum class ChatScrollFollowTarget {
  ReadAnchor,
  LatestContent,
}

internal data class ChatReaderState(
  val ownerSessionKey: String? = null,
  val initialized: Boolean = false,
  val followTarget: ChatScrollFollowTarget? = null,
  val hasNewerContent: Boolean = false,
  val latestUserMessageId: String? = null,
  val latestUserMessageVersion: String? = null,
  val latestContentVersion: String? = null,
  /**
   * Set when the reader asked to return to the live edge. The follow target is then
   * trustworthy even before the list has actually arrived, so the "is the reader really
   * at the bottom?" guard must not veto it and cancel the follow it was just given.
   */
  val followRequested: Boolean = false,
)

internal fun createChatReaderStateSaver(expectedSessionKey: String? = null) =
  listSaver<ChatReaderState, Any>(
    save = { state ->
      listOf(
        state.ownerSessionKey != null,
        state.ownerSessionKey.orEmpty(),
        state.initialized,
        state.followTarget?.name.orEmpty(),
        state.hasNewerContent,
        state.latestUserMessageId != null,
        state.latestUserMessageId.orEmpty(),
        state.latestUserMessageVersion != null,
        state.latestUserMessageVersion.orEmpty(),
        state.latestContentVersion != null,
        state.latestContentVersion.orEmpty(),
        state.followRequested,
      )
    },
    restore = { saved ->
      val restored =
        ChatReaderState(
          ownerSessionKey = (saved[1] as String).takeIf { saved[0] as Boolean },
          initialized = saved[2] as Boolean,
          followTarget =
            (saved[3] as String).takeIf(String::isNotEmpty)?.let(ChatScrollFollowTarget::valueOf),
          hasNewerContent = saved[4] as Boolean,
          latestUserMessageId = (saved[6] as String).takeIf { saved[5] as Boolean },
          latestUserMessageVersion = (saved[8] as String).takeIf { saved[7] as Boolean },
          latestContentVersion = (saved[10] as String).takeIf { saved[9] as Boolean },
          followRequested = saved[11] as Boolean,
        )
      restored.takeIf { expectedSessionKey == null || it.ownerSessionKey == expectedSessionKey }
    },
  )

internal val ChatReaderStateSaver = createChatReaderStateSaver()

internal data class ChatReaderTransition(
  val state: ChatReaderState,
  val scrollIndex: Int? = null,
  val animated: Boolean = false,
)

internal data class ChatReaderScrollController(
  val listState: LazyListState,
  val showJumpToLatest: Boolean,
  /** True while a drag or fling is still moving the list. */
  val scrolling: Boolean,
  val jumpToLatest: () -> Unit,
  val navigation: ChatReaderNavigation,
  val nestedScrollConnection: NestedScrollConnection,
)

private data class ChatReaderViewport(
  val scrolling: Boolean,
  val index: Int,
  val offset: Int,
  val placementRevision: Int,
  val layout: LazyListLayoutInfo,
  val canScroll: Boolean,
  val navigating: Boolean,
)

internal class ChatReaderNavigation(
  private val scope: CoroutineScope,
  private val listState: LazyListState? = null,
  private val pauseFollowing: () -> Unit = {},
) {
  val anchors = ChatReaderAnchors(listState)

  private data class Request(
    val job: Job,
    val automatic: Boolean,
  )

  private var active by mutableStateOf<Request?>(null)
  val isNavigating: Boolean get() = active != null

  fun launch(
    owner: CoroutineScope,
    automatic: Boolean = false,
    action: suspend () -> Unit,
  ): Job =
    owner.launch(start = CoroutineStart.UNDISPATCHED) {
      // Admit in the coroutine before suspension: cancelled row/session callbacks
      // return a cancelled job without retiring current work or skipping its cleanup.
      ensureActive()
      scope.ensureActive()
      if (!automatic) {
        anchors.invalidate()
        pauseFollowing()
      }
      retire()
      val request = coroutineContext.job
      active = Request(request, automatic)
      try {
        // Automatic follow must never kill a user drag/fling; only explicit
        // reading navigation stops the current scroll.
        if (!automatic) listState?.stopScroll()
        action()
      } finally {
        if (active?.job === request) active = null
      }
    }

  fun pause() {
    launch(scope) {}
  }

  fun cancel(request: Job) {
    if (active?.job === request) pause() else request.cancel()
  }

  fun cancelAutomatic() {
    active?.takeIf { it.automatic }?.job?.cancel()
  }

  fun retire() {
    active?.job?.cancel()
    active = null
  }

  fun viewportHeight(contentHeight: Int): Int = minOf(contentHeight, listState?.layoutInfo?.viewportSize?.height ?: contentHeight)
}

internal class ChatReaderAction(
  private val scope: CoroutineScope,
  private val navigation: ChatReaderNavigation,
) {
  private var request: Job? = null

  fun launch(action: suspend () -> Unit) {
    request = navigation.launch(scope, action = action)
  }

  fun pause() = launch {}

  fun cancel() {
    request?.let(navigation::cancel)
    request = null
  }

  fun viewportHeight(contentHeight: Int): Int = navigation.viewportHeight(contentHeight)
}

internal val LocalChatReaderNavigation = staticCompositionLocalOf<ChatReaderNavigation?> { null }

@Composable
internal fun rememberChatReaderAction(): ChatReaderAction {
  val scope = rememberCoroutineScope()
  val navigation = LocalChatReaderNavigation.current ?: remember(scope) { ChatReaderNavigation(scope) }
  val action = remember(scope, navigation) { ChatReaderAction(scope, navigation) }
  DisposableEffect(action) { onDispose { action.cancel() } }
  return action
}

@Composable
internal fun rememberChatReaderScrollController(
  sessionKey: String,
  timeline: ChatTimeline,
  historyLoading: Boolean,
): ChatReaderScrollController {
  val listState = rememberLazyListState()
  val scope = key(sessionKey) { rememberCoroutineScope() }
  val targetTolerancePx = with(LocalDensity.current) { 24.dp.roundToPx() }
  val currentTimeline by rememberUpdatedState(timeline)
  val readerStateSaver = remember(sessionKey) { createChatReaderStateSaver(sessionKey) }
  var readerState by
    rememberSaveable(sessionKey, stateSaver = readerStateSaver) {
      mutableStateOf(ChatReaderState(ownerSessionKey = sessionKey))
    }
  var isUserScrolling by remember(sessionKey) { mutableStateOf(false) }

  val navigation =
    remember(scope) {
      ChatReaderNavigation(scope, listState) {
        // Explicit reading replaces the gesture; its idle must not restore following.
        // The viewport still hides Jump while the latest content remains visible.
        isUserScrolling = false
        readerState = readerState.copy(followTarget = null, hasNewerContent = true)
      }
    }

  DisposableEffect(navigation) {
    onDispose {
      navigation.retire()
      navigation.anchors.clearReading()
    }
  }
  LaunchedEffect(navigation) {
    listState.interactionSource.interactions.collect { interaction ->
      if (interaction is DragInteraction.Start) navigation.pause()
    }
  }

  val nestedScroll =
    remember(sessionKey) {
      object : NestedScrollConnection {
        override fun onPreScroll(
          available: Offset,
          source: NestedScrollSource,
        ): Offset {
          // A code viewport can consume the whole drag without scrolling the transcript.
          // Its reader intent must still retire follow, without consuming the gesture.
          if (source == NestedScrollSource.UserInput && available.y != 0f) {
            navigation.anchors.invalidate()
            readerState = readerState.copy(followTarget = null)
            // Bring-into-view also emits UserInput; do not cancel its own reveal.
            navigation.cancelAutomatic()
          }
          return Offset.Zero
        }
      }
    }

  suspend fun applyTransition(transition: ChatReaderTransition) =
    coroutineScope {
      readerState = transition.state
      if (transition.state.followTarget != null) navigation.anchors.clearReading()
      val index = transition.scrollIndex ?: return@coroutineScope
      navigation
        .launch(this, automatic = true) {
          if (transition.animated) {
            listState.animateScrollToItem(index)
          } else {
            listState.scrollToItem(index)
          }
        }.join()
      // The request has landed: from here on the ordinary "is the reader really at
      // the bottom?" check answers for itself.
      if (readerState === transition.state) readerState = transition.state.copy(followRequested = false)
    }

  // Loading only changes empty-timeline transitions. A populated-history refresh
  // must not cancel a moving scroll after its content version has been recorded.
  var lastTimeline by remember(sessionKey) { mutableStateOf(timeline) }
  var lastRowKeys by remember(sessionKey) { mutableStateOf<List<String>>(emptyList()) }
  // The reader's seat as the frame that inserted rows last saw it. LazyList keeps the
  // old first row in place by key, so by the time the follow transition runs the live
  // edge has moved to index 1 and a follower would read as "scrolled up".
  var wasAtLiveEdge by remember(sessionKey) { mutableStateOf(true) }
  SideEffect {
    val rowKeys = timeline.items.map(::chatTimelineItemKey)
    if (rowKeys == lastRowKeys) return@SideEffect
    // Row structure changed (a new chain row, a frozen phase, or the
    // live->persisted swap). Plain text deltas keep identical keys and skip this.
    val previous = lastTimeline
    lastTimeline = timeline
    lastRowKeys = rowKeys
    val layout = listState.layoutInfo
    if (layout.visibleItemsInfo.isNotEmpty()) {
      wasAtLiveEdge =
        isAtTarget(
          listState.firstVisibleItemIndex,
          listState.firstVisibleItemScrollOffset,
          previous.latestContentIndex,
          targetTolerancePx,
        )
    }
    if (!readerState.initialized) return@SideEffect
    // Reader rule: only a reader parked at the bottom follows. While scrolled
    // away, keep the visible rows at their exact pixels.
    if (readerState.followTarget != null) return@SideEffect
    if (listState.isScrollInProgress || navigation.isNavigating) return@SideEffect
    if (layout.visibleItemsInfo.isEmpty()) return@SideEffect
    val indexByKey = HashMap<String, Int>(rowKeys.size)
    rowKeys.forEachIndexed { index, key -> indexByKey[key] = index }
    // Bottom-most visible row whose key survives the change; the live rows near
    // index 0 are the ones that vanish, so prefer a durable one.
    val anchor =
      layout.visibleItemsInfo.firstNotNullOfOrNull { info ->
        val key = previous.items.getOrNull(info.index)?.let(::chatTimelineItemKey)
          ?: return@firstNotNullOfOrNull null
        indexByKey[key]?.let { newIndex -> newIndex to info.offset }
      } ?: return@SideEffect
    val (newIndex, offset) = anchor
    // Applies during this frame's measurement: no jumped frame is ever drawn.
    // reverseLayout keeps the row's top in place with a negative offset.
    listState.requestScrollToItem(newIndex, -offset)
  }
  LaunchedEffect(sessionKey, timeline, historyLoading && timeline.items.isEmpty()) {
    val transition =
      if (readerState.initialized) {
        readerState.onTimelineChanged(
          timeline,
          historyLoading,
          listState,
          targetTolerancePx,
          wasAtLiveEdge = wasAtLiveEdge,
        )
      } else {
        initialChatReaderTransition(timeline, ownerSessionKey = sessionKey)
      }
    applyTransition(transition)
  }

  LaunchedEffect(sessionKey) {
    var previousViewport: ChatReaderViewport? = null
    snapshotFlow {
      ChatReaderViewport(
        listState.isScrollInProgress,
        listState.firstVisibleItemIndex,
        listState.firstVisibleItemScrollOffset,
        navigation.anchors.revision,
        listState.layoutInfo,
        listState.canScrollBackward || listState.canScrollForward,
        navigation.isNavigating,
      )
    }.collect { viewport ->
      // Observe geometry during navigation without replaying its suppressed resize later.
      val previous = previousViewport
      previousViewport = viewport
      if (!readerState.initialized || viewport.navigating) return@collect
      val (scrolling, index, offset) = viewport
      val resizedToFit =
        previous != null && !previous.navigating && !previous.scrolling && previous.canScroll &&
          !viewport.canScroll && viewport.layout.totalItemsCount > 0 &&
          previous.layout.viewportSize != viewport.layout.viewportSize
      if (scrolling) {
        navigation.anchors.clearReading()
        isUserScrolling = true
        readerState = readerState.copy(followTarget = null)
        return@collect
      } else if (isUserScrolling || resizedToFit) {
        isUserScrolling = false
        readerState = readerState.onViewportChanged(index, offset, currentTimeline, targetTolerancePx)
      }
      // A reader who scrolled away owns the viewport: whatever streams in below must
      // not move the glyph they are looking at. The anchor engine measures that glyph
      // and scrolls by the delta the new layout introduced, in this same frame, so the
      // correction is never drawn as a jump. Following the live edge stays owned by
      // the explicit followTarget transitions above.
      if (readerState.followTarget == null && !viewport.navigating) {
        navigation.anchors.correct { delta -> listState.dispatchRawDelta(delta) }
      }
    }
  }

  // reverseLayout puts the latest tail at the viewport start. Scrolling within
  // content padding does not hide text; this geometry must not change follow intent.
  val latestContentHidden by
    remember(listState) {
      derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val latestItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentTimeline.latestContentIndex }
        latestItem?.let { it.offset < layoutInfo.viewportStartOffset } ?: listState.canScrollBackward
      }
    }
  return ChatReaderScrollController(
    listState = listState,
    showJumpToLatest = readerState.hasNewerContent && timeline.items.isNotEmpty() && latestContentHidden,
    scrolling = listState.isScrollInProgress,
    jumpToLatest = {
      navigation.retire()
      scope.launch(start = CoroutineStart.UNDISPATCHED) {
        applyTransition(readerState.jumpToLatest(currentTimeline))
      }
    },
    navigation = navigation,
    nestedScrollConnection = nestedScroll,
  )
}

internal fun initialChatReaderTransition(
  timeline: ChatTimeline,
  ownerSessionKey: String? = null,
): ChatReaderTransition {
  val initialIndex = timeline.latestContentIndex
  return ChatReaderTransition(
    state =
      ChatReaderState(
        ownerSessionKey = ownerSessionKey,
        initialized = initialIndex != null,
        followTarget = initialIndex?.let { ChatScrollFollowTarget.LatestContent },
        // Opening a transcript is a request for the live edge, even before the list
        // has measured it once.
        followRequested = initialIndex != null,
        latestUserMessageId = timeline.latestUserMessageId,
        latestUserMessageVersion = timeline.latestUserMessageVersion,
        latestContentVersion = timeline.latestContentVersion,
      ),
    scrollIndex = initialIndex,
  )
}

internal fun ChatReaderState.onTimelineChanged(
  timeline: ChatTimeline,
  historyLoading: Boolean = false,
  listStateForFollow: LazyListState? = null,
  targetTolerancePx: Int = 0,
  /** The reader was at the live edge before this change inserted rows. */
  wasAtLiveEdge: Boolean = false,
): ChatReaderTransition {
  if (timeline.items.isEmpty()) {
    return ChatReaderTransition(
      state = if (historyLoading) this else ChatReaderState(ownerSessionKey = ownerSessionKey),
    )
  }
  if (timeline.latestContentVersion == latestContentVersion) {
    return ChatReaderTransition(state = this)
  }
  val previousUserStillPresent =
    if (latestUserMessageVersion == null) {
      latestUserMessageId == null
    } else {
      latestUserMessageId?.let(timeline::containsMessage) == true ||
        timeline.containsUserMessageVersion(latestUserMessageVersion)
    }
  if (!previousUserStillPresent) {
    return ChatReaderTransition(
      state =
        copy(
          followTarget = null,
          hasNewerContent = false,
          latestUserMessageId = timeline.latestUserMessageId,
          latestUserMessageVersion = timeline.latestUserMessageVersion,
          latestContentVersion = timeline.latestContentVersion,
        ),
    )
  }
  val hasNewUserTurn =
    timeline.latestUserMessageVersion != null && timeline.latestUserMessageVersion != latestUserMessageVersion
  if (hasNewUserTurn) {
    // A live turn follows the bottom so the reply streams into view (parity with the
    // iOS reader, #108692/#108693). Re-pinning the prompt here would hide the reply
    // below the fold behind a jump pill.
    return ChatReaderTransition(
      state =
        copy(
          followTarget = ChatScrollFollowTarget.LatestContent,
          // The turn's first tokens can arrive before the entrance scroll lands.
          // Without this the "is the reader at the bottom?" guard vetoes the follow
          // it was just given, which is what left a sent message parked mid-list.
          followRequested = true,
          hasNewerContent = false,
          latestUserMessageId = timeline.latestUserMessageId,
          latestUserMessageVersion = timeline.latestUserMessageVersion,
          latestContentVersion = timeline.latestContentVersion,
        ),
      scrollIndex = timeline.latestContentIndex ?: timeline.readAnchorIndex,
      animated = true,
    )
  }

  val target = followTarget
  if (target == null) {
    return ChatReaderTransition(
      state =
        copy(
          hasNewerContent = true,
          latestUserMessageId = timeline.latestUserMessageId,
          latestUserMessageVersion = timeline.latestUserMessageVersion,
          latestContentVersion = timeline.latestContentVersion,
        ),
    )
  }

  // Only a reader genuinely parked at the bottom may follow the live edge. A
  // stale followTarget left over from before the user scrolled away must not
  // yank them to the bottom when the run completes. A target the reader just
  // asked for is exempt: the list may not have arrived yet.
  val atBottom =
    followRequested ||
      wasAtLiveEdge ||
      (listStateForFollow?.let { state ->
        isAtTarget(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset, timeline.latestContentIndex, targetTolerancePx)
      } ?: true)
  if (target == ChatScrollFollowTarget.LatestContent && !atBottom) {
    return ChatReaderTransition(
      state =
        copy(
          followTarget = null,
          followRequested = false,
          hasNewerContent = true,
          latestUserMessageId = timeline.latestUserMessageId,
          latestUserMessageVersion = timeline.latestUserMessageVersion,
          latestContentVersion = timeline.latestContentVersion,
        ),
    )
  }

  val targetIndex = timeline.indexForFollowTarget(target)
  return ChatReaderTransition(
    state =
      copy(
        hasNewerContent = target == ChatScrollFollowTarget.ReadAnchor && targetIndex != timeline.latestContentIndex,
        latestUserMessageId = timeline.latestUserMessageId,
        latestUserMessageVersion = timeline.latestUserMessageVersion,
        latestContentVersion = timeline.latestContentVersion,
      ),
    scrollIndex = targetIndex,
  )
}

internal fun ChatReaderState.onViewportChanged(
  index: Int,
  offset: Int,
  timeline: ChatTimeline,
  targetTolerancePx: Int,
): ChatReaderState {
  val nextTarget =
    if (isAtTarget(index, offset, timeline.latestContentIndex, targetTolerancePx)) {
      ChatScrollFollowTarget.LatestContent
    } else {
      null
    }
  return copy(
    followTarget = nextTarget,
    // A settled viewport answers the question the request was asking, either way.
    followRequested = false,
    hasNewerContent = nextTarget == null && timeline.latestContentIndex != null,
  )
}

internal fun ChatReaderState.jumpToLatest(timeline: ChatTimeline): ChatReaderTransition =
  ChatReaderTransition(
    state = copy(followTarget = ChatScrollFollowTarget.LatestContent, hasNewerContent = false, followRequested = true),
    scrollIndex = timeline.latestContentIndex ?: timeline.readAnchorIndex,
    animated = true,
  )

private fun ChatTimeline.indexForFollowTarget(target: ChatScrollFollowTarget): Int? =
  when (target) {
    ChatScrollFollowTarget.ReadAnchor -> readAnchorIndex
    ChatScrollFollowTarget.LatestContent -> latestContentIndex
  }

private fun ChatTimeline.containsMessage(id: String): Boolean =
  items
    .filterIsInstance<ChatTimelineItem.Message>()
    .any { item -> item.message.id == id }

private fun isAtTarget(
  index: Int,
  offset: Int,
  target: Int?,
  tolerancePx: Int,
): Boolean = target != null && index == target && offset <= tolerancePx

/** Where "park this turn's prompt under the bar" lands, in the reader's coordinates. */
internal data class ChatTurnTopJump(
  /** Row that touches the prompt's top edge: the list is newest-first, so it is the prompt's index + 1. */
  val anchorIndex: Int,
  /** `scrollToItem` offset that puts the anchor's bottom edge at [topPx]. */
  val scrollOffset: Int,
  /** The prompt already sits at [topPx]: the pill has nothing to offer. */
  val aligned: Boolean,
)

/**
 * Index of the user prompt that opened the turn being read at [fromIndex].
 * Chain rows (reasoning, tools) never interrupt the walk, so the search stops at
 * the first user message at or above the row the reader is looking at.
 */
internal fun chatTurnPromptIndex(
  items: List<ChatTimelineItem>,
  fromIndex: Int,
): Int? {
  for (index in fromIndex.coerceAtLeast(0) until items.size) {
    val message = (items[index] as? ChatTimelineItem.Message)?.message ?: continue
    if (message.role.trim().equals("user", ignoreCase = true)) return index
  }
  return null
}

/**
 * Jump that parks the current turn's prompt just below the top bar.
 *
 * The jump anchors the row *above* the prompt (index + 1 in a `reverseLayout`
 * list) instead of the prompt itself: that row's bottom edge is the prompt's top
 * edge, and in a `reverseLayout` list a row's bottom edge is
 * `viewportEndOffset - offset`. Aiming it at [topPx] needs no knowledge of the
 * prompt's measured height (which is not available while the row is off screen).
 *
 * The oldest prompt has no row above it and the list already ends there, so the
 * jump is not offered.
 */
internal fun chatTurnTopJump(
  layout: LazyListLayoutInfo,
  items: List<ChatTimelineItem>,
  topPx: Int,
  tolerancePx: Int,
): ChatTurnTopJump? {
  // Visible rows are ordered bottom-up, so the last one is the row at the top.
  val topIndex = layout.visibleItemsInfo.lastOrNull()?.index ?: return null
  val promptIndex = chatTurnPromptIndex(items, topIndex) ?: return null
  if (promptIndex == items.lastIndex) return null
  val anchorIndex = promptIndex + 1
  val anchorBottom =
    layout.visibleItemsInfo
      .firstOrNull { info -> info.index == anchorIndex }
      ?.let { info -> layout.viewportEndOffset - info.offset }
  return ChatTurnTopJump(
    anchorIndex = anchorIndex,
    scrollOffset = topPx - layout.viewportEndOffset,
    aligned = anchorBottom != null && abs(anchorBottom - topPx) <= tolerancePx,
  )
}

/** Gentle launch, long settle. Never a spring snap at either end. */
internal val ChatTurnTopJumpEasing: Easing = CubicBezierEasing(0.34f, 0f, 0.14f, 1f)
private const val ChatTurnTopJumpMinMs = 320
private const val ChatTurnTopJumpMaxMs = 720

/**
 * Lands the row above the turn's prompt on [topPx] in one eased pixel scroll.
 *
 * `animateScrollToItem` walks to the target item by item, restarting a spring on
 * every hop, which is what made the pill's jump snap. The distance is instead
 * read off the rows that are measured: the anchor itself, the prompt hanging
 * under it, or the reply below the prompt (that one lands on the prompt's foot,
 * one row short, and the settle trims it). Only when none of them is composed
 * does this fall back to the stock walk.
 */
internal suspend fun LazyListState.animateTurnTopJump(
  anchorIndex: Int,
  scrollOffset: Int,
  topPx: Int,
  easing: Easing = ChatTurnTopJumpEasing,
) {
  val layout = layoutInfo
  val promptIndex = anchorIndex - 1
  val probe =
    layout.visibleItemsInfo
      .firstOrNull { info -> info.index == anchorIndex }
      ?.let { info -> layout.viewportEndOffset - info.offset - topPx }
      ?: layout.visibleItemsInfo
        .firstOrNull { info -> info.index == promptIndex }
        ?.let { info -> layout.viewportEndOffset - info.offset - info.size - topPx }
      ?: layout.visibleItemsInfo
        .firstOrNull { info -> info.index == promptIndex - 1 }
        ?.let { info -> layout.viewportEndOffset - info.offset - info.size - topPx }
  if (probe != null && abs(probe) > 1) {
    val viewport = (layout.viewportEndOffset - layout.viewportStartOffset).coerceAtLeast(1)
    val stretch = abs(probe).toFloat() / viewport * 220f
    val duration = (ChatTurnTopJumpMinMs + stretch).toInt().coerceIn(
      ChatTurnTopJumpMinMs,
      ChatTurnTopJumpMaxMs,
    )
    // Scrolling forward in a reverseLayout list pushes rows down the screen, so
    // a row sitting above the line needs a positive delta.
    animateScrollBy(value = -probe.toFloat(), animationSpec = tween(duration, easing = easing))
  }
  // Exact landing for the part the eased leg could not measure; leftover is at
  // most one row, so this reads as the tail of the same motion.
  animateScrollToItem(anchorIndex, scrollOffset)
}
