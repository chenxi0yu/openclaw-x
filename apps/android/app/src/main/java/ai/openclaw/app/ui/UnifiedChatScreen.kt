package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.ui.chat.ChatScreen
import ai.openclaw.app.ui.chat.rememberChatRealtimeTalkLauncher
import ai.openclaw.app.ui.design.ClawScaffold
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.window.layout.DisplayFeature

@Composable
internal fun UnifiedChatShellScreen(
  viewModel: MainViewModel,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onOpenDashboard: (String) -> Unit,
  onOpenGatewaySettings: () -> Unit,
  onOpenProvidersModels: () -> Unit,
  tabletopPanes: TabletopPaneBounds? = null,
  features: List<DisplayFeature> = emptyList(),
) {
  val talkModeEnabled by viewModel.talkModeEnabled.collectAsState()
  val startTalk = rememberChatRealtimeTalkLauncher(viewModel)
  LaunchedEffect(viewModel) { viewModel.refreshTalkSetupReadiness() }

  ClawScaffold(
    // No top inset: the chat pane owns the strip under the status bar so the
    // header's glass can run all the way to the top edge of the screen. The
    // header pads its own content back down (see ChatHeader).
    contentPadding = PaddingValues(start = 0.dp, top = 0.dp, end = 0.dp, bottom = 0.dp),
    contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
  ) {
    ChatScreen(
      viewModel = viewModel,
      talkActive = talkModeEnabled,
      showSidebarButton = showSidebarButton,
      onOpenSidebar = onOpenSidebar,
      onToggleTalk = {
        if (talkModeEnabled) {
          viewModel.setTalkModeEnabled(false)
        } else {
          startTalk()
        }
      },
      onOpenDashboard = onOpenDashboard,
      onOpenGatewaySettings = onOpenGatewaySettings,
      onOpenProvidersModels = onOpenProvidersModels,
      tabletopPanes = tabletopPanes,
      features = features,
    )
  }
}
