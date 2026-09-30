package ai.openclaw.app.chat

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Regression: a run acknowledged before a gateway disconnect finishes on the
 * gateway while the client is offline. The live terminal event is never
 * received, so reconnect history (persisted user turn + assistant reply, no
 * inFlightRun) is the only terminal evidence. The stale pending run must retire
 * on that snapshot instead of leaving the chat stuck on "Working" with a
 * no-op stop button.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatControllerDisconnectedRunReconciliationTest {
  private val json = chatControllerTestJson

  private fun TestScope.loadController(gateway: ScriptedGateway): ChatController =
    backgroundScope.createChatController(requestGateway = gateway::request).also {
      it.load("main")
      runCurrent()
    }

  @Test
  fun reconnectHistoryWithPersistedReplyRetiresRunMissedTerminal() =
    runTest {
      val gateway = ScriptedGateway(json)
      gateway.respondWith("chat.history", historyResponse("session-1", emptyList()))
      gateway.respondChatSend(status = "started")
      val controller = loadController(gateway)
      assertTrue(controller.sendMessageAwaitAcceptance("keep working", "off", emptyList()))
      val runId = requireNotNull(gateway.lastRunId)
      runCurrent()
      assertEquals(1, controller.pendingRunCount.value)

      controller.onDisconnected("Reconnecting…")
      // The gateway finished the run while offline; reconnect history carries
      // the persisted reply and no in-flight snapshot.
      gateway.respondWith(
        "chat.history",
        historyResponse(
          "session-1",
          listOf(
            ReplayHistoryMessage("user", "keep working", 1_000, idempotencyKey = "$runId:user"),
            ReplayHistoryMessage("assistant", "finished while offline", 2_000),
          ),
          hasActiveRun = false,
          activeRunIds = emptyList(),
        ),
      )
      controller.onGatewayConnected()
      runCurrent()

      assertEquals(0, controller.pendingRunCount.value)
      assertNull(controller.selectedActiveRunPresentation.value.runId)
      assertNull(controller.streamingAssistantText.value)
      assertEquals(2, controller.messages.value.size)
    }
}
