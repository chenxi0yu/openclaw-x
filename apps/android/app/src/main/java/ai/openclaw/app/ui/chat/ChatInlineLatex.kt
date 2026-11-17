package ai.openclaw.app.ui.chat

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily

/**
 * Splits a run of plain text into alternating literal / inline-math segments.
 *
 * Inline math is the one delimiter the block segmenter never sees: its content is
 * embedded in a paragraph, heading, or table cell, so it has to be cut out of the
 * text after CommonMark has already flattened the node into a literal string.
 *
 * Rules kept deliberately conservative, matching the way a reader expects `$` to
 * behave in prose:
 * - `$...$`, `$$...$$`, `\(...\)` and `\[...\]` are accepted on one line.
 * - An escaped `\$` is a literal dollar sign.
 * - The span must be closed on the same line; an unclosed opener stays literal.
 * - For `$...$` there must be no whitespace right after the opening `$` or right
 *   before the closing one, so prices like `$5 and $7` do not turn into math.
 *
 * Segments carry their offsets so renderers can copy the marked-up text (bold,
 * inline code, links) around a formula instead of flattening it back to plain.
 */
internal fun splitInlineLatex(
  text: String,
  isStreaming: Boolean = false,
): List<InlineLatexSegment> {
  if (!containsInlineMath(text)) {
    // Models often write a formula without any `$` delimiters (`(a^x)'=a^x\ln a`,
    // `x^x=e^{x\ln x}`). Recover only high-confidence bare TeX so ordinary prose
    // such as `C:\Users` or `a_b` stays literal.
    return splitBareLatex(text) ?: listOf(InlineLatexSegment.Text(text, 0, text.length))
  }

  val segments = mutableListOf<InlineLatexSegment>()
  var literalStart = 0
  var index = 0

  fun flushLiteral(end: Int) {
    if (end > literalStart) {
      segments.add(InlineLatexSegment.Text(text.substring(literalStart, end), literalStart, end))
    }
  }

  while (index < text.length) {
    val span = findInlineMathSpan(text, index)
    if (span == null) {
      index += 1
      continue
    }
    flushLiteral(span.start)
    segments.add(
      InlineLatexSegment.Math(
        latex = text.substring(span.contentStart, span.contentEnd).trim(),
        start = span.start,
        end = span.end,
      ),
    )
    index = span.end
    literalStart = span.end
  }
  flushLiteral(text.length)
  return segments.ifEmpty { listOf(InlineLatexSegment.Text(text, 0, text.length)) }
}

/**
 * CommonMark resolves backslash escapes (`\\` becomes `\`, `\,` becomes `,`)
 * before an inline formula reaches the TeX engine. Doubling the backslashes
 * inside each math span before parsing makes that resolution a no-op, so matrix
 * row breaks and thin spaces survive intact.
 *
 * Fenced code, indented code, and inline code are left untouched: a `$` there is
 * literal text, not math.
 */
internal fun protectChatInlineMath(source: String): String {
  // Bare TeX also needs its backslashes protected from CommonMark escapes, so the
  // gate cannot require a `$` delimiter here either.
  if (!source.contains('\\') && !containsInlineMath(source)) return source
  val lines = source.split('\n')
  val protected = ArrayList<String>(lines.size)
  var fenceChar: Char? = null
  var fenceLength = 0
  for (line in lines) {
    val trimmed = line.trimStart(' ')
    val indent = line.length - trimmed.length
    if (fenceChar != null) {
      protected.add(line)
      if (indent <= 3 && isClosingFence(trimmed, fenceChar, fenceLength)) {
        fenceChar = null
        fenceLength = 0
      }
      continue
    }
    if (indent <= 3) {
      val opener = openingFence(trimmed)
      if (opener != null) {
        fenceChar = opener.first
        fenceLength = opener.second
        protected.add(line)
        continue
      }
    }
    if (indent >= 4) {
      // Indented code blocks are literal; a pseudo-opener inside one must not
      // split the line as math.
      protected.add(line)
      continue
    }
    protected.add(protectInlineMathInLine(line))
  }
  return protected.joinToString("\n")
}

internal sealed interface InlineLatexSegment {
  val start: Int
  val end: Int

  data class Text(
    val text: String,
    override val start: Int,
    override val end: Int,
  ) : InlineLatexSegment

  data class Math(
    val latex: String,
    override val start: Int,
    override val end: Int,
  ) : InlineLatexSegment
}

internal fun containsInlineMath(text: String): Boolean =
  text.contains('$') || text.contains("\\[") || text.contains("\\(")

private data class InlineMathSpan(
  val start: Int,
  val contentStart: Int,
  val contentEnd: Int,
  val end: Int,
)

/**
 * Finds a complete inline formula opening at [index], or null when this index is
 * not an opener or the span closes on another line. Shared by the renderer and
 * the backslash protector so both always agree on what counts as math.
 */
private fun findInlineMathSpan(
  text: String,
  index: Int,
): InlineMathSpan? {
  if (index >= text.length) return null
  if (text[index] == '\\' && index + 1 < text.length) {
    val opener = text[index + 1]
    val closeToken =
      when (opener) {
        '[' -> "\\]"
        '(' -> "\\)"
        else -> return null
      }
    val close = findCommandClose(text, index + 2, closeToken) ?: return null
    if (text.substring(index + 2, close).isBlank()) return null
    return InlineMathSpan(index, index + 2, close, close + 2)
  }
  if (text[index] != '$') return null
  if (index + 1 < text.length && text[index + 1] == '$') {
    val close = findDoubleDollarClose(text, index + 2) ?: return null
    if (text.substring(index + 2, close).isBlank()) return null
    return InlineMathSpan(index, index + 2, close, close + 2)
  }
  val close = findSingleDollarClose(text, index) ?: return null
  return InlineMathSpan(index, index + 1, close, close + 1)
}

private fun findSingleDollarClose(
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

private fun findDoubleDollarClose(
  text: String,
  from: Int,
): Int? {
  var index = from
  while (index < text.length) {
    val char = text[index]
    if (char == '\n') return null
    if (char == '\\' && index + 1 < text.length) {
      index += 2
      continue
    }
    if (char == '$' && index + 1 < text.length && text[index + 1] == '$') return index
    index += 1
  }
  return null
}

/**
 * Recovers delimited-looking formulas the model forgot to wrap in `$`.
 *
 * Only two shapes qualify, because both are unambiguous in prose:
 * - a parenthesis-balanced run that contains TeX structure (`\ln`, `^`, `_`,
 *   `\cdot`, …) — e.g. `((e^x)'=e^x)` or `(x^x=e^{x\ln x})`;
 * - a whitespace-free run carrying a TeX command — e.g. `2^{3x}\ln2\cdot 3`.
 *
 * Everything else stays literal, so paths (`C:\Users`), snake_case names and
 * ordinary parenthetical prose never turn into math. Returns null when no
 * segment qualifies, letting the caller keep the plain text.
 */
private fun splitBareLatex(text: String): List<InlineLatexSegment>? {
  val segments = mutableListOf<InlineLatexSegment>()
  var literalStart = 0
  var index = 0
  while (index < text.length) {
    val span = bareLatexSpanAt(text, index)
    if (span == null) {
      index += 1
      continue
    }
    if (span.start > literalStart) {
      segments += InlineLatexSegment.Text(text.substring(literalStart, span.start), literalStart, span.start)
    }
    segments +=
      InlineLatexSegment.Math(
        latex = text.substring(span.contentStart, span.contentEnd).trim(),
        start = span.start,
        end = span.end,
      )
    literalStart = span.end
    index = span.end
  }
  if (segments.isEmpty()) return null
  if (literalStart < text.length) {
    segments += InlineLatexSegment.Text(text.substring(literalStart), literalStart, text.length)
  }
  return segments
}

private fun bareLatexSpanAt(
  text: String,
  index: Int,
): InlineMathSpan? {
  val open = text[index]
  // Shape 1: a balanced parenthesis run.
  if (open == '(') {
    val close = matchingParen(text, index) ?: return null
    val inner = text.substring(index + 1, close)
    if (inner.length < 3 || '\n' in inner) return null
    // Prose parentheses ("(see below)", "(注意)") carry no TeX structure; a real
    // formula has a command or a super/subscript and no CJK prose.
    if (inner.any { it.code in 0x3000..0x9FFF || it.code in 0xFF00..0xFFEF }) return null
    if (!hasTexOperator(inner)) return null
    if (countTexMarkers(inner) < 1) return null
    return InlineMathSpan(index, index + 1, close, close + 1)
  }
  // Shape 2: a whitespace-free ASCII token carrying a TeX command, e.g.
  // `2^{3x}\ln2\cdot 3` or `x^x=e^{x\ln x}`. CJK/punctuation breaks the token,
  // and the token must contain a real command so a bare path stays literal.
  if (open.isLetterOrDigit()) {
    var end = index
    while (end < text.length) {
      val c = text[end]
      if (c.isWhitespace() || c == '$' || c == '（' || c == '，' || c == '。' || c == '）') break
      if (c.code in 0x3000..0x9FFF || c.code in 0xFF00..0xFFEF) break
      end += 1
    }
    if (end - index < 4) return null
    val token = text.substring(index, end)
    if (!Regex("\\\\[a-zA-Z]{2,}").containsMatchIn(token)) return null
    if (token.count { it == '\\' } == 0) return null
    return InlineMathSpan(index, index, end, end)
  }
  return null
}

private fun matchingParen(
  text: String,
  open: Int,
): Int? {
  var depth = 0
  var index = open
  while (index < text.length) {
    when (text[index]) {
      '(' -> depth += 1
      ')' -> {
        depth -= 1
        if (depth == 0) return index
      }
      '\n' -> return null
    }
    index += 1
  }
  return null
}

/** TeX-only markers: a backslash command, super/subscript, or a math operator. */
private fun countTexMarkers(s: String): Int {
  var markers = 0
  var index = 0
  while (index < s.length) {
    when (s[index]) {
      '\\' -> {
        markers += 1
        index += 2
      }
      '^', '_' -> {
        markers += 1
        index += 1
      }
      else -> index += 1
    }
  }
  return markers
}

/** True when the run has a TeX command or a super/subscript — not just parens. */
private fun hasTexOperator(s: String): Boolean =
  s.contains('^') || s.contains('_') || Regex("\\\\[a-zA-Z]{2,}").containsMatchIn(s)

private fun findCommandClose(
  text: String,
  from: Int,
  closeToken: String,
): Int? {  var index = from
  while (index < text.length) {
    val char = text[index]
    if (char == '\n') return null
    if (char == '\\' && index + 1 < text.length) {
      if (text.startsWith(closeToken, index)) return index
      index += 2
      continue
    }
    index += 1
  }
  return null
}

private fun protectInlineMathInLine(line: String): String {
  if (!line.contains('\\') && !containsInlineMath(line)) return line
  val out = StringBuilder(line.length + 8)
  var index = 0
  while (index < line.length) {
    if (line[index] == '`') {
      val start = index
      var run = 0
      while (index < line.length && line[index] == '`') {
        run += 1
        index += 1
      }
      var search = index
      var close = -1
      while (search < line.length) {
        if (line[search] != '`') {
          search += 1
          continue
        }
        val runStart = search
        var closeRun = 0
        while (search < line.length && line[search] == '`') {
          closeRun += 1
          search += 1
        }
        if (closeRun == run) {
          close = search
          break
        }
        if (search == runStart) search += 1
      }
      if (close >= 0) {
        out.append(line, start, close)
        index = close
      } else {
        out.append(line, start, line.length)
        index = line.length
      }
      continue
    }
    val span = findInlineMathSpan(line, index)
    if (span != null) {
      out.append(line, index, span.contentStart)
      out.append(line.substring(span.contentStart, span.contentEnd).replace("\\", "\\\\"))
      out.append(line, span.contentEnd, span.end)
      index = span.end
      continue
    }
    // Bare TeX has no delimiters to keep, but its backslashes still must survive
    // CommonMark, otherwise `\ln`/`\cdot` reach the TeX engine as plain letters.
    val bare = bareLatexSpanAt(line, index)
    if (bare != null) {
      out.append(line, index, bare.contentStart)
      out.append(line.substring(bare.contentStart, bare.contentEnd).replace("\\", "\\\\"))
      out.append(line, bare.contentEnd, bare.end)
      index = bare.end
      continue
    }
    out.append(line[index])
    index += 1
  }
  return out.toString()
}

private fun openingFence(trimmed: String): Pair<Char, Int>? {
  val char = trimmed.firstOrNull() ?: return null
  if (char != '`' && char != '~') return null
  var run = 0
  while (run < trimmed.length && trimmed[run] == char) run += 1
  if (run < 3) return null
  // A backtick fence's info string cannot contain another backtick.
  if (char == '`' && trimmed.drop(run).contains('`')) return null
  return char to run
}

private fun isClosingFence(
  trimmed: String,
  char: Char,
  length: Int,
): Boolean {
  var run = 0
  while (run < trimmed.length && trimmed[run] == char) run += 1
  return run >= length && trimmed.drop(run).isBlank()
}

/**
 * Code runs (inline code and code blocks) render in the platform monospace font,
 * which on some devices has no superscript plus/minus glyph. Re-point just the
 * super/subscript range at the app font so chemistry like `4H⁺` stops showing
 * tofu boxes; everything else keeps its monospace metrics.
 */
internal fun AnnotatedString.withScriptGlyphFallback(family: FontFamily?): AnnotatedString {
  if (family == null || isEmpty()) return this
  var runStart = -1
  var builder: AnnotatedString.Builder? = null
  fun closeRun(end: Int) {
    if (runStart < 0) return
    val target = builder ?: AnnotatedString.Builder(this).also { builder = it }
    target.addStyle(SpanStyle(fontFamily = family), runStart, end)
    runStart = -1
  }
  for (index in text.indices) {
    if (needsScriptFallback(text[index])) {
      if (runStart < 0) runStart = index
    } else {
      closeRun(index)
    }
  }
  closeRun(text.length)
  return builder?.toAnnotatedString() ?: this
}

private fun needsScriptFallback(char: Char): Boolean {
  val code = char.code
  return code in 0x2070..0x209F ||
    code == 0x00B2 ||
    code == 0x00B3 ||
    code == 0x00B9 ||
    code == 0x00BA
}
