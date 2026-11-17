package ai.openclaw.app.ui.design

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.ui.image.RemoteImageResult
import ai.openclaw.app.ui.image.decodeRemoteImageBitmap
import ai.openclaw.app.ui.image.safeRemoteImageStore
import android.graphics.Bitmap
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val AGENT_AVATAR_MAX_DIMENSION = 256
private const val AGENT_AVATAR_MAX_BYTES = 2 * 1024 * 1024
private const val AGENT_AVATAR_CACHE_MAX_BYTES = 4 * 1024 * 1024
private const val AGENT_AVATAR_MAX_DATA_URL_PREFIX_CHARS = 26

// Keep Android decoding inside the Gateway's shared avatar payload boundary.
private const val AGENT_AVATAR_MAX_DATA_URL_CHARS =
  ((AGENT_AVATAR_MAX_BYTES + 2) / 3) * 4 + AGENT_AVATAR_MAX_DATA_URL_PREFIX_CHARS
private val dataImageBase64Prefix =
  Regex("^data:(image/[a-z0-9.+-]+);base64,", RegexOption.IGNORE_CASE)
private val remoteImagePrefix = Regex("^https?://", RegexOption.IGNORE_CASE)

internal sealed interface AgentAvatarSource {
  data class Data(
    val mimeType: String,
    val base64: String,
  ) : AgentAvatarSource

  data class Remote(
    val url: String,
  ) : AgentAvatarSource
}

/**
 * Process-wide decoded avatar cache. Catalog refreshes and picker recompositions reuse the
 * same payload strings, so a keyed cache is all that stands between a blink and no blink.
 */
private object AgentAvatarDecodeCache {
  private val bitmaps =
    object : LruCache<String, Bitmap>(AGENT_AVATAR_CACHE_MAX_BYTES) {
      override fun sizeOf(
        key: String,
        value: Bitmap,
      ): Int = value.allocationByteCount
    }
  private val byteArrays =
    object : LruCache<String, ByteArray>(AGENT_AVATAR_CACHE_MAX_BYTES) {
      override fun sizeOf(
        key: String,
        value: ByteArray,
      ): Int = value.size
    }

  fun key(
    mimeType: String,
    base64: String,
  ): String = "$mimeType:${base64.hashCode()}:${base64.length}"

  fun bitmap(key: String): Bitmap? = bitmaps.get(key)

  fun putBitmap(
    key: String,
    bitmap: Bitmap,
  ) {
    bitmaps.put(key, bitmap)
  }

  fun bytes(key: String): ByteArray? = byteArrays.get(key)

  fun putBytes(
    key: String,
    bytes: ByteArray,
  ) {
    byteArrays.put(key, bytes)
  }
}

/** Returns the authoritative Android-renderable agent avatar source, if present. */
internal fun agentAvatarSource(agent: GatewayAgentSummary): AgentAvatarSource? {
  val candidate =
    agent.avatarUrl?.trim()?.takeIf { it.isNotEmpty() }
      ?: agent.avatar?.trim()?.takeIf { it.isNotEmpty() }
      ?: return null
  if (remoteImagePrefix.containsMatchIn(candidate)) {
    return AgentAvatarSource.Remote(candidate)
  }
  if (candidate.length > AGENT_AVATAR_MAX_DATA_URL_CHARS) return null
  val prefix = dataImageBase64Prefix.find(candidate) ?: return null
  val base64 = candidate.substring(prefix.range.last + 1).trim().takeIf { it.isNotEmpty() } ?: return null
  return AgentAvatarSource.Data(
    mimeType = prefix.groupValues[1].lowercase(Locale.US),
    base64 = base64,
  )
}

/** Renders an agent image when loading succeeds, otherwise the caller-owned fallback. */
@Composable
internal fun ClawAgentAvatar(
  source: AgentAvatarSource?,
  size: Dp,
  shape: Shape = CircleShape,
  fallback: @Composable () -> Unit,
) {
  when (source) {
    is AgentAvatarSource.Data -> {
      if (source.mimeType == "image/svg+xml") {
        SvgAgentAvatar(base64 = source.base64, size = size, shape = shape, fallback = fallback)
      } else {
        RasterDataAgentAvatar(source = source, size = size, shape = shape, fallback = fallback)
      }
    }

    is AgentAvatarSource.Remote -> {
      RemoteAgentAvatar(source.url, size, shape, fallback)
    }

    null -> {
      fallback()
    }
  }
}

@Composable
private fun RasterDataAgentAvatar(
  source: AgentAvatarSource.Data,
  size: Dp,
  shape: Shape,
  fallback: @Composable () -> Unit,
) {
  val cacheKey = remember(source) { AgentAvatarDecodeCache.key(source.mimeType, source.base64) }
  var bitmap by remember(cacheKey) { mutableStateOf(AgentAvatarDecodeCache.bitmap(cacheKey)) }
  LaunchedEffect(cacheKey) {
    if (bitmap != null) return@LaunchedEffect
    val decoded =
      withContext(Dispatchers.Default) {
        val bytes = decodeAgentAvatarBase64(source.base64) ?: return@withContext null
        decodeRemoteImageBitmap(
          bytes = bytes,
          maxDimension = AGENT_AVATAR_MAX_DIMENSION,
          expectedContentType = source.mimeType,
        )
      }
    if (decoded != null) {
      AgentAvatarDecodeCache.putBitmap(cacheKey, decoded)
      bitmap = decoded
    }
  }
  val resolved = bitmap
  if (resolved == null) {
    fallback()
  } else {
    Image(
      bitmap = resolved.asImageBitmap(),
      contentDescription = null,
      modifier = Modifier.size(size).clip(shape),
      contentScale = ContentScale.Crop,
    )
  }
}

@Composable
private fun RemoteAgentAvatar(
  url: String,
  size: Dp,
  shape: Shape,
  fallback: @Composable () -> Unit,
) {
  var result by remember(url) { mutableStateOf(safeRemoteImageStore.peek(url)) }
  LaunchedEffect(url) {
    if (result != null) return@LaunchedEffect
    result = safeRemoteImageStore.get(url)
  }
  when (val image = result) {
    is RemoteImageResult.Raster -> {
      Image(
        bitmap = image.bitmap.asImageBitmap(),
        contentDescription = null,
        modifier = Modifier.size(size).clip(shape),
        contentScale = ContentScale.Crop,
      )
    }

    is RemoteImageResult.Svg -> {
      SvgAgentAvatar(bytes = image.bytes, size = size, shape = shape, fallback = fallback)
    }

    RemoteImageResult.Failed, null -> {
      fallback()
    }
  }
}

@Composable
private fun SvgAgentAvatar(
  base64: String,
  size: Dp,
  shape: Shape,
  fallback: @Composable () -> Unit,
) {
  val cacheKey = remember(base64) { AgentAvatarDecodeCache.key("image/svg+xml", base64) }
  var bytes by remember(cacheKey) { mutableStateOf(AgentAvatarDecodeCache.bytes(cacheKey)) }
  LaunchedEffect(cacheKey) {
    if (bytes != null) return@LaunchedEffect
    val decoded = withContext(Dispatchers.Default) { decodeAgentAvatarBase64(base64) }
    if (decoded != null) {
      AgentAvatarDecodeCache.putBytes(cacheKey, decoded)
      bytes = decoded
    }
  }
  val resolved = bytes
  if (resolved == null) {
    fallback()
  } else {
    SvgAgentAvatar(bytes = resolved, size = size, shape = shape, fallback = fallback)
  }
}

@Composable
private fun SvgAgentAvatar(
  bytes: ByteArray,
  size: Dp,
  shape: Shape,
  fallback: @Composable () -> Unit,
) {
  val context = LocalPlatformContext.current
  val request =
    remember(bytes, context) {
      ImageRequest
        .Builder(context)
        .data(bytes)
        .size(AGENT_AVATAR_MAX_DIMENSION)
        .build()
    }
  val painter = rememberAsyncImagePainter(model = request, contentScale = ContentScale.Crop)
  val painterState by painter.state.collectAsState()
  if (painterState !is AsyncImagePainter.State.Success) {
    fallback()
    return
  }
  Image(
    painter = painter,
    contentDescription = null,
    modifier = Modifier.size(size).clip(shape),
    contentScale = ContentScale.Crop,
  )
}

private fun decodeAgentAvatarBase64(base64: String): ByteArray? =
  runCatching { Base64.decode(base64, Base64.DEFAULT) }
    .getOrNull()
    ?.takeIf { it.isNotEmpty() && it.size <= AGENT_AVATAR_MAX_BYTES }
