package ai.openclaw.app.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Characters revealed per display frame. A fixed cadence keeps a buffered answer
 * readable instead of turning one gateway snapshot into a single visual jump.
 */
private const val TYPEWRITER_CHARS_PER_FRAME = 2

/**
 * Typewriter reveal for the newest assistant answer.
 *
 * openclaw finishes reasoning and then emits the whole answer in one chunk, so the
 * live "streaming" row can be swapped for the persisted message within a frame. The
 * reveal is therefore tracked by answer *text* rather than by timeline row: a body
 * that grew (or an identical body handed over from the live row) keeps its progress,
 * which makes the handoff seamless instead of restarting mid-answer.
 */
@Stable
internal class ChatAnswerTypewriter {
  private var owner: String = ""
  private var revealed by mutableIntStateOf(0)

  /** The slice of [text] that belongs on screen right now. */
  fun visible(text: String, animate: Boolean): String {
    if (text.isEmpty()) return text
    if (isRevealed(text)) return text
    if (!continues(text)) return if (animate) "" else text
    return text.take(revealed.coerceAtMost(text.length))
  }

  /** Advances the reveal of [text]. Call from a [LaunchedEffect], never during composition. */
  suspend fun run(text: String, animate: Boolean) {
    if (text.isEmpty() || isRevealed(text)) return
    if (!continues(text)) {
      owner = text
      revealed = if (animate) 0 else text.length
      if (!animate) return
    } else {
      owner = text
    }
    while (revealed < text.length) {
      withFrameNanos { }
      revealed = (revealed + TYPEWRITER_CHARS_PER_FRAME).coerceAtMost(text.length)
    }
  }

  /** True while [text] is the body this reveal belongs to, including partial handovers. */
  private fun continues(text: String): Boolean =
    owner.isNotEmpty() && (text.startsWith(owner) || owner.startsWith(text))

  private fun isRevealed(text: String): Boolean = owner == text && revealed >= text.length
}

@Composable
internal fun rememberChatAnswerTypewriter(sessionKey: String): ChatAnswerTypewriter =
  remember(sessionKey) { ChatAnswerTypewriter() }

/**
 * Only an answer that arrived while the reader was open types itself out.
 *
 * Gateway builds disagree on the unit of a message `timestamp`, so a value that
 * looks like epoch seconds is normalized first: reading it as milliseconds
 * would make every answer look decades old and silently disable the reveal.
 */
internal fun chatAnswerIsFresh(timestampMs: Long?): Boolean {
  val raw = timestampMs ?: return false
  val normalized = if (raw < EPOCH_SECONDS_CUTOFF) raw * 1_000L else raw
  val age = System.currentTimeMillis() - normalized
  return age >= 0 && age < 15_000
}

/** Any timestamp below this is epoch seconds, not milliseconds (2001-09-09). */
private const val EPOCH_SECONDS_CUTOFF = 1_000_000_000_000L
