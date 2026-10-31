package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassSurface
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.clawPressDrag
import ai.openclaw.app.ui.design.rememberClawPressDrag
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState

/** Structured quote attached to the composer; the wire format stays a Markdown blockquote. */
internal data class ChatQuoteDraft(
  val text: String,
  /** Local transcript id of the quoted message, when the quote came from a bubble. */
  val sourceMessageId: String? = null,
) {
  val body: String get() = text.trim()
}

internal data class ChatQuoteSplit(
  val quote: String,
  val body: String,
)

internal data class ChatQuotedContent(
  val quote: String,
  val content: List<ChatMessageContent>,
)

/**
 * Splits the leading `> ...` block that [quoteChatMessage] writes. Only the composer's own
 * quote format is recognized: every line starts with `>`, and the body starts after the
 * blank separator. Returns null when the message does not open with a blockquote.
 */
internal fun splitLeadingChatQuote(text: String): ChatQuoteSplit? {
  if (!text.startsWith(">")) return null
  val lines = text.lines()
  val quoteLines = ArrayList<String>(lines.size)
  var index = 0
  while (index < lines.size && lines[index].startsWith(">")) {
    val content = lines[index].removePrefix(">")
    quoteLines += if (content.startsWith(" ")) content.substring(1) else content
    index++
  }
  val quote = quoteLines.joinToString("\n").trim()
  if (quote.isEmpty()) return null
  val body = lines.drop(index).joinToString("\n").trimStart('\n')
  return ChatQuoteSplit(quote = quote, body = body)
}

/** Splits a user message's first text part into its hidden quote and the visible body. */
internal fun splitQuotedChatContent(content: List<ChatMessageContent>): ChatQuotedContent? {
  val textIndex = content.indexOfFirst { it.type == "text" && !it.text.isNullOrEmpty() }
  if (textIndex < 0) return null
  val split = splitLeadingChatQuote(content[textIndex].text.orEmpty()) ?: return null
  val next = content.toMutableList()
  if (split.body.isBlank()) {
    next.removeAt(textIndex)
  } else {
    next[textIndex] = content[textIndex].copy(text = split.body)
  }
  return ChatQuotedContent(quote = split.quote, content = next)
}

/** Same text the composer sends: blockquote first, then the typed body. */
internal fun composeChatOutgoingMessage(
  quote: ChatQuoteDraft?,
  text: String,
): String {
  val body = text.trim()
  val quoted = quote?.body?.takeIf(String::isNotEmpty) ?: return body
  val block = quoteChatMessage(quoted)
  return if (body.isEmpty()) block.trimEnd() else block + body
}

internal fun normalizeChatQuoteText(text: String): String =
  text
    .replace(quoteWhitespace, " ")
    .trim()

private val quoteWhitespace = Regex("\\s+")

/**
 * Finds the nearest earlier message whose rendered text contains [quote]. The list is the
 * reversed transcript timeline, so older rows live at larger indices.
 */
internal fun findQuotedChatMessageIndex(
  items: List<ChatTimelineItem>,
  fromIndex: Int,
  quote: String,
): Int? {
  val needle = normalizeChatQuoteText(quote)
  if (needle.isEmpty()) return null
  for (index in fromIndex + 1 until items.size) {
    val message = (items[index] as? ChatTimelineItem.Message)?.message ?: continue
    if (chatMessageMatchesQuote(message, needle)) return index
  }
  return null
}

private fun chatMessageMatchesQuote(
  message: ChatMessage,
  normalizedQuote: String,
): Boolean {
  val raw = chatMessagePlainText(message.content)
  if (raw.isBlank()) return false
  val user = message.role.trim().equals("user", ignoreCase = true)
  val body = if (user) splitLeadingChatQuote(raw)?.body ?: raw else raw
  val rendered = chatMarkdownDisplayText(body).ifBlank { body }
  return normalizeChatQuoteText(rendered).contains(normalizedQuote) ||
    normalizeChatQuoteText(body).contains(normalizedQuote)
}

private val quoteLinkBlueLight = Color(0xFF2563EB)
private val quoteLinkBlueDark = Color(0xFF6EA8FF)

internal fun chatQuoteLinkColor(canvas: Color): Color =
  if (canvas.luminance() < 0.5f) quoteLinkBlueDark else quoteLinkBlueLight

/** The single blue affordance a sent quote leaves in the transcript. */
@Composable
internal fun ChatQuotedMessageLink(
  onClick: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val interaction = remember { MutableInteractionSource() }
  Text(
    text = nativeString("Quoted earlier content"),
    style = ClawTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    color = chatQuoteLinkColor(colors.canvas),
    modifier =
      modifier
        .clickable(
          enabled = onClick != null,
          interactionSource = interaction,
          indication = null,
          onClick = { onClick?.invoke() },
        )
        .padding(vertical = 1.dp),
  )
}

/** Capsule above the composer: `引用 <content>` with a two-line ceiling and a remove action. */
@Composable
internal fun ChatQuoteCapsule(
  quote: ChatQuoteDraft,
  onRemove: () -> Unit,
  hazeState: HazeState?,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  // One sheet of material: the whole capsule swells and stretches under the finger, the same
  // press feel the other glass bars carry.
  val pressDrag = rememberClawPressDrag(glow = false)
  ClawGlassSurface(
    modifier =
      modifier
        .fillMaxWidth()
        .clawPressDrag(pressDrag)
        .then(pressDrag.modifier.then(pressDrag.gestureModifier)),
    cornerRadius = 18.dp,
    contentPadding = PaddingValues(0.dp),
    hazeState = hazeState,
    fillAlpha = 0.72f,
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(start = 13.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = buildAnnotatedString {
            withStyle(SpanStyle(color = colors.textMuted, fontWeight = FontWeight.Medium)) {
              append(nativeString("Quote"))
            }
            append("  ")
            withStyle(SpanStyle(color = colors.text)) {
              append(quote.body)
            }
          },
          style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 17.sp),
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(
        onClick = onRemove,
        modifier = Modifier.size(28.dp),
      ) {
        Icon(
          imageVector = Icons.Default.Close,
          contentDescription = nativeString("Remove quote"),
          tint = colors.textMuted,
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}
