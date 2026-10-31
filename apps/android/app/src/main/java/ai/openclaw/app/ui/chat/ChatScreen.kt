package ai.openclaw.app.ui.chat

import ai.openclaw.app.ChatDraft
import ai.openclaw.app.ChatDraftPlacement
import ai.openclaw.app.ComposerBarStyle
import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.GatewayModelSummary
import ai.openclaw.app.GatewayModelUnavailableReason
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.PendingAssistantAutoSend
import ai.openclaw.app.R
import ai.openclaw.app.SHARED_AUDIO_DOCUMENT_MIME_TYPES
import ai.openclaw.app.SHARED_VIDEO_MIME_TYPES
import ai.openclaw.app.SessionCatalog
import ai.openclaw.app.SessionShareOutcome
import ai.openclaw.app.chat.ChatCommandEntry
import ai.openclaw.app.chat.ChatComposerOwner
import ai.openclaw.app.chat.ChatController
import ai.openclaw.app.chat.ChatDiffStat
import ai.openclaw.app.chat.ChatFastMode
import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.chat.ChatMessageCost
import ai.openclaw.app.chat.ChatMessageUsage
import ai.openclaw.app.chat.ChatOutboxItem
import ai.openclaw.app.chat.ChatOutboxStatus
import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.chat.ChatPermissionMode
import ai.openclaw.app.chat.ChatPlanStepStatus
import ai.openclaw.app.chat.ChatProgressCard
import ai.openclaw.app.chat.ChatQuestionDraft
import ai.openclaw.app.chat.ChatQuestionPrompt
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.chat.ChatSubagentActivity
import ai.openclaw.app.chat.ChatThinkingLevelOption
import ai.openclaw.app.chat.ChatThinkingLevelSelection
import ai.openclaw.app.chat.ChatToolActivity
import ai.openclaw.app.chat.ChatTranscriptAnchorState
import ai.openclaw.app.chat.ChatWidgetResource
import ai.openclaw.app.chat.MessageSpeechPhase
import ai.openclaw.app.chat.MessageSpeechState
import ai.openclaw.app.chat.SessionBranch
import ai.openclaw.app.chat.VoiceNoteRecorderState
import ai.openclaw.app.chat.chatOutboxQueueFailureText
import ai.openclaw.app.chat.isTranscriptOnlyOpenClawAssistant
import ai.openclaw.app.chat.questionsForSession
import ai.openclaw.app.chat.resolveChatComposerOwner
import ai.openclaw.app.chat.resolveGatewayDefaultAgentId
import ai.openclaw.app.currentAppLanguage
import ai.openclaw.app.gateway.GatewayLoadedImage
import ai.openclaw.app.gateway.GatewayLoadedMedia
import ai.openclaw.app.gateway.GatewayMediaKind
import ai.openclaw.app.i18n.NativeText
import ai.openclaw.app.i18n.joinedNativeText
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.i18n.nativeText
import ai.openclaw.app.i18n.resolveNativeTextResource
import ai.openclaw.app.sessionShareFailureText
import ai.openclaw.app.i18n.verbatimText
import ai.openclaw.app.operatorScopesAllowAdmin
import ai.openclaw.app.operatorScopesAllowWrite
import ai.openclaw.app.resolveAgentIdFromMainSessionKey
import ai.openclaw.app.ui.TabletopPaneBounds
import ai.openclaw.app.ui.copyGatewayDiagnosticsReport
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawCapsuleIconButton
import ai.openclaw.app.ui.design.ClawCircularButtonDefaults
import ai.openclaw.app.ui.design.ClawCircularIconButton
import ai.openclaw.app.ui.design.ClawGlassMenu
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawMorphMenuDefaults
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawMorphMenuState
import ai.openclaw.app.ui.design.LocalClawMorphMenuHost
import ai.openclaw.app.ui.design.rememberClawMorphMenuState
import ai.openclaw.app.ui.design.ClawGlassSurface
import ai.openclaw.app.ui.design.ClawJumpToLatestButton
import ai.openclaw.app.ui.design.ClawListItem
import ai.openclaw.app.ui.design.LucideCameraIcon
import ai.openclaw.app.ui.design.LucideImageIcon
import ai.openclaw.app.ui.design.LucidePaperclipIcon
import ai.openclaw.app.ui.design.LucideVideoIcon
import ai.openclaw.app.ui.design.ClawLoadingState
import ai.openclaw.app.ui.design.ClawPanel
import ai.openclaw.app.ui.design.ClawPrimaryButton
import ai.openclaw.app.ui.design.ClawSecondaryButton
import ai.openclaw.app.ui.design.ClawStatus
import ai.openclaw.app.ui.design.ClawStatusPill
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.LocalClawHazeState
import ai.openclaw.app.ui.design.ProviderBrandIcon
import ai.openclaw.app.ui.design.agentAvatarSource
import ai.openclaw.app.ui.design.rememberClawHaptics
import ai.openclaw.app.ui.design.sessionColor
import ai.openclaw.app.ui.design.clawBackdropTopBar
import ai.openclaw.app.ui.design.ClawMorphTrigger
import ai.openclaw.app.ui.design.clawMorphTrigger
import ai.openclaw.app.ui.design.rememberClawMorphTrigger
import ai.openclaw.app.ui.design.rememberClawBackdrop
import ai.openclaw.app.ui.foldAwareSheet
import ai.openclaw.app.ui.gatewayDiagnosticsEndpoint
import ai.openclaw.app.ui.gatewayStatusForDisplay
import ai.openclaw.app.ui.localizedUppercase
import ai.openclaw.app.ui.relativeSessionTime
import ai.openclaw.app.ui.rememberSystemAnimationsEnabled
import ai.openclaw.app.ui.rememberWindowDisplayFeatureState
import ai.openclaw.app.ui.sessionPresentationTitle
import ai.openclaw.app.ui.sidebarCatalogHosts
import android.net.Uri
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GppMaybe
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.window.layout.DisplayFeature
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.icon.MiuixIcons
import java.io.File
import java.text.DateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.math.sin
import top.yukonga.miuix.kmp.icon.extended.Add as MiuixAdd
import top.yukonga.miuix.kmp.icon.extended.ChevronForward as MiuixChevronForward
import top.yukonga.miuix.kmp.icon.extended.Edit as MiuixEdit
import top.yukonga.miuix.kmp.icon.extended.Home as MiuixHome
import top.yukonga.miuix.kmp.icon.extended.More as MiuixMore
import top.yukonga.miuix.kmp.icon.extended.Refresh as MiuixRefresh
import top.yukonga.miuix.kmp.icon.extended.Timer as MiuixTimer

/** Returns a pending assistant prompt only when chat can accept it immediately. */
internal fun resolvePendingAssistantAutoSend(
  pending: PendingAssistantAutoSend?,
  currentOwner: ChatComposerOwner,
  healthOk: Boolean,
  pendingRunCount: Int,
): PendingAssistantAutoSend? {
  val queued = pending ?: return null
  if (queued.prompt.isBlank() || queued.owner != currentOwner) return null
  if (!healthOk || pendingRunCount > 0) return null
  return queued
}

internal enum class ChatComposerPrimaryAction {
  StartTalk,
  None,
  Preparing,
  Stop,
  Send,
}

/** New drafts can steer an active run; Talk retains its independent voice controls. */
internal fun resolveChatComposerPrimaryAction(
  talkActive: Boolean,
  runActive: Boolean,
  sendInFlight: Boolean = false,
  hasContent: Boolean,
  liveTalkVisible: Boolean = true,
): ChatComposerPrimaryAction =
  when {
    runActive -> ChatComposerPrimaryAction.Stop
    sendInFlight -> ChatComposerPrimaryAction.Preparing
    hasContent && !talkActive -> ChatComposerPrimaryAction.Send
    talkActive -> ChatComposerPrimaryAction.None
    // With live talk hidden the primary slot is the send arrow, disabled while
    // the composer is empty, instead of leaving an empty hole.
    liveTalkVisible -> ChatComposerPrimaryAction.StartTalk
    else -> ChatComposerPrimaryAction.Send
  }

internal object ChatUserMessageDisclosurePolicy {
  const val collapsedLineLimit = 12
  const val collapsedCharacterLimit = 700

  fun collapsedPreview(text: String): String? {
    var end = minOf(text.length, collapsedCharacterLimit)
    if (end in 1 until text.length && text[end - 1].isHighSurrogate() && text[end].isLowSurrogate()) {
      end -= 1
    }
    var lineCount = 1
    for (index in 0 until end) {
      if (text[index] != '\n') continue
      if (lineCount == collapsedLineLimit) {
        end = index
        break
      }
      lineCount += 1
    }
    if (end == text.length) return null
    return text.substring(0, end).trimEnd() + "…"
  }
}

internal fun shouldUseUserMessageDisclosure(
  isUser: Boolean,
  content: List<ChatMessageContent>,
): Boolean =
  isUser &&
    content.isNotEmpty() &&
    content.all { it.type == "text" } &&
    ChatUserMessageDisclosurePolicy.collapsedPreview(chatMessagePlainText(content)) != null

private class ChatBranchOpening(
  val session: ChatModelPickerSession,
  val selectionGeneration: Long,
)

/** Full chat surface that wires MainViewModel state to messages, attachments, voice, and composer actions. */
@Composable
internal fun ChatScreen(
  viewModel: MainViewModel,
  talkActive: Boolean,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onToggleTalk: () -> Unit,
  onOpenDashboard: (String) -> Unit,
  onOpenGatewaySettings: () -> Unit,
  onOpenProvidersModels: () -> Unit = onOpenGatewaySettings,
  tabletopPanes: TabletopPaneBounds? = null,
  features: List<DisplayFeature> = emptyList(),
  /** Lets the shell stop owning drawer gestures while a modal chat picker is open. */
  onSelectionModeChange: (Boolean) -> Unit = {},
  /** Lets the shell hand the left-edge swipe to the share card while it is open. */
  onSharePanelChange: (Boolean) -> Unit = {},
) {
  val messages by viewModel.chatMessages.collectAsState()
  val transcriptAnchor by viewModel.chatTranscriptAnchor.collectAsState()
  val historyLoading by viewModel.chatHistoryLoading.collectAsState()
  val sessionCreating by viewModel.chatSessionCreating.collectAsState()
  val errorText by viewModel.chatError.collectAsState()
  val pendingRunCount by viewModel.pendingRunCount.collectAsState()
  val selectedActiveRun by viewModel.chatSelectedActiveRunPresentation.collectAsState()
  val healthOk by viewModel.chatHealthOk.collectAsState()
  val gatewayConnectionDisplay by viewModel.gatewayConnectionDisplay.collectAsState()
  val operatorScopes by viewModel.operatorScopes.collectAsState()
  val permissionSettingsAvailable by viewModel.chatPermissionSettingsAvailable.collectAsState()
  val canWriteSessionSettings = operatorScopesAllowWrite(operatorScopes)
  val canAdminSessionSettings = operatorScopesAllowAdmin(operatorScopes)
  val activeGatewayStableId by viewModel.activeGatewayStableId.collectAsState()
  val sessionKey by viewModel.chatSessionKey.collectAsState()
  val selectionGeneration by viewModel.chatSelectionGeneration.collectAsState()
  val gatewayCatalogRevision by viewModel.gatewayCatalogRevision.collectAsState()
  val sessionOwnerAgentId by viewModel.chatSessionOwnerAgentId.collectAsState()
  val mainSessionKey by viewModel.mainSessionKey.collectAsState()
  val gatewayDefaultAgentId by viewModel.gatewayDefaultAgentId.collectAsState()
  val gatewayComposerDefaultAgentOwner by viewModel.gatewayComposerDefaultAgentOwner.collectAsState()
  val gatewayAgents by viewModel.gatewayAgents.collectAsState()
  val thinkingLevel by viewModel.chatThinkingLevel.collectAsState()
  val thinkingLevelSelection by viewModel.chatThinkingLevelSelection.collectAsState()
  val streamingAssistantText = rememberThrottledStreamText(viewModel.chatStreamingAssistantText)
  val streamingThinkingText = rememberThrottledStreamText(viewModel.chatStreamingThinkingText)
  var toolDetailSheet by remember { mutableStateOf<ToolActivityDetail?>(null) }
  val pendingToolCalls by viewModel.chatPendingToolCalls.collectAsState()
  val subagentActivities by viewModel.chatSubagentActivities.collectAsState()
  val questions by viewModel.chatQuestions.collectAsState()
  val progressCard by viewModel.chatProgressCard.collectAsState()
  val sessions by viewModel.chatSessions.collectAsState()
  val swarmGroups by viewModel.chatSwarmGroups.collectAsState()
  val sessionBranches by viewModel.chatSessionBranches.collectAsState()
  val sessionBranchesLoading by viewModel.chatSessionBranchesLoading.collectAsState()
  val sessionBranchSwitching by viewModel.chatSessionBranchSwitching.collectAsState()
  val chatCommands by viewModel.chatCommands.collectAsState()
  val chatDraft by viewModel.chatDraft.collectAsState()
  val chatShareDrafts by viewModel.chatShareDrafts.collectAsState()
  val pendingAssistantAutoSend by viewModel.pendingAssistantAutoSend.collectAsState()
  val assistantAutoSendInFlight by viewModel.assistantAutoSendInFlight.collectAsState()
  val remoteAddress by viewModel.remoteAddress.collectAsState()
  val outboxItems by viewModel.chatOutboxItems.collectAsState()
  val outboxPresentationRestored by viewModel.chatOutboxPresentationRestored.collectAsState()
  val messageSpeechState by viewModel.chatMessageSpeech.collectAsState()
  val manualHost by viewModel.manualHost.collectAsState()
  val manualPort by viewModel.manualPort.collectAsState()
  val manualTls by viewModel.manualTls.collectAsState()
  val modelCatalog by viewModel.chatModelCatalog.collectAsState()
  val modelFavorites by viewModel.modelFavorites.collectAsState()
  val modelRecents by viewModel.modelRecents.collectAsState()
  val selectedModelRef by viewModel.chatSelectedModelRef.collectAsState()
  val pendingSessionSettingsKeys by viewModel.chatPendingSessionSettingsKeys.collectAsState()
  val micEnabled by viewModel.micEnabled.collectAsState()
  val micIsListening by viewModel.micIsListening.collectAsState()
  val micCooldown by viewModel.micCooldown.collectAsState()
  val talkModeEnabled by viewModel.talkModeEnabled.collectAsState()
  val talkModeListening by viewModel.talkModeListening.collectAsState()
  val liveTalkVisible by viewModel.chatLiveTalkVisible.collectAsState()
  val voiceInputVisible by viewModel.chatVoiceInputVisible.collectAsState()
  val composerBarStyle by viewModel.composerBarStyle.collectAsState()
  // The floating simple bar rides the system gesture bar instead of a fixed band
  // of canvas, so nothing empty is left under it. Hiding the gesture bar costs
  // nothing: with no pill there is nothing to clear, and the pane's own 4dp gap
  // stays.
  val simpleComposerBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val inlineMediaPlaybackBlocked = messageSpeechState?.isActive == true || talkModeEnabled || talkModeListening
  val thinkingSupported =
    chatThinkingSupported(
      selection = thinkingLevelSelection,
      fallbackSupported = thinkingSupportedForSelection(selectedModelRef, modelCatalog),
    )
  val contextUsage = resolveChatContextUsage(sessionKey = sessionKey, mainSessionKey = mainSessionKey, sessions = sessions)
  val activeSession =
    sessions.firstOrNull {
      isActiveSessionChoice(
        choiceKey = it.key,
        sessionKey = sessionKey,
        mainSessionKey = mainSessionKey,
      )
    }
  val fastMode = (activeSession?.effectiveFastMode ?: activeSession?.fastMode ?: ChatFastMode.Off).isEnabled
  val modelSelectionLocked = activeSession?.modelSelectionLocked == true
  val permissionModePending = activeSession?.permissionModePending == true
  val sessionSettingsPending = sessionKey in pendingSessionSettingsKeys
  val fastModeProviderSupported =
    fastModeProviderSupportedForSelection(
      selectedModelRef = selectedModelRef,
      sessionModelProvider = activeSession?.modelProvider,
      catalog = modelCatalog,
    )
  val fastModeSupported =
    fastModeSupportedForSelection(
      providerSupported = fastModeProviderSupported,
      hasConfiguredFastModeOverride = activeSession?.fastMode != null,
    )
  val gatewayAddress = gatewayDiagnosticsEndpoint(remoteAddress = remoteAddress, manualHost = manualHost, manualPort = manualPort, manualTls = manualTls)
  val gatewayProblemMessage = gatewayConnectionDisplay.problem?.message?.takeIf { it.isNotBlank() }
  val offlineStatus = gatewayStatusForDisplay(gatewayProblemMessage ?: gatewayConnectionDisplay.statusText)
  val gatewayOffline = !gatewayConnectionDisplay.isConnected
  val gatewayRequiresImmediateOfflineNotice =
    gatewayConnectionDisplay.problem?.let { !shouldKeepGatewayOfflineNoticeInline(it.isNetworkFailure) } == true
  var showOfflineNotice by remember(activeGatewayStableId) { mutableStateOf(false) }
  LaunchedEffect(activeGatewayStableId, gatewayOffline, gatewayRequiresImmediateOfflineNotice) {
    if (!gatewayOffline) {
      showOfflineNotice = false
    } else if (gatewayRequiresImmediateOfflineNotice) {
      showOfflineNotice = true
    } else {
      showOfflineNotice = false
      delay(CHAT_OFFLINE_NOTICE_DELAY_MS)
      if (gatewayOffline) {
        showOfflineNotice = true
      }
    }
  }
  val gatewayConnecting = gatewayOffline && !showOfflineNotice && !gatewayRequiresImmediateOfflineNotice
  val effectiveGatewayDefaultAgentId =
    resolveGatewayDefaultAgentId(activeGatewayStableId, gatewayDefaultAgentId, gatewayComposerDefaultAgentOwner)
  val sessionAgentId = resolveAgentIdFromMainSessionKey(sessionKey) ?: sessionOwnerAgentId ?: effectiveGatewayDefaultAgentId ?: "main"
  val composerOwner =
    resolveChatComposerOwner(
      gatewayStableId = activeGatewayStableId,
      gatewayDefaultAgentId = sessionOwnerAgentId ?: gatewayDefaultAgentId,
      lastVerifiedOwner = if (sessionOwnerAgentId == null) gatewayComposerDefaultAgentOwner else null,
      sessionKey = sessionKey,
      mainSessionKey = mainSessionKey,
    )
  val currentSessionOutboxItems =
    outboxItemsForSession(
      items = outboxItems,
      sessionKey = sessionKey,
      mainSessionKey = mainSessionKey,
      ownerAgentId = composerOwner.agentId,
      messages = messages,
    )
  val activeAgentId = sessionAgentId
  val activeAgent = gatewayAgents.firstOrNull { it.id == activeAgentId }
  val headerCatalogState by viewModel.sessionCatalogState.collectAsState()
  // The startup draft has no session row of its own; read as a fresh conversation.
  val localNewChatDraft by viewModel.chatLocalNewChatDraft.collectAsState()
  val activeSessionTitle =
    if (localNewChatDraft) {
      nativeString("New chat")
    } else {
      chatHeaderSessionTitle(activeSession) { nativeString("New chat") }
    }
  val activeProjectLabel = chatHeaderProjectLabel(sessionKey = sessionKey, catalogs = headerCatalogState.catalogs)
  val workspaceGit = activeAgent?.workspaceGit == true
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
  val resolver = context.applicationContext.contentResolver
  val scope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val keyboardController = LocalSoftwareKeyboardController.current
  val composerState = remember(viewModel) { viewModel.chatComposerState }
  val inputDrafts = composerState.textDrafts
  val imagePickerOwnerCheckpoint =
    rememberSaveable(saver = ChatComposerMediaCheckpoint.Saver) { ChatComposerMediaCheckpoint() }
  val filePickerOwnerCheckpoint =
    rememberSaveable(saver = ChatComposerMediaCheckpoint.Saver) { ChatComposerMediaCheckpoint() }
  val voiceNoteCommitCheckpoint = remember { ChatComposerMediaCheckpoint() }
  val input = inputDrafts[composerOwner]
  val quoteDraft = composerState.quoteDrafts[composerOwner]
  val attachmentsByOwner by composerState.attachments.collectAsState()
  val attachments = attachmentsByOwner[composerOwner].orEmpty()
  val sendStates by composerState.sendStates.collectAsState()
  val attachmentNotices by composerState.attachmentNotices.collectAsState()
  val shareOwnerRevision by viewModel.chatShareDraftOwnerRevision.collectAsState()
  val chatShareDraft =
    remember(chatShareDrafts, composerOwner, mainSessionKey, shareOwnerRevision) {
      chatShareDrafts.firstOrNull { draft ->
        viewModel.chatShareDraftTargetsOwner(draft.id, composerOwner, mainSessionKey)
      }
    }
  val shareStaging =
    composerState.hasPendingImport(composerOwner) ||
      chatShareDraft?.let { viewModel.chatShareDraftTargetsOwner(it.id, composerOwner, mainSessionKey) } == true
  val pendingSendAdmissionIds = sendStates[composerOwner]?.pendingAdmissionIds.orEmpty()
  val currentPickerOwner by rememberUpdatedState(composerOwner)
  val currentPickerMainSessionKey by rememberUpdatedState(mainSessionKey)
  val sendInFlight = composerOwner in sendStates
  val pickerActivity = LocalActivity.current
  val pickerView = LocalView.current

  // Admission reads current settings, not the last composed enabled state.
  fun currentEffortSession() =
    viewModel.chatSessions.value.firstOrNull {
      isActiveSessionChoice(it.key, viewModel.chatSessionKey.value, viewModel.mainSessionKey.value)
    }

  fun currentFastModeProviderSupported() =
    fastModeProviderSupportedForSelection(
      selectedModelRef = viewModel.chatSelectedModelRef.value,
      sessionModelProvider = currentEffortSession()?.modelProvider,
      catalog = viewModel.chatModelCatalog.value,
    )

  fun canChangeThinking() =
    operatorScopesAllowAdmin(viewModel.operatorScopes.value) &&
      chatThinkingSupported(
        selection = viewModel.chatThinkingLevelSelection.value,
        fallbackSupported = thinkingSupportedForSelection(viewModel.chatSelectedModelRef.value, viewModel.chatModelCatalog.value),
      )

  fun canChangeFastMode(expected: ChatComposerOwner) =
    chatFastModeControlEnabled(
      supported =
        fastModeSupportedForSelection(
          providerSupported = currentFastModeProviderSupported(),
          hasConfiguredFastModeOverride = currentEffortSession()?.fastMode != null,
        ),
      adminAuthorized = operatorScopesAllowAdmin(viewModel.operatorScopes.value),
      connected = viewModel.gatewayConnectionDisplay.value.isConnected,
      gatewayAvailable = viewModel.chatHealthOk.value,
      loading = viewModel.chatHistoryLoading.value || viewModel.chatSessionCreating.value,
      sending = expected in composerState.sendStates.value,
      activeRun = viewModel.pendingRunCount.value > 0,
      streaming = viewModel.chatStreamingAssistantText.value != null,
      settingsMutationPending = viewModel.chatSessionKey.value in viewModel.chatPendingSessionSettingsKeys.value,
    )

  val modelPicker =
    remember(viewModel, pickerActivity, pickerView, lifecycleOwner) {
      ChatModelPickerSessionOwner(pickerActivity, pickerView, lifecycleOwner.lifecycle) { expected ->
        viewModel.isCurrentChatComposerOwner(expected) &&
          viewModel.gatewayConnectionDisplay.value.isConnected &&
          operatorScopesAllowWrite(viewModel.operatorScopes.value)
      }
    }
  val effortPicker =
    remember(viewModel, pickerActivity, pickerView, lifecycleOwner) {
      ChatModelPickerSessionOwner(pickerActivity, pickerView, lifecycleOwner.lifecycle) { expected ->
        viewModel.isCurrentChatComposerOwner(expected) && (canChangeThinking() || canChangeFastMode(expected))
      }
    }
  val backgroundTasks =
    remember(viewModel, pickerActivity, pickerView, lifecycleOwner) {
      ChatModelPickerSessionOwner(pickerActivity, pickerView, lifecycleOwner.lifecycle) { expected ->
        viewModel.isCurrentChatComposerOwner(expected)
      }
    }
  val branchPicker =
    remember(viewModel, pickerActivity, pickerView, lifecycleOwner) {
      ChatModelPickerSessionOwner(pickerActivity, pickerView, lifecycleOwner.lifecycle) { expected ->
        viewModel.isCurrentChatComposerOwner(expected)
      }
    }
  var branchOpening by remember(branchPicker) { mutableStateOf<ChatBranchOpening?>(null) }

  fun isCurrentBranchOpening(opening: ChatBranchOpening): Boolean {
    if (branchPicker.visible !== opening.session || opening.session.geometry.revoked) return false
    if (!viewModel.isCurrentChatBranchTarget(opening.session.composerOwner, opening.selectionGeneration)) {
      branchPicker.retire(opening.session)
      return false
    }
    return true
  }

  rememberWindowDisplayFeatureState { publication ->
    modelPicker.publishFeatures(publication)
    effortPicker.publishFeatures(publication)
    backgroundTasks.publishFeatures(publication)
    branchPicker.publishFeatures(publication)
  }
  SideEffect {
    modelPicker.refreshTarget()
    effortPicker.refreshTarget()
    backgroundTasks.refreshTarget()
    branchPicker.refreshTarget()
    branchOpening?.let { isCurrentBranchOpening(it) }
  }
  DisposableEffect(modelPicker, effortPicker, backgroundTasks, branchPicker) {
    onDispose {
      modelPicker.dispose()
      effortPicker.dispose()
      backgroundTasks.dispose()
      branchPicker.dispose()
    }
  }
  var detailsExpanded by rememberSaveable { mutableStateOf(false) }
  // Batch share picker: null means browsing; a set means the transcript is in
  // selection mode with those message ids checked. Session-scoped so a switch
  // can never carry a selection into another conversation.
  var shareSelection by remember(sessionKey) { mutableStateOf<Set<String>?>(null) }
  // Every selectable bubble in the session at the moment the picker opened. The
  // transcript owns the timeline, so it hands the candidate set up with the seed.
  var shareSelectionCandidates by remember(sessionKey) { mutableStateOf<Set<String>>(emptySet()) }
  val shareSelectionActive = shareSelection != null
  val currentSelectionModeCallback by rememberUpdatedState(onSelectionModeChange)
  LaunchedEffect(shareSelectionActive) {
    if (shareSelectionActive) {
      detailsExpanded = false
      // Selection is a modal surface: the keyboard must not stay up behind an
      // input bar the user can no longer reach.
      focusManager.clearFocus(force = true)
      keyboardController?.hide()
    }
    currentSelectionModeCallback(shareSelectionActive)
  }
  fun clearShareSelection() {
    shareSelection = null
    shareSelectionCandidates = emptySet()
  }
  BackHandler(enabled = shareSelectionActive) { clearShareSelection() }
  // Share card shown after confirm. Nothing is created until a link action is
  // tapped; blank-tap/back dismissal just closes the card.
  var sharePanelPayload by remember(sessionKey) { mutableStateOf<ChatSharePanelPayload?>(null) }
  var shareLinkBusy by remember { mutableStateOf<ChatShareLinkAction?>(null) }
  val currentSharePanelCallback by rememberUpdatedState(onSharePanelChange)
  LaunchedEffect(sharePanelPayload != null) {
    currentSharePanelCallback(sharePanelPayload != null)
  }
  BackHandler(enabled = sharePanelPayload != null) { sharePanelPayload = null }
  var sendMessageTooLong by rememberSaveable(composerOwner) { mutableStateOf(false) }
  var sendCheckpointFull by rememberSaveable(composerOwner) { mutableStateOf(false) }

  LaunchedEffect(composerOwner, mainSessionKey, chatShareDraft?.id) {
    viewModel.resolveChatComposerOwnerAliases(to = composerOwner, mainSessionKey = mainSessionKey)
    if (shouldMigrateComposerDraft(voiceNoteCommitCheckpoint.owner, composerOwner, mainSessionKey)) {
      voiceNoteCommitCheckpoint.owner = composerOwner
    }
    viewModel.resolveChatShareDraftOwner(chatShareDraft?.id, composerOwner, mainSessionKey)
  }

  DisposableEffect(viewModel) {
    onDispose(viewModel::stopChatMessageSpeech)
  }
  val modelSections =
    remember(modelCatalog, modelFavorites, modelRecents) {
      chatModelPickerSections(
        catalog = modelCatalog,
        favorites = modelFavorites,
        recents = modelRecents,
      )
    }
  val selectedModelLabel =
    if (modelSelectionLocked) {
      if (activeSession.agentRuntimeId == "codex") nativeString("Native Codex model") else nativeString("Locked session model")
    } else {
      selectedModelRef?.let { selected ->
        modelCatalog.firstOrNull { it.providerQualifiedRef() == selected }?.name?.takeIf { it.isNotBlank() }
          ?: selected.substringAfterLast('/')
      } ?: nativeString("Model")
    }
  val modelUnavailableReason =
    selectedChatModelSendBlockingReason(
      gatewayReady = healthOk,
      selectedModelRef = selectedModelRef,
      catalog = modelCatalog,
    )
  val modelUnavailableMessage = chatModelUnavailableText(modelUnavailableReason)
  val micCaptureActive = micEnabled || micIsListening || micCooldown || talkModeEnabled || talkModeListening
  val voiceNoteRecorder =
    rememberVoiceNoteRecorderController(
      viewModel = viewModel,
      ownerKey = composerOwner,
      mainSessionKey = mainSessionKey,
      onFinished = { recordingId, attachment ->
        val lease = voiceNoteCommitCheckpoint.consume(recordingId) ?: return@rememberVoiceNoteRecorderController
        composerState.addAuthorizedAttachments(lease.owner, lease.authorizationId, listOf(attachment))
      },
    )
  val voiceNoteState by voiceNoteRecorder.state.collectAsState()
  val voiceNoteElapsedMs by voiceNoteRecorder.elapsedMs.collectAsState()
  val voiceNoteLevel by voiceNoteRecorder.inputLevel.collectAsState()
  val dictationController = rememberChatDictationController(viewModel)
  val dictationState by dictationController.state.collectAsState()
  val dictationActive =
    dictationState is ChatDictationState.Starting || dictationState is ChatDictationState.Listening
  val pickImages =
    rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
      val lease = imagePickerOwnerCheckpoint.consume() ?: return@rememberLauncherForActivityResult
      if (uris.isNullOrEmpty()) {
        composerState.cancelMediaAcquisition(lease.authorizationId)
        return@rememberLauncherForActivityResult
      }
      val importOwner =
        if (shouldMigrateComposerDraft(lease.owner, currentPickerOwner, currentPickerMainSessionKey)) {
          currentPickerOwner
        } else {
          lease.owner
        }
      val selectedUris = uris.take(8)
      viewModel.importChatComposerAttachments(
        owner = importOwner,
        mediaAuthorizationId = lease.authorizationId,
        mainSessionKey = currentPickerMainSessionKey,
        expectedCount = uris.size,
      ) {
        selectedUris
          .mapNotNull { uri ->
            try {
              loadSizedImageAttachment(resolver, uri)
            } catch (err: CancellationException) {
              throw err
            } catch (_: Throwable) {
              null
            }
          }
      }
    }
  val pickMediaOrDocument =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
      val lease = filePickerOwnerCheckpoint.consume() ?: return@rememberLauncherForActivityResult
      if (uri == null) {
        composerState.cancelMediaAcquisition(lease.authorizationId)
        return@rememberLauncherForActivityResult
      }
      val importOwner =
        if (shouldMigrateComposerDraft(lease.owner, currentPickerOwner, currentPickerMainSessionKey)) {
          currentPickerOwner
        } else {
          lease.owner
        }
      viewModel.importChatComposerAttachments(
        owner = importOwner,
        mediaAuthorizationId = lease.authorizationId,
        mainSessionKey = currentPickerMainSessionKey,
        expectedCount = 1,
      ) {
        listOfNotNull(
          try {
            loadPickedMediaOrDocumentAttachment(resolver, uri)
          } catch (err: CancellationException) {
            throw err
          } catch (_: Throwable) {
            null
          },
        )
      }
    }
  // The camera app writes to a FileProvider URI we hand it; the contract only
  // reports success, so the URI is kept here until the result comes back.
  val cameraCaptureDir = "camera"
  var cameraCaptureUri by rememberSaveable { mutableStateOf<Uri?>(null) }
  val cameraCaptureOwnerCheckpoint =
    rememberSaveable(saver = ChatComposerMediaCheckpoint.Saver) { ChatComposerMediaCheckpoint() }
  val takePicture =
    rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
      val lease = cameraCaptureOwnerCheckpoint.consume() ?: return@rememberLauncherForActivityResult
      val uri = cameraCaptureUri
      cameraCaptureUri = null
      if (!saved || uri == null) {
        composerState.cancelMediaAcquisition(lease.authorizationId)
        return@rememberLauncherForActivityResult
      }
      val importOwner =
        if (shouldMigrateComposerDraft(lease.owner, currentPickerOwner, currentPickerMainSessionKey)) {
          currentPickerOwner
        } else {
          lease.owner
        }
      viewModel.importChatComposerAttachments(
        owner = importOwner,
        mediaAuthorizationId = lease.authorizationId,
        mainSessionKey = currentPickerMainSessionKey,
        expectedCount = 1,
      ) {
        listOfNotNull(
          try {
            loadSizedImageAttachment(resolver, uri)
          } catch (err: CancellationException) {
            throw err
          } catch (_: Throwable) {
            null
          },
        )
      }
    }
  val requestCameraPermission =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      val lease = cameraCaptureOwnerCheckpoint.consume() ?: return@rememberLauncherForActivityResult
      val uri = cameraCaptureUri
      cameraCaptureUri = null
      if (!granted || uri == null) {
        // Denied: drop the pending acquisition so the composer is not left
        // waiting on a photo that will never arrive.
        composerState.cancelMediaAcquisition(lease.authorizationId)
        return@rememberLauncherForActivityResult
      }
      cameraCaptureOwnerCheckpoint.begin(lease.owner, lease.authorizationId)
      cameraCaptureUri = uri
      takePicture.launch(uri)
    }

  LaunchedEffect(composerOwner) {
    dictationController.cancel()
  }

  LaunchedEffect(Unit) {
    viewModel.loadCurrentChat()
    viewModel.refreshChatSessions(limit = 100)
    viewModel.refreshChatCommands()
  }

  LaunchedEffect(
    pendingAssistantAutoSend,
    assistantAutoSendInFlight,
    sendStates,
    composerOwner,
    healthOk,
    pendingRunCount,
    thinkingLevel,
  ) {
    if (!healthOk) return@LaunchedEffect
    val pending =
      resolvePendingAssistantAutoSend(
        pending = pendingAssistantAutoSend,
        currentOwner = composerOwner,
        healthOk = healthOk,
        pendingRunCount = pendingRunCount,
      ) ?: return@LaunchedEffect
    viewModel.dispatchPendingAssistantAutoSend(
      pending = pending,
      thinking = thinkingLevel,
    )
  }

  val shareImportNotice =
    when (attachmentNotices[composerOwner]) {
      ChatComposerAttachmentNotice.Attachment -> {
        NativeText.Resource(source = "Could not stage an attachment for sending.", formatArgs = emptyList())
      }

      ChatComposerAttachmentNotice.Image -> {
        nativeText("Some shared images were omitted or could not be added.")
      }

      null -> {
        when {
          sendMessageTooLong -> {
            joinedNativeText(
              separator = " ",
              parts =
                listOf(
                  chatOutboxQueueFailureText(),
                  verbatimText("${input.length}/$CHAT_COMPOSER_MAX_SEND_CHARS"),
                ),
            )
          }

          sendCheckpointFull -> {
            chatOutboxQueueFailureText()
          }

          else -> {
            null
          }
        }
      }
    }

  LaunchedEffect(chatDraft, composerOwner, mainSessionKey) {
    val pending = chatDraft ?: return@LaunchedEffect
    val claimed =
      viewModel.consumeChatDraft(
        expected = pending,
        owner = composerOwner,
        mainSessionKey = mainSessionKey,
      ) ?: return@LaunchedEffect
    val merged =
      mergeChatDraft(draft = claimed, currentInput = input, currentOwner = composerOwner) ?: return@LaunchedEffect
    inputDrafts[composerOwner] = merged
    // Rewind/fork replace the composer wholesale; an attachment staged during the
    // in-flight round-trip is accepted collateral rather than a draft revision field.
    claimed.attachments?.let { composerState.replaceAttachments(composerOwner, it) }
  }

  LaunchedEffect(composerOwner, pendingSendAdmissionIds) {
    pendingSendAdmissionIds.forEach { admissionId ->
      viewModel.acknowledgeChatComposerSendAdmission(composerOwner, admissionId)
    }
  }

  // The process queue remembers the first owner; only an explicit alias/identity resolution
  // migrates that claim. Navigating elsewhere must never retarget a shared payload.
  LaunchedEffect(chatShareDraft?.id, lifecycleState, composerOwner, shareOwnerRevision) {
    if (!lifecycleState.isAtLeast(Lifecycle.State.RESUMED)) return@LaunchedEffect
    val share = chatShareDraft ?: return@LaunchedEffect
    val ownerSnapshot = composerOwner
    viewModel.withChatShareDraftLease(share.id, ownerSnapshot) {
      val staged =
        withContext(Dispatchers.IO) {
          stageChatShareDraft(share) { attachment ->
            loadSharedAttachment(resolver, attachment)
          }
        }
      if (!viewModel.isCurrentChatComposerOwner(ownerSnapshot)) return@withChatShareDraftLease
      if (
        !canCommitStagedChatShare(
          stagedId = share.id,
          currentHead = viewModel.chatShareDraftForOwner(ownerSnapshot, mainSessionKey),
          ownerSnapshot = ownerSnapshot,
          currentOwner = ownerSnapshot,
        )
      ) {
        return@withChatShareDraftLease
      }
      // A non-resumed Activity must not acknowledge into its hidden composer; the next visible
      // Activity keeps the process-owned head and retries the complete import instead.
      if (!lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
        return@withChatShareDraftLease
      }
      // Keep the head pending through both mutations: Send stays gated until text and images
      // have been merged together, and disposal before this point leaves the head for retry.
      inputDrafts[ownerSnapshot] =
        mergeSharedChatText(sharedText = staged.text, currentInput = inputDrafts[ownerSnapshot])
      val admissionOmissions = composerState.addAttachments(ownerSnapshot, staged.attachments)
      composerState.reportAttachmentOmission(
        ownerSnapshot,
        staged.failedAttachmentCount + staged.droppedAttachmentCount + admissionOmissions,
      )
      viewModel.acknowledgeChatShareDraft(share.id, ownerSnapshot)
    }
  }

  val newChatEnabled =
    !sessionCreating && !modelSelectionLocked &&
      canStartNewChat(
        pendingRunCount = pendingRunCount,
        hasQueuedMessage = pendingAssistantAutoSend != null,
        gatewayReady = healthOk && !gatewayOffline,
      )

  val startNewChat: (Boolean) -> Unit = { worktree ->
    if (newChatEnabled) {
      viewModel.startNewChat(worktree = worktree)
      viewModel.refreshChatSessions(limit = 100)
      viewModel.refreshChatCommands()
    }
  }

  // The chat itself is a glass source, so the floating menus opened from the
  // composer and from the header blur the transcript behind them, exactly like
  // the sidebar's session menu blurs the sidebar. Declared above `headerContent`
  // because that lambda captures it.
  val chatHazeState = rememberHazeState()
  val chatBackdrop = rememberClawBackdrop()
  // Actions menu state lives here, not in ChatHeader: the overlay that draws the
  // morphing glass is a sibling of the header. The trigger carries the capsule's
  // bounds and the glide, so ChatHeader just applies `modifier`.
  val actionsMenu = rememberClawMorphMenuState()
  val actionsTrigger = rememberClawMorphTrigger(actionsMenu)
  // The simple bar's ＋ owns a second morph surface: its primary card is the
  // shared glass morph, and drilling adds a nested panel the shared surface
  // cannot express (it draws exactly one shape).
  val simpleComposerMenu = remember { SimpleComposerMenuState() }
  // Haze on the transcript: the panel reads as a white sheet (the look the
  // design settled on) instead of the darker backdrop shader.
  simpleComposerMenu.morph.haze = chatHazeState
  // One text-selection owner per session: a quote/selection cannot leak across sessions.
  val textSelection = remember(sessionKey) { ChatTextSelectionController() }
  var composerFocusRequest by remember(composerOwner) { mutableIntStateOf(0) }
  var simpleEditorOpen by remember { mutableStateOf(false) }
  var fullScreenEditorFocusRequest by remember { mutableIntStateOf(0) }
  var annotationTarget by remember { mutableStateOf<AttachmentAnnotationTarget?>(null) }
  // The header's branch-switcher action, hoisted here because the morph menu
  // (a sibling overlay, not part of the header) needs the same behavior.
  val openBranchSwitcherMenu = {
    if (viewModel.isCurrentChatBranchTarget(composerOwner, selectionGeneration)) {
      val previous = branchPicker.visible
      branchPicker.open(composerOwner, sessionKey)
      branchPicker.visible?.takeIf { it !== previous && !it.geometry.revoked }?.let { session ->
        val opening = ChatBranchOpening(session, selectionGeneration)
        branchOpening = opening
        // Reads can start before placement; admitted operations outlive the keyed sheet.
        scope.launch {
          if (isCurrentBranchOpening(opening)) viewModel.refreshChatSessionBranches()
        }
      }
    }
  }
  // The actions capsule's menu body. It is claimed into the shared morph host
  // rather than rendered in place, because the glass has to be drawn by the
  // pane-level overlay above everything it covers.
  val actionsMenuContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
    val newChatInWorktreeMenuLabel = stringResource(R.string.new_chat_in_worktree)
    ClawGlassMenuItem(
      label = nativeString("Refresh chat"),
      icon = MiuixIcons.Medium.MiuixRefresh,
      onClick = { viewModel.refreshChat(); viewModel.refreshChatSessions(limit = 100) },
    )
    if (sessionBranches.size > 1) {
      ClawGlassMenuItem(
        label = nativeString("Switch branch"),
        icon = MiuixIcons.Medium.MiuixChevronForward,
        enabled = viewModel.isCurrentChatBranchTarget(composerOwner, selectionGeneration),
        onClick = {
          detailsExpanded = false
          openBranchSwitcherMenu()
        },
      )
    }
    ClawGlassMenuItem(
      label = nativeString("Dashboard"),
      icon = MiuixIcons.Medium.MiuixHome,
      onClick = {
        detailsExpanded = false
        onOpenDashboard(sessionKey)
      },
    )
    ClawGlassMenuItem(
      label = nativeString("Background tasks"),
      icon = MiuixIcons.Medium.MiuixTimer,
      onClick = {
        detailsExpanded = false
        backgroundTasks.open(composerOwner, sessionKey)
      },
    )
    if (workspaceGit) {
      ClawGlassMenuItem(
        label = newChatInWorktreeMenuLabel,
        enabled = newChatEnabled,
        onClick = {
          detailsExpanded = false
          startNewChat(true)
        },
      )
    }
  }
  val headerContent: @Composable (() -> Unit) -> Unit = { dismissDetails ->
    ChatHeader(
      activeAgent = activeAgent,
      projectLabel = activeProjectLabel,
      sessionTitle = activeSessionTitle,
      sessionColor = activeSession?.color,
      showSidebarButton = showSidebarButton,
      onOpenSidebar = {
        dismissDetails()
        onOpenSidebar()
      },
      onNewChat = {
        dismissDetails()
        startNewChat(false)
      },
      healthOk = healthOk,
      pendingRunCount = pendingRunCount,
      sessionCreating = sessionCreating,
      newChatEnabled = newChatEnabled,
      actionsMenuExpanded = actionsMenu.expanded,
      onActionsMenuExpandedChange = { expanded ->
        if (expanded) actionsTrigger.open(content = actionsMenuContent) else actionsMenu.dismiss()
      },
      morphTrigger = actionsTrigger,
      hazeState = chatHazeState,
      backdrop = chatBackdrop,
      interactionLocked = shareSelectionActive,
    )
  }
  val conversationStatus: @Composable () -> Unit = {
    errorText?.takeIf { it.isNotBlank() }?.let { error ->
      ChatNotice(
        title = nativeString("Chat needs attention"),
        body = userFacingChatError(error = error, gatewayConnected = gatewayConnectionDisplay.isConnected),
      )
    }
    ChatSwarmProgress(groups = swarmGroups)
  }
  toolDetailSheet?.let { detail ->
    // Live thinking keeps streaming inside the sheet while it is open.
    val liveThinking = streamingThinkingText.takeIf { detail.key == LIVE_THINKING_DETAIL_KEY }
    ToolDetailSheet(
      detail = if (liveThinking != null) detail.copy(detailText = liveThinking, running = true) else detail,
      onDismiss = { toolDetailSheet = null },
    )
  }
  ChatMessageList(
    morphHost = actionsMenu,
    sessionKey = sessionKey,
    fullMessageOwner = composerOwner,
    selectionGeneration = selectionGeneration,
    gatewayCatalogRevision = gatewayCatalogRevision,
    prepareFullMessageRead = { message -> viewModel.prepareFullMessageRead(composerOwner, selectionGeneration, gatewayCatalogRevision, message) },
    session = activeSession,
    messages = messages,
    transcriptAnchor = transcriptAnchor,
    historyLoading = historyLoading,
    activeRunCount = selectedActiveRun.count,
    activeRunId = selectedActiveRun.runId,
    activeRunClockKey = selectedActiveRun.clockKey,
    activeRunOutputTokens = selectedActiveRun.outputTokens,
    pendingToolCalls = pendingToolCalls,
    subagentActivities = subagentActivities,
    questions = questionsForSession(questions, sessionKey, mainSessionKey, activeAgentId),
    streamingAssistantText = streamingAssistantText,
    streamingThinkingText = streamingThinkingText,
    onToolDetail = { detail -> toolDetailSheet = detail },
    healthOk = healthOk,
    gatewayOffline = gatewayOffline,
    outboxItems = currentSessionOutboxItems,
    recoveryOutboxItems =
      outboxItemsForRecovery(
        items = outboxItems,
      ),
    onRetryOutbox = viewModel::retryChatOutboxCommand,
    onDeleteOutbox = viewModel::deleteChatOutboxCommand,
    onResolveQuestion = viewModel::resolveChatQuestion,
    onQuestionDraftChanged = viewModel::updateChatQuestionDraft,
    onSkipQuestion = viewModel::skipChatQuestion,
    onStarterPrompt = { prompt -> inputDrafts[composerOwner] = prompt },
    onQuoteMessage = { quote ->
      viewModel.setChatQuoteDraft(quote, composerOwner)
      composerFocusRequest++
    },
    textSelection = textSelection,
    sessionActionsEnabled =
      pendingRunCount == 0 &&
        !sessionBranchSwitching &&
        outboxPresentationRestored &&
        currentSessionOutboxItems.none { it.status != ChatOutboxStatus.Failed },
    onRewindMessage = { entryId ->
      val expectedInput = inputDrafts[composerOwner].orEmpty()
      scope.launch {
        val result = viewModel.rewindChatAtEntry(entryId) ?: return@launch
        viewModel.setChatDraft(
          ChatDraft(
            text = result.editorText.orEmpty(),
            placement = ChatDraftPlacement.Replace,
            owner = composerOwner,
            expectedExistingText = expectedInput,
            acceptsEmptyText = true,
            attachments = result.editorAttachments.toPendingAttachments(),
          ),
        )
      }
    },
    onForkMessage = { entryId ->
      scope.launch {
        val result = viewModel.forkChatAtEntry(entryId) ?: return@launch
        val newOwner = composerOwner.copy(sessionKey = result.sessionKey)
        val expectedInput = inputDrafts[newOwner].orEmpty()
        viewModel.switchChatSession(result.sessionKey, composerOwner.agentId)
        viewModel.setChatDraft(
          ChatDraft(
            text = result.editorText.orEmpty(),
            placement = ChatDraftPlacement.Replace,
            owner = newOwner,
            expectedExistingText = expectedInput,
            acceptsEmptyText = true,
            attachments = result.editorAttachments.toPendingAttachments(),
          ),
        )
      }
    },
    speechState = messageSpeechState,
    onToggleListen = viewModel::toggleChatMessageSpeech,
    inlineMediaPlaybackBlocked = inlineMediaPlaybackBlocked,
    shareSelection = shareSelection,
    onEnterShareSelection = { selected, all ->
      shareSelection = selected
      shareSelectionCandidates = all
    },
    onToggleShareSelection = { id ->
      shareSelection =
        shareSelection?.let { current ->
          if (id in current) current - id else current + id
        }
    },
    resolveInlineWidgetResource = viewModel::resolveInlineWidgetResource,
    loadImageArtifact = viewModel::loadChatImageArtifact,
    loadMediaArtifact = viewModel::loadChatMediaArtifact,
    // The header floats over the transcript, so only the body below it may be a
    // haze source: sampling is done from the body, the header is its sibling.
    modifier = Modifier.fillMaxSize().imePadding(),
    headerModifier = Modifier.clawBackdropTopBar(chatBackdrop),
    // Haze stays scoped to the transcript: its effects live inside that subtree
    // and Haze forbids blurring an ancestor source.
    bodyModifier = Modifier.hazeSource(chatHazeState),
    // The liquid-glass backdrop needs the whole pane (down to window y=0) or the
    // strip above the source's top edge samples empty and bands under the
    // status bar. Canvas fill first so the capture is never transparent.
    backdropSourceModifier =
      Modifier
        .layerBackdrop(chatBackdrop)
        .background(ClawTheme.colors.canvas),
    // The transcript reserves exactly the bar's height under its newest row; the
    // classic two-row figure left a bare canvas band under the one-row simple bar.
    composerStyle = composerBarStyle,
    composerBottomInset = simpleComposerBottomInset,
    hazeState = chatHazeState,
    overlay = {
      Box(Modifier.fillMaxSize()) {
        val selectedShareIds = shareSelection.orEmpty().intersect(shareSelectionCandidates)
        ChatShareSelectionBar(
          visible = shareSelectionActive,
          selectedCount = selectedShareIds.size,
          allSelected =
            shareSelectionCandidates.isNotEmpty() &&
              selectedShareIds.size == shareSelectionCandidates.size,
          onCancel = { clearShareSelection() },
          onSelectAll = { shareSelection = shareSelectionCandidates },
          onConfirm = {
            // Confirm only stages the payload; the gateway create runs lazily
            // when a link action is tapped, so dismissing the card afterwards
            // never leaves an orphan share behind.
            if (selectedShareIds.isEmpty()) return@ChatShareSelectionBar
            val shareText =
              messages
                .filter { it.entryId in selectedShareIds }
                .mapNotNull { message ->
                  chatMessagePlainText(message.content.filter { it.toolActivity == null })
                    .takeIf(String::isNotBlank)
                }
                .joinToString("\n\n")
            if (shareText.isBlank()) return@ChatShareSelectionBar
            sharePanelPayload =
              ChatSharePanelPayload(
                sessionKey = sessionKey,
                messageIds = selectedShareIds.toList(),
                title = activeSessionTitle,
                text = shareText,
              )
            clearShareSelection()
          },
          backdrop = chatBackdrop,
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = chatComposerClearance(composerBarStyle, simpleComposerBottomInset)),
        )
        if (sharePanelPayload != null) {
          // The card is modal: nothing behind it scrolls or reacts, a tap on
          // blank space (or back) just dismisses it.
          Box(Modifier.matchParentSize().chatShareBlankDismiss { sharePanelPayload = null })
        }
        ChatSharePanel(
          visible = sharePanelPayload != null,
          linkLoading = shareLinkBusy == ChatShareLinkAction.CopyLink,
          wechatLoading = shareLinkBusy == ChatShareLinkAction.WeChat,
          onCopyLink = {
            val payload = sharePanelPayload
            if (payload != null && shareLinkBusy == null) {
              shareLinkBusy = ChatShareLinkAction.CopyLink
              scope.launch {
                val outcome = viewModel.createChatShare(payload.sessionKey, payload.messageIds)
                if (outcome is SessionShareOutcome.Success) {
                  sharePanelPayload = null
                  copyChatText(context, outcome.result.url, nativeString("链接已复制"))
                } else if (outcome is SessionShareOutcome.Failure) {
                  Toast.makeText(context, sessionShareFailureText(outcome.failure), Toast.LENGTH_LONG).show()
                }
                shareLinkBusy = null
              }
            }
          },
          onShareWeChat = {
            val payload = sharePanelPayload
            if (payload != null && shareLinkBusy == null) {
              shareLinkBusy = ChatShareLinkAction.WeChat
              scope.launch {
                val outcome = viewModel.createChatShare(payload.sessionKey, payload.messageIds)
                if (outcome is SessionShareOutcome.Success) {
                  sharePanelPayload = null
                  shareChatLinkToWeChat(context, outcome.result.url)
                } else if (outcome is SessionShareOutcome.Failure) {
                  Toast.makeText(context, sessionShareFailureText(outcome.failure), Toast.LENGTH_LONG).show()
                }
                shareLinkBusy = null
              }
            }
          },
          onCopyText = {
            val payload = sharePanelPayload
            sharePanelPayload = null
            if (payload != null) copyChatText(context, payload.text)
          },
          backdrop = chatBackdrop,
          modifier =
            Modifier
              .align(Alignment.BottomCenter)
              .padding(bottom = chatComposerClearance(composerBarStyle, simpleComposerBottomInset)),
        )
        ClawMorphMenuOverlay(
          state = actionsMenu,
          onDismissRequest = actionsMenu::dismiss,
          backdrop = chatBackdrop,
        )
        SimpleComposerMenuOverlay(
          state = simpleComposerMenu,
          backdrop = chatBackdrop,
          hazeState = chatHazeState,
        )
        SimpleComposerFullScreenEditor(
          visible = simpleEditorOpen,
          value = input,
          onValueChange = {
            sendMessageTooLong = false
            sendCheckpointFull = false
            inputDrafts[composerOwner] = it
          },
          focusRequestId = fullScreenEditorFocusRequest,
          backdrop = chatBackdrop,
          onCollapse = {
            simpleEditorOpen = false
            composerFocusRequest++
          },
        )
        ChatTextSelectionOverlay(
          controller = textSelection,
          backdrop = chatBackdrop,
          onFollowUp = { quote ->
            viewModel.setChatQuoteDraft(quote, composerOwner)
            composerFocusRequest++
          },
          onCopy = { text -> copyChatText(context, text) },
        )
      }
    },
    tabletopPanes = tabletopPanes,
    features = features,
    conversationStatus = conversationStatus,
    header = { compactHeight, tabletop ->
      if ((!compactHeight || tabletop) && !detailsExpanded) headerContent { detailsExpanded = false }
    },
  ) { compactHeight, tabletop ->
    ChatComposer(
      compactHeight = compactHeight,
      detailsExpanded = detailsExpanded,
      onDetailsExpandedChange = { detailsExpanded = it },
      conversationHeader = { dismissDetails -> headerContent(dismissDetails) },
      conversationStatus = {
        if (!tabletop) conversationStatus()
      },
      progressCard = progressCard,
      value = input,
      onValueChange = {
        sendMessageTooLong = false
        sendCheckpointFull = false
        inputDrafts[composerOwner] = it
      },
      attachments = attachments,
      thinkingLevel = thinkingLevel,
      thinkingOptions = thinkingLevelSelection.options,
      thinkingSupported = thinkingSupported,
      thinkingLevelEnabled = canAdminSessionSettings,
      fastMode = fastMode,
      fastModeEnabled =
        chatFastModeControlEnabled(
          supported = fastModeSupported,
          adminAuthorized = canAdminSessionSettings,
          connected = gatewayConnectionDisplay.isConnected,
          gatewayAvailable = healthOk,
          loading = historyLoading || sessionCreating,
          sending = sendInFlight,
          activeRun = pendingRunCount > 0,
          streaming = streamingAssistantText != null,
          settingsMutationPending = sessionSettingsPending,
        ),
      contextUsage = contextUsage,
      selectedModelLabel = selectedModelLabel,
      modelPickerEnabled = gatewayConnectionDisplay.isConnected && canWriteSessionSettings,
      modelSections = modelSections,
      modelFavorites = modelFavorites.toSet(),
      modelSelectionLocked = modelSelectionLocked,
      onSelectModel = { ref ->
        scope.launch { viewModel.setChatSessionModel(sessionKey = sessionKey, modelRef = ref) }
      },
      onToggleModelFavorite = { ref ->
        scope.launch { viewModel.toggleModelFavorite(ref) }
      },
      onOpenModelProviders = { ref ->
        onOpenProvidersModels()
      },
      healthOk = healthOk,
      gatewayOffline = gatewayOffline,
      gatewayConnecting = gatewayConnecting,
      showOfflineNotice = showOfflineNotice,
      offlineStatus = offlineStatus,
      pendingRunCount = pendingRunCount,
      shareStaging = shareStaging,
      sendInFlight = sendInFlight,
      shareImportNotice = shareImportNotice,
      modelUnavailableMessage = modelUnavailableMessage,
      onDismissShareImportNotice = {
        sendMessageTooLong = false
        sendCheckpointFull = false
        composerState.clearAttachmentOmission(composerOwner)
      },
      commands = chatCommands,
      onOpenEffortPicker = { effortPicker.open(composerOwner, sessionKey) },
      onOpenModelPicker = { modelPicker.open(composerOwner, sessionKey) },
      onOpenFullScreenEditor = {
        simpleEditorOpen = true
        fullScreenEditorFocusRequest++
      },
      onPickImages = {
        if (!viewModel.isCurrentChatComposerOwner(composerOwner)) return@ChatComposer
        val authorizationId = composerState.beginMediaAcquisition(composerOwner) ?: return@ChatComposer
        imagePickerOwnerCheckpoint.begin(composerOwner, authorizationId)
        pickImages.launch("image/*")
      },
      onPickAudioOrDocument = {
        if (!viewModel.isCurrentChatComposerOwner(composerOwner)) return@ChatComposer
        val authorizationId = composerState.beginMediaAcquisition(composerOwner) ?: return@ChatComposer
        filePickerOwnerCheckpoint.begin(composerOwner, authorizationId)
        pickMediaOrDocument.launch(SHARED_AUDIO_DOCUMENT_MIME_TYPES)
      },
      onPickVideo = {
        if (!viewModel.isCurrentChatComposerOwner(composerOwner)) return@ChatComposer
        val authorizationId = composerState.beginMediaAcquisition(composerOwner) ?: return@ChatComposer
        filePickerOwnerCheckpoint.begin(composerOwner, authorizationId)
        pickMediaOrDocument.launch(SHARED_VIDEO_MIME_TYPES)
      },
      onCapturePhoto = {
        if (!viewModel.isCurrentChatComposerOwner(composerOwner)) return@ChatComposer
        val authorizationId = composerState.beginMediaAcquisition(composerOwner) ?: return@ChatComposer
        // Clear the previous capture: leaving the URI in place would let a
        // later camera result overwrite the file this run is about to attach.
        cameraCaptureUri?.let { previous -> runCatching { resolver.delete(previous, null, null) } }
        val captureDir = File(context.cacheDir, cameraCaptureDir).apply { mkdirs() }
        val capture = File(captureDir, "capture-${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", capture)
        cameraCaptureOwnerCheckpoint.begin(composerOwner, authorizationId)
        cameraCaptureUri = uri
        // The stock camera Activity declares android:permission="android.permission.CAMERA"
        // on its capture entry point, so launching it without the runtime grant
        // throws SecurityException — the contract alone is not enough even though
        // we hand the camera a FileProvider URI and never open the sensor here.
        val granted =
          ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
          takePicture.launch(uri)
        } else {
          requestCameraPermission.launch(android.Manifest.permission.CAMERA)
        }
      },
      onRemoveAttachment = { id -> composerState.removeAttachments(composerOwner, setOf(id)) },
      onAnnotateAttachment = { attachment, chipBounds ->
        annotationTarget = AttachmentAnnotationTarget(attachment, chipBounds)
      },
      voiceNoteState = voiceNoteState,
      voiceNoteElapsedMs = voiceNoteElapsedMs,
      voiceNoteLevel = voiceNoteLevel,
      recordVoiceNoteEnabled =
        !talkActive &&
          !composerOwner.gatewayStableId.isNullOrBlank() &&
          pendingRunCount == 0 &&
          !micCaptureActive &&
          !dictationActive &&
          !sendInFlight,
      onStartVoiceNote = {
        scope.launch {
          val ownerSnapshot = composerOwner
          val mediaAuthorizationId = composerState.beginMediaAcquisition(ownerSnapshot) ?: return@launch
          val recordingId = UUID.randomUUID().toString()
          if (!viewModel.isCurrentChatComposerOwner(ownerSnapshot)) {
            composerState.cancelMediaAcquisition(mediaAuthorizationId)
            return@launch
          }
          dictationController.cancel()
          if (voiceNoteRecorder.start(recordingId)) {
            if (
              viewModel.isCurrentChatComposerOwner(ownerSnapshot) &&
              composerState.isMediaAcquisitionActive(mediaAuthorizationId)
            ) {
              voiceNoteCommitCheckpoint.begin(ownerSnapshot, mediaAuthorizationId, recordingId)
            } else {
              voiceNoteRecorder.cancel()
              composerState.cancelMediaAcquisition(mediaAuthorizationId)
            }
          } else {
            composerState.cancelMediaAcquisition(mediaAuthorizationId)
          }
        }
      },
      onCancelVoiceNote = {
        voiceNoteCommitCheckpoint.clear()?.let { lease ->
          composerState.cancelMediaAcquisition(lease.authorizationId)
        }
        voiceNoteRecorder.cancel()
      },
      onFinishVoiceNote = voiceNoteRecorder::finish,
      dictationState = dictationState,
      dictationEnabled =
        !talkActive &&
          pendingRunCount == 0 &&
          !micCaptureActive &&
          !sendInFlight &&
          (voiceNoteState is VoiceNoteRecorderState.Idle || voiceNoteState is VoiceNoteRecorderState.Failure),
      onToggleDictation = {
        if (dictationActive) {
          dictationController.finish()
        } else {
          scope.launch {
            val ownerSnapshot = composerOwner
            val transcript = dictationController.start()
            // Recognition can finish after navigation. Only the composer that started
            // dictation may receive its transcript; otherwise a late result crosses drafts.
            if (transcript != null && viewModel.isCurrentChatComposerOwner(ownerSnapshot)) {
              inputDrafts[ownerSnapshot] =
                appendChatDictationTranscript(inputDrafts[ownerSnapshot], transcript)
            }
          }
        }
      },
      talkActive = talkActive,
      liveTalkVisible = liveTalkVisible,
      voiceInputVisible = voiceInputVisible,
      composerStyle = composerBarStyle,
      simpleMenu = simpleComposerMenu,
      selectedModelRef = selectedModelRef,
      onSelectThinkingLevel = { level ->
        if (canChangeThinking()) viewModel.setChatThinkingLevel(level)
      },
      onToggleTalk = onToggleTalk,
      onFixConnection = onOpenGatewaySettings,
      onOpenProvidersModels = onOpenProvidersModels,
      onCopyDiagnostics = {
        copyGatewayDiagnosticsReport(
          context = context,
          screen = "chat composer",
          gatewayAddress = gatewayAddress,
          statusText = offlineStatus,
        )
      },
      onAbort = viewModel::abortChat,
      onSend = {
        // Re-read the ViewModel so a stale click callback cannot beat StateFlow recomposition.
        val currentShare = viewModel.chatShareDraftForOwner(composerOwner, mainSessionKey)
        if (currentShare != null || composerOwner in sendStates) {
          return@ChatComposer
        }
        val ownerSnapshot = composerOwner
        if (!viewModel.isCurrentChatComposerOwner(ownerSnapshot)) return@ChatComposer
        val result =
          viewModel.beginChatComposerSend(
            owner = ownerSnapshot,
            thinking = thinkingLevel,
          )
        sendMessageTooLong = result == ChatComposerSendStartResult.MessageTooLong
        sendCheckpointFull = result == ChatComposerSendStartResult.CheckpointFull
      },
      hazeState = chatHazeState,
      interactionLocked = shareSelectionActive,
      quote = quoteDraft,
      onClearQuote = { viewModel.setChatQuoteDraft(null, composerOwner) },
      focusRequestId = composerFocusRequest,
    )
  }

  annotationTarget?.let { target ->
    AttachmentAnnotationOverlay(
      attachment = target.attachment,
      chipBounds = target.chipBounds,
      hazeState = chatHazeState,
      onConfirm = { annotatedBase64 ->
        val updated =
          attachments.map { attachment ->
            if (attachment.id == target.attachment.id) attachment.copy(base64 = annotatedBase64) else attachment
          }
        composerState.replaceAttachments(composerOwner, updated)
      },
      onDismiss = { annotationTarget = null },
    )
  }

  effortPicker.visible?.let { opening ->
    key(opening) {
      ChatEffortSheet(
        opening = opening,
        hazeState = chatHazeState,
        options = thinkingLevelSelection.options,
        selectedId = thinkingLevel,
        thinkingSupported = thinkingSupported,
        thinkingLevelEnabled = canAdminSessionSettings,
        fastMode = fastMode,
        fastModeEnabled = canChangeFastMode(opening.composerOwner),
        onSelect = { level ->
          if (effortPicker.admit(opening) && canChangeThinking()) {
            viewModel.setChatThinkingLevel(level)
          }
        },
        onFastModeChange = { enabled ->
          if (effortPicker.admit(opening) && canChangeFastMode(opening.composerOwner)) {
            viewModel.setChatSessionFastMode(
              sessionKey = opening.sessionKey,
              enabled = enabled,
              clearOverride = !currentFastModeProviderSupported(),
            )
          }
        },
        onDismiss = { if (effortPicker.admit(opening)) effortPicker.retire(opening) },
      )
    }
  }

  modelPicker.visible?.let { opening ->
    // The original callback target never becomes the newest opening after a coalesced close/open.
    fun currentSession() =
      viewModel.chatSessions.value.firstOrNull {
        isActiveSessionChoice(it.key, opening.sessionKey, viewModel.mainSessionKey.value)
      }

    fun admitPermissions(): Boolean =
      modelPicker.admit(opening) &&
        viewModel.chatPermissionSettingsAvailable.value &&
        currentSession()?.let { !it.sessionId.isNullOrBlank() && it.permissionModePending != true } == true &&
        opening.sessionKey !in viewModel.chatPendingSessionSettingsKeys.value

    key(opening) {
      ChatModelPickerSheet(
        opening = opening,
        hazeState = chatHazeState,
        admit = { modelPicker.admit(opening) },
        admitPermissions = ::admitPermissions,
        sections = modelSections,
        favorites = modelFavorites.toSet(),
        selectedModelLabel = selectedModelLabel,
        modelSelectionLocked = modelSelectionLocked,
        contextUsage = contextUsage,
        messages = messages,
        permissionMode = activeSession?.permissionMode,
        permissionModePending = permissionModePending,
        permissionPickerEnabled =
          permissionSettingsAvailable &&
            !activeSession?.sessionId.isNullOrBlank() &&
            gatewayConnectionDisplay.isConnected &&
            canWriteSessionSettings &&
            !permissionModePending &&
            !sessionSettingsPending,
        permissionUnavailableReason =
          when {
            !permissionSettingsAvailable -> nativeString("Update the Gateway to change session permissions.")
            activeSession?.sessionId.isNullOrBlank() -> nativeString("Refresh this chat before changing permissions.")
            else -> null
          },
        canSelectFullPermission = canAdminSessionSettings,
        onPermissionModeChange = { mode ->
          if (admitPermissions() && canSelectChatPermissionMode(mode, operatorScopesAllowAdmin(viewModel.operatorScopes.value))) {
            viewModel.setChatSessionPermissionMode(opening.sessionKey, mode)
            true
          } else {
            false
          }
        },
        onDismiss = { if (modelPicker.admit(opening)) modelPicker.retire(opening) },
        onSelect = { modelRef ->
          val model = viewModel.chatModelCatalog.value.firstOrNull { it.providerQualifiedRef() == modelRef }
          if (modelPicker.admit(opening) && currentSession()?.modelSelectionLocked != true &&
            (modelRef == null || model?.let(::chatModelPickerAction) == ChatModelPickerAction.Select)
          ) {
            modelPicker.retire(opening)
            viewModel.setChatSessionModel(sessionKey = opening.sessionKey, modelRef = modelRef)
          }
        },
        onOpenProviders = { ref ->
          val model = viewModel.chatModelCatalog.value.firstOrNull { it.providerQualifiedRef() == ref }
          if (modelPicker.admit(opening) && currentSession()?.modelSelectionLocked != true &&
            model?.let(::chatModelPickerAction) == ChatModelPickerAction.OpenProviders
          ) {
            modelPicker.retire(opening)
            onOpenProvidersModels()
          }
        },
        onToggleFavorite = { ref ->
          val model = viewModel.chatModelCatalog.value.firstOrNull { it.providerQualifiedRef() == ref }
          if (modelPicker.admit(opening) && currentSession()?.modelSelectionLocked != true &&
            model != null && model.available != false
          ) {
            viewModel.toggleModelFavorite(ref)
          }
        },
      )
    }
  }

  branchOpening?.takeIf { branchPicker.visible === it.session }?.let { opening ->
    key(opening) {
      BranchSwitcherSheet(
        opening = opening.session,
        branches = sessionBranches,
        selectionEnabled =
          canAdminSessionSettings && !sessionBranchesLoading &&
            viewModel.canSwitchChatSessionBranch(opening.session.composerOwner, opening.selectionGeneration),
        onDismiss = {
          if (isCurrentBranchOpening(opening) && branchPicker.admit(opening.session)) branchPicker.retire(opening.session)
        },
        onSelect = { leafEntryId ->
          scope.launch {
            if (isCurrentBranchOpening(opening) && branchPicker.admit(opening.session) &&
              viewModel.canSwitchChatSessionBranch(opening.session.composerOwner, opening.selectionGeneration, leafEntryId) &&
              viewModel.switchChatSessionBranch(leafEntryId)
            ) {
              branchPicker.retire(opening.session)
            }
          }
        },
      )
    }
  }
  backgroundTasks.visible?.let { opening ->
    key(opening) {
      BackgroundTasksSheet(
        viewModel = viewModel,
        opening = opening,
        admit = { backgroundTasks.admit(opening) },
        onDismiss = { if (backgroundTasks.admit(opening)) backgroundTasks.retire(opening) },
      )
    }
  }
}

internal fun canStartNewChat(
  pendingRunCount: Int,
  hasQueuedMessage: Boolean,
  gatewayReady: Boolean,
): Boolean = gatewayReady && pendingRunCount == 0 && !hasQueuedMessage

/**
 * Breathing room between the screen edge and the outermost header targets
 * (sidebar button on the left, new-chat / actions on the right).
 */
private val CHAT_HEADER_EDGE_GUTTER = 14.dp

internal fun chatHeaderSessionTitle(
  session: ChatSessionEntry?,
  unnamedTitle: () -> String,
): String = session?.let { sessionPresentationTitle(it, unnamedTitle) } ?: unnamedTitle()

internal fun chatHeaderProjectLabel(
  sessionKey: String,
  catalogs: List<SessionCatalog>,
): String? {
  val normalizedKey = sessionKey.trim().takeIf(String::isNotEmpty) ?: return null
  return sidebarCatalogHosts(catalogs)
    .asSequence()
    .flatMap { it.workspaces.asSequence() }
    .firstOrNull { workspace -> workspace.sessions.any { it.sessionKey == normalizedKey } }
    ?.label
}

@Composable
private fun ChatHeader(
  activeAgent: GatewayAgentSummary?,
  projectLabel: String?,
  sessionTitle: String,
  sessionColor: String?,
  showSidebarButton: Boolean,
  onOpenSidebar: () -> Unit,
  onNewChat: () -> Unit,
  healthOk: Boolean,
  pendingRunCount: Int,
  sessionCreating: Boolean,
  /** Gates the capsule's new-chat half; the actions half is always live. */
  newChatEnabled: Boolean,
  /** Backdrop the actions menu blurs: the chat transcript. */
  hazeState: HazeState? = null,
  backdrop: Backdrop? = null,
  /** Expanded state of the actions morph menu; owned by the ChatScreen overlay. */
  actionsMenuExpanded: Boolean = false,
  onActionsMenuExpandedChange: (Boolean) -> Unit = {},
  /**
   * Drives the actions morph menu: publishes the capsule's bounds and glides it
   * toward the growing panel while fading, so the swap reads as one continuous
   * element instead of two crossfading cards. Null only in previews that render
   * the header outside a chat pane.
   */
  morphTrigger: ClawMorphTrigger? = null,
  /** Swallows taps on the sidebar target and the actions capsule while sharing. */
  interactionLocked: Boolean = false,
) {
  val statusLabel =
    when {
      sessionCreating -> nativeString("Loading")
      pendingRunCount > 0 -> nativeString("Working")
      healthOk -> nativeString("Ready")
      else -> nativeString("Not ready")
    }
  val statusColor =
    when {
      pendingRunCount > 0 -> ClawTheme.colors.warning
      healthOk -> ClawTheme.colors.success
      else -> ClawTheme.colors.danger
    }

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        // The chat pane is pinned to the top edge of the screen, so the bar
        // grows up into the status bar: the glass covers it and the content
        // below stays clear of the clock and the icons.
        .statusBarsPadding()
        .padding(
          // Room the pane's own top padding used to give the bar.
          top = 10.dp,
          // The glass keeps going well past the row: this stretch is pure
          // fade-out, because the blur ramp reaches zero at the bottom edge.
          // Two reasons for it: the blur reads deeper than a 48dp strip, and
          // the header is *measured* with this padding, so
          // `headerReserve` (= measured height + gap) grows by the same amount
          // and the transcript clears the bar by a comfortable margin instead
          // of hiding its first message underneath it.
          // Extend the glass beyond the content row so its lower fade is
          // gradual instead of ending at the first transcript line.
          bottom = 56.dp,
        )
        .heightIn(min = ClawTheme.spacing.touchTarget),
  ) {
    Box(
      modifier =
        Modifier
          .align(Alignment.CenterStart)
          // Keep the round target off the screen edge: the disc is 48dp wide,
          // so without this gutter it sits flush against the display corner.
          .padding(start = CHAT_HEADER_EDGE_GUTTER)
          .size(ClawTheme.spacing.touchTarget),
      contentAlignment = Alignment.Center,
    ) {
      if (showSidebarButton) {
        ClawCircularIconButton(
          icon = Icons.Default.Menu,
          contentDescription = nativeString("Show Sidebar"),
          onClick = onOpenSidebar,
          hazeState = hazeState,
          backdrop = backdrop,
        )
      }
    }
    Row(
      modifier =
        Modifier
          .align(Alignment.CenterStart)
          .fillMaxWidth()
          // Clear of the two 48dp header targets on the right plus their spacing
          // and of the sidebar target on the left; both now carry an edge gutter.
          .padding(start = 56.dp + CHAT_HEADER_EDGE_GUTTER, end = 110.dp + CHAT_HEADER_EDGE_GUTTER)
          .clearAndSetSemantics {
            contentDescription = listOfNotNull(projectLabel, sessionTitle, statusLabel).joinToString(", ")
          },
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      val resolvedSessionColor = ClawTheme.colors.sessionColor(sessionColor)
      val avatarFallback =
        activeAgent
          ?.emoji
          ?.trim()
          ?.takeIf(String::isNotEmpty)
          ?: activeAgent
            ?.name
            ?.trim()
            ?.firstOrNull()
            ?.uppercaseChar()
            ?.toString()
          ?: "O"
      Box(
        modifier =
          Modifier
            .size(30.dp)
            .background(resolvedSessionColor ?: Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
      ) {
        ClawAgentAvatar(
          source = activeAgent?.let(::agentAvatarSource),
          size = 26.dp,
        ) {
          Box(
            modifier = Modifier.size(26.dp).background(ClawTheme.colors.surfaceRaised, CircleShape),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              text = avatarFallback,
              style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Medium),
              color = ClawTheme.colors.text,
              maxLines = 1,
            )
          }
        }
      }
      Column(modifier = Modifier.weight(1f)) {
        projectLabel?.let { project ->
          Text(
            text = project,
            style = chatProjectStyle(),
            color = ClawTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = sessionTitle,
            style = chatTitleStyle(),
            color = ClawTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          if (sessionCreating) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = ClawTheme.colors.textMuted)
          } else {
            Box(modifier = Modifier.size(6.dp).background(statusColor, CircleShape))
          }
        }
      }
    }
    Row(
      modifier =
        Modifier
          .align(Alignment.CenterEnd)
          // Same gutter as the sidebar target so both ends read symmetric.
          .padding(end = CHAT_HEADER_EDGE_GUTTER),
      horizontalArrangement = Arrangement.spacedBy(ClawCircularButtonDefaults.GroupSpacing),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ClawCapsuleIconButton(
        leftIcon = MiuixIcons.Medium.MiuixEdit,
        leftContentDescription = nativeString("New chat"),
        onLeftClick = onNewChat,
        leftEnabled = newChatEnabled,
        rightIcon = MiuixIcons.Medium.MiuixMore,
        rightContentDescription = nativeString("Chat actions"),
        onRightClick = { onActionsMenuExpandedChange(!actionsMenuExpanded) },
        hazeState = hazeState,
        backdrop = backdrop,
        modifier = morphTrigger?.modifier ?: Modifier,
      )
    }
    if (interactionLocked) {
      // Drawn last so it sits above both header targets. Kept transparent: the
      // picker's checkboxes and capsule are the visible mode change, and dimming
      // the bar would invent a second "disabled" surface.
      Box(Modifier.matchParentSize().chatShareTouchShield(true))
    }
  }
}

/**
 * Gateway text deltas arrive far faster than the transcript can be rebuilt:
 * every delta used to invalidate the whole timeline, so a busy reply spent the
 * frame budget recomposing instead of animating — the typewriter stalled and
 * the text landed in lumps. Conflating down to one update per interval keeps
 * the text current without recomposing more than ~16 times a second.
 */
@Composable
private fun rememberThrottledStreamText(source: StateFlow<String?>): String? {
  var shown by remember { mutableStateOf(source.value) }
  // Collect into a single pending slot, then publish at most once per frame.
  // StateFlow already conflates equal/latest values, so this avoids both a
  // backlog and the old polling race that could miss a short-lived final chunk.
  LaunchedEffect(source) {
    var pending = source.value
    val collector =
      launch {
        source.collect { latest -> pending = latest }
      }
    try {
      while (currentCoroutineContext().isActive) {
        withFrameNanos { }
        if (pending != shown) shown = pending
      }
    } finally {
      collector.cancel()
    }
  }
  return shown
}

@Composable
private fun ChatMessageList(
  sessionKey: String,
  fullMessageOwner: ChatComposerOwner,
  selectionGeneration: Long,
  gatewayCatalogRevision: Long,
  prepareFullMessageRead: (ChatMessage) -> ChatController.FullMessageRead?,
  session: ChatSessionEntry?,
  messages: List<ChatMessage>,
  transcriptAnchor: ChatTranscriptAnchorState?,
  historyLoading: Boolean,
  activeRunCount: Int,
  activeRunId: String?,
  activeRunClockKey: String?,
  activeRunOutputTokens: Long?,
  pendingToolCalls: List<ChatPendingToolCall>,
  subagentActivities: Map<String, ChatSubagentActivity>,
  questions: List<ChatQuestionPrompt>,
  streamingAssistantText: String?,
  streamingThinkingText: String?,
  onToolDetail: (ToolActivityDetail) -> Unit,
  healthOk: Boolean,
  gatewayOffline: Boolean,
  outboxItems: List<ChatOutboxItem>,
  recoveryOutboxItems: List<ChatOutboxItem>,
  onRetryOutbox: (String) -> Unit,
  onDeleteOutbox: (String) -> Unit,
  onResolveQuestion: (ChatQuestionPrompt, Map<String, List<String>>) -> Unit,
  onQuestionDraftChanged: (ChatQuestionPrompt, (ChatQuestionDraft) -> ChatQuestionDraft) -> Unit,
  onSkipQuestion: (ChatQuestionPrompt) -> Unit,
  onStarterPrompt: (String) -> Unit,
  onQuoteMessage: (ChatQuoteDraft) -> Unit,
  textSelection: ChatTextSelectionController,
  sessionActionsEnabled: Boolean,
  onRewindMessage: (String) -> Unit,
  onForkMessage: (String) -> Unit,
  /** Checked message ids while the batch share picker is open; null while browsing. */
  shareSelection: Set<String>? = null,
  onEnterShareSelection: (selected: Set<String>, all: Set<String>) -> Unit = { _, _ -> },
  onToggleShareSelection: (String) -> Unit = {},
  speechState: MessageSpeechState?,
  onToggleListen: (String, String) -> Unit,
  inlineMediaPlaybackBlocked: Boolean,
  resolveInlineWidgetResource: suspend (String, ChatWidgetResource?) -> ChatWidgetResource?,
  loadImageArtifact: suspend (String) -> GatewayLoadedImage?,
  loadMediaArtifact: suspend (String, GatewayMediaKind, Boolean) -> GatewayLoadedMedia?,
  modifier: Modifier = Modifier,
  /** Subtree the header glass samples: everything below the header. */
  bodyModifier: Modifier = Modifier,
  /** Full-pane backdrop source for the header glass and the morph menu. */
  backdropSourceModifier: Modifier = Modifier,
  headerModifier: Modifier = Modifier,
  /** Input bar in play; the transcript reserves exactly its height under the newest row. */
  composerStyle: ComposerBarStyle = ComposerBarStyle.Classic,
  /** Space between the pane's bottom edge and the floating bar's gap. */
  composerBottomInset: Dp = 10.dp,
  /** Backdrop long-press message menus blur. Published to the transcript. */
  hazeState: HazeState? = null,
  /** Pane-wide layer above header and transcript: hosts the morph menu. */
  overlay: @Composable () -> Unit = {},
  /** The pane's shared morph surface, published to every slot below. */
  morphHost: ClawMorphMenuState,
  tabletopPanes: TabletopPaneBounds?,
  features: List<DisplayFeature>,
  conversationStatus: @Composable () -> Unit,
  header: @Composable (Boolean, Boolean) -> Unit,
  composer: @Composable (Boolean, Boolean) -> Unit,
) {
  val onShowThinkingDetail: (String) -> Unit = { text ->
    onToolDetail(
      ToolActivityDetail(
        key = "thinking-history",
        title = "思考过程",
        kind = CompletedToolKind.Other,
        detailText = text,
        detailLabel = "思考内容",
        iconOverride = Icons.Outlined.Lightbulb,
      )
    )
  }
  val baseTimeline =
    remember(messages, activeRunCount, pendingToolCalls, subagentActivities, questions, streamingAssistantText, streamingThinkingText, outboxItems, recoveryOutboxItems) {
      buildChatTimeline(
        messages = messages,
        pendingRunCount = activeRunCount,
        pendingToolCalls = pendingToolCalls,
        streamingAssistantText = streamingAssistantText,
        streamingThinkingText = streamingThinkingText,
        subagentActivities = subagentActivities,
        outboxItems = outboxItems,
        recoveryOutboxItems = recoveryOutboxItems,
        questions = questions,
      )
    }
  val context = LocalContext.current
  val indicatorVisible = activeRunCount > 0
  val workingRunTracker = remember(sessionKey) { ChatWorkingRunTracker(sessionKey) }
  val workingRun =
    workingRunTracker.resolve(
      indicatorVisible = indicatorVisible,
      clockKey = activeRunClockKey,
      authoritativeRunId = activeRunId,
      nowElapsedMs = SystemClock.elapsedRealtime(),
      outputTokens = activeRunOutputTokens,
    )
  val turnRecapResolver = remember { TurnRecapResolver() }
  val turnRecap =
    turnRecapResolver.resolve(
      sessionKey = sessionKey,
      indicatorVisible = indicatorVisible,
      row = session,
      transcript =
        TurnRecapTranscriptState(
          sessionKey = transcriptAnchor?.sessionKey,
          newestItemId = transcriptAnchor?.newestItemId,
          completedEndedAt = transcriptAnchor?.completedEndedAt,
          completedNewestItemId = transcriptAnchor?.completedNewestItemId,
        ),
    )
  val timeline =
    remember(baseTimeline, turnRecap, activeRunCount, sessionKey) {
      baseTimeline.withWorkDurationLabels(messages, activeRunCount > 0, sessionKey).withTurnRecap(turnRecap)
    }
  // Rows that show up one at a time (a new thinking block, a finished tool)
  // fade in; bulk changes such as a history load never do.
  // Keyed off the row *count*, not the item list: streaming rewrites the list
  // on every token, and rebuilding all keys (plus restarting the effect) that
  // often is what made the transcript stutter mid-answer.
  val revealTracker = remember(sessionKey) { ChatChainRevealTracker() }
  val rowCount = timeline.items.size
  val revealKeys =
    remember(sessionKey, rowCount) { revealTracker.animateKeys(timeline.items.map(::chatTimelineItemKey)) }
  LaunchedEffect(sessionKey, rowCount) { revealTracker.mark(timeline.items.map(::chatTimelineItemKey)) }
  // Which folded step groups the user has opened. Keyed by the fold's own key,
  // so it survives recomposition but resets when the session changes.
  val expandedSteps = remember(sessionKey) { mutableStateMapOf<String, Boolean>() }
  // The answer arrives as one chunk, so the typewriter reveal lives here (per
  // session) instead of inside a timeline row that may be replaced any frame.
  val answerTypewriter = rememberChatAnswerTypewriter(sessionKey)
  // "This answer just arrived" cannot be decided from the gateway timestamp
  // alone: a long reasoning turn stamps the message when the turn starts, so by
  // the time the body lands the timestamp is older than the freshness window and
  // the reveal is silently skipped — the body then pops in as one block. Seeing
  // a live body in this composition is the reliable handoff signal.
  var liveAnswerObserved by remember(sessionKey) { mutableStateOf(false) }
  LaunchedEffect(streamingAssistantText) {
    if (!streamingAssistantText.isNullOrBlank()) liveAnswerObserved = true
  }
  val answerArrivedLive =
    liveAnswerObserved &&
      timeline.items.any {
        it is ChatTimelineItem.Message && it.message.role.equals("assistant", ignoreCase = true)
      }
  val latestAssistantIndex =
    timeline.items.indexOfFirst { item ->
      item is ChatTimelineItem.Message &&
        item.message.role.trim().equals("assistant", ignoreCase = true) &&
        item.message.content.any { part -> part.type == "text" && !part.text.isNullOrBlank() }
    }
  val readerScroll =
    rememberChatReaderScrollController(
      sessionKey = sessionKey,
      timeline = timeline,
      historyLoading = historyLoading,
    )
  // Handle drags past the transcript edge scroll it, so the selection keeps growing under the
  // finger; the list owns that scroll.
  SideEffect {
    textSelection.autoScroll = { delta -> readerScroll.listState.dispatchRawDelta(delta) }
  }
  DisposableEffect(sessionKey, turnRecapResolver) {
    onDispose { turnRecapResolver.abandonActiveWatch(sessionKey) }
  }
  val messageListScope = rememberCoroutineScope()

  fun jumpToQuotedMessage(
    fromIndex: Int,
    quote: String,
  ) {
    val target = findQuotedChatMessageIndex(timeline.items, fromIndex, quote)
    if (target == null) {
      Toast
        .makeText(
          context,
          nativeString("Quoted message is not in this conversation."),
          Toast.LENGTH_SHORT,
        ).show()
      return
    }
    readerScroll.navigation.launch(messageListScope) {
      readerScroll.listState.animateScrollToItem(target)
    }
  }

  BackHandler(enabled = textSelection.active) { textSelection.exit() }
  val shareSelectionActive = shareSelection != null
  val shareableMessageIds =
    remember(timeline.items) { chatShareableMessages(timeline.items).mapNotNull { it.entryId }.toSet() }
  // Prune ids the timeline no longer carries (compaction, branch switch) before
  // they reach the confirm payload.
  val selectedShareIds =
    remember(shareSelection, shareableMessageIds) {
      shareSelection?.intersect(shareableMessageIds).orEmpty()
    }

  val density = LocalDensity.current
  val headerTextHeight = minimumChatLineHeight(chatProjectStyle()) + minimumChatLineHeight(chatTitleStyle())
  val readerLineHeight = minimumChatLineHeight(ClawTheme.type.body)
  val minimumHeaderHeight =
    with(density) {
      maxOf(ClawTheme.spacing.touchTarget.roundToPx(), headerTextHeight).toDp()
    }
  val minimumReaderHeight =
    with(density) {
      maxOf(ClawTheme.spacing.touchTarget.roundToPx(), readerLineHeight).toDp()
    }
  // The overlay renders above the whole pane — above the header too — so the
  // morph menu's glass can grow from the header capsule over the transcript.
  // It sits outside ChatPaneLayout: that layout's slots are measured for the
  // header/transcript/composer split and would clip a pane-sized layer.
  //
  // The same surface is published downward as the pane's morph host, so the
  // composer's ＋ and model menus and a long-pressed message claim it instead of
  // each owning an overlay they would then have to position themselves.
  CompositionLocalProvider(LocalClawMorphMenuHost provides morphHost) {
    Box(modifier = modifier) {
    ChatPaneLayout(
    tabletopPanes = tabletopPanes,
    features = features,
    minimumInputHeight = minimumChatInputHeight(composerStyle),
    composerBottomInset = composerBottomInset,
    minimumHeaderHeight = minimumHeaderHeight,
    minimumReaderHeight = minimumReaderHeight,
    touchTarget = ClawTheme.spacing.touchTarget,
    // Fill the wrapping overlay Box; IME padding moves pane and overlay together.
    modifier = Modifier.fillMaxSize(),
    headerModifier = headerModifier,
    bodyModifier = bodyModifier,
    backdropSourceModifier = backdropSourceModifier,
    header = { compact, tabletop -> header(compact, tabletop) },
    status = conversationStatus,
    composer = { compact, tabletop -> composer(compact, tabletop) },
    transcript = { headerReserve ->
      CompositionLocalProvider(
        LocalChatReaderNavigation provides readerScroll.navigation,
        // Long-press menus are launched from deep inside the rows; publishing
        // here spares every bubble a `hazeState` parameter.
        LocalClawHazeState provides hazeState,
        LocalChatTextSelectionController provides textSelection,
        // The registry stays on for the whole transcript: Text only reports its layout
        // once, so registering on demand would leave selection mode without geometry.
        LocalChatTextHitRegistry provides textSelection.registry,
      ) {
        ChatMessageDisclosure(
          messages = messages,
          owner = fullMessageOwner,
          selectionGeneration = selectionGeneration,
          catalogRevision = gatewayCatalogRevision,
          prepareRead = prepareFullMessageRead,
        ) { visibleContent, disclosure ->
          Box(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            LazyColumn(
              modifier = Modifier.fillMaxSize().nestedScroll(readerScroll.nestedScrollConnection).onGloballyPositioned(readerScroll.navigation.anchors::viewportPlaced),
              state = readerScroll.listState,
              reverseLayout = true,
              // reverseLayout swaps contentPadding top/bottom semantics: the
              // padding above the oldest message (visually the top) is the
              // `top` argument. The header reserve goes there so the oldest
              // message clears the bar when the list is scrolled all the way
              // up, while the list itself still slides underneath the bar.
              // The transcript extends behind the glass composer so its backdrop
              // remains visible, but the newest row still needs a scrollable
              // landing zone above that overlay.
              contentPadding =
                PaddingValues(
                  top = 3.dp + headerReserve,
                  // The list still reaches the pane's bottom edge so the glass
                  // keeps content behind it, but the resting scroll position
                  // leaves the newest row clear of the floating bar.
                  bottom =
                    if (composerStyle == ComposerBarStyle.Simple) {
                      chatComposerClearance(composerStyle, composerBottomInset)
                    } else {
                      6.dp + minimumChatInputHeight()
                    },
                ),
            ) {
              itemsIndexed(items = timeline.items, key = { _, item -> chatTimelineItemKey(item) }) { index, item ->
                // reverseLayout: the item visually above this one sits at index + 1.
                val spacingAbove = chatTimelineRowSpacing(timeline.items.getOrNull(index + 1), item)
                val revealKey = chatTimelineItemKey(item)
                // Consumed once per key: recycling the row later must not replay it.
                val animateReveal = remember(revealKey, revealKeys) { revealTracker.consume(revealKey) }
                // Process rows are siblings of the message bubble, not its
                // children: without the same share-picker inset a thinking or
                // tool chain would stay behind while its answer glides right.
                val shiftsWithSharePicker =
                  item is ChatTimelineItem.Thinking ||
                    item is ChatTimelineItem.PendingTools ||
                    item is ChatTimelineItem.CompletedTools ||
                    item is ChatTimelineItem.SubagentActivity ||
                    item is ChatTimelineItem.CollapsedSteps ||
                    item is ChatTimelineItem.StreamingAssistant
                val shareRowInset =
                  rememberChatShareStartInset(shareSelectionActive, shiftsWithSharePicker)
                Column(modifier = Modifier.padding(top = spacingAbove).offset(x = shareRowInset)) {
                ChatReaderItem(revealKey) {
                  when (item) {
                    is ChatTimelineItem.Message -> {
                      // reverseLayout puts the newest row at index 0. The typewriter is
                      // always handed to the latest assistant bubble so the reveal
                      // continues seamlessly across the live->persisted handoff; only an
                      // answer that just arrived animates - history never replays.
                      val isLatestAssistant = index == latestAssistantIndex
                      val typingAnswer =
                        isLatestAssistant &&
                          (chatAnswerIsFresh(item.message.timestampMs) || answerArrivedLive)
                      // Selection keys are server-side transcript ids (entryId):
                      // the create endpoint has never heard of the client-local
                      // ChatMessage.id, and rows without an entryId cannot be shared.
                      val shareEntryId = chatShareableEntryId(item)
                      // Only agent bubbles travel right; a user bubble is already
                      // clear of the checkbox rail on the left.
                      val agentRow = item.message.role.trim().equals("assistant", ignoreCase = true)
                      // Never hand the long-press menu a null share action: its
                      // fallback is the legacy direct text share. Rows restored
                      // from the transcript cache carry no entryId until the
                      // live history refresh lands, so a tap in that window
                      // explains the wait instead of silently switching flows.
                      val shareSeed: () -> Unit = {
                        val id = chatShareableEntryId(item)
                        if (id == null) {
                          Toast.makeText(context, "会话还在同步，请稍后再分享", Toast.LENGTH_SHORT).show()
                        } else {
                          // Freeze following before the picker opens: a new
                          // stream delta must not yank the list while the user
                          // is choosing older rows.
                          readerScroll.navigation.pause()
                          onEnterShareSelection(
                            chatShareSelectionSeed(timeline.items, id),
                            shareableMessageIds,
                          )
                        }
                      }
                      ChatShareSelectionRow(
                        selectable = shareEntryId != null,
                        shiftContent = shareEntryId != null && agentRow,
                        selectionActive = shareSelectionActive,
                        selected = shareEntryId != null && shareEntryId in selectedShareIds,
                        onToggle = { shareEntryId?.let(onToggleShareSelection) },
                        testTag = "chat-share-select:${item.message.id}",
                      ) {
                        ChatBubble(
                          typewriter = answerTypewriter.takeIf { isLatestAssistant },
                          typewriterAnimate = typingAnswer,
                          messageId = item.message.id,
                          entryId = item.message.entryId,
                          role = item.message.role,
                          live = false,
                          content = visibleContent(item.message).filter { it.toolActivity == null },
                          timestampMs = item.message.timestampMs.takeIf { item.showTimestamp },
                          workDurationMs = item.workDurationMs,
                          onQuoteMessage = onQuoteMessage,
                          onQuoteLinkClick = { quote -> jumpToQuotedMessage(index, quote) },
                          sessionActionsEnabled = sessionActionsEnabled,
                          onRewindMessage = onRewindMessage,
                          onForkMessage = onForkMessage,
                          onShare = shareSeed,
                          selectionActive = shareSelectionActive,
                          speechState = speechState,
                          onToggleListen = onToggleListen,
                          inlineMediaPlaybackBlocked = inlineMediaPlaybackBlocked,
                          inlineWidgetResolverReady = healthOk,
                          resolveInlineWidgetResource = resolveInlineWidgetResource,
                          loadImageArtifact = loadImageArtifact,
                          loadMediaArtifact = loadMediaArtifact,
                          senderLabel = item.message.senderLabel,
                          disclosure = { disclosure(item.message) },
                          onShowThinking = onShowThinkingDetail,
                        )
                      }
                    }

                    is ChatTimelineItem.OutboxCommand -> {
                      ChatOutboxBubble(
                        item = item.item,
                        onRetry = { onRetryOutbox(item.item.id) },
                        onDelete = { onDeleteOutbox(item.item.id) },
                        onQuoteLinkClick = { quote -> jumpToQuotedMessage(index, quote) },
                      )
                    }

                    is ChatTimelineItem.RecoveryOutboxCommand -> {
                      ChatOutboxBubble(
                        item = item.item,
                        retryEnabled = false,
                        onRetry = { onRetryOutbox(item.item.id) },
                        onDelete = { onDeleteOutbox(item.item.id) },
                        onQuoteLinkClick = { quote -> jumpToQuotedMessage(index, quote) },
                      )
                    }

                    is ChatTimelineItem.OutboxRecoveryHeader -> {
                      ChatNotice(
                        title = nativeString("Messages to recover"),
                        body =
                          nativeString(
                            "\${item.count} message(s) need recovery. Re-enter anything you want to keep, then delete these rows.",
                            item.count,
                          ),
                      )
                    }

                    is ChatTimelineItem.PendingTools -> {
                      ChatChainReveal(revealKey = revealKey, animate = animateReveal) {
                        ToolBubble(toolCalls = item.toolCalls, onToolClick = onToolDetail)
                      }
                    }

                    is ChatTimelineItem.CompletedTools -> {
                      ChatChainReveal(revealKey = revealKey, animate = animateReveal) {
                        CompletedToolActivity(
                          tools = item.tools,
                          stableKey = item.key,
                          runningIds = item.runningIds,
                          onToolDetail = onToolDetail,
                        )
                      }
                    }

                    is ChatTimelineItem.SubagentActivity -> {
                      ChatChainReveal(revealKey = revealKey, animate = animateReveal) {
                        SubagentActivityRows(
                          activities = item.activities,
                          moreWorkingCount = item.moreWorkingCount,
                        )
                      }
                    }

                    is ChatTimelineItem.CollapsedSteps -> {
                      ChatChainReveal(revealKey = revealKey, animate = animateReveal) {
                        CollapsedStepsBlock(
                          steps = item.steps,
                          expanded = expandedSteps[item.key] == true,
                          onToggleExpanded = { expandedSteps[item.key] = expandedSteps[item.key] != true },
                          onToolDetail = onToolDetail,
                        )
                      }
                    }

                    is ChatTimelineItem.QuestionPrompt -> {
                      ChatQuestionCard(prompt = item.prompt, onDraftChanged = onQuestionDraftChanged, onSubmit = onResolveQuestion, onSkip = onSkipQuestion)
                    }

                    is ChatTimelineItem.TurnRecapSummary -> {
                      ChatTurnRecapRow(item.recap)
                    }

                    is ChatTimelineItem.SystemNotice -> {
                      ChatSystemNoticeRow(item)
                    }

                    is ChatTimelineItem.SystemDivider -> {
                      ChatSystemDividerRow(item)
                    }

                    is ChatTimelineItem.StreamingAssistant -> {
                      ChatBubble(
                        typewriter = answerTypewriter,
                        typewriterAnimate = true,
                        messageId = null,
                        entryId = null,
                        role = "assistant",
                        live = true,
                        content = listOf(ChatMessageContent(text = item.text)),
                        timestampMs = null,
                        onQuoteMessage = onQuoteMessage,
                        onQuoteLinkClick = null,
                        sessionActionsEnabled = false,
                        onRewindMessage = onRewindMessage,
                        onForkMessage = onForkMessage,
                        speechState = null,
                        onToggleListen = onToggleListen,
                        inlineMediaPlaybackBlocked = inlineMediaPlaybackBlocked,
                        inlineWidgetResolverReady = healthOk,
                        resolveInlineWidgetResource = resolveInlineWidgetResource,
                        loadImageArtifact = loadImageArtifact,
                        loadMediaArtifact = loadMediaArtifact,
                      )
                    }

                    is ChatTimelineItem.Thinking -> {
                      ChatChainReveal(revealKey = revealKey, animate = animateReveal) {
                        if (item.text.isNotBlank()) {
                          ChatThinkingBlock(
                            text = item.text,
                            streaming = item.streaming,
                            onClick = { onToolDetail(thinkingToolDetail(item)) },
                          )
                        } else {
                          // No reasoning streamed (or the model is not thinking):
                          // fall back to the plain activity indicator.
                          val run = workingRun
                          if (run != null) {
                            ChatTypingIndicatorBubble(
                              runKey = run.clockKey,
                              observedAtElapsedMs = run.observedAtElapsedMs,
                              outputTokens = run.outputTokens,
                            )
                          }
                        }
                      }
                    }
                    }
                  }
                }
              }
            }

            if (timeline.items.isEmpty()) {
              if (showChatLoadingPlaceholder(historyLoading = historyLoading, healthOk = healthOk, gatewayOffline = gatewayOffline)) {
                ClawLoadingState(title = nativeString("Loading thread"), modifier = Modifier.align(Alignment.Center))
              } else {
                EmptyChatHint(
                  healthOk = healthOk,
                  gatewayOffline = gatewayOffline,
                  onStarterPrompt = onStarterPrompt,
                  modifier = Modifier.align(Alignment.Center),
                )
              }
            }
            // Floating "back to latest" pill: it hides while the list is moving so it
            // never rides along under the finger, and returns once the scroll settles.
            // The share picker owns the same bottom slot, so it retires it entirely.
            ClawJumpToLatestButton(
              visible =
                readerScroll.showJumpToLatest &&
                  !readerScroll.scrolling &&
                  !shareSelectionActive &&
                  !textSelection.active,
              onClick = readerScroll.jumpToLatest,
              modifier =
                Modifier
                  .align(Alignment.BottomCenter)
                  .padding(bottom = chatComposerClearance(composerStyle, composerBottomInset)),
            )
          }
        }
      }
    },
    )
    // Above every pane slot, including the header the capsule lives in.
    overlay()
    }
  }
}

internal data class ChatWorkingRun(
  val clockKey: String,
  val observedAtElapsedMs: Long,
  val authoritativeRunId: String?,
  val outputTokens: Long?,
)

internal class ChatWorkingRunTracker(
  private val sessionKey: String,
) {
  private var current: ChatWorkingRun? = null

  fun resolve(
    indicatorVisible: Boolean,
    clockKey: String?,
    authoritativeRunId: String?,
    nowElapsedMs: Long,
    outputTokens: Long?,
  ): ChatWorkingRun? {
    if (!indicatorVisible) {
      current = null
      return null
    }
    val resolvedClockKey = clockKey ?: "$sessionKey:active"
    val previous = current
    if (previous == null || previous.clockKey != resolvedClockKey) {
      return ChatWorkingRun(
        clockKey = resolvedClockKey,
        observedAtElapsedMs = nowElapsedMs,
        authoritativeRunId = authoritativeRunId,
        outputTokens = outputTokens,
      ).also { current = it }
    }
    if (previous.authoritativeRunId != authoritativeRunId || previous.outputTokens != outputTokens) {
      current =
        previous.copy(
          authoritativeRunId = authoritativeRunId,
          outputTokens = outputTokens,
        )
    }
    return current
  }
}

internal const val CHAT_OFFLINE_NOTICE_DELAY_MS = 10_000L

internal fun isTransientGatewayConnectionStatus(statusText: String): Boolean =
  statusText.trim() == "Connecting…" || statusText.trim() == "Reconnecting…"

internal fun shouldKeepGatewayOfflineNoticeInline(isNetworkFailure: Boolean): Boolean = isNetworkFailure

internal fun showChatLoadingPlaceholder(
  historyLoading: Boolean,
  healthOk: Boolean,
  gatewayOffline: Boolean,
): Boolean = historyLoading && !healthOk && !gatewayOffline

@Composable
private fun EmptyChatHint(
  healthOk: Boolean,
  gatewayOffline: Boolean,
  onStarterPrompt: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(
        text =
          if (healthOk) {
            nativeString("Ready when you are")
          } else if (gatewayOffline) {
            nativeString("Gateway offline")
          } else {
            nativeString("Chat not ready")
          },
        style = ClawTheme.type.title.copy(fontSize = 18.sp, lineHeight = 23.sp),
        color = ClawTheme.colors.text,
      )
      Text(
        text =
          if (healthOk) {
            nativeString("Start with a prompt, or use voice.")
          } else if (gatewayOffline) {
            nativeString("Use the recovery options below to reconnect.")
          } else {
            nativeString("Use Refresh chat to check Gateway health.")
          },
        style = ClawTheme.type.body,
        color = ClawTheme.colors.textMuted,
        textAlign = TextAlign.Center,
      )
    }
    if (healthOk) {
      StarterPromptList(onStarterPrompt = onStarterPrompt)
    }
  }
}

@Composable
private fun ChatOfflineActions(
  onFixConnection: () -> Unit,
  onCopyDiagnostics: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(
    modifier = modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    ClawPrimaryButton(text = nativeString("Fix connection"), icon = Icons.Default.Cloud, onClick = onFixConnection, modifier = Modifier.fillMaxWidth())
    ClawSecondaryButton(text = nativeString("Copy diagnostics"), icon = Icons.Default.ContentCopy, onClick = onCopyDiagnostics, modifier = Modifier.fillMaxWidth())
  }
}

@Composable
private fun StarterPromptList(onStarterPrompt: (String) -> Unit) {
  ClawPanel(contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
    Column {
      starterPrompts.forEachIndexed { index, prompt ->
        val message = prompt.message.resolveNativeTextResource()
        StarterPromptRow(prompt = prompt, onClick = { onStarterPrompt(message) })
        if (index != starterPrompts.lastIndex) {
          HorizontalDivider(color = ClawTheme.colors.border, thickness = 1.dp)
        }
      }
    }
  }
}

@Composable
private fun StarterPromptRow(
  prompt: StarterPrompt,
  onClick: () -> Unit,
) {
  Surface(onClick = onClick, color = Color.Transparent, contentColor = ClawTheme.colors.text) {
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(30.dp)
            .background(ClawTheme.colors.surfacePressed, RoundedCornerShape(ClawTheme.radii.row)),
        contentAlignment = Alignment.Center,
      ) {
        Text(text = prompt.mark, style = ClawTheme.type.label, color = ClawTheme.colors.text)
      }
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(text = prompt.title.resolveNativeTextResource(), style = ClawTheme.type.body, color = ClawTheme.colors.text, maxLines = 1)
        Text(text = prompt.subtitle.resolveNativeTextResource(), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

internal data class StarterPrompt(
  val mark: String,
  val title: NativeText,
  val subtitle: NativeText,
  val message: NativeText,
)

/** Default prompts shown only for an empty, connected session. */
internal val starterPrompts =
  listOf(
    StarterPrompt(
      mark = "1",
      title = nativeText("Catch me up"),
      subtitle = nativeText("Summarize recent threads and next steps."),
      message = nativeText("Catch me up on my recent OpenClaw threads and suggest next steps."),
    ),
    StarterPrompt(
      mark = "2",
      title = nativeText("Plan the work"),
      subtitle = nativeText("Turn a goal into an actionable checklist."),
      message = nativeText("Help me turn this goal into a practical checklist: "),
    ),
    StarterPrompt(
      mark = "3",
      title = nativeText("Use this phone"),
      subtitle = nativeText("Ask OpenClaw to use Android capabilities."),
      message = nativeText("What can you help me do from this phone right now?"),
    ),
  )

@Composable
internal fun ChatBubble(
  messageId: String?,
  entryId: String?,
  role: String,
  live: Boolean,
  content: List<ChatMessageContent>,
  timestampMs: Long?,
  onQuoteMessage: (ChatQuoteDraft) -> Unit,
  /** Runs when the quote link is tapped; null on rows that cannot search the timeline. */
  onQuoteLinkClick: ((String) -> Unit)? = null,
  sessionActionsEnabled: Boolean,
  onRewindMessage: (String) -> Unit,
  onForkMessage: (String) -> Unit,
  /** Null keeps the legacy direct chooser; the full transcript supplies the batch entry. */
  onShare: (() -> Unit)? = null,
  /** True while the batch picker is open: the long-press menu must stay shut. */
  selectionActive: Boolean = false,
  speechState: MessageSpeechState?,
  onToggleListen: (String, String) -> Unit,
  inlineMediaPlaybackBlocked: Boolean,
  inlineWidgetResolverReady: Boolean,
  resolveInlineWidgetResource: suspend (String, ChatWidgetResource?) -> ChatWidgetResource?,
  loadImageArtifact: suspend (String) -> GatewayLoadedImage?,
  loadMediaArtifact: suspend (String, GatewayMediaKind, Boolean) -> GatewayLoadedMedia?,
  senderLabel: String? = null,
  workDurationMs: Long? = null,
  onShowThinking: (String) -> Unit = {},
  disclosure: @Composable () -> Unit = {},
  typewriter: ChatAnswerTypewriter? = null,
  typewriterAnimate: Boolean = false,
) {
  val normalizedRole = role.trim().lowercase(Locale.US)
  val isUser = normalizedRole == "user"
  val peerSenderLabel = senderLabel?.trim()?.takeIf { isUser && it.isNotEmpty() }
  val speaker =
    when {
      isUser -> peerSenderLabel ?: nativeString("You")
      normalizedRole == "system" -> nativeString("System")
      else -> nativeString("OpenClaw")
    }
  val caption =
    when {
      live -> nativeString("OpenClaw · Live")
      normalizedRole == "system" -> nativeString("System")
      peerSenderLabel != null -> peerSenderLabel
      else -> null
    }
  var visibleImageCount = 0
  val displayableContent =
    content.filter { part ->
      when (part.type) {
        "text" -> {
          !part.text.isNullOrBlank()
        }

        "thinking" -> {
          !part.text.isNullOrBlank()
        }

        "image" -> {
          val visible = visibleImageCount < 4
          visibleImageCount += 1
          visible
        }

        "canvas" -> {
          normalizedRole == "assistant" && part.widget != null
        }

        else -> {
          part.type == "file" || part.isAudioAttachment() || part.isVideoAttachment()
        }
      }
    }
    // Reasoning always renders above the answer text, even when the gateway
    // persisted the blocks in the opposite order. sortedBy is stable, so the
    // relative order of same-type parts is preserved.
    .sortedBy { part -> if (part.type == "thinking") 0 else 1 }
  val omittedImageCount = (visibleImageCount - 4).coerceAtLeast(0)
  val quoted =
    if (isUser) {
      splitQuotedChatContent(displayableContent)
    } else {
      null
    }
  val renderedContent = quoted?.content ?: displayableContent
  if (renderedContent.isEmpty() && quoted == null) return

  val textSelection = LocalChatTextSelectionController.current
  val textSelectionActive = textSelection?.activeMessageId == messageId
  val anyTextSelection = textSelection?.active == true
  val messageText = chatMessagePlainText(renderedContent)
  fun currentQuoteText(): String =
    chatMessageDisplayText(renderedContent)
      .ifBlank { messageText }
      .trim()
  // The newest answer types itself out after the gateway row lands; keep the menu out of the
  // way while either the live row or the reveal still owns the text.
  val typingText = renderedContent.lastOrNull { it.type == "text" }?.text.orEmpty()
  val outputActive =
    live ||
      (typewriter != null && typewriterAnimate && typewriter.isRevealing(typingText))
  val collapsibleUserText =
    shouldUseUserMessageDisclosure(isUser, renderedContent) && !textSelectionActive
  var userMessageExpanded by rememberSaveable(messageId, messageText) { mutableStateOf(false) }
  val messageSpeech = speechState?.takeIf { it.messageId == messageId }
  val canListen = !live && messageId != null && normalizedRole == "assistant" && messageText.isNotBlank()
  val toggleListen: (() -> Unit)? =
    if (canListen) {
      { onToggleListen(checkNotNull(messageId), messageText) }
    } else {
      null
    }
  val selectionPress: ((ChatTextPress) -> Unit)? =
    if (messageId != null && textSelection != null) {
      { press -> textSelection.enter(messageId, press) }
    } else {
      null
    }
  LaunchedEffect(textSelectionActive) {
    if (textSelectionActive) textSelection?.selectInitialSelection()
  }
  DisposableEffect(textSelectionActive) {
    onDispose { if (textSelectionActive) textSelection?.exit() }
  }

  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
  ) {
    // The host box caps how wide a bubble may grow, but the bubble itself hugs its
    // text, so it has to be pushed to the box's trailing edge — otherwise every
    // message lines up on the left with a different right-hand gap.
    Box(
      modifier =
        Modifier
          .fillMaxWidth(chatBubbleWidthFraction(isUser))
          .semantics(mergeDescendants = true) { contentDescription = speaker },
      contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.TopStart,
    ) {
      ChatMessageActionHost(
        text = messageText,
        onQuote = { onQuoteMessage(ChatQuoteDraft(currentQuoteText(), messageId)) },
        onSelectText = selectionPress,
        showSessionActions = isUser && entryId != null && sessionActionsEnabled,
        onRewind = entryId?.let { value -> { onRewindMessage(value) } },
        onFork = entryId?.let { value -> { onForkMessage(value) } },
        onShare = onShare,
        enabled = !outputActive && !selectionActive && !anyTextSelection,
        listenActive = messageSpeech?.isActive == true,
        onToggleListen = toggleListen,
      ) {
      // A message that carries only reasoning has no reply of its own; the
      // timestamp belongs to the final answer message, so skip it here to
      // avoid one clock per chain segment.
      val thinkingOnly = displayableContent.all { it.type == "thinking" }
      val timestampText = timestampMs?.takeIf { !thinkingOnly }?.let {
        val durationLabel = workedDurationLabel(workDurationMs)
        if (durationLabel != null) "$durationLabel · ${formatChatTimestamp(it)}" else formatChatTimestamp(it)
      }
      Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
      Surface(
        // A user bubble hugs its text and only wraps when it reaches the width
        // cap imposed by the action host above; assistant content stays full width.
        modifier = if (isUser) Modifier else Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (isUser) CHAT_BUBBLE_CORNER_RADIUS_DP.dp else 0.dp),
        color = if (isUser) ClawTheme.colors.userMessageSurface else Color.Transparent,
        contentColor = ClawTheme.colors.text,
        border = null,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
      ) {
        Column(
          modifier =
            if (isUser) {
              Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            } else {
              Modifier.padding(vertical = 4.dp)
            },
          verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
          quoted?.let { quote ->
            ChatQuotedMessageLink(
              onClick = onQuoteLinkClick?.let { click -> { click(quote.quote) } },
            )
          }
          val bubbleBody: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              caption?.let {
                Text(
                  text = it,
                  style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium),
                  color = ClawTheme.colors.textMuted,
                )
              }
              if (collapsibleUserText && messageText.isNotBlank()) {
                ChatUserMessageText(
                  textParts = renderedContent.mapNotNull { it.text },
                  plainText = messageText,
                  expanded = userMessageExpanded || textSelectionActive,
                  onExpandedChange = { userMessageExpanded = it },
                )
              }
              // Only the trailing text block of a freshly arrived answer types itself out.
              val typewriterPartIndex =
                if (typewriter == null) -1 else renderedContent.indexOfLast { part -> part.type == "text" }
              renderedContent.forEachIndexed { partIndex, part ->
                when {
                  part.type == "thinking" -> {
                    ChatThinkingBlock(
                      text = part.text.orEmpty(),
                      streaming = false,
                      onClick = { onShowThinking(part.text.orEmpty()) },
                    )
                  }

                  part.type == "text" && !collapsibleUserText -> {
                    val rawText = part.text.orEmpty()
                    val typing = partIndex == typewriterPartIndex
                    if (typing && typewriterAnimate) {
                      LaunchedEffect(rawText, typewriterAnimate) { typewriter?.run(rawText, typewriterAnimate) }
                    }
                    ChatText(
                      text = if (typing && typewriterAnimate) checkNotNull(typewriter).visible(rawText, typewriterAnimate) else rawText,
                      textColor = ClawTheme.colors.text,
                      isStreaming = live,
                      centered = isUser,
                    )
                  }

                  part.type == "text" -> {}

                  part.isAudioAttachment() && part.hasPlayableMediaArtifact() -> {
                    ChatAudioPlayerCard(
                      content = part,
                      playbackBlocked = inlineMediaPlaybackBlocked,
                      loadMedia = loadMediaArtifact,
                    )
                  }

                  part.isVideoAttachment() && part.hasPlayableMediaArtifact() -> {
                    ChatVideoPlayerCard(
                      content = part,
                      playbackBlocked = inlineMediaPlaybackBlocked,
                      loadMedia = loadMediaArtifact,
                    )
                  }

                  part.isAudioAttachment() || part.isVideoAttachment() -> {
                    ChatMediaAttachmentLabel(content = part)
                  }

                  part.isImageAttachment() && !part.base64.isNullOrBlank() -> {
                    ChatBase64Image(base64 = part.base64, mimeType = part.mimeType)
                  }

                  part.isImageAttachment() && !part.artifactId.isNullOrBlank() -> {
                    ChatManagedImage(
                      artifactId = part.artifactId,
                      label = part.alt?.takeIf(String::isNotBlank) ?: part.fileName ?: nativeString("Image"),
                      resolverReady = inlineWidgetResolverReady,
                      loadImage = loadImageArtifact,
                    )
                  }

                  part.type == "canvas" && normalizedRole == "assistant" -> {
                    ChatInlineWidget(
                      preview = checkNotNull(part.widget),
                      resolverReady = inlineWidgetResolverReady,
                      resolveResource = resolveInlineWidgetResource,
                    )
                  }

                  else -> {
                    Text(text = part.fileName ?: nativeString("Attachment"), style = ClawTheme.type.body, color = ClawTheme.colors.textMuted)
                  }
                }
              }
              if (omittedImageCount > 0) {
                Text(
                  text = nativeString("Additional images hidden: \${omittedImageCount}", omittedImageCount),
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
              if (messageId != null) {
                ChatMessageLinkPreview(messageId = messageId, role = normalizedRole, content = renderedContent)
              }
              disclosure()
              messageSpeech?.let { speech ->
                FullChatSpeechIndicator(
                  phase = speech.phase,
                  onToggle = { onToggleListen(checkNotNull(messageId), messageText) },
                )
              }
            }
          }
          CompositionLocalProvider(LocalChatMarkdownNativeSelection provides false) {
            bubbleBody()
          }
        }
      }
      timestampText?.let { text ->
        Text(
          text = text,
          style = ClawTheme.type.caption.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal),
          color = ClawTheme.colors.textSubtle.copy(alpha = 0.7f),
          modifier = Modifier.padding(top = 3.dp, end = if (isUser) 4.dp else 0.dp, start = if (isUser) 0.dp else 4.dp),
        )
      }
      }
    }
    }
  }
}

internal fun chatBubbleWidthFraction(isUser: Boolean): Float = if (isUser) 0.88f else 1f

/** Chain rows (reasoning / tool activity) belong to one agent turn and sit tight together. */
private fun ChatTimelineItem.isChatChainRow(): Boolean =
  when (this) {
    is ChatTimelineItem.Thinking,
    is ChatTimelineItem.PendingTools,
    is ChatTimelineItem.CompletedTools,
    is ChatTimelineItem.CollapsedSteps,
    is ChatTimelineItem.StreamingAssistant,
    -> true

    is ChatTimelineItem.Message -> {
      if (!message.role.trim().equals("assistant", ignoreCase = true)) {
        false
      } else {
        val visible = message.content.filter { it.toolActivity == null }
        visible.isNotEmpty() && visible.all { it.type == "thinking" }
      }
    }

    else -> false
  }

/**
 * Vertical gap above [below]: chain rows hug each other, messages keep the
 * classic breathing room, and the chain-to-answer transition gets a small gap.
 * [above] is the item rendered visually on top (list index + 1 in reverseLayout).
 */
private fun chatTimelineRowSpacing(
  above: ChatTimelineItem?,
  below: ChatTimelineItem,
): Dp =
  when {
    above == null -> 0.dp
    // Chain rows already carry 3.dp of their own vertical padding, so 0 here
    // leaves a ~6-7.dp gap - the same rhythm as the 4.dp between text lines.
    above.isChatChainRow() || below.isChatChainRow() -> 0.dp
    else -> 12.dp
  }

/** User bubbles are wide rounded rectangles, never full capsules. */
internal const val CHAT_BUBBLE_CORNER_RADIUS_DP = 16

@Composable
private fun FullChatSpeechIndicator(
  phase: MessageSpeechPhase,
  onToggle: () -> Unit,
) {
  Surface(
    onClick = onToggle,
    shape = RoundedCornerShape(999.dp),
    color = ClawTheme.colors.surfacePressed,
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector =
          when (phase) {
            MessageSpeechPhase.Preparing -> Icons.Default.HourglassEmpty
            MessageSpeechPhase.Speaking -> Icons.AutoMirrored.Filled.VolumeUp
            MessageSpeechPhase.Failed -> Icons.Default.Refresh
          },
        contentDescription = null,
        modifier = Modifier.size(14.dp),
        tint = ClawTheme.colors.textMuted,
      )
      Text(
        text =
          when (phase) {
            MessageSpeechPhase.Preparing -> nativeString("Preparing audio…")
            MessageSpeechPhase.Speaking -> nativeString("Speaking…")
            MessageSpeechPhase.Failed -> nativeString("Audio error · Retry")
          },
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textMuted,
      )
    }
  }
}

@Composable
private fun ChatUserMessageText(
  textParts: List<String>,
  plainText: String,
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
) {
  val preview = ChatUserMessageDisclosurePolicy.collapsedPreview(plainText)
  val action = key(plainText) { rememberChatReaderAction() }
  val requester = remember(action) { BringIntoViewRequester() }
  var pendingPlacement by remember(action) { mutableStateOf<CompletableDeferred<IntSize>?>(null) }
  if (preview != null && !expanded) {
    val anchor = rememberChatReaderAnchor(plainText)
    val hit = rememberChatTextHitTarget()
    Text(
      text = preview,
      modifier = (anchor?.modifier ?: Modifier).then(hit.modifier),
      onTextLayout = {
        hit.onTextLayout(it)
        anchor?.onTextLayout?.invoke(it)
      },
      style = ClawTheme.type.body.copy(fontWeight = FontWeight.Normal),
      color = ClawTheme.colors.text,
    )
  } else {
    Column(
      modifier =
        Modifier.bringIntoViewRequester(requester).onGloballyPositioned { coordinates ->
          pendingPlacement?.let { pending ->
            pendingPlacement = null
            pending.complete(coordinates.size)
          }
        },
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      textParts.forEach { text ->
        ChatMarkdown(
          text = text,
          textColor = ClawTheme.colors.text,
          isStreaming = false,
          bodyStyle = ClawTheme.type.body.copy(fontWeight = FontWeight.Normal),
        )
      }
    }
  }

  if (preview != null) {
    val toggleLabel = if (expanded) nativeString("Close") else nativeString("View all")
    ChatMessageDisclosureButton(toggleLabel) {
      // Repeated actions from one render keep the same open/close intent.
      onExpandedChange(!expanded)
      if (expanded) {
        pendingPlacement = null
        action.pause()
      } else {
        // Only this tap requests a reveal. Restored expansion and ordinary re-layout
        // keep their reading position; placement, not a guessed frame delay, admits it.
        val placement = CompletableDeferred<IntSize>()
        pendingPlacement = placement
        action.launch {
          val size = placement.await()
          requester.bringIntoView(Rect(0f, 0f, size.width.toFloat(), action.viewportHeight(size.height).toFloat()))
        }
      }
    }
  }
}

@Composable
private fun ChatText(
  text: String,
  textColor: Color,
  isStreaming: Boolean,
  centered: Boolean = false,
) {
  ChatMarkdown(
    text = text,
    textColor = textColor,
    isStreaming = isStreaming,
    bodyStyle = ClawTheme.type.body.copy(fontWeight = FontWeight.Normal),
    contentAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
  )
}

/** Detail sheet payload for a reasoning row: shared by the transcript and by folded step groups. */
private fun thinkingToolDetail(item: ChatTimelineItem.Thinking): ToolActivityDetail =
  ToolActivityDetail(
    key = item.key ?: LIVE_THINKING_DETAIL_KEY,
    title = "思考过程",
    kind = CompletedToolKind.Other,
    detailText = item.text,
    detailLabel = "思考内容",
    running = item.streaming,
    iconOverride = Icons.Outlined.Lightbulb,
  )

/**
 * A folded run of finished chain rows. The summary row always stays put while
 * the steps unfold downward out of it and fold back upward, so a long turn
 * stops eating the screen without hiding what the agent actually did.
 */
@Composable
private fun CollapsedStepsBlock(
  steps: List<ChatTimelineItem>,
  expanded: Boolean,
  onToggleExpanded: () -> Unit,
  onToolDetail: (ToolActivityDetail) -> Unit,
) {
  val colors = ClawTheme.colors
  val sizeSpec = tween<IntSize>(CHAIN_REVEAL_MS, easing = FastOutSlowInEasing)
  val fadeSpec = tween<Float>(CHAIN_REVEAL_MS, easing = FastOutSlowInEasing)
  Column(modifier = Modifier.fillMaxWidth()) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
      Surface(
        onClick = onToggleExpanded,
        modifier =
          Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { role = Role.Button },
        shape = RoundedCornerShape(4.dp),
        color = Color.Transparent,
        contentColor = colors.textMuted,
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          LucideStepsIcon(
            modifier = Modifier.size(20.dp),
            tint = colors.textMuted,
          )
          Text(
            text = "多个步骤",
            style = ClawTheme.type.caption,
            color = colors.textMuted,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false),
          )
          Text(
            text = steps.size.toString(),
            style = ClawTheme.type.captionSmall,
            color = colors.textSubtle,
          )
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.textSubtle,
          )
        }
      }
    }
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(animationSpec = sizeSpec, expandFrom = Alignment.Top) + fadeIn(animationSpec = fadeSpec),
      exit = shrinkVertically(animationSpec = sizeSpec, shrinkTowards = Alignment.Top) + fadeOut(animationSpec = fadeSpec),
    ) {
      val guideColor = colors.border
      Column(
        modifier =
          Modifier
            .padding(start = 7.dp)
            .drawBehind {
              drawLine(
                color = guideColor,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }.padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        steps.forEach { step ->
          when (step) {
            is ChatTimelineItem.Thinking ->
              ChatThinkingBlock(
                text = step.text,
                streaming = step.streaming,
                onClick = { onToolDetail(thinkingToolDetail(step)) },
              )

            is ChatTimelineItem.CompletedTools ->
              CompletedToolActivity(
                tools = step.tools,
                stableKey = step.key,
                runningIds = step.runningIds,
                onToolDetail = onToolDetail,
              )

            is ChatTimelineItem.PendingTools -> ToolBubble(toolCalls = step.toolCalls, onToolClick = onToolDetail)

            is ChatTimelineItem.SubagentActivity ->
              SubagentActivityRows(activities = step.activities, moreWorkingCount = step.moreWorkingCount)

            else -> Unit
          }
        }
      }
    }
  }
}

@Composable
private fun ToolBubble(
  toolCalls: List<ChatPendingToolCall>,
  onToolClick: (ToolActivityDetail) -> Unit,
) {
  // Deliberately not wrapped in a panel. A tool that is still running has to
  // read as one more row in the chain: the reasoning sits right above it and
  // its own finished row takes the same slot a moment later. A card around it
  // made the live row look like a detached strip pasted below the chain.
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    toolCalls.forEach { tool ->
      val detail = pendingToolDetail(tool)
      ToolActivityRow(detail = detail, onClick = { onToolClick(detail) })
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun CompletedToolActivity(
  tools: List<ChatToolActivity>,
  stableKey: String,
  runningIds: Set<String> = emptySet(),
  onToolDetail: (ToolActivityDetail) -> Unit,
) {
  // One or two calls stay as individual rows. Folding them into an aggregate
  // ("read 2 files") is what made the chain read out of sync with the reasoning
  // right above it, which names the files one by one. Bigger runs still fold:
  // that is the case the "多个步骤" fold exists for anyway.
  if (tools.isEmpty()) return
  if (tools.size <= CHAT_TOOL_INLINE_LIMIT) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
      tools.forEachIndexed { index, tool ->
        val toolCallId = tool.toolCallId ?: "$index:${tool.name}:${tool.detail.orEmpty().hashCode()}"
        val detail =
          completedToolDetail(
            tool = tool.copy(toolCallId = toolCallId),
            running = toolCallId in runningIds,
          )
        ToolActivityRow(detail = detail, onClick = { onToolDetail(detail) })
      }
    }
    return
  }
  if (tools.all { completedToolKind(it.name) == CompletedToolKind.Progress }) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      tools.forEach { ProgressToolReceipt(it) }
    }
    return
  }
  var expanded by rememberSaveable(stableKey) { mutableStateOf(false) }
  var showAll by rememberSaveable(stableKey) { mutableStateOf(false) }
  val summary = completedToolGroupSummary(tools)
  val state = if (expanded) nativeString("Expanded") else nativeString("Collapsed")
  // Remeasure disclosures immediately: nested size springs leave blank space
  // while the reverse-layout transcript readjusts its bottom anchor.
  Column(
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
  // Same tight-row treatment as ToolActivityRow: no Material 48dp stamp.
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(
      onClick = {
        expanded = !expanded
        if (!expanded) showAll = false
      },
      modifier =
        Modifier
          .fillMaxWidth()
          .semantics(mergeDescendants = true) {
            role = Role.Button
            stateDescription = state
          },
      shape = RoundedCornerShape(4.dp),
      color = Color.Transparent,
      contentColor = ClawTheme.colors.textMuted,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LucideStepsIcon(
          modifier = Modifier.size(20.dp),
          tint = ClawTheme.colors.textMuted,
        )
        Text(
          text = summary,
          modifier = Modifier.weight(1f, fill = false),
          style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Normal),
          color = ClawTheme.colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Icon(
          imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(16.dp),
          tint = ClawTheme.colors.textMuted,
        )
      }
    }
    if (expanded) {
      val guideColor = ClawTheme.colors.border
      Column(
        modifier =
          Modifier
            .padding(start = 7.dp)
            .drawBehind {
              drawLine(
                color = guideColor,
                start = Offset(0f, 0f),
                end = Offset(0f, size.height),
                strokeWidth = 1.dp.toPx(),
              )
            }.padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        (if (showAll) tools else tools.take(COMPLETED_TOOL_DETAIL_LIMIT)).forEachIndexed { index, tool ->
          val detail =
            completedToolDetail(
              tool.copy(toolCallId = tool.toolCallId ?: "$index:${tool.name}:${tool.detail.orEmpty().hashCode()}")
            )
          ToolActivityRow(detail = detail, onClick = { onToolDetail(detail) })
        }
        if (!showAll && tools.size > COMPLETED_TOOL_DETAIL_LIMIT) {
          Surface(
            onClick = { showAll = true },
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Button },
            shape = RoundedCornerShape(4.dp),
            color = Color.Transparent,
            contentColor = ClawTheme.colors.textMuted,
          ) {
            Row(
              modifier = Modifier.heightIn(min = ClawTheme.spacing.touchTarget).padding(vertical = 12.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text = nativeString("Show all \${count} tools", tools.size),
                modifier = Modifier.weight(1f, fill = false),
                style = ClawTheme.type.caption,
              )
              Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
              )
            }
          }
        }
      }
    }
  }
  }
}

@Composable
private fun CompletedToolActivityItem(
  tool: ChatToolActivity,
  saveableKey: String,
  parentStableKey: String,
) {
  if (completedToolKind(tool.name) == CompletedToolKind.Progress) {
    ProgressToolReceipt(tool)
    return
  }
  var expanded by rememberSaveable(parentStableKey, saveableKey) { mutableStateOf(false) }
  val kind = completedToolKind(tool.name)
  val resultPresentation = completedToolResultPresentation(tool)
  val preview =
    tool.detail
      ?.lineSequence()
      ?.firstOrNull { it.isNotBlank() }
      ?.trim()
  val summary =
    if (kind == CompletedToolKind.Command) {
      completedCommandText(tool).orEmpty()
    } else {
      val name = completedToolDisplayName(tool.name)
      preview?.substringAfter(": ", preview)?.let { "$name · $it" } ?: name
    }
  val expandable = resultPresentation.expandable
  val state = if (expanded) nativeString("Expanded") else nativeString("Collapsed")
  Column(
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Surface(
      modifier =
        Modifier
          .fillMaxWidth()
          .then(if (expandable) Modifier.clickable { expanded = !expanded } else Modifier)
          .semantics(mergeDescendants = true) {
            if (expandable) {
              role = Role.Button
              stateDescription = state
            }
          },
      color = Color.Transparent,
      contentColor = ClawTheme.colors.textMuted,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LucideToolIcon(kind = kind, tint = ClawTheme.colors.textMuted, size = 20.dp)
        Row(
          modifier = Modifier.weight(1f),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          if (kind == CompletedToolKind.Command) {
            Text(
              text = nativeString("\$"),
              modifier = Modifier.alignByBaseline().padding(end = 2.dp),
              style = ClawTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
              color = ClawTheme.colors.textMuted,
            )
          }
          Text(
            text = summary,
            modifier = Modifier.weight(1f).alignByBaseline(),
            style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Normal),
            color = ClawTheme.colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (expandable) {
          Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = ClawTheme.colors.textSubtle,
          )
        }
      }
    }
    if (expanded && kind == CompletedToolKind.Command) {
      CompletedCommandOutput(tool)
    } else if (expanded) {
      tool.detail?.let { detail ->
        Text(
          text = detail,
          modifier = Modifier.padding(start = 2.dp, end = 8.dp),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
          maxLines = 4,
          overflow = TextOverflow.Ellipsis,
        )
      }
      resultPresentation.output?.let { result ->
        resultPresentation.outputLabel?.let { label ->
          Text(
            text = label,
            modifier = Modifier.padding(start = 2.dp, end = 8.dp),
            style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = ClawTheme.colors.textMuted,
          )
        }
        Text(
          text = result,
          modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 5.dp),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textSubtle,
          maxLines = 12,
          overflow = TextOverflow.Ellipsis,
        )
      }
      resultPresentation.outcome?.let { outcome ->
        Text(
          text = outcome,
          modifier = Modifier.padding(start = 2.dp, end = 8.dp, bottom = 5.dp),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
        )
      }
    }
  }
}

@Composable
private fun CompletedCommandOutput(tool: ChatToolActivity) {
  val resultPresentation = completedToolResultPresentation(tool)
  val shellShape = RoundedCornerShape(14.dp)
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .padding(top = 5.dp, bottom = 8.dp)
        .border(1.dp, ClawTheme.colors.border, shellShape)
        .clip(shellShape)
        .heightIn(max = 360.dp)
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 14.dp, vertical = 12.dp),
  ) {
    SelectionContainer {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = nativeString("\$"),
            style = ClawTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
            color = ClawTheme.colors.textMuted,
          )
          Text(
            text = completedCommandText(tool, singleLine = false).orEmpty(),
            modifier = Modifier.weight(1f),
            style = ClawTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
            color = ClawTheme.colors.text,
          )
        }
        resultPresentation.output?.let { result ->
          Text(
            text = result,
            style = ClawTheme.type.caption.copy(fontFamily = FontFamily.Monospace),
            color = ClawTheme.colors.text,
          )
        }
        resultPresentation.outcome?.let { outcome ->
          Text(
            text = outcome,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
    }
  }
}

@Composable
private fun ProgressToolReceipt(tool: ChatToolActivity) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Default.Checklist,
      contentDescription = null,
      modifier = Modifier.size(16.dp),
      tint = ClawTheme.colors.textMuted,
    )
    Text(
      text = progressReceiptLabel(tool),
      modifier = Modifier.weight(1f),
      style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Normal),
      color = ClawTheme.colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

private const val COMPLETED_TOOL_DETAIL_LIMIT = 20

/** Completed calls rendered as their own rows instead of one aggregate line. */
private const val CHAT_TOOL_INLINE_LIMIT = 2

internal fun readableToolName(name: String): String =
  name
    .trim()
    .replace('_', ' ')
    .replace('.', ' ')
    .split(Regex("\\s+"))
    .filter(String::isNotEmpty)
    .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.US) } }
    .ifEmpty { nativeString("Tool") }

@Composable
private fun SubagentActivityRows(
  activities: List<ChatSubagentActivity>,
  moreWorkingCount: Int,
) {
  val animationsEnabled = rememberSystemAnimationsEnabled()
  ClawPanel {
    Column(
      modifier = if (animationsEnabled) Modifier.animateContentSize() else Modifier,
      verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
      activities.forEach { activity -> SubagentActivityRow(activity, animationsEnabled) }
      if (moreWorkingCount > 0) {
        Text(
          text = nativeString("+\${moreWorkingCount} more working", moreWorkingCount),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textSubtle,
        )
      }
    }
  }
}

@Composable
private fun SubagentActivityRow(
  activity: ChatSubagentActivity,
  animationsEnabled: Boolean,
) {
  val completed = activity.status == "completed"
  val summary = if (activity.isWorking) activity.snippet else activity.terminalSummary ?: activity.error ?: activity.snippet
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (activity.isWorking) {
      WorkingClawIcon(runKey = activity.id, color = ClawTheme.colors.primary)
    } else {
      Icon(
        imageVector = if (completed) Icons.Default.Check else Icons.Default.Close,
        contentDescription = null,
        modifier = Modifier.size(15.dp),
        tint = if (completed) ClawTheme.colors.success else ClawTheme.colors.danger,
      )
    }
    Text(
      text = subagentActivityStatusLabel(activity.status),
      style = ClawTheme.type.label,
      color = ClawTheme.colors.text,
      maxLines = 1,
    )
    if (summary.isNullOrBlank()) {
      Box(modifier = Modifier.weight(1f))
    } else if (animationsEnabled) {
      AnimatedContent(
        targetState = summary,
        modifier = Modifier.weight(1f),
      ) { text ->
        SubagentActivitySnippet(text)
      }
    } else {
      SubagentActivitySnippet(summary, Modifier.weight(1f))
    }
    activity.diffStat?.takeIf { it.added > 0 || it.removed > 0 }?.let { DiffStatChips(it) }
  }
}

@Composable
private fun SubagentActivitySnippet(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    modifier = modifier,
    style = ClawTheme.type.caption,
    color = ClawTheme.colors.textMuted,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
  )
}

@Composable
private fun DiffStatChips(diff: ChatDiffStat) {
  Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
    if (diff.added > 0) {
      DiffStatChip(text = nativeString("+\${diff.added}", diff.added), color = ClawTheme.colors.success, background = ClawTheme.colors.successSoft)
    }
    if (diff.removed > 0) {
      DiffStatChip(text = nativeString("−\${diff.removed}", diff.removed), color = ClawTheme.colors.danger, background = ClawTheme.colors.dangerSoft)
    }
  }
}

@Composable
private fun DiffStatChip(
  text: String,
  color: Color,
  background: Color,
) {
  Surface(shape = RoundedCornerShape(ClawTheme.radii.control), color = background) {
    Text(
      text = text,
      modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
      style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
      color = color,
      maxLines = 1,
    )
  }
}

@Composable
private fun subagentActivityStatusLabel(status: String): String =
  when (status) {
    "queued", "running" -> nativeString("Subagent working")
    "completed" -> nativeString("Subagent finished")
    "failed", "timed_out" -> nativeString("Subagent failed")
    "cancelled" -> nativeString("Subagent cancelled")
    else -> nativeString("Subagent finished")
  }

@Composable
private fun ChatNotice(
  title: String,
  body: String,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(ClawTheme.radii.panel),
    color = ClawTheme.colors.surface,
    contentColor = ClawTheme.colors.text,
    border = BorderStroke(1.dp, ClawTheme.colors.border),
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(modifier = Modifier.size(6.dp).background(ClawTheme.colors.warning, CircleShape))
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = ClawTheme.type.section, color = ClawTheme.colors.text)
        Text(text = body, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
    }
  }
}

internal fun progressCardIsComplete(
  card: ChatProgressCard,
  hasActiveRun: Boolean,
): Boolean =
  if (card.steps.isEmpty()) {
    !hasActiveRun
  } else {
    card.steps.all { it.status == ChatPlanStepStatus.Completed }
  }

@Composable
private fun ProgressCardPill(
  card: ChatProgressCard,
  hasActiveRun: Boolean,
  modifier: Modifier = Modifier,
  attachedToComposer: Boolean = false,
) {
  val steps = card.steps
  val currentStep =
    steps.firstOrNull { it.status == ChatPlanStepStatus.InProgress }
      ?: steps.firstOrNull { it.status == ChatPlanStepStatus.Pending }
      ?: steps.lastOrNull { it.status == ChatPlanStepStatus.Completed }
  val complete = progressCardIsComplete(card, hasActiveRun)
  val currentPosition = if (complete) steps.size else (steps.indexOf(currentStep) + 1).coerceAtLeast(1)
  var expanded by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(complete) {
    if (complete) expanded = false
  }
  val activityTime = relativeSessionTime(card.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
  val activityLabel =
    if (complete) {
      nativeString("Completed \$activityTime", activityTime)
    } else {
      nativeString("Updated \$activityTime", activityTime)
    }
  val expandedActivityLabel =
    if (steps.isEmpty()) {
      activityLabel
    } else {
      nativeString("\$activityLabel \u00b7 \$currentPosition/\${steps.size}", activityLabel, currentPosition, steps.size)
    }

  val attachedShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
  val baseModifier = modifier.fillMaxWidth().heightIn(max = 240.dp).testTag("chat-progress-card")
  val progressModifier =
    if (attachedToComposer) {
      baseModifier
        .clip(attachedShape)
        .background(ClawTheme.colors.surface)
        .border(1.dp, ClawTheme.colors.borderStrong, attachedShape)
        .padding(bottom = 18.dp)
    } else {
      baseModifier
    }
  Column(modifier = progressModifier) {
    Surface(
      onClick = { expanded = !expanded },
      modifier = Modifier.fillMaxWidth().heightIn(min = 42.dp),
      shape = RoundedCornerShape(0.dp),
      color = Color.Transparent,
      contentColor = ClawTheme.colors.text,
    ) {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 15.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (expanded) {
          Text(
            text = nativeString("Task progress"),
            style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
            color = ClawTheme.colors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = expandedActivityLabel,
            style = ClawTheme.type.caption.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = ClawTheme.colors.textMuted,
            maxLines = 1,
          )
        } else {
          when {
            complete -> {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = nativeString("Completed"),
                modifier = Modifier.size(14.dp),
                tint = ClawTheme.colors.success,
              )
            }

            currentStep != null -> {
              PlanStepMarker(status = currentStep.status)
            }

            else -> {
              Box(modifier = Modifier.width(14.dp))
            }
          }
          Text(
            text = currentStep?.step ?: nativeString("Progress note"),
            style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Medium),
            color = ClawTheme.colors.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          if (steps.isNotEmpty()) {
            Text(
              text = nativeString("\$currentPosition/\${steps.size}", currentPosition, steps.size),
              style = ClawTheme.type.caption,
              color = ClawTheme.colors.textMuted,
              maxLines = 1,
            )
          }
        }
        Icon(
          imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = if (expanded) nativeString("Collapse progress card") else nativeString("Expand progress card"),
          modifier = Modifier.size(18.dp),
          tint = ClawTheme.colors.textMuted,
        )
      }
    }

    if (expanded) {
      Column(
        modifier =
          Modifier
            .weight(1f, fill = false)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 12.dp, end = 16.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        card.markdown?.let { markdown ->
          ChatMarkdown(
            text = markdown,
            textColor = ClawTheme.colors.text,
            isStreaming = false,
            progressBars = true,
          )
        }
        steps.forEach { step ->
          val textColor =
            when (step.status) {
              ChatPlanStepStatus.Completed -> ClawTheme.colors.textMuted
              ChatPlanStepStatus.InProgress -> ClawTheme.colors.text
              ChatPlanStepStatus.Pending -> ClawTheme.colors.textSubtle
            }
          val textStyle =
            when (step.status) {
              ChatPlanStepStatus.InProgress -> ClawTheme.type.label
              else -> ClawTheme.type.caption
            }
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            PlanStepMarker(status = step.status)
            Text(text = step.step, style = textStyle, color = textColor)
          }
        }
      }
    }
  }
}

@Composable
private fun PlanStepMarker(status: ChatPlanStepStatus) {
  Box(modifier = Modifier.width(14.dp), contentAlignment = Alignment.Center) {
    when (status) {
      ChatPlanStepStatus.Completed -> {
        Text(
          text = "✓",
          style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Bold),
          color = ClawTheme.colors.success,
        )
      }

      ChatPlanStepStatus.InProgress -> {
        Box(modifier = Modifier.size(8.dp).background(ClawTheme.colors.primary, CircleShape))
      }

      ChatPlanStepStatus.Pending -> {
        Box(modifier = Modifier.size(8.dp).background(ClawTheme.colors.textSubtle, CircleShape))
      }
    }
  }
}

@Composable
internal fun chatDraftStyle(): TextStyle = ClawTheme.type.body.copy(fontSize = 16.sp, lineHeight = 22.sp)

@Composable
private fun chatProjectStyle(): TextStyle = ClawTheme.type.caption.copy(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)

@Composable
private fun chatTitleStyle(): TextStyle = ClawTheme.type.title.copy(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)

@Composable
private fun minimumChatLineHeight(style: TextStyle): Int {
  // Android's nonlinear scaling resolves line height relative to the rendered font size.
  val measured = rememberTextMeasurer().measure("H", style = style, maxLines = 1, softWrap = false).size.height
  return maxOf(measured, with(LocalDensity.current) { ceil(style.lineHeight.toPx()).toInt() })
}

/**
 * The room the transcript reserves under its last row so the newest message
 * stays clear of the composer. It tracks the bar the style actually draws: the
 * classic bar is two rows tall, the simple bar one, and reserving the classic
 * height behind a one-row bar leaves a bare canvas band under the glass.
 */
@Composable
internal fun minimumChatInputHeight(composerStyle: ComposerBarStyle = ComposerBarStyle.Classic): Dp =
  when (composerStyle) {
    ComposerBarStyle.Simple -> SimpleComposerBarHeight
    ComposerBarStyle.Classic -> {
      val lineHeight = minimumChatLineHeight(chatDraftStyle())
      with(LocalDensity.current) {
        // Match each separately rounded editor/action padding and the text's full pixel line.
        (
          maxOf(ClawTheme.spacing.touchTarget.roundToPx(), lineHeight + 8.dp.roundToPx() + 4.dp.roundToPx()) +
            ClawTheme.spacing.touchTarget.roundToPx() + 4.dp.roundToPx() * 2
        ).toDp()
      }
    }
  }

/**
 * Distance from the pane's bottom edge that keeps the transcript's newest row
 * and the floating chat pills clear of the composer. The simple bar rides the
 * gesture area, so its clearance adds the nav inset and the pane's 4dp float
 * gap on top of the one-line bar plus a breathing margin; the classic bar is
 * already covered by [minimumChatInputHeight].
 */
@Composable
internal fun chatComposerClearance(
  composerStyle: ComposerBarStyle,
  composerBottomInset: Dp,
): Dp =
  when (composerStyle) {
    ComposerBarStyle.Simple -> composerBottomInset + SimpleComposerBarHeight + 16.dp
    ComposerBarStyle.Classic -> 10.dp + minimumChatInputHeight(composerStyle)
  }

@Composable
private fun ChatComposer(
  compactHeight: Boolean,
  detailsExpanded: Boolean,
  onDetailsExpandedChange: (Boolean) -> Unit,
  conversationHeader: @Composable (() -> Unit) -> Unit,
  conversationStatus: @Composable () -> Unit,
  progressCard: ChatProgressCard?,
  value: String,
  onValueChange: (String) -> Unit,
  attachments: List<PendingAttachment>,
  thinkingLevel: String,
  thinkingOptions: List<ChatThinkingLevelOption>,
  thinkingSupported: Boolean,
  thinkingLevelEnabled: Boolean,
  fastMode: Boolean,
  fastModeEnabled: Boolean,
  contextUsage: ChatContextUsage,
  selectedModelLabel: String,
  modelPickerEnabled: Boolean,
  modelSections: ChatModelPickerSections,
  modelFavorites: Set<String>,
  modelSelectionLocked: Boolean,
  onSelectModel: (String?) -> Unit,
  onToggleModelFavorite: (String) -> Unit,
  onOpenModelProviders: (String) -> Unit,
  healthOk: Boolean,
  gatewayOffline: Boolean,
  gatewayConnecting: Boolean,
  showOfflineNotice: Boolean,
  offlineStatus: String,
  pendingRunCount: Int,
  shareStaging: Boolean,
  sendInFlight: Boolean,
  shareImportNotice: NativeText?,
  modelUnavailableMessage: NativeText?,
  onDismissShareImportNotice: () -> Unit,
  commands: List<ChatCommandEntry>,
  onOpenEffortPicker: () -> Unit,
  onOpenModelPicker: () -> Unit,
  onPickImages: () -> Unit,
  onPickAudioOrDocument: () -> Unit,
  onPickVideo: () -> Unit,
  onCapturePhoto: () -> Unit,
  onRemoveAttachment: (String) -> Unit,
  onAnnotateAttachment: (PendingAttachment, Rect) -> Unit,
  voiceNoteState: VoiceNoteRecorderState,
  voiceNoteElapsedMs: Long,
  voiceNoteLevel: Float,
  recordVoiceNoteEnabled: Boolean,
  onStartVoiceNote: () -> Unit,
  onCancelVoiceNote: () -> Unit,
  onFinishVoiceNote: () -> Unit,
  dictationState: ChatDictationState,
  dictationEnabled: Boolean,
  onToggleDictation: () -> Unit,
  talkActive: Boolean,
  liveTalkVisible: Boolean,
  voiceInputVisible: Boolean,
  composerStyle: ComposerBarStyle,
  simpleMenu: SimpleComposerMenuState,
  selectedModelRef: String?,
  onSelectThinkingLevel: (String) -> Unit,
  onOpenFullScreenEditor: () -> Unit,
  onToggleTalk: () -> Unit,
  onFixConnection: () -> Unit,
  onOpenProvidersModels: () -> Unit,
  onCopyDiagnostics: () -> Unit,
  onAbort: () -> Unit,
  onSend: () -> Unit,
  /** Backdrop the attachment menu blurs; the chat screen marks itself as the source. */
  hazeState: HazeState? = null,
  /** Swallows every touch on the composer while the share picker owns the screen. */
  interactionLocked: Boolean = false,
  quote: ChatQuoteDraft? = null,
  onClearQuote: () -> Unit = {},
  focusRequestId: Int = 0,
) {
  val slashCommands =
    remember(value, commands) {
      matchingSlashCommands(input = value, commands = commands)
    }
  val dictationActive =
    dictationState is ChatDictationState.Starting || dictationState is ChatDictationState.Listening
  val hasContent = value.trim().isNotEmpty() || attachments.isNotEmpty() || quote != null
  // Offline sends queue durably too (text, images, and voice notes), so the gate is identical
  // to the connected one; admission errors keep the draft when the durable queue refuses it.
  val sendEnabled =
    chatComposerSendEnabled(
      voiceNoteState = voiceNoteState,
      talkActive = talkActive,
      hasContent = hasContent,
      shareStaging = shareStaging,
      sendInFlight = sendInFlight,
      dictationActive = dictationActive,
      modelUnavailable = modelUnavailableMessage != null,
    )

  val simpleMenuTrigger = rememberClawMorphTrigger(simpleMenu.morph)
  val simpleMenuModelOptions =
    remember(modelSections) {
      (modelSections.pinned + modelSections.recent + modelSections.remaining)
        .distinctBy { it.providerQualifiedRef() }
    }
  val simpleMenuThinkingLabel =
    thinkingOptions
      .firstOrNull { it.id.trim().equals(thinkingLevel.trim(), ignoreCase = true) }
      ?.let(::chatThinkingOptionLabel)
      ?: thinkingLevel.ifBlank { nativeString("Off") }
  val latestModelLabel = rememberUpdatedState(selectedModelLabel)
  val latestModelEnabled = rememberUpdatedState(modelPickerEnabled && !modelSelectionLocked)
  val latestThinkingLabel = rememberUpdatedState(simpleMenuThinkingLabel)
  val latestThinkingEnabled = rememberUpdatedState(thinkingSupported && thinkingLevelEnabled)
  val latestSelectedModelRef = rememberUpdatedState(selectedModelRef)
  val latestThinkingLevel = rememberUpdatedState(thinkingLevel)
  val openSimpleMenu: () -> Unit = {
    if (!simpleMenu.morph.expanded) {
      simpleMenu.prepareOpen()
      // The disc itself swells into the card (see simpleComposerButtonMorph)
      // while the overlay grows the crisp glass from the same bottom-left pin.
      simpleMenuTrigger.open(
        origin = TransformOrigin(0f, 1f),
        panelWidth = SimpleComposerMenuPanelWidth,
      ) {
        SimpleComposerMenuPrimaryContent(
          state = simpleMenu,
          modelLabel = latestModelLabel.value,
          modelEnabled = latestModelEnabled.value,
          modelBody = {
            SimpleComposerModelOptions(
              models = simpleMenuModelOptions,
              selectedRef = latestSelectedModelRef.value,
              onSelect = { ref ->
                onSelectModel(ref)
                simpleMenu.endDrill()
              },
            )
          },
          thinkingLabel = latestThinkingLabel.value,
          thinkingEnabled = latestThinkingEnabled.value,
          thinkingBody = {
            SimpleComposerThinkingOptions(
              options = thinkingOptions,
              selectedId = latestThinkingLevel.value,
              onSelect = { level ->
                onSelectThinkingLevel(level)
                simpleMenu.endDrill()
              },
            )
          },
          onCamera = {
            simpleMenu.closeAll()
            onCapturePhoto()
          },
          onPhotos = {
            simpleMenu.closeAll()
            onPickImages()
          },
          onFiles = {
            simpleMenu.closeAll()
            onPickAudioOrDocument()
          },
        )
      }
    }
  }

  val attachedProgress = progressCard != null && voiceNoteState !is VoiceNoteRecorderState.Recording && voiceNoteState !is VoiceNoteRecorderState.Preparing
  val auxiliaryContent: @Composable (Dp) -> Unit = { availableHeight ->
    conversationStatus()
    if (shareImportNotice != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = shareImportNotice.resolveNativeTextResource(),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.warning,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismissShareImportNotice, modifier = Modifier.size(32.dp)) {
          Icon(Icons.Default.Close, contentDescription = nativeString("Dismiss attachment warning"))
        }
      }
    }
    if (modelUnavailableMessage != null) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
      ) {
        Text(
          text = modelUnavailableMessage.resolveNativeTextResource(),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.warning,
          modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onOpenProvidersModels) {
          Text(nativeString("Providers"))
        }
      }
    }
    if (attachments.isNotEmpty()) {
      AttachmentStrip(
        attachments = attachments,
        preparing = sendInFlight,
        onRemoveAttachment = onRemoveAttachment,
        onAnnotateAttachment = onAnnotateAttachment,
      )
    }

    if (shouldShowSlashCommandMenu(value)) {
      SlashCommandPanel(
        commands = slashCommands,
        onSelect = { command ->
          onDetailsExpandedChange(false)
          onValueChange(slashCommandCompletion(command))
        },
        modifier = Modifier.heightIn(max = minOf(240.dp, availableHeight)),
      )
    }

    VoiceNoteRecorderError(voiceNoteState)
    ChatDictationError(dictationState)
    if (recordVoiceNoteEnabled && (dictationState as? ChatDictationState.Failure)?.reason == ChatDictationFailure.Unavailable) {
      TextButton(onClick = onStartVoiceNote) { Text(voiceNoteRecordLabel()) }
    }
    if (showOfflineNotice) {
      ChatOfflineNotice(
        status = offlineStatus,
        onFixConnection = onFixConnection,
        onCopyDiagnostics = onCopyDiagnostics,
      )
    }
    progressCard?.let { card ->
      ProgressCardPill(card, pendingRunCount > 0, Modifier.fillMaxWidth().heightIn(max = availableHeight), attachedProgress && !detailsExpanded)
    }
  }

  BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
    val inputHeightLimit = if (compactHeight) maxHeight else maxOf(minimumChatInputHeight(composerStyle), maxHeight - ClawTheme.spacing.touchTarget)
    Column(
      modifier = if (detailsExpanded) Modifier.clearAndSetSemantics {} else Modifier,
      verticalArrangement = Arrangement.spacedBy(if (attachedProgress && !compactHeight && !detailsExpanded) (-18).dp else 4.dp),
    ) {
      if (!compactHeight && !detailsExpanded) {
        BoxWithConstraints(Modifier.weight(1f, fill = false)) {
          val auxiliaryHeight = maxHeight
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            auxiliaryContent(auxiliaryHeight)
          }
        }
      }
      Row(
        modifier = Modifier.fillMaxWidth().heightIn(max = inputHeightLimit),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        if (voiceNoteState is VoiceNoteRecorderState.Recording) {
          VoiceNoteRecordingControls(
            elapsedMs = voiceNoteElapsedMs,
            level = voiceNoteLevel,
            onCancel = onCancelVoiceNote,
            onDone = onFinishVoiceNote,
            modifier = Modifier.weight(1f),
          )
        } else if (voiceNoteState is VoiceNoteRecorderState.Preparing) {
          VoiceNotePreparing(modifier = Modifier.weight(1f))
        } else {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            // The last quote survives dismissal so the capsule can animate out with its text.
            var lastQuote by remember { mutableStateOf(quote) }
            if (quote != null) lastQuote = quote
            AnimatedVisibility(
              visible = quote != null,
              // Drops in from above with a soft overshoot, and retracts the same way on close.
              enter =
                slideInVertically(
                  animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f),
                  initialOffsetY = { height -> -height / 3 },
                ) + fadeIn(animationSpec = tween(120)),
              exit =
                slideOutVertically(
                  animationSpec = spring(dampingRatio = 0.7f, stiffness = 420f),
                  targetOffsetY = { height -> -height / 3 },
                ) + fadeOut(animationSpec = tween(110)),
            ) {
              lastQuote?.let { currentQuote ->
                ChatQuoteCapsule(
                  quote = currentQuote,
                  onRemove = onClearQuote,
                  hazeState = hazeState,
                  modifier = Modifier.fillMaxWidth(),
                )
              }
            }
            if (composerStyle == ComposerBarStyle.Simple) {
              SimpleComposerBar(
                inputEnabled = !detailsExpanded,
                gatewayConnecting = gatewayConnecting,
                value = value,
                onValueChange = onValueChange,
                focusRequestId = focusRequestId,
                hasContent = hasContent,
                sendEnabled = sendEnabled,
                onSend = onSend,
                runActive = pendingRunCount > 0,
                sendInFlight = sendInFlight,
                onAbort = onAbort,
                talkActive = talkActive,
                onToggleTalk = onToggleTalk,
                onOpenMenu = {
                  if (simpleMenu.morph.expanded) {
                    simpleMenu.closeAll()
                  } else {
                    openSimpleMenu()
                  }
                },
                onExpandEditor = onOpenFullScreenEditor,
                plusTriggerModifier = simpleMenuTrigger.modifier,
                hazeState = hazeState,
                modifier = Modifier.fillMaxWidth(),
              )
            } else {
            ChatInputPill(
              inputEnabled = !detailsExpanded,
              gatewayConnecting = gatewayConnecting,
              onOpenDetails = if (compactHeight) ({ onDetailsExpandedChange(true) }) else null,
              value = value,
              onValueChange = onValueChange,
              onPickImages = onPickImages,
              onPickAudioOrDocument = onPickAudioOrDocument,
              onPickVideo = onPickVideo,
              onCapturePhoto = onCapturePhoto,
              onStartVoiceNote = onStartVoiceNote,
              recordVoiceNoteEnabled = recordVoiceNoteEnabled,
            dictationActive = dictationActive,
            dictationEnabled = dictationEnabled,
            onToggleDictation = onToggleDictation,
            talkActive = talkActive,
            liveTalkVisible = liveTalkVisible,
            voiceInputVisible = voiceInputVisible,
            onToggleTalk = onToggleTalk,
            runActive = pendingRunCount > 0,
            sendInFlight = sendInFlight,
            onAbort = onAbort,
            hasContent = hasContent,
            sendEnabled = sendEnabled,
            onSend = onSend,
            selectedModelLabel = selectedModelLabel,
            modelPickerEnabled = modelPickerEnabled,
            onOpenModelPicker = onOpenModelPicker,
            modelSections = modelSections,
            modelFavorites = modelFavorites.toSet(),
            modelSelectionLocked = modelSelectionLocked,
            onSelectModel = { ref ->
              onSelectModel(ref)
            },
            onToggleModelFavorite = onToggleModelFavorite,
            onOpenModelProviders = onOpenModelProviders,
            thinkingLevel = thinkingLevel,
            thinkingOptions = thinkingOptions,
            thinkingSupported = thinkingSupported,
            thinkingLevelEnabled = thinkingLevelEnabled,
            fastMode = fastMode,
            fastModeEnabled = fastModeEnabled,
            onOpenEffortPicker = onOpenEffortPicker,
            contextUsage = contextUsage,
            hazeState = hazeState,
            modifier = Modifier.fillMaxWidth(),
            focusRequestId = focusRequestId,
            )
            }
          }
        }
      }
    }
    if (detailsExpanded) {
      BackHandler { onDetailsExpandedChange(false) }
      val detailsTitle = nativeString("Details")
      // Stay inside the current pane and IME constraints; a dialog would escape them.
      Surface(
        modifier = Modifier.fillMaxSize().semantics { paneTitle = detailsTitle },
        color = ClawTheme.colors.surface,
        contentColor = ClawTheme.colors.text,
      ) {
        Column {
          Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(detailsTitle, style = ClawTheme.type.label, modifier = Modifier.weight(1f))
            IconButton(onClick = { onDetailsExpandedChange(false) }, modifier = Modifier.size(ClawTheme.spacing.touchTarget)) {
              Icon(Icons.Default.Close, contentDescription = nativeString("Close"))
            }
          }
          BoxWithConstraints(Modifier.weight(1f)) {
            val auxiliaryHeight = maxHeight
            Column(
              modifier = Modifier.verticalScroll(rememberScrollState()),
              verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              conversationHeader { onDetailsExpandedChange(false) }
              auxiliaryContent(auxiliaryHeight)
            }
          }
        }
      }
    }
    if (interactionLocked) {
      // A sibling above the composer: a guard on the composer's own modifier
      // would let the focused text field and its buttons see the down event
      // first on the main pass.
      Box(Modifier.matchParentSize().chatShareTouchShield(true))
    }
  }
}

internal data class ChatEffortPosition(
  val optionIndex: Int,
  val fraction: Float?,
) {
  val anchored: Boolean
    get() = fraction != null
}

internal fun chatEffortStopFractions(optionCount: Int): List<Float> =
  when {
    optionCount <= 0 -> emptyList()
    optionCount == 1 -> listOf(1f)
    else -> List(optionCount) { index -> index.toFloat() / (optionCount - 1) }
  }

internal fun resolveChatEffortPosition(
  selectedId: String,
  options: List<ChatThinkingLevelOption>,
): ChatEffortPosition {
  val normalizedSelected = selectedId.trim().lowercase(Locale.US)
  val selectedIndex = options.indexOfFirst { it.id.trim().lowercase(Locale.US) == normalizedSelected }
  val stopFractions = chatEffortStopFractions(options.size)
  val fraction =
    when {
      selectedIndex < 0 -> null
      normalizedSelected == "off" -> 0f
      else -> stopFractions[selectedIndex]
    }
  return ChatEffortPosition(optionIndex = selectedIndex, fraction = fraction)
}

internal fun chatEffortNeedleAngle(position: ChatEffortPosition): Float? = position.fraction?.let { 150f + it * 240f }

internal fun chatEffortVisualFraction(
  fraction: Float,
  layoutDirection: LayoutDirection,
): Float = if (layoutDirection == LayoutDirection.Rtl) 1f - fraction else fraction

@Composable
private fun ChatThinkingLevelPicker(
  options: List<ChatThinkingLevelOption>,
  selectedId: String,
  thinkingSupported: Boolean,
  thinkingLevelEnabled: Boolean,
  fastMode: Boolean,
  fastModeEnabled: Boolean,
  onOpen: () -> Unit,
) {
  val enabled = (thinkingSupported && thinkingLevelEnabled) || fastModeEnabled
  val languageTag = currentAppLanguage().languageTag
  val position = resolveChatEffortPosition(selectedId, options)
  val description = nativeString("Thinking")
  val dialColor = if (enabled) ClawTheme.colors.textMuted else ClawTheme.colors.textSubtle
  val needleColor = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle
  val haptics = rememberClawHaptics()
  Surface(
    onClick = {
      haptics.tap()
      onOpen()
    },
    enabled = enabled,
    modifier =
      Modifier.size(ClawTheme.spacing.touchTarget).semantics {
        contentDescription = description
        stateDescription = chatThinkingChipStateDescription(fastMode, selectedId, options, languageTag)
      },
    shape = CircleShape,
    color = Color.Transparent,
  ) {
    Box(contentAlignment = Alignment.Center) {
      Box(modifier = Modifier.size(20.dp).testTag("chat-thinking-gauge")) {
        Canvas(modifier = Modifier.matchParentSize()) {
          val dialStrokeWidth = 1.5.dp.toPx()
          val needleStrokeWidth = 2.dp.toPx()
          // The dial omits its bottom arc; center the visible ink with the other controls.
          translate(top = size.height / 8f) {
            drawArc(color = dialColor, startAngle = 150f, sweepAngle = 240f, useCenter = false, style = Stroke(width = dialStrokeWidth, cap = StrokeCap.Round))
            // An unadvertised effective level is not the minimum/Off position.
            chatEffortNeedleAngle(position)?.let { angle ->
              rotate(angle) {
                drawLine(
                  color = needleColor,
                  start = center,
                  end = Offset(size.width * 0.82f, center.y),
                  strokeWidth = needleStrokeWidth,
                  cap = StrokeCap.Round,
                )
              }
              drawCircle(color = needleColor, radius = 1.25.dp.toPx(), center = center)
            }
          }
        }
        if (fastMode) {
          Box(
            modifier =
              Modifier
                .align(Alignment.BottomEnd)
                .size(8.dp)
                .background(ClawTheme.colors.surface, CircleShape)
                .testTag("chat-fast-mode-badge"),
            contentAlignment = Alignment.Center,
          ) {
            Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(7.dp), tint = ClawTheme.colors.primary)
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatEffortSliderControl(
  options: List<ChatThinkingLevelOption>,
  selectedId: String,
  enabled: Boolean,
  onSelect: (String) -> Unit,
) {
  val languageTag = currentAppLanguage().languageTag
  val selectedPosition = resolveChatEffortPosition(selectedId, options)
  var previewing by remember(selectedId, options) { mutableStateOf(false) }
  val haptics = rememberClawHaptics()
  val sliderState =
    remember(selectedId, options) {
      SliderState(
        value = selectedPosition.optionIndex.coerceAtLeast(0).toFloat(),
        steps = (options.size - 2).coerceAtLeast(0),
        valueRange = 0f..options.lastIndex.coerceAtLeast(0).toFloat(),
      )
    }
  var lastTickIndex by remember(selectedId, options) { mutableIntStateOf(sliderState.value.roundToInt()) }
  sliderState.onValueChange = { value ->
    sliderState.value = value
    previewing = true
    // One tick per crossed stop: dragging across three levels must not buzz on
    // every animation frame, only when the highlighted option actually changes.
    val tickIndex = value.roundToInt()
    if (tickIndex != lastTickIndex) {
      lastTickIndex = tickIndex
      haptics.tick()
    }
  }
  sliderState.onValueChangeFinished = {
    options.getOrNull(sliderState.value.roundToInt())?.let { option ->
      if (!option.id.equals(selectedId, ignoreCase = true)) onSelect(option.id)
    }
    sliderState.value = selectedPosition.optionIndex.coerceAtLeast(0).toFloat()
    previewing = false
  }
  val sliderIndex = sliderState.value.roundToInt()
  val selectedLabel =
    sliderIndex
      .takeIf { previewing }
      ?.let(options::getOrNull)
      ?.let { option -> chatThinkingOptionLabel(option, languageTag) }
      ?: chatThinkingOptionLabel(
        options.getOrNull(selectedPosition.optionIndex) ?: ChatThinkingLevelOption(selectedId, selectedId),
        languageTag,
      )

  Column {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(nativeString("Effort"), style = ClawTheme.type.label.copy(fontWeight = FontWeight.SemiBold))
      Text(selectedLabel, style = ClawTheme.type.label, color = ClawTheme.colors.primary)
    }
    // Material treats two endpoints as continuous; use explicit choices for binary
    // profiles and unknown selections so accessibility can reach every option.
    if (options.size > 2 && selectedPosition.anchored) {
      Slider(
        state = sliderState,
        enabled = enabled,
        modifier =
          Modifier.padding(horizontal = 20.dp).semantics {
            contentDescription = nativeString("Thinking")
            stateDescription = selectedLabel
          },
        thumb = {
          Box(
            Modifier
              .size(width = 28.dp, height = 20.dp)
              .background(
                color = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
                shape = RoundedCornerShape(10.dp),
              ),
          )
        },
        track = { state -> ChatEffortSliderTrack(state, options.size, enabled) },
      )
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
      ) {
        Text(nativeString("Faster"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
        Text(nativeString("Smarter"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
      }
    } else {
      options.forEachIndexed { index, option ->
        val optionSelected = selectedPosition.optionIndex == index
        Surface(
          onClick = {
            if (!optionSelected) {
              haptics.tap()
              onSelect(option.id)
            }
          },
          enabled = enabled,
          modifier = Modifier.fillMaxWidth().heightIn(min = ClawTheme.spacing.touchTarget).semantics { selected = optionSelected },
          color = Color.Transparent,
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Text(chatThinkingOptionLabel(option, languageTag), style = ClawTheme.type.body)
            if (optionSelected) Icon(Icons.Default.Check, contentDescription = nativeString("Selected"))
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatEffortSliderTrack(
  state: SliderState,
  optionCount: Int,
  enabled: Boolean,
) {
  val activeFraction =
    if (optionCount > 1) {
      (state.value / (optionCount - 1)).coerceIn(0f, 1f)
    } else {
      0f
    }
  val inactiveColor = ClawTheme.colors.text.copy(alpha = if (enabled) 0.07f else 0.04f)
  val activeColor = ClawTheme.colors.text.copy(alpha = if (enabled) 0.18f else 0.08f)
  val dotColor = ClawTheme.colors.text.copy(alpha = if (enabled) 0.28f else 0.12f)
  Canvas(modifier = Modifier.fillMaxWidth().height(26.dp)) {
    val cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
    drawRoundRect(color = inactiveColor, cornerRadius = cornerRadius)
    if (activeFraction > 0f) {
      val activeWidth = size.width * activeFraction
      drawRoundRect(
        color = activeColor,
        topLeft = Offset(x = if (layoutDirection == LayoutDirection.Rtl) size.width - activeWidth else 0f, y = 0f),
        size = Size(width = activeWidth, height = size.height),
        cornerRadius = cornerRadius,
      )
    }
    val dotRadius = 2.dp.toPx()
    chatEffortStopFractions(optionCount).forEach { fraction ->
      val visualFraction = chatEffortVisualFraction(fraction, layoutDirection)
      drawCircle(color = dotColor, radius = dotRadius, center = Offset(size.width * visualFraction, size.height / 2f))
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatEffortSheet(
  opening: ChatModelPickerSession,
  hazeState: HazeState?,
  options: List<ChatThinkingLevelOption>,
  selectedId: String,
  thinkingSupported: Boolean,
  thinkingLevelEnabled: Boolean,
  fastMode: Boolean,
  fastModeEnabled: Boolean,
  onSelect: (String) -> Unit,
  onFastModeChange: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  val thinkingOptions = if (thinkingSupported) options else emptyList()
  ModalBottomSheet(
    modifier = Modifier.foldAwareSheet(opening.geometry),
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = Color.Transparent,
    contentColor = ClawTheme.colors.text,
  ) {
    ClawGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = 28.dp,
      hazeState = hazeState,
      fillAlpha = 0.72f,
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
      ) {
      if (thinkingOptions.isNotEmpty()) {
        ChatEffortSliderControl(
          options = thinkingOptions,
          selectedId = selectedId,
          enabled = thinkingLevelEnabled,
          onSelect = onSelect,
        )
      }
      if (thinkingOptions.isNotEmpty()) {
        HorizontalDivider(color = ClawTheme.colors.border, modifier = Modifier.padding(top = 14.dp))
      }
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Icon(Icons.Default.Bolt, contentDescription = null, tint = ClawTheme.colors.primary, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(nativeString("Fast mode"), style = ClawTheme.type.body.copy(fontWeight = FontWeight.Medium))
          Text(
            nativeString("Faster responses, higher usage of limits."),
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
        Switch(
          checked = fastMode,
          onCheckedChange = onFastModeChange,
          enabled = fastModeEnabled,
          modifier = Modifier.semantics { contentDescription = nativeString("Fast mode") },
        )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BranchSwitcherSheet(
  opening: ChatModelPickerSession,
  branches: List<SessionBranch>,
  selectionEnabled: Boolean,
  onDismiss: () -> Unit,
  onSelect: (String) -> Unit,
) {
  ModalBottomSheet(
    modifier = Modifier.foldAwareSheet(opening.geometry),
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = ClawTheme.colors.surface,
    contentColor = ClawTheme.colors.text,
  ) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
      contentPadding = PaddingValues(bottom = 24.dp),
    ) {
      item {
        Text(
          text = nativeString("Switch branch"),
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
          style = ClawTheme.type.title,
          color = ClawTheme.colors.text,
        )
        HorizontalDivider(color = ClawTheme.colors.border, thickness = 1.dp)
      }
      itemsIndexed(branches, key = { _, branch -> branch.leafEntryId }) { _, branch ->
        Surface(
          onClick = { if (!branch.active) onSelect(branch.leafEntryId) },
          enabled = selectionEnabled && !branch.active,
          color = if (branch.active) ClawTheme.colors.surfacePressed else Color.Transparent,
          contentColor = ClawTheme.colors.text,
        ) {
          Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = ClawTheme.spacing.touchTarget).padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
              Text(
                text = branch.headline.trim().takeIf(String::isNotEmpty) ?: nativeString("Untitled branch"),
                style = ClawTheme.type.body,
                color = ClawTheme.colors.text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
              )
              Text(
                text = branchMetadataText(branch),
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.textMuted,
              )
            }
            if (branch.active) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = nativeString("Current branch"),
                tint = ClawTheme.colors.primary,
              )
            }
          }
        }
      }
    }
  }
}

internal fun branchMessageCountText(count: Int): String = nativeString("Messages: \$count", count)

internal fun branchMetadataText(branch: SessionBranch): String {
  val count = branchMessageCountText(branch.messageCount)
  val updated =
    branch.updatedAt
      ?.let { timestamp -> runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull() }
      ?.let(::relativeSessionTime)
  return if (updated == null) count else nativeString("\$count · \$updated", count, updated)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatModelPickerSheet(
  opening: ChatModelPickerSession,
  hazeState: HazeState?,
  admit: () -> Boolean,
  admitPermissions: () -> Boolean,
  sections: ChatModelPickerSections,
  favorites: Set<String>,
  selectedModelLabel: String,
  modelSelectionLocked: Boolean,
  contextUsage: ChatContextUsage,
  messages: List<ChatMessage>,
  permissionMode: ChatPermissionMode?,
  permissionModePending: Boolean,
  permissionPickerEnabled: Boolean,
  permissionUnavailableReason: String?,
  canSelectFullPermission: Boolean,
  onPermissionModeChange: (ChatPermissionMode?) -> Boolean,
  onDismiss: () -> Unit,
  onSelect: (String?) -> Unit,
  onOpenProviders: (String) -> Unit,
  onToggleFavorite: (String) -> Unit,
) {
  var showPermissionPicker by rememberSaveable { mutableStateOf(false) }
  var showUsageDetails by rememberSaveable { mutableStateOf(false) }
  LaunchedEffect(permissionPickerEnabled) {
    if (showPermissionPicker && !permissionPickerEnabled && admit()) showPermissionPicker = false
  }
  ModalBottomSheet(
    modifier = Modifier.foldAwareSheet(opening.geometry),
    onDismissRequest = onDismiss,
    // IME dismissal can remove a partial-height anchor while the selector opens.
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = Color.Transparent,
    contentColor = ClawTheme.colors.text,
    properties =
      ModalBottomSheetProperties(
        shouldDismissOnBackPress = false,
        shouldDismissOnClickOutside = true,
      ),
  ) {
    // Material captures its Back callback's enabled state when the dialog is created.
    // Own both pages here so recreation cannot leave the model page without Back.
    BackHandler {
      if (admit()) {
        if (showPermissionPicker) showPermissionPicker = false else onDismiss()
      }
    }
    // Keep the outer sheet unconstrained: Material anchors use the full window height.
    // Cap only its scrollable content against the actual inset-adjusted available bounds.
    ClawGlassSurface(
      modifier = Modifier.fillMaxWidth(),
      cornerRadius = 28.dp,
      hazeState = hazeState,
      fillAlpha = 0.72f,
      contentPadding = PaddingValues(0.dp),
    ) {
      BoxWithConstraints {
        Box(Modifier.heightIn(max = maxHeight * 0.5f)) {
        if (showPermissionPicker) {
          ChatPermissionPicker(
            selectedMode = permissionMode,
            canSelectFull = canSelectFullPermission,
            onBack = { if (admit()) showPermissionPicker = false },
            onSelect = { mode ->
              if (onPermissionModeChange(mode)) showPermissionPicker = false
            },
          )
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
          ) {
            item {
              Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = selectedModelLabel, style = ClawTheme.type.label, color = ClawTheme.colors.text)
                if (modelSelectionLocked) {
                  Text(text = nativeString("Model selection is locked for this session."), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                }
                chatContextSummary(contextUsage)?.let { summary ->
                  val (pressureLabel, contextColor) =
                    when {
                      summary.percent >= 90 -> nativeString("Critical") to ClawTheme.colors.danger
                      summary.percent >= 75 -> nativeString("Warning") to ClawTheme.colors.warning
                      else -> null to ClawTheme.colors.primary
                    }
                  FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                  ) {
                    Text(text = nativeString("Context window"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                    Text(text = summary.detail, style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold), color = ClawTheme.colors.text)
                    pressureLabel?.let { Text(text = it, style = ClawTheme.type.caption, color = contextColor) }
                  }
                  LinearProgressIndicator(
                    progress = { summary.fraction },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = contextColor,
                    trackColor = ClawTheme.colors.surfacePressed,
                  )
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                  Text(text = nativeString("Latest run"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  TextButton(
                    onClick = { if (admit()) showUsageDetails = !showUsageDetails },
                    modifier = Modifier.semantics { stateDescription = if (showUsageDetails) nativeString("Expanded") else nativeString("Collapsed") },
                  ) {
                    Text(nativeString("Details"))
                    Icon(if (showUsageDetails) Icons.Default.KeyboardArrowUp else Icons.Default.ArrowDropDown, contentDescription = null)
                  }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                  ChatContextStat(label = nativeString("Non-cached input"), value = formatContextUsageTokens(contextUsage.inputTokens), modifier = Modifier.weight(1f))
                  ChatContextStat(label = nativeString("Output"), value = formatContextUsageTokens(contextUsage.outputTokens), modifier = Modifier.weight(1f))
                  ChatContextStat(label = nativeString("Est. cost"), value = formatContextEstimatedCost(contextUsage.estimatedCostUsd), modifier = Modifier.weight(1f))
                }
                if (showUsageDetails) {
                  Text(text = nativeString("Non-cached input excludes cache reads."), style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle)
                }
                val latestCallUsage = latestChatMessageUsage(messages)
                val latestCallCostStats = latestChatMessageCost(messages)?.let(::availableChatCostStats).orEmpty()
                if (showUsageDetails && (latestCallUsage != null || latestCallCostStats.isNotEmpty())) {
                  Text(text = nativeString("Latest model call"), style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted)
                  latestCallUsage?.let { usage ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                      ChatContextStat(label = nativeString("Non-cached input"), value = formatContextUsageTokens(usage.input), modifier = Modifier.weight(1f))
                      ChatContextStat(label = nativeString("Output"), value = formatContextUsageTokens(usage.output), modifier = Modifier.weight(1f))
                      ChatContextStat(label = nativeString("Cache read"), value = formatContextUsageTokens(usage.cacheRead), modifier = Modifier.weight(1f))
                    }
                  }
                  latestCallCostStats.chunked(2).forEach { row ->
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                      row.forEach { (label, value) ->
                        ChatContextStat(
                          label = label,
                          value = formatContextEstimatedCost(value),
                          modifier = Modifier.weight(1f),
                        )
                      }
                      if (row.size == 1) Box(modifier = Modifier.weight(1f))
                    }
                  }
                }
              }
            }
            item {
              Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = ClawTheme.spacing.touchTarget),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Surface(
                  onClick = { if (admitPermissions()) showPermissionPicker = true },
                  enabled = permissionPickerEnabled,
                  modifier = Modifier.weight(1f).heightIn(min = ClawTheme.spacing.touchTarget),
                  color = Color.Transparent,
                ) {
                  Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ChatPermissionIcon(mode = permissionMode, contentDescription = null, modifier = Modifier.size(20.dp))
                    Text(nativeString("Permissions"), style = ClawTheme.type.body, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                  }
                }
                Text(
                  text = if (permissionModePending) nativeString("Applying permissions…") else chatPermissionModeLabel(permissionMode),
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                  modifier = Modifier.padding(end = 20.dp),
                )
              }
              permissionUnavailableReason?.let { reason ->
                Text(reason, style = ClawTheme.type.caption, color = ClawTheme.colors.textMuted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
              }
            }
            if (modelSelectionLocked) return@LazyColumn
            item {
              HorizontalDivider(color = ClawTheme.colors.border)
            }
            item {
              Surface(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth().heightIn(min = ClawTheme.spacing.touchTarget),
                color = Color.Transparent,
                contentColor = ClawTheme.colors.text,
              ) {
                Text(
                  text = nativeString("Default model"),
                  modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                  style = ClawTheme.type.body,
                )
              }
            }
            item {
              HorizontalDivider(color = ClawTheme.colors.border, thickness = 1.dp)
            }
            listOf(
              nativeString("Pinned") to sections.pinned,
              nativeString("Recent") to sections.recent,
              nativeString("Models") to sections.remaining,
            ).forEach { (title, models) ->
              if (models.isNotEmpty()) {
                item(key = "section-$title") {
                  Text(
                    text = title,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 6.dp),
                    style = ClawTheme.type.caption,
                    color = ClawTheme.colors.textMuted,
                  )
                }
                itemsIndexed(
                  items = models,
                  key = { _, model -> model.providerQualifiedRef() },
                ) { _, model ->
                  val ref = model.providerQualifiedRef()
                  ChatModelPickerRow(
                    model = model,
                    pinned = ref in favorites,
                    onSelect = { onSelect(ref) },
                    onOpenProviders = { onOpenProviders(ref) },
                    onToggleFavorite = { onToggleFavorite(ref) },
                  )
                }
              }
            }
          }
        }
      }
    }
  }
  }
}

@Composable
private fun ChatModelPickerRow(
  model: GatewayModelSummary,
  pinned: Boolean,
  onSelect: () -> Unit,
  onOpenProviders: () -> Unit,
  onToggleFavorite: () -> Unit,
) {
  val action = chatModelPickerAction(model)
  val unavailable = model.available == false
  val availabilityLabel =
    if (!unavailable) {
      null
    } else {
      when (model.unavailableReason) {
        GatewayModelUnavailableReason.MissingAuth,
        GatewayModelUnavailableReason.AuthFailed,
        -> nativeString("Authentication needed")

        GatewayModelUnavailableReason.Cooldown -> nativeString("Unavailable")

        null -> nativeString("Unavailable")
      }
    }
  Surface(
    onClick = {
      when (action) {
        ChatModelPickerAction.Select -> onSelect()
        ChatModelPickerAction.OpenProviders -> onOpenProviders()
        ChatModelPickerAction.Disabled -> Unit
      }
    },
    enabled = action != ChatModelPickerAction.Disabled,
    modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
    color = Color.Transparent,
    contentColor = if (unavailable) ClawTheme.colors.textMuted else ClawTheme.colors.text,
  ) {
    Row(
      modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      ProviderBrandIcon(provider = model.provider, size = 24.dp)
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
          text = model.name,
          style = ClawTheme.type.body.copy(fontWeight = FontWeight.Medium),
          color = if (unavailable) ClawTheme.colors.textMuted else ClawTheme.colors.text,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = listOfNotNull(model.provider, availabilityLabel).joinToString(" · "),
          style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Normal),
          color = if (unavailable) ClawTheme.colors.warning else ClawTheme.colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      IconButton(onClick = onToggleFavorite, enabled = !unavailable) {
        Icon(
          imageVector = if (pinned) Icons.Default.Star else Icons.Default.StarBorder,
          contentDescription = if (pinned) nativeString("Unpin model") else nativeString("Pin model"),
          tint = if (pinned) ClawTheme.colors.primary else ClawTheme.colors.textMuted,
        )
      }
    }
  }
}

@Composable
private fun SlashCommandPanel(
  commands: List<ChatCommandEntry>,
  onSelect: (ChatCommandEntry) -> Unit,
  modifier: Modifier,
) {
  ClawPanel(modifier = modifier, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp)) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
      if (commands.isEmpty()) {
        Text(
          text = nativeString("No commands found"),
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
          modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp),
        )
      } else {
        commands.forEachIndexed { index, command ->
          SlashCommandRow(command = command, onClick = { onSelect(command) })
          if (index != commands.lastIndex) {
            HorizontalDivider(color = ClawTheme.colors.border, thickness = 1.dp)
          }
        }
      }
    }
  }
}

@Composable
private fun SlashCommandRow(
  command: ChatCommandEntry,
  onClick: () -> Unit,
) {
  Surface(onClick = onClick, color = Color.Transparent, contentColor = ClawTheme.colors.text) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .padding(horizontal = 10.dp, vertical = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        text = slashCommandText(command),
        style = ClawTheme.type.label,
        color = ClawTheme.colors.text,
        modifier = Modifier.width(82.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(
          text = command.description.ifBlank { command.category ?: nativeString("Command") },
          style = ClawTheme.type.caption,
          color = ClawTheme.colors.textMuted,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}

@Composable
private fun ChatOfflineNotice(
  status: String,
  onFixConnection: () -> Unit,
  onCopyDiagnostics: () -> Unit,
) {
  ClawPanel(contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
      Text(
        text = nativeString("Gateway offline"),
        style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
        color = ClawTheme.colors.warning,
      )
      Text(
        text = status,
        style = ClawTheme.type.caption.copy(fontSize = 12.5.sp, lineHeight = 16.sp),
        color = ClawTheme.colors.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      ChatOfflineActions(onFixConnection = onFixConnection, onCopyDiagnostics = onCopyDiagnostics)
    }
  }
}

internal data class ChatPermissionOption(
  val mode: ChatPermissionMode?,
  val label: String,
  val description: String,
)

internal fun chatPermissionOptions(): List<ChatPermissionOption> =
  listOf(
    ChatPermissionOption(null, nativeString("Policy default"), nativeString("Follow the agent's configured policy.")),
    ChatPermissionOption(
      ChatPermissionMode.ReadOnly,
      nativeString("Read only"),
      nativeString("Read-only access; native tool approval rules still apply."),
    ),
    ChatPermissionOption(
      ChatPermissionMode.Guarded,
      nativeString("Guarded"),
      nativeString("Human review for requests beyond the session's access."),
    ),
    ChatPermissionOption(
      ChatPermissionMode.Workspace,
      nativeString("Workspace"),
      nativeString("AI review, with human fallback, for additional access."),
    ),
    ChatPermissionOption(
      ChatPermissionMode.Full,
      nativeString("Full access"),
      nativeString("Run without approval prompts, subject to host and tool policy."),
    ),
  )

internal fun chatPermissionModeLabel(mode: ChatPermissionMode?): String = chatPermissionOptions().first { it.mode == mode }.label

internal fun canSelectChatPermissionMode(
  mode: ChatPermissionMode?,
  canSelectFull: Boolean,
): Boolean = mode != ChatPermissionMode.Full || canSelectFull

@Composable
private fun ChatInputPill(
  inputEnabled: Boolean,
  onOpenDetails: (() -> Unit)?,
  gatewayConnecting: Boolean,
  value: String,
  onValueChange: (String) -> Unit,
  onPickImages: () -> Unit,
  onPickAudioOrDocument: () -> Unit,
  onPickVideo: () -> Unit,
  onCapturePhoto: () -> Unit,
  onStartVoiceNote: () -> Unit,
  recordVoiceNoteEnabled: Boolean,
  dictationActive: Boolean,
  dictationEnabled: Boolean,
  onToggleDictation: () -> Unit,
  talkActive: Boolean,
  liveTalkVisible: Boolean,
  voiceInputVisible: Boolean,
  onToggleTalk: () -> Unit,
  runActive: Boolean,
  sendInFlight: Boolean,
  onAbort: () -> Unit,
  hasContent: Boolean,
  sendEnabled: Boolean,
  onSend: () -> Unit,
  selectedModelLabel: String,
  modelPickerEnabled: Boolean,
  onOpenModelPicker: () -> Unit,
  modelSections: ChatModelPickerSections,
  modelFavorites: Set<String>,
  modelSelectionLocked: Boolean,
  onSelectModel: (String?) -> Unit,
  onToggleModelFavorite: (String) -> Unit,
  onOpenModelProviders: (String) -> Unit,
  thinkingLevel: String,
  thinkingOptions: List<ChatThinkingLevelOption>,
  thinkingSupported: Boolean,
  thinkingLevelEnabled: Boolean,
  fastMode: Boolean,
  fastModeEnabled: Boolean,
  onOpenEffortPicker: () -> Unit,
  contextUsage: ChatContextUsage,
  hazeState: HazeState? = null,
  modifier: Modifier = Modifier,
  focusRequestId: Int = 0,
) {
  val keyboardController = LocalSoftwareKeyboardController.current
  val focusManager = LocalFocusManager.current
  fun dismissKeyboardForPicker() {
    focusManager.clearFocus(force = true)
    keyboardController?.hide()
  }
  // Both composer menus grow out of their own button through the pane's shared
  // morph surface. Each keeps its own anchor: the surface has one slot, and the
  // last control to lay out must not decide where the next menu opens.
  val attachmentTrigger = clawMorphTrigger(LocalClawMorphMenuHost.current)
  val modelTrigger = clawMorphTrigger(LocalClawMorphMenuHost.current)
  val attachmentMenuContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
    ClawGlassMenuItem(
      label = nativeString("Camera"),
      icon = LucideCameraIcon,
      onClick = onCapturePhoto,
    )
    ClawGlassMenuItem(
      label = nativeString("Photos"),
      icon = LucideImageIcon,
      onClick = onPickImages,
    )
    ClawGlassMenuItem(
      label = nativeString("Videos"),
      icon = LucideVideoIcon,
      onClick = onPickVideo,
    )
    ClawGlassMenuItem(
      label = nativeString("Files"),
      icon = LucidePaperclipIcon,
      onClick = onPickAudioOrDocument,
    )
  }
  val modelMenuContent: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit = {
    if (!modelSelectionLocked) {
      listOf(
        nativeString("Pinned") to modelSections.pinned,
        nativeString("Recent") to modelSections.recent,
        nativeString("Models") to modelSections.remaining,
      ).forEach { (title, models) ->
        if (models.isNotEmpty()) {
          Text(
            text = title,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
            modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 2.dp),
          )
          models.forEach { model ->
            val ref = model.providerQualifiedRef()
            ClawGlassMenuItem(
              label = model.name.ifEmpty { ref.substringAfterLast('/') },
              selected = ref in modelFavorites,
              enabled = model.available != false,
              onClick = { onSelectModel(ref) },
            )
          }
        }
      }
      ClawGlassMenuItem(
        label = nativeString("Default model"),
        selected = false,
        onClick = { onSelectModel(null) },
      )
    }
    HorizontalDivider(
      color = ClawTheme.colors.border,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
    ClawGlassMenuItem(
      label = nativeString("More info"),
      icon = MiuixIcons.Medium.MiuixMore,
      onClick = {
        dismissKeyboardForPicker()
        onOpenModelPicker()
      },
    )
  }
  // Grows up from the button's bottom-left and covers it, like the popup did.
  // The heavier tint keeps the input bar underneath from reading through.
  val composerMenuOrigin = TransformOrigin(0f, 1f)
  val draftStyle = chatDraftStyle()
  val haptics = rememberClawHaptics()

  ClawGlassSurface(
    modifier = modifier.testTag("chat-composer-surface"),
    cornerRadius = 26.dp,
    hazeState = hazeState,
    fillAlpha = 0.58f,
    contentPadding = PaddingValues(0.dp),
  ) {
    Column(
      // No border here: ClawGlassSurface already draws the hairline edge, and a
      // second grey stroke on top of the glass tint read as a dirty rim.
      modifier = Modifier.clip(RoundedCornerShape(26.dp)),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            // Reserve the action row before measuring the draft in the IME viewport.
            .weight(1f, fill = false)
            .heightIn(min = ClawTheme.spacing.touchTarget)
            // 18dp puts the caret and placeholder on the same vertical line as
            // the ＋ icon's left edge (4dp row inset + 14dp icon inset).
            .padding(start = 18.dp, end = 14.dp, top = 8.dp, bottom = 4.dp),
        contentAlignment = Alignment.CenterStart,
      ) {
        if (value.isEmpty()) {
          AnimatedContent(
            targetState = gatewayConnecting,
            transitionSpec = {
              (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.98f, animationSpec = tween(220))) togetherWith
                fadeOut(animationSpec = tween(160))
            },
            label = "gateway connection placeholder",
          ) { connecting ->
            if (connecting) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(14.dp),
                  strokeWidth = 1.8.dp,
                  color = ClawTheme.colors.textMuted,
                )
                Text(
                  text = nativeString("Connecting…"),
                  style = draftStyle,
                  color = ClawTheme.colors.textMuted,
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis,
                )
              }
            } else {
              Text(
                text = nativeString("Message OpenClaw"),
                style = draftStyle,
                color = ClawTheme.colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
              )
            }
          }
        }
        ChatComposerEditText(
          value = value,
          onValueChange = onValueChange,
          enabled = inputEnabled,
          textStyle = draftStyle.copy(color = ClawTheme.colors.text),
          cursorColor = ClawTheme.colors.primary,
          sendEnabled = sendEnabled,
          onSend = onSend,
          focusRequestId = focusRequestId,
          modifier = Modifier.fillMaxWidth(),
        )
      }
      Row(
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        if (onOpenDetails != null) {
          IconButton(onClick = onOpenDetails, modifier = Modifier.size(ClawTheme.spacing.touchTarget)) {
            Icon(MiuixIcons.Medium.MiuixMore, contentDescription = nativeString("Details"))
          }
        }
        Box {
          Surface(
            onClick = {
              haptics.tap()
              attachmentTrigger.open(
                origin = composerMenuOrigin,
                panelWidth = 200.dp,
                tintAlpha = 0.82f,
                content = attachmentMenuContent,
              )
            },
            modifier = Modifier.size(ClawTheme.spacing.touchTarget).then(attachmentTrigger.modifier),
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = ClawTheme.colors.textMuted,
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(imageVector = MiuixIcons.Medium.MiuixAdd, contentDescription = nativeString("Add attachment"), modifier = Modifier.size(20.dp))
            }
          }
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
          Box(modifier = Modifier.weight(1f, fill = false).then(modelTrigger.modifier)) {
            ChatComposerModelPicker(
              label = selectedModelLabel,
              contextUsage = contextUsage,
              enabled = modelPickerEnabled,
              onClick = {
                modelTrigger.open(
                  origin = composerMenuOrigin,
                  panelWidth = 200.dp,
                  tintAlpha = 0.82f,
                  content = modelMenuContent,
                )
              },
            )
          }
          if (thinkingSupported || fastModeEnabled || fastMode) {
            ChatThinkingLevelPicker(
              options = thinkingOptions,
              selectedId = thinkingLevel,
              thinkingSupported = thinkingSupported,
              thinkingLevelEnabled = thinkingLevelEnabled,
              fastMode = fastMode,
              fastModeEnabled = fastModeEnabled,
              onOpen = { dismissKeyboardForPicker(); onOpenEffortPicker() },
            )
          }
        }
        if (liveTalkVisible && talkActive) {
          LiveTalkButton(active = true, onClick = onToggleTalk)
        } else if (voiceInputVisible) {
          ChatComposerMicButton(
            dictationActive = dictationActive,
            dictationEnabled = dictationEnabled,
            voiceNoteEnabled = recordVoiceNoteEnabled,
            onToggleDictation = onToggleDictation,
            onStartVoiceNote = onStartVoiceNote,
          )
        }
        ChatComposerPrimaryActionSlot(
          action =
            resolveChatComposerPrimaryAction(
              talkActive = talkActive,
              runActive = runActive,
              sendInFlight = sendInFlight,
              hasContent = hasContent,
              liveTalkVisible = liveTalkVisible,
            ),
          sendEnabled = inputEnabled && sendEnabled,
          onSend = onSend,
          onAbort = onAbort,
          onToggleTalk = onToggleTalk,
          modifier = Modifier.size(ClawTheme.spacing.touchTarget),
        )
      }
    }
  }
}

@Composable
private fun ChatPermissionIcon(
  mode: ChatPermissionMode?,
  contentDescription: String?,
  modifier: Modifier = Modifier,
) {
  val icon =
    when (mode) {
      null -> Icons.Default.Security
      ChatPermissionMode.ReadOnly -> Icons.Default.GppMaybe
      ChatPermissionMode.Guarded -> Icons.Default.Policy
      ChatPermissionMode.Workspace -> Icons.Default.AdminPanelSettings
      ChatPermissionMode.Full -> Icons.Default.Shield
    }
  Icon(icon, contentDescription = contentDescription, modifier = modifier)
}

@Composable
private fun ChatPermissionPicker(
  selectedMode: ChatPermissionMode?,
  canSelectFull: Boolean,
  onBack: () -> Unit,
  onSelect: (ChatPermissionMode?) -> Unit,
) {
  val options = chatPermissionOptions()
  LazyColumn(
    modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
    contentPadding = PaddingValues(bottom = 24.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        TextButton(onClick = onBack) { Text(nativeString("Back")) }
        Text(
          text = nativeString("Permissions"),
          style = ClawTheme.type.label.copy(fontWeight = FontWeight.SemiBold),
          modifier = Modifier.padding(start = 4.dp),
        )
      }
      HorizontalDivider(color = ClawTheme.colors.border)
    }
    itemsIndexed(options, key = { _, option -> option.mode?.wireValue ?: "policy-default" }) { _, option ->
      val selected = option.mode == selectedMode
      val selectable = canSelectChatPermissionMode(option.mode, canSelectFull)
      Surface(
        onClick = { onSelect(option.mode) },
        enabled = selectable,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp).semantics { this.selected = selected },
        color = Color.Transparent,
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
          ChatPermissionIcon(mode = option.mode, contentDescription = null, modifier = Modifier.size(18.dp))
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
              text = option.label,
              style = ClawTheme.type.body.copy(fontWeight = FontWeight.Medium),
            )
            Text(
              text = option.description,
              style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Normal),
              color = ClawTheme.colors.textMuted,
            )
            if (!selectable) {
              Text(
                text = nativeString("Full access requires operator.admin access."),
                style = ClawTheme.type.caption,
                color = ClawTheme.colors.warning,
              )
            }
          }
          when {
            !selectable -> Icon(Icons.Default.Lock, contentDescription = nativeString("Requires operator.admin"))
            selected -> Icon(Icons.Default.Check, contentDescription = nativeString("Selected"))
          }
        }
      }
    }
  }
}

internal data class ChatContextSummary(
  val fraction: Float,
  val percent: Int,
  val approximate: Boolean,
  val detail: String,
)

internal fun chatContextSummary(
  usage: ChatContextUsage,
  locale: Locale = Locale.getDefault(),
): ChatContextSummary? {
  val fraction = contextMeterWidth(usage) ?: return null
  val used = usage.totalTokens?.takeIf { it >= 0L } ?: return null
  val context = usage.contextTokens?.takeIf { it > 0L } ?: return null
  val approximate = usage.totalTokensFresh == false
  val approximation = if (approximate) "~" else ""
  val percent = (fraction * 100).roundToInt()
  return ChatContextSummary(
    fraction = fraction,
    percent = percent,
    approximate = approximate,
    detail = "$approximation${formatCompactTokenCount(used, locale)} / ${formatCompactTokenCount(context, locale)} \u00b7 $approximation$percent%",
  )
}

internal fun formatContextUsageTokens(
  value: Long?,
  locale: Locale = Locale.getDefault(),
): String = value?.takeIf { it >= 0L }?.let { formatCompactTokenCount(it, locale) } ?: "\u2014"

internal fun formatContextEstimatedCost(value: Double?): String {
  val cost = value?.takeIf { it.isFinite() && it >= 0.0 } ?: return "\u2014"
  val format =
    when {
      cost == 0.0 -> "%.2f"
      cost < 0.01 -> "%.4f"
      cost < 1.0 -> "%.3f"
      else -> "%.2f"
    }
  return "\u0024" + String.format(Locale.US, format, cost)
}

private fun ChatMessage.isContextBoundary(): Boolean =
  when (transcriptMarker?.kind) {
    "compaction", "reset" -> true
    else -> false
  }

private fun latestRealAssistantMessage(messages: List<ChatMessage>): ChatMessage? {
  for (message in messages.asReversed()) {
    if (message.isContextBoundary()) return null
    if (message.role != "assistant" || message.isSyntheticDisplay) continue
    if (message.isTranscriptOnlyOpenClawAssistant()) continue
    return message
  }
  return null
}

internal fun latestChatMessageUsage(messages: List<ChatMessage>): ChatMessageUsage? = latestRealAssistantMessage(messages)?.usage

internal fun latestChatMessageCost(messages: List<ChatMessage>): ChatMessageCost? = latestRealAssistantMessage(messages)?.cost

internal fun availableChatCostStats(cost: ChatMessageCost): List<Pair<String, Double>> {
  val components =
    listOf(
      nativeString("Input cost") to cost.input,
      nativeString("Output cost") to cost.output,
      nativeString("Cache read cost") to cost.cacheRead,
      nativeString("Cache write cost") to cost.cacheWrite,
    ).mapNotNull { (label, value) -> value?.takeIf { it.isFinite() && it >= 0.0 }?.let { label to it } }
  if (components.isNotEmpty()) return components
  return cost.total
    ?.takeIf { it.isFinite() && it >= 0.0 }
    ?.let { listOf(nativeString("Est. cost") to it) }
    .orEmpty()
}

@Composable
private fun ChatContextStat(
  label: String,
  value: String,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(text = label, style = ClawTheme.type.caption, color = ClawTheme.colors.textSubtle)
    Text(
      text = value,
      style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold),
      color = ClawTheme.colors.text,
    )
  }
}

internal fun chatThinkingChipStateDescription(
  fastMode: Boolean,
  thinkingLevel: String,
  thinkingOptions: List<ChatThinkingLevelOption>,
  languageTag: String? = null,
): String {
  val normalizedLevel = thinkingLevel.trim().ifEmpty { "off" }
  val selectedOption =
    thinkingOptions.firstOrNull { it.id.trim().equals(normalizedLevel, ignoreCase = true) }
      ?: ChatThinkingLevelOption(id = normalizedLevel, label = normalizedLevel)
  val selectedLabel = chatThinkingOptionLabel(selectedOption, languageTag)
  val fastModeState =
    if (fastMode) {
      nativeString("On")
    } else {
      nativeString("Off")
    }
  return nativeString(
    "\$selectedLabel, \$fastModeLabel: \$fastModeState",
    selectedLabel,
    nativeString("Fast mode"),
    fastModeState,
  )
}

@Composable
private fun ChatComposerModelPicker(
  label: String,
  contextUsage: ChatContextUsage,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val description = nativeString("Model")
  val contextDescription = chatContextSummary(contextUsage)?.let { nativeString("Context: \$detail", it.detail) }
  val haptics = rememberClawHaptics()
  Surface(
    onClick = {
      haptics.tap()
      onClick()
    },
    enabled = enabled,
    modifier =
      modifier.heightIn(min = ClawTheme.spacing.touchTarget).semantics {
        contentDescription = description
        contextDescription?.let { stateDescription = it }
        role = Role.Button
      },
    shape = RoundedCornerShape(ClawTheme.radii.pill),
    color = Color.Transparent,
    contentColor = if (enabled) ClawTheme.colors.textMuted else ClawTheme.colors.textSubtle,
  ) {
    Box(modifier = Modifier.padding(horizontal = 4.dp), contentAlignment = Alignment.CenterStart) {
      Text(
        text = label,
        style = ClawTheme.type.caption,
        // Android supports middle ellipsis only on one line; keep both ends of the model name visible.
        maxLines = 1,
        overflow = TextOverflow.MiddleEllipsis,
      )
    }
  }
}

@Composable
internal fun LiveTalkButton(
  active: Boolean,
  onClick: () -> Unit,
) {
  val buttonDescription = if (active) nativeString("End Talk") else nativeString("Start Talk")
  Surface(
    onClick = onClick,
    modifier =
      Modifier
        .size(ClawTheme.spacing.touchTarget)
        .semantics { contentDescription = buttonDescription },
    shape = CircleShape,
    color = Color.Transparent,
    contentColor = if (active) ClawTheme.colors.accent else ClawTheme.colors.primaryText,
  ) {
    Box(modifier = Modifier.padding(8.dp).background(if (active) Color.Transparent else ClawTheme.colors.primary, CircleShape), contentAlignment = Alignment.Center) {
      LiveTalkWaveform(active = active, modifier = Modifier.size(20.dp))
    }
  }
}

@Composable
internal fun StopButton(onClick: () -> Unit) {
  Surface(
    onClick = onClick,
    modifier = Modifier.size(ClawTheme.spacing.touchTarget),
    shape = CircleShape,
    color = Color.Transparent,
  ) {
    // HyperOS run-stop: a light disc with a heavy ring and a rounded square in
    // the middle, not a filled disc with a pause glyph. Same 32dp disc as the
    // send button so the slot never changes size between states.
    Box(
      modifier =
        Modifier
          .padding(8.dp)
          .size(32.dp)
          .clip(CircleShape)
          .background(ClawTheme.colors.surface)
          .border(3.dp, ClawTheme.colors.danger, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      Box(
        modifier =
          Modifier
            .size(10.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(ClawTheme.colors.danger),
      )
    }
  }
}

@Composable
private fun LiveTalkWaveform(
  active: Boolean,
  modifier: Modifier = Modifier,
) {
  val color = LocalContentColor.current
  val phase =
    if (active) {
      val value by rememberInfiniteTransition().animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(animation = tween(durationMillis = 720, easing = LinearEasing), repeatMode = RepeatMode.Restart),
      )
      value
    } else {
      0f
    }

  Canvas(modifier = modifier) {
    val strokeWidth = 1.5.dp.toPx()
    repeat(5) { index ->
      val envelope = 1f - abs(index - 2) * 0.28f
      val pulse = if (active) 0.7f + 0.3f * ((sin(phase + index * 0.9f) + 1f) / 2f) else 1f
      val halfHeight = (size.height - strokeWidth * 2f) * envelope * pulse / 2f
      val x = size.width * (index + 0.5f) / 5f
      drawLine(
        color = color,
        start = Offset(x, center.y - halfHeight),
        end = Offset(x, center.y + halfHeight),
        strokeWidth = strokeWidth,
        cap = StrokeCap.Round,
      )
    }
  }
}

@Composable
private fun AttachmentStrip(
  attachments: List<PendingAttachment>,
  preparing: Boolean,
  onRemoveAttachment: (String) -> Unit,
  onAnnotateAttachment: (PendingAttachment, Rect) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    horizontalArrangement = Arrangement.spacedBy(6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    attachments.forEach { attachment ->
      key(attachment.id) {
        AttachmentChip(
          attachment = attachment,
          onRemove = { onRemoveAttachment(attachment.id) },
          onAnnotate = { chipBounds -> onAnnotateAttachment(attachment, chipBounds) },
        )
      }
    }
    if (preparing) {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        strokeWidth = 2.dp,
        color = ClawTheme.colors.primary,
      )
      Text(
        text = nativeString("Preparing attachments"),
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textMuted,
      )
    }
  }
}

/** Image attachment queued in the annotation editor, with the chip it launched from. */
internal data class AttachmentAnnotationTarget(
  val attachment: PendingAttachment,
  val chipBounds: Rect,
)

@Composable
private fun AttachmentChip(
  attachment: PendingAttachment,
  onRemove: () -> Unit,
  onAnnotate: ((Rect) -> Unit)? = null,
) {
  val videoThumbnail =
    remember(attachment.videoThumbnailBase64) {
      attachment.videoThumbnailBase64?.let(::decodeBase64Bitmap)
    }
  val isImage = attachment.mimeType.startsWith("image/")
  val imageState = if (isImage) rememberBase64ImageState(attachment.base64) else null
  var chipBounds by remember { mutableStateOf<Rect?>(null) }
  var thumbBounds by remember { mutableStateOf<Rect?>(null) }
  val annotator = onAnnotate
  val chipModifier = Modifier.onGloballyPositioned { chipBounds = it.boundsInRoot() }

  val content: @Composable () -> Unit = {
    if (isImage) {
      val thumb = imageState?.image
      val thumbModifier =
        Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(8.dp))
          .onGloballyPositioned { thumbBounds = it.boundsInRoot() }
      if (thumb != null) {
        Image(
          bitmap = thumb,
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = thumbModifier,
        )
      } else {
        Box(modifier = thumbModifier.background(ClawTheme.colors.canvas), contentAlignment = Alignment.Center) {
          Icon(imageVector = LucideImageIcon, contentDescription = null, modifier = Modifier.size(16.dp), tint = ClawTheme.colors.textMuted)
        }
      }
    } else if (attachment.mimeType.startsWith("audio/")) {
      Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(14.dp), tint = ClawTheme.colors.textMuted)
    } else if (attachment.mimeType.startsWith("video/")) {
      if (videoThumbnail != null) {
        Image(
          bitmap = videoThumbnail.asImageBitmap(),
          contentDescription = null,
          contentScale = ContentScale.Crop,
          modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
        )
      } else {
        Icon(imageVector = Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(14.dp), tint = ClawTheme.colors.textMuted)
      }
    }
    Text(
      text =
        attachment.durationMs?.let { duration -> nativeString("Voice note · \${formatVoiceNoteDuration(duration)}", formatVoiceNoteDuration(duration)) }
          ?: attachment.fileName,
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 96.dp),
    )
    Surface(onClick = onRemove, modifier = Modifier.size(ClawTheme.spacing.touchTarget), shape = CircleShape, color = ClawTheme.colors.canvas, contentColor = ClawTheme.colors.text) {
      Box(contentAlignment = Alignment.Center) {
        Icon(imageVector = Icons.Default.Close, contentDescription = nativeString("Remove attachment"), modifier = Modifier.size(13.dp))
      }
    }
  }

  if (annotator != null && isImage) {
    Surface(
      onClick = { (thumbBounds ?: chipBounds)?.let(annotator) },
      shape = RoundedCornerShape(ClawTheme.radii.pill),
      color = ClawTheme.colors.surfaceRaised,
      contentColor = ClawTheme.colors.text,
      border = BorderStroke(1.dp, ClawTheme.colors.border),
      modifier = chipModifier,
      content = { Row(modifier = Modifier.padding(start = 9.dp, top = 5.dp, end = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = { content() }) },
    )
  } else {
    Surface(
      shape = RoundedCornerShape(ClawTheme.radii.pill),
      color = ClawTheme.colors.surfaceRaised,
      contentColor = ClawTheme.colors.text,
      border = BorderStroke(1.dp, ClawTheme.colors.border),
      modifier = chipModifier,
    ) {
      Row(modifier = Modifier.padding(start = 9.dp, top = 5.dp, end = 5.dp, bottom = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = { content() })
    }
  }
}

private fun isActiveSessionChoice(
  choiceKey: String,
  sessionKey: String,
  mainSessionKey: String,
): Boolean {
  val mainKey = mainSessionKey.trim().ifEmpty { "main" }
  val current = sessionKey.trim().let { if (it == "main" && mainKey != "main") mainKey else it }
  return choiceKey == current
}

internal data class ChatContextUsage(
  val totalTokens: Long?,
  val totalTokensFresh: Boolean?,
  val contextTokens: Long?,
  val inputTokens: Long? = null,
  val outputTokens: Long? = null,
  val estimatedCostUsd: Double? = null,
)

internal fun resolveChatContextUsage(
  sessionKey: String,
  mainSessionKey: String,
  sessions: List<ChatSessionEntry>,
): ChatContextUsage {
  val entry =
    sessions.firstOrNull {
      isActiveSessionChoice(
        choiceKey = it.key,
        sessionKey = sessionKey,
        mainSessionKey = mainSessionKey,
      )
    }
  return ChatContextUsage(
    totalTokens = entry?.totalTokens,
    totalTokensFresh = entry?.totalTokensFresh,
    contextTokens = entry?.contextTokens,
    // sessions.list owns run-cumulative usage across model calls, tools, and retries.
    // Transcript message usage remains a separate latest-model-call detail below.
    inputTokens = entry?.inputTokens,
    outputTokens = entry?.outputTokens,
    estimatedCostUsd = entry?.estimatedCostUsd,
  )
}

/**
 * Miuix's extended set has no plain up arrow: "Share" is an arrow out of a tray,
 * "Forward" is diagonal, and the rest are chevrons. This follows the same 24dp
 * grid with a heavier round stroke, matching the HyperOS send glyph's chunky
 * shaft and wide head rather than Material's thin filled arrow.
 */
internal val ComposerSendArrow: ImageVector by lazy {
  ImageVector
    .Builder(
      name = "ComposerSendArrow",
      defaultWidth = 24.dp,
      defaultHeight = 24.dp,
      viewportWidth = 24f,
      viewportHeight = 24f,
    ).apply {
      addPath(
        pathData = PathParser().parsePathString("M12 18.5V6M4.8 12.8 12 5.6l7.2 7.2").toNodes(),
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 3f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
      )
    }.build()
}

@Composable
internal fun SendButton(
  enabled: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    onClick = onClick,
    enabled = enabled,
    modifier = Modifier.size(ClawTheme.spacing.touchTarget),
    shape = CircleShape,
    color = Color.Transparent,
    contentColor = if (enabled) ClawTheme.colors.primaryText else ClawTheme.colors.textSubtle,
  ) {
    Box(modifier = Modifier.padding(8.dp).background(if (enabled) ClawTheme.colors.primary else ClawTheme.colors.surfacePressed, CircleShape), contentAlignment = Alignment.Center) {
      Icon(imageVector = ComposerSendArrow, contentDescription = nativeString("Send"), modifier = Modifier.size(22.dp))
    }
  }
}

@Composable
internal fun PreparingSendButton() {
  Surface(
    modifier = Modifier.size(ClawTheme.spacing.touchTarget),
    shape = CircleShape,
    color = Color.Transparent,
  ) {
    Box(
      modifier =
        Modifier
          .padding(8.dp)
          .background(ClawTheme.colors.surfacePressed, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(18.dp),
        strokeWidth = 2.dp,
        color = ClawTheme.colors.primary,
      )
    }
  }
}

internal fun userFacingChatError(
  error: String,
  gatewayConnected: Boolean,
): String {
  val lower = error.lowercase(Locale.US)
  return when {
    lower.contains("not connected") && gatewayConnected -> nativeString("Chat is still checking Gateway health.")
    lower.contains("not connected") -> nativeString("Gateway is offline. Fix the connection below or copy diagnostics.")
    lower.contains("unauthorized") || lower.contains("auth") -> nativeString("Gateway authentication needs attention.")
    else -> error
  }
}

internal fun contextMeterWidth(usage: ChatContextUsage): Float? {
  val total = usage.totalTokens?.takeIf { it >= 0L } ?: return null
  val context = usage.contextTokens?.takeIf { it > 0L } ?: return null
  return (total.toDouble() / context.toDouble()).coerceIn(0.0, 1.0).toFloat()
}

internal fun chatThinkingSupported(
  selection: ChatThinkingLevelSelection,
  fallbackSupported: Boolean,
): Boolean =
  if (selection.isGatewayProvided) {
    selection.options.any { it.id.trim().lowercase(Locale.US) != "off" }
  } else {
    fallbackSupported
  }

internal fun chatFastModeControlEnabled(
  supported: Boolean,
  adminAuthorized: Boolean,
  connected: Boolean,
  gatewayAvailable: Boolean,
  loading: Boolean,
  sending: Boolean,
  activeRun: Boolean,
  streaming: Boolean,
  settingsMutationPending: Boolean,
): Boolean =
  supported &&
    adminAuthorized &&
    connected &&
    gatewayAvailable &&
    !loading &&
    !sending &&
    !activeRun &&
    !streaming &&
    !settingsMutationPending

internal fun chatThinkingOptionLabel(
  option: ChatThinkingLevelOption,
  languageTag: String? = null,
): String {
  val id = option.id.trim()
  val rawLabel = option.label.trim().ifEmpty { id }
  val localizedLabel =
    if (rawLabel.equals(id, ignoreCase = true)) {
      when (id.lowercase(Locale.US)) {
        "off" -> nativeString("Off")
        "minimal" -> nativeString("Minimal")
        "low" -> nativeString("Low")
        "medium" -> nativeString("Medium")
        "high" -> nativeString("High")
        "xhigh" -> nativeString("Xhigh")
        "adaptive" -> nativeString("Adaptive")
        "max" -> nativeString("Max")
        else -> rawLabel
      }
    } else {
      rawLabel
    }
  return localizedUppercase(localizedLabel.take(1), languageTag) + localizedLabel.drop(1)
}

private fun formatChatTimestamp(timestampMs: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(timestampMs))

private fun ChatMessageContent.isImageAttachment(): Boolean =
  type.equals("image", ignoreCase = true) ||
    mimeType?.startsWith("image/", ignoreCase = true) == true
