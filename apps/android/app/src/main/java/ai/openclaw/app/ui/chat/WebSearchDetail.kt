package ai.openclaw.app.ui.chat

import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.image.isPubliclyRoutableHost
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Search as MiuixSearch

/** One normalized web-search hit, with the tool's untrusted-content wrappers removed. */
internal data class WebSearchResult(
  val title: String,
  val url: String,
  val snippet: String,
)

/** The query envelope plus its result rows, decoded from the `web_search` tool output. */
internal data class WebSearchInfo(
  val query: String,
  val provider: String?,
  val count: Int?,
  val tookMillis: Long?,
  val results: List<WebSearchResult>,
)

private val webSearchJson = Json {
  ignoreUnknownKeys = true
  isLenient = true
}

private val untrustedOpen = Regex("""<<<EXTERNAL_UNTRUSTED_CONTENT[^>]*>>>""")
private val untrustedClose = Regex("""<<<END_EXTERNAL_UNTRUSTED_CONTENT[^>]*>>>""")
private val sourceLine = Regex("""(?m)^\s*Source:\s*Web Search\s*$""")
private val dividerLine = Regex("""(?m)^\s*---\s*$""")

/**
 * Reads the MCP search envelope:
 * `{kind:"results", provider:"serper", query, count, tookMs, results:[{title,url,snippet}]}`.
 * Returns null for any other tool output so the sheet keeps its raw fallback.
 */
internal fun parseWebSearchInfo(
  output: String?,
  arguments: String?,
): WebSearchInfo? {
  val body = output?.trim()?.takeIf(String::isNotEmpty) ?: return null
  val root = decodeObject(body) ?: return null
  if (root.string("kind") != "results") return null
  val results =
    (root["results"] as? JsonArray)
      .orEmpty()
      .mapNotNull { element ->
        val item = element as? JsonObject ?: return@mapNotNull null
        val url = item.string("url")?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
          ?: return@mapNotNull null
        val title = oneLine(cleanUntrusted(item.string("title"))).ifBlank { url.substringAfterLast('/') }
        WebSearchResult(
          title = title,
          url = url,
          snippet = oneLine(cleanUntrusted(item.string("snippet"))),
        )
      }
  val query =
    root.string("query")?.takeIf { it.isNotEmpty() }
      ?: decodeObject(arguments.orEmpty())?.let { args ->
        args.string("query") ?: args.string("q") ?: args.string("search") ?: args.string("keywords")
      }.orEmpty()
  if (query.isEmpty() && results.isEmpty()) return null
  return WebSearchInfo(
    query = query,
    provider = root.string("provider")?.takeIf { it.isNotEmpty() },
    count = (root["count"] as? JsonPrimitive)?.intOrNull,
    tookMillis = (root["tookMs"] as? JsonPrimitive)?.longOrNull,
    results = results,
  )
}

private fun decodeObject(raw: String): JsonObject? {
  if (raw.isBlank()) return null
  val start = raw.indexOf('{')
  val end = raw.lastIndexOf('}')
  val candidate = if (start >= 0 && end > start) raw.substring(start, end + 1) else raw
  return runCatching { webSearchJson.parseToJsonElement(candidate) as? JsonObject }.getOrNull()
}

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty)

/** Keeps the payload inside the untrusted wrapper, dropping the wrapper and its source banner. */
private fun cleanUntrusted(value: String?): String =
  value.orEmpty()
    .replace(untrustedOpen, "")
    .replace(untrustedClose, "")
    .replace(sourceLine, "")
    .replace(dividerLine, "")
    .trim()

private fun oneLine(value: String): String =
  value.replace(Regex("\\s+"), " ").trim()

@Composable
internal fun WebSearchDetailContent(
  info: WebSearchInfo,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    StaggeredReveal(index = 0) {
      WebSearchQueryCard(info)
    }
    if (info.results.isEmpty()) {
      Text(
        text = "没有返回搜索结果。",
        style = ClawTheme.type.body,
        color = ClawTheme.colors.textMuted,
      )
    } else {
      Text(
        text = "搜索结果",
        style = ClawTheme.type.captionSmall,
        color = ClawTheme.colors.textSubtle,
        modifier = Modifier.padding(start = 2.dp),
      )
      info.results.forEachIndexed { index, result ->
        StaggeredReveal(index = index + 1) {
          WebSearchResultCard(result = result, provider = info.provider)
        }
      }
    }
  }
}

@Composable
private fun WebSearchQueryCard(info: WebSearchInfo) {
  val colors = ClawTheme.colors
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border.copy(alpha = 0.7f)),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
      verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = MiuixIcons.Medium.MiuixSearch,
          contentDescription = null,
          tint = colors.accent,
          modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
          text = "搜索关键词",
          style = ClawTheme.type.captionSmall,
          color = colors.textSubtle,
          modifier = Modifier.weight(1f),
        )
        if (info.provider != null) {
          ProviderCapsule(provider = info.provider)
        }
      }
      Text(
        text = info.query.ifEmpty { "（未记录关键词）" },
        style = ClawTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
        color = colors.text,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      if (info.count != null || info.tookMillis != null) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          info.count?.let { MetaCapsule("$it 条结果") }
          info.tookMillis?.let { MetaCapsule("$it ms") }
        }
      }
    }
  }
}

@Composable
private fun WebSearchResultCard(
  result: WebSearchResult,
  provider: String?,
) {
  val colors = ClawTheme.colors
  val uriHandler = LocalUriHandler.current
  Surface(
    modifier = Modifier.fillMaxWidth(),
    onClick = { runCatching { uriHandler.openUri(result.url) } },
    shape = RoundedCornerShape(16.dp),
    color = colors.surface,
    border = androidx.compose.foundation.BorderStroke(0.5.dp, colors.border.copy(alpha = 0.7f)),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(10.dp),
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      verticalAlignment = Alignment.Top,
    ) {
      SiteFavicon(url = result.url, size = 40.dp)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Text(
            text = result.title,
            style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          provider?.let { ProviderCapsule(provider = it) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = result.snippet.ifEmpty { result.url },
            style = ClawTheme.type.caption.copy(fontSize = 12.sp),
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
          )
          // The source is what the user scans for; the favicon alone is too easy
          // to miss, so the host always rides along on the snippet line.
          val host = result.url.toHttpUrlOrNull()?.host.orEmpty()
          Text(
            text = host,
            style = ClawTheme.type.captionSmall.copy(fontSize = 10.sp),
            color = colors.textSubtle,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
          )
        }
      }
    }
  }
}

@Composable
private fun ProviderCapsule(provider: String) {
  Surface(
    shape = CircleShape,
    color = ClawTheme.colors.surfacePressed,
    contentColor = ClawTheme.colors.textMuted,
  ) {
    Text(
      text = provider,
      style = ClawTheme.type.captionSmall.copy(fontSize = 10.5.sp),
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun MetaCapsule(text: String) {
  Surface(
    shape = CircleShape,
    color = ClawTheme.colors.surfacePressed,
    contentColor = ClawTheme.colors.textMuted,
  ) {
    Text(
      text = text,
      style = ClawTheme.type.captionSmall.copy(fontSize = 10.5.sp),
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
    )
  }
}

@Composable
private fun SiteFavicon(
  url: String,
  size: Dp,
) {
  val context = LocalPlatformContext.current
  val host = remember(url) { url.toHttpUrlOrNull()?.host?.removePrefix("www.").orEmpty() }
  val shape = RoundedCornerShape(10.dp)
  // One source of truth: the site's own favicon, then a colored initial block.
  val favicon = remember(url) { faviconUrl(url) }
  if (favicon != null) {
    val request = remember(favicon) { ImageRequest.Builder(context).data(favicon).build() }
    val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)
    val state by painter.state.collectAsState()
    if (state is AsyncImagePainter.State.Success) {
      Image(
        painter = painter,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(shape),
      )
      return
    }
  }
  Box(
    modifier =
      Modifier
        .size(size)
        .clip(shape)
        .background(siteFallbackColor(host)),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = host.take(2).uppercase(),
      style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
      color = Color.White,
    )
  }
}

private fun faviconUrl(url: String): String? {
  val parsed = url.toHttpUrlOrNull() ?: return null
  if (parsed.scheme != "https") return null
  if (!isPubliclyRoutableHost(parsed)) return null
  return "https://${parsed.host}/favicon.ico"
}

private val siteFallbackColors =
  listOf(
    Color(0xFF3B82F6),
    Color(0xFF10B981),
    Color(0xFFF59E0B),
    Color(0xFF8B5CF6),
    Color(0xFFEC4899),
    Color(0xFF06B6D4),
  )

private fun siteFallbackColor(host: String): Color =
  siteFallbackColors[(host.hashCode() and Int.MAX_VALUE) % siteFallbackColors.size]

@Composable
private fun StaggeredReveal(
  index: Int,
  content: @Composable () -> Unit,
) {
  var revealed by remember { mutableStateOf(false) }
  LaunchedEffect(Unit) {
    delay(120L + index * 45L)
    revealed = true
  }
  val fraction by
    animateFloatAsState(
      targetValue = if (revealed) 1f else 0f,
      animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
      label = "webSearchReveal",
    )
  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .graphicsLayer {
          alpha = fraction
          translationX = (1f - fraction) * 28.dp.toPx()
        },
  ) {
    content()
  }
}
