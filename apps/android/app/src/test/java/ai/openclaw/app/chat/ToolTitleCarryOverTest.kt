package ai.openclaw.app.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class ToolTitleCarryOverTest {
  private fun toolMessage(
    toolCallId: String,
    title: String? = null,
  ): ChatMessage =
    ChatMessage(
      id = "msg-$toolCallId",
      role = "assistant",
      content =
        listOf(
          ChatMessageContent(
            type = "toolCall",
            toolActivity =
              ChatToolActivity(
                toolCallId = toolCallId,
                name = "exec",
                detail = "echo hi",
                result = null,
                isError = false,
                title = title,
              ),
          ),
        ),
      timestampMs = 1_000L,
    )

  @Test
  fun `backfills missing title from the live item stream`() {
    val carried = carryToolTitles(listOf(toolMessage("call-1")), mapOf("call-1" to "exec Run smoke test"))

    assertEquals("exec Run smoke test", carried.single().content.single().toolActivity?.title)
  }

  @Test
  fun `keeps a title supplied by history`() {
    val message = toolMessage("call-1", title = "exec From history")
    val carried = carryToolTitles(listOf(message), mapOf("call-1" to "exec Run smoke test"))

    assertEquals("exec From history", carried.single().content.single().toolActivity?.title)
  }

  @Test
  fun `blank titles are treated as missing`() {
    val carried = carryToolTitles(listOf(toolMessage("call-1", title = "  ")), mapOf("call-1" to "exec Run smoke test"))

    assertEquals("exec Run smoke test", carried.single().content.single().toolActivity?.title)
  }

  @Test
  fun `messages without a matching tool call keep their identity`() {
    val message = toolMessage("call-1")
    val messages = listOf(message)

    assertSame(messages, carryToolTitles(messages, mapOf("call-2" to "exec Other")))
    assertSame(messages, carryToolTitles(messages, emptyMap()))
    assertSame(messages, carryToolTitles(messages, null))
  }

  @Test
  fun `tool rows without an id keep their identity`() {
    val message =
      ChatMessage(
        id = "msg-orphan",
        role = "assistant",
        content =
          listOf(
            ChatMessageContent(
              type = "toolCall",
              toolActivity = ChatToolActivity(toolCallId = null, name = "exec", detail = "echo hi", result = null, isError = false),
            ),
          ),
        timestampMs = 1_000L,
      )
    val messages = listOf(message)

    val carried = carryToolTitles(messages, mapOf("call-1" to "exec Run smoke test"))

    assertSame(messages, carried)
    assertNull(carried.single().content.single().toolActivity?.title)
  }

  @Test
  fun `collects titles from cached rows so a history rebuild keeps them`() {
    val cached =
      listOf(
        toolMessage("call-1", title = "exec Run smoke test"),
        toolMessage("call-2", title = "  "),
        toolMessage("call-3"),
      )

    val titles = collectToolTitles(cached)

    assertEquals(mapOf("call-1" to "exec Run smoke test"), titles)
    assertEquals(
      "exec Run smoke test",
      carryToolTitles(listOf(toolMessage("call-1")), titles).single().content.single().toolActivity?.title,
    )
  }
}
