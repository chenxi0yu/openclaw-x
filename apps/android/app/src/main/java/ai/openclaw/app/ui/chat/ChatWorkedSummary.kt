package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessage

// Match web assistantGroupIsForwardedBoundary: attribution labels do not establish turn ownership.

/**
 * Annotates the terminal assistant message of each completed agent turn with
 * the turn's elapsed time, shown next to the message timestamp.
 *
 * Tool calls and thinking blocks always stay visible in the chain; nothing is
 * folded away anymore (the old WorkedSummary row duplicated the chain).
 */
internal fun ChatTimeline.withWorkDurationLabels(
  messages: List<ChatMessage>,
  runWorking: Boolean,
  sessionKey: String,
): ChatTimeline {
  val sessionParts = sessionKey.trim().lowercase().split(':')
  if (sessionParts.size != 4 || sessionParts[0] != "agent" || sessionParts[1].isBlank() ||
    sessionParts[2] != "dashboard" || sessionParts[3].isBlank()
  ) {
    return this
  }
  val chronological = items.asReversed()
  val turns = mutableListOf<MutableList<ChatTimelineItem>>()
  chronological.forEach { item ->
    val startsTurn =
      when (item) {
        is ChatTimelineItem.Message -> {
          item.turnBoundary ||
            item.message.role
              .trim()
              .equals("user", ignoreCase = true) || item.message.isForwardedBoundary()
        }

        is ChatTimelineItem.CompletedTools -> {
          item.turnBoundary
        }

        else -> {
          false
        }
      }
    if (turns.isEmpty() || startsTurn) turns.add(mutableListOf())
    turns.last().add(item)
  }
  val timestamps = messages.associate { (it.entryId ?: it.idempotencyKey ?: it.id) to it.timestampMs }

  fun timestamp(item: ChatTimelineItem): Long? =
    when (item) {
      is ChatTimelineItem.Message -> item.message.timestampMs
      is ChatTimelineItem.CompletedTools -> timestamps[item.key]
      // A folded run stands in for its first tool row.
      is ChatTimelineItem.CollapsedSteps ->
        item.steps.filterIsInstance<ChatTimelineItem.CompletedTools>().firstOrNull()?.let { timestamps[it.key] }

      else -> null
    }

  fun isWork(item: ChatTimelineItem): Boolean =
    when (item) {
      is ChatTimelineItem.CompletedTools -> {
        true
      }

      // Folded steps are finished work: the turn still starts at them, so the
      // elapsed-time label keeps measuring the whole run.
      is ChatTimelineItem.CollapsedSteps -> item.steps.any { it is ChatTimelineItem.CompletedTools }

      is ChatTimelineItem.Message -> {
        item.message.role
          .trim()
          .equals("assistant", ignoreCase = true) &&
          !item.message.isForwardedBoundary()
      }

      else -> {
        false
      }
    }
  // Steering messages continue an existing run; they are not completed-turn boundaries.
  val runTurns = mutableMapOf<String, Int>()
  val steeringTurns = linkedMapOf<String, MutableList<Int>>()
  turns.forEachIndexed { index, turn ->
    val user =
      (turn.firstOrNull() as? ChatTimelineItem.Message)?.message?.takeIf {
        it.role.equals("user", ignoreCase = true)
      }
    user?.runId?.let { runTurns.putIfAbsent(it, index) }
    user?.steerTargetRunId?.let { steeringTurns.getOrPut(it) { mutableListOf() }.add(index) }
  }
  val continuations = mutableMapOf<Int, Int>()
  val preceding = mutableMapOf<Int, Int>()
  steeringTurns.forEach { (runId, indexes) ->
    var previous = runTurns[runId] ?: return@forEach
    indexes.forEach { index ->
      if (index > previous) {
        continuations[previous] = index
        preceding[index] = previous
        previous = index
      }
    }
  }
  val finalIndexes =
    turns.mapIndexed { index, turn ->
      if (index in continuations) {
        -1
      } else {
        turn.indexOfLast {
          it is ChatTimelineItem.Message && it.message.role.equals("assistant", ignoreCase = true) && !it.message.isForwardedBoundary()
        }
      }
    }
  val terminalReplies =
    turns
      .mapIndexed { index, turn ->
        turn.getOrNull(finalIndexes[index]) as? ChatTimelineItem.Message
      }.toMutableList()
  for (index in turns.lastIndex - 1 downTo 0) {
    if (terminalReplies[index] == null) continuations[index]?.let { terminalReplies[index] = terminalReplies[it] }
  }
  val liveTurns = mutableSetOf<Int>()
  if (runWorking) {
    var index = turns.lastIndex
    while (index >= 0 && liveTurns.add(index)) index = preceding[index] ?: break
  }
  val rendered =
    buildList {
      turns.forEachIndexed { turnIndex, turn ->
        val live =
          turnIndex in liveTurns ||
            turn.any {
              it is ChatTimelineItem.StreamingAssistant || it is ChatTimelineItem.PendingTools || it is ChatTimelineItem.Thinking
            }
        val finalIndex = finalIndexes[turnIndex]
        val terminal = terminalReplies[turnIndex]
        val end = if (finalIndex >= 0) finalIndex else turn.size
        var start = end
        while (start > 0 && isWork(turn[start - 1])) start--
        if (live || terminal == null || start == end) {
          addAll(turn)
        } else {
          val boundary = turn.firstOrNull() as? ChatTimelineItem.Message
          val startTime =
            boundary
              ?.takeIf {
                it.message.role
                  .trim()
                  .equals("user", ignoreCase = true)
              }?.message
              ?.timestampMs ?: timestamp(turn[start])
          val endTime = terminal.message.timestampMs
          val duration = if (startTime != null && endTime != null && endTime > startTime) endTime - startTime else null
          turn.forEachIndexed { index, item ->
            if (index == finalIndex && item is ChatTimelineItem.Message && duration != null) {
              add(item.copy(workDurationMs = duration))
            } else {
              add(item)
            }
          }
        }
      }
    }.asReversed()
  return copy(
    items = rendered,
    readAnchorIndex = rendered.indexOfFirst { it is ChatTimelineItem.Message && it.message.id == latestUserMessageId }.takeIf { it >= 0 } ?: latestContentIndex,
  )
}

/** Chinese compact duration like "已运行 1分17秒"; null when too short to mention. */
internal fun workedDurationLabel(durationMs: Long?): String? {
  if (durationMs == null || durationMs <= 0) return null
  var remaining = if (durationMs < 1000) durationMs else ((durationMs + 500) / 1000) * 1000
  val parts = mutableListOf<String>()
  for ((scale, suffix) in listOf(86_400_000L to "天", 3_600_000L to "小时", 60_000L to "分", 1000L to "秒", 1L to "毫秒")) {
    val value = remaining / scale
    remaining %= scale
    if (value > 0) parts.add("$value$suffix")
    if (parts.size == 2) break
  }
  if (parts.isEmpty()) return null
  return "已运行 " + parts.joinToString("")
}
