package ai.openclaw.app.ui.chat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.sp
import org.json.JSONObject

private const val MATH_WIDTH_BUCKET_PX = 64

internal data class ChatMathRenderKey(
  val latex: String,
  val widthBucket: Int,
  val darkMode: Boolean,
  val displayMode: Boolean,
)

/**
 * Kept as the render-request shape the rich-block coordinator tests exercise.
 * Formulas themselves no longer go through the WebView pipeline: they are laid
 * out by the native TeX engine in [ChatMathNative] and painted straight onto a
 * Canvas, which removes the queue wait and the bitmap round-trip entirely.
 */
internal data class ChatMathRenderRequest(
  val key: ChatMathRenderKey,
  val textColor: Int,
  val fontSizePx: Float,
  override val density: Float,
) : ChatRichBlockRequest {
  override val kind get() = ChatRichBlockKind.Math
  override val source get() = key.latex
  override val widthPx get() = key.widthBucket

  override fun payload(id: String): JSONObject =
    JSONObject()
      .put("id", id)
      .put("latex", source)
      .put("widthCssPx", widthPx / density)
      .put("fontSizeCssPx", fontSizePx / density)
      .put("color", chatRenderCssColor(textColor))
      .put("displayMode", key.displayMode)

  companion object {
    fun create(
      latex: String,
      widthPx: Int,
      darkMode: Boolean,
      textColor: Int,
      fontSizePx: Float,
      density: Float,
      displayMode: Boolean = true,
    ): ChatMathRenderRequest {
      val boundedWidth = widthPx.coerceAtLeast(1)
      val widthBucket =
        ((boundedWidth / MATH_WIDTH_BUCKET_PX) * MATH_WIDTH_BUCKET_PX).coerceAtLeast(MATH_WIDTH_BUCKET_PX)
      return ChatMathRenderRequest(
        key =
          ChatMathRenderKey(
            latex = latex,
            widthBucket = widthBucket,
            darkMode = darkMode,
            displayMode = displayMode,
          ),
        textColor = textColor,
        fontSizePx = fontSizePx,
        density = density,
      )
    }
  }
}

@Composable
internal fun ChatMathBlock(
  latex: String,
  textColor: Color,
) {
  val density = LocalDensity.current
  // Display math sits a notch above the 16sp body so a block reads as its own line.
  val fontSizePx = with(density) { 18.sp.toPx() }
  val layout = rememberChatMathLayout(latex, fontSizePx, textColor)
  if (layout == null) {
    // Still parsing, or the engine cannot read the source. Either way the raw
    // formula stays on screen rather than collapsing to nothing.
    ChatMathFallback(latex)
    return
  }
  val anchor = rememberChatReaderAnchor(latex)
  val scrollState = rememberScrollState()
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .horizontalScroll(scrollState),
  ) {
    ChatMathCanvas(layout, Modifier.then(anchor?.modifier ?: Modifier))
  }
}

@Composable
internal fun ChatMathFallback(latex: String) {
  ChatCodeBlock(code = latex, language = null)
}
