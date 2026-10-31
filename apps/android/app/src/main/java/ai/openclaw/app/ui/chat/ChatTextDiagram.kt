package ai.openclaw.app.ui.chat

import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.commonmark.node.FencedCodeBlock
import java.util.Locale
import kotlin.math.abs

internal const val CHAT_TEXT_DIAGRAM_MAX_LINES = 256
internal const val CHAT_TEXT_DIAGRAM_MAX_CHARS = 20_000

private const val DIAGRAM_TAB_STOP = 8
private const val ZERO_WIDTH_JOINER = 0x200D
private const val EMOJI_VARIATION_SELECTOR = 0xFE0F

// Untagged/text fences are what models use for monospaced diagrams; language-tagged
// fences keep the syntax-highlighted code renderer.
private val textDiagramLanguages = setOf("", "text", "txt", "plain", "plaintext", "ascii", "diagram", "flowchart")

private val diagramFontScales = listOf(0.82f, 0.75f, 0.69f, 0.63f, 0.56f)

internal fun isChatTextDiagramFence(
  block: FencedCodeBlock,
  isStreaming: Boolean,
): Boolean {
  // Match Mermaid: keep a streaming fence in the plain renderer until it closes so
  // the grid does not reflow on every token.
  if (isStreaming && block.closingFenceLength == null) return false
  val language =
    block.info
      .orEmpty()
      .trim()
      .takeWhile { !it.isWhitespace() }
      .lowercase(Locale.US)
  if (language !in textDiagramLanguages) return false
  return looksLikeChatTextDiagram(block.literal.orEmpty())
}

/**
 * Spaced Unicode diagrams (arrows, box drawing, block glyphs) are positional: the
 * column of every glyph carries meaning, so wrapping them destroys the layout.
 */
internal fun looksLikeChatTextDiagram(code: String): Boolean {
  if (code.length > CHAT_TEXT_DIAGRAM_MAX_CHARS) return false
  val lines = code.split('\n').map { it.removeSuffix("\r") }.filter { it.isNotBlank() }
  if (lines.size < 3 || lines.size > CHAT_TEXT_DIAGRAM_MAX_LINES) return false
  val glyphLines = lines.count { line -> line.any(::isDiagramGlyph) }
  if (glyphLines < 2) return looksLikeAsciiDiagram(lines)
  val standaloneGlyphLines = lines.count { line -> line.all { it.isWhitespace() || isDiagramGlyph(it) } }
  val indentedLines = lines.count { line -> line.takeWhile { it == ' ' }.length >= 4 }
  return standaloneGlyphLines >= 1 || indentedLines >= 2
}

private fun isDiagramGlyph(char: Char): Boolean =
  char in '\u2190'..'\u21FF' ||
    char in '\u2500'..'\u259F' ||
    char in '\u25A0'..'\u25FF'

private fun looksLikeAsciiDiagram(lines: List<String>): Boolean {
  val connectorLines =
    lines.count { line ->
      val trimmed = line.trim()
      asciiArrowMarkers.any(line::contains) || (trimmed.length == 1 && trimmed[0] in "|vV^")
    }
  val frameLines =
    lines.count { line ->
      val trimmed = line.trim()
      trimmed.length >= 3 && trimmed.any { it == '|' || it == '+' } && trimmed.all { it in "|+-=<>vV^:. " }
    }
  return connectorLines >= 2 && frameLines >= 2
}

private val asciiArrowMarkers = listOf("->", "<-", "-->", "<--", "==>", "<==")

internal data class ChatDiagramCluster(
  val text: String,
  val cells: Int,
)

internal data class ChatDiagramLine(
  val clusters: List<ChatDiagramCluster>,
) {
  val cells: Int get() = clusters.sumOf { it.cells }
}

internal fun chatDiagramLines(code: String): List<ChatDiagramLine> =
  code
    .trimEnd()
    .split('\n')
    .map { line -> ChatDiagramLine(chatDiagramClusters(line.removeSuffix("\r"))) }

internal fun chatDiagramCellWidth(line: String): Int = chatDiagramClusters(line).sumOf { it.cells }

/**
 * Splits a line into terminals-style cells. Full-width glyphs occupy two columns so
 * source text padded in a terminal keeps the same alignment here, independent of the
 * actual monospace font metrics.
 */
internal fun chatDiagramClusters(line: String): List<ChatDiagramCluster> {
  val clusters = mutableListOf<ChatDiagramCluster>()
  var column = 0
  var index = 0
  while (index < line.length) {
    val codePoint = line.codePointAt(index)
    if (codePoint == '\t'.code) {
      val cells = DIAGRAM_TAB_STOP - column % DIAGRAM_TAB_STOP
      clusters += ChatDiagramCluster(" ".repeat(cells), cells)
      column += cells
      index += 1
      continue
    }
    val start = index
    var wide = isDiagramWide(codePoint)
    var hasBase = !isDiagramZeroWidth(codePoint)
    index += Character.charCount(codePoint)
    var joinsNext = codePoint == ZERO_WIDTH_JOINER
    while (index < line.length) {
      val next = line.codePointAt(index)
      if (joinsNext) {
        wide = wide || isDiagramWide(next)
        hasBase = hasBase || !isDiagramZeroWidth(next)
        index += Character.charCount(next)
        joinsNext = next == ZERO_WIDTH_JOINER
        continue
      }
      if (isDiagramZeroWidth(next)) {
        if (next == EMOJI_VARIATION_SELECTOR) wide = true
        index += Character.charCount(next)
        joinsNext = next == ZERO_WIDTH_JOINER
        continue
      }
      break
    }
    val cells =
      when {
        wide -> 2
        !hasBase -> 0
        else -> 1
      }
    clusters += ChatDiagramCluster(line.substring(start, index), cells)
    column += cells
  }
  return clusters
}

private fun isDiagramZeroWidth(codePoint: Int): Boolean =
  codePoint == ZERO_WIDTH_JOINER ||
    codePoint in 0xFE00..0xFE0F ||
    codePoint in 0xE0100..0xE01EF ||
    when (Character.getType(codePoint)) {
      Character.NON_SPACING_MARK.toInt(),
      Character.ENCLOSING_MARK.toInt(),
      Character.COMBINING_SPACING_MARK.toInt(),
      -> true
      else -> false
    }

// Terminal width (wcwidth) wide/fullwidth ranges. Arrows and geometric glyphs are
// deliberately narrow: terminals advance them one column, while CJK advances two.
private fun isDiagramWide(codePoint: Int): Boolean =
  codePoint in 0x1100..0x115F ||
    codePoint == 0x2329 ||
    codePoint == 0x232A ||
    codePoint in 0x2E80..0x303E ||
    codePoint in 0x3041..0x33FF ||
    codePoint in 0x3400..0x4DBF ||
    codePoint in 0x4E00..0x9FFF ||
    codePoint in 0xA000..0xA4CF ||
    codePoint in 0xA960..0xA97F ||
    codePoint in 0xAC00..0xD7A3 ||
    codePoint in 0xF900..0xFAFF ||
    codePoint in 0xFE10..0xFE19 ||
    codePoint in 0xFE30..0xFE6F ||
    codePoint in 0xFF00..0xFF60 ||
    codePoint in 0xFFE0..0xFFE6 ||
    codePoint in 0x1B000..0x1B2FF ||
    codePoint in 0x1F300..0x1F64F ||
    codePoint in 0x1F900..0x1F9FF ||
    codePoint in 0x1FA70..0x1FAFF ||
    codePoint in 0x20000..0x3FFFD

@Composable
internal fun ChatTextDiagramBlock(code: String) {
  val colors = ClawTheme.colors
  val density = LocalDensity.current
  val measurer = rememberTextMeasurer()
  val baseStyle = ClawTheme.type.body
  val lines = remember(code) { chatDiagramLines(code) }
  val maxCells = remember(lines) { lines.maxOfOrNull { it.cells } ?: 1 }

  Surface(
    shape = RoundedCornerShape(8.dp),
    color = colors.codeBg,
    border = BorderStroke(1.dp, colors.codeBorder),
    modifier = Modifier.fillMaxWidth(),
  ) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
      val horizontalPadding = 12.dp
      val availableWidthPx =
        with(density) { (maxWidth - horizontalPadding * 2).toPx() }.coerceAtLeast(1f)
      val fontSize =
        remember(baseStyle, maxCells, availableWidthPx, measurer, density) {
          val fitted =
            diagramFontScales.firstOrNull { scale ->
              val size = baseStyle.fontSize * scale
              val cellWidth =
                chatDiagramCellWidthPx(measurer, baseStyle.copy(fontFamily = FontFamily.Monospace, fontSize = size))
              maxCells * cellWidth <= availableWidthPx
            }
          baseStyle.fontSize * (fitted ?: diagramFontScales.last())
        }
      val style =
        remember(baseStyle, fontSize) {
          baseStyle.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            lineHeight = fontSize * 1.45f,
          )
        }
      val text = remember(lines, style, measurer, density) { buildDiagramText(lines, style, measurer, density) }
      val nativeSelection = LocalChatMarkdownNativeSelection.current
      val hit = rememberChatTextHitTarget()

      Box(modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 10.dp)) {
        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
          val diagram =
            @Composable {
              Text(
                text = text,
                style = style,
                color = colors.codeText,
                softWrap = false,
                modifier = hit.modifier,
                onTextLayout = hit::onTextLayout,
              )
            }
          if (nativeSelection) {
            SelectionContainer {
              diagram()
            }
          } else {
            diagram()
          }
        }
      }
    }
  }
}

private fun chatDiagramCellWidthPx(
  measurer: TextMeasurer,
  style: TextStyle,
): Float = measurer.measure(AnnotatedString("0000000000"), style = style, softWrap = false, maxLines = 1).size.width / 10f

/**
 * Pads each cluster to its terminal cell width with letter spacing. The source text
 * stays untouched for selection/copy; only glyph advances are normalized.
 */
private fun buildDiagramText(
  lines: List<ChatDiagramLine>,
  style: TextStyle,
  measurer: TextMeasurer,
  density: Density,
): AnnotatedString {
  val cellWidth = chatDiagramCellWidthPx(measurer, style)
  return buildAnnotatedString {
    lines.forEachIndexed { lineIndex, line ->
      line.clusters.forEachIndexed { clusterIndex, cluster ->
        val isLastInLine = clusterIndex == line.clusters.lastIndex
        if (isLastInLine || cluster.cells == 0) {
          append(cluster.text)
        } else {
          val natural = measurer.measure(AnnotatedString(cluster.text), style = style, softWrap = false, maxLines = 1).size.width
          val delta = cluster.cells * cellWidth - natural.toFloat()
          if (abs(delta) < 0.05f) {
            append(cluster.text)
          } else {
            withStyle(SpanStyle(letterSpacing = with(density) { delta.toSp() })) {
              append(cluster.text)
            }
          }
        }
      }
      if (lineIndex != lines.lastIndex) append('\n')
    }
  }
}
