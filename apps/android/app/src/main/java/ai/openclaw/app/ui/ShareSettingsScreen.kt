package ai.openclaw.app.ui

import ai.openclaw.app.MainViewModel
import ai.openclaw.app.SessionShareDeleteOutcome
import ai.openclaw.app.SessionShareListOutcome
import ai.openclaw.app.SessionShareSummary
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.sessionShareFailureText
import ai.openclaw.app.ui.design.ClawGlassTopBarButton
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.ClawTopBarButtonTopInset
import ai.openclaw.app.ui.design.ClawTopBarContentHeight
import ai.openclaw.app.ui.design.clawBackdropTopBar
import ai.openclaw.app.ui.design.rememberClawBackdrop
import ai.openclaw.app.ui.design.rememberClawHaptics
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy as MiuixCopy
import top.yukonga.miuix.kmp.icon.extended.Delete as MiuixDelete
import top.yukonga.miuix.kmp.icon.extended.Refresh as MiuixRefresh
import top.yukonga.miuix.kmp.icon.extended.Share as MiuixShare
import java.time.Instant

private val ShareTileBlue = Color(0xFF2C84FA)

private sealed interface ShareListState {
  data object Loading : ShareListState

  data object Disconnected : ShareListState

  data class Ready(val shares: List<ShareUi>) : ShareListState

  data class Failed(val text: String, val pluginMissing: Boolean = false) : ShareListState
}

private data class ShareUi(
  val id: String,
  val title: String,
  val url: String,
  val deadlineElapsedMs: Long,
  val createdAtEpochMs: Long,
)

@Composable
internal fun ShareSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val haptics = rememberClawHaptics()
  val isConnected by viewModel.isConnected.collectAsState()
  var state by remember { mutableStateOf<ShareListState>(ShareListState.Loading) }
  var refreshing by remember { mutableStateOf(false) }
  var reloadKey by remember { mutableIntStateOf(0) }
  var nowElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
  var pendingDelete by remember { mutableStateOf<ShareUi?>(null) }
  var deletingId by remember { mutableStateOf<String?>(null) }
  // 404 from the share route is the plugin handshake: the route only answers when
  // the gateway-side share plugin is loaded, so no extra gateway API is needed.
  var pluginDialogVisible by remember { mutableStateOf(false) }

  fun removeShare(id: String) {
    val current = state
    if (current is ShareListState.Ready) {
      state = current.copy(shares = current.shares.filterNot { it.id == id })
    }
  }

  fun deleteShare(share: ShareUi) {
    if (deletingId != null) return
    deletingId = share.id
    scope.launch {
      when (val outcome = viewModel.deleteChatShare(share.id)) {
        SessionShareDeleteOutcome.Success,
        SessionShareDeleteOutcome.NotFound,
        -> removeShare(share.id)

        is SessionShareDeleteOutcome.Failure ->
          Toast
            .makeText(
              context,
              sessionShareFailureText(outcome.failure, nativeString("删除分享失败，请稍后重试")),
              Toast.LENGTH_LONG,
            ).show()
      }
      deletingId = null
    }
  }

  LaunchedEffect(isConnected, reloadKey) {
    if (!isConnected) {
      state = ShareListState.Disconnected
      refreshing = false
      return@LaunchedEffect
    }
    val hadContent = state is ShareListState.Ready
    if (!hadContent) state = ShareListState.Loading
    refreshing = true
    val outcome = viewModel.listChatShares()
    refreshing = false
    nowElapsedMs = SystemClock.elapsedRealtime()
    when (outcome) {
      is SessionShareListOutcome.Success -> {
        state =
          ShareListState.Ready(
            outcome.shares
              .map(::shareUi)
              .sortedByDescending(ShareUi::createdAtEpochMs),
          )
      }

      is SessionShareListOutcome.Failure -> {
        val failure = outcome.failure
        if (failure.code == 404) {
          state = ShareListState.Failed("当前网关还没有安装分享插件", pluginMissing = true)
          pluginDialogVisible = true
        } else if (hadContent) {
          Toast
            .makeText(
              context,
              sessionShareFailureText(failure, nativeString("刷新分享链接失败，请稍后重试")),
              Toast.LENGTH_LONG,
            ).show()
        } else {
          state =
            ShareListState.Failed(
              sessionShareFailureText(failure, nativeString("加载分享链接失败，请稍后重试")),
            )
        }
      }
    }
  }

  // Remaining time is minute-granular, so a half-minute tick is plenty and keeps
  // the list from recomposing every second.
  LaunchedEffect(Unit) {
    while (true) {
      delay(30_000L)
      nowElapsedMs = SystemClock.elapsedRealtime()
    }
  }

  val listState = rememberLazyListState()
  val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val topBarHeight = statusBarHeight + ClawTopBarContentHeight
  val backdrop = rememberClawBackdrop()
  val scrollProgressState =
    remember(listState) {
      derivedStateOf {
        if (listState.firstVisibleItemIndex > 0) {
          1f
        } else {
          val headerSize = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == 0 }?.size ?: 1
          (listState.firstVisibleItemScrollOffset / (headerSize * 0.5f).coerceAtLeast(1f)).coerceIn(0f, 1f)
        }
      }
    }
  val scrollProgress by scrollProgressState
  val bigTitleAlphaState =
    remember(scrollProgressState) {
      derivedStateOf { (1f - scrollProgressState.value / 0.5f).coerceIn(0f, 1f) }
    }
  val bigTitleAlpha by bigTitleAlphaState
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
      label = "shareTopChrome",
    )
  val topTitleAlpha by
    animateFloatAsState(
      targetValue = if (scrollProgress > 0.42f) 1f else 0f,
      animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "shareTopTitle",
    )

  fun copyShareLink(url: String) {
    if (url.isBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("OpenClaw share link", url))
    Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    Box(modifier = Modifier.fillMaxSize().layerBackdrop(backdrop).background(ClawTheme.colors.canvas)) {
      ClawScaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        contentPadding = PaddingValues(start = ClawTheme.spacing.sm, top = 0.dp, end = ClawTheme.spacing.sm, bottom = 8.dp),
      ) {
        LazyColumn(
          state = listState,
          modifier = Modifier.fillMaxSize(),
          verticalArrangement = Arrangement.spacedBy(12.dp),
          contentPadding = PaddingValues(top = topBarHeight, bottom = 20.dp),
        ) {
          item {
            Column(
              modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 2.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
              Text(
                text = nativeString("Share"),
                style = ClawTheme.type.display.copy(fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold),
                color = ClawTheme.colors.text,
                modifier = Modifier.graphicsLayer { alpha = bigTitleAlpha },
              )
              Text(
                text = "链接到期后自动失效 · 网页分享需要网关安装 share 插件",
                style = ClawTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = ClawTheme.colors.textMuted,
                modifier = Modifier.graphicsLayer { alpha = bigTitleAlpha },
              )
            }
          }

          val shares = (state as? ShareListState.Ready)?.shares.orEmpty()
          item {
            Row(
              modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 2.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween,
            ) {
              Text(
                text = "已分享",
                style = ClawTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = ClawTheme.colors.textMuted,
              )
              if (shares.isNotEmpty()) {
                Text(
                  text = "${shares.size} 条",
                  style = ClawTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
                  color = ClawTheme.colors.textSubtle,
                )
              }
            }
          }

          when (val current = state) {
            ShareListState.Loading -> {
              item {
                ShareStatusCard(
                  title = nativeString("正在加载分享链接"),
                  body = "",
                  loading = true,
                )
              }
            }

            ShareListState.Disconnected -> {
              item {
                ShareStatusCard(
                  title = "未连接网关",
                  body = "连接网关后才能查看已分享的链接。",
                )
              }
            }

            is ShareListState.Failed -> {
              item {
                ShareStatusCard(
                  title = if (current.pluginMissing) "未安装分享插件" else nativeString("加载失败"),
                  body =
                    if (current.pluginMissing) {
                      "网页分享（复制链接、微信好友）需要在网关侧安装并启用 share 插件，安装后已分享的链接会显示在这里。"
                    } else {
                      current.text
                    },
                  actionLabel = nativeString("Retry"),
                  onAction = { reloadKey += 1 },
                )
              }
            }

            is ShareListState.Ready -> {
              if (current.shares.isEmpty()) {
                item {
                  ShareStatusCard(
                    title = "还没有分享链接",
                    body = "在聊天中长按消息，选择分享即可生成链接。",
                  )
                }
              } else {
                items(current.shares, key = ShareUi::id) { share ->
                  ShareCard(
                    share = share,
                    nowElapsedMs = nowElapsedMs,
                    deleting = deletingId == share.id,
                    actionsEnabled = deletingId == null,
                    onCopy = {
                      haptics.tap()
                      copyShareLink(share.url)
                    },
                    onDelete = {
                      haptics.tap()
                      pendingDelete = share
                    },
                    modifier = Modifier.animateItem(),
                  )
                }
              }
            }
          }

          item {
            Spacer(modifier = Modifier.height(8.dp))
          }
        }
      }
    }

    Box(modifier = Modifier.fillMaxWidth().height(topBarHeight).clawBackdropTopBar(backdrop)) {
      ClawGlassTopBarButton(
        contentDescription = nativeString("Back"),
        chromeAlpha = chromeAlpha,
        backdrop = backdrop,
        onClick = onBack,
        modifier =
          Modifier
            .align(Alignment.TopStart)
            .padding(top = statusBarHeight + ClawTopBarButtonTopInset, start = ClawTheme.spacing.sm),
      ) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.ArrowBack,
          contentDescription = null,
          modifier = Modifier.size(22.dp),
          tint = ClawTheme.colors.text,
        )
      }
      ClawGlassTopBarButton(
        contentDescription = nativeString("Refresh"),
        chromeAlpha = chromeAlpha,
        backdrop = backdrop,
        onClick = { reloadKey += 1 },
        enabled = isConnected && !refreshing,
        modifier =
          Modifier
            .align(Alignment.TopEnd)
            .padding(top = statusBarHeight + ClawTopBarButtonTopInset, end = ClawTheme.spacing.sm),
      ) {
        if (refreshing) {
          CircularProgressIndicator(
            modifier = Modifier.size(17.dp),
            color = ClawTheme.colors.textMuted,
            strokeWidth = 2.dp,
          )
        } else {
          Icon(
            imageVector = MiuixIcons.Medium.MiuixRefresh,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (isConnected) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
          )
        }
      }
      Box(
        modifier =
          Modifier
            .align(Alignment.TopCenter)
            .padding(top = statusBarHeight)
            .height(ClawTopBarContentHeight)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
      ) {
        Text(
          text = nativeString("Share"),
          style = ClawTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
          color = ClawTheme.colors.text,
          modifier =
            Modifier.graphicsLayer {
              alpha = topTitleAlpha
              translationY = (1f - topTitleAlpha) * 8.dp.toPx()
            },
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }

  if (pluginDialogVisible) {
    AlertDialog(
      onDismissRequest = { pluginDialogVisible = false },
      shape = RoundedCornerShape(26.dp),
      containerColor = ClawTheme.colors.surfaceRaised,
      title = {
        Text(
          text = "需要网关安装分享插件",
          style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
          color = ClawTheme.colors.text,
        )
      },
      text = {
        Text(
          text = "网页分享（复制链接、微信好友）依赖网关侧的 share 插件。当前网关没有检测到这个插件，安装后即可使用。",
          style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
          color = ClawTheme.colors.textMuted,
        )
      },
      confirmButton = {
        TextButton(onClick = { pluginDialogVisible = false }) {
          Text("知道了", color = ClawTheme.colors.primary)
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            pluginDialogVisible = false
            reloadKey += 1
          },
        ) {
          Text(nativeString("Retry"), color = ClawTheme.colors.textMuted)
        }
      },
    )
  }

  pendingDelete?.let { share ->
    AlertDialog(
      onDismissRequest = { if (deletingId == null) pendingDelete = null },
      shape = RoundedCornerShape(26.dp),
      containerColor = ClawTheme.colors.surfaceRaised,
      title = {
        Text(
          text = "删除这个分享？",
          style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
          color = ClawTheme.colors.text,
        )
      },
      text = {
        Text(
          text = "链接会立即失效，已经发出去的链接也无法再打开。",
          style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
          color = ClawTheme.colors.textMuted,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            pendingDelete = null
            deleteShare(share)
          },
          enabled = deletingId == null,
        ) {
          Text(nativeString("Delete"), color = ClawTheme.colors.danger)
        }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }, enabled = deletingId == null) {
          Text(nativeString("Cancel"), color = ClawTheme.colors.textMuted)
        }
      },
    )
  }
}

@Composable
private fun ShareCard(
  share: ShareUi,
  nowElapsedMs: Long,
  deleting: Boolean,
  actionsEnabled: Boolean,
  onCopy: () -> Unit,
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val remainingSeconds = shareRemainingSeconds(deadlineElapsedMs = share.deadlineElapsedMs, nowElapsedMs = nowElapsedMs)
  val remainingColor =
    when {
      remainingSeconds <= 0L -> ClawTheme.colors.textSubtle
      remainingSeconds < 3_600L -> ClawTheme.colors.warning
      else -> ClawTheme.colors.textMuted
    }
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = ClawTheme.colors.surface,
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(
          modifier =
            Modifier
              .size(36.dp)
              .clip(RoundedCornerShape(11.dp))
              .background(ShareTileBlue.copy(alpha = if (remainingSeconds > 0L) 1f else 0.5f)),
          contentAlignment = Alignment.Center,
        ) {
          Icon(
            imageVector = MiuixIcons.Medium.MiuixShare,
            contentDescription = null,
            modifier = Modifier.size(19.dp),
            tint = Color.White,
          )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = share.title,
            style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
            color = ClawTheme.colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
          Text(
            text = shareRemainingText(remainingSeconds),
            style = ClawTheme.type.caption.copy(fontSize = 13.sp, lineHeight = 17.sp),
            color = remainingColor,
            maxLines = 1,
          )
        }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          ShareIconAction(
            icon = MiuixIcons.Medium.MiuixCopy,
            contentDescription = "复制链接",
            tint = ClawTheme.colors.textMuted,
            enabled = actionsEnabled && share.url.isNotBlank(),
            onClick = onCopy,
          )
          ShareIconAction(
            icon = MiuixIcons.Medium.MiuixDelete,
            contentDescription = "删除分享",
            tint = ClawTheme.colors.danger,
            enabled = actionsEnabled,
            loading = deleting,
            onClick = onDelete,
          )
        }
      }
      if (share.url.isNotBlank()) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(top = 8.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(ClawTheme.colors.surfacePressed.copy(alpha = 0.65f))
              .padding(horizontal = 10.dp, vertical = 7.dp),
        ) {
          Text(
            text = share.url,
            style =
              ClawTheme.type.caption.copy(
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFamily = FontFamily.Monospace,
              ),
            color = ClawTheme.colors.textMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
    }
  }
}

@Composable
private fun ShareIconAction(
  icon: ImageVector,
  contentDescription: String,
  tint: Color,
  enabled: Boolean,
  onClick: () -> Unit,
  loading: Boolean = false,
) {
  Box(
    modifier =
      Modifier
        .size(40.dp)
        .clip(CircleShape)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics {
          role = Role.Button
          this.contentDescription = contentDescription
        },
    contentAlignment = Alignment.Center,
  ) {
    if (loading) {
      CircularProgressIndicator(
        modifier = Modifier.size(16.dp),
        color = ClawTheme.colors.textMuted,
        strokeWidth = 2.dp,
      )
    } else {
      Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = if (enabled) tint else tint.copy(alpha = 0.35f),
      )
    }
  }
}

@Composable
private fun ShareStatusCard(
  title: String,
  body: String,
  loading: Boolean = false,
  actionLabel: String? = null,
  onAction: (() -> Unit)? = null,
) {
  Surface(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(24.dp),
    color = ClawTheme.colors.surface,
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          color = ClawTheme.colors.textMuted,
          strokeWidth = 2.dp,
        )
      } else {
        Icon(
          imageVector = MiuixIcons.Medium.MiuixShare,
          contentDescription = null,
          modifier = Modifier.size(26.dp),
          tint = ClawTheme.colors.textSubtle,
        )
      }
      Text(
        text = title,
        style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        color = ClawTheme.colors.text,
        textAlign = TextAlign.Center,
      )
      if (body.isNotBlank()) {
        Text(
          text = body,
          style = ClawTheme.type.body.copy(fontSize = 13.sp, lineHeight = 18.sp),
          color = ClawTheme.colors.textMuted,
          textAlign = TextAlign.Center,
        )
      }
      if (actionLabel != null && onAction != null) {
        Box(
          modifier =
            Modifier
              .clip(RoundedCornerShape(18.dp))
              .background(ClawTheme.colors.surfacePressed)
              .clickable(role = Role.Button, onClick = onAction)
              .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
          Text(
            text = actionLabel,
            style = ClawTheme.type.body.copy(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            color = ClawTheme.colors.primary,
          )
        }
      }
    }
  }
}

private fun shareUi(share: SessionShareSummary): ShareUi =
  ShareUi(
    id = share.id,
    title = share.title.takeIf { it.isNotBlank() } ?: "对话分享",
    url = share.url,
    deadlineElapsedMs = SystemClock.elapsedRealtime() + share.remainingSeconds.coerceAtLeast(0L) * 1_000L,
    createdAtEpochMs =
      share.createdAt
        ?.takeIf { it.isNotBlank() }
        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
        ?: 0L,
  )

/** Remaining lifetime rounded up to whole seconds, never negative. */
internal fun shareRemainingSeconds(
  deadlineElapsedMs: Long,
  nowElapsedMs: Long,
): Long = ((deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L) + 999L) / 1_000L

internal fun shareRemainingText(remainingSeconds: Long): String {
  if (remainingSeconds <= 0L) return "已过期"
  if (remainingSeconds < 60L) return "即将失效"
  val days = remainingSeconds / 86_400L
  val hours = (remainingSeconds % 86_400L) / 3_600L
  val minutes = (remainingSeconds % 3_600L) / 60L
  return when {
    days > 0L && hours > 0L -> "剩余 $days 天 $hours 小时"
    days > 0L -> "剩余 $days 天"
    hours > 0L && minutes > 0L -> "剩余 $hours 小时 $minutes 分"
    hours > 0L -> "剩余 $hours 小时"
    else -> "剩余 $minutes 分"
  }
}
