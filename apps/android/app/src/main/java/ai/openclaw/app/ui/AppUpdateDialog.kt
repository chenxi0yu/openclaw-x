package ai.openclaw.app.ui

import ai.openclaw.app.BuildConfig
import ai.openclaw.app.update.AppUpdateRelease
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast

/**
 * 启动检查发现新版本时弹出的更新对话框。
 *
 * “马上更新”会在浏览器里打开从 Release 说明中提取到的分享链接，并把链接旁的
 * 密码写入剪贴板，随后提示“验证码已复制”，用户在浏览器里粘贴即可下载。
 * 没有可复制链接时该按钮只做关闭，避免误导性的复制提示。
 */
@Composable
internal fun AppUpdateDialog(
  release: AppUpdateRelease,
  onDismiss: () -> Unit,
) {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val downloadUrl = release.downloadUrl

  FoldAwarePrompt(
    onDismissRequest = onDismiss,
    containerColor = ClawTheme.colors.surfaceRaised,
    title = "发现新版本 ${release.title}",
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = "当前 ${BuildConfig.VERSION_NAME}",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textSubtle,
          )
          Text(
            text = "→",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textSubtle,
          )
          Text(
            text = release.tag,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.primary,
          )
        }

        if (release.notes.isNotBlank()) {
          SelectionContainer {
            Text(
              text = release.notes,
              style = ClawTheme.type.body,
              color = ClawTheme.colors.textMuted,
            )
          }
        }

        if (downloadUrl == null) {
          // Naming the fix keeps this from being a dead button; the release exists but
          // its notes carry no link this build's match rule can find.
          Text(
            text = "这条 Release 的说明里没找到下载链接。请在说明中补上分享链接，或在设置里调整链接匹配规则。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.warning,
          )
        } else {
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "下载地址", style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle)
            SelectionContainer {
              Text(
                text = downloadUrl,
                style = ClawTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
                color = ClawTheme.colors.text,
              )
            }
          }
        }

        release.downloadPassword?.let { password ->
          Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = "提取密码", style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle)
            Text(
              text = password,
              style = ClawTheme.type.body.copy(fontFamily = FontFamily.Monospace),
              color = ClawTheme.colors.text,
            )
          }
        }
      }
    },
    actions = {
      TextButton(onClick = onDismiss) {
        Text("取消")
      }
      TextButton(
        enabled = downloadUrl != null,
        onClick = {
          startAppUpdateDownload(context, uriHandler, requireNotNull(downloadUrl), release.downloadPassword)
          onDismiss()
        },
      ) {
        Text("马上更新")
      }
    },
  )
}

/**
 * Opens the share link in the browser and stages its password on the clipboard.
 *
 * Kept out of composition so the click handler stays a boring call. The toast fires
 * only when a password was actually copied; otherwise "验证码已复制" would lie.
 */
private fun startAppUpdateDownload(
  context: android.content.Context,
  uriHandler: androidx.compose.ui.platform.UriHandler,
  downloadUrl: String,
  password: String?,
) {
  // A link the browser cannot handle must not crash the dialog; the download step is best-effort.
  runCatching { uriHandler.openUri(downloadUrl) }
  if (!password.isNullOrBlank()) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("OpenClaw update password", password))
    Toast.makeText(context, "验证码已复制", Toast.LENGTH_SHORT).show()
  }
}
