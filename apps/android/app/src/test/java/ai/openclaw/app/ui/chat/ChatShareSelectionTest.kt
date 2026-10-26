package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.ui.design.ClawDesignTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChatShareSelectionTest {
  @get:Rule val composeRule = createComposeRule()

  private fun message(
    id: String,
    role: String,
    text: String,
  ): ChatTimelineItem.Message =
    ChatTimelineItem.Message(
      ChatMessage(
        id = id,
        entryId = id,
        role = role,
        content = listOf(ChatMessageContent(type = "text", text = text)),
        timestampMs = 1L,
      ),
    )

  private fun transcript(): List<ChatTimelineItem> =
    listOf(
      message("assistant-2", "assistant", "answer two"),
      message("user-2", "user", "question two"),
      ChatTimelineItem.Thinking(text = "reasoning"),
      message("assistant-1", "assistant", "answer one"),
      message("user-1", "user", "question one"),
      ChatTimelineItem.StreamingAssistant("live answer"),
      ChatTimelineItem.SystemNotice(key = "notice", label = "Notice", body = "body"),
    )

  @Test
  fun agentShareSelectionStartsWithItsUserQuestion() {
    assertEquals(
      setOf("assistant-2", "user-2"),
      chatShareSelectionSeed(transcript(), "assistant-2"),
    )
    assertEquals(
      setOf("assistant-1", "user-1"),
      chatShareSelectionSeed(transcript(), "assistant-1"),
    )
  }

  @Test
  fun userShareSelectionStartsWithOnlyThatBubble() {
    assertEquals(setOf("user-2"), chatShareSelectionSeed(transcript(), "user-2"))
  }

  @Test
  fun agentWithoutAQuestionStillSelectsItself() {
    val items = listOf(message("assistant-only", "assistant", "unprompted"))
    assertEquals(setOf("assistant-only"), chatShareSelectionSeed(items, "assistant-only"))
  }

  @Test
  fun shareableMessagesSkipNonBubbleRowsAndKeepConversationOrder() {
    val messages = chatShareableMessages(transcript())
    assertEquals(
      listOf("user-1", "assistant-1", "user-2", "assistant-2"),
      messages.map { it.entryId },
    )
    assertTrue(messages.all { it.role == "user" || it.role == "assistant" })
  }

  @Test
  fun shareableMessagesSkipRowsWithoutAServerTranscriptId() {
    // ChatMessage.id is a client-local random UUID the gateway has never heard
    // of; only rows carrying the server-side __openclaw.id (entryId) can be
    // addressed by the share create endpoint.
    val localOnly =
      ChatTimelineItem.Message(
        ChatMessage(
          id = "local-only",
          role = "assistant",
          content = listOf(ChatMessageContent(type = "text", text = "still streaming")),
          timestampMs = 1L,
        ),
      )
    assertEquals(null, chatShareableMessage(localOnly))
    assertEquals(emptySet<String>(), chatShareSelectionSeed(listOf(localOnly), "local-only"))
    assertTrue(chatShareableMessages(transcript() + localOnly).none { it.id == "local-only" })
  }

  @Test
  fun selectionRowShowsCheckboxOnlyWhileThePickerIsOpen() {
    var active by mutableStateOf(false)
    var selected by mutableStateOf(false)
    var toggleCount = 0
    composeRule.setContent {
      ClawDesignTheme {
        ChatShareSelectionRow(
          selectable = true,
          shiftContent = true,
          selectionActive = active,
          selected = selected,
          onToggle = {
            toggleCount += 1
            selected = !selected
          },
          testTag = "share-select",
        ) {
          Box(Modifier.size(80.dp).testTag("share-select-content"))
        }
      }
    }

    composeRule.onNodeWithTag("share-select").assertDoesNotExist()
    composeRule.runOnIdle { active = true }
    composeRule.waitForIdle()
    composeRule.onNodeWithTag("share-select").assertExists()
    composeRule.onNodeWithTag("share-select").performClick()
    composeRule.runOnIdle {
      assertEquals(1, toggleCount)
      assertTrue(selected)
    }
  }

  @Test
  fun selectionRowTogglesWhenTheMessageBodyIsTapped() {
    var active by mutableStateOf(true)
    var toggleCount = 0
    composeRule.setContent {
      ClawDesignTheme {
        ChatShareSelectionRow(
          selectable = true,
          shiftContent = true,
          selectionActive = active,
          selected = false,
          onToggle = { toggleCount += 1 },
          testTag = "share-select",
        ) {
          Box(Modifier.size(80.dp).testTag("share-select-content"))
        }
      }
    }

    // The checkbox is a sibling overlaid at the rail; tapping the message body uses
    // the row's own clickable and must not also fire the checkbox. The content
    // lambda's node is merged under the row's clickable, so read it unmerged.
    composeRule.onNodeWithTag("share-select-content", useUnmergedTree = true).performClick()
    composeRule.waitForIdle()
    assertEquals(1, toggleCount)
  }

  @Test
  fun selectionRowIsNotTappableWhileThePickerIsClosed() {
    var active by mutableStateOf(false)
    var toggleCount = 0
    composeRule.setContent {
      ClawDesignTheme {
        ChatShareSelectionRow(
          selectable = true,
          shiftContent = true,
          selectionActive = active,
          selected = false,
          onToggle = { toggleCount += 1 },
          testTag = "share-select",
        ) {
          Box(Modifier.size(80.dp).testTag("share-select-content"))
        }
      }
    }

    // With the picker closed the row exposes no click action natively; the callback
    // never firing proves the non-interactive path. Read unmerged: the content Box
    // sits under the row's clickable merge boundary while the picker is open.
    composeRule.onNodeWithTag("share-select-content", useUnmergedTree = true).assertExists()
    composeRule.runOnIdle { assertEquals(0, toggleCount) }
  }

  @Test
  fun sharePanelBlocksLinkActionsWhileOneIsCreating() {
    composeRule.setContent {
      ClawDesignTheme {
        ChatSharePanel(
          visible = true,
          linkLoading = true,
          onCopyLink = {},
          onShareWeChat = {},
          onCopyText = {},
        )
      }
    }

    // Both link actions hit the same create endpoint, so either being busy
    // disables both; copying text never touches the gateway and stays live.
    composeRule.onNodeWithTag("chat-share-action-copy-link").assertExists().assertIsNotEnabled()
    composeRule.onNodeWithTag("chat-share-action-wechat").assertExists().assertIsNotEnabled()
    composeRule.onNodeWithTag("chat-share-action-copy-text").assertExists().assertIsEnabled()
  }

  @Test
  fun sharePanelShowsTheThreeActionsInFixedOrder() {
    var copyLink = 0
    var shareWeChat = 0
    var copyText = 0
    composeRule.setContent {
      ClawDesignTheme {
        ChatSharePanel(
          visible = true,
          onCopyLink = { copyLink += 1 },
          onShareWeChat = { shareWeChat += 1 },
          onCopyText = { copyText += 1 },
        )
      }
    }

    composeRule.onNodeWithTag("chat-share-panel").assertExists()
    composeRule.onNodeWithText("复制链接").assertExists().performClick()
    composeRule.onNodeWithText("微信好友").assertExists().performClick()
    composeRule.onNodeWithText("复制文本").assertExists().performClick()
    composeRule.runOnIdle {
      assertEquals(1, copyLink)
      assertEquals(1, shareWeChat)
      assertEquals(1, copyText)
    }
  }

  @Test
  fun selectionBarRequiresASelectionBeforeConfirming() {
    var cancelled = 0
    var selectAll = 0
    var confirmed = 0
    composeRule.setContent {
      ClawDesignTheme {
        ChatShareSelectionBar(
          visible = true,
          selectedCount = 0,
          allSelected = false,
          onCancel = { cancelled += 1 },
          onSelectAll = { selectAll += 1 },
          onConfirm = { confirmed += 1 },
        )
      }
    }

    composeRule.onNodeWithText("取消").assertExists().assertIsEnabled()
    composeRule.onNodeWithText("全选").assertExists().assertIsEnabled()
    composeRule.onNodeWithText("确认").assertExists().assertIsNotEnabled()

    composeRule.onNodeWithText("取消").performClick()
    composeRule.onNodeWithText("全选").performClick()
    composeRule.runOnIdle {
      assertEquals(1, cancelled)
      assertEquals(1, selectAll)
      assertEquals(0, confirmed)
    }
  }
}
