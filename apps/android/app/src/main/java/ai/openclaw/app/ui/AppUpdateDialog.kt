package ai.openclaw.app.ui

import ai.openclaw.app.BuildConfig
import ai.openclaw.app.update.AppUpdateRelease
import ai.openclaw.app.update.enqueueApkDownload
import ai.openclaw.app.ui.chat.ChatMarkdown
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
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.unit.dp
import android.content.Context
import android.widget.Toast

/**
 * 启动检查发现新版本时弹出的更新对话框。
 *
 * Release 带安装包附件时，“立即下载”把它交给系统下载器，下载器自己的通知负责进度和安装；
 * 没有附件时退到 Release 发布页，让用户自己在页面上取包——任何一条 Release 都不会把更新
 * 路径断在这里。
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
          // The chat renderer already covers headings, lists, emphasis, inline code and
          // fenced code; release bodies are authored in the same Markdown. SelectionContainer
          // stays so a rendered body remains copyable, which the markdown text itself is not.
          SelectionContainer {
            ChatMarkdown(text = release.notes, textColor = ClawTheme.colors.textMuted)
          }
        }

        if (downloadUrl == null) {
          // The release exists but carries no APK attachment; opening its page still gets the
          // operator to the files.
          Text(
            text = "这条 Release 没有附带安装包，打开发布页即可取包。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.warning,
          )
        }
      }
    },
    actions = {
      TextButton(onClick = onDismiss) {
        Text("取消")
      }
      TextButton(
        onClick = {
          startAppUpdateDownload(context, uriHandler, release)
          onDismiss()
        },
      ) {
        Text(if (downloadUrl == null) "打开发布页" else "立即下载")
      }
    },
  )
}

/**
 * Hands the release's own APK to the system downloader, which owns progress, retry, and the
 * completed notification that opens the installer.
 *
 * Kept out of composition so the click handler stays a boring call. A device without a
 * downloader, or a release with no attachment, ends on the release page instead of a tap that
 * does nothing.
 */
private fun startAppUpdateDownload(
  context: Context,
  uriHandler: UriHandler,
  release: AppUpdateRelease,
) {
  val downloadUrl = release.downloadUrl
  if (downloadUrl != null && enqueueApkDownload(context, downloadUrl, release.version)) {
    Toast.makeText(context, "已开始下载，完成后点通知安装", Toast.LENGTH_LONG).show()
    return
  }
  if (downloadUrl != null) {
    Toast.makeText(context, "系统下载器不可用，改从发布页取包", Toast.LENGTH_SHORT).show()
  }
  // A link the browser cannot handle must not crash the dialog; the fallback step is best-effort.
  runCatching { uriHandler.openUri(release.pageUrl) }
}
