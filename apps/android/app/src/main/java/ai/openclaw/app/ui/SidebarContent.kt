package ai.openclaw.app.ui

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.GatewayConnectionDisplay
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.R
import ai.openclaw.app.SessionCatalog
import ai.openclaw.app.SessionCatalogEntry
import ai.openclaw.app.SessionCatalogState
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.defaultSidebarPageOrder
import ai.openclaw.app.defaultSidebarVisiblePages
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.operatorScopesAllowWrite
import ai.openclaw.app.ui.design.ClawColors
import ai.openclaw.app.ui.design.ClawGlassMenuDefaults
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.OpenClawMascot
import ai.openclaw.app.ui.design.ProviderBrandIcon
import ai.openclaw.app.ui.design.clawSelectionTravel
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder as MiuixAddFolder
import top.yukonga.miuix.kmp.icon.extended.Sort as MiuixSort
import top.yukonga.miuix.kmp.icon.extended.Add as MiuixAdd
import top.yukonga.miuix.kmp.icon.extended.All as MiuixAll
import top.yukonga.miuix.kmp.icon.extended.ChevronForward as MiuixChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close as MiuixClose
import top.yukonga.miuix.kmp.icon.extended.Delete as MiuixDelete
import top.yukonga.miuix.kmp.icon.extended.Edit as MiuixEdit
import top.yukonga.miuix.kmp.icon.extended.Email as MiuixEmail
import top.yukonga.miuix.kmp.icon.extended.Folder as MiuixFolder
import top.yukonga.miuix.kmp.icon.extended.Home as MiuixHome
import top.yukonga.miuix.kmp.icon.extended.Messages as MiuixMessages
import top.yukonga.miuix.kmp.icon.extended.Ok as MiuixOk
import top.yukonga.miuix.kmp.icon.extended.Pin as MiuixPin
import top.yukonga.miuix.kmp.icon.extended.Refresh as MiuixRefresh
import top.yukonga.miuix.kmp.icon.extended.Rename as MiuixRename
import top.yukonga.miuix.kmp.icon.extended.Report as MiuixReport
import top.yukonga.miuix.kmp.icon.extended.Search as MiuixSearch
import top.yukonga.miuix.kmp.icon.extended.Settings as MiuixSettings
import top.yukonga.miuix.kmp.icon.extended.Tasks as MiuixTasks
import top.yukonga.miuix.kmp.icon.extended.Unpin as MiuixUnpin
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Collections

private const val SIDEBAR_CATALOG_REFRESH_MS = 30_000L

// The selection card overshoots by this much on its way to a new row, then settles back.
private const val SIDEBAR_SELECTION_PULSE_SCALE = 1.08f

internal enum class SidebarDestination(
  val stableId: String,
  val icon: ImageVector,
) {
  // HyperOS icon set (Medium weight) replaces the previous mixed
  // Lucide/Material strokes so the drawer matches the system look.
  Settings(stableId = "settings", icon = MiuixIcons.Medium.MiuixSettings),
  Work(stableId = "work", icon = MiuixIcons.Medium.MiuixTasks),
  Home(stableId = "home", icon = MiuixIcons.Medium.MiuixHome),
  Skills(stableId = "skills", icon = MiuixIcons.Medium.MiuixAll),
  Threads(stableId = "threads", icon = MiuixIcons.Medium.MiuixMessages),
}

internal fun SidebarDestination.localizedLabel(): String =
  when (this) {
    SidebarDestination.Settings -> nativeString("Settings")
    SidebarDestination.Work -> nativeString("Work")
    SidebarDestination.Home -> nativeString("Home")
    SidebarDestination.Skills -> nativeString("Skills")
    SidebarDestination.Threads -> nativeString("Threads")
  }

private enum class SidebarPagesMenuMode {
  Closed,
  Navigate,
  Edit,
}

internal fun orderedSidebarDestinations(pageIds: List<String>): List<SidebarDestination> {
  val byId = SidebarDestination.entries.associateBy(SidebarDestination::stableId)
  val supplied = pageIds.mapNotNull(byId::get).distinct()
  return supplied + SidebarDestination.entries.filterNot(supplied::contains)
}

internal fun moveSidebarDestination(
  pageIds: List<String>,
  destinationId: String,
  direction: Int,
  visiblePageIds: Set<String>? = null,
): List<String> {
  val ordered = orderedSidebarDestinations(pageIds).map(SidebarDestination::stableId).toMutableList()
  if (direction == 0) return ordered
  val visibleOrder = ordered.filter { visiblePageIds == null || it in visiblePageIds }
  val fromIndex = visibleOrder.indexOf(destinationId)
  if (fromIndex < 0) return ordered
  val targetId = visibleOrder.getOrNull(fromIndex + direction.sign) ?: return ordered
  // Swap visible slots so hidden pages keep their positions and never consume a drag step.
  Collections.swap(ordered, ordered.indexOf(destinationId), ordered.indexOf(targetId))
  return ordered
}

internal fun updateSidebarDestinationVisibility(
  visibleIds: List<String>,
  destination: SidebarDestination,
  visible: Boolean,
): List<String> {
  val current = visibleIds.toSet()
  if (!visible && destination.stableId in current && current.size == 1) return visibleIds
  val updated =
    if (visible) {
      current + destination.stableId
    } else {
      current - destination.stableId
    }
  return SidebarDestination.entries.map(SidebarDestination::stableId).filter(updated::contains)
}

private val Int.sign: Int
  get() =
    when {
      this < 0 -> -1
      this > 0 -> 1
      else -> 0
    }

private const val SIDEBAR_SESSION_LIMIT = 12

internal data class SidebarSessionPresentation(
  val recentSections: List<SessionSection>,
  val canExpandRecent: Boolean,
)

internal fun sidebarRecentSessions(
  sessions: List<ChatSessionEntry>,
): List<ChatSessionEntry> =
  sessions
    .asSequence()
    .filter { it.archived != true }
    .sortedWith(
      compareByDescending<ChatSessionEntry> { it.pinned == true }
        // createdAt keeps a just-created session visible before any activity lands.
        .thenByDescending { it.lastActivityAt ?: it.updatedAtMs ?: it.createdAtMs ?: 0L }
        .thenBy { it.key },
    ).toList()

internal fun sidebarSessionPresentation(
  sessions: List<ChatSessionEntry>,
  knownGroups: List<String>,
  expanded: Boolean,
  excludedSessionKeys: Set<String> = emptySet(),
): SidebarSessionPresentation {
  val activeSessions = sidebarRecentSessions(sessions)
  // Pins are no longer their own section; they stay in the single recent list and
  // sort to the top through sidebarRecentSessions, marked by the row's pin icon.
  val recent =
    activeSessions.filter { session ->
      session.key !in excludedSessionKeys
    }
  val visibleRecent = if (expanded) recent else recent.take(SIDEBAR_SESSION_LIMIT)
  return SidebarSessionPresentation(
    recentSections = groupSessionEntries(visibleRecent, knownGroups, separatePinned = false).filter { it.entries.isNotEmpty() },
    canExpandRecent = recent.size > SIDEBAR_SESSION_LIMIT,
  )
}

internal fun sessionPresentationTitle(
  session: ChatSessionEntry,
  unnamedTitle: () -> String,
): String {
  val label = session.label?.trim()?.takeIf(String::isNotEmpty)
  val displayName = session.displayName?.trim()?.takeIf(String::isNotEmpty)
  val autoLabel = session.autoLabel?.trim()?.takeIf(String::isNotEmpty)
  val localFallbackTitle = session.localFallbackTitle?.trim()?.takeIf(String::isNotEmpty)
  if (label != null) {
    return label
  }
  if (displayName != null) {
    return displayName
  }
  if (autoLabel != null) {
    return autoLabel
  }
  if (localFallbackTitle != null) {
    return localFallbackTitle
  }
  return nativeString("New chat").takeIf { session.isDashboardSession() } ?: unnamedTitle()
}

private fun ChatSessionEntry.isDashboardSession(): Boolean {
  if (classification == "dashboard") return true
  val parts = key.split(':', limit = 4)
  return parts.size == 4 && parts[0] == "agent" && parts[2] == "dashboard"
}

internal fun sidebarSessionTitle(session: ChatSessionEntry): String = sessionPresentationTitle(session) { session.key }

internal data class SidebarCatalogWorkspace(
  val stableId: String,
  val label: String,
  val path: String?,
  val sessions: List<SessionCatalogEntry>,
)

internal data class SidebarCatalogHost(
  val stableId: String,
  val catalogId: String,
  val label: String,
  val connected: Boolean,
  val errorText: String?,
  val workspaces: List<SidebarCatalogWorkspace>,
  val canLoadMore: Boolean,
)

internal data class SidebarCatalogSection(
  val catalog: SessionCatalog,
  val expanded: Boolean,
)

internal fun sidebarCatalogSections(
  catalogs: List<SessionCatalog>,
  expandedCatalogIds: Collection<String>,
): List<SidebarCatalogSection> =
  catalogs
    .filter { catalog ->
      catalog.canCreateSession ||
        catalog.errorText != null ||
        catalog.hosts.any { host ->
          host.errorText != null || host.nextCursor != null || host.sessions.any { !it.archived }
        }
    }.map { catalog ->
      SidebarCatalogSection(
        catalog = catalog,
        expanded = catalog.id in expandedCatalogIds,
      )
    }

internal fun sidebarCatalogSessionCreationEnabled(
  catalog: SessionCatalog,
  canMutateSessions: Boolean,
): Boolean = catalog.canCreateSession && canMutateSessions

internal fun toggleSidebarCatalogExpansion(
  expandedCatalogIds: List<String>,
  catalogId: String,
): List<String> = if (catalogId in expandedCatalogIds) expandedCatalogIds - catalogId else expandedCatalogIds + catalogId

internal fun sidebarCatalogRefreshNeeded(
  catalogAgentId: String?,
  selectedAgentId: String?,
  anyCatalogExpanded: Boolean,
  catalogDiscoveryNeeded: Boolean,
): Boolean {
  val normalizedSelectedAgentId = selectedAgentId?.trim()?.takeIf(String::isNotEmpty)
  return catalogAgentId != normalizedSelectedAgentId ||
    anyCatalogExpanded ||
    catalogDiscoveryNeeded
}

internal fun sidebarCatalogHosts(catalogs: List<SessionCatalog>): List<SidebarCatalogHost> =
  catalogs.flatMap { catalog ->
    catalog.hosts.mapNotNull { host ->
      val workspaces =
        host.sessions
          .asSequence()
          .filterNot(SessionCatalogEntry::archived)
          .groupBy { it.cwd?.trim()?.takeIf(String::isNotEmpty) }
          .map { (cwd, sessions) ->
            val normalizedPath = cwd?.replace('\\', '/')?.trimEnd('/')
            val label =
              normalizedPath
                ?.substringAfterLast('/')
                ?.takeIf(String::isNotEmpty)
                ?: nativeString("Other work")
            SidebarCatalogWorkspace(
              stableId = listOf(catalog.id, host.hostId, cwd.orEmpty()).joinToString("::"),
              label = label,
              path = cwd,
              sessions =
                sessions.sortedWith(
                  compareByDescending<SessionCatalogEntry> { it.recencyAt ?: Double.NEGATIVE_INFINITY }
                    .thenBy { it.name ?: it.threadId },
                ),
            )
          }.sortedWith(
            compareBy<SidebarCatalogWorkspace> { it.path == null }
              .thenBy(String.CASE_INSENSITIVE_ORDER, SidebarCatalogWorkspace::label),
          )
      // Android has no archived catalog view; hide a fully filtered host only after pagination is exhausted.
      if (
        workspaces.isEmpty() &&
        host.sessions.isNotEmpty() &&
        host.nextCursor == null &&
        host.errorText.isNullOrBlank()
      ) {
        return@mapNotNull null
      }
      SidebarCatalogHost(
        stableId = listOf(catalog.id, host.hostId).joinToString("::"),
        catalogId = catalog.id,
        label = host.label,
        connected = host.connected,
        errorText = host.errorText,
        workspaces = workspaces,
        canLoadMore = host.nextCursor != null,
      )
    }
  }

internal fun sidebarVisibleCatalogSessionKeys(catalogs: List<SessionCatalog>): Set<String> =
  sidebarCatalogHosts(catalogs)
    .asSequence()
    .flatMap { it.workspaces.asSequence() }
    .flatMap { it.sessions.asSequence() }
    .mapNotNull(SessionCatalogEntry::sessionKey)
    .toSet()

internal data class SidebarPalette(
  val background: Color,
  val elevated: Color,
  val selection: Color,
  val text: Color,
  val muted: Color,
  val hairline: Color,
)

internal class SidebarRowHost {
  private data class Placement(
    val band: IntRect?,
    val viewport: IntRect,
  )

  private var placement: Placement? = null
  var generation by mutableLongStateOf(0L)
    private set

  fun recordPlacement(
    band: IntRect?,
    viewport: IntRect,
  ) {
    val next = Placement(band, viewport)
    if (next != placement) {
      placement = next
      generation++
    }
  }
}

internal fun sidebarPalette(colors: ClawColors): SidebarPalette =
  SidebarPalette(
    background = colors.canvas,
    elevated = colors.surfaceRaised,
    selection = colors.accentSoft,
    text = colors.text,
    muted = colors.textMuted,
    hairline = colors.border,
  )

@Composable
private fun sidebarPalette(): SidebarPalette = sidebarPalette(ClawTheme.colors)

@Composable
internal fun OpenClawSidebar(
  viewModel: MainViewModel,
  agents: List<GatewayAgentSummary>,
  selectedAgentId: String?,
  sessions: List<ChatSessionEntry>,
  activeSessionKey: String,
  activeDestination: SidebarDestination?,
  connection: GatewayConnectionDisplay,
  visible: Boolean,
  showCloseButton: Boolean,
  onClose: () -> Unit,
  onDragActiveChange: (Boolean) -> Unit,
  onNewSession: () -> Unit,
  onSelectAgent: (String) -> Unit,
  onSelectSession: (ChatSessionEntry) -> Unit,
  onSelectCatalogSession: (SessionCatalogEntry) -> Unit,
  onCreateCatalogSession: (String) -> Unit,
  onSelectDestination: (SidebarDestination) -> Unit,
  rowHostBand: IntRect? = null,
) {
  val palette = sidebarPalette()
  val scope = rememberCoroutineScope()
  val lifecycle = LocalLifecycleOwner.current.lifecycle
  val scrollState = rememberScrollState()
  val rowHost = remember { SidebarRowHost() }
  val agentPicker = agentPickerState(agents, selectedAgentId)
  val storedGroups by viewModel.sessionCustomGroups.collectAsState()
  val catalogState by viewModel.sessionCatalogState.collectAsState()
  val sessionCreating by viewModel.chatSessionCreating.collectAsState()
  val catalogAvailable by viewModel.sessionCatalogAvailable.collectAsState()
  val operatorScopes by viewModel.operatorScopes.collectAsState()
  val canMutateSessions = operatorScopesAllowWrite(operatorScopes)
  val liveSessionsByKey = remember(sessions) { sessions.associateBy(ChatSessionEntry::key) }
  val pageOrder by viewModel.sidebarPageOrder.collectAsState()
  val visiblePageIds by viewModel.sidebarVisiblePages.collectAsState()
  var query by rememberSaveable { mutableStateOf("") }
  var searchVisible by rememberSaveable { mutableStateOf(false) }
  val searchFocus = remember { FocusRequester() }
  var searchFocused by remember { mutableStateOf(false) }
  val restoreSearchFocus = searchVisible && searchFocused && !showCloseButton
  LaunchedEffect(showCloseButton) {
    // Only the previously focused field may reclaim focus after moving out of the modal host.
    if (restoreSearchFocus) searchFocus.requestFocus()
  }
  var pagesExpanded by rememberSaveable { mutableStateOf(true) }
  var pagesMenuMode by rememberSaveable { mutableStateOf(SidebarPagesMenuMode.Closed) }
  var sessionsExpanded by rememberSaveable { mutableStateOf(false) }
  var expandedCatalogIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
  // Session actions live in a long-press menu now, so the only remembered row
  // state is which session is waiting on a rename or delete confirmation.
  var renameSessionTarget by remember { mutableStateOf<ChatSessionEntry?>(null) }
  var deleteSessionTarget by remember { mutableStateOf<ChatSessionEntry?>(null) }
  var newGroupSessionTarget by remember { mutableStateOf<ChatSessionEntry?>(null) }
  var renameGroupTarget by remember { mutableStateOf<String?>(null) }
  var deleteGroupTarget by remember { mutableStateOf<String?>(null) }
  var collapsedGroupNames by rememberSaveable { mutableStateOf(emptyList<String>()) }
  var collapsedCatalogHostIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
  var collapsedCatalogWorkspaceIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
  val catalogSections = sidebarCatalogSections(catalogState.catalogs, expandedCatalogIds)
  val anyCatalogExpanded = catalogSections.any(SidebarCatalogSection::expanded)
  val catalogDiscoveryNeeded = catalogSections.isEmpty()
  val catalogRefreshNeeded =
    sidebarCatalogRefreshNeeded(
      catalogAgentId = catalogState.agentId,
      selectedAgentId = selectedAgentId,
      anyCatalogExpanded = anyCatalogExpanded,
      catalogDiscoveryNeeded = catalogDiscoveryNeeded,
    )
  val catalogErrorText = catalogState.errorText
  val catalogSessionKeys = sidebarVisibleCatalogSessionKeys(catalogState.catalogs)
  val recentPresentation =
    sidebarSessionPresentation(
      sessions = sessions,
      knownGroups = storedGroups,
      expanded = sessionsExpanded,
      excludedSessionKeys = catalogSessionKeys,
    )
  val recentSections = recentPresentation.recentSections
  val orderedPages = orderedSidebarDestinations(pageOrder)
  val visiblePageIdSet = visiblePageIds.toSet()
  val connectionLabel = gatewayStatusLabel(connection)
  val setSessionPinned: (String, String?, Boolean) -> Unit = { key, ownerAgentId, pinned ->
    scope.launch {
      viewModel.patchChatSession(key = key, ownerAgentId = ownerAgentId, pinned = pinned)
    }
  }
  val setSessionUnread: (String, String?, Boolean) -> Unit = { key, ownerAgentId, unread ->
    scope.launch {
      viewModel.patchChatSession(key = key, ownerAgentId = ownerAgentId, unread = unread)
    }
  }
  // null clears the category, which returns the thread to Ungrouped.
  val setSessionGroup: (ChatSessionEntry, String?) -> Unit = { session, group ->
    scope.launch {
      viewModel.patchChatSession(
        key = session.key,
        ownerAgentId = session.ownerAgentId,
        category = group,
        clearCategory = group == null,
      )
    }
  }
  // Empty groups stay listed only while the local record remembers them, so the
  // picker shows stored names plus every category currently in use. "Ungrouped"
  // is not a stored group — it is always offered as the last destination.
  val knownGroupNames =
    remember(storedGroups, sessions) {
      val ungrouped = nativeString("Ungrouped")
      (storedGroups + sessions.mapNotNull { it.category?.trim()?.takeIf(String::isNotEmpty) })
        .filterNot { it.equals(ungrouped, ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .sortedBy { it.lowercase() }
    }
  LaunchedEffect(
    connection.isConnected,
    selectedAgentId,
    anyCatalogExpanded,
    catalogRefreshNeeded,
    catalogDiscoveryNeeded,
    visible,
    lifecycle,
    catalogAvailable,
  ) {
    if (
      !connection.isConnected ||
      !catalogAvailable ||
      !visible
    ) {
      return@LaunchedEffect
    }
    if (!catalogRefreshNeeded) return@LaunchedEffect
    lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
      viewModel.refreshSessionCatalog(selectedAgentId)
      while (true) {
        delay(SIDEBAR_CATALOG_REFRESH_MS)
        viewModel.refreshSessionCatalog(selectedAgentId)
      }
    }
  }
  // Canonical debounced gateway search shared with the Sessions browser; the
  // controller falls back to filtering cached rows when the gateway is offline.
  val searchState =
    rememberSessionBrowserSearchState(
      viewModel = viewModel,
      sessions = sessions,
      query = query,
      archived = false,
    )
  val searchResults =
    resolveSessionBrowserEntries(
      entries = searchState.entries,
      currentSessionKey = activeSessionKey,
      filter = SessionFilter.Recent,
      recentFirst = true,
    )

  Column(
    modifier =
      Modifier
        .fillMaxSize()
        .background(palette.background)
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(horizontal = 14.dp, vertical = 10.dp),
  ) {
    // The compact search field is opt-in from the header, matching the web sidebar.
    Row(
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      if (agentPicker.selected != null) {
        AgentPicker(
          state = agentPicker,
          onSelectAgent = onSelectAgent,
          modifier = Modifier.weight(1f),
        )
      } else {
        OpenClawMascot(modifier = Modifier.size(28.dp))
        Text(
          text = "OpenClaw",
          modifier = Modifier.weight(1f),
          style = ClawTheme.type.title.copy(fontSize = 18.sp, lineHeight = 22.sp),
          color = palette.text,
          maxLines = 1,
        )
      }
      IconButton(
        onClick = {
          if (searchVisible) query = ""
          searchVisible = !searchVisible
        },
        modifier = Modifier.size(48.dp).testTag("sidebar-search-toggle"),
      ) {
        Icon(
          imageVector = MiuixIcons.Medium.MiuixSearch,
          contentDescription = nativeString(if (searchVisible) "Hide search" else "Search sessions"),
          tint = palette.text,
          modifier = Modifier.size(20.dp),
        )
      }
      IconButton(
        onClick = onNewSession,
        enabled = !sessionCreating,
        modifier =
          Modifier.size(48.dp).semantics {
            contentDescription = nativeString("New session")
            if (sessionCreating) stateDescription = nativeString("Loading")
          },
      ) {
        if (sessionCreating) {
          CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = palette.text)
        } else {
          Icon(
            imageVector = MiuixIcons.Medium.MiuixAdd,
            contentDescription = null,
            tint = palette.text,
            modifier = Modifier.size(22.dp),
          )
        }
      }
      if (showCloseButton) {
        IconButton(onClick = onClose, modifier = Modifier.size(48.dp).testTag("sidebar-close")) {
          Icon(
            imageVector = MiuixIcons.Medium.MiuixClose,
            contentDescription = nativeString("Hide Sidebar"),
            tint = palette.text,
            modifier = Modifier.size(20.dp),
          )
        }
      }
    }
    if (searchVisible) {
      SidebarSearchField(
        query = query,
        onQueryChange = { query = it },
        palette = palette,
        modifier =
          Modifier
            .padding(top = 4.dp, bottom = 10.dp)
            .focusRequester(searchFocus)
            .onFocusChanged { searchFocused = it.isFocused },
      )
    }

    // Cancel gesture-bearing rows when changing hosts; keep navigation, search and scroll above it.
    key(showCloseButton) {
      Column(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .onPlaced {
              // Observe the viewport, never row reorder/drag offsets or the scrolling content.
              rowHost.recordPlacement(rowHostBand, IntRect(it.positionInParent().round(), it.size))
            }.verticalScroll(scrollState),
      ) {
        if (searchState.query.isNotEmpty()) {
          SidebarSectionTitle(nativeString("Threads"), palette)
          when (sessionEmptyMode(searchState.query, searchState.loading)) {
            SessionEmptyMode.SearchLoading -> {
              Text(
                text = nativeString("Searching threads"),
                style = ClawTheme.type.caption,
                color = palette.muted,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
              )
            }

            else -> {
              if (searchResults.isEmpty()) {
                Text(
                  text = nativeString("No matching threads"),
                  style = ClawTheme.type.caption,
                  color = palette.muted,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
              } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  searchResults.forEach { session ->
                    SidebarSessionRow(
                      session = session,
                      selected = session.key == activeSessionKey,
                      palette = palette,
                      onClick = { onSelectSession(session) },
                      menuContent =
                        sidebarSessionMenu(
                          session = session,
                          canMutate = canMutateSessions,
                          groups = knownGroupNames,
                          onSetPinned = { pinned -> setSessionPinned(session.key, session.ownerAgentId, pinned) },
                          onSetUnread = { unread -> setSessionUnread(session.key, session.ownerAgentId, unread) },
                          onAssignGroup = { group -> setSessionGroup(session, group) },
                          onNewGroup = { newGroupSessionTarget = session },
                          onRename = { renameSessionTarget = session },
                          onDelete = { deleteSessionTarget = session },
                        ),
                    )
                  }
                }
              }
            }
          }
        } else {
          SidebarPagesHeader(
            rowHost = rowHost,
            expanded = pagesExpanded,
            menuMode = pagesMenuMode,
            destinations = orderedPages,
            visiblePageIds = visiblePageIdSet,
            activeDestination = activeDestination,
            palette = palette,
            onToggleExpanded = { pagesExpanded = !pagesExpanded },
            onMenuModeChange = { pagesMenuMode = it },
            onSelectDestination = onSelectDestination,
            onVisibilityChange = { destination, visible ->
              viewModel.setSidebarVisiblePages(
                updateSidebarDestinationVisibility(
                  visibleIds = visiblePageIds,
                  destination = destination,
                  visible = visible,
                ),
              )
            },
            onMove = { destination, direction ->
              viewModel.setSidebarPageOrder(
                moveSidebarDestination(
                  pageIds = pageOrder,
                  destinationId = destination.stableId,
                  direction = direction,
                ),
              )
            },
            onReset = {
              viewModel.setSidebarPageOrder(defaultSidebarPageOrder)
              viewModel.setSidebarVisiblePages(defaultSidebarVisiblePages)
            },
            onDragActiveChange = onDragActiveChange,
          )
          if (pagesExpanded) {
            val pageRows = orderedPages.filter { it.stableId in visiblePageIdSet }
            var pageRowHeightPx by remember { mutableIntStateOf(0) }
            val selectedRowIndex = activeDestination?.let { pageRows.indexOf(it) } ?: -1
            // One card travels between slots instead of every row repainting its own highlight, so
            // a tap reads as the sidebar picking a page while that page fades in beside it.
            val selectionTop by
              animateFloatAsState(
                targetValue = if (selectedRowIndex >= 0) selectedRowIndex * pageRowHeightPx.toFloat() else 0f,
                // Snap for the first frame: the row height only becomes known during layout, and
                // springing from an unmeasured slot would slide the card on drawer open.
                animationSpec = if (pageRowHeightPx > 0) clawSelectionTravel() else snap(),
                label = "sidebar selection row",
              )
            val selectionAlpha by
              animateFloatAsState(
                targetValue = if (selectedRowIndex >= 0) 1f else 0f,
                animationSpec = tween(140),
                label = "sidebar selection alpha",
              )
            // Grow, travel, settle: the card swells as it leaves and deflates into the new slot.
            // Only between two real rows, so opening the drawer on the current page never bounces.
            val selectionScale = remember { Animatable(1f) }
            var lastSelectedRowIndex by remember { mutableIntStateOf(-1) }
            LaunchedEffect(selectedRowIndex) {
              if (lastSelectedRowIndex >= 0 && selectedRowIndex >= 0 && lastSelectedRowIndex != selectedRowIndex) {
                selectionScale.animateTo(SIDEBAR_SELECTION_PULSE_SCALE, tween(110, easing = FastOutSlowInEasing))
                selectionScale.animateTo(1f, tween(200, easing = LinearOutSlowInEasing))
              }
              lastSelectedRowIndex = selectedRowIndex
            }
            Box(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .drawBehind {
                    if (pageRowHeightPx > 0 && selectionAlpha > 0f) {
                      scale(
                        scaleX = selectionScale.value,
                        scaleY = selectionScale.value,
                        pivot = Offset(size.width / 2f, selectionTop + pageRowHeightPx / 2f),
                      ) {
                        drawRoundRect(
                          color = palette.selection.copy(alpha = palette.selection.alpha * selectionAlpha),
                          topLeft = Offset(0f, selectionTop),
                          size = Size(size.width, pageRowHeightPx.toFloat()),
                          cornerRadius = CornerRadius(ClawGlassMenuDefaults.CornerRadius.toPx()),
                        )
                      }
                    }
                  },
            ) {
              Column {
                pageRows.forEach { destination ->
                  key(destination.stableId) {
                    SidebarNavigationRow(
                      destination = destination,
                      rowHost = rowHost,
                      palette = palette,
                      modifier = Modifier.onSizeChanged { pageRowHeightPx = it.height },
                      onClick = { onSelectDestination(destination) },
                      onMove = { direction ->
                        viewModel.setSidebarPageOrder(
                          moveSidebarDestination(
                            pageIds = pageOrder,
                            destinationId = destination.stableId,
                            direction = direction,
                            visiblePageIds = visiblePageIdSet,
                          ),
                        )
                      },
                      onDragActiveChange = onDragActiveChange,
                    )
                  }
                }
              }
            }
          }
          if (catalogAvailable) {
            when {
              // A catalog still loading with nothing discovered yet simply stays
              // out of the way: showing a "Loading" row here is what made the
              // list jump while the drawer was still sliding open.
              catalogErrorText != null && catalogSections.isEmpty() -> {
                SidebarCatalogStatus(catalogErrorText, palette)
              }

              // Nothing discovered on this gateway: drop the block entirely rather
              // than parking an empty "No sessions" row above the recent threads.
              catalogSections.isEmpty() -> Unit

              else -> {
                catalogSections.forEach { section ->
                  val catalog = section.catalog
                  key("catalog:${catalog.id}") {
                    SidebarCollapsibleHeader(
                      label = catalog.label,
                      expanded = section.expanded,
                      palette = palette,
                      iconContent = {
                        ProviderBrandIcon(provider = catalog.id, size = 18.dp)
                      },
                      trailingContent =
                        if (
                          sidebarCatalogSessionCreationEnabled(catalog, canMutateSessions) &&
                          catalogState.continuingEntryId == null
                        ) {
                          {
                            IconButton(
                              onClick = { onCreateCatalogSession(catalog.id) },
                              enabled = !sessionCreating,
                              modifier = Modifier.size(40.dp),
                            ) {
                              Icon(
                                imageVector = MiuixIcons.Medium.MiuixAdd,
                                contentDescription = nativeString("New session"),
                                tint = palette.text,
                                modifier = Modifier.size(18.dp),
                              )
                            }
                          }
                        } else {
                          null
                        },
                      onClick = {
                        expandedCatalogIds = toggleSidebarCatalogExpansion(expandedCatalogIds, catalog.id)
                      },
                    )
                    if (section.expanded) {
                      SidebarSessionCatalog(
                        state = catalogState,
                        catalog = catalog,
                        activeSessionKey = activeSessionKey,
                        liveSessionsByKey = liveSessionsByKey,
                        collapsedHostIds = collapsedCatalogHostIds.toSet(),
                        collapsedWorkspaceIds = collapsedCatalogWorkspaceIds.toSet(),
                        palette = palette,
                        onToggleHost = { stableId ->
                          collapsedCatalogHostIds =
                            if (stableId in collapsedCatalogHostIds) {
                              collapsedCatalogHostIds - stableId
                            } else {
                              collapsedCatalogHostIds + stableId
                            }
                        },
                        onToggleWorkspace = { stableId ->
                          collapsedCatalogWorkspaceIds =
                            if (stableId in collapsedCatalogWorkspaceIds) {
                              collapsedCatalogWorkspaceIds - stableId
                            } else {
                              collapsedCatalogWorkspaceIds + stableId
                            }
                        },
                        onSelectSession = onSelectCatalogSession,
                        onLoadMore = viewModel::loadMoreSessionCatalog,
                        canMutateSessions = canMutateSessions,
                        groups = knownGroupNames,
                        onSetPinned = setSessionPinned,
                        onSetUnread = setSessionUnread,
                        onAssignGroup = setSessionGroup,
                        onNewGroup = { session -> newGroupSessionTarget = session },
                        onRename = { session -> renameSessionTarget = session },
                        onDelete = { session -> deleteSessionTarget = session },
                      )
                    }
                  }
                }
                catalogErrorText?.let { SidebarCatalogStatus(it, palette) }
              }
            }
          }

          // Recent sessions are always listed: a collapsible section hid the only
          // route to a conversation behind one extra tap.
          SidebarSectionTitle(
            nativeString("Recent"),
            palette,
            Modifier.padding(top = 10.dp),
            loading = catalogState.loading,
          )
          if (recentSections.isEmpty()) {
            Text(
              text = nativeString("No recent sessions"),
              style = ClawTheme.type.caption,
              color = palette.muted,
              modifier = Modifier.padding(horizontal = 40.dp, vertical = 10.dp),
            )
          } else {
            recentSections.forEach { section ->
              val sectionTitle = section.title
              val sectionExpanded = sectionTitle == null || sectionTitle !in collapsedGroupNames
              sectionTitle?.let { title ->
                SidebarGroupHeader(
                  title = title,
                  canManage = canMutateSessions && section.isCategory,
                  expanded = sectionExpanded,
                  palette = palette,
                  onToggle = {
                    collapsedGroupNames =
                      if (title in collapsedGroupNames) {
                        collapsedGroupNames - title
                      } else {
                        collapsedGroupNames + title
                      }
                  },
                  onRename = { renameGroupTarget = title },
                  onDelete = { deleteGroupTarget = title },
                )
              }
              if (sectionExpanded) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                  section.entries.forEach { session ->
                    SidebarSessionRow(
                      session = session,
                      selected = session.key == activeSessionKey,
                      palette = palette,
                      onClick = { onSelectSession(session) },
                      menuContent =
                        sidebarSessionMenu(
                          session = session,
                          canMutate = canMutateSessions,
                          groups = knownGroupNames,
                          onSetPinned = { pinned -> setSessionPinned(session.key, session.ownerAgentId, pinned) },
                          onSetUnread = { unread -> setSessionUnread(session.key, session.ownerAgentId, unread) },
                          onAssignGroup = { group -> setSessionGroup(session, group) },
                          onNewGroup = { newGroupSessionTarget = session },
                          onRename = { renameSessionTarget = session },
                          onDelete = { deleteSessionTarget = session },
                        ),
                    )
                  }
                }
              }
            }
          }
          if (recentPresentation.canExpandRecent) {
            SidebarActionRow(
              label = nativeString(if (sessionsExpanded) "Show less" else "Show more"),
              icon = MiuixIcons.Medium.MiuixChevronForward,
              iconRotationZ = if (sessionsExpanded) -90f else 90f,
              palette = palette,
              onClick = { sessionsExpanded = !sessionsExpanded },
            )
          }
        }
      }
    }
    HorizontalDivider(color = palette.hairline)
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .semantics(mergeDescendants = true) {
            stateDescription = connectionLabel
          }.padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
      Box(
        modifier =
          Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(if (connection.isConnected) ClawTheme.colors.success else palette.muted)
            .clearAndSetSemantics {},
      )
      Text(
        text = connectionLabel,
        style = ClawTheme.type.caption,
        color = palette.muted,
        maxLines = 1,
      )
    }
    renameSessionTarget?.let { session ->
      SessionTextDialog(
        title = nativeString("Rename thread"),
        stateKey = "sidebar-rename:${session.key}",
        initialValue = session.label ?: session.displayName.orEmpty(),
        confirmLabel = nativeString("Rename"),
        allowEmpty = true,
        onDismiss = { renameSessionTarget = null },
        onConfirm = { value ->
          renameSessionTarget = null
          val label = value.trim()
          scope.launch {
            viewModel.patchChatSession(
              key = session.key,
              ownerAgentId = session.ownerAgentId,
              label = label.takeIf(String::isNotEmpty),
              clearLabel = label.isEmpty(),
            )
          }
        },
      )
    }
    deleteSessionTarget?.let { session ->
      AlertDialog(
        onDismissRequest = { deleteSessionTarget = null },
        containerColor = ClawTheme.colors.surfaceRaised,
        title = { Text(nativeString("Delete thread?"), style = ClawTheme.type.section, color = ClawTheme.colors.text) },
        text = {
          Text(
            nativeString("This removes the thread and its transcript."),
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              deleteSessionTarget = null
              scope.launch {
                viewModel.deleteChatSession(session.key, session.ownerAgentId, session.sessionId)
              }
            },
          ) {
            Text(nativeString("Delete"), color = ClawTheme.colors.danger)
          }
        },
        dismissButton = {
          TextButton(onClick = { deleteSessionTarget = null }) {
            Text(nativeString("Cancel"))
          }
        },
      )
    }
    newGroupSessionTarget?.let { session ->
      SessionTextDialog(
        title = "新建分组",
        stateKey = "sidebar-group-new:${session.key}",
        initialValue = "",
        confirmLabel = nativeString("Create"),
        allowEmpty = false,
        onDismiss = { newGroupSessionTarget = null },
        onConfirm = { value ->
          newGroupSessionTarget = null
          val group = value.trim()
          if (group.isNotEmpty()) {
            // Remember the name so the group survives locally even if the patch
            // later empties it, then move this thread into it.
            viewModel.addChatSessionGroup(group)
            scope.launch {
              viewModel.patchChatSession(
                key = session.key,
                ownerAgentId = session.ownerAgentId,
                category = group,
              )
            }
          }
        },
      )
    }
    renameGroupTarget?.let { group ->
      SessionTextDialog(
        title = nativeString("Rename group"),
        stateKey = "sidebar-group-rename:$group",
        initialValue = group,
        confirmLabel = nativeString("Rename"),
        allowEmpty = false,
        onDismiss = { renameGroupTarget = null },
        onConfirm = { value ->
          renameGroupTarget = null
          val next = value.trim()
          if (next.isNotEmpty() && next != group) {
            // Keep the collapsed state attached to the renamed group.
            collapsedGroupNames = collapsedGroupNames.map { if (it == group) next else it }
            scope.launch { viewModel.renameChatSessionGroup(from = group, to = next) }
          }
        },
      )
    }
    deleteGroupTarget?.let { group ->
      AlertDialog(
        onDismissRequest = { deleteGroupTarget = null },
        containerColor = ClawTheme.colors.surfaceRaised,
        title = { Text(nativeString("Delete group?"), style = ClawTheme.type.section, color = ClawTheme.colors.text) },
        text = {
          Text(
            nativeString("Threads in \"\$group\" are kept and move back to Ungrouped.", group),
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textMuted,
          )
        },
        confirmButton = {
          TextButton(
            onClick = {
              deleteGroupTarget = null
              collapsedGroupNames = collapsedGroupNames - group
              scope.launch { viewModel.deleteChatSessionGroup(group) }
            },
          ) {
            Text(nativeString("Delete"), color = ClawTheme.colors.danger)
          }
        },
        dismissButton = {
          TextButton(onClick = { deleteGroupTarget = null }) {
            Text(nativeString("Cancel"))
          }
        },
      )
    }
  }
}

/**
 * Long-press actions for one sidebar row. Returns null for read-only operators so
 * the row keeps its plain tap behavior instead of opening an empty menu.
 */
@Composable
private fun sidebarSessionMenu(
  session: ChatSessionEntry,
  canMutate: Boolean,
  groups: List<String>,
  onSetPinned: (Boolean) -> Unit,
  onSetUnread: (Boolean) -> Unit,
  onAssignGroup: (String?) -> Unit,
  onNewGroup: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
): (@Composable ColumnScope.() -> Unit)? {
  if (!canMutate) return null
  val pinned = session.pinned == true
  val unread = session.unread == true
  val pinLabel = nativeString(if (pinned) "Unpin" else "Pin")
  val unreadLabel = nativeString(if (unread) "Mark as read" else "Mark as unread")
  val renameLabel = nativeString("Rename")
  val deleteLabel = nativeString("Delete")
  val drill = LocalSidebarDrillHost.current
  val currentGroup = session.category?.trim()?.takeIf(String::isNotEmpty)
  // The drill body outlives this composition (it is stored on the menu state), so
  // it must read the live group list: a group created from inside the card has to
  // show up in that same card without closing it.
  val liveGroups by rememberUpdatedState(groups)
  // The long-pressed row already tells the user which session they are acting
  // on, so the card carries no title of its own.
  return {
    ClawGlassMenuItem(
      label = pinLabel,
      icon = if (pinned) MiuixIcons.Medium.MiuixUnpin else MiuixIcons.Medium.MiuixPin,
      selected = pinned,
      onClick = { onSetPinned(!pinned) },
    )
    ClawGlassMenuItem(
      label = unreadLabel,
      icon = MiuixIcons.Medium.MiuixEmail,
      selected = unread,
      onClick = { onSetUnread(!unread) },
    )
    if (drill != null) {
      // Drills into the group picker on the same surface; the primary card must
      // stay alive for the nested card to grow out of this row.
      ClawGlassMenuItem(
        label = "添加到分组",
        icon = MiuixIcons.Medium.MiuixSort,
        // Mirrors the nested header: current group on the right, ⇅ as the
        // "expands" affordance, so both cards read as one control.
        trailing = currentGroup ?: nativeString("Ungrouped"),
        trailingIcon = MiuixIcons.Medium.MiuixSort,
        keepOpen = true,
        // Publishes this row's position so the nested card can continue the same
        // line instead of guessing where it was tapped.
        modifier =
          Modifier.sidebarDrillRowBounds(
            host = { drill.morph.hostCoords },
            onBounds = { drill.rowBounds["session-group"] = it },
          ),
        onClick = {
          drill.drillInto(
            key = "session-group",
            header =
              SidebarDrillHeader(
                icon = MiuixIcons.Medium.MiuixSort,
                title = "添加到分组",
                value = currentGroup ?: nativeString("Ungrouped"),
              ),
          ) {
            val ungrouped = nativeString("Ungrouped")
            // Ungrouped leads: it is the default state, and every thread can
            // always be taken back out of its group.
            SidebarDrillOptionRow(
              label = ungrouped,
              selected = currentGroup == null,
              onClick = {
                drill.closeAll()
                onAssignGroup(null)
              },
            )
            liveGroups
              .filterNot { it.equals(ungrouped, ignoreCase = true) }
              .forEach { group ->
                SidebarDrillOptionRow(
                  label = group,
                  selected = group.equals(currentGroup, ignoreCase = true),
                  onClick = {
                    drill.closeAll()
                    onAssignGroup(group)
                  },
                )
              }
            SidebarDrillDivider()
            SidebarDrillActionRow(
              icon = MiuixIcons.Medium.MiuixAddFolder,
              label = "新建分组",
              clickable = true,
              // Stays open: the new group appears in this very list so it can be
              // picked right away, instead of collapsing back to the session row.
              onClick = onNewGroup,
            )
          }
        },
      )
    }
    ClawGlassMenuItem(label = renameLabel, icon = MiuixIcons.Medium.MiuixRename, onClick = onRename)
    ClawGlassMenuItem(label = deleteLabel, icon = MiuixIcons.Medium.MiuixDelete, danger = true, onClick = onDelete)
  }
}

@Composable
private fun SidebarPagesHeader(
  rowHost: SidebarRowHost,
  expanded: Boolean,
  menuMode: SidebarPagesMenuMode,
  destinations: List<SidebarDestination>,
  visiblePageIds: Set<String>,
  activeDestination: SidebarDestination?,
  palette: SidebarPalette,
  onToggleExpanded: () -> Unit,
  onMenuModeChange: (SidebarPagesMenuMode) -> Unit,
  onSelectDestination: (SidebarDestination) -> Unit,
  onVisibilityChange: (SidebarDestination, Boolean) -> Unit,
  onMove: (SidebarDestination, Int) -> Unit,
  onReset: () -> Unit,
  onDragActiveChange: (Boolean) -> Unit,
) {
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier =
        Modifier
          .weight(1f)
          .heightIn(min = 44.dp)
          .clip(RoundedCornerShape(ClawGlassMenuDefaults.CornerRadius))
          .clickable(role = Role.Button, onClick = onToggleExpanded)
          .padding(horizontal = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      SidebarChevron(expanded = expanded, palette = palette)
      Text(
        text = nativeString("Pages"),
        style = ClawTheme.type.caption.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
        color = palette.muted,
        maxLines = 1,
      )
    }

    Box {
      IconButton(
        onClick = { onMenuModeChange(SidebarPagesMenuMode.Navigate) },
        modifier = Modifier.size(44.dp).testTag("sidebar-pages-menu"),
      ) {
        Icon(
          imageVector = MiuixIcons.Medium.MiuixEdit,
          contentDescription = nativeString("Edit pinned items"),
          tint = palette.text,
          modifier = Modifier.size(18.dp),
        )
      }

      DropdownMenu(
        expanded = menuMode != SidebarPagesMenuMode.Closed,
        onDismissRequest = { onMenuModeChange(SidebarPagesMenuMode.Closed) },
        modifier = Modifier.widthIn(min = 210.dp, max = 340.dp),
        containerColor = palette.elevated,
      ) {
        when (menuMode) {
          SidebarPagesMenuMode.Closed -> {}

          SidebarPagesMenuMode.Navigate -> {
            destinations.forEach { destination ->
              DropdownMenuItem(
                text = { Text(destination.localizedLabel(), maxLines = 1) },
                leadingIcon = {
                  Icon(
                    imageVector = destination.icon,
                    contentDescription = null,
                    tint = palette.text,
                    modifier = Modifier.size(18.dp),
                  )
                },
                trailingIcon = {
                  if (destination == activeDestination) {
                    Icon(
                      imageVector = MiuixIcons.Medium.MiuixOk,
                      contentDescription = nativeString("Selected"),
                      tint = palette.text,
                      modifier = Modifier.size(18.dp),
                    )
                  }
                },
                onClick = {
                  onMenuModeChange(SidebarPagesMenuMode.Closed)
                  onSelectDestination(destination)
                },
              )
            }
            HorizontalDivider(color = palette.hairline)
            DropdownMenuItem(
              text = { Text(nativeString("Edit pinned items"), maxLines = 1) },
              leadingIcon = {
                Icon(
                  imageVector = MiuixIcons.Medium.MiuixEdit,
                  contentDescription = null,
                  tint = palette.text,
                  modifier = Modifier.size(18.dp),
                )
              },
              onClick = { onMenuModeChange(SidebarPagesMenuMode.Edit) },
            )
          }

          SidebarPagesMenuMode.Edit -> {
            Text(
              text = nativeString("EDIT PINNED ITEMS"),
              style =
                ClawTheme.type.caption.copy(
                  fontWeight = FontWeight.SemiBold,
                  letterSpacing = 0.8.sp,
                ),
              color = palette.muted,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
            destinations.forEach { destination ->
              key(destination.stableId) {
                val visible = destination.stableId in visiblePageIds
                SidebarNavigationRow(
                  destination = destination,
                  rowHost = rowHost,
                  pinned = visible,
                  palette = palette,
                  onClick = { onVisibilityChange(destination, !visible) },
                  onMove = { direction -> onMove(destination, direction) },
                  onDragActiveChange = onDragActiveChange,
                )
              }
            }
            HorizontalDivider(color = palette.hairline)
            DropdownMenuItem(
              text = { Text(nativeString("Reset pinned items"), maxLines = 1) },
              leadingIcon = {
                Icon(
                  imageVector = MiuixIcons.Medium.MiuixRefresh,
                  contentDescription = null,
                  tint = palette.text,
                  modifier = Modifier.size(18.dp),
                )
              },
              onClick = onReset,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun SidebarSessionCatalog(
  state: SessionCatalogState,
  catalog: SessionCatalog,
  activeSessionKey: String,
  liveSessionsByKey: Map<String, ChatSessionEntry>,
  collapsedHostIds: Set<String>,
  collapsedWorkspaceIds: Set<String>,
  palette: SidebarPalette,
  onToggleHost: (String) -> Unit,
  onToggleWorkspace: (String) -> Unit,
  onSelectSession: (SessionCatalogEntry) -> Unit,
  onLoadMore: (String) -> Unit,
  canMutateSessions: Boolean,
  groups: List<String>,
  onSetPinned: (String, String?, Boolean) -> Unit,
  onSetUnread: (String, String?, Boolean) -> Unit,
  onAssignGroup: (ChatSessionEntry, String?) -> Unit,
  onNewGroup: (ChatSessionEntry) -> Unit,
  onRename: (ChatSessionEntry) -> Unit,
  onDelete: (ChatSessionEntry) -> Unit,
) {
  val hosts = sidebarCatalogHosts(listOf(catalog))
  when {
    catalog.errorText != null && hosts.isEmpty() -> {
      SidebarCatalogStatus(catalog.errorText, palette)
    }

    // A provider with no hosts has nothing to list; the collapsible header is
    // enough, so no empty placeholder row.
    hosts.isEmpty() -> Unit

    else -> {
      catalog.errorText?.let { error ->
        SidebarCatalogStatus(error, palette)
      }
      hosts.forEach { host ->
        val hostExpanded = host.stableId !in collapsedHostIds
        Row(
          modifier =
            Modifier
              .fillMaxWidth()
              .clickable(role = Role.Button) { onToggleHost(host.stableId) }
              .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          SidebarChevron(expanded = hostExpanded, palette = palette)
          Icon(
            imageVector = Icons.Outlined.DesktopWindows,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = host.label,
            style = ClawTheme.type.caption,
            color = palette.muted,
            modifier = Modifier.weight(1f),
            maxLines = 1,
          )
          when {
            !host.errorText.isNullOrBlank() -> {
              Icon(
                imageVector = MiuixIcons.Medium.MiuixReport,
                contentDescription = nativeString("Host error"),
                tint = ClawTheme.colors.danger,
                modifier = Modifier.size(16.dp),
              )
            }

            !host.connected -> {
              Text(
                text = nativeString("Offline"),
                style = ClawTheme.type.caption,
                color = palette.muted,
                maxLines = 1,
              )
            }

            else -> {
              Text(
                text = host.workspaces.sumOf { it.sessions.size }.toString(),
                style = ClawTheme.type.caption,
                color = palette.muted,
                maxLines = 1,
              )
            }
          }
        }
        if (hostExpanded) {
          host.errorText?.let { SidebarCatalogStatus(it, palette) }
          if (host.workspaces.isEmpty() && host.errorText == null) {
            SidebarCatalogStatus(nativeString("No sessions"), palette)
          }
          host.workspaces.forEach { workspace ->
            val expanded = workspace.stableId !in collapsedWorkspaceIds
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .clickable(role = Role.Button) { onToggleWorkspace(workspace.stableId) }
                  .padding(start = 24.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              SidebarChevron(expanded = expanded, palette = palette)
              Icon(
                imageVector = MiuixIcons.Medium.MiuixFolder,
                contentDescription = null,
                tint = palette.muted,
                modifier = Modifier.size(16.dp),
              )
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = workspace.label,
                  style = ClawTheme.type.body,
                  color = palette.text,
                  maxLines = 1,
                )
                workspace.path?.takeIf { it != workspace.label }?.let { path ->
                  Text(
                    text = path,
                    style = ClawTheme.type.caption,
                    color = palette.muted,
                    maxLines = 1,
                  )
                }
              }
              Text(
                text = workspace.sessions.size.toString(),
                style = ClawTheme.type.caption,
                color = palette.muted,
                maxLines = 1,
              )
            }
            if (expanded) {
              workspace.sessions.forEach { session ->
                SidebarCatalogSessionRow(
                  session = session,
                  liveSession = session.sessionKey?.let(liveSessionsByKey::get),
                  selected = session.sessionKey == activeSessionKey,
                  continuing = state.continuingEntryId == session.locatorId,
                  selectionEnabled = state.continuingEntryId == null,
                  palette = palette,
                  onClick = { onSelectSession(session) },
                  canMutateSessions = canMutateSessions,
                  groups = groups,
                  onSetPinned = onSetPinned,
                  onSetUnread = onSetUnread,
                  onAssignGroup = onAssignGroup,
                  onNewGroup = onNewGroup,
                  onRename = onRename,
                  onDelete = onDelete,
                )
              }
            }
          }
        }
      }
      hosts
        .filter(SidebarCatalogHost::canLoadMore)
        .map(SidebarCatalogHost::catalogId)
        .distinct()
        .forEach { catalogId ->
          if (catalogId in state.loadingMoreCatalogIds) {
            SidebarCatalogStatus(nativeString("Loading more sessions"), palette, progress = true)
          } else {
            SidebarActionRow(
              label = nativeString("Load more"),
              icon = MiuixIcons.Medium.MiuixChevronForward,
              iconRotationZ = 90f,
              palette = palette,
              onClick = { onLoadMore(catalogId) },
            )
          }
        }
    }
  }
}

internal fun sidebarCatalogSessionSelectionEnabled(
  session: SessionCatalogEntry,
  canMutateSessions: Boolean,
): Boolean = session.sessionKey != null || (session.canContinue && canMutateSessions)

@Composable
private fun SidebarCatalogSessionRow(
  session: SessionCatalogEntry,
  liveSession: ChatSessionEntry?,
  selected: Boolean,
  continuing: Boolean,
  selectionEnabled: Boolean,
  palette: SidebarPalette,
  onClick: () -> Unit,
  canMutateSessions: Boolean,
  groups: List<String>,
  onSetPinned: (String, String?, Boolean) -> Unit,
  onSetUnread: (String, String?, Boolean) -> Unit,
  onAssignGroup: (ChatSessionEntry, String?) -> Unit,
  onNewGroup: (ChatSessionEntry) -> Unit,
  onRename: (ChatSessionEntry) -> Unit,
  onDelete: (ChatSessionEntry) -> Unit,
) {
  val enabled = sidebarCatalogSessionSelectionEnabled(session, canMutateSessions)
  val nativeTitle = session.name?.takeIf(String::isNotBlank) ?: session.threadId
  val pinned = liveSession?.pinned == true
  val mutableSession = liveSession?.takeIf { canMutateSessions }
  val activity =
    sidebarSessionActivity(
      // Adopted rows use Gateway state; unadopted catalogs also call their running state "active".
      status =
        if (liveSession != null) {
          liveSession.status
        } else {
          session.status
            .trim()
            .lowercase()
            .let { if (it == "active") "running" else it }
        },
      lastRunError = liveSession?.lastRunError,
      hasActiveRun = liveSession?.hasActiveRun,
      unread = liveSession?.unread == true,
      continuing = continuing,
    )
  val menuContent =
    mutableSession?.let { live ->
      sidebarSessionMenu(
        session = live,
        canMutate = true,
        groups = groups,
        onSetPinned = { next -> onSetPinned(live.key, live.ownerAgentId, next) },
        onSetUnread = { next -> onSetUnread(live.key, live.ownerAgentId, next) },
        onAssignGroup = { group -> onAssignGroup(live, group) },
        onNewGroup = { onNewGroup(live) },
        onRename = { onRename(live) },
        onDelete = { onDelete(live) },
      )
    }
  SidebarRowSurface(
    selected = selected,
    palette = palette,
    enabled = enabled && selectionEnabled,
    onClick = onClick,
    menuContent = menuContent,
    contentPadding = PaddingValues(start = 48.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = liveSession?.let { sessionPresentationTitle(it) { nativeTitle } } ?: nativeTitle,
        style = ClawTheme.type.body,
        color = if (enabled) palette.text else palette.muted,
        maxLines = 1,
      )
      val detail =
        listOfNotNull(
          nativeString("Pinned").takeIf { pinned },
          session.gitBranch,
        ).joinToString(" \u00b7 ")
      if (detail.isNotEmpty()) {
        Text(text = detail, style = ClawTheme.type.caption, color = palette.muted, maxLines = 1)
      }
    }
    activity?.let {
      SidebarSessionActivityIndicator(activity = it, palette = palette)
    }
  }
}

@Composable
private fun SidebarCatalogStatus(
  text: String,
  palette: SidebarPalette,
  progress: Boolean = false,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 10.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (progress) {
      CircularProgressIndicator(
        modifier = Modifier.size(14.dp),
        color = palette.text,
        strokeWidth = 2.dp,
      )
    }
    Text(text = text, style = ClawTheme.type.caption, color = palette.muted)
  }
}
