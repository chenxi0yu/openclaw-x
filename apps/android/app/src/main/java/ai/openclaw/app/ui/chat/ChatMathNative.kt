package ai.openclaw.app.ui.chat

import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import ru.noties.jlatexmath.JLatexMathDrawable

/**
 * A formula laid out by the native TeX engine. The drawable paints itself onto a
 * Canvas on demand, so nothing here is a bitmap: the same layout re-renders crisply
 * at any scale and costs nothing to keep around.
 */
internal class ChatMathLayout(
  val latex: String,
  internal val drawable: Drawable,
  val widthPx: Int,
  val heightPx: Int,
)

private const val MATH_LAYOUT_CACHE_ENTRIES = 256

// Parsing mutates the engine's shared font tables on first use, so every layout
// runs on one dedicated thread. That removes the static-init race and still costs
// nothing: a formula lays out in about a millisecond.
private val chatMathLayoutDispatcher =
  Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "chat-math-layout").apply { isDaemon = true }
  }.asCoroutineDispatcher()

// Parsing is pure CPU and keyed on every visual input, so a formula repeated
// across a transcript (or across typewriter frames) is laid out exactly once.
private val chatMathLayoutCache = LruCache<String, ChatMathLayout>(MATH_LAYOUT_CACHE_ENTRIES)

/** Synchronous cache probe; lets an already-laid-out formula skip the frame gap. */
internal fun cachedChatMathLayout(
  latex: String,
  textSizePx: Float,
  colorArgb: Int,
): ChatMathLayout? = chatMathLayoutCache.get("$colorArgb|$textSizePx|${normalizeChatLatex(latex)}")

private fun layoutChatMath(
  latex: String,
  textSizePx: Float,
  colorArgb: Int,
): ChatMathLayout? {
  // LLM-authored TeX routinely uses commands the bundled engine lacks; rewriting
  // them to supported equivalents before layout is the difference between a
  // rendered formula and the raw source.
  val normalized = normalizeChatLatex(latex)
  val key = "$colorArgb|$textSizePx|$normalized"
  chatMathLayoutCache.get(key)?.let { return it }
  val layout =
    runCatching {
      val drawable =
        JLatexMathDrawable
          .builder(normalized)
          .textSize(textSizePx)
          .color(colorArgb)
          .build()
      val width = drawable.intrinsicWidth
      val height = drawable.intrinsicHeight
      if (width <= 0 || height <= 0) return@runCatching null
      ChatMathLayout(latex, drawable, width, height)
    }.getOrNull() ?: return null
  chatMathLayoutCache.put(key, layout)
  return layout
}

private val chatLatexBareVerticalBar = Regex("""\\[lr]vert(?![a-zA-Z])""")
private val chatLatexTag = Regex("""\\tag\*?\s*\{[^{}]*\}""")
private val chatLatexLabel = Regex("""\\label\s*\{[^{}]*\}""")
private val chatLatexSpacing = Regex("""\\(?:hspace|vspace)\*?\s*\{[^{}]*\}""")
private val chatLatexMathClass = Regex("""\\(?:mathbin|mathrel|mathop|mathord|mathopen|mathclose|mathpunct|mathinner)\s*""")
private val chatLatexEquationEnv = Regex("""\\(?:begin|end)\{equation\*?\}""")

/**
 * Rewrites TeX the bundled engine cannot parse. `\lvert`/`\rvert` are the common
 * LLM spelling of `\vert` and are not in jlatexmath's command set; equation
 * numbering and spacing commands are presentation-only and would otherwise
 * fail the whole formula.
 */
internal fun normalizeChatLatex(latex: String): String {
  if (latex.isEmpty()) return latex
  var out = latex
  if (out.contains("\\lvert") || out.contains("\\rvert")) {
    // A lambda keeps the replacement's backslash literal; Matcher replacement
    // strings drop `\v`.
    out = chatLatexBareVerticalBar.replace(out) { "\\vert" }
  }
  if (out.contains("\\tag")) out = out.replace(chatLatexTag, "")
  if (out.contains("\\label")) out = out.replace(chatLatexLabel, "")
  if (out.contains("hspace") || out.contains("vspace")) out = out.replace(chatLatexSpacing, "")
  if (out.contains("equation")) out = out.replace(chatLatexEquationEnv, "")
  if (out.contains("\\operatorname*")) out = out.replace("\\operatorname*", "\\operatorname")
  if (out.contains("\\nonumber")) out = out.replace("\\nonumber", "")
  if (out.contains("\\notag")) out = out.replace("\\notag", "")
  if (out.contains("\\middle")) out = out.replace(Regex("""\\middle(?![a-zA-Z])"""), "")
  if (out.contains("mathbin") || out.contains("mathrel") || out.contains("mathop") ||
    out.contains("mathord") || out.contains("mathopen") || out.contains("mathclose") ||
    out.contains("mathpunct") || out.contains("mathinner")
  ) {
    out = out.replace(chatLatexMathClass, "")
  }
  return out
}

/**
 * Lays a formula out on a background thread and reports the result. Returns null
 * until the layout is ready (and permanently on a parse error, where the caller
 * falls back to the raw source).
 */
@Composable
internal fun rememberChatMathLayout(
  latex: String,
  textSizePx: Float,
  textColor: Color,
): ChatMathLayout? {
  val colorArgb = textColor.toArgbInt()
  // A cached formula is available immediately, so only the first appearance of
  // each expression pays a frame of raw-source fallback.
  var layout by
    remember(latex, textSizePx, colorArgb) {
      mutableStateOf(cachedChatMathLayout(latex, textSizePx, colorArgb))
    }
  LaunchedEffect(latex, textSizePx, colorArgb) {
    if (layout == null) {
      layout = withContext(chatMathLayoutDispatcher) { layoutChatMath(latex, textSizePx, colorArgb) }
    }
  }
  return layout
}

/** Convenience for inline runs, where the size comes from the surrounding style. */
@Composable
internal fun rememberInlineChatMath(
  latex: String,
  fontSize: TextUnit,
  textColor: Color,
): ChatMathLayout? {
  val density = LocalDensity.current
  val textSizePx = with(density) { fontSize.toPx() }
  return rememberChatMathLayout(latex, textSizePx, textColor)
}

/**
 * Paints a laid-out formula straight onto the Canvas. There is no bitmap stage:
 * the TeX box is vector content drawn at the current density every frame.
 */
@Composable
internal fun ChatMathCanvas(
  layout: ChatMathLayout,
  modifier: Modifier = Modifier,
  scale: Float = 1f,
) {
  val density = LocalDensity.current
  val width = with(density) { (layout.widthPx * scale).toDp() }
  val height = with(density) { (layout.heightPx * scale).toDp() }
  Canvas(modifier = modifier.size(width, height)) {
    drawIntoCanvas { canvas ->
      val native = canvas.nativeCanvas
      if (scale != 1f) {
        native.save()
        native.scale(scale, scale)
      }
      layout.drawable.setBounds(0, 0, layout.widthPx, layout.heightPx)
      layout.drawable.draw(native)
      if (scale != 1f) native.restore()
    }
  }
}

private fun Color.toArgbInt(): Int = this.toArgb()
