package ai.openclaw.app.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * Lucide glyphs used by the tool chain.
 *
 * Lucide draws on a 24x24 grid with a round stroke, so the raw `d` strings are
 * kept verbatim and parsed to [Path]s at draw time. The glyphs are static: the
 * "still running" signal belongs to the row label's shimmer, not to the icon.
 */
internal data class LucideGlyph(
  val paths: List<String>,
)

/** Compose has no circle primitive in path data; two half arcs reproduce one. */
private fun circlePath(
  cx: Float,
  cy: Float,
  r: Float,
): String {
  val d = r * 2f
  return "M ${cx - r} $cy a $r $r 0 1 0 $d 0 a $r $r 0 1 0 ${-d} 0"
}

private val TERMINAL =
  LucideGlyph(
    listOf(
      "M12 19h8",
      "m4 17 6-6-6-6",
    ),
  )

private val FILE_TEXT =
  LucideGlyph(
    listOf(
      "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
      "M14 2v5a1 1 0 0 0 1 1h5",
      "M10 9H8",
      "M16 13H8",
      "M16 17H8",
    ),
  )

private val SQUARE_PEN =
  LucideGlyph(
    listOf(
      "M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7",
      "M18.375 2.625a1 1 0 0 1 3 3l-9.013 9.014a2 2 0 0 1-.853.505l-2.873.84a.5.5 0 0 1-.62-.62l.84-2.873a2 2 0 0 1 .506-.852z",
    ),
  )

private val FILE_PLUS =
  LucideGlyph(
    listOf(
      "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
      "M14 2v5a1 1 0 0 0 1 1h5",
      "M9 15h6",
      "M12 18v-6",
    ),
  )

private val FILE_SEARCH =
  LucideGlyph(
    listOf(
      "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
      "M14 2v5a1 1 0 0 0 1 1h5",
      circlePath(11.5f, 14.5f, 2.5f),
      "M13.3 16.3 15 18",
    ),
  )

/**
 * Lucide `sparkles` for the thinking row: one large four-point star, a plus at
 * the top-right and a small ring at the bottom-left.
 *
 * The assistant-app convention for "AI reasoning", drawn with the same
 * round-joined stroke as the tool glyphs so the chain looks like one family.
 */
private val STAR =
  LucideGlyph(
    listOf(
      "M11.017 2.814a1 1 0 0 1 1.966 0l1.051 5.558a2 2 0 0 0 1.594 1.594l5.558 1.051a1 1 0 0 1 0 1.966l-5.558 1.051a2 2 0 0 0-1.594 1.594l-1.051 5.558a1 1 0 0 1-1.966 0l-1.051-5.558a2 2 0 0 0-1.594-1.594l-5.558-1.051a1 1 0 0 1 0-1.966l5.558-1.051a2 2 0 0 0 1.594-1.594z",
      "M20 2v4",
      "M22 4h-4",
      circlePath(4f, 20f, 2f),
    ),
  )

private val GLOBE =
  LucideGlyph(
    listOf(
      circlePath(12f, 12f, 10f),
      "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
      "M2 12h20",
    ),
  )

private val SEARCH =
  LucideGlyph(
    listOf(
      "m21 21-4.34-4.34",
      circlePath(11f, 11f, 8f),
    ),
  )

private val CIRCLE_DASHED =
  LucideGlyph(
    listOf(
      "M10.1 2.182a10 10 0 0 1 3.8 0",
      "M13.9 21.818a10 10 0 0 1-3.8 0",
      "M17.609 3.721a10 10 0 0 1 2.69 2.7",
      "M2.182 13.9a10 10 0 0 1 0-3.8",
      "M20.279 17.609a10 10 0 0 1-2.7 2.69",
      "M21.818 10.1a10 10 0 0 1 0 3.8",
      "M3.721 6.391a10 10 0 0 1 2.7-2.69",
      "M6.391 20.279a10 10 0 0 1-2.69-2.7",
    ),
  )

private val WRENCH =
  LucideGlyph(
    listOf(
      "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.106-3.105c.32-.322.863-.22.983.218a6 6 0 0 1-8.259 7.057l-7.91 7.91a1 1 0 0 1-2.999-3l7.91-7.91a6 6 0 0 1 7.057-8.259c.438.12.54.662.219.984z",
    ),
  )

/** Stacked sheets: the chain holds several steps rather than one tool. */
private val LAYERS =
  LucideGlyph(
    listOf(
      "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
      "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
      "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
    ),
  )

internal fun lucideGlyph(kind: CompletedToolKind): LucideGlyph =
  when (kind) {
    CompletedToolKind.Command -> TERMINAL
    CompletedToolKind.Read -> FILE_TEXT
    CompletedToolKind.Edit -> SQUARE_PEN
    CompletedToolKind.Write -> FILE_PLUS
    CompletedToolKind.Search -> FILE_SEARCH
    CompletedToolKind.Fetch -> GLOBE
    CompletedToolKind.WebSearch -> SEARCH
    CompletedToolKind.Progress -> CIRCLE_DASHED
    CompletedToolKind.Other -> WRENCH
  }

/**
 * Parsed paths are shared across rows: a long tool chain re-issues the same
 * handful of glyphs on every recomposition, and parsing is not cheap.
 */
private val parsedGlyphCache = ConcurrentHashMap<CompletedToolKind, List<Path>>()

private fun parsedGlyph(kind: CompletedToolKind): List<Path> =
  parsedGlyphCache.getOrPut(kind) {
    lucideGlyph(kind).paths.map { d -> PathParser().parsePathString(d).toPath() }
  }

/**
 * The same cache keyed by raw `d` string, for the two glyphs that sit outside
 * the [CompletedToolKind] mapping: the thinking star and the steps layers.
 */
private val parsedPathCache = ConcurrentHashMap<String, Path>()

private fun parsedPathData(data: List<String>): List<Path> =
  data.map { d -> parsedPathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() } }

/**
 * Stroke width on the 24-unit grid. Lucide's stock 2f thins out badly once the
 * glyph is scaled into an 18dp chain row; 3f is the extra-bold step and keeps
 * the small icons readable at a glance.
 */
private const val LUCIDE_STROKE = 3f

/** Draws one stroke glyph, scaled from the 24x24 grid onto the canvas. */
private fun DrawScope.drawLucide(
  paths: List<Path>,
  color: Color,
) {
  val glyphScale = size.minDimension / 24f
  // Pivot must be the grid origin, not DrawScope's default `center`: that
  // default is measured in pixels (the centre of the 16dp box) while the glyph
  // coordinates are in the 0..24 grid. Mixing the two throws the shape off
  // canvas and leaves only a corner visible — hence Offset.Zero everywhere.
  scale(glyphScale, glyphScale, Offset.Zero) {
    val stroke = Stroke(width = LUCIDE_STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
    paths.forEach { path -> drawPath(path = path, color = color, style = stroke) }
  }
}

/**
 * One tool row's icon. Static by design: movement belongs to the label's
 * shimmer, so the glyph keeps the same silhouette before and after a call
 * finishes.
 */
@Composable
internal fun LucideToolIcon(
  kind: CompletedToolKind,
  modifier: Modifier = Modifier,
  tint: Color,
  size: Dp = 16.dp,
) {
  val paths = remember(kind) { parsedGlyph(kind) }
  Canvas(modifier = modifier.size(size)) {
    drawLucide(paths = paths, color = tint)
  }
}

/**
 * Thinking row's mark: a star, held still. Motion now lives in the label's
 * shimmer, so the glyph keeps one silhouette from "思考中…" to "思考完成".
 */
@Composable
internal fun LucideThinkingIcon(
  modifier: Modifier = Modifier,
  tint: Color,
  size: Dp = 16.dp,
) {
  val paths = remember { parsedPathData(STAR.paths) }
  Canvas(modifier = modifier.size(size)) {
    drawLucide(paths = paths, color = tint)
  }
}

/** Stacked-sheets glyph for the collapsed step-chain header; held still. */
@Composable
internal fun LucideStepsIcon(
  modifier: Modifier = Modifier,
  tint: Color,
  size: Dp = 16.dp,
) {
  val paths = remember { parsedPathData(LAYERS.paths) }
  Canvas(modifier = modifier.size(size)) {
    drawLucide(paths = paths, color = tint)
  }
}
