package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.LocalClawMorphMenuHost
import ai.openclaw.app.ui.design.clawMorphTrigger
import ai.openclaw.app.ui.design.rememberClawHaptics
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState

internal fun chatMessagePlainText(content: List<ChatMessageContent>): String =
  content
    .asSequence()
    .filter { it.type == "text" }
    .mapNotNull(ChatMessageContent::text)
    .filter(String::isNotBlank)
    .joinToString("\n\n")

internal fun quoteChatMessage(text: String): String {
  val quoted =
    text
      .lineSequence()
      .joinToString("\n") { line -> if (line.isEmpty()) ">" else "> $line" }
  return nativeString("\$quoted\n\n", quoted)
}

/** Long-press message actions shared by the full Chat tab and compact chat sheet. */
@Composable
internal fun ChatMessageActionHost(
  text: String,
  onQuote: () -> Unit,
  onSelectText: ((press: ChatTextPress) -> Unit)? = null,
  modifier: Modifier = Modifier,
  showSessionActions: Boolean = false,
  onRewind: (() -> Unit)? = null,
  onFork: (() -> Unit)? = null,
  /** Batch-share entry. Null callers (previews, non-transcript bubbles) keep the direct chooser. */
  onShare: (() -> Unit)? = null,
  enabled: Boolean = true,
  listenActive: Boolean = false,
  onToggleListen: (() -> Unit)? = null,
  /**
   * Backdrop the long-press card blurs. Falls back to the nearest published
   * source (the transcript), so bubbles do not have to thread it through.
   */
  hazeState: HazeState? = null,
  content: @Composable () -> Unit,
) {
  if (!enabled || (text.isBlank() && !showSessionActions)) {
    Box(modifier = modifier) { content() }
    return
  }

  val context = LocalContext.current
  val haptics = rememberClawHaptics()

  val bubbleTrigger = clawMorphTrigger(LocalClawMorphMenuHost.current)
  var lastPress by remember { mutableStateOf<ChatTextPress?>(null) }
  var hostCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
  val screenDensity = LocalDensity.current
  val screen = LocalConfiguration.current
  val screenWidthPx = with(screenDensity) { screen.screenWidthDp.dp.toPx() }
  val screenHeightPx = with(screenDensity) { screen.screenHeightDp.dp.toPx() }

  val menuContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
    if (text.isNotBlank()) {
      onToggleListen?.let { toggleListen ->
        ClawGlassMenuItem(
          label = if (listenActive) nativeString("Stop") else nativeString("Listen"),
          icon = if (listenActive) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
          onClick = toggleListen,
        )
      }
      ClawGlassMenuItem(
        label = nativeString("Copy"),
        icon = Icons.Default.ContentCopy,
        onClick = { copyChatText(context, text) },
      )
      ClawGlassMenuItem(
        label = nativeString("Select text"),
        icon = Icons.Default.TextFields,
        onClick = {
          lastPress?.let { press -> onSelectText?.invoke(press) }
        },
      )
      ClawGlassMenuItem(
        label = nativeString("Share"),
        icon = Icons.Default.Share,
        onClick = onShare ?: { shareChatText(context, text) },
      )
      ClawGlassMenuItem(
        label = nativeString("Follow up"),
        icon = Icons.AutoMirrored.Filled.Reply,
        onClick = onQuote,
      )
    }
    if (showSessionActions) {
      onRewind?.let { rewind ->
        ClawGlassMenuItem(
          label = nativeString("Rewind to here"),
          icon = Icons.Default.History,
          onClick = rewind,
        )
      }
      onFork?.let { fork ->
        ClawGlassMenuItem(
          label = nativeString("Fork from here"),
          icon = Icons.AutoMirrored.Filled.CallSplit,
          onClick = fork,
        )
      }
    }
  }

  Box(
    modifier =
      modifier
        .then(bubbleTrigger.anchorModifier)
        .onGloballyPositioned { hostCoordinates = it }
        .pointerInput(Unit) {
          detectTapGestures(
            onLongPress = { offset ->
              haptics.longPress()
              // Resolve to window coordinates while this bubble is still attached: opening
              // selection mode recomposes the host away, detaching its LayoutCoordinates.
              lastPress =
                hostCoordinates?.takeIf { it.isAttached }?.let { host ->
                  val topLeft = host.localToWindow(Offset.Zero)
                  ChatTextPress(
                    windowOffset = host.localToWindow(offset),
                    hostBounds =
                      Rect(
                        topLeft,
                        Size(host.size.width.toFloat(), host.size.height.toFloat()),
                      ),
                  )
                }
              // The glass grows out of the spot under the finger, so a long press
              // on a tall reply still puts the card where it was pressed. There is
              // no popup left to reposition it, so clamp it into the viewport
              // here: right-aligned user bubbles would otherwise push the card off
              // the right edge, and a press near the bottom off the top.
              val finger = bubbleTrigger.anchor?.topLeft?.plus(offset) ?: return@detectTapGestures
              val edge = with(screenDensity) { 12.dp.toPx() }
              val panelW = with(screenDensity) { 200.dp.toPx() }
              val lastHeight = bubbleTrigger.state.panelHeightPx.toFloat()
              val estH =
                if (lastHeight > 0f) lastHeight else with(screenDensity) { 220.dp.toPx() }
              // Pin the card's top-left just off the finger, then keep the whole
              // card inside the viewport: a right-aligned user bubble presses near
              // the right edge, where the card would otherwise run off screen.
              val left = (finger.x - edge).coerceIn(edge, (screenWidthPx - edge - panelW).coerceAtLeast(edge))
              val top = (finger.y - edge).coerceIn(edge, (screenHeightPx - edge - estH).coerceAtLeast(edge))
              val blob = with(screenDensity) { 44.dp.toPx() }
              bubbleTrigger.open(
                origin = TransformOrigin(0f, 0f),
                panelWidth = 200.dp,
                overrideAnchor = Rect(left, top, left + blob, top + blob),
                content = menuContent,
              )
            },
          )
        },
  ) {
    content()
  }
}

internal fun copyChatText(
  context: Context,
  text: String,
  toastLabel: String = nativeString("Text copied"),
) {
  val clipboard = context.getSystemService(ClipboardManager::class.java)
  clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw text", text))
  Toast.makeText(context, toastLabel, Toast.LENGTH_SHORT).show()
}

internal fun shareChatText(
  context: Context,
  text: String,
) {
  val sendIntent =
    Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, text)
  val chooser = Intent.createChooser(sendIntent, nativeString("Share message"))
  if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(chooser) }
    .onFailure {
      Toast.makeText(context, nativeString("No app can share this message"), Toast.LENGTH_SHORT).show()
    }
}

/**
 * Hands a share link to WeChat via the system share sheet, targeting the WeChat
 * package. When the package cannot be resolved (not installed, or hidden by
 * package visibility), fall back to the generic chooser instead of failing.
 *
 * The text is the bare URL and nothing else: WeChat only turns the message into
 * a link card when the whole payload is a URL. A title line prefix makes it a
 * plain text message, which is why sharing with a title never produced a card.
 */
internal fun shareChatLinkToWeChat(
  context: Context,
  url: String,
) {
  fun shareIntent(): Intent =
    Intent(Intent.ACTION_SEND)
      .setType("text/plain")
      .putExtra(Intent.EXTRA_TEXT, url)

  val weChatIntent = shareIntent().setPackage("com.tencent.mm")
  val resolved =
    runCatching { weChatIntent.resolveActivity(context.packageManager) }.getOrNull() != null
  val intent =
    if (resolved) {
      weChatIntent
    } else {
      Intent.createChooser(shareIntent(), nativeString("Share message"))
    }
  if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  runCatching { context.startActivity(intent) }
    .onFailure {
      Toast.makeText(context, nativeString("No app can share this message"), Toast.LENGTH_SHORT).show()
    }
}
