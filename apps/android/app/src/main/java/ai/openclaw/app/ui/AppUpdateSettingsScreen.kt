package ai.openclaw.app.ui

import ai.openclaw.app.BuildConfig
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.GitHubBrandIcon
import ai.openclaw.app.ui.design.GiteeBrandIcon
import ai.openclaw.app.ui.design.MiuixGroup
import ai.openclaw.app.ui.design.MiuixSwitchRow
import ai.openclaw.app.ui.design.MiuixValueRow
import ai.openclaw.app.ui.design.rememberClawBackdrop
import ai.openclaw.app.ui.design.rememberClawMorphMenuState
import ai.openclaw.app.ui.design.rememberClawMorphTrigger
import ai.openclaw.app.update.AppUpdateCheck
import ai.openclaw.app.update.AppUpdateFailure
import ai.openclaw.app.update.AppUpdateRelease
import ai.openclaw.app.update.AppUpdateSource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop

/**
 * HyperOS-style update settings: one large title, one switch, one picker, and a
 * manual check that reports whether the chosen mirror is reachable.
 *
 * The mirror owns its repository and the release carries the download, so the old
 * editable repository/link-rule rows are gone. The picker reuses the About page's
 * morph card menu; the selected mirror gets the trailing check there.
 */
@Composable
internal fun AppUpdateSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val source by viewModel.appUpdateSource.collectAsState()
  val autoCheck by viewModel.appUpdateAutoCheck.collectAsState()
  val updateState by viewModel.appUpdate.collectAsState()
  val pendingRelease by viewModel.pendingAppUpdate.collectAsState()

  val backdrop = rememberClawBackdrop()
  val sourceMenuState = rememberClawMorphMenuState()
  val sourceTrigger = rememberClawMorphTrigger(sourceMenuState)

  val availableRelease =
    pendingRelease ?: (updateState.result as? AppUpdateCheck.Available)?.release
  val failed = updateState.result as? AppUpdateCheck.Failed

  Box(modifier = Modifier.fillMaxSize()) {
    // The glass samples this inner layer, so the overlay MUST stay outside it: a
    // sampler inside the layer it samples recurses through the RenderNode tree
    // until the render thread's stack overflows. Same split as the About page.
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .layerBackdrop(backdrop)
          .background(ClawTheme.colors.canvas),
    ) {
      ClawScaffold(
        contentPadding = PaddingValues(start = 14.dp, top = 8.dp, end = 14.dp, bottom = 0.dp),
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(rememberScrollState())
              .padding(bottom = 24.dp),
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ClawPlainIconButton(
              icon = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "返回",
              onClick = onBack,
            )
          }
          Text(
            text = "更新设置",
            style =
              ClawTheme.type.display.copy(
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
              ),
            color = ClawTheme.colors.text,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 18.dp),
          )

          MiuixGroup {
            MiuixSwitchRow(
              title = "自动更新",
              subtitle = "启动应用时在后台检查新版本",
              checked = autoCheck,
              onCheckedChange = viewModel::setAppUpdateAutoCheck,
              reserveIconSpace = false,
            )
            MiuixValueRow(
              title = "更新来源",
              value = source.displayName,
              reserveIconSpace = false,
              trailingIcon = Icons.Default.UnfoldMore,
              trailingModifier = sourceTrigger.modifier,
              onClick = {
                sourceTrigger.open(origin = TransformOrigin(1f, 0f), panelWidth = 200.dp) {
                  AppUpdateSource.entries.forEach { option ->
                    ClawGlassMenuItem(
                      label = option.displayName,
                      icon =
                        when (option) {
                          AppUpdateSource.Gitee -> GiteeBrandIcon
                          AppUpdateSource.GitHub -> GitHubBrandIcon
                        },
                      selected = option == source,
                      onClick = { viewModel.setAppUpdateSource(option) },
                    )
                  }
                }
              },
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          MiuixGroup {
            MiuixValueRow(
              title = "手动检查更新",
              value = "检查",
              subtitle =
                manualCheckStatus(
                  checking = updateState.checking,
                  outcome = updateState.result,
                  availableRelease = availableRelease,
                  source = source,
                ),
              subtitleColor = if (failed != null) ClawTheme.colors.warning else null,
              reserveIconSpace = false,
              loading = updateState.checking,
              showChevron = false,
              onClick =
                if (updateState.checking) {
                  null
                } else {
                  { viewModel.refreshAppUpdate(automatic = false) }
                },
            )
          }

          Text(
            text = "当前版本 ${BuildConfig.VERSION_NAME}",
            style = ClawTheme.type.caption.copy(fontSize = 12.5.sp),
            color = ClawTheme.colors.textSubtle,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
          )
        }
      }
    }

    // Sibling of the sampled layer, never a child: see the note above.
    ClawMorphMenuOverlay(
      state = sourceMenuState,
      onDismissRequest = { sourceMenuState.dismiss() },
      backdrop = backdrop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

/** Manual-check row copy: says what the last attempt found, including reachability. */
private fun manualCheckStatus(
  checking: Boolean,
  outcome: AppUpdateCheck?,
  availableRelease: AppUpdateRelease?,
  source: AppUpdateSource,
): String =
  when {
    checking -> "正在连接 ${source.displayName}…"
    outcome is AppUpdateCheck.UpToDate -> "连接正常，已是最新版本"
    outcome is AppUpdateCheck.Failed -> appUpdateFailureText(outcome.reason, source)
    availableRelease != null -> "连接正常，发现新版本 ${availableRelease.tag}"
    else -> "连接 ${source.displayName}，读取最新 Release"
  }

private fun appUpdateFailureText(
  reason: AppUpdateFailure,
  source: AppUpdateSource,
): String =
  when (reason) {
    AppUpdateFailure.Network -> "无法连接 ${source.displayName}，请检查网络后重试"
    AppUpdateFailure.UnexpectedResponse -> "${source.displayName} 返回了无法识别的数据"
    AppUpdateFailure.NoReleases -> "${source.displayName} 还没有发布 Release"
  }
