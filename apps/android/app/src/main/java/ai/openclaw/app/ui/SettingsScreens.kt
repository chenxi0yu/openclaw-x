package ai.openclaw.app.ui

import ai.openclaw.app.AndroidLicenseNotice
import ai.openclaw.app.AppLanguage
import ai.openclaw.app.AppLogRange
import ai.openclaw.app.AppearanceThemeFamily
import ai.openclaw.app.AppearanceThemeMode
import ai.openclaw.app.BuildConfig
import ai.openclaw.app.captureAppLogSnapshot
import ai.openclaw.app.shareAppLogSnapshot
import ai.openclaw.app.CronEditorDraftState
import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.GatewayCronActionState
import ai.openclaw.app.GatewayCronJobDetail
import ai.openclaw.app.GatewayCronJobDetailState
import ai.openclaw.app.GatewayCronJobEdit
import ai.openclaw.app.GatewayCronJobSummary
import ai.openclaw.app.GatewayCronRunHistoryState
import ai.openclaw.app.GatewayExecApprovalNotice
import ai.openclaw.app.GatewayExecApprovalSummary
import ai.openclaw.app.GatewayProfileRefreshResult
import ai.openclaw.app.GatewayProfileState
import ai.openclaw.app.GatewayProfileWriteResult
import ai.openclaw.app.GatewaySummaryState
import ai.openclaw.app.GatewayTalkSetupReadiness
import ai.openclaw.app.GatewayTalkSetupState
import ai.openclaw.app.GatewayUsageProviderSummary
import ai.openclaw.app.LocationMode
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.NotificationPackageFilterMode
import ai.openclaw.app.R
import ai.openclaw.app.SensitiveFeatureConfig
import ai.openclaw.app.VoiceCaptureMode
import ai.openclaw.app.appLanguageRowSubtitle
import ai.openclaw.app.appearanceAccentPalette
import ai.openclaw.app.appearanceAccentPreferenceValue
import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.currentAppLanguage
import ai.openclaw.app.currentSystemLanguageTag
import ai.openclaw.app.gateway.GatewayEndpoint
import ai.openclaw.app.gateway.GatewayRegistryEntryKind
import ai.openclaw.app.gatewayExecApprovalTextForDisplay
import ai.openclaw.app.gatewayTalkSetupDescription
import ai.openclaw.app.gatewayTalkSetupStatusText
import ai.openclaw.app.hasPhotoReadPermission
import ai.openclaw.app.i18n.NativeText
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.i18n.resolveNativeText
import ai.openclaw.app.i18n.resolveNativeTextResource
import ai.openclaw.app.isReady
import ai.openclaw.app.loadAndroidLicenseNotices
import ai.openclaw.app.locationModeAfterBackgroundSettings
import ai.openclaw.app.node.DeviceNotificationListenerService
import ai.openclaw.app.photoReadPermissionsForRequest
import ai.openclaw.app.reconcileRestoredAction
import ai.openclaw.app.setAppLanguage
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawGlassTopBarButton
import ai.openclaw.app.ui.design.ClawIconBadge
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawListItem
import ai.openclaw.app.ui.design.ClawListPanel
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPlainIconButton
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawSegmentedControl
import ai.openclaw.app.ui.design.ClawSeparatedColumn
import ai.openclaw.app.ui.design.ClawStatus
import ai.openclaw.app.ui.design.ClawStatusPill
import ai.openclaw.app.ui.design.ClawTextBadge
import ai.openclaw.app.ui.design.ClawTextField
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.ClawTopBarButtonTopInset
import ai.openclaw.app.ui.design.ClawTopBarContentHeight
import ai.openclaw.app.ui.design.MAX_APP_TEXT_SCALE
import ai.openclaw.app.ui.design.MIN_APP_TEXT_SCALE
import ai.openclaw.app.ui.design.MiuixGroup
import ai.openclaw.app.ui.design.MiuixGroupDivider
import ai.openclaw.app.ui.design.MiuixGroupLabel
import ai.openclaw.app.ui.design.MiuixInputRow
import ai.openclaw.app.ui.design.MiuixTone
import ai.openclaw.app.ui.design.MiuixValueRow
import ai.openclaw.app.ui.design.OpenClawMascot
import ai.openclaw.app.ui.design.ProvideAppTextScale
import ai.openclaw.app.ui.design.TalkWaveform
import ai.openclaw.app.ui.design.TalkWaveformPhase
import ai.openclaw.app.ui.design.agentAvatarSource
import ai.openclaw.app.ui.design.appTextScaleSteps
import ai.openclaw.app.ui.design.clawBackdropTopBar
import ai.openclaw.app.ui.design.rememberClawBackdrop
import ai.openclaw.app.ui.design.rememberClawMorphMenuState
import ai.openclaw.app.ui.design.rememberClawMorphTrigger
import ai.openclaw.app.uppercaseFirstGraphemeOrNull
import ai.openclaw.app.voice.AudioInputDeviceOption
import ai.openclaw.app.voice.VoiceWakePreferences
import ai.openclaw.app.voice.audioInputDeviceOptionFromKey
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.icon.extended.Edit as MiuixEdit
import top.yukonga.miuix.kmp.icon.extended.Email as MiuixEmail
import top.yukonga.miuix.kmp.icon.extended.Ok as MiuixOk
import top.yukonga.miuix.kmp.icon.extended.Photos as MiuixPhotos
import top.yukonga.miuix.kmp.icon.extended.Refresh as MiuixRefresh

/**
 * Dispatches a selected settings route to its detail screen without changing navigation ownership.
 */
@Composable
internal fun SettingsDetailScreen(
  viewModel: MainViewModel,
  route: SettingsRoute,
  onBack: () -> Unit,
) {
  when (route) {
    SettingsRoute.Home -> Unit
    SettingsRoute.Profile -> ProfileSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Voice -> VoiceSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Agents -> AgentsSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.ProvidersModels -> ProvidersModelsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Approvals -> ApprovalsSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.CronJobs -> CronJobsSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Usage -> UsageSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Share -> ShareSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Skills -> SkillsSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.SkillWorkshop -> SkillWorkshopSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.SystemAgent -> SystemAgentSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.NodesDevices -> NodesDevicesSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Channels -> ChannelsSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Dreaming -> DreamingSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Terminal -> TerminalSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Desktop -> DesktopScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Notifications -> NotificationSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.PhoneCapabilities -> PhoneCapabilitiesScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Gateway -> GatewaySettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Appearance -> AppearanceSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.AppUpdate -> AppUpdateSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Health -> HealthLogsSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.About -> AboutSettingsScreen(viewModel = viewModel, onBack = onBack)
    SettingsRoute.Licenses -> LicensesSettingsScreen(onBack = onBack)
  }
}

@Composable
private fun UsageSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val usageState by viewModel.usageState.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val usageConverging = usageRefreshVisible(usageState.refreshing, usageState.summary?.refreshing == true)

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshUsage()
    }
  }

  SettingsDetailFrame(title = nativeString("Usage"), subtitle = nativeString("Provider limits and quota health."), icon = Icons.Default.Storage, onBack = onBack) {
    SettingsRefreshControls(isConnected, usageConverging, usageState.errorText, viewModel::refreshUsage)
    SettingsSummaryContent(usageState, isConnected, nativeString("Connect the gateway to load usage.")) { usageSummary ->
      SettingsMetricPanel(
        rows =
          listOf(
            SettingsMetric(nativeString("Providers"), usageSummary.providers.size.toString()),
            SettingsMetric(nativeString("Issues"), usageSummary.providers.count { it.error != null }.toString()),
            SettingsMetric(nativeString("Updated"), formatUsageUpdated(usageSummary.updatedAtMs)),
          ),
      )
      if (usageSummary.providers.isNotEmpty()) {
        UsageProvidersPanel(providers = usageSummary.providers)
      } else if (usageState.errorText == null) {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = if (usageConverging) nativeString("Refreshing") else nativeString("No usage data yet."), style = ClawTheme.type.section, color = ClawTheme.colors.text)
            Text(text = nativeString("Provider limits will appear here when your gateway reports them."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
          }
        }
      }
    }
  }
}

@Composable
private fun CronJobsSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val cronStatus by viewModel.cronStatus.collectAsState()
  val cronJobs by viewModel.cronJobs.collectAsState()
  val cronRefreshing by viewModel.cronRefreshing.collectAsState()
  val cronErrorText by viewModel.cronErrorText.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  var selectedJobId by rememberSaveable { mutableStateOf<String?>(null) }
  var query by rememberSaveable { mutableStateOf("") }
  var filterName by rememberSaveable { mutableStateOf(CronJobsListFilter.All.name) }
  val filter = CronJobsListFilter.valueOf(filterName)
  val visibleJobs = filterCronJobs(cronJobs, query, filter)
  selectedJobId?.let { jobId ->
    CronJobDetailSettingsScreen(
      viewModel = viewModel,
      jobId = jobId,
      jobName = cronJobs.firstOrNull { it.id == jobId }?.name,
      onBack = { selectedJobId = null },
    )
    return
  }

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshCronJobs()
    }
  }

  SettingsDetailFrame(
    title = nativeString("Automations"),
    subtitle = nativeString("Scheduled OpenClaw work from your gateway."),
    icon = Icons.Default.Bolt,
    onBack = onBack,
    trailingAction = {
      ClawPlainIconButton(
        icon = Icons.Default.Refresh,
        contentDescription = if (cronRefreshing) nativeString("Refreshing") else nativeString("Refresh"),
        onClick = viewModel::refreshCronJobs,
        enabled = isConnected && !cronRefreshing,
      )
    },
  ) {
    CronSummaryStrip(
      rows =
        listOf(
          SettingsMetric(nativeString("Status"), if (cronStatus.enabled) nativeString("Enabled") else nativeString("Off")),
          SettingsMetric(nativeString("Automations"), cronStatus.jobs.toString()),
          SettingsMetric(nativeString("Next Wake"), formatCronWake(cronStatus.nextWakeAtMs)),
        ),
    )
    ClawTextField(
      value = query,
      onValueChange = { query = it },
      placeholder = nativeString("Search automations"),
      label = nativeString("Search"),
      enabled = isConnected,
    )
    val filterOptions = CronJobsListFilter.entries.map(CronJobsListFilter::label)
    ClawSegmentedControl(
      options = filterOptions,
      selected = filter.label,
      onSelect = { selected ->
        CronJobsListFilter.entries.firstOrNull { it.label == selected }?.let {
          filterName = it.name
        }
      },
      modifier = Modifier.fillMaxWidth(),
      enabledOptions = if (isConnected) filterOptions.toSet() else emptySet(),
    )
    Text(
      text = nativeString("Open an automation to inspect its configuration and run history. Admin-scoped connections can also run, edit, enable, disable, or delete it."),
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textMuted,
    )
    cronErrorText?.let { errorText ->
      ClawPanel {
        Text(text = errorText, style = ClawTheme.type.body, color = ClawTheme.colors.warning)
      }
    }
    when {
      !isConnected -> {
        ClawPanel {
          Text(text = nativeString("Connect the gateway to load automations."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }

      cronJobs.isEmpty() -> {
        ClawPanel {
          Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = nativeString("No automations yet."), style = ClawTheme.type.section, color = ClawTheme.colors.text)
            Text(text = nativeString("Scheduled work created on the gateway will appear here."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
          }
        }
      }

      visibleJobs.isEmpty() -> {
        ClawPanel {
          Text(text = nativeString("No matching automations."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }

      else -> {
        CronJobsPanel(jobs = visibleJobs, onJobClick = { selectedJobId = it.id })
      }
    }
  }
}

@Composable
private fun CronSummaryStrip(rows: List<SettingsMetric>) {
  ClawPanel {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs)) {
      rows.forEach { row ->
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(text = row.title, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 1)
          Text(text = row.value, style = ClawTheme.type.label, color = ClawTheme.colors.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
      }
    }
  }
}

@Composable
private fun CronJobDetailSettingsScreen(
  viewModel: MainViewModel,
  jobId: String,
  jobName: String?,
  onBack: () -> Unit,
) {
  fun leaveDetail() {
    viewModel.cronEditorDraftMemory.clear(jobId)
    viewModel.dismissCronActionNotice(jobId)
    onBack()
  }
  BackHandler(onBack = ::leaveDetail)

  val detailState by viewModel.cronJobDetailState.collectAsState()
  val historyState by viewModel.cronRunHistoryState.collectAsState()
  val actionState by viewModel.cronActionState.collectAsState()
  val pendingCronRunJobIds by viewModel.pendingCronRunJobIds.collectAsState()
  val operatorAdminScopeAvailable by viewModel.operatorAdminScopeAvailable.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val activity = LocalActivity.current

  DisposableEffect(activity, viewModel, jobId) {
    onDispose {
      viewModel.clearCronJobDetail()
      if (cronDetailDisposalClearsTransientState(activity?.isChangingConfigurations == true)) {
        viewModel.cronEditorDraftMemory.clear(jobId)
        viewModel.dismissCronActionNotice(jobId)
      }
    }
  }

  LaunchedEffect(isConnected, jobId) {
    if (isConnected) {
      viewModel.loadCronJobDetail(jobId)
    }
  }

  val current = (detailState as? GatewayCronJobDetailState.Loaded)?.job?.takeIf { it.id == jobId }
  var editorDraft by remember(viewModel, jobId) {
    mutableStateOf(viewModel.cronEditorDraftMemory.get(jobId))
  }
  var restoredDraftNeedsActionCheck by remember(viewModel, jobId) {
    mutableStateOf(editorDraft?.savePending == true)
  }

  fun updateEditorDraft(value: CronEditorDraftState?) {
    editorDraft = value
    viewModel.cronEditorDraftMemory.set(jobId, value)
  }
  LaunchedEffect(isConnected, actionState, restoredDraftNeedsActionCheck) {
    if (restoredDraftNeedsActionCheck) {
      updateEditorDraft(
        editorDraft?.reconcileRestoredAction(
          isConnected = isConnected,
          jobId = jobId,
          actionState = actionState,
        ),
      )
      restoredDraftNeedsActionCheck = false
    }
  }
  LaunchedEffect(isConnected) {
    if (!isConnected) updateEditorDraft(editorDraft?.saveAborted())
  }
  LaunchedEffect(current) {
    current?.let { job ->
      updateEditorDraft(editorDraft?.observeJob(job) ?: CronEditorDraftState.from(job))
    }
  }
  LaunchedEffect(actionState, current) {
    val notice = actionState as? GatewayCronActionState.Notice
    if (notice?.id == jobId) {
      val observed = editorDraft?.observeSaveNotice(notice.kind)
      updateEditorDraft(
        current?.let { job ->
          observed?.observeJob(job) ?: CronEditorDraftState.from(job)
        } ?: observed,
      )
    }
  }
  val loading = (detailState as? GatewayCronJobDetailState.Loading)?.id == jobId
  val errorText = (detailState as? GatewayCronJobDetailState.Error)?.takeIf { it.id == jobId }?.message
  val deleted =
    (actionState as? GatewayCronActionState.Notice)
      ?.takeIf { it.id == jobId }
      ?.deleted == true

  LaunchedEffect(deleted) {
    if (deleted) leaveDetail()
  }
  SettingsDetailFrame(
    title = current?.name ?: jobName ?: nativeString("Automation"),
    subtitle = nativeString("Inspect and manage scheduled gateway work."),
    icon = Icons.Default.Bolt,
    onBack = ::leaveDetail,
  ) {
    ClawSecondaryButton(
      text = if (loading) nativeString("Refreshing") else nativeString("Refresh"),
      onClick = { viewModel.loadCronJobDetail(jobId) },
      enabled =
        cronDetailRefreshEnabled(
          isConnected = isConnected,
          loading = loading,
          hasCurrentJob = current != null,
          draftRequiresResolution = editorDraft?.requiresResolution == true,
          saveSucceeded = editorDraft?.saveSucceeded == true,
        ),
      modifier = Modifier.fillMaxWidth(),
    )

    when {
      !isConnected -> {
        ClawPanel {
          Text(text = nativeString("Connect the gateway to inspect automations."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }

      errorText != null -> {
        ClawPanel {
          Text(text = errorText.resolveNativeTextResource(), style = ClawTheme.type.body, color = ClawTheme.colors.warning)
        }
      }

      current == null -> {
        ClawPanel {
          Text(text = if (loading) nativeString("Loading automation…") else nativeString("Automation not loaded."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }

      else -> {
        CronJobDetailPanel(
          job = current,
          editorDraft = editorDraft ?: CronEditorDraftState.from(current),
          onEditorDraftChange = ::updateEditorDraft,
          historyState = historyState,
          actionState = actionState,
          runPending = jobId in pendingCronRunJobIds,
          operatorAdminScopeAvailable = operatorAdminScopeAvailable,
          onRun = { viewModel.runCronJob(current.id) },
          onToggleEnabled = {
            viewModel.setCronJobEnabled(id = current.id, enabled = !current.enabled)
          },
          onSave = { edit -> viewModel.updateCronJob(original = current, edit = edit) },
          onRefreshHistory = { viewModel.refreshCronRunHistory(current.id) },
          onDelete = { viewModel.deleteCronJob(current.id) },
        )
      }
    }
  }
}

internal fun cronDetailRefreshEnabled(
  isConnected: Boolean,
  loading: Boolean,
  hasCurrentJob: Boolean,
  draftRequiresResolution: Boolean,
  saveSucceeded: Boolean,
): Boolean =
  isConnected &&
    !loading &&
    (!hasCurrentJob || !draftRequiresResolution || saveSucceeded)

internal fun cronDetailDisposalClearsTransientState(isChangingConfigurations: Boolean): Boolean = !isChangingConfigurations

@Composable
private fun AgentsSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val agents by viewModel.gatewayAgents.collectAsState()
  val defaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshAgents()
    }
  }

  SettingsDetailFrame(title = nativeString("Agents"), subtitle = nativeString("Choose and inspect the assistants available on this gateway."), icon = Icons.Default.Person, onBack = onBack) {
    SettingsMetricPanel(
      rows =
        listOf(
          SettingsMetric(nativeString("Available"), agents.size.toString()),
          SettingsMetric(nativeString("Default"), defaultAgentName(agents, defaultAgentId)),
        ),
    )
    when {
      !isConnected -> {
        ClawPanel {
          Text(text = nativeString("Connect the gateway to load agents."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }

      agents.isEmpty() -> {
        ClawPanel {
          Text(text = nativeString("No agents loaded yet."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }

      else -> {
        AgentsPanel(agents = agents, defaultAgentId = defaultAgentId)
      }
    }
  }
}

@Composable
private fun ApprovalsSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val isConnected by viewModel.isConnected.collectAsState()
  val inbox by viewModel.execApprovalInbox.collectAsState()
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val pendingRunCount by viewModel.pendingRunCount.collectAsState()
  val issueCount = inbox.approvals.count { it.errorText != null } + pendingToolCalls.count { it.isError == true }

  LaunchedEffect(isConnected) {
    if (isConnected) {
      viewModel.refreshExecApprovals()
    }
  }

  SettingsDetailFrame(title = nativeString("Approvals"), subtitle = nativeString("Review actions that need your attention."), icon = Icons.Default.Lock, onBack = onBack) {
    SettingsMetricPanel(
      rows =
        listOf(
          SettingsMetric(nativeString("Gateway Pending"), inbox.approvals.size.toString()),
          SettingsMetric(nativeString("Thread Activity"), pendingToolCalls.size.toString()),
          SettingsMetric(nativeString("Issues"), issueCount.toString()),
          SettingsMetric(nativeString("Active Runs"), pendingRunCount.toString()),
        ),
    )
    ClawSecondaryButton(
      text = if (inbox.refreshing) nativeString("Refreshing") else nativeString("Refresh"),
      onClick = viewModel::refreshExecApprovals,
      enabled = isConnected && !inbox.refreshing,
      modifier = Modifier.fillMaxWidth(),
    )
    inbox.errorText?.let { errorText ->
      ClawPanel {
        Text(text = gatewayExecApprovalTextForDisplay(errorText), style = ClawTheme.type.body, color = ClawTheme.colors.warning)
      }
    }
    // The inbox publishes terminal notices with their cards retired in the same snapshot.
    // Keep the banner independent of remaining cards until the user dismisses it.
    inbox.notice?.let { notice ->
      ExecApprovalNotice(notice = notice, onDismiss = { viewModel.dismissExecApprovalsNotice(notice) })
    }
    if (!isConnected) {
      ClawPanel {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(text = nativeString("Gateway disconnected."), style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(text = nativeString("Connect the gateway to load approval requests in the app."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }
    } else if (inbox.approvals.isEmpty()) {
      ClawPanel {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(text = nativeString("No gateway approvals."), style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(text = nativeString("Exec approval requests will appear here while this phone is connected."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      }
    } else {
      ExecApprovalsPanel(
        approvals = inbox.approvals,
        onResolve = viewModel::resolveExecApproval,
      )
    }
    if (pendingToolCalls.isNotEmpty()) {
      Text(text = nativeString("Thread activity"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
      Text(text = nativeString("Chat tool calls waiting in the active thread remain visible here."), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      SessionToolCallsPanel(toolCalls = pendingToolCalls)
    }
  }
}

private fun profileRefreshErrorText(result: GatewayProfileRefreshResult): String =
  when (result) {
    GatewayProfileRefreshResult.Success -> ""
    GatewayProfileRefreshResult.Disconnected -> "网关未连接，请检查连接"
    GatewayProfileRefreshResult.Unsupported -> "当前网关版本不支持个人资料"
    GatewayProfileRefreshResult.Forbidden -> "当前连接没有查看个人资料的权限"
    is GatewayProfileRefreshResult.Failed -> result.message ?: "资料刷新失败，请稍后重试"
  }

private fun profileWriteErrorText(result: GatewayProfileWriteResult): String =
  when (result) {
    GatewayProfileWriteResult.Success -> ""
    GatewayProfileWriteResult.Disconnected -> "网关未连接，请稍后重试"
    GatewayProfileWriteResult.Unsupported -> "当前网关版本不支持修改资料"
    GatewayProfileWriteResult.Forbidden -> "当前连接没有修改资料的权限"
    is GatewayProfileWriteResult.Failed -> result.message ?: "保存失败，请稍后重试"
  }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val profileState by viewModel.gatewayProfile.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val adminAvailable by viewModel.operatorAdminScopeAvailable.collectAsState()
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val profile = (profileState as? GatewayProfileState.Ready)?.profile
  var refreshing by remember { mutableStateOf(false) }
  var uploadingAvatar by remember { mutableStateOf(false) }
  var notice by remember { mutableStateOf<String?>(null) }
  var errorText by remember { mutableStateOf<String?>(null) }
  var showNameSheet by remember { mutableStateOf(false) }
  var nameSaving by remember { mutableStateOf(false) }
  var nameError by remember { mutableStateOf<String?>(null) }
  val canEdit = profile?.canEdit == true || adminAvailable
  val editingEnabled =
    profile != null &&
      isConnected &&
      canEdit &&
      !refreshing &&
      !uploadingAvatar

  LaunchedEffect(profile?.id) {
    notice = null
    errorText = null
    viewModel.ensureGatewayProfileLoaded()
  }
  LaunchedEffect(isConnected) {
    if (isConnected) viewModel.ensureGatewayProfileLoaded()
  }

  fun refresh() {
    if (refreshing || uploadingAvatar) return
    scope.launch {
      refreshing = true
      notice = null
      errorText = null
      val result = viewModel.refreshGatewayProfile()
      if (result == GatewayProfileRefreshResult.Success) {
        notice = "资料已刷新"
      } else {
        errorText = profileRefreshErrorText(result)
      }
      refreshing = false
    }
  }

  fun submitName(value: String?) {
    if (nameSaving) return
    scope.launch {
      nameSaving = true
      nameError = null
      when (val result = viewModel.setGatewayProfileDisplayName(value)) {
        GatewayProfileWriteResult.Success -> {
          showNameSheet = false
          notice = "名字已保存"
        }

        else -> nameError = profileWriteErrorText(result)
      }
      nameSaving = false
    }
  }

  val photoPicker =
    rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      scope.launch {
        uploadingAvatar = true
        notice = null
        errorText = null
        when (val picked = prepareProfileAvatar(context, uri)) {
          is ProfileAvatarPickResult.Ready -> {
            when (val result = viewModel.setGatewayProfileAvatar(picked.upload)) {
              GatewayProfileWriteResult.Success -> notice = "头像已更新"
              else -> errorText = profileWriteErrorText(result)
            }
          }

          ProfileAvatarPickResult.InvalidImage -> {
            errorText = "无法读取这张照片，请换一张"
          }

          ProfileAvatarPickResult.TooLarge -> {
            errorText = "照片太大，请选择较小的图片"
          }
        }
        uploadingAvatar = false
      }
    }

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
        Spacer(modifier = Modifier.weight(1f))
        if (refreshing) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            color = ClawTheme.colors.textMuted,
          )
        } else {
          ClawPlainIconButton(
            icon = MiuixIcons.Medium.MiuixRefresh,
            contentDescription = "刷新资料",
            enabled = !uploadingAvatar,
            onClick = { refresh() },
          )
        }
      }
      Text(
        text = "我的信息",
        style =
          ClawTheme.type.display.copy(
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        color = ClawTheme.colors.text,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 18.dp),
      )

      MiuixGroupLabel("基础资料")
      MiuixGroup {
        if (profile == null) {
          when (val state = profileState) {
            GatewayProfileState.Loading -> {
              Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(18.dp),
                  strokeWidth = 2.dp,
                  color = ClawTheme.colors.textMuted,
                )
                Text("正在加载网关资料…", style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
              }
            }

            GatewayProfileState.Disconnected -> {
              ProfileUnavailableContent(
                title = "网关未连接",
                body = "连接网关后可以查看和修改个人资料。",
                onRetry = { refresh() },
              )
            }

            GatewayProfileState.Unsupported -> {
              ProfileUnavailableContent(
                title = "当前网关版本不支持个人资料",
                body = "升级网关后再试。",
                onRetry = null,
              )
            }

            is GatewayProfileState.Failed -> {
              ProfileUnavailableContent(
                title = "加载失败",
                body = state.message ?: "请稍后重试。",
                onRetry = { refresh() },
              )
            }

            is GatewayProfileState.Ready -> Unit
          }
        } else {
          AccountSettingRow(
            title = "头像",
            onClick =
              if (editingEnabled) {
                {
                  photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                  )
                }
              } else {
                null
              },
            showChevron = editingEnabled,
            trailing = {
              GatewayProfileAvatar(
                profile = profile,
                size = 40.dp,
                loadAvatar = { viewModel.loadGatewayProfileAvatar() },
              )
              Spacer(modifier = Modifier.width(8.dp))
            },
          )
          AccountSettingRow(
            title = "昵称",
            value = profile.displayName?.takeIf(String::isNotBlank) ?: "未设置",
            onClick =
              if (editingEnabled) {
                {
                  nameError = null
                  showNameSheet = true
                }
              } else {
                null
              },
            showChevron = editingEnabled,
          )
          AccountSettingRow(
            title = "关联邮箱",
            value = profile.emails.joinToString(", ").ifEmpty { "未关联" },
            showChevron = false,
          )
        }
      }

      if (profile != null) {
        if (!isConnected) {
          Text(
            text = "当前显示的是上次缓存的资料。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
            modifier = Modifier.padding(start = 6.dp, top = 10.dp),
          )
        } else if (!canEdit) {
          Text(
            text = "当前连接没有修改资料的权限。",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.warning,
            modifier = Modifier.padding(start = 6.dp, top = 10.dp),
          )
        }
        notice?.let { message ->
          Text(
            text = message,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.success,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
          )
        }
        errorText?.let { message ->
          Text(
            text = message,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.danger,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
          )
        }
      }
    }
  }

  if (showNameSheet && profile != null) {
    ProfileNameSheet(
      initialName = profile.displayName.orEmpty(),
      saving = nameSaving,
      errorText = nameError,
      onDismiss = {
        if (!nameSaving) {
          showNameSheet = false
          nameError = null
        }
      },
      onSave = { value -> submitName(value) },
    )
  }
}

@Composable
private fun AccountSettingRow(
  title: String,
  modifier: Modifier = Modifier,
  value: String? = null,
  onClick: (() -> Unit)? = null,
  showChevron: Boolean = onClick != null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 58.dp)
        .then(
          if (onClick != null) {
            Modifier.clickable(role = Role.Button, onClick = onClick)
          } else {
            Modifier
          },
        ).padding(horizontal = 20.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      text = title,
      style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      color = ClawTheme.colors.text,
      modifier = Modifier.weight(1f),
    )
    trailing?.invoke()
    value?.let {
      Text(
        text = it,
        style = ClawTheme.type.body.copy(fontSize = 15.sp),
        color = ClawTheme.colors.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 8.dp),
      )
    }
    if (showChevron) {
      Spacer(modifier = Modifier.width(4.dp))
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = ClawTheme.colors.textSubtle,
      )
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileNameSheet(
  initialName: String,
  saving: Boolean,
  errorText: String?,
  onDismiss: () -> Unit,
  onSave: (String?) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  var draft by remember(initialName) { mutableStateOf(initialName) }
  ModalBottomSheet(
    onDismissRequest = { if (!saving) onDismiss() },
    sheetState = sheetState,
    containerColor = ClawTheme.colors.surface,
    dragHandle = null,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .padding(top = 24.dp, bottom = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(
        text = "设置名字",
        style = ClawTheme.type.section.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        color = ClawTheme.colors.text,
        modifier = Modifier.padding(bottom = 18.dp),
      )
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ClawTheme.colors.surfacePressed)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (draft.isEmpty()) {
          Text(
            text = "输入名字",
            style = ClawTheme.type.body.copy(fontSize = 17.sp),
            color = ClawTheme.colors.textSubtle,
          )
        }
        BasicTextField(
          value = draft,
          onValueChange = { value -> if (value.length <= 256) draft = value },
          singleLine = true,
          textStyle = ClawTheme.type.body.copy(fontSize = 17.sp, color = ClawTheme.colors.text),
          cursorBrush = SolidColor(ClawTheme.colors.accent),
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
          keyboardActions = KeyboardActions(onDone = { if (!saving) onSave(draft.trim().takeIf(String::isNotEmpty)) }),
          modifier = Modifier.fillMaxWidth(),
        )
      }
      errorText?.let { message ->
        Text(
          text = message,
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.danger,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        ProfileSheetButton(
          text = "取消",
          onClick = onDismiss,
          containerColor = ClawTheme.colors.surfacePressed,
          contentColor = ClawTheme.colors.text,
          enabled = !saving,
          modifier = Modifier.weight(1f),
        )
        ProfileSheetButton(
          text = if (saving) "保存中…" else "确定",
          onClick = { onSave(draft.trim().takeIf(String::isNotEmpty)) },
          containerColor = ClawTheme.colors.primary,
          contentColor = ClawTheme.colors.primaryText,
          enabled = !saving,
          modifier = Modifier.weight(1f),
        )
      }
    }
  }
}

@Composable
private fun ProfileSheetButton(
  text: String,
  onClick: () -> Unit,
  containerColor: Color,
  contentColor: Color,
  enabled: Boolean,
  modifier: Modifier = Modifier,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = modifier.height(52.dp),
    shape = RoundedCornerShape(16.dp),
    color = if (enabled) containerColor else containerColor.copy(alpha = 0.5f),
    contentColor = contentColor,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Text(
        text = text,
        style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      )
    }
  }
}

@Composable
private fun ProfileUnavailableContent(
  title: String,
  body: String,
  onRetry: (() -> Unit)?,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(text = title, style = ClawTheme.type.section, color = ClawTheme.colors.text)
    Text(text = body, style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
  }
  onRetry?.let { retry ->
    MiuixGroupDivider()
    AccountSettingRow(title = "重试", onClick = retry)
  }
}

@Composable
private fun VoiceSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val speakerEnabled by viewModel.speakerEnabled.collectAsState()
  val preferredAudioInputDevice by viewModel.preferredAudioInputDevice.collectAsState()
  val voiceCaptureMode by viewModel.voiceCaptureMode.collectAsState()
  val activeAudioInputDevicePreference by viewModel.activeAudioInputDevicePreference.collectAsState()
  val isConnected by viewModel.isConnected.collectAsState()
  val talkSetupReadiness by viewModel.talkSetupReadiness.collectAsState()
  val voiceWakeEnabled by viewModel.voiceWakeEnabled.collectAsState()
  val voiceWakeAvailable by viewModel.voiceWakeAvailable.collectAsState()
  val voiceWakeIsListening by viewModel.voiceWakeIsListening.collectAsState()
  val voiceWakeStatusText by viewModel.voiceWakeStatusText.collectAsState()
  val voiceWakeWords by viewModel.voiceWakeWords.collectAsState()
  val voiceWakeLastCommand by viewModel.voiceWakeLastTriggeredCommand.collectAsState()
  val voiceWakeWordsSaving by viewModel.voiceWakeWordsSaving.collectAsState()
  val voiceWakeWordsNoticeText by viewModel.voiceWakeWordsNoticeText.collectAsState()
  var wakeWordDrafts by remember(voiceWakeWords) {
    mutableStateOf(voiceWakeWords)
  }
  var audioInputDevices by remember { mutableStateOf<List<AudioInputDeviceOption>>(emptyList()) }
  val audioInputDevicePending =
    voiceCaptureMode != VoiceCaptureMode.Off && preferredAudioInputDevice != activeAudioInputDevicePreference

  val microphonePermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.refreshVoiceWakePermission()
      if (granted) viewModel.setVoiceWakeEnabled(true)
    }

  fun setVoiceWake(checked: Boolean) {
    if (!checked) {
      viewModel.setVoiceWakeEnabled(false)
    } else if (hasPermission(context, Manifest.permission.RECORD_AUDIO)) {
      viewModel.refreshVoiceWakePermission()
      viewModel.setVoiceWakeEnabled(true)
    } else {
      microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
  }

  LaunchedEffect(isConnected) {
    if (isConnected) viewModel.refreshTalkSetupReadiness()
  }

  DisposableEffect(viewModel) {
    val observer = viewModel.observeAudioInputDevices { devices -> audioInputDevices = devices }
    onDispose { observer.close() }
  }

  SettingsDetailFrame(title = nativeString("Voice"), subtitle = nativeString("Configure wake words, talk, and playback."), icon = Icons.Default.Mic, onBack = onBack) {
    Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
      Text(text = nativeString("Voice Wake"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
      SettingsTogglePanel(
        rows =
          listOf(
            SettingsToggleRow(
              title = nativeString("Listen for wake words"),
              subtitle =
                if (voiceWakeAvailable) {
                  nativeString("Runs on-device while OpenClaw is visible.")
                } else {
                  nativeString("On-device speech recognition is unavailable.")
                },
              icon = Icons.Default.Mic,
              checked = voiceWakeEnabled,
              onCheckedChange = ::setVoiceWake,
              enabled = voiceWakeAvailable || voiceWakeEnabled,
            ),
          ),
      )
      VoiceSetupActionRow(
        title = nativeString("Wake listener"),
        subtitle =
          voiceWakeLastCommand?.let { command -> nativeString("Last command: \$command", command) }
            ?: nativeString("Pauses during other voice activity."),
        icon = Icons.Default.GraphicEq,
        statusText = voiceWakeStatusText,
        ready = voiceWakeIsListening,
      )
      ClawPanel {
        Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
          Text(text = nativeString("Wake words"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(
            text = nativeString("Add one wake word or phrase per field. Then say one before your command."),
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
          )
          wakeWordDrafts.forEachIndexed { index, value ->
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              ClawTextField(
                value = value,
                onValueChange = { updated ->
                  wakeWordDrafts = wakeWordDrafts.toMutableList().also { it[index] = updated }
                },
                placeholder = nativeString("Wake word or phrase"),
                enabled = voiceWakeAvailable && !voiceWakeWordsSaving,
                modifier = Modifier.weight(1f),
              )
              if (voiceWakeAvailable && !voiceWakeWordsSaving && wakeWordDrafts.size > 1) {
                ClawPlainIconButton(
                  icon = Icons.Default.Delete,
                  contentDescription = nativeString("Remove wake phrase"),
                  onClick = {
                    wakeWordDrafts = wakeWordDrafts.filterIndexed { draftIndex, _ -> draftIndex != index }
                  },
                )
              }
            }
          }
          ClawSecondaryButton(
            text = nativeString("Add wake phrase"),
            onClick = { wakeWordDrafts = wakeWordDrafts + "" },
            enabled = voiceWakeAvailable && !voiceWakeWordsSaving && wakeWordDrafts.size < VoiceWakePreferences.maxWords,
            icon = Icons.Default.Add,
            modifier = Modifier.fillMaxWidth(),
          )
          ClawSecondaryButton(
            text = if (voiceWakeWordsSaving) nativeString("Saving…") else nativeString("Save wake words"),
            onClick = { viewModel.setVoiceWakeWords(wakeWordDrafts) },
            enabled = voiceWakeAvailable && isConnected && !voiceWakeWordsSaving && wakeWordDrafts.any(String::isNotBlank),
            modifier = Modifier.fillMaxWidth(),
          )
          (voiceWakeWordsNoticeText ?: if (!isConnected) nativeString("Connect to a Gateway to save wake words") else null)?.let { notice ->
            Text(text = notice, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }
      Text(text = nativeString("Talk Provider Setup"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
      VoiceSetupPanel(talkSetupReadiness)
      Text(text = nativeString("Microphone"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
      AudioInputDevicePanel(
        devices = audioInputDevices,
        preferredDeviceKey = preferredAudioInputDevice,
        preferencePending = audioInputDevicePending,
        onSelect = viewModel::setPreferredAudioInputDevice,
      )
      Text(text = nativeString("Audio Test"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
      Text(text = nativeString("Check that OpenClaw can speak clearly on this phone."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
      SettingsWaveformPanel(active = speakerEnabled, onClick = ::playVoiceSetupTone)
      VoiceSetupActionRow(
        title = if (speakerEnabled) nativeString("Mute speaker") else nativeString("Enable speaker"),
        subtitle = if (speakerEnabled) nativeString("Replies play aloud") else nativeString("Assistant speech muted"),
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        statusText = if (speakerEnabled) nativeString("On") else nativeString("Muted"),
        ready = speakerEnabled,
        onClick = { viewModel.setSpeakerEnabled(!speakerEnabled) },
      )
      ClawPrimaryButton(text = nativeString("Done"), onClick = onBack, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.GraphicEq)
    }
  }
}

@Composable
private fun AudioInputDevicePanel(
  devices: List<AudioInputDeviceOption>,
  preferredDeviceKey: String?,
  preferencePending: Boolean,
  onSelect: (String?) -> Unit,
) {
  val preferredAvailable = devices.any { it.key == preferredDeviceKey }
  val unavailablePreferredDevice =
    preferredDeviceKey?.takeUnless { preferredAvailable }?.let(::audioInputDeviceOptionFromKey)
  ClawPanel {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      AudioInputDeviceRow(
        title = nativeString("Automatic"),
        subtitle =
          if (preferredDeviceKey != null && !preferredAvailable) {
            nativeString("Preferred microphone unavailable; using automatic routing.")
          } else {
            nativeString("Prioritizes connected Bluetooth microphones.")
          },
        selected = preferredDeviceKey == null || !preferredAvailable,
        pending = preferencePending && preferredDeviceKey == null,
        onClick = { onSelect(null) },
      )
      unavailablePreferredDevice?.let { device ->
        HorizontalDivider(color = ClawTheme.colors.border)
        AudioInputDeviceRow(
          title = device.productName.ifBlank { nativeString("Preferred microphone") },
          subtitle = nativeString("Unavailable"),
          selected = false,
          pending = preferencePending,
          onClick = null,
        )
      }
      devices.forEach { device ->
        HorizontalDivider(color = ClawTheme.colors.border)
        val typeLabel = audioInputDeviceTypeLabel(device.type)
        AudioInputDeviceRow(
          title = device.productName.ifBlank { typeLabel },
          subtitle = typeLabel,
          selected = device.key == preferredDeviceKey,
          pending = preferencePending && device.key == preferredDeviceKey,
          onClick = { onSelect(device.key) },
        )
      }
    }
  }
}

@Composable
private fun AudioInputDeviceRow(
  title: String,
  subtitle: String,
  selected: Boolean,
  pending: Boolean,
  onClick: (() -> Unit)?,
) {
  ClawListItem(
    title = title,
    subtitle = subtitle,
    metadata = nativeString("Next session").takeIf { pending },
    leading = { ClawIconBadge(Icons.Default.Mic) },
    trailing =
      if (selected) {
        {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = nativeString("Selected"),
            modifier = Modifier.size(18.dp),
            tint = ClawTheme.colors.primary,
          )
        }
      } else {
        null
      },
    onClick = onClick,
  )
}

@Composable
private fun audioInputDeviceTypeLabel(type: Int): String =
  when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_MIC -> nativeString("Built-in microphone")

    AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> nativeString("Bluetooth microphone")

    AudioDeviceInfo.TYPE_BLE_HEADSET -> nativeString("Bluetooth LE microphone")

    AudioDeviceInfo.TYPE_WIRED_HEADSET -> nativeString("Wired headset microphone")

    AudioDeviceInfo.TYPE_USB_DEVICE,
    AudioDeviceInfo.TYPE_USB_ACCESSORY,
    AudioDeviceInfo.TYPE_USB_HEADSET,
    -> nativeString("USB microphone")

    else -> nativeString("External microphone")
  }

@Composable
private fun VoiceSetupPanel(
  readiness: GatewayTalkSetupReadiness,
) {
  Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
    VoiceSetupReadinessRow(title = nativeString("Realtime Talk"), state = readiness.realtimeTalk, icon = Icons.Default.GraphicEq)
    VoiceSetupReadinessRow(title = nativeString("Dictation"), state = readiness.dictation, icon = Icons.Default.Mic)
  }
}

@Composable
private fun VoiceSetupReadinessRow(
  title: String,
  state: GatewayTalkSetupState,
  icon: ImageVector,
) {
  VoiceSetupActionRow(
    title = title,
    subtitle = gatewayTalkSetupDescription(state),
    icon = icon,
    statusText = gatewayTalkSetupStatusText(state),
    ready = state.isReady,
  )
}

@Composable
private fun VoiceSetupActionRow(
  title: String,
  subtitle: String,
  icon: ImageVector,
  statusText: String,
  ready: Boolean,
  onClick: (() -> Unit)? = null,
) {
  val rowModifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
  Surface(
    onClick = onClick ?: {},
    enabled = onClick != null,
    modifier = rowModifier,
    shape = RoundedCornerShape(ClawTheme.radii.panel),
    color = ClawTheme.colors.surface,
    contentColor = ClawTheme.colors.text,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
  ) {
    ClawListItem(
      title = title,
      subtitle = subtitle,
      modifier = Modifier.padding(horizontal = ClawTheme.spacing.xs),
      leading = {
        Surface(
          modifier = Modifier.size(ClawTheme.spacing.iconSlot),
          shape = CircleShape,
          color = ClawTheme.colors.canvas,
          contentColor = ClawTheme.colors.text,
          border = BorderStroke(1.dp, ClawTheme.colors.borderStrong),
        ) {
          Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ClawTheme.spacing.icon))
          }
        }
      },
      trailing = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
          Box(
            modifier =
              Modifier
                .size(6.dp)
                .background(if (ready) ClawTheme.colors.success else ClawTheme.colors.textSubtle, CircleShape),
          )
          Text(text = statusText, style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
          if (onClick != null) {
            Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = ClawTheme.colors.textMuted)
          }
        }
      },
    )
  }
}

@Composable
private fun SettingsWaveformPanel(
  active: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    modifier = Modifier.fillMaxWidth().height(64.dp),
    shape = RoundedCornerShape(ClawTheme.radii.panel),
    color = ClawTheme.colors.surface,
    contentColor = ClawTheme.colors.text,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = ClawTheme.spacing.sm),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
    ) {
      Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(ClawTheme.spacing.icon), tint = ClawTheme.colors.text)
      // Thinking is the preview phase: no capture runs on this screen, so the
      // synthetic swell demonstrates the animation without touching the mic.
      TalkWaveform(
        phase = if (active) TalkWaveformPhase.Thinking else TalkWaveformPhase.Idle,
        modifier = Modifier.weight(1f).height(40.dp),
      )
    }
  }
}

private fun playVoiceSetupTone() {
  val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 80)
  tone.startTone(ToneGenerator.TONE_PROP_BEEP, 250)
  Handler(Looper.getMainLooper()).postDelayed({ tone.release() }, 300L)
}

private const val NOTIFICATION_PICKER_RESULT_LIMIT = 40

@Composable
private fun NotificationSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val enabled by viewModel.notificationForwardingEnabled.collectAsState()
  val mode by viewModel.notificationForwardingMode.collectAsState()
  val packages by viewModel.notificationForwardingPackages.collectAsState()
  val quietEnabled by viewModel.notificationForwardingQuietHoursEnabled.collectAsState()
  val quietStart by viewModel.notificationForwardingQuietStart.collectAsState()
  val quietEnd by viewModel.notificationForwardingQuietEnd.collectAsState()
  val maxEventsPerMinute by viewModel.notificationForwardingMaxEventsPerMinute.collectAsState()
  val modeLabel = if (mode == NotificationPackageFilterMode.Blocklist) nativeString("Blocklist") else nativeString("Allowlist")
  val installedApps = remember(context, packages) { queryInstalledApps(context, packages) }
  var notificationPickerExpanded by remember { mutableStateOf(false) }
  var notificationAppSearch by remember { mutableStateOf("") }
  var notificationShowSystemApps by remember { mutableStateOf(false) }
  val filteredApps =
    remember(installedApps, packages, notificationAppSearch, notificationShowSystemApps) {
      filterNotificationAppsForPicker(
        apps = installedApps,
        selectedPackages = packages,
        query = notificationAppSearch,
        showSystemApps = notificationShowSystemApps,
      )
    }
  var listenerEnabled by remember { mutableStateOf(DeviceNotificationListenerService.isAccessEnabled(context)) }

  DisposableEffect(lifecycleOwner, context) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          listenerEnabled = DeviceNotificationListenerService.isAccessEnabled(context)
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val notificationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.setNotificationForwardingEnabled(granted && DeviceNotificationListenerService.isAccessEnabled(context))
    }

  fun setForwarding(checked: Boolean) {
    if (!checked) {
      viewModel.setNotificationForwardingEnabled(false)
      return
    }
    listenerEnabled = DeviceNotificationListenerService.isAccessEnabled(context)
    if (!listenerEnabled) {
      openNotificationListenerSettings(context)
      return
    }
    if (Build.VERSION.SDK_INT >= 33 && !hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)) {
      notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      viewModel.setNotificationForwardingEnabled(true)
    }
  }

  SettingsDetailFrame(title = nativeString("Notifications"), subtitle = nativeString("Choose what reaches OpenClaw."), icon = Icons.Default.Notifications, onBack = onBack) {
    SettingsTogglePanel(
      rows =
        listOf(
          SettingsToggleRow(nativeString("Forward Notifications"), if (enabled) nativeString("OpenClaw can receive selected alerts.") else nativeString("Alerts stay on this phone."), Icons.Default.Notifications, enabled, ::setForwarding),
          SettingsToggleRow(
            nativeString("Quiet Hours"),
            nativeString("\$quietStart to \$quietEnd", quietStart, quietEnd),
            Icons.Default.Bolt,
            quietEnabled,
            onCheckedChange = { checked ->
              viewModel.setNotificationForwardingQuietHours(enabled = checked, start = quietStart, end = quietEnd)
            },
          ),
        ),
    )
    SettingsMetricPanel(
      rows =
        listOf(
          SettingsMetric(nativeString("Policy"), modeLabel),
          SettingsMetric(nativeString("Selected Apps"), packages.size.toString()),
          SettingsMetric(nativeString("Rate Limit"), "$maxEventsPerMinute/min"),
          SettingsMetric(nativeString("Access"), if (listenerEnabled) nativeString("Granted") else nativeString("Setup")),
        ),
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      ClawSecondaryButton(
        text = if (listenerEnabled) nativeString("Check Access") else nativeString("Open System Access"),
        onClick = {
          openNotificationListenerSettings(context)
        },
        modifier = Modifier.weight(1f),
      )
    }
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = nativeString("Forwarding Mode"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        ClawSegmentedControl(
          options = listOf(nativeString("Blocklist"), nativeString("Allowlist")),
          selected = nativeString(modeLabel),
          onSelect = { selected ->
            viewModel.setNotificationForwardingMode(
              if (selected == nativeString("Allowlist")) {
                NotificationPackageFilterMode.Allowlist
              } else {
                NotificationPackageFilterMode.Blocklist
              },
            )
          },
        )
      }
    }
    NotificationPackagePickerPanel(
      mode = mode,
      selectedPackages = packages,
      apps = filteredApps,
      search = notificationAppSearch,
      showSystemApps = notificationShowSystemApps,
      expanded = notificationPickerExpanded,
      onSearchChange = { notificationAppSearch = it },
      onShowSystemAppsChange = { notificationShowSystemApps = it },
      onExpandedChange = { notificationPickerExpanded = it },
      onPackageSelectionChange = { packageName, selected ->
        val next = packages.toMutableSet()
        if (selected) {
          next.add(packageName)
        } else {
          next.remove(packageName)
        }
        viewModel.setNotificationForwardingPackagesCsv(next.sorted().joinToString(","))
      },
    )
  }
}

@Composable
private fun NotificationPackagePickerPanel(
  mode: NotificationPackageFilterMode,
  selectedPackages: Set<String>,
  apps: List<InstalledApp>,
  search: String,
  showSystemApps: Boolean,
  expanded: Boolean,
  onSearchChange: (String) -> Unit,
  onShowSystemAppsChange: (Boolean) -> Unit,
  onExpandedChange: (Boolean) -> Unit,
  onPackageSelectionChange: (String, Boolean) -> Unit,
) {
  val visibleApps = apps.take(NOTIFICATION_PICKER_RESULT_LIMIT)
  ClawPanel {
    Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
      Text(text = nativeString("App Filter"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
      Text(
        text = notificationPackageSelectionSummary(mode = mode, selectedCount = selectedPackages.size),
        style = ClawTheme.type.body,
        color = ClawTheme.colors.textMuted,
      )
      ClawSecondaryButton(
        text = if (expanded) nativeString("Close App Picker") else nativeString("Open App Picker"),
        onClick = { onExpandedChange(!expanded) },
        modifier = Modifier.fillMaxWidth(),
      )
      if (expanded) {
        ClawTextField(value = search, onValueChange = onSearchChange, placeholder = nativeString("Search apps"))
        SettingsToggleListRow(
          SettingsToggleRow(
            title = nativeString("Show System Apps"),
            subtitle = nativeString("Include Android and background packages."),
            icon = Icons.Default.Storage,
            checked = showSystemApps,
            onCheckedChange = onShowSystemAppsChange,
          ),
        )
        if (visibleApps.isEmpty()) {
          Text(text = nativeString("No matching apps."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        } else {
          ClawSeparatedColumn(items = visibleApps) { app ->
            NotificationPackageAppRow(
              app = app,
              selected = selectedPackages.contains(app.packageName),
              onSelectedChange = { selected -> onPackageSelectionChange(app.packageName, selected) },
            )
          }
          if (apps.size > visibleApps.size) {
            Text(
              text = nativeString("Showing \${visibleApps.size} of \${apps.size}. Refine search for more.", visibleApps.size, apps.size),
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun NotificationPackageAppRow(
  app: InstalledApp,
  selected: Boolean,
  onSelectedChange: (Boolean) -> Unit,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = ClawTheme.spacing.row)
        .clickable { onSelectedChange(!selected) }
        .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
  ) {
    ClawTextBadge(text = notificationAppBadge(app.label))
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(
        text = app.label,
        style = ClawTheme.type.body,
        color = ClawTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = app.packageName,
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    Switch(checked = selected, onCheckedChange = onSelectedChange)
  }
}

@Composable
private fun PhoneCapabilitiesScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val cameraEnabled by viewModel.cameraEnabled.collectAsState()
  val locationMode by viewModel.locationMode.collectAsState()
  val locationPreciseEnabled by viewModel.locationPreciseEnabled.collectAsState()
  val preventSleep by viewModel.preventSleep.collectAsState()
  val installedAppsSharingEnabled by viewModel.installedAppsSharingEnabled.collectAsState()
  val photosAvailable = remember { SensitiveFeatureConfig.photosEnabled }
  val backgroundLocationAvailable = remember { SensitiveFeatureConfig.backgroundLocationEnabled }
  val photoPermissions = remember { photoReadPermissionsForRequest() }
  var photosGranted by remember { mutableStateOf(photosAvailable && hasPhotoReadPermission(context)) }
  var pendingLocationModeRaw by rememberSaveable { mutableStateOf<String?>(null) }
  var pendingAlwaysPreviousModeRaw by rememberSaveable { mutableStateOf<String?>(null) }
  var awaitingBackgroundSettings by rememberSaveable { mutableStateOf(false) }
  var showBackgroundLocationExplanation by rememberSaveable { mutableStateOf(false) }
  var showInstalledAppsDisclosure by rememberSaveable { mutableStateOf(false) }
  var pendingPreciseLocation by rememberSaveable { mutableStateOf(false) }
  val platformBackgroundPermissionLabel =
    remember(context) {
      context.packageManager.backgroundPermissionOptionLabel.toString()
    }
  val backgroundPermissionLabel = resolvedBackgroundPermissionLabel(platformBackgroundPermissionLabel)
  val cameraPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      viewModel.setCameraEnabled(granted)
    }
  val locationPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
      val foregroundGranted = hasLocationPermission(context)
      val fineGranted = hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
      if (pendingPreciseLocation) {
        pendingPreciseLocation = false
        viewModel.setLocationPreciseEnabled(fineGranted)
        if (foregroundGranted && locationMode == LocationMode.Off) {
          viewModel.setLocationMode(LocationMode.WhileUsing)
        }
        return@rememberLauncherForActivityResult
      }

      val requestedMode = LocationMode.fromRawValue(pendingLocationModeRaw)
      pendingLocationModeRaw = null
      when (requestedMode) {
        LocationMode.WhileUsing -> {
          viewModel.setLocationMode(
            if (foregroundGranted) LocationMode.WhileUsing else LocationMode.Off,
          )
        }

        LocationMode.Always -> {
          if (foregroundGranted) {
            viewModel.setLocationMode(LocationMode.WhileUsing)
            showBackgroundLocationExplanation = true
          } else {
            viewModel.setLocationMode(LocationMode.Off)
            pendingAlwaysPreviousModeRaw = null
          }
        }

        LocationMode.Off -> {}
      }
      viewModel.setLocationPreciseEnabled(fineGranted)
    }
  val photoPermissionLauncher =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      photosGranted = photosAvailable && hasPhotoReadPermission(context)
    }

  DisposableEffect(
    lifecycleOwner,
    context,
    photosAvailable,
    backgroundLocationAvailable,
    locationMode,
    awaitingBackgroundSettings,
    pendingAlwaysPreviousModeRaw,
  ) {
    val observer =
      LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
          photosGranted = photosAvailable && hasPhotoReadPermission(context)
          val foregroundGranted = hasLocationPermission(context)
          val backgroundGranted = hasBackgroundLocationPermission(context)
          if (awaitingBackgroundSettings && pendingAlwaysPreviousModeRaw != null) {
            val previousMode = LocationMode.fromRawValue(pendingAlwaysPreviousModeRaw)
            viewModel.setLocationMode(
              locationModeAfterBackgroundSettings(
                previousMode = previousMode,
                foregroundGranted = foregroundGranted,
                backgroundGranted = backgroundGranted,
              ),
            )
            awaitingBackgroundSettings = false
            pendingAlwaysPreviousModeRaw = null
          } else if (
            locationMode == LocationMode.Always &&
            (!backgroundLocationAvailable || !foregroundGranted || !backgroundGranted)
          ) {
            viewModel.setLocationMode(
              if (foregroundGranted) LocationMode.WhileUsing else LocationMode.Off,
            )
          } else if (locationMode == LocationMode.WhileUsing && !foregroundGranted) {
            viewModel.setLocationMode(LocationMode.Off)
          }
        }
      }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  fun setCameraAccess(checked: Boolean) {
    if (!checked) {
      viewModel.setCameraEnabled(false)
      return
    }
    if (hasPermission(context, Manifest.permission.CAMERA)) {
      viewModel.setCameraEnabled(true)
    } else {
      cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }
  }

  fun setLocationAccess(mode: LocationMode) {
    when (mode) {
      LocationMode.Off -> {
        viewModel.setLocationMode(LocationMode.Off)
      }

      LocationMode.WhileUsing -> {
        if (hasLocationPermission(context)) {
          viewModel.setLocationMode(LocationMode.WhileUsing)
        } else {
          pendingLocationModeRaw = mode.rawValue
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
          )
        }
      }

      LocationMode.Always -> {
        if (!backgroundLocationAvailable) return
        if (hasLocationPermission(context) && hasBackgroundLocationPermission(context)) {
          viewModel.setLocationMode(LocationMode.Always)
          return
        }
        pendingAlwaysPreviousModeRaw = locationMode.rawValue
        if (hasLocationPermission(context)) {
          showBackgroundLocationExplanation = true
        } else {
          pendingLocationModeRaw = mode.rawValue
          locationPermissionLauncher.launch(
            arrayOf(
              Manifest.permission.ACCESS_FINE_LOCATION,
              Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
          )
        }
      }
    }
  }

  fun setPreciseLocation(checked: Boolean) {
    if (!checked) {
      viewModel.setLocationPreciseEnabled(false)
      return
    }
    if (hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)) {
      viewModel.setLocationPreciseEnabled(true)
      if (locationMode == LocationMode.Off) {
        viewModel.setLocationMode(LocationMode.WhileUsing)
      }
    } else {
      pendingPreciseLocation = true
      locationPermissionLauncher.launch(
        arrayOf(
          Manifest.permission.ACCESS_FINE_LOCATION,
          Manifest.permission.ACCESS_COARSE_LOCATION,
        ),
      )
    }
  }

  fun setPhotoAccess(checked: Boolean) {
    if (checked && !hasPhotoReadPermission(context)) {
      photoPermissionLauncher.launch(photoPermissions.toTypedArray())
    } else {
      openAppPermissionSettings(context)
    }
  }

  fun setInstalledAppsSharing(checked: Boolean) {
    if (checked) {
      showInstalledAppsDisclosure = true
    } else {
      viewModel.revokeInstalledAppsDisclosureConsent()
    }
  }

  SettingsDetailFrame(title = nativeString("Phone Capabilities"), subtitle = nativeString("Choose what this phone can share."), icon = Icons.AutoMirrored.Filled.ScreenShare, onBack = onBack) {
    SettingsTogglePanel(
      rows =
        listOfNotNull(
          SettingsToggleRow(nativeString("Camera"), nativeString("Allow camera tools when requested."), Icons.Default.CameraAlt, cameraEnabled, ::setCameraAccess),
          SettingsToggleRow(nativeString("Precise Location"), nativeString("Share precise location while location is enabled."), Icons.Default.LocationOn, locationPreciseEnabled, ::setPreciseLocation),
          if (photosAvailable) {
            SettingsToggleRow(
              nativeString("Photos"),
              if (photosGranted) nativeString("Selected or full photo access granted.") else nativeString("Allow photo library access."),
              Icons.Default.Image,
              photosGranted,
              ::setPhotoAccess,
            )
          } else {
            null
          },
          SettingsToggleRow(
            nativeString("Installed Apps"),
            if (installedAppsSharingEnabled) nativeString("OpenClaw can list launcher-visible apps.") else nativeString("App list stays on this phone."),
            Icons.Default.Storage,
            installedAppsSharingEnabled,
            ::setInstalledAppsSharing,
          ),
          SettingsToggleRow(nativeString("Keep Awake"), nativeString("Keep the node available during active work."), Icons.Default.Bolt, preventSleep, viewModel::setPreventSleep),
        ),
    )
    if (SensitiveFeatureConfig.accessibilityControlEnabled) {
      FlavorPhoneCapabilitiesSettings(viewModel)
    }
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = nativeString("Location"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        ClawSegmentedControl(
          options = locationModeLabels(backgroundLocationAvailable),
          selected = locationMode.displayLabel,
          onSelect = { selected -> setLocationAccess(locationModeForLabel(selected)) },
        )
        if (backgroundLocationAvailable) {
          Text(
            text = nativeString("Always allows requested location checks while OpenClaw is in the background; Android shows this in the persistent node notification."),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
    }
  }

  if (showInstalledAppsDisclosure) {
    InstalledAppsDisclosureDialog(
      onDismiss = { showInstalledAppsDisclosure = false },
      onAgree = {
        showInstalledAppsDisclosure = false
        viewModel.grantInstalledAppsDisclosureConsent()
      },
    )
  }

  if (showBackgroundLocationExplanation) {
    fun cancelBackgroundLocationRequest() {
      val previousMode = LocationMode.fromRawValue(pendingAlwaysPreviousModeRaw)
      viewModel.setLocationMode(
        locationModeAfterBackgroundSettings(
          previousMode = previousMode,
          foregroundGranted = hasLocationPermission(context),
          backgroundGranted = hasBackgroundLocationPermission(context),
        ),
      )
      pendingAlwaysPreviousModeRaw = null
      showBackgroundLocationExplanation = false
    }

    AlertDialog(
      onDismissRequest = ::cancelBackgroundLocationRequest,
      title = { Text(nativeString("Allow background location?")) },
      text = {
        Text(
          nativeString(
            "OpenClaw only checks location when your paired Gateway requests it. On the next Android screen, choose \$backgroundPermissionLabel to allow checks while the app is in the background.",
            backgroundPermissionLabel,
          ),
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            showBackgroundLocationExplanation = false
            awaitingBackgroundSettings = true
            openAppPermissionSettings(context)
          },
        ) {
          Text(nativeString("Open Settings"))
        }
      },
      dismissButton = {
        TextButton(onClick = ::cancelBackgroundLocationRequest) {
          Text(nativeString("Not Now"))
        }
      },
    )
  }
}

@Composable
private fun InstalledAppsDisclosureDialog(
  onDismiss: () -> Unit,
  onAgree: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(nativeString("Share installed app information?")) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
        Text(
          nativeString("OpenClaw collects and sends the names, package IDs, and status of apps visible on this phone when your paired OpenClaw Gateway asks for them. This lets your assistant answer questions and take actions using installed apps."),
        )
        Text(
          nativeString("Your phone sends this information to your Gateway, not to a server run by OpenClaw. Your Gateway may include it in requests to the AI provider you chose."),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onAgree) {
        Text(nativeString("Agree and Enable"))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(nativeString("Not Now"))
      }
    },
  )
}

@Composable
private fun GatewaySettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val uriHandler = LocalUriHandler.current
  val isNodeConnected by viewModel.isNodeConnected.collectAsState()
  val operatorAdminScopeAvailable by viewModel.operatorAdminScopeAvailable.collectAsState()
  val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()
  val serverName by viewModel.serverName.collectAsState()
  val remoteAddress by viewModel.remoteAddress.collectAsState()
  val manualHost by viewModel.manualHost.collectAsState()
  val manualPort by viewModel.manualPort.collectAsState()
  val manualTls by viewModel.manualTls.collectAsState()
  val pairedGateways by viewModel.pairedGateways.collectAsState()
  val activeGatewayStableId by viewModel.activeGatewayStableId.collectAsState()
  val connectedGatewayStableIds by viewModel.connectedGatewayStableIds.collectAsState()
  val discoveredGateways by viewModel.gateways.collectAsState()
  val gatewayAgents by viewModel.gatewayAgents.collectAsState()
  val gatewayDefaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
  val instanceId by viewModel.instanceId.collectAsState()
  var setupCode by remember { mutableStateOf("") }
  var hostInput by remember(manualHost) { mutableStateOf(manualHost.ifBlank { "127.0.0.1" }) }
  var portInput by remember(manualPort) { mutableStateOf(manualPort.toString()) }
  var tlsInput by remember(manualTls) { mutableStateOf(manualTls) }
  var tokenInput by remember { mutableStateOf("") }
  var bootstrapTokenInput by remember { mutableStateOf("") }
  var passwordInput by remember { mutableStateOf("") }
  var validationText by remember { mutableStateOf<String?>(null) }
  var showSetupCodeHelp by remember { mutableStateOf(false) }
  var pendingSetupResetPlan by remember { mutableStateOf<GatewayConnectPlan?>(null) }
  var pendingForgetStableId by remember { mutableStateOf<String?>(null) }
  val transport =
    remember(hostInput, tlsInput) {
      gatewayManualTransportPresentation(
        hostInput = hostInput,
        requestedTls = tlsInput,
      )
    }

  fun saveAndConnect(plan: GatewayConnectPlan) {
    validationText = null
    viewModel.saveGatewayConfigAndConnect(plan)
  }

  pendingSetupResetPlan?.let { plan ->
    FoldAwarePrompt(
      onDismissRequest = { pendingSetupResetPlan = null },
      title = nativeString("Replace gateway setup?"),
      text = {
        Text(
          gatewaySettingsSetupResetConfirmationText(),
          style = ClawTheme.type.body,
          color = ClawTheme.colors.text,
        )
      },
      actions = {
        TextButton(onClick = { pendingSetupResetPlan = null }) {
          Text(nativeString("Cancel"))
        }
        TextButton(
          onClick = {
            pendingSetupResetPlan = null
            saveAndConnect(plan)
          },
        ) {
          Text(nativeString("Replace setup"))
        }
      },
      containerColor = ClawTheme.colors.surface,
    )
  }

  pendingForgetStableId?.let { stableId ->
    val entry = pairedGateways.firstOrNull { it.stableId == stableId }
    val gatewayName = entry?.name ?: nativeString("this gateway")
    FoldAwarePrompt(
      onDismissRequest = { pendingForgetStableId = null },
      title = nativeString("Forget gateway?"),
      text = {
        Text(
          nativeString(
            "Remove \$gatewayName and its saved credentials from this phone?",
            gatewayName,
          ),
        )
      },
      actions = {
        TextButton(onClick = { pendingForgetStableId = null }) { Text(nativeString("Cancel")) }
        TextButton(
          onClick = {
            pendingForgetStableId = null
            viewModel.forgetGateway(stableId)
          },
        ) {
          Text(nativeString("Forget"))
        }
      },
      containerColor = ClawTheme.colors.surface,
    )
  }

  // Discovery only runs while a discovery consumer is active; the Add Gateway
  // panel needs live results just like onboarding does.
  LaunchedEffect(Unit) { viewModel.startGatewayDiscovery() }

  fun connectSetupCode() {
    val plan =
      resolveGatewayConnectPlan(
        useSetupCode = true,
        setupCode = setupCode,
        savedManualHost = manualHost,
        savedManualPort = manualPort.toString(),
        savedManualTls = manualTls,
        manualHostInput = hostInput,
        manualPortInput = portInput,
        manualTlsInput = transport.effectiveTls,
        tokenInput = "",
        bootstrapTokenInput = "",
        passwordInput = "",
      )
    if (plan == null) {
      validationText = nativeString("Enter a valid setup code or gateway address.")
      return
    }
    if (plan.savedAuthAction == GatewaySavedAuthAction.REPLACE_SETUP) {
      pendingSetupResetPlan = plan
    } else {
      saveAndConnect(plan)
    }
  }

  SettingsDetailFrame(
    title = nativeString("Gateway"),
    subtitle = nativeString("Connection between this phone and OpenClaw."),
    icon = Icons.Default.Cloud,
    onBack = onBack,
    trailingAction = {
      ClawPlainIconButton(
        icon = Icons.Default.QrCode2,
        contentDescription = nativeString("Scan QR"),
        onClick = viewModel::pairNewGateway,
      )
    },
  ) {
    SettingsMetricPanel(
      rows =
        listOf(
          SettingsMetric(nativeString("Connection"), if (gatewayConnectionDisplay.isConnected) nativeString("Connected") else nativeString("Offline")),
          SettingsMetric(nativeString("Node"), if (isNodeConnected) nativeString("Online") else nativeString("Offline")),
          SettingsMetric(
            nativeString("Access"),
            gatewayAccessLabel(
              isConnected = gatewayConnectionDisplay.isConnected,
              operatorAdminScopeAvailable = operatorAdminScopeAvailable,
            ),
          ),
          SettingsMetric(nativeString("Gateway"), serverName?.takeIf { it.isNotBlank() } ?: nativeString("Home Gateway")),
          SettingsMetric(nativeString("Address"), remoteAddress?.takeIf { it.isNotBlank() } ?: nativeString("Not available")),
          SettingsMetric(
            nativeString("Status"),
            gatewayStatusLabel(gatewayConnectionDisplay),
          ),
          SettingsMetric(nativeString("Discovered"), discoveredGateways.size.toString()),
          SettingsMetric(nativeString("Default Agent"), defaultAgentName(gatewayAgents, gatewayDefaultAgentId)),
          SettingsMetric(nativeString("Agents"), gatewayAgents.size.toString()),
          SettingsMetric(nativeString("Instance ID"), instanceId, copyable = true),
        ),
    )
    // First-run hero: no paired gateways yet, so pairing is the primary action.
    if (gatewayShowsScanHero(pairedGateways.size)) {
      ClawPrimaryButton(
        text = nativeString("Scan QR to Pair"),
        onClick = viewModel::pairNewGateway,
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Default.QrCode2,
      )
    }
    if (gatewayConnectionDisplay.isConnected && !operatorAdminScopeAvailable) {
      ClawPanel {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
            text = nativeString("Limited Gateway access"),
            style = ClawTheme.type.section,
            color = ClawTheme.colors.text,
          )
          Text(
            text = gatewayLimitedAccessUpgradeText(),
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
    }
    gatewayConnectionDisplay.problem?.takeIf { it.isNetworkFailure }?.let { problem ->
      Text(
        text = recoveryGatewayAuthDetail(problem),
        style = ClawTheme.type.body,
        color = ClawTheme.colors.textMuted,
      )
      gatewayNetworkRecoveryHelpUrl(problem)?.let { url ->
        TextButton(onClick = { uriHandler.openUri(url) }) {
          Text(nativeString("Set up Tailscale"))
        }
      }
    }
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      ClawPrimaryButton(text = nativeString("Reconnect"), onClick = viewModel::refreshGatewayConnection, modifier = Modifier.weight(1f))
      ClawSecondaryButton(text = nativeString("Disconnect"), onClick = viewModel::disconnect, modifier = Modifier.weight(1f))
    }
    ClawSecondaryButton(
      text = nativeString("Diagnose"),
      onClick = {
        copyGatewayDiagnosticsReport(
          context = context,
          screen = "gateway settings",
          gatewayAddress = gatewayDiagnosticsEndpoint(remoteAddress, manualHost, manualPort, manualTls),
          statusText = gatewayStatusLabel(gatewayConnectionDisplay),
        )
      },
      modifier = Modifier.fillMaxWidth(),
    )
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
        Text(text = nativeString("Add Gateway"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        Text(
          text = nativeString("Scan or paste a setup code to add another gateway."),
          style = ClawTheme.type.body,
          color = ClawTheme.colors.textMuted,
        )
        ClawSecondaryButton(text = nativeString("Scan QR"), onClick = viewModel::pairNewGateway, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.QrCode2)
        ClawTextField(value = setupCode, onValueChange = { setupCode = it }, placeholder = nativeString("Setup code"), secret = true)
        ClawSecondaryButton(text = nativeString("Connect"), onClick = ::connectSetupCode, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Cloud)
        TextButton(onClick = { showSetupCodeHelp = !showSetupCodeHelp }) {
          Text(nativeString("Where do I get a setup code?"))
        }
        if (showSetupCodeHelp) {
          Text(
            text = nativeString("Android can scan or paste an existing setup code, but this gateway does not expose setup-code generation to the app yet. Generate the QR/code on the gateway host with openclaw qr, then scan it here or paste the setup code below."),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
        if (discoveredGateways.isEmpty()) {
          Text(
            text = nativeString("No gateways found yet. Use manual setup if discovery is blocked."),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        } else {
          discoveredGateways.forEachIndexed { index, endpoint ->
            if (index > 0) HorizontalDivider(color = ClawTheme.colors.border)
            ClawListItem(
              title = endpoint.name,
              subtitle = gatewayDiscoveredRowSubtitle(endpoint),
              leading = { ClawIconBadge(Icons.Default.Cloud) },
              trailing = {
                TextButton(onClick = { viewModel.connect(endpoint) }) {
                  Text(nativeString("Connect"))
                }
              },
              onClick = null,
            )
          }
        }
      }
    }
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = nativeString("Gateways"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        if (pairedGateways.isEmpty()) {
          Text(text = nativeString("No paired gateways."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        } else {
          pairedGateways.forEachIndexed { index, entry ->
            if (index > 0) HorizontalDivider(color = ClawTheme.colors.border)
            ClawListItem(
              title = entry.name,
              subtitle =
                when (entry.kind) {
                  GatewayRegistryEntryKind.MANUAL -> "${entry.host}:${entry.port}"
                  GatewayRegistryEntryKind.DISCOVERED -> entry.stableId
                },
              leading = {
                if (entry.stableId == activeGatewayStableId) {
                  ClawIconBadge(Icons.Default.Check)
                } else {
                  ClawIconBadge(Icons.Default.Cloud)
                }
              },
              trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Switch(
                    checked = entry.stableId == activeGatewayStableId || entry.stableId in connectedGatewayStableIds,
                    onCheckedChange = { enabled ->
                      viewModel.setGatewayConnectionEnabled(entry.stableId, enabled)
                    },
                    enabled = entry.stableId != activeGatewayStableId,
                  )
                  TextButton(onClick = { pendingForgetStableId = entry.stableId }) {
                    Text(nativeString("Forget"))
                  }
                }
              },
              onClick =
                if (entry.stableId == activeGatewayStableId) {
                  null
                } else {
                  { viewModel.switchToGateway(entry.stableId) }
                },
            )
          }
        }
      }
    }
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = nativeString("Manual Gateway"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawTextField(value = hostInput, onValueChange = { hostInput = it }, placeholder = nativeString("Host"), modifier = Modifier.weight(1f))
          ClawTextField(value = portInput, onValueChange = { portInput = it }, placeholder = nativeString("Port"), modifier = Modifier.weight(0.62f))
        }
        Text(text = nativeString("Connection security"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        val securityOptions = listOf(nativeString("Unencrypted"), nativeString("Secure (TLS)"))
        ClawSegmentedControl(
          options = securityOptions,
          selected = if (transport.effectiveTls) nativeString("Secure (TLS)") else nativeString("Unencrypted"),
          onSelect = { selected -> tlsInput = selected == nativeString("Secure (TLS)") },
          enabledOptions =
            if (transport.requiresTls) {
              setOf(nativeString("Secure (TLS)"))
            } else {
              securityOptions.toSet()
            },
        )
        transport.helperText?.let { helperText ->
          Text(
            text = helperText,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          ClawTextField(value = tokenInput, onValueChange = { tokenInput = it }, placeholder = nativeString("Token"), modifier = Modifier.weight(1f), secret = true)
          ClawTextField(value = bootstrapTokenInput, onValueChange = { bootstrapTokenInput = it }, placeholder = nativeString("Bootstrap"), modifier = Modifier.weight(1.05f), secret = true)
        }
        ClawTextField(value = passwordInput, onValueChange = { passwordInput = it }, placeholder = nativeString("Password"), secret = true)
        validationText?.let {
          Text(text = it, style = ClawTheme.type.caption, color = ClawTheme.colors.warning)
        }
        ClawPrimaryButton(
          text = nativeString("Save & Connect"),
          onClick = {
            val plan =
              resolveGatewayConnectPlan(
                useSetupCode = false,
                setupCode = "",
                savedManualHost = manualHost,
                savedManualPort = manualPort.toString(),
                savedManualTls = manualTls,
                manualHostInput = hostInput,
                manualPortInput = portInput,
                manualTlsInput = transport.effectiveTls,
                tokenInput = tokenInput,
                bootstrapTokenInput = bootstrapTokenInput,
                passwordInput = passwordInput,
              )
            if (plan == null) {
              validationText = nativeString("Enter a valid setup code or gateway address.")
              return@ClawPrimaryButton
            }
            if (plan.savedAuthAction == GatewaySavedAuthAction.REPLACE_SETUP) {
              pendingSetupResetPlan = plan
            } else {
              saveAndConnect(plan)
            }
          },
          modifier = Modifier.fillMaxWidth(),
        )
      }
    }
  }
}

internal fun gatewayAccessLabel(
  isConnected: Boolean,
  operatorAdminScopeAvailable: Boolean,
): String =
  when {
    !isConnected -> nativeString("Not available")
    operatorAdminScopeAvailable -> nativeString("Full")
    else -> nativeString("Limited")
  }

internal fun gatewayLimitedAccessUpgradeText(): String =
  nativeString(
    "Use a secure wss:// or Tailscale Serve Gateway, generate a full-access setup code in the Control UI or with openclaw qr, then scan or paste it below and reconnect to enable settings and upgrades.",
  )

@Composable
private fun AppearanceSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val themeMode by viewModel.appearanceThemeMode.collectAsState()
  val themeFamily by viewModel.appearanceThemeFamily.collectAsState()
  val accentArgb by viewModel.appearanceAccentArgb.collectAsState()
  val textScale by viewModel.appearanceTextScale.collectAsState()
  val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
  val context = LocalContext.current
  // Re-seeded whenever the committed value changes, so the thumb follows both
  // slider drags and any external reset without fighting the drag in progress.
  var textScaleDraft by remember(textScale) { mutableFloatStateOf(textScale) }
  var appLanguage by remember { mutableStateOf(currentAppLanguage()) }
  val systemLanguageTag = currentSystemLanguageTag(context)

  SettingsDetailFrame(title = nativeString("Appearance"), subtitle = nativeString("Theme and translated Android text."), icon = Icons.Default.Palette, onBack = onBack) {
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
        Text(text = nativeString("Theme family"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        AppearanceThemeFamily.entries.chunked(2).forEach { rowFamilies ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
          ) {
            rowFamilies.forEach { family ->
              AppearanceThemeFamilyCard(
                family = family,
                selected = family == themeFamily,
                onClick = { viewModel.setAppearanceThemeFamily(family) },
                modifier = Modifier.weight(1f),
              )
            }
            if (rowFamilies.size == 1) Spacer(modifier = Modifier.weight(1f))
          }
        }
        Text(
          text = nativeString("Color mode"),
          style = ClawTheme.type.section,
          color = ClawTheme.colors.text,
          modifier = Modifier.padding(top = ClawTheme.spacing.xxs),
        )
        ClawSegmentedControl(
          options = appearanceThemeOptions(),
          selected = appearanceThemeSummary(themeMode),
          onSelect = { selected -> viewModel.setAppearanceThemeMode(appearanceThemeModeForLabel(selected)) },
        )
        Text(
          text = nativeString("Accent color"),
          style = ClawTheme.type.section,
          color = ClawTheme.colors.text,
          modifier = Modifier.padding(top = ClawTheme.spacing.xxs),
        )
        (listOf<Long?>(null) + appearanceAccentPalette).chunked(5).forEach { accentRow ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
          ) {
            accentRow.forEach { candidate ->
              AppearanceAccentSwatch(
                argb = candidate,
                previewArgb = candidate ?: themeFamily.previewAccentArgb,
                selected = candidate == accentArgb,
                onClick = { viewModel.setAppearanceAccentArgb(candidate) },
              )
            }
          }
        }
      }
    }
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Text(
            text = nativeString("Text size"),
            style = ClawTheme.type.section,
            color = ClawTheme.colors.text,
          )
          Text(
            text = "${(textScaleDraft * 100).roundToInt()}%",
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
        Slider(
          value = textScaleDraft,
          onValueChange = { textScaleDraft = it },
          // Committing only on release keeps the whole app from re-measuring on
          // every frame of the drag; the preview below tracks the draft live.
          onValueChangeFinished = { viewModel.setAppearanceTextScale(textScaleDraft) },
          valueRange = MIN_APP_TEXT_SCALE..MAX_APP_TEXT_SCALE,
          steps = appTextScaleSteps(),
          modifier = Modifier.fillMaxWidth(),
          colors =
            SliderDefaults.colors(
              thumbColor = ClawTheme.colors.text,
              activeTrackColor = ClawTheme.colors.primary,
              inactiveTrackColor = ClawTheme.colors.borderStrong,
            ),
        )
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(text = "A", style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle)
          Text(text = "A", style = ClawTheme.type.title, color = ClawTheme.colors.textSubtle)
        }
        // The whole app is already scaled by the committed value, so the preview
        // only needs the relative difference to show the draft at true size.
        ProvideAppTextScale(scale = textScaleDraft / textScale) {
          AppearanceTextScalePreview()
        }
      }
    }
    SettingsTogglePanel(
      rows =
        listOf(
          SettingsToggleRow(
            title = nativeString("Haptics"),
            subtitle = nativeString("Buzz on chat controls: sidebar, new chat, menu, attachments, model, thinking, and the thinking slider."),
            icon = Icons.Default.Vibration,
            checked = hapticsEnabled,
            onCheckedChange = viewModel::setHapticsEnabled,
          ),
        ),
    )
    ClawPanel {
        Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
          Text(text = nativeString("Media cache"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
          Text(
            text = nativeString("Downloaded inbound images are kept locally and reused when available."),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
          ClawSecondaryButton(
            text = nativeString("Clear image cache"),
            onClick = viewModel::clearChatImageCache,
            modifier = Modifier.fillMaxWidth(),
          )
        }
    }
    ClawPanel {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = nativeString("App language"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
        AppLanguage.entries.forEachIndexed { index, language ->
          if (index > 0) HorizontalDivider(color = ClawTheme.colors.border)
          AppLanguageRow(
            language = language,
            selected = language == appLanguage,
            systemLanguageTag = systemLanguageTag,
            onClick = {
              appLanguage = language
              setAppLanguage(language)
            },
          )
        }
      }
    }
  }
}

@Composable
private fun AppearanceTextScalePreview() {
  val shape = RoundedCornerShape(ClawTheme.radii.control)
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = shape,
    color = ClawTheme.colors.canvas,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
  ) {
    Column(
      modifier = Modifier.padding(ClawTheme.spacing.xs),
      verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxxs),
    ) {
      Text(
        text = nativeString("Text size preview"),
        style = ClawTheme.type.label,
        color = ClawTheme.colors.text,
      )
      Text(
        text = nativeString("Every screen scales with this slider, including chat transcripts and sidebar rows."),
        style = ClawTheme.type.body,
        color = ClawTheme.colors.textMuted,
      )
      Text(
        text = nativeString("Session · just now"),
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textSubtle,
      )
    }
  }
}

@Composable
private fun AppearanceThemeFamilyCard(
  family: AppearanceThemeFamily,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(ClawTheme.radii.control)
  Column(
    modifier =
      modifier
        .heightIn(min = 72.dp)
        .clip(shape)
        .background(if (selected) ClawTheme.colors.surfacePressed else ClawTheme.colors.surface)
        .border(1.dp, if (selected) ClawTheme.colors.primary else ClawTheme.colors.borderStrong, shape)
        .clickable(onClick = onClick)
        .padding(ClawTheme.spacing.xs),
    verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        listOf(family.previewAccentArgb, family.previewSecondaryArgb, family.previewCanvasArgb).forEach { color ->
          Box(
            modifier =
              Modifier
                .size(13.dp)
                .clip(CircleShape)
                .background(Color(color))
                .border(1.dp, ClawTheme.colors.borderStrong, CircleShape),
          )
        }
      }
      if (selected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = nativeString("Selected"),
          tint = ClawTheme.colors.primary,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    Text(text = family.displayLabel, style = ClawTheme.type.label, color = ClawTheme.colors.text)
  }
}

internal fun appearanceAccentSwatchDescription(argb: Long?): String =
  listOf(
    nativeString("Accent color"),
    appearanceAccentPreferenceValue(argb)?.uppercase() ?: nativeString("Default"),
  ).joinToString(", ")

@Composable
private fun AppearanceAccentSwatch(
  argb: Long?,
  previewArgb: Long,
  selected: Boolean,
  onClick: () -> Unit,
) {
  IconButton(
    onClick = onClick,
    modifier =
      Modifier
        .size(48.dp)
        .semantics {
          contentDescription = appearanceAccentSwatchDescription(argb)
          this.selected = selected
        },
  ) {
    Box(
      modifier =
        Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(previewArgb))
          .border(if (selected) 3.dp else 1.dp, if (selected) ClawTheme.colors.text else ClawTheme.colors.borderStrong, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      if (selected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = ClawTheme.colors.canvas,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@Composable
private fun AppLanguageRow(
  language: AppLanguage,
  selected: Boolean,
  systemLanguageTag: String,
  onClick: () -> Unit,
) {
  ClawListItem(
    title = appLanguageTitle(language),
    subtitle = appLanguageRowSubtitle(language = language, systemLanguageTag = systemLanguageTag),
    leading = { ClawIconBadge(Icons.Default.Language) },
    trailing =
      if (selected) {
        {
          Icon(
            imageVector = Icons.Default.Check,
            contentDescription = nativeString("Selected"),
            modifier = Modifier.size(18.dp),
            tint = ClawTheme.colors.primary,
          )
        }
      } else {
        null
      },
    onClick = onClick,
  )
}

private fun appLanguageTitle(language: AppLanguage): String = if (language == AppLanguage.System) nativeString("System") else language.displayName

// Literal lookups keep every mode visible to the phone localization extractor.
internal fun appearanceThemeSummary(mode: AppearanceThemeMode): String =
  when (mode) {
    AppearanceThemeMode.System -> nativeString("System")
    AppearanceThemeMode.Dark -> nativeString("Dark")
    AppearanceThemeMode.Light -> nativeString("Light")
  }

internal fun appearanceThemeOptions(): List<String> = AppearanceThemeMode.entries.map(::appearanceThemeSummary)

internal fun appearanceThemeModeForLabel(label: String): AppearanceThemeMode =
  AppearanceThemeMode.entries.firstOrNull { appearanceThemeSummary(it).equals(label.trim(), ignoreCase = true) }
    ?: AppearanceThemeMode.Dark

internal fun locationModeLabels(backgroundLocationAvailable: Boolean): List<String> =
  if (backgroundLocationAvailable) {
    listOf(nativeString("Off"), nativeString("While Using"), nativeString("Always"))
  } else {
    listOf(nativeString("Off"), nativeString("While Using"))
  }

internal fun locationModeForLabel(label: String): LocationMode =
  when (label) {
    nativeString("While Using") -> LocationMode.WhileUsing
    nativeString("Always") -> LocationMode.Always
    else -> LocationMode.Off
  }

private val LocationMode.displayLabel: String
  get() =
    when (this) {
      LocationMode.Off -> nativeString("Off")
      LocationMode.WhileUsing -> nativeString("While Using")
      LocationMode.Always -> nativeString("Always")
    }

@Composable
private fun AboutSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val isConnected by viewModel.isConnected.collectAsState()
  val gatewayVersion by viewModel.gatewayVersion.collectAsState()
  val activeGatewayStableId by viewModel.activeGatewayStableId.collectAsState()
  val gateways by viewModel.gateways.collectAsState()
  val updateAvailable by viewModel.gatewayUpdateAvailable.collectAsState()
  val currentGatewayVersion = updateAvailable?.currentVersion?.takeIf { it.isNotBlank() } ?: gatewayVersion
  val appLocale = LocalConfiguration.current.locales[0]
  val identity =
    aboutBuildIdentity(
      versionName = BuildConfig.VERSION_NAME,
      versionCode = BuildConfig.VERSION_CODE,
      gitCommit = BuildConfig.GIT_COMMIT,
      buildTimestamp = BuildConfig.BUILD_TIMESTAMP,
      locale = appLocale,
      unknownLabel = stringResource(R.string.about_build_unknown),
    )
  val uriHandler = LocalUriHandler.current
  var logCapturing by remember { mutableStateOf(false) }
  // Reuse the transcript's morph-menu surface: it already grows the glass out
  // of the trigger's measured bounds, scales its content, blurs mid-morph and
  // folds back on dismiss — the exact animation the chat capsule/menus use.
  val logMenuState = rememberClawMorphMenuState()
  val logTrigger = rememberClawMorphTrigger(logMenuState)

  val activeEndpoint = gateways.firstOrNull { it.stableId == activeGatewayStableId }
  val gatewayType =
    when {
      !isConnected -> nativeString("未连接")
      viewModel.gatewayProxyConfigured(activeGatewayStableId) -> nativeString("反向代理")
      activeEndpoint?.tlsEnabled == true -> nativeString("TLS 直连")
      else -> nativeString("HTTP 直连")
    }
  val commitClick: (() -> Unit)? =
    identity.fullCommit?.let { commit ->
      {
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("OpenClaw commit", commit))
        Toast.makeText(context, nativeString("Commit copied"), Toast.LENGTH_SHORT).show()
      }
    }

  // HyperOS-style about page: a soft pastel gradient fills the ENTIRE screen —
  // status bar included — as the page background, dissolving into the canvas
  // colour towards the bottom so the groups appear to sit on it. The scaffold
  // itself is transparent; the gradient is painted behind it.
  val darkTheme = ClawTheme.colors.canvas.luminance() < 0.5f
  val pageColors =
    if (darkTheme) {
      listOf(Color(0xFF3A2E44), Color(0xFF32324A), Color(0xFF2C3E58))
    } else {
      listOf(Color(0xFFFACCDA), Color(0xFFEADAF3), Color(0xFFC7D9F7))
    }
  val pageBrush = Brush.verticalGradient(pageColors + ClawTheme.colors.canvas)
  // Title ink is the page gradient itself, deepened toward a neutral ink in the
  // light theme and lifted toward white in the dark one. Same hue family as the
  // backdrop, without the saturated two-colour tint that read as cheap.
  val titleInk = if (darkTheme) Color.White else Color(0xFF241F29)
  val titleMix = if (darkTheme) 0.55f else 0.68f
  val titleBrush = Brush.verticalGradient(pageColors.map { base -> lerp(base, titleInk, titleMix) })
  val listState = rememberLazyListState()
  // 0 at rest, 1 once the hero spacer has fully scrolled off. The spacer is the
  // first list item and exactly as tall as the static hero, so this is also how
  // far the first card has travelled toward the top bar.
  val scrollProgressState =
    remember(listState) {
      derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) {
          1f
        } else {
          val spacerSize = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size ?: 1
          (listState.firstVisibleItemScrollOffset / spacerSize.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
      }
    }
  val scrollProgress by scrollProgressState
  // The hero does not scroll with the list: it fades, lifts and shrinks in
  // place, finishing before the first card reaches the title's top edge.
  val heroFadeState =
    remember {
      derivedStateOf { ((scrollProgressState.value - 0.10f) / 0.26f).coerceIn(0f, 1f) }
    }
  val versionFadeState =
    remember {
      derivedStateOf { (1f - scrollProgressState.value / 0.20f).coerceIn(0f, 1f) }
    }
  // The bar's chrome (glass disc, its rim, title) only exists once cards can
  // slide underneath it; while the hero is flush with the top the bar reads as
  // bare background with a floating chevron, HyperOS style.
  val chromeOffsetThreshold = with(LocalDensity.current) { 8.dp.toPx() }
  val chromeShown by
    remember(listState, chromeOffsetThreshold) {
      derivedStateOf {
        listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > chromeOffsetThreshold
      }
    }
  val chromeAlpha by
    animateFloatAsState(
      targetValue = if (chromeShown) 1f else 0f,
      animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "aboutTopBarChrome",
    )
  val titleAlpha by
    animateFloatAsState(
      targetValue = if (scrollProgress > 0.38f) 1f else 0f,
      animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "aboutTopBarTitle",
    )
  val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val topBarHeight = statusBarHeight + ClawTopBarContentHeight
  // Progressive blur, same kit as the chat header: the source is the *unpadded*
  // page layer holding the gradient plus the scrolling cards, and the bar is a
  // sibling that samples it — sampling a layer that contains the sampler would
  // recurse through the render tree.
  val aboutBackdrop = rememberClawBackdrop()
  Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().layerBackdrop(aboutBackdrop).background(brush = pageBrush)) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val heroHeight = maxHeight * 0.40f
        // Static hero: the list slides over it, so OpenClaw-X never travels
        // with the scroll. Only its own fade/lift/shrink reads the scroll
        // state, and the gradient behind it stays put entirely.
        Box(
          modifier = Modifier.fillMaxWidth().offset(y = topBarHeight).height(heroHeight),
          contentAlignment = Alignment.Center,
        ) {
          Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Text(
              text = "OpenClaw-X",
              style =
                ClawTheme.type.title.copy(
                  fontSize = 34.sp,
                  fontWeight = FontWeight.Bold,
                  brush = titleBrush,
                ),
              modifier =
                Modifier.graphicsLayer {
                  val fade = heroFadeState.value
                  alpha = 1f - fade
                  translationY = -fade * 20.dp.toPx()
                  val scale = 1f - 0.05f * fade
                  scaleX = scale
                  scaleY = scale
                },
            )
            Text(
              text = BuildConfig.VERSION_NAME,
              style = ClawTheme.type.body,
              color = ClawTheme.colors.textMuted,
              modifier = Modifier.graphicsLayer { alpha = versionFadeState.value },
            )
          }
        }
        ClawScaffold(
          containerColor = Color.Transparent,
          contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
          contentPadding = PaddingValues(start = ClawTheme.spacing.sm, top = 0.dp, end = ClawTheme.spacing.sm, bottom = ClawTheme.spacing.xxxs),
        ) {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs),
            contentPadding = PaddingValues(top = topBarHeight, bottom = ClawTheme.spacing.xxxs),
          ) {
            item {
              // Reserves exactly the hero's footprint so the first card starts
              // below it; scrolling replaces this spacer with cards that slide
              // over the fading hero.
              Spacer(modifier = Modifier.fillMaxWidth().height(heroHeight))
            }
      item {
        MiuixGroup {
          AboutInfoRow(label = nativeString("二次开发"), value = "chenxi0yu")
          AboutInfoRow(label = nativeString("软件版本"), value = identity.version)
          AboutInfoRow(label = nativeString("提交"), value = identity.commit, onClick = commitClick, monospace = true)
          AboutInfoRow(label = nativeString("构建日期"), value = identity.built)
        }
      }
      item {
        MiuixGroup {
          // The device-card pattern: the section title lives INSIDE the card,
          // above its stat rows — sized like a card title, not a hero heading.
          Text(
            text = "Gateway",
            style = ClawTheme.type.title.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            color = ClawTheme.colors.text,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 2.dp),
          )
          AboutGatewayStatRow(value = gatewayType, caption = nativeString("网关类型"))
          AboutGatewayStatRow(value = currentGatewayVersion ?: "—", caption = nativeString("版本号"))
        }
      }
      item {
        MiuixGroup {
          aboutLinks.forEach { link ->
            MiuixValueRow(
              title = aboutLinkTitle(link.title),
              value = link.subtitle,
              reserveIconSpace = false,
              onClick = { uriHandler.openUri(link.url) },
            )
          }
        }
      }
      item {
        MiuixGroup {
          // The menu grows out of the trailing "导出 ⇅" cluster through the
          // transcript's shared morph surface; the whole row is the tap target.
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) {
                  if (logCapturing) return@clickable
                  logTrigger.open(
                    origin = TransformOrigin(1f, 1f),
                    panelWidth = 200.dp,
                  ) {
                    AppLogRange.entries.forEach { range ->
                      ClawGlassMenuItem(
                        label = range.title,
                        icon =
                          when (range) {
                            AppLogRange.LastHour -> Icons.Default.Schedule
                            AppLogRange.Today -> Icons.Default.Today
                            AppLogRange.Full -> Icons.Default.Description
                          },
                        onClick = {
                          if (logCapturing) return@ClawGlassMenuItem
                          logCapturing = true
                          scope.launch {
                            val file = withContext(Dispatchers.IO) { captureAppLogSnapshot(context, range) }
                            logCapturing = false
                            if (file == null) {
                              Toast.makeText(context, nativeString("日志提取失败"), Toast.LENGTH_SHORT).show()
                            } else {
                              shareAppLogSnapshot(context, file)
                            }
                          }
                        },
                      )
                    }
                  }
                }
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = nativeString("软件日志"),
              style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
              color = ClawTheme.colors.text,
            )
            Row(
              modifier = logTrigger.modifier,
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = nativeString("导出"),
                style = ClawTheme.type.body.copy(fontSize = 14.sp),
                color = ClawTheme.colors.textMuted,
                maxLines = 1,
              )
              Icon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = ClawTheme.colors.textMuted,
              )
            }
          }
        }
      }
      item {
        Text(
          text = nativeString("基于 OpenClaw 二次开发 · OpenClaw 采用 MIT 协议"),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textSubtle,
          modifier = Modifier.fillMaxWidth(),
          textAlign = TextAlign.Center,
        )
      }
          }
        }
      }
    }
    Box(
      modifier = Modifier.fillMaxWidth().height(topBarHeight).clawBackdropTopBar(aboutBackdrop),
    ) {
      AboutTopBarBackButton(
        contentDescription = nativeString("Back"),
        chromeAlpha = chromeAlpha,
        backdrop = aboutBackdrop,
        onClick = onBack,
        modifier = Modifier.align(Alignment.TopStart).padding(top = statusBarHeight + ClawTopBarButtonTopInset, start = ClawTheme.spacing.sm),
      )
      Box(
        modifier = Modifier.align(Alignment.TopCenter).padding(top = statusBarHeight).height(ClawTopBarContentHeight).fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = nativeString("About"),
          style = ClawTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
          color = ClawTheme.colors.text,
          modifier =
            Modifier.graphicsLayer {
              alpha = titleAlpha
              translationY = (1f - titleAlpha) * 10.dp.toPx()
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    // The transcript's shared morph surface draws the glass above everything,
    // grows it from the "导出 ⇅" anchor and is click-through while collapsed.
    ClawMorphMenuOverlay(
      state = logMenuState,
      onDismissRequest = { logMenuState.dismiss() },
      backdrop = aboutBackdrop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

/**
 * HyperOS top-bar back disc: a 42dp circle that is pure glass — no elevation
 * slab. At rest the glass and its rim are transparent and only the chevron
 * floats over the gradient; once cards slide under the bar the disc fades in
 * behind it.
 */
@Composable
private fun AboutTopBarBackButton(
  contentDescription: String,
  chromeAlpha: Float,
  backdrop: Backdrop,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ClawGlassTopBarButton(
    contentDescription = contentDescription,
    chromeAlpha = chromeAlpha,
    backdrop = backdrop,
    onClick = onClick,
    modifier = modifier,
  ) {
    Icon(
      imageVector = Icons.AutoMirrored.Filled.ArrowBack,
      contentDescription = null,
      modifier = Modifier.size(22.dp),
      tint = ClawTheme.colors.text,
    )
  }
}

/** HyperOS-style about row: 17sp medium label on the left, muted value on the right. */
@Composable
private fun AboutInfoRow(
  label: String,
  value: String,
  onClick: (() -> Unit)? = null,
  monospace: Boolean = false,
) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        .padding(horizontal = 16.dp, vertical = 13.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      color = ClawTheme.colors.text,
    )
    Text(
      text = value,
      style =
        ClawTheme.type.body.copy(
          fontSize = 14.sp,
          fontFamily = if (monospace) FontFamily.Monospace else ClawTheme.type.body.fontFamily,
        ),
      color = ClawTheme.colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

/** HyperOS device-card stat: bold value on top, small muted caption below. */
@Composable
private fun AboutGatewayStatRow(
  value: String,
  caption: String,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Text(
      text = value,
      style = ClawTheme.type.body.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
      color = ClawTheme.colors.text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Text(text = caption, style = ClawTheme.type.caption.copy(fontSize = 12.sp), color = ClawTheme.colors.textMuted)
  }
}

/** External project links; static first-party URLs plus the fork's own repository. */
private data class AboutLink(
  val title: String,
  val subtitle: String,
  val url: String,
)

private val aboutLinks =
  listOf(
    AboutLink("Website", "openclaw.ai", "https://openclaw.ai"),
    AboutLink("Docs", "docs.openclaw.ai", "https://docs.openclaw.ai"),
    AboutLink("GitHub", "github.com/openclaw/openclaw", "https://github.com/openclaw/openclaw"),
    AboutLink("OpenClaw-X 仓库", "github.com/chenxi0yu/openclaw-x", "https://github.com/chenxi0yu/openclaw-x"),
    AboutLink("Discord", "discord.gg/clawd", "https://discord.gg/clawd"),
  )

@Composable
private fun LicensesSettingsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val licenses = remember(context) { loadAndroidLicenseNotices(context.assets) }
  var selectedLicense by remember { mutableStateOf<AndroidLicenseNotice?>(null) }
  val backToListOrSettings = {
    if (selectedLicense == null) {
      onBack()
    } else {
      selectedLicense = null
    }
  }

  BackHandler(enabled = selectedLicense != null) {
    selectedLicense = null
  }

  SettingsDetailFrame(
    title = nativeString("Licenses"),
    subtitle = if (selectedLicense == null) nativeString("OpenClaw appreciates its partners in the open-source community.") else "",
    subtitleTextAlign = TextAlign.Center,
    icon = Icons.Default.Info,
    onBack = backToListOrSettings,
  ) {
    val selected = selectedLicense
    if (selected == null) {
      if (licenses.isEmpty()) {
        ClawPanel {
          Text(text = nativeString("No license notices are packaged in this build."), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
        }
      } else {
        ClawListPanel(items = licenses) { license ->
          LicenseListRow(license = license, onClick = { selectedLicense = license })
        }
      }
    } else {
      ClawPanel {
        Text(text = selected.text, style = ClawTheme.type.caption.copy(fontFamily = FontFamily.Monospace), color = ClawTheme.colors.textMuted)
      }
    }
  }
}

@Composable
private fun LicenseListRow(
  license: AndroidLicenseNotice,
  onClick: () -> Unit,
) {
  ClawListItem(
    title = license.title,
    onClick = onClick,
    trailing = {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = nativeString("Open \${license.title}", license.title),
        modifier = Modifier.size(20.dp),
        tint = ClawTheme.colors.text,
      )
    },
  )
}

internal fun aboutLinkTitle(title: String): String =
  when (title) {
    "Website" -> nativeString("Website")
    "Docs" -> nativeString("Docs")
    else -> title
  }

internal fun resolvedBackgroundPermissionLabel(platformLabel: String): String = platformLabel.trim().ifEmpty { nativeString("Allow all the time") }

internal fun gatewaySettingsSetupResetConfirmationText(): String =
  nativeString(
    "Replacing the setup code clears this phone's saved setup credentials and device tokens before reconnecting. This phone may need node capability approval again; continue only when you mean to pair with a fresh gateway setup code.",
  )

@Composable
internal fun SettingsRefreshControls(
  connected: Boolean,
  refreshing: Boolean,
  errorText: NativeText?,
  onRefresh: () -> Unit,
  label: String = nativeString("Refresh"),
) {
  ClawSecondaryButton(
    text = if (refreshing) nativeString("Refreshing") else label,
    onClick = onRefresh,
    enabled = connected && !refreshing,
    modifier = Modifier.fillMaxWidth(),
  )
  errorText?.let { error ->
    ClawPanel {
      Text(text = error.resolveNativeTextResource(), style = ClawTheme.type.body, color = ClawTheme.colors.warning)
    }
  }
}

@Composable
internal fun <T> SettingsSummaryContent(
  state: GatewaySummaryState<T>,
  connected: Boolean,
  disconnectedText: String,
  content: @Composable (T) -> Unit,
) {
  val summary = state.summary
  when {
    !connected -> {
      ClawPanel {
        Text(text = disconnectedText, style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
      }
    }

    summary != null -> {
      content(summary)
    }

    !state.refreshing && state.errorText == null -> {
      ClawPanel {
        Text(text = nativeString("Load from gateway"), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
      }
    }
  }
}

@Composable
internal fun SettingsDetailFrame(
  title: String,
  subtitle: String,
  icon: ImageVector,
  onBack: () -> Unit,
  subtitleTextAlign: TextAlign = TextAlign.Start,
  trailingAction: (@Composable () -> Unit)? = null,
  content: @Composable () -> Unit,
) {
  ClawScaffold(
    contentPadding =
      PaddingValues(
        start = ClawTheme.spacing.sm,
        top = ClawTheme.spacing.xxs,
        end = ClawTheme.spacing.sm,
        bottom = ClawTheme.spacing.xxxs,
      ),
  ) {
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs), contentPadding = PaddingValues(bottom = ClawTheme.spacing.xxxs)) {
      item {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
          ClawPlainIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = nativeString("Back"),
            onClick = onBack,
          )
          Text(text = title, style = ClawTheme.type.title, color = ClawTheme.colors.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
          trailingAction?.invoke()
          SettingsIconMark(icon = icon)
        }
      }
      if (subtitle.isNotBlank()) {
        item {
          Text(
            text = subtitle,
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = subtitleTextAlign,
          )
        }
      }
      item {
        Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xs)) {
          content()
        }
      }
    }
  }
}

/**
 * Toggle row model reused by settings sections that render simple on/off controls.
 */
internal data class SettingsToggleRow(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val checked: Boolean,
  val onCheckedChange: (Boolean) -> Unit,
  val enabled: Boolean = true,
)

/**
 * Compact metric row model for connected gateway summaries.
 */
internal data class SettingsMetric(
  val title: String,
  val value: String,
  val copyable: Boolean = false,
)

@Composable
private fun ExecApprovalsPanel(
  approvals: List<GatewayExecApprovalSummary>,
  onResolve: (String, String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
    approvals.forEach { approval ->
      ExecApprovalCard(
        approval = approval,
        onResolve = onResolve,
      )
    }
  }
}

@Composable
private fun ExecApprovalCard(
  approval: GatewayExecApprovalSummary,
  onResolve: (String, String) -> Unit,
) {
  val resolving = approval.resolvingDecision != null
  ClawPanel {
    Column(verticalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
      Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
          Text(text = nativeString("Command approval"), style = ClawTheme.type.section, color = ClawTheme.colors.text)
          approval.commandPreview?.let { preview ->
            Text(text = preview, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
          }
        }
        ClawStatusPill(text = if (resolving) nativeString("Sending") else nativeString("Review"), status = if (resolving) ClawStatus.Warning else ClawStatus.Success)
      }
      ExecApprovalCommandReview(approval.commandText.resolveNativeTextResource())
      approval.warningText?.let { warningText ->
        Text(text = warningText, style = ClawTheme.type.body, color = ClawTheme.colors.warning)
      }
      Text(text = execApprovalMetadata(approval), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
      approval.errorText?.let { errorText ->
        Text(text = gatewayExecApprovalTextForDisplay(errorText), style = ClawTheme.type.caption, color = ClawTheme.colors.warning)
      }
      Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        execApprovalActions(approval.allowedDecisions).forEach { action ->
          if (action.decision == "allow-once") {
            ClawPrimaryButton(
              text = action.label,
              onClick = { onResolve(approval.id, action.decision) },
              enabled = !resolving,
              modifier = Modifier.fillMaxWidth(),
            )
          } else {
            ClawSecondaryButton(
              text = action.label,
              onClick = { onResolve(approval.id, action.decision) },
              enabled = !resolving,
              modifier = Modifier.fillMaxWidth(),
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ExecApprovalCommandReview(commandText: String) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(8.dp),
    color = ClawTheme.colors.surfacePressed,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
  ) {
    SelectionContainer {
      Text(
        text = commandText,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        style = ClawTheme.type.body.copy(fontFamily = FontFamily.Monospace),
        color = ClawTheme.colors.text,
      )
    }
  }
}

internal data class ExecApprovalAction(
  val decision: String,
  val label: String,
)

internal fun execApprovalActions(allowedDecisions: List<String>): List<ExecApprovalAction> =
  allowedDecisions.mapNotNull { decision ->
    when (decision) {
      "allow-once" -> ExecApprovalAction(decision, nativeString("Allow Once"))
      "allow-always" -> ExecApprovalAction(decision, nativeString("Allow Always"))
      "deny" -> ExecApprovalAction(decision, nativeString("Deny"))
      else -> null
    }
  }

@Composable
private fun ExecApprovalNotice(
  notice: GatewayExecApprovalNotice,
  onDismiss: () -> Unit,
) {
  ClawPanel {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs)) {
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = gatewayExecApprovalTextForDisplay(notice.message),
          style = ClawTheme.type.body,
          color = if (notice.warning) ClawTheme.colors.warning else ClawTheme.colors.success,
        )
        // The retired card is gone by the time this renders; keep the id association
        // so the outcome stays attributable while other approval cards remain visible.
        Text(
          text = nativeString("Approval \${notice.approvalId}", notice.approvalId),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      ClawPlainIconButton(icon = Icons.Default.Close, contentDescription = nativeString("Dismiss approval notice"), onClick = onDismiss)
    }
  }
}

@Composable
private fun SessionToolCallsPanel(toolCalls: List<ChatPendingToolCall>) {
  ClawListPanel(items = toolCalls) { toolCall ->
    ApprovalListRow(toolCall = toolCall)
  }
}

@Composable
private fun ApprovalListRow(toolCall: ChatPendingToolCall) {
  val hasIssue = toolCall.isError == true
  ClawListItem(
    title = approvalActionName(toolCall.name),
    subtitle = approvalSubtitle(toolCall, hasIssue),
    leading = { ClawIconBadge(icon = Icons.Default.Lock) },
    trailing = { ClawStatusPill(text = if (hasIssue) nativeString("Issue") else nativeString("Review"), status = if (hasIssue) ClawStatus.Warning else ClawStatus.Success) },
  )
}

@Composable
private fun CronJobsPanel(
  jobs: List<GatewayCronJobSummary>,
  onJobClick: (GatewayCronJobSummary) -> Unit,
) {
  ClawListPanel(items = jobs) { job ->
    CronJobListRow(job = job, onClick = { onJobClick(job) })
  }
}

@Composable
private fun UsageProvidersPanel(providers: List<GatewayUsageProviderSummary>) {
  ClawListPanel(items = providers) { provider ->
    UsageProviderListRow(provider = provider)
  }
}

internal fun usageRefreshVisible(
  requestRefreshing: Boolean,
  summaryRefreshing: Boolean,
): Boolean = requestRefreshing || summaryRefreshing

@Composable
private fun UsageProviderListRow(provider: GatewayUsageProviderSummary) {
  val hasIssue = provider.error != null
  ClawListItem(
    title = provider.displayName,
    subtitle = usageProviderSubtitle(provider),
    leading = { ClawTextBadge(text = provider.displayName.uppercaseFirstGraphemeOrNull() ?: "U") },
    trailing = { ClawStatusPill(text = if (hasIssue) nativeString("Issue") else "OK", status = if (hasIssue) ClawStatus.Warning else ClawStatus.Success) },
  )
}

@Composable
private fun CronJobListRow(
  job: GatewayCronJobSummary,
  onClick: () -> Unit,
) {
  ClawListItem(
    title = job.name,
    subtitle = cronJobSubtitle(job),
    modifier = Modifier.clickable(onClickLabel = nativeString("Open automation detail"), onClick = onClick),
    leading = { ClawIconBadge(icon = Icons.Default.Bolt) },
    trailing = {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ClawStatusPill(text = cronJobStatusText(job), status = cronJobStatus(job))
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(17.dp), tint = ClawTheme.colors.textSubtle)
      }
    },
  )
}

@Composable
private fun CronJobDetailPanel(
  job: GatewayCronJobDetail,
  editorDraft: CronEditorDraftState,
  onEditorDraftChange: (CronEditorDraftState) -> Unit,
  historyState: GatewayCronRunHistoryState,
  actionState: GatewayCronActionState,
  runPending: Boolean,
  operatorAdminScopeAvailable: Boolean,
  onRun: () -> Unit,
  onToggleEnabled: () -> Unit,
  onSave: (GatewayCronJobEdit) -> Unit,
  onRefreshHistory: () -> Unit,
  onDelete: () -> Unit,
) {
  CronJobManagementPanel(
    job = job,
    editorDraft = editorDraft,
    onEditorDraftChange = onEditorDraftChange,
    historyState = historyState,
    actionState = actionState,
    runPending = runPending,
    operatorAdminScopeAvailable = operatorAdminScopeAvailable,
    onRun = onRun,
    onToggleEnabled = onToggleEnabled,
    onSave = onSave,
    onRefreshHistory = onRefreshHistory,
    onDelete = onDelete,
  )
  SettingsMetricPanel(
    rows =
      listOf(
        SettingsMetric(nativeString("Status"), if (job.enabled) nativeString("Enabled") else nativeString("Off")),
        SettingsMetric(nativeString("Schedule"), job.scheduleLabel.resolveNativeTextResource()),
        SettingsMetric(nativeString("Next Wake"), formatCronWake(job.nextRunAtMs)),
        SettingsMetric(nativeString("Last Run"), formatCronTimestamp(job.lastRunAtMs)),
      ),
  )
  CronJobFieldsPanel(
    rows =
      listOf(
        SettingsMetric("ID", job.id, copyable = true),
        SettingsMetric(nativeString("Description"), job.description.ifBlank { nativeString("None") }),
        SettingsMetric(nativeString("Schedule Detail"), job.scheduleDetail.resolveNativeTextResource()),
        SettingsMetric(nativeString("Session Target"), cronSessionTargetLabel(job.sessionTarget)),
        SettingsMetric(nativeString("Wake Mode"), cronWakeModeLabel(job.wakeMode)),
        SettingsMetric(nativeString("Delete After Run"), if (job.deleteAfterRun) nativeString("Yes") else nativeString("No")),
        SettingsMetric(nativeString("Payload"), job.payloadLabel.resolveNativeTextResource()),
        SettingsMetric(nativeString("Delivery"), job.deliveryLabel.resolveNativeTextResource()),
        SettingsMetric(nativeString("Failure Alert"), job.failureAlertLabel.resolveNativeTextResource()),
        SettingsMetric(nativeString("Created"), formatCronTimestamp(job.createdAtMs)),
        SettingsMetric(nativeString("Updated"), formatCronTimestamp(job.updatedAtMs)),
        SettingsMetric(nativeString("Running Since"), formatCronTimestamp(job.runningAtMs)),
        SettingsMetric(nativeString("Last Status"), cronJobStatusText(job)),
        SettingsMetric(
          nativeString("Last Duration"),
          job.lastDurationMs?.let { durationMs -> nativeString("\${durationMs}ms", durationMs) } ?: nativeString("None"),
        ),
        SettingsMetric(nativeString("Consecutive Errors"), job.consecutiveErrors?.toString() ?: "0"),
        SettingsMetric(nativeString("Consecutive Skips"), job.consecutiveSkipped?.toString() ?: "0"),
        SettingsMetric(nativeString("Delivery Status"), cronJobDeliveryStatusText(job.lastDeliveryStatus)),
      ),
  )
  job.payloadText?.let { text ->
    CronJobTextPanel(title = cronPayloadTextTitle(job), text = text)
  }
  job.lastError?.let { text ->
    CronJobTextPanel(title = nativeString("Last Error"), text = text, warning = true)
  }
  job.lastDeliveryError?.let { text ->
    CronJobTextPanel(title = nativeString("Delivery Error"), text = text, warning = true)
  }
}

internal fun cronJobDeliveryStatusText(status: String?): String = status?.let(::cronDeliveryStatusLabel) ?: nativeString("None")

@Composable
private fun CronJobFieldsPanel(rows: List<SettingsMetric>) {
  val context = LocalContext.current
  ClawPanel(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
    ClawSeparatedColumn(items = rows) { row ->
      val rowModifier =
        if (row.copyable) {
          Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clickable(onClickLabel = nativeString("Copy \${row.title}", row.title)) { copySettingsDetailValue(context, row.title, row.value) }
            .padding(vertical = 6.dp)
        } else {
          Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .padding(vertical = 6.dp)
        }
      Row(modifier = rowModifier, horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs), verticalAlignment = Alignment.Top) {
        Text(text = row.title, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.weight(0.42f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(modifier = Modifier.weight(0.58f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = row.value,
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.text,
              modifier = Modifier.weight(1f),
              maxLines = 3,
              overflow = TextOverflow.Ellipsis,
            )
            if (row.copyable) {
              Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ClawTheme.colors.text)
            }
          }
          if (row.copyable) {
            Text(text = nativeString("Tap to copy"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
          }
        }
      }
    }
  }
}

private fun copySettingsDetailValue(
  context: Context,
  title: String,
  value: String,
) {
  val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw $title", value))
  Toast.makeText(context, nativeString("\$title copied", title), Toast.LENGTH_SHORT).show()
}

@Composable
private fun CronJobTextPanel(
  title: String,
  text: String,
  warning: Boolean = false,
) {
  ClawPanel {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(text = title, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      Text(
        text = text,
        style = ClawTheme.type.body,
        color = if (warning) ClawTheme.colors.warning else ClawTheme.colors.text,
      )
    }
  }
}

@Composable
private fun AgentsPanel(
  agents: List<GatewayAgentSummary>,
  defaultAgentId: String?,
) {
  ClawListPanel(items = agents) { agent ->
    AgentListRow(agent = agent, isDefault = agent.id == defaultAgentId)
  }
}

@Composable
private fun AgentListRow(
  agent: GatewayAgentSummary,
  isDefault: Boolean,
) {
  ClawListItem(
    title = agent.name?.takeIf { it.isNotBlank() } ?: agent.id,
    subtitle = if (isDefault) nativeString("Default assistant") else nativeString("Ready"),
    leading = {
      ClawAgentAvatar(source = agentAvatarSource(agent), size = 30.dp) {
        ClawTextBadge(text = agentBadge(agent))
      }
    },
    trailing = { ClawStatusPill(text = if (isDefault) nativeString("Default") else nativeString("Ready"), status = ClawStatus.Success) },
  )
}

/** First-run pairing state: the hero scan CTA shows until a gateway is paired. */
internal fun gatewayShowsScanHero(pairedGatewayCount: Int): Boolean = pairedGatewayCount == 0

/** Discovered rows show the endpoint target so users can tell twins apart before connecting. */
internal fun gatewayDiscoveredRowSubtitle(endpoint: GatewayEndpoint): String = "${endpoint.host}:${endpoint.port}"

/**
 * Chooses a display name for the configured default agent, falling back to any available agent.
 */
private fun defaultAgentName(
  agents: List<GatewayAgentSummary>,
  defaultAgentId: String?,
): String {
  val defaultId = defaultAgentId?.trim().orEmpty()
  val agent = agents.firstOrNull { it.id == defaultId } ?: agents.firstOrNull()
  return agent?.name?.takeIf { it.isNotBlank() } ?: agent?.id ?: nativeString("None")
}

/**
 * Builds a short stable badge from agent emoji/name/id for dense lists.
 */
private fun agentBadge(agent: GatewayAgentSummary): String {
  agent.emoji
    ?.trim()
    ?.takeIf { it.isNotEmpty() }
    ?.let { return it }
  val source = agent.name?.takeIf { it.isNotBlank() } ?: agent.id
  return source
    .split(' ', '-', '_')
    .filter { it.isNotBlank() }
    .take(2)
    .mapNotNull { it.uppercaseFirstGraphemeOrNull() }
    .joinToString("")
    .ifBlank { "A" }
}

/**
 * Normalizes tool-call names into readable approval action labels.
 */
internal fun approvalActionName(name: String): String {
  val cleaned =
    name
      .replace('.', ' ')
      .replace('_', ' ')
      .replace('-', ' ')
      .trim()
  return cleaned
    .split(' ')
    .filter { it.isNotBlank() }
    .joinToString(" ") { word -> word.replaceFirstChar { it.uppercaseChar() } }
    .ifBlank { nativeString("Action Request") }
}

/** Builds approval row age/error copy without exposing raw tool arguments. */
private fun approvalSubtitle(
  toolCall: ChatPendingToolCall,
  hasIssue: Boolean,
): String {
  if (hasIssue) return nativeString("Needs attention")
  val ageMs = (System.currentTimeMillis() - toolCall.startedAtMs).coerceAtLeast(0L)
  val minutes = ageMs / 60_000L
  return if (minutes < 1) nativeString("Waiting for review") else nativeString("Waiting \${minutes}m", minutes)
}

internal fun execApprovalMetadata(
  approval: GatewayExecApprovalSummary,
  nowMs: Long = System.currentTimeMillis(),
): String {
  val target =
    when {
      approval.host == "node" && approval.nodeId != null -> {
        val nodeId = approval.nodeId.take(8)
        nativeString("Node \${nodeId}", nodeId)
      }

      approval.host == "node" -> {
        nativeString("Node")
      }

      approval.host == "gateway" -> {
        nativeString("Gateway")
      }

      approval.host != null -> {
        approval.host
      }

      else -> {
        nativeString("Gateway")
      }
    }
  val agent =
    approval.agentId?.let {
      val agentId = it.take(8)
      nativeString("Agent \${agentId}", agentId)
    }
  val age =
    approval.createdAtMs?.let {
      val duration = formatApprovalDuration(nowMs - it)
      nativeString("Waiting \${duration}", duration)
    }
  val expires =
    approval.expiresAtMs?.let {
      val duration = formatApprovalDuration(it - nowMs)
      nativeString("Expires \${duration}", duration)
    }
  return listOfNotNull(target, agent, age, expires).joinToString(" · ")
}

internal fun formatApprovalDuration(deltaMs: Long): String {
  val safeDelta = deltaMs.coerceAtLeast(0L)
  val minutes = safeDelta / 60_000L
  val hours = minutes / 60L
  return when {
    minutes < 1 -> nativeString("soon")
    hours < 1 -> nativeString("\${minutes}m", minutes)
    else -> nativeString("\${hours}h", hours)
  }
}

internal fun cronSessionTargetLabel(target: String): String =
  when (target) {
    "main" -> nativeString("Main")
    "isolated" -> nativeString("Isolated")
    "current" -> nativeString("Current")
    else -> target
  }

/** Builds the dense cron-job subtitle from schedule, next wake, and prompt preview. */
private fun cronJobSubtitle(job: GatewayCronJobSummary): String =
  nativeString(
    "\${job.scheduleLabel} · \${formatCronWake(job.nextRunAtMs)} · \${job.promptPreview}",
    job.scheduleLabel.resolveNativeText(),
    formatCronWake(job.nextRunAtMs),
    job.promptPreview.resolveNativeText(),
  )

internal enum class CronJobsListFilter {
  All,
  Active,
  Paused,
  ;

  val label: String
    get() =
      when (this) {
        All -> nativeString("All")
        Active -> nativeString("Active")
        Paused -> nativeString("Paused")
      }
}

internal fun filterCronJobs(
  jobs: List<GatewayCronJobSummary>,
  rawQuery: String,
  filter: CronJobsListFilter,
): List<GatewayCronJobSummary> {
  val query = rawQuery.trim()
  return jobs.filter { job ->
    val statusMatches =
      when (filter) {
        CronJobsListFilter.All -> true
        CronJobsListFilter.Active -> job.enabled
        CronJobsListFilter.Paused -> !job.enabled
      }
    statusMatches &&
      (
        query.isEmpty() ||
          listOf(job.name, job.scheduleLabel.resolveNativeText(), job.promptPreview.resolveNativeText())
            .any { it.contains(query, ignoreCase = true) }
      )
  }
}

/** Summarizes a provider plan and most-used quota window for usage rows. */
internal fun usageProviderSubtitle(provider: GatewayUsageProviderSummary): String {
  provider.error?.let { return it }
  val window = provider.windows.maxByOrNull { it.usedPercent }
  val quota =
    window?.let {
      val remaining = (100.0 - it.usedPercent).coerceIn(0.0, 100.0).toInt()
      nativeString("\${remaining}% left \${label}", remaining, it.label)
    }
  return listOfNotNull(provider.plan, quota).joinToString(" · ").ifBlank {
    nativeString("No limits reported")
  }
}

/**
 * Converts usage timestamps into short relative labels for metric panels.
 */
internal fun formatUsageUpdated(
  updatedAtMs: Long?,
  nowMs: Long = System.currentTimeMillis(),
): String {
  val updated = updatedAtMs ?: return nativeString("Never")
  val deltaMs = (nowMs - updated).coerceAtLeast(0L)
  val minutes = deltaMs / 60_000L
  val hours = minutes / 60L
  return when {
    minutes < 1 -> {
      nativeString("Now")
    }

    hours < 1 -> {
      nativeString("\${minutes}m", minutes)
    }

    hours < 24 -> {
      nativeString("\${hours}h", hours)
    }

    else -> {
      val days = hours / 24L
      nativeString("\${days}d", days)
    }
  }
}

/** Converts gateway cron status text into the short row badge label. */
private fun cronJobStatusText(job: GatewayCronJobSummary): String {
  if (!job.enabled) return nativeString("Off")
  return when (job.lastRunStatus?.lowercase()) {
    "error" -> nativeString("Issue")
    "ok" -> "OK"
    "skipped" -> nativeString("Skipped")
    else -> nativeString("Ready")
  }
}

private fun cronJobStatusText(job: GatewayCronJobDetail): String {
  if (!job.enabled) return nativeString("Off")
  return when (job.lastRunStatus?.lowercase()) {
    "error" -> nativeString("Issue")
    "ok" -> "OK"
    "skipped" -> nativeString("Skipped")
    else -> nativeString("Ready")
  }
}

/** Maps gateway cron status text to app status colors. */
private fun cronJobStatus(job: GatewayCronJobSummary): ClawStatus {
  if (!job.enabled) return ClawStatus.Neutral
  return when (job.lastRunStatus?.lowercase()) {
    "error" -> ClawStatus.Danger
    "skipped" -> ClawStatus.Warning
    else -> ClawStatus.Success
  }
}

private fun cronPayloadTextTitle(job: GatewayCronJobDetail): String =
  when (job.payloadKind) {
    "systemEvent" -> nativeString("System Event Text")
    "agentTurn" -> nativeString("Agent Prompt")
    "command" -> nativeString("Command")
    "script" -> nativeString("Script")
    else -> nativeString("Payload Text")
  }

/** Applies query/system visibility rules while always preserving selected packages. */
internal fun filterNotificationAppsForPicker(
  apps: List<InstalledApp>,
  selectedPackages: Set<String>,
  query: String,
  showSystemApps: Boolean,
): List<InstalledApp> {
  val normalizedQuery = query.trim().lowercase()
  return apps.filter { app ->
    val selected = app.packageName in selectedPackages
    val visibleByType = showSystemApps || !app.isSystemApp || selected
    val visibleBySearch =
      normalizedQuery.isEmpty() ||
        app.label.lowercase().contains(normalizedQuery) ||
        app.packageName.lowercase().contains(normalizedQuery)
    visibleByType && visibleBySearch
  }
}

/** Summarizes allowlist/blocklist mode with an empty-state warning when needed. */
private fun notificationPackageSelectionSummary(
  mode: NotificationPackageFilterMode,
  selectedCount: Int,
): String =
  when (mode) {
    NotificationPackageFilterMode.Allowlist -> {
      if (selectedCount == 0) {
        nativeString("No apps selected. Nothing forwards until you add apps.")
      } else if (selectedCount == 1) {
        nativeString("\$selectedCount app allowed to forward.", selectedCount)
      } else {
        nativeString("\$selectedCount apps allowed to forward.", selectedCount)
      }
    }

    NotificationPackageFilterMode.Blocklist -> {
      if (selectedCount == 0) {
        nativeString("No apps blocked. Apps can forward unless you add blocks.")
      } else if (selectedCount == 1) {
        nativeString("\$selectedCount app blocked from forwarding.", selectedCount)
      } else {
        nativeString("\$selectedCount apps blocked from forwarding.", selectedCount)
      }
    }
  }

/** Builds compact two-letter app badges from package-picker labels. */
private fun notificationAppBadge(label: String): String {
  val initials =
    label
      .split(' ', '-', '_', '.')
      .asSequence()
      .filter { it.isNotBlank() }
      .take(2)
      .mapNotNull { it.uppercaseFirstGraphemeOrNull() }
      .joinToString("")
  return initials.ifBlank { "A" }
}

/**
 * Converts cron wake times into short relative labels for scheduled-work rows.
 */
internal fun formatCronWake(
  timeMs: Long?,
  nowMs: Long = System.currentTimeMillis(),
): String {
  val target = timeMs ?: return nativeString("None")
  val deltaMs = target - nowMs
  if (deltaMs <= 0) return nativeString("Due")
  val minutes = deltaMs / 60_000L
  val hours = minutes / 60L
  val days = hours / 24L
  return when {
    days > 0 -> nativeString("\${days}d", days)
    hours > 0 -> nativeString("\${hours}h", hours)
    minutes > 0 -> nativeString("\${minutes}m", minutes)
    else -> nativeString("Soon")
  }
}

internal fun formatCronTimestamp(timeMs: Long?): String {
  val value = timeMs ?: return nativeString("None")
  return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
}

@Composable
internal fun SettingsTogglePanel(rows: List<SettingsToggleRow>) {
  ClawPanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
    ClawSeparatedColumn(items = rows) { row ->
      SettingsToggleListRow(row)
    }
  }
}

@Composable
private fun SettingsToggleListRow(row: SettingsToggleRow) {
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(enabled = row.enabled) { row.onCheckedChange(!row.checked) }
        .padding(horizontal = 10.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(ClawTheme.spacing.xxs),
  ) {
    Icon(imageVector = row.icon, contentDescription = null, modifier = Modifier.size(19.dp), tint = ClawTheme.colors.text)
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
      Text(text = row.title, style = ClawTheme.type.body, color = ClawTheme.colors.text, maxLines = 1)
      Text(text = row.subtitle, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
    Switch(checked = row.checked, onCheckedChange = row.onCheckedChange, enabled = row.enabled)
  }
}

/** These diagnostics have no detail view, so their rows must show complete values. */
@Composable
internal fun SettingsMetricPanel(rows: List<SettingsMetric>) {
  val context = LocalContext.current
  ClawListPanel(items = rows) { row ->
    val rowModifier =
      if (row.copyable) {
        Modifier.clickable(onClickLabel = nativeString("Copy \${row.title}", row.title)) {
          copySettingsDetailValue(context, row.title, row.value)
        }
      } else {
        Modifier
      }
    ClawListItem(
      title = row.title,
      subtitle = row.value,
      metadata = nativeString("Tap to copy").takeIf { row.copyable },
      modifier = rowModifier,
      trailing =
        if (row.copyable) {
          {
            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp), tint = ClawTheme.colors.text)
          }
        } else {
          null
        },
    )
  }
}

@Composable
private fun SettingsIconMark(icon: ImageVector) {
  Surface(
    modifier = Modifier.size(30.dp),
    shape = CircleShape,
    color = ClawTheme.colors.surfaceRaised,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
    contentColor = ClawTheme.colors.text,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(15.dp))
    }
  }
}

/**
 * Checks an exact Android runtime permission for settings enablement.
 */
private fun hasPermission(
  context: Context,
  permission: String,
): Boolean = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

/** Returns true when either fine or coarse location is available to settings callers. */
private fun hasLocationPermission(context: Context): Boolean =
  hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
    hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun hasBackgroundLocationPermission(context: Context): Boolean = hasPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

private fun openNotificationListenerSettings(context: Context) {
  val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(intent)
}

private fun openAppPermissionSettings(context: Context) {
  val intent =
    Intent(
      Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
      Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  context.startActivity(intent)
}
