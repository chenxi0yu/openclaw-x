package ai.openclaw.app.ui.chat

/**
 * Splits a run of plain text into alternating literal / inline-math segments.
 *
 * Inline math (`$...$`) is the one delimiter the block segmenter never sees: its
 * content is embedded in a paragraph or a table cell, so it has to be cut out of the
 * text after CommonMark has already flattened the node into a literal string.
 *
 * Rules kept deliberately conservative, matching the way a reader expects `$` to
 * behave in prose:
 * - `$$` never opens an inline span (that is display math, handled upstream).
 * - An escaped `\$` is a literal dollar sign.
 * - The span must be closed on the same line; an unclosed `$` stays literal.
 * - There must be no whitespace right after the opening `$` or right before the
 *   closing one, so prices like `$5 and $7` do not turn into math.
 */
internal fun splitInlineLatex(
  text: String,
  isStreaming: Boolean = false,
): List<InlineLatexSegment> {
  if (!text.contains('$')) return listOf(InlineLatexSegment.Text(text))

  val segments = mutableListOf<InlineLatexSegment>()
  val literal = StringBuilder()
  var index = 0

  fun flushLiteral() {
    if (literal.isNotEmpty()) {
      segments.add(InlineLatexSegment.Text(literal.toString()))
      literal.setLength(0)
    }
  }

  while (index < text.length) {
    val char = text[index]

    // Escaped dollar sign: keep the character itself and drop the escape.
    if (char == '\\' && index + 1 < text.length && text[index + 1] == '$') {
      literal.append('$')
      index += 2
      continue
    }

    if (char != '$') {
      literal.append(char)
      index += 1
      continue
    }

    // `$$` is display math and is owned by the block segmenter.
    if (index + 1 < text.length && text[index + 1] == '$') {
      literal.append("$$")
      index += 2
      continue
    }

    val close = findInlineClose(text = text, open = index)
    if (close == null) {
      // Unclosed while streaming may still complete on a later frame; either way the
      // dollar sign is not yet math, so it stays part of the literal run.
      literal.append(text, index, text.length)
      index = text.length
      continue
    }

    flushLiteral()
    segments.add(InlineLatexSegment.Math(text.substring(index + 1, close).trim()))
    index = close + 1
  }

  flushLiteral()
  return if (segments.isEmpty()) listOf(InlineLatexSegment.Text(text)) else segments
}

/**
 * Finds the closing `$` for the span opened at [open], or null when the line ends
 * first. Only same-line pairs are considered so a stray dollar sign cannot swallow
 * the rest of the paragraph.
 */
private fun findInlineClose(
  text: String,
  open: Int,
): Int? {
  var index = open + 1
  while (index < text.length) {
    val char = text[index]
    if (char == '\n') return null
    if (char == '\\' && index + 1 < text.length) {
      index += 2
      continue
    }
    if (char == '$') {
      // `$$` closing is not an inline terminator.
      if (index + 1 < text.length && text[index + 1] == '$') {
        index += 2
        continue
      }
      // Reject "a $ b" and "a $b $" patterns that are prose, not math.
      if (index == open + 1 || text[index - 1].isWhitespace()) return null
      val inner = text.substring(open + 1, index)
      if (inner.isBlank() || inner.first().isWhitespace() || inner.last().isWhitespace()) return null
      return index
    }
    index += 1
  }
  return null
}

/** One run of a paragraph: either literal text or an inline LaTeX expression. */
internal sealed interface InlineLatexSegment {
  data class Text(
    val text: String,
  ) : InlineLatexSegment

  data class Math(
    val latex: String,
  ) : InlineLatexSegment
}
