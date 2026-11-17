package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatLivePhaseCheckpoint
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.chat.ChatOutboxItem
import ai.openclaw.app.chat.ChatOutboxStatus
import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.chat.ChatQuestionPrompt
import ai.openclaw.app.chat.ChatSubagentActivity
import ai.openclaw.app.chat.ChatToolActivity
import ai.openclaw.app.chat.OUTBOX_OWNER_CHANGED_ERROR
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.resolveAgentIdFromMainSessionKey
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * How long a live tool row may trail the newest transcript row before it is
 * treated as a leftover. Generous: a tool may legitimately outlive the row that
 * announced it, and dropping a row that is genuinely running is worse than
 * briefly showing one that is not.
 */
private const val CHAT_LIVE_TOOL_CALL_MAX_AGE_MS = 10 * 60 * 1000L

internal sealed class ChatTimelineItem {  data class Message(
    val message: ChatMessage,
    val turnBoundary: Boolean = message.turnBoundary,
    /** Elapsed time of the completed agent turn this message concludes; shown next to the timestamp. */
    val workDurationMs: Long? = null,
    /** False for mid-chain assistant rows: one clock per chain, at the final message. */
    val showTimestamp: Boolean = true,
  ) : ChatTimelineItem()

  /** Durable queued/failed offline command shown below the transcript until acked or deleted. */
  data class OutboxCommand(
    val item: ChatOutboxItem,
  ) : ChatTimelineItem()

  /** Gateway-level recovery row that cannot be placed in the visible owner/session. */
  data class RecoveryOutboxCommand(
    val item: ChatOutboxItem,
  ) : ChatTimelineItem()

  data class OutboxRecoveryHeader(
    val count: Int,
  ) : ChatTimelineItem()

  data class StreamingAssistant(
    val text: String,
    /** Distinguishes the live phase segments that share one run. */
    val key: String = "stream",
    /** True only for the segment still receiving text; frozen phases are static. */
    val liveTail: Boolean = true,
  ) : ChatTimelineItem()

  data class PendingTools(
    val toolCalls: List<ChatPendingToolCall>,
    /** Distinguishes one live phase's tool row from the others. */
    val key: String = "tools",
  ) : ChatTimelineItem()

  data class CompletedTools(
    val key: String,
    val tools: List<ChatToolActivity>,
    val turnBoundary: Boolean = false,
    /**
     * Tool calls the live run still reports as in flight. The transcript already
     * carries these rows while they run — it is written as soon as a call
     * starts — so without this they would read 已执行 from the very first frame.
     * Rows listed here read 正在… until the run reports them finished.
     */
    val runningIds: Set<String> = emptySet(),
  ) : ChatTimelineItem()

  data class SubagentActivity(
    val activities: List<ChatSubagentActivity>,
    val moreWorkingCount: Int = 0,
  ) : ChatTimelineItem()

  data class QuestionPrompt(
    val prompt: ChatQuestionPrompt,
  ) : ChatTimelineItem()

  data class TurnRecapSummary(
    val recap: TurnRecap,
  ) : ChatTimelineItem()

  data class SystemNotice(
    val key: String,
    val label: String,
    val body: String,
  ) : ChatTimelineItem()

  data class SystemDivider(
    val key: String,
    val kind: SystemDividerKind,
    val label: String,
    val metric: String? = null,
    val secondary: String? = null,
  ) : ChatTimelineItem()

  data class SkillLearnedNotice(
    val key: String,
    val skillName: String,
    val proposalId: String?,
  ) : ChatTimelineItem()

  /**
   * One reasoning block in the turn chain: thinking -> tool -> thinking -> answer.
   * [text] stays empty while the gateway has streamed no reasoning yet; the row
   * then falls back to the plain typing indicator.
   */
  /**
   * A run of chain rows (reasoning / tools) that already sits above answer
   * prose. Long turns bury the answer, so the finished part of the run folds
   * into a single "多个步骤" row that expands in place.
   */
  data class CollapsedSteps(
    val key: String,
    /** Oldest first: the order the rows used to render in, top to bottom. */
    val steps: List<ChatTimelineItem>,
  ) : ChatTimelineItem()

  data class Thinking(
    val text: String,
    val streaming: Boolean = false,
    /** Stable identity for historical reasoning rows so the list can key them apart. */
    val key: String? = null,
  ) : ChatTimelineItem()
}

internal enum class SystemDividerKind {
  Compaction,
  Reset,
}

/** Every tool call the transcript already has a finished row for. */
private fun transcriptToolCallIds(messages: List<ChatMessage>): Set<String> {
  val ids = HashSet<String>()
  messages.forEach { message ->
    message.content.forEach { part ->
      val toolCallId = part.toolActivity?.toolCallId?.trim().takeIf { !it.isNullOrEmpty() }
      if (toolCallId != null) ids.add(toolCallId)
    }
  }
  return ids
}

/** Prefix overlap used to hide a persisted copy while its live row is mounted. */
private fun thinkingTextsOverlap(left: String, right: String): Boolean {
  val a = left.trim()
  val b = right.trim()
  if (a.isEmpty() || b.isEmpty()) return false
  return a.startsWith(b) || b.startsWith(a)
}

internal data class ChatTimeline(
  val items: List<ChatTimelineItem>,
  val readAnchorIndex: Int?,
  val latestContentIndex: Int?,
  val latestUserMessageId: String?,
  val latestUserMessageVersion: String?,
  val latestContentVersion: String,
)

internal fun buildChatTimeline(
  messages: List<ChatMessage>,
  pendingRunCount: Int,
  pendingToolCalls: List<ChatPendingToolCall>,
  streamingAssistantText: String?,
  streamingThinkingText: String? = null,
  livePhaseCheckpoints: List<ChatLivePhaseCheckpoint> = emptyList(),
  /** A whole user turn is still awaiting its answer, including subagent gaps. */
  turnWorking: Boolean = false,
  subagentActivities: Map<String, ChatSubagentActivity> = emptyMap(),
  outboxItems: List<ChatOutboxItem> = emptyList(),
  recoveryOutboxItems: List<ChatOutboxItem> = emptyList(),
  questions: List<ChatQuestionPrompt> = emptyList(),
): ChatTimeline {
  // Preserve the exact stream prefix. Trimming every snapshot changes the
  // parser input while the model is writing markdown and can make the live row
  // recompose as a different answer on each delta.
  val stream = streamingAssistantText?.takeIf { it.isNotBlank() }
  val thinking = streamingThinkingText?.trim()?.takeIf { it.isNotEmpty() }
  // The transcript is written as soon as a call starts, so it usually already
  // holds a row for a tool that is still running. While ordered live phases are
  // active the phase block renders the current turn's tool rows (interleaved
  // with reasoning) and the transcript hides those same rows below; the settled
  // filter here only feeds the legacy stack, orphan rows and liveness checks.
  val settledToolCallIds = transcriptToolCallIds(messages)
  // A running row whose start event never got its matching end would otherwise
  // stay on screen forever. The transcript already carries these rows, so a live
  // row older than the turn itself can only be a leftover: drop it and let the
  // transcript own the row. Freshness is measured against the newest tool the
  // transcript knows about, which advances as the turn progresses.
  val newestSettledToolMs =
    messages
      .mapNotNull { it.timestampMs }
      .filter { ms -> ms > 0L }
      .maxOrNull()
  val livePendingToolCalls =
    if (newestSettledToolMs == null) {
      pendingToolCalls
    } else {
      val cutoff = newestSettledToolMs - CHAT_LIVE_TOOL_CALL_MAX_AGE_MS
      pendingToolCalls.filter { it.startedAtMs >= cutoff }
    }
  val runningIds = livePendingToolCalls.filterNot { it.done }.mapTo(HashSet()) { it.toolCallId }
  val liveToolCalls = livePendingToolCalls.filterNot { it.toolCallId in settledToolCallIds }
  val visibleSubagents = visibleSubagentActivities(subagentActivities.values)
  // Gateways that only emit the projected text (or runs without a tool) keep
  // the legacy single live stack; ordered phases need at least one checkpoint.
  val hasLivePhases = livePhaseCheckpoints.isNotEmpty()
  val liveThinking = thinking
  val livePhaseItems =
    if (hasLivePhases) {
      buildLivePhaseItems(
        stream = stream,
        thinking = thinking,
        // Pass the full pending list (incl. calls already persisted) so the phase
        // block keeps its tool anchors and renders thinking -> tool interleaved.
        // The transcript hides those same rows below to avoid doubling.
        liveToolCalls = livePendingToolCalls,
        checkpoints = livePhaseCheckpoints,
        pendingRunCount = pendingRunCount,
        turnWorking = turnWorking,
      )
    } else {
      emptyList()
    }
  val liveTurnActive =
    pendingRunCount > 0 || turnWorking || stream != null || thinking != null || liveToolCalls.isNotEmpty()
  val phaseToolIds =
    livePhaseItems
      .asSequence()
      .filterIsInstance<ChatTimelineItem.PendingTools>()
      .flatMap { it.toolCalls.asSequence() }
      .mapTo(HashSet()) { it.toolCallId }
  // While the run is live the live reasoning row is canonical. Hide a persisted
  // copy from the same turn instead of hiding the live row: the old direction
  // made the thinking jump from the live tail into the transcript mid-stream.
  val liveThinkingTexts =
    buildList {
        if (hasLivePhases) {
          livePhaseItems.filterIsInstance<ChatTimelineItem.Thinking>().forEach { add(it.text) }
        }
        liveThinking?.let(::add)
      }
      .map(String::trim)
      .filter(String::isNotEmpty)
      .distinct()
  val transcriptItems = buildTranscriptTimeline(messages).asReversed()
  val newestUserIndex =
    transcriptItems.indexOfFirst { item ->
      (item as? ChatTimelineItem.Message)?.message?.role?.trim()?.equals("user", ignoreCase = true) == true
    }
  val visibleTranscriptItems =
    if (liveTurnActive && (liveThinkingTexts.isNotEmpty() || phaseToolIds.isNotEmpty())) {
      if (newestUserIndex < 0) {
        transcriptItems
      } else {
        transcriptItems.mapIndexedNotNull { index, item ->
          val inCurrentTurn = index < newestUserIndex
          when {
            inCurrentTurn && item is ChatTimelineItem.Thinking ->
              item.takeIf { persisted ->
                liveThinkingTexts.none { live -> thinkingTextsOverlap(live, persisted.text) }
              }
            // The live phase block owns these tool rows while the run is live;
            // hide the persisted copy here or the turn renders as two stacked
            // blocks (all tools above all thinking) instead of interleaved.
            inCurrentTurn && item is ChatTimelineItem.CompletedTools && phaseToolIds.isNotEmpty() -> {
              val remaining = item.tools.filterNot { it.toolCallId in phaseToolIds }
              if (remaining.isEmpty()) null else item.copy(tools = remaining)
            }
            else -> item
          }
        }
      }
    } else {
      transcriptItems
    }
  // A history tool row can land before its agent start event. Only the current
  // turn may still be in flight; older rows always have their result merged, so
  // fencing here keeps historical rows from reading 正在… during a new run.
  val currentTurnUnfinishedToolIds =
    if (liveTurnActive && newestUserIndex > 0) {
      transcriptItems
        .subList(0, newestUserIndex)
        .filterIsInstance<ChatTimelineItem.CompletedTools>()
        .flatMap { it.tools }
        .filter { it.result == null && !it.isError }
        .mapNotNull { it.toolCallId }
        .toSet()
    } else {
      emptySet()
    }
  val effectiveRunningIds = runningIds + currentTurnUnfinishedToolIds
  val items =
    // reverseLayout: index 0 renders bottom-most; queued commands are the newest user input.
    foldFinishedChainSteps(
      buildList {
          questions.asReversed().forEach { prompt -> add(ChatTimelineItem.QuestionPrompt(prompt)) }
          outboxItems.asReversed().forEach { item -> add(ChatTimelineItem.OutboxCommand(item)) }
          recoveryOutboxItems.asReversed().forEach { item -> add(ChatTimelineItem.RecoveryOutboxCommand(item)) }
          if (recoveryOutboxItems.isNotEmpty()) add(ChatTimelineItem.OutboxRecoveryHeader(recoveryOutboxItems.size))
          if (hasLivePhases) {
            addAll(livePhaseItems)
            if (visibleSubagents.activities.isNotEmpty()) {
              add(
                ChatTimelineItem.SubagentActivity(
                  activities = visibleSubagents.activities,
                  moreWorkingCount = visibleSubagents.moreWorkingCount,
                ),
              )
            }
            val orphanToolCalls = liveToolCalls.filterNot { it.toolCallId in phaseToolIds }
            if (orphanToolCalls.isNotEmpty()) {
              add(ChatTimelineItem.PendingTools(orphanToolCalls, key = "tools:orphan"))
            }
          } else {
            if (stream != null) add(ChatTimelineItem.StreamingAssistant(stream))
            if (liveToolCalls.isNotEmpty()) add(ChatTimelineItem.PendingTools(liveToolCalls))
            if (visibleSubagents.activities.isNotEmpty()) {
              add(
                ChatTimelineItem.SubagentActivity(
                  activities = visibleSubagents.activities,
                  moreWorkingCount = visibleSubagents.moreWorkingCount,
                ),
              )
            }
            // Keep an activity row during the gap between a finished reasoning row and
            // the first answer chunk; otherwise a buffered provider looks frozen.
            // Use the same keys the phase builder uses so the first tool
            // checkpoint moves this row instead of remounting it.
            if (liveThinking != null) {
              add(
                ChatTimelineItem.Thinking(
                  text = liveThinking,
                  // Collapse the preview as soon as answer prose exists; keeping it
                  // expanded for the whole run made the last thinking linger open
                  // while the answer streamed (legacy stack has no phase diff).
                  streaming = stream == null,
                  key = "live-current",
                ),
              )
            } else if ((pendingRunCount > 0 || turnWorking) && stream == null) {
              add(ChatTimelineItem.Thinking(text = "", streaming = true, key = "live-waiting"))
            }
          }
          addAll(visibleTranscriptItems)
        }
        .map { item ->
          if (item !is ChatTimelineItem.CompletedTools) return@map item
          if (effectiveRunningIds.isEmpty() || item.tools.none { it.toolCallId in effectiveRunningIds }) return@map item
          item.copy(runningIds = effectiveRunningIds)
        },
      // Older turns stay folded during the live run too; only the newest turn is
      // exempt (handled inside). Unfolding them while the answer streamed made the
      // whole transcript re-layout on completion, which read as the reader's
      // viewport jumping a screenful.
      enabled = true,
    )
  if (items.isEmpty()) {
    return ChatTimeline(
      items = items,
      readAnchorIndex = null,
      latestContentIndex = null,
      latestUserMessageId = null,
      latestUserMessageVersion = null,
      latestContentVersion = "",
    )
  }

  val latestUserMessage =
    items.firstNotNullOfOrNull { item ->
      val message = (item as? ChatTimelineItem.Message)?.message ?: return@firstNotNullOfOrNull null
      message.takeIf { it.role.trim().equals("user", ignoreCase = true) }
    }
  val latestUserIndex =
    items.indexOfFirst { item ->
      item is ChatTimelineItem.Message &&
        item.message.id == latestUserMessage?.id
    }
  val latestContentIndex = 0
  // In reverseLayout, index 0 is bottom-most. Keep the latest prompt as a stable
  // reader anchor even after streaming rows collapse into a finished reply.
  val readAnchorIndex = latestUserIndex.takeIf { it >= 0 } ?: latestContentIndex

  return ChatTimeline(
    items = items,
    readAnchorIndex = readAnchorIndex,
    latestContentIndex = latestContentIndex,
    latestUserMessageId = latestUserMessage?.id,
    latestUserMessageVersion = latestUserMessage?.let(::stableMessageVersion),
    latestContentVersion =
      latestContentVersion(
        messages,
        pendingRunCount,
        liveToolCalls,
        visibleSubagents.activities,
        visibleSubagents.moreWorkingCount,
        stream,
        thinking,
        livePhaseCheckpoints,
        turnWorking,
        outboxItems + recoveryOutboxItems,
        questions,
      ),
  )
}

private data class LivePhase(
  val thinking: String,
  val text: String,
  val tool: ChatPendingToolCall?,
  val index: Int,
)

/**
 * Rebuilds the live turn as ordered phases. Tool starts snapshot both buffers,
 * so consecutive snapshots diff back into the sequence the agent actually
 * produced: thinking -> prose -> tool -> thinking -> answer.
 * Items are newest-first because the transcript uses reverseLayout.
 */
private fun buildLivePhaseItems(
  stream: String?,
  thinking: String?,
  liveToolCalls: List<ChatPendingToolCall>,
  checkpoints: List<ChatLivePhaseCheckpoint>,
  pendingRunCount: Int,
  turnWorking: Boolean,
): List<ChatTimelineItem> {
  val fullText = stream.orEmpty()
  val fullThinking = thinking.orEmpty()
  val liveToolsById = liveToolCalls.associateBy { it.toolCallId }
  val phases = mutableListOf<LivePhase>()
  var previousText = ""
  var previousThinking = ""
  checkpoints.forEachIndexed { index, checkpoint ->
    val phaseText = checkpoint.textSnapshot.removePrefixOrSelf(previousText).trimStart('\n')
    val phaseThinking = checkpoint.thinkingSnapshot.removePrefixOrSelf(previousThinking).trimStart('\n')
    // The caller passes the full pending list (including calls already persisted
    // to the transcript), so a settled call keeps its tool anchor here and the
    // phase renders thinking -> tool interleaved; the transcript hides the same
    // row. Keep the phase even when the tool is absent from the pending list:
    // dropping it also dropped its reasoning and prose, which is what made the
    // first "思考完成" vanish until the next one arrived.
    phases +=
      LivePhase(
        thinking = phaseThinking,
        text = phaseText,
        tool = liveToolsById[checkpoint.toolCallId],
        index = index,
      )
    previousText = checkpoint.textSnapshot
    previousThinking = checkpoint.thinkingSnapshot
  }
  val currentText = fullText.removePrefixOrSelf(previousText).trimStart('\n')
  val currentThinking = fullThinking.removePrefixOrSelf(previousThinking).trimStart('\n')
  phases += LivePhase(thinking = currentThinking, text = currentText, tool = null, index = checkpoints.size)

  val current = phases.last()
  val currentThinkingVisible = current.thinking.takeIf { it.isNotBlank() }
  val items = mutableListOf<ChatTimelineItem>()
  if (current.text.isNotBlank()) {
    items +=
      ChatTimelineItem.StreamingAssistant(
        text = current.text,
        key = if (checkpoints.isEmpty()) "stream" else "stream:live:${checkpoints.size}",
      )
  }
  if (currentThinkingVisible != null) {
    items +=
      ChatTimelineItem.Thinking(
        text = currentThinkingVisible,
        streaming = pendingRunCount > 0 && current.text.isBlank(),
        key = if (checkpoints.isEmpty()) "live-current" else "live-thinking:${checkpoints.size}",
      )
  } else if ((pendingRunCount > 0 || turnWorking) && current.text.isBlank()) {
    // No reasoning streamed yet (or the model is not thinking): keep the
    // activity indicator below the last step while the run is still alive.
    items += ChatTimelineItem.Thinking(text = "", streaming = true, key = "live-waiting")
  }
  phases.dropLast(1).asReversed().forEach { phase ->
    phase.tool?.let {
      items += ChatTimelineItem.PendingTools(listOf(it), key = "tools:${it.toolCallId}")
    }
    if (phase.text.isNotBlank()) {
      // Reuse the key the segment had while it was the live tail, so freezing
      // it above the tool moves the row instead of remounting its text.
      items +=
        ChatTimelineItem.StreamingAssistant(
          text = phase.text,
          key = if (phase.index == 0) "stream" else "stream:live:${phase.index}",
          liveTail = false,
        )
    }
    val thinkingText = phase.thinking.takeIf { it.isNotBlank() }
    if (thinkingText != null) {
      // Same key the row had while it was the live thinking buffer, so a tool
      // start freezes it in place instead of remounting the chain row.
      items +=
        ChatTimelineItem.Thinking(
          text = thinkingText,
          streaming = false,
          key = if (phase.index == 0) "live-current" else "live-thinking:${phase.index}",
        )
    }
  }
  return items
}

/** Snapshot diff: a replacement buffer is the whole new phase, not a suffix. */
private fun String.removePrefixOrSelf(prefix: String): String =
  if (prefix.isNotEmpty() && startsWith(prefix)) removePrefix(prefix) else this

/** Chain rows long enough to earn a fold; shorter runs stay fully visible. */
internal const val CHAT_STEP_FOLD_MIN = 3

/** Rows that each count as one agent step when folding a long chain. */
private fun ChatTimelineItem.isStepRow(): Boolean =
  when (this) {
    is ChatTimelineItem.Thinking,
    is ChatTimelineItem.PendingTools,
    is ChatTimelineItem.CompletedTools,
    is ChatTimelineItem.SubagentActivity,
    -> true

    else -> false
  }

/**
 * A row that carries answer prose. Everything stacked above it is the finished
 * part of the turn and may be folded away.
 */
private fun ChatTimelineItem.isAnswerProseRow(): Boolean =
  when (this) {
    is ChatTimelineItem.StreamingAssistant -> true
    is ChatTimelineItem.Message ->
      message.role.trim().equals("assistant", ignoreCase = true) &&
        message.content.any { it.toolActivity == null && it.type == "text" && !it.text.isNullOrBlank() }

    else -> false
  }

/**
 * Folds the finished chain rows above every answer into one expandable row.
 *
 * A long turn (thinking -> tool -> thinking -> tool -> answer) buries the
 * answer, so once prose exists the rows above it collapse into a single
 * "多个步骤" summary that expands in place. Rows still being produced are never
 * folded: the scan only reaches above the newest answer row, which is why a
 * turn that is still thinking keeps showing its live row.
 */
internal fun foldFinishedChainSteps(
  items: List<ChatTimelineItem>,
  enabled: Boolean = true,
): List<ChatTimelineItem> {
  // The live turn keeps every step visible so the reader can follow
  // thinking -> tool -> thinking -> answer in order instead of watching the
  // chain collapse the moment prose starts.
  if (!enabled) return items
  // Keep the newest turn's process whole. Folding it the moment its answer
  // lands hides the thinking/tool sequence the reader just watched; older
  // turns still collapse to keep deep history compact.
  val newestUserIndex =
    items.indexOfFirst { item ->
      (item as? ChatTimelineItem.Message)?.message?.role?.trim()?.equals("user", ignoreCase = true) == true
    }
  val folded = ArrayList<ChatTimelineItem>(items.size)
  var index = 0
  while (index < items.size) {
    val item = items[index]
    if (!item.isAnswerProseRow()) {
      folded.add(item)
      index++
      continue
    }
    var end = index + 1
    while (end < items.size && items[end].isStepRow()) end++
    // reverseLayout renders higher indices further up, so the answer goes in
    // first and the fold rides above it.
    folded.add(item)
    val newestTurn = newestUserIndex < 0 || index < newestUserIndex
    if (!newestTurn && end - index - 1 >= CHAT_STEP_FOLD_MIN) {
      val steps = items.subList(index + 1, end).reversed()
      folded.add(
        ChatTimelineItem.CollapsedSteps(
          key = "steps:${chatTimelineItemKey(steps.first())}",
          steps = steps,
        ),
      )
    } else {
      folded.addAll(items.subList(index + 1, end))
    }
    index = end
  }
  return folded
}

// Gateway projects sessions_send user inputs as assistant rows; they still start a new turn.
internal fun ChatMessage.isForwardedBoundary(): Boolean =
  role.trim().equals("assistant", ignoreCase = true) &&
    provenance?.kind == "inter_session" && provenance.sourceTool == "sessions_send"

private data class SkillLearnedNoticeData(
  val skillName: String,
  val proposalId: String?,
)

/**
 * Skill Workshop is a Gateway-owned tool contract. Only its explicit create/apply
 * actions become a learned-skill notice; arbitrary tool titles and result prose do
 * not affect the chat timeline.
 */
private fun ChatToolActivity.skillLearnedNotice(): SkillLearnedNoticeData? {
  if (name.trim() != "skill_workshop" || isError) return null
  val action = arguments?.get("action").asStringOrNull() ?: return null
  if (action != "create" && action != "apply") return null
  val skillName =
    arguments
      ?.get("name")
      .asStringOrNull()
      ?.trim()
      ?.takeIf(String::isNotEmpty)
      ?: return null
  val proposalId =
    arguments
      ?.get("proposal_id")
      .asStringOrNull()
      ?.trim()
      ?.takeIf(String::isNotEmpty)
  return SkillLearnedNoticeData(skillName = skillName, proposalId = proposalId)
}

private fun kotlinx.serialization.json.JsonElement?.asStringOrNull(): String? =
  (this as? JsonPrimitive)?.contentOrNull

/** Build transcript rows in source order so hidden turn boundaries fence tool groups. */
private fun buildTranscriptTimeline(messages: List<ChatMessage>): List<ChatTimelineItem> {
  val toolsByMessage = projectTranscriptToolActivity(messages)
  val items = buildList {
    val completedTools = mutableListOf<ChatToolActivity>()
    var completedToolsKey: String? = null
    var completedToolsTurnBoundary = false
    var pendingTurnBoundary = false

    fun flushCompletedTools() {
      if (completedTools.isEmpty()) return
      completedTools.mapNotNull { it.skillLearnedNotice() }.forEach { notice ->
        add(
          ChatTimelineItem.SkillLearnedNotice(
            key = "${checkNotNull(completedToolsKey)}:${notice.proposalId ?: notice.skillName}",
            skillName = notice.skillName,
            proposalId = notice.proposalId,
          ),
        )
      }
      add(ChatTimelineItem.CompletedTools(checkNotNull(completedToolsKey), coalesceToolActivity(completedTools), completedToolsTurnBoundary))
      completedTools.clear()
      completedToolsKey = null
      completedToolsTurnBoundary = false
    }

    messages.forEachIndexed { index, message ->
      if (message.turnBoundary || message.isForwardedBoundary()) {
        flushCompletedTools()
        pendingTurnBoundary = true
      }
      val tools = toolsByMessage[index]
      val hasVisibleContent = message.content.any { it.toolActivity == null }
      // Empty or consumed result envelopes must not erase a pending turn boundary.
      if (tools.isEmpty() && !hasVisibleContent && message.transcriptMarker == null) return@forEachIndexed
      val key = message.entryId ?: message.idempotencyKey ?: message.id
      if (tools.isNotEmpty() && !hasVisibleContent && message.transcriptMarker == null) {
        if (completedTools.isEmpty()) {
          completedToolsKey = key
          completedToolsTurnBoundary = pendingTurnBoundary
          pendingTurnBoundary = false
        }
        completedTools.addAll(tools)
      } else {
        flushCompletedTools()
        tools.mapNotNull { it.skillLearnedNotice() }.forEach { notice ->
          add(ChatTimelineItem.SkillLearnedNotice("$key:${notice.proposalId ?: notice.skillName}", notice.skillName, notice.proposalId))
        }
        val classified = classifyTranscriptMessage(message, index)
        val reasoningRow = classified?.reasoningOnlyRow()
        if (reasoningRow != null) {
          add(reasoningRow)
          pendingTurnBoundary = false
        } else if (classified is ChatTimelineItem.Message) {
          add(classified.copy(turnBoundary = pendingTurnBoundary || classified.turnBoundary))
          pendingTurnBoundary = false
        } else {
          classified?.let(::add)
        }
        if (tools.isNotEmpty()) {
          add(ChatTimelineItem.CompletedTools(key, coalesceToolActivity(tools), pendingTurnBoundary))
          pendingTurnBoundary = false
        }
      }
    }
    flushCompletedTools()
  }
  return suppressMidChainTimestamps(reorderStrayThinkingRows(items))
}

/** Visible (non tool-envelope) parts of an assistant row, or null for other rows. */
private fun ChatTimelineItem.assistantVisibleParts(): List<ChatMessageContent>? {
  val message = (this as? ChatTimelineItem.Message)?.message ?: return null
  if (!message.role.trim().equals("assistant", ignoreCase = true)) return null
  return message.content.filter { it.toolActivity == null }.takeIf { it.isNotEmpty() }
}

/**
 * Historical reasoning lands in the transcript as its own assistant message.
 * Rendering it as a plain [ChatTimelineItem.Thinking] row (instead of a bubble)
 * keeps it pixel-identical to the tool rows and drops the bubble's padding,
 * which was what made the chain look so airy.
 */
private fun ChatTimelineItem.reasoningOnlyRow(): ChatTimelineItem.Thinking? {
  val visible = assistantVisibleParts() ?: return null
  if (!visible.all { it.type == "thinking" }) return null
  val text = visible.mapNotNull { it.text?.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")
  if (text.isEmpty()) return null
  val message = (this as ChatTimelineItem.Message).message
  return ChatTimelineItem.Thinking(
    text = text,
    streaming = false,
    key = message.entryId ?: message.idempotencyKey ?: message.id,
  )
}

/** Assistant bubble that carries answer text and can therefore host reasoning above it. */
private fun ChatTimelineItem.isMovableAnswerRow(): Boolean {
  if (this !is ChatTimelineItem.Message) return false
  if (turnBoundary || message.isForwardedBoundary()) return false
  val visible = assistantVisibleParts() ?: return false
  return visible.any { it.type == "text" && !it.text.isNullOrBlank() }
}

/**
 * The gateway sometimes persists a reasoning-only row after the short text it
 * actually preceded (text committed first, reasoning flushed after). That made
 * the "思考完成" row render below its own answer. Bubble such rows back above
 * the preceding assistant text of the same turn; turn boundaries and forwarded
 * rows are never crossed.
 *
 * Do NOT let a reasoning row climb over tool rows. Verified against real
 * gateway transcripts: reasoning and the calls it announces land in the *same*
 * assistant message (THINK + CALL + CALL …), so the persisted order is already
 * interleaved correctly and climbing over tool rows collapses it into
 * "all thinking first, all calls after".
 */
private fun reorderStrayThinkingRows(items: List<ChatTimelineItem>): List<ChatTimelineItem> {
  val result = items.toMutableList()
  var i = 1
  while (i < result.size) {
    val current = result[i]
    if (current is ChatTimelineItem.Thinking && !current.streaming) {
      var j = i
      while (j > 0) {
        if (!result[j - 1].isMovableAnswerRow()) break
        val previous = result[j - 1]
        result[j] = previous
        result[j - 1] = current
        j--
      }
    }
    i++
  }
  return result
}

/**
 * One clock per agent chain: when another assistant message of the same turn
 * follows (tool rows in between do not break the chain), the earlier bubble
 * hides its timestamp so only the final answer shows one.
 */
private fun suppressMidChainTimestamps(items: List<ChatTimelineItem>): List<ChatTimelineItem> {
  val result = items.toMutableList()
  for (i in result.indices) {
    val current = result[i] as? ChatTimelineItem.Message ?: continue
    if (!current.showTimestamp) continue
    if (!current.message.role.trim().equals("assistant", ignoreCase = true)) continue
    var j = i + 1
    while (j < result.size && result[j] !is ChatTimelineItem.Message) j++
    val next = result.getOrNull(j) as? ChatTimelineItem.Message ?: break
    val continuesChain =
      next.message.role.trim().equals("assistant", ignoreCase = true) &&
        !next.turnBoundary &&
        !next.message.isForwardedBoundary()
    if (continuesChain) {
      result[i] = current.copy(showTimestamp = false)
    }
  }
  return result
}

/**
 * Outbox rows for the visible session owner. Rows enqueued under the "main" alias still belong to the
 * canonical main session once the gateway hello rewrites the current key. Rows whose user turn
 * is already visible as a message (optimistic while a live run owns it, or the canonical history
 * copy right before the row retires) are hidden so one send never renders as two bubbles. Migrated
 * ownerless and unreachable legacy-main rows are excluded here and rendered only in the
 * gateway-level recovery section.
 */
internal fun outboxItemsForSession(
  items: List<ChatOutboxItem>,
  sessionKey: String,
  mainSessionKey: String,
  ownerAgentId: String,
  messages: List<ChatMessage> = emptyList(),
): List<ChatOutboxItem> {
  val mainKey = mainSessionKey.trim().ifEmpty { "main" }
  val current = sessionKey.trim().let { if (it == "main") mainKey else it }
  val visibleUserKeys =
    messages
      .mapNotNull { message -> message.idempotencyKey?.trim()?.takeIf { it.isNotEmpty() } }
      .toSet()
  return items.filter { item ->
    val itemKey = item.sessionKey.let { if (it == "main") mainKey else it }
    val ownerMatches = item.ownerAgentId == ownerAgentId
    ownerMatches &&
      itemKey == current &&
      "${item.id}:user" !in visibleUserKeys &&
      !isRecoveryOutboxItem(item)
  }
}

/** Rows with missing or internally contradictory ownership still need neutral controls. */
internal fun outboxItemsForRecovery(items: List<ChatOutboxItem>): List<ChatOutboxItem> = items.filter(::isRecoveryOutboxItem)

private fun isRecoveryOutboxItem(item: ChatOutboxItem): Boolean {
  val keyOwner = resolveAgentIdFromMainSessionKey(item.sessionKey)
  val parkedMainAlias =
    item.sessionKey.trim() == "main" &&
      item.status == ChatOutboxStatus.Failed &&
      item.lastError == OUTBOX_OWNER_CHANGED_ERROR
  return item.ownerAgentId == null ||
    (keyOwner != null && keyOwner != item.ownerAgentId) ||
    parkedMainAlias
}

private fun stableMessageVersion(message: ChatMessage): String {
  val role = message.role.trim().lowercase()
  val idempotencyKey = message.idempotencyKey?.trim().orEmpty()
  if (idempotencyKey.isNotEmpty()) return "$role:idempotency:$idempotencyKey"

  return buildString {
    append(role)
    append(':')
    append(message.timestampMs ?: "")
    message.content.forEach { appendContentVersion(it) }
  }
}

private fun StringBuilder.appendContentVersion(content: ChatMessageContent) {
  append(':')
  append(content.type)
  append('=')
  append(content.text?.hashCode() ?: 0)
  append(',')
  append(content.mimeType.orEmpty())
  append(',')
  append(content.fileName.orEmpty())
  append(',')
  append(content.base64?.length ?: 0)
  append(',')
  append(content.durationMs ?: "")
  append(',')
  append(content.toolActivity?.toolCallId.orEmpty())
  append(',')
  append(content.toolActivity?.detail?.hashCode() ?: 0)
  append(',')
  append(content.toolActivity?.result?.hashCode() ?: 0)
  append(',')
  append(content.toolActivity?.isError ?: false)
  append(',')
  append(content.toolActivity?.arguments?.hashCode() ?: 0)
}

internal fun ChatTimeline.containsUserMessageVersion(version: String): Boolean =
  items.any { item ->
    val message = (item as? ChatTimelineItem.Message)?.message ?: return@any false
    message.role.trim().equals("user", ignoreCase = true) && stableMessageVersion(message) == version
  }

internal fun ChatTimeline.withTurnRecap(recap: TurnRecap?): ChatTimeline {
  if (recap == null) return this
  // reverseLayout makes index 0 the newest visual edge. The recap replaces the terminal
  // thinking slot there, while shifting the saved user-message anchor to the same row.
  return copy(
    items = listOf(ChatTimelineItem.TurnRecapSummary(recap)) + items,
    readAnchorIndex = readAnchorIndex?.plus(1),
    latestContentIndex = 0,
    latestContentVersion = "$latestContentVersion:recap=${recap.runtimeMs}:${recap.outputTokens ?: ""}",
  )
}

// Reader restoration only needs to detect changes at the live edge. Avoid hashing
// the full transcript whenever a streamed response updates.
private fun latestContentVersion(
  messages: List<ChatMessage>,
  pendingRunCount: Int,
  pendingToolCalls: List<ChatPendingToolCall>,
  subagentActivities: Collection<ChatSubagentActivity>,
  moreWorkingCount: Int,
  stream: String?,
  thinking: String? = null,
  livePhaseCheckpoints: List<ChatLivePhaseCheckpoint> = emptyList(),
  turnWorking: Boolean = false,
  outboxItems: List<ChatOutboxItem> = emptyList(),
  questions: List<ChatQuestionPrompt> = emptyList(),
): String {
  val latest = messages.lastOrNull()
  return buildString {
    append(messages.size)
    append(':')
    append(latest?.id.orEmpty())
    append(':')
    append(latest?.role.orEmpty())
    append(':')
    append(latest?.timestampMs ?: "")
    latest?.content?.forEach { appendContentVersion(it) }
    append(":turnBoundary=")
    append(latest?.turnBoundary ?: false)
    append(":thinking=")
    append(thinking?.hashCode() ?: 0)
    append(":phases=")
    livePhaseCheckpoints.forEach { checkpoint ->
      append(checkpoint.toolCallId)
      append(',')
      append(checkpoint.textSnapshot.length)
      append(',')
      append(checkpoint.thinkingSnapshot.length)
      append(';')
    }
    append(":runs=")
    append(pendingRunCount)
    append(":turnWorking=")
    append(turnWorking)
    append(":tools=")
    pendingToolCalls.forEach { call ->
      append(call.toolCallId)
      append(',')
      append(call.name)
      append(',')
      append(call.isError)
      append(',')
      append(call.liveDiff)
      append(';')
    }
    append(":subagents=")
    subagentActivities.sortedBy { it.id }.forEach { activity ->
      append(activity.id)
      append(',')
      append(activity.status)
      append(',')
      append(activity.snippet?.hashCode() ?: 0)
      append(',')
      append(activity.terminalSummary?.hashCode() ?: 0)
      append(',')
      append(activity.error?.hashCode() ?: 0)
      append(',')
      append(activity.diffStat)
      append(';')
    }
    append("more=")
    append(moreWorkingCount)
    append(":stream=")
    append(stream?.hashCode() ?: 0)
    append(":outbox=")
    outboxItems.forEach { item ->
      append(item.id)
      append(',')
      append(item.status)
      append(';')
    }
    append(":questions=")
    questions.forEach { prompt ->
      append(prompt.record.id)
      append(',')
      append(prompt.status())
      append(',')
      append(prompt.submitting)
      append(',')
      append(prompt.skipping)
      append(',')
      append(prompt.errorText?.hashCode() ?: 0)
      append(',')
      append(prompt.record.answers.hashCode())
      append(';')
    }
  }
}

internal fun chatTimelineItemKey(item: ChatTimelineItem): String =
  when (item) {
    is ChatTimelineItem.Message -> "message:${item.message.id}"
    is ChatTimelineItem.OutboxCommand -> "outbox:${item.item.id}"
    is ChatTimelineItem.RecoveryOutboxCommand -> "outbox-recovery:${item.item.id}"
    is ChatTimelineItem.OutboxRecoveryHeader -> "outbox-recovery-header"
    is ChatTimelineItem.PendingTools -> item.key
    is ChatTimelineItem.CompletedTools -> "completed-tools:${item.key}"
    is ChatTimelineItem.SubagentActivity -> "subagent-activity"
    is ChatTimelineItem.QuestionPrompt -> "question:${item.prompt.record.id}"
    is ChatTimelineItem.TurnRecapSummary -> "turn-recap"
    is ChatTimelineItem.SystemNotice -> item.key
    is ChatTimelineItem.SystemDivider -> item.key
    is ChatTimelineItem.SkillLearnedNotice -> "skill-learned:${item.key}"
    is ChatTimelineItem.StreamingAssistant -> item.key
    is ChatTimelineItem.CollapsedSteps -> item.key
    is ChatTimelineItem.Thinking -> "thinking:${item.key ?: item.streaming}"
  }

private fun classifyTranscriptMessage(
  message: ChatMessage,
  index: Int,
): ChatTimelineItem? {
  message.transcriptMarker?.let { marker ->
    val keySuffix = marker.id ?: "${message.timestampMs ?: "missing"}:$index"
    return when (marker.kind) {
      "compaction" -> {
        val before = marker.tokensBefore
        val after = marker.tokensAfter
        val saved =
          if (before != null && before.isFinite() && after != null && after.isFinite() && before > after) {
            (before - after).toLong()
          } else {
            null
          }
        ChatTimelineItem.SystemDivider(
          key = "divider:compaction:$keySuffix",
          kind = SystemDividerKind.Compaction,
          label = nativeString("Compacted history"),
          metric = saved?.let { nativeString("saved \$count tokens", formatCompactTokenCount(it)) },
        )
      }

      "reset" -> {
        ChatTimelineItem.SystemDivider(
          key = "divider:reset:$keySuffix",
          kind = SystemDividerKind.Reset,
          label = nativeString("Session reset"),
          secondary = nativeString("The earlier conversation was cleared."),
        )
      }

      else -> {
        null
      }
    }
  }

  val provenance = message.provenance
  if (message.role == "user" && provenance?.kind == "internal_system") {
    val rawBody = chatMessagePlainText(message.content).removePrefix("[System] ")
    val label: String
    val body: String
    when (provenance.sourceTool) {
      "main_session_restart_recovery" -> {
        label = nativeString("System · restart recovery")
        body = nativeString("Turn interrupted by a gateway restart — asked the agent to resume and finish the response.")
      }

      "restart-sentinel" -> {
        label = nativeString("System · gateway restarted")
        body = rawBody
      }

      else -> {
        label = nativeString("System")
        body = rawBody
      }
    }
    if (body.isBlank()) return null
    val keySuffix = message.entryId ?: message.idempotencyKey ?: "${message.timestampMs ?: "missing"}:$index"
    return ChatTimelineItem.SystemNotice(
      key = "system-notice:$keySuffix",
      label = label,
      body = body,
    )
  }

  return message.takeIf { it.content.isNotEmpty() }?.let(ChatTimelineItem::Message)
}

// Results belong to their invocation even when commentary separates the two.
// Keep their display at the original call instead of manufacturing a second Tool row.
private fun projectTranscriptToolActivity(messages: List<ChatMessage>): List<List<ChatToolActivity>> {
  val projected = messages.map { mutableListOf<ChatToolActivity>() }
  val calls = mutableMapOf<String, Pair<Int, Int>>()
  var turnRunId: String? = null
  messages.forEachIndexed { messageIndex, message ->
    if (message.turnBoundary || message.isForwardedBoundary()) {
      calls.clear()
      turnRunId = null
    }
    // A later turn may reuse a harness-local call ID.
    if (message.transcriptMarker != null) {
      calls.clear()
      turnRunId = null
    } else if (message.role.equals("user", ignoreCase = true)) {
      val continuesRun = turnRunId != null && message.steerTargetRunId == turnRunId
      if (!continuesRun) {
        calls.clear()
        turnRunId = message.runId
      }
    }
    message.content.forEach { content ->
      val tool = content.toolActivity ?: return@forEach
      val result = content.type.equals("toolResult", ignoreCase = true)
      val owner = if (result) tool.toolCallId?.let(calls::get) else null
      if (owner != null) {
        val original = projected[owner.first][owner.second]
        projected[owner.first][owner.second] = mergeToolActivity(original, tool)
      } else {
        // ID-only result envelopes have no standalone UI. Keep meaningful unnamed
        // output and failures, and keep empty named calls (they may still be running).
        val emptyOrphan =
          result && tool.name == "tool" && tool.detail.isNullOrBlank() &&
            tool.result.isNullOrBlank() && !tool.isError && tool.arguments.isNullOrEmpty()
        if (!emptyOrphan) {
          if (!result) tool.toolCallId?.let { calls[it] = messageIndex to projected[messageIndex].size }
          projected[messageIndex].add(tool)
        }
      }
    }
  }
  return projected
}

private fun mergeToolActivity(
  previous: ChatToolActivity,
  next: ChatToolActivity,
): ChatToolActivity =
  previous.copy(
    name = previous.name.takeUnless { it == "tool" } ?: next.name,
    detail = previous.detail ?: next.detail,
    result = next.result ?: previous.result,
    isError = previous.isError || next.isError,
    arguments = previous.arguments ?: next.arguments,
    title = previous.title ?: next.title,
  )

private fun coalesceToolActivity(parts: List<ChatToolActivity>): List<ChatToolActivity> {
  val merged = linkedMapOf<String, ChatToolActivity>()
  parts.forEachIndexed { index, part ->
    val key = part.toolCallId ?: "${part.name}:$index"
    val previous = merged[key]
    merged[key] =
      if (previous == null) {
        part
      } else {
        mergeToolActivity(previous, part)
      }
  }
  return merged.values.toList()
}

internal data class VisibleSubagentActivities(
  val activities: List<ChatSubagentActivity>,
  val moreWorkingCount: Int,
)

internal fun visibleSubagentActivities(activities: Collection<ChatSubagentActivity>): VisibleSubagentActivities {
  val working = activities.filter(ChatSubagentActivity::isWorking).sortedWith(compareBy<ChatSubagentActivity> { it.startedAtMs }.thenBy { it.id })
  val finished =
    activities
      .filterNot(ChatSubagentActivity::isWorking)
      .sortedWith(compareByDescending<ChatSubagentActivity> { it.endedAtMs ?: Long.MIN_VALUE }.thenBy { it.id })
  val visible = (working + finished).take(5)
  return VisibleSubagentActivities(
    activities = visible,
    moreWorkingCount =
      working.count { it.status == "running" && it !in visible },
  )
}
