package ai.openclaw.app.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.util.concurrent.ConcurrentHashMap

/**
 * Lucide glyphs used by the tool chain.
 *
 * Lucide draws on a 24x24 grid with a 2px round stroke, so the raw `d` strings
 * are kept verbatim. They are parsed to [Path]s at draw time rather than baked
 * into a vector drawable because the running state trims each stroke by length
 * (see [ToolIconAnimation]), which needs a real path rather than a rendered
 * bitmap of one.
 */
internal data class LucideGlyph(
  val paths: List<String>,
  /** Index into [paths] of the path the animation moves, or null for a whole-glyph move. */
  val animatedPath: Int? = null,
  /**
   * A stroke that never moves while [paths] animates around it.
   *
   * Held apart from [paths] because some glyphs animate only their interior: a
   * spinner reads as a spinner exactly because its ring holds still.
   */
  val ringPath: String? = null,
)

/**
 * Compose has no circle primitive in path data. Two half arcs reproduce one
 * while keeping a single continuous start point, which matters once a draw-on
 * animation trims the stroke by length.
 */
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
    animatedPath = 0,
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
    animatedPath = 2,
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
    animatedPath = 2,
  )

private val FILE_SEARCH =
  LucideGlyph(
    listOf(
      "M6 22a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h8a2.4 2.4 0 0 1 1.704.706l3.588 3.588A2.4 2.4 0 0 1 20 8v12a2 2 0 0 1-2 2z",
      "M14 2v5a1 1 0 0 0 1 1h5",
      circlePath(11.5f, 14.5f, 2.5f),
      "M13.3 16.3 15 18",
    ),
    animatedPath = 3,
  )

/**
 * A ring with a core and two satellites riding it.
 *
 * The ring is what makes the glyph legible at rest. Without it the same three
 * circles are just three dots on a diagonal, and a finished thought loses the
 * "orbit" reading entirely; the ring says "this is a path" whether or not
 * anything is currently moving along it.
 */
private val ORBIT =
  LucideGlyph(
    // Centre core first, then the two satellites, which sit on the ring at
    // radius 10 — (19,5) and (5,19) are both ~9.9 from (12,12).
    paths =
      listOf(
        circlePath(12f, 12f, 3f),
        circlePath(19f, 5f, 2f),
        circlePath(5f, 19f, 2f),
      ),
    // Never rotates: it is the path the satellites travel along.
    ringPath = circlePath(12f, 12f, 10f),
  )

private val GLOBE =
  LucideGlyph(
    listOf(
      circlePath(12f, 12f, 10f),
      "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
      "M2 12h20",
    ),
    animatedPath = 1,
  )

private val SEARCH =
  LucideGlyph(
    listOf(
      "m21 21-4.34-4.34",
      circlePath(11f, 11f, 8f),
    ),
    animatedPath = 0,
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
 * Which motion a running row plays.
 *
 * The source library drives these with framer-motion keyframes; each entry here
 * is the same keyframe list re-expressed for [InfiniteTransition]. Keeping one
 * enum per motion (rather than one per glyph) is what lets a glyph be swapped
 * without also rewriting its animation.
 */
private enum class ToolIconAnimation {
  /** A whole-glyph tilt, for tools with no single meaningful part. */
  Tilt,

  /** A short dash drawn and erased along one path. */
  DrawOn,

  /** A steady spin, for verbs whose meaning is "still working". */
  Spin,
}

private fun animationFor(kind: CompletedToolKind): ToolIconAnimation =
  when (kind) {
    CompletedToolKind.Progress -> ToolIconAnimation.Spin
    CompletedToolKind.Other -> ToolIconAnimation.Tilt
    else -> ToolIconAnimation.DrawOn
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
 * the [CompletedToolKind] mapping: the thinking orbit and the steps layers.
 */
private val parsedPathCache = ConcurrentHashMap<String, Path>()

private fun parsedPathData(data: List<String>): List<Path> =
  data.map { d -> parsedPathCache.getOrPut(d) { PathParser().parsePathString(d).toPath() } }

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
    val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    paths.forEach { path -> drawPath(path = path, color = color, style = stroke) }
  }
}

/**
 * Draws one path trimmed to [length] (0..1) of its own arc length.
 *
 * Measuring via [PathMeasure] is what lets a partly drawn stroke start at the
 * path's own start point — the same semantics the source library gets from
 * animating `pathLength` / `pathOffset`.
 */
private fun DrawScope.drawTrimmedPath(
  path: Path,
  color: Color,
  strokeWidth: Float,
  length: Float,
) {
  val measure = PathMeasure()
  measure.setPath(path, false)
  val total = measure.length
  if (total <= 0f) return
  val visible = (total * length).coerceIn(0f, total)
  val trimmed = Path()
  measure.getSegment(0f, visible, trimmed, true)
  drawPath(
    path = trimmed,
    color = color,
    style = Stroke(strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
  )
}

/**
 * One tool row's icon: static when the call has settled, animated while it
 * runs. The caller's [tint] already carries the settled/running distinction
 * (muted vs accent), so the animation is the only extra signal.
 */
@Composable
internal fun LucideToolIcon(
  kind: CompletedToolKind,
  modifier: Modifier = Modifier,
  tint: Color,
  size: Dp = 16.dp,
  running: Boolean = false,
) {
  val glyph = remember(kind) { lucideGlyph(kind) }
  val paths = remember(kind) { parsedGlyph(kind) }
  val animation = animationFor(kind)
  val transition = rememberInfiniteTransition(label = "toolIcon")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec =
        infiniteRepeatable(
          animation =
            tween(
              durationMillis =
                when (animation) {
                  ToolIconAnimation.Spin -> 2200
                  ToolIconAnimation.DrawOn -> 1800
                  ToolIconAnimation.Tilt -> 1600
                },
              easing = if (animation == ToolIconAnimation.Spin) LinearEasing else FastOutSlowInEasing,
            ),
          repeatMode = RepeatMode.Restart,
        ),
      label = "phase",
    )

  Canvas(modifier = modifier.size(size)) {
    val animatedIndex = glyph.animatedPath
    // Settled rows keep the full stroke and lose the motion entirely: a
    // finished call should read as done, not as a still frame of working.
    if (!running || animatedIndex == null) {
      drawLucide(paths = paths, color = tint)
      return@Canvas
    }
    val glyphScale = this.size.minDimension / 24f
    // Tilt and spin rotate the whole glyph, so the canvas has to be mapped to
    // the 24x24 grid first; rotating inside the scaled pass would spin the
    // stroke width along with the shape.
    when (animation) {
      ToolIconAnimation.DrawOn -> {
        val stroke = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        scale(glyphScale, glyphScale, Offset.Zero) {
          paths.forEachIndexed { index, path ->
            if (index != animatedIndex) {
              drawPath(path = path, color = tint, style = stroke)
            }
          }
          // Breathes between a full stroke and roughly a sixth of one, so the
          // shape stays legible instead of blinking out at the trough.
          drawTrimmedPath(paths[animatedIndex], tint, 2f, 1f - 0.16f * phase)
        }
      }
      ToolIconAnimation.Spin -> {
        val pivot = Offset(12f * glyphScale, 12f * glyphScale)
        rotate(degrees = 360f * phase, pivot = pivot) {
          drawLucide(paths = paths, color = tint)
        }
      }
      ToolIconAnimation.Tilt -> {
        val pivot = Offset(12f * glyphScale, 12f * glyphScale)
        // Eases out to a small lean and back, like a wrench being turned.
        val degrees = 12f * kotlin.math.sin(phase * 2f * Math.PI).toFloat()
        rotate(degrees = degrees, pivot = pivot) {
          drawLucide(paths = paths, color = tint)
        }
      }
    }
  }
}

/**
 * Runs the two satellites around the ring while the model is thinking.
 *
 * Only the satellites move. The ring and the core are drawn outside the
 * rotation, which is what makes the glyph still read as an orbit once thinking
 * stops: the path stays drawn, so a finished thought is an orbit at rest rather
 * than three loose dots. While thinking, the ring is the fixed frame the
 * satellites sweep along, so the motion reads as circulation rather than spin.
 */
@Composable
internal fun LucideThinkingIcon(
  modifier: Modifier = Modifier,
  tint: Color,
  size: Dp = 16.dp,
  running: Boolean = true,
) {
  val core = remember { parsedPathData(listOf(ORBIT.paths[0])) }
  val satellites = remember { parsedPathData(ORBIT.paths.drop(1)) }
  val ring = remember { parsedPathData(listOf(ORBIT.ringPath!!)) }
  val stroke = remember { Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round) }
  if (!running) {
    // At rest the whole glyph is drawn still: ring, core and satellites all
    // keep their places, so the silhouette is the orbit itself.
    Canvas(modifier = modifier.size(size)) {
      drawLucide(paths = ring + satellites + core, color = tint)
    }
    return
  }
  val transition = rememberInfiniteTransition(label = "thinkingIcon")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      // Slower than a spinner on purpose: a satellite that laps the ring in a
      // second reads as a loading widget, while one that takes its time reads
      // as something actually going around.
      animationSpec = infiniteRepeatable(tween(durationMillis = 2600, easing = LinearEasing)),
      label = "orbit",
    )
  Canvas(modifier = modifier.size(size)) {
    scale(this.size.minDimension / 24f, this.size.minDimension / 24f, Offset.Zero) {
      // Ring and core first so the satellites pass over them.
      ring.forEach { path -> drawPath(path = path, color = tint, style = stroke) }
      core.forEach { path -> drawPath(path = path, color = tint, style = stroke) }
      rotate(degrees = phase, pivot = Offset(12f, 12f)) {
        satellites.forEach { path -> drawPath(path = path, color = tint, style = stroke) }
      }
    }
  }
}

/**
 * Draws the stacked-sheets glyph used by the collapsed step-chain header.
 *
 * The three sheets drift in turn while the chain is collapsed, which is the
 * same "still collecting steps" signal the header's count already carries; once
 * the reader expands it the layers hold still and let the rows speak.
 */
@Composable
internal fun LucideStepsIcon(
  modifier: Modifier = Modifier,
  tint: Color,
  size: Dp = 16.dp,
  running: Boolean = false,
) {
  val paths = remember { parsedPathData(LAYERS.paths) }
  val transition = rememberInfiniteTransition(label = "stepsIcon")
  val phase by
    transition.animateFloat(
      initialValue = 0f,
      targetValue = 1f,
      animationSpec = infiniteRepeatable(tween(durationMillis = 2400, easing = FastOutSlowInEasing)),
      label = "layers",
    )
  Canvas(modifier = modifier.size(size)) {
    val glyphScale = this.size.minDimension / 24f
    scale(glyphScale, glyphScale, Offset.Zero) {
      val stroke = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
      paths.forEachIndexed { index, path ->
        // Each sheet leads the one below it by a third of the cycle, so the
        // stack reads as a wave rather than pulsing in unison.
        val local = if (running) (phase + index / 3f).mod(1f) else 0f
        val alpha = if (running) 0.45f + 0.55f * kotlin.math.sin(local * Math.PI).toFloat() else 1f
        drawPath(path = path, color = tint.copy(alpha = tint.alpha * alpha), style = stroke)
      }
    }
  }
}
