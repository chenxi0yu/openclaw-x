package ai.openclaw.app.ui

import ai.openclaw.app.BuildConfig
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.update.AppUpdateCheck
import ai.openclaw.app.update.AppUpdateFailure
import ai.openclaw.app.update.AppUpdateRelease
import ai.openclaw.app.update.DEFAULT_APP_UPDATE_LINK_PATTERN
import ai.openclaw.app.update.DEFAULT_APP_UPDATE_REPOSITORY
import ai.openclaw.app.update.gitHubRepositorySlug
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.MiuixGroup
import ai.openclaw.app.ui.design.MiuixGroupDivider
import ai.openclaw.app.ui.design.MiuixGroupLabel
import ai.openclaw.app.ui.design.MiuixInputRow
import ai.openclaw.app.ui.design.MiuixSwitchRow
import ai.openclaw.app.ui.design.MiuixTone
import ai.openclaw.app.ui.design.MiuixValueRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 更新设置页，MIUI 风格分组行。
 *
 * 标题、取消等已有 zh-CN 翻译的通用键走 nativeString；此功能新增的行内文案
 * 直接用中文 verbatim，因为本 fork 没有 apps/.i18n 翻译产物，新键位只会回退
 * 英文，混排反而难看。网关自身的更新提示仍在“关于”页，互不干扰。
 */
@Composable
internal fun AppUpdateSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val repository by viewModel.appUpdateRepository.collectAsState()
  val autoCheck by viewModel.appUpdateAutoCheck.collectAsState()
  val linkPattern by viewModel.appUpdateLinkPattern.collectAsState()
  val updateState by viewModel.appUpdate.collectAsState()
  val pendingRelease by viewModel.pendingAppUpdate.collectAsState()

  SettingsDetailFrame(
    title = nativeString("Update"),
    subtitle = "从 GitHub 仓库的 Releases 里检查新版本。",
    icon = Icons.Default.SystemUpdate,
    onBack = onBack,
  ) {
    MiuixGroupLabel(text = "来源")
    MiuixGroup {
      MiuixInputRow(
        title = "仓库地址",
        subtitle = "发布 Android 构建的 GitHub 仓库。",
        value = repository,
        onValueChange = viewModel::setAppUpdateRepository,
        icon = Icons.Default.CloudDownload,
        tone = MiuixTone.Accent,
        placeholder = DEFAULT_APP_UPDATE_REPOSITORY,
        monospace = true,
      )
      MiuixGroupDivider()
      MiuixValueRow(
        title = "当前版本",
        value = BuildConfig.VERSION_NAME,
        subtitle = "已安装的构建。",
        icon = Icons.Default.SystemUpdate,
        tone = MiuixTone.Neutral,
      )
    }

    MiuixGroupLabel(text = "检查")
    MiuixGroup {
      MiuixSwitchRow(
        title = "自动检查更新",
        subtitle = "每次启动应用时在后台查一次新版本。",
        checked = autoCheck,
        onCheckedChange = viewModel::setAppUpdateAutoCheck,
        icon = Icons.Default.Refresh,
        tone = MiuixTone.Success,
      )
      MiuixGroupDivider()
      MiuixInputRow(
        title = "链接匹配规则",
        subtitle = "从 Release 说明里提取下载链接的正则表达式，默认匹配蓝奏云。",
        value = linkPattern,
        onValueChange = viewModel::setAppUpdateLinkPattern,
        icon = Icons.Default.Link,
        tone = MiuixTone.Warning,
        placeholder = DEFAULT_APP_UPDATE_LINK_PATTERN,
        monospace = true,
      )
    }

    MiuixGroupLabel(text = "状态")
    MiuixGroup {
      MiuixValueRow(
        title = if (updateState.checking) "正在检查更新…" else "立即检查更新",
        value = appUpdateOutcomeText(updateState.result, pendingRelease),
        icon = Icons.Default.Refresh,
        tone = MiuixTone.Accent,
        onClick = { viewModel.refreshAppUpdate(automatic = false) },
      )
    }

    if (repository.isNotBlank() && gitHubRepositorySlug(repository) == null) {
      Text(
        text = "这个仓库地址看起来不正确。",
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.warning,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
      )
    }

    Text(
      text = "选择更新时，会把 Release 说明里链接旁的密码自动复制到剪贴板。",
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textSubtle,
      modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
    )
  }
}

/** 状态行的一句话结果；版本内容仍在更新对话框里展示。 */
private fun appUpdateOutcomeText(
  result: AppUpdateCheck?,
  pendingRelease: AppUpdateRelease?,
): String =
  when {
    pendingRelease != null -> "有新版本：${pendingRelease.title}"
    result is AppUpdateCheck.Available -> "有新版本：${result.release.title}"
    result is AppUpdateCheck.UpToDate -> "已是最新版本"
    result is AppUpdateCheck.Failed -> appUpdateFailureText(result.reason)
    else -> "还没有检查过"
  }

private fun appUpdateFailureText(reason: AppUpdateFailure): String =
  when (reason) {
    AppUpdateFailure.InvalidRepository -> "仓库地址无效"
    AppUpdateFailure.Network -> "无法连接 GitHub"
    AppUpdateFailure.UnexpectedResponse -> "GitHub 返回了意外的内容"
    AppUpdateFailure.NoReleases -> "仓库还没有发布过 Release"
  }
