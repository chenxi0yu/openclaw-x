package ai.openclaw.app.ui

import ai.openclaw.app.GatewayProfileAvatarUpload
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val PROFILE_AVATAR_EDGE_PX = 512
private const val PROFILE_AVATAR_SOURCE_EDGE_PX = 2048
private const val PROFILE_AVATAR_MAX_BYTES = 512 * 1024
private const val PROFILE_AVATAR_MAX_SOURCE_BYTES = 10 * 1024 * 1024
private const val PROFILE_AVATAR_MAX_BASE64_CHARS = 700_000

internal sealed interface ProfileAvatarPickResult {
  data class Ready(val upload: GatewayProfileAvatarUpload) : ProfileAvatarPickResult

  data object InvalidImage : ProfileAvatarPickResult

  data object TooLarge : ProfileAvatarPickResult
}

/**
 * Mirrors the Control UI's profile-avatar contract: a 512px square, at most 512 KiB,
 * encoded as WebP. ImageDecoder applies the source EXIF orientation before the crop.
 */
internal suspend fun prepareProfileAvatar(
  context: Context,
  uri: Uri,
): ProfileAvatarPickResult =
  withContext(Dispatchers.IO) {
    try {
      val sourceBytes =
        runCatching {
          context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()
      if (sourceBytes != null && sourceBytes > PROFILE_AVATAR_MAX_SOURCE_BYTES) {
        return@withContext ProfileAvatarPickResult.TooLarge
      }
      val source = ImageDecoder.createSource(context.contentResolver, uri)
      val decoded =
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
          decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
          val longestEdge = max(info.size.width, info.size.height)
          if (longestEdge > PROFILE_AVATAR_SOURCE_EDGE_PX) {
            val scale = PROFILE_AVATAR_SOURCE_EDGE_PX.toFloat() / longestEdge
            decoder.setTargetSize(
              (info.size.width * scale).roundToInt().coerceAtLeast(1),
              (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
          }
        }
      var square: Bitmap? = null
      var scaled: Bitmap? = null
      try {
        val edge = minOf(decoded.width, decoded.height)
        if (edge <= 0) return@withContext ProfileAvatarPickResult.InvalidImage
        val x = (decoded.width - edge) / 2
        val y = (decoded.height - edge) / 2
        square = Bitmap.createBitmap(decoded, x, y, edge, edge)
        scaled =
          if (edge == PROFILE_AVATAR_EDGE_PX) {
            square
          } else {
            Bitmap.createScaledBitmap(square, PROFILE_AVATAR_EDGE_PX, PROFILE_AVATAR_EDGE_PX, true)
          }
        val bytes = compressProfileAvatar(scaled) ?: return@withContext ProfileAvatarPickResult.TooLarge
        val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
        if (base64.length > PROFILE_AVATAR_MAX_BASE64_CHARS) {
          return@withContext ProfileAvatarPickResult.TooLarge
        }
        ProfileAvatarPickResult.Ready(
          GatewayProfileAvatarUpload(
            mimeType = "image/webp",
            base64 = base64,
            bytes = bytes,
          ),
        )
      } finally {
        decoded.recycle()
        if (square != null && square !== decoded) square.recycle()
        if (scaled != null && scaled !== square && scaled !== decoded) scaled.recycle()
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Throwable) {
      ProfileAvatarPickResult.InvalidImage
    }
  }

private fun compressProfileAvatar(bitmap: Bitmap): ByteArray? {
  var quality = 84
  while (quality >= 40) {
    val output = ByteArrayOutputStream()
    val encoded = bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, quality, output)
    val bytes = output.toByteArray()
    if (encoded && bytes.isNotEmpty() && bytes.size <= PROFILE_AVATAR_MAX_BYTES) {
      return bytes
    }
    quality -= 8
  }
  return null
}
