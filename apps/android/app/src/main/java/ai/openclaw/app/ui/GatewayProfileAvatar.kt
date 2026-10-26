package ai.openclaw.app.ui

import ai.openclaw.app.GatewayProfile
import ai.openclaw.app.GatewayProfileAvatarImage
import ai.openclaw.app.currentAppLanguage
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.image.decodeRemoteImageBitmap
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun GatewayProfileAvatar(
  profile: GatewayProfile?,
  size: Dp,
  modifier: Modifier = Modifier,
  loadAvatar: suspend () -> GatewayProfileAvatarImage?,
) {
  val displayName = profile?.displayName?.trim().orEmpty()
  val avatarKey = profile?.let { "${it.id}:${it.hasAvatar}:${it.updatedAtMs}" }
  var bitmap by remember(avatarKey) { mutableStateOf<Bitmap?>(null) }
  LaunchedEffect(avatarKey) {
    bitmap = null
    if (profile?.hasAvatar != true) return@LaunchedEffect
    val image = loadAvatar() ?: return@LaunchedEffect
    bitmap =
      withContext(Dispatchers.Default) {
        decodeRemoteImageBitmap(
          bytes = image.bytes,
          maxDimension = 256,
          expectedContentType = image.mimeType,
        )
      }
  }
  Surface(
    modifier = modifier.size(size),
    shape = CircleShape,
    color = ClawTheme.colors.surfacePressed,
  ) {
    val resolved = bitmap
    if (resolved != null) {
      Image(
        bitmap = resolved.asImageBitmap(),
        contentDescription = displayName.takeIf(String::isNotEmpty),
        modifier = Modifier.fillMaxSize(),
        contentScale = ContentScale.Crop,
      )
    } else {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = localizedInitial(displayName, currentAppLanguage().languageTag) ?: "O",
          style =
            ClawTheme.type.title.copy(
              fontSize = (size.value * 0.36f).sp,
              lineHeight = (size.value * 0.42f).sp,
            ),
          color = ClawTheme.colors.text,
          textAlign = TextAlign.Center,
        )
      }
    }
  }
}
