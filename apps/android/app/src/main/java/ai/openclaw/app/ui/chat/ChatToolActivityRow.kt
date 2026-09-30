package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatPendingToolCall
import ai.openclaw.app.chat.ChatToolActivity
import ai.openclaw.app.ui.design.ClawTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val TOOL_DETAIL_MAX_CHARS = 4_000

/** Object names run long (URLs, shell one-liners); one row must stay one line. */
private const val TOOL_TARGET_MAX_CHARS = 80

/** Fixed key for the live thinking row so the sheet can keep streaming updates. */
internal const val LIVE_THINKING_DETAIL_KEY = "thinking-live"

/**
 * One collapsed tool row plus everything the detail sheet needs.
 * Built from both live [ChatPendingToolCall] and finished [ChatToolActivity].
 */
internal data class ToolActivityDetail(
  val key: String,
  val title: String,
  val kind: CompletedToolKind,
  val target: String? = null,
  val detailText: String? = null,
  val output: String? = null,
  val isError: Boolean = false,
  val running: Boolean = false,
  val iconOverride: ImageVector? = null,
  /** Label for the detailText section in the sheet; defaults to "参数". */
  val detailLabel: String? = null,
)

/**
 * Object shown after the verb, falling back to the tool's own name.
 *
 * An unrecognised tool has neither a path nor a url to name, so the row used to
 * read a bare 正在使用 with no hint of what ran. Falling back to the tool name
 * keeps those rows identifiable instead of leaving a wall of identical rows.
 */
private fun toolTargetOrName(
  target: String?,
  name: String,
): String? =
  target?.trim()?.takeIf { it.isNotEmpty() }
    ?: completedToolDisplayName(name).trim().takeIf { it.isNotEmpty() }

private fun toolTitle(
  verb: String,
  target: String?,
): String = if (target.isNullOrBlank()) verb else "$verb $target"

/** Row icon: an explicit override when the caller has one, else the Lucide glyph. */
@Composable
private fun ToolRowIcon(
  detail: ToolActivityDetail,
  tint: Color,
  size: Dp,
) {
  if (detail.iconOverride != null) {
    Icon(
      imageVector = detail.iconOverride,
      contentDescription = null,
      modifier = Modifier.size(size),
      tint = tint,
    )
  } else {
    LucideToolIcon(kind = detail.kind, tint = tint, size = size, running = detail.running)
  }
}

internal fun pendingToolDetail(call: ChatPendingToolCall): ToolActivityDetail {
  val kind = completedToolKind(call.name)
  val target =
    toolTargetOrName(call.label ?: toolTargetLabel(call.args, call.name), call.name)
      ?.take(TOOL_TARGET_MAX_CHARS)
  return ToolActivityDetail(
    key = call.toolCallId,
    title = toolTitle(if (call.done) toolVerbDone(kind) else toolVerbInProgress(kind), target),
    kind = kind,
    target = target,
    detailText = call.args?.toString()?.take(TOOL_DETAIL_MAX_CHARS) ?: call.label,
    isError = call.isError == true,
    running = !call.done,
  )
}

internal fun completedToolDetail(
  tool: ChatToolActivity,
  running: Boolean = false,
): ToolActivityDetail {
  val kind = completedToolKind(tool.name)
  val target =
    toolTargetOrName(toolTargetLabel(tool.arguments, tool.name), tool.name)
      ?.take(TOOL_TARGET_MAX_CHARS)
  return ToolActivityDetail(
    key = tool.toolCallId ?: "${tool.name}:${tool.detail.orEmpty().hashCode()}",
    title = toolTitle(if (running) toolVerbInProgress(kind) else toolVerbDone(kind), target),
    kind = kind,
    target = target,
    detailText = tool.detail?.take(TOOL_DETAIL_MAX_CHARS),
    output = completedToolResultPresentation(tool).output?.take(TOOL_DETAIL_MAX_CHARS),
    isError = tool.isError,
    running = running,
  )
}

/** Collapsed single-line tool row: icon + 已写入 xxx, tappable for full detail. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolActivityRow(
  detail: ToolActivityDetail,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
) {
  val colors = ClawTheme.colors
  val rowColor = if (detail.isError) colors.danger else colors.textMuted
  // Material's Surface(onClick) stamps a 48dp minimum height onto the row; the
  // chain must hug, so the enforcement is opted out and the row keeps its
  // compact intrinsic height (full-width tap area is preserved).
  CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
    Surface(
      modifier = modifier.fillMaxWidth(),
      onClick = onClick,
      shape = RoundedCornerShape(4.dp),
      color = Color.Transparent,
      contentColor = rowColor,
    ) {
      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        ToolRowIcon(detail = detail, tint = rowColor, size = 16.dp)
        Text(
          text = detail.title,
          style = ClawTheme.type.caption,
          color = rowColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (detail.running) {
          Text(
            text = "…",
            style = ClawTheme.type.caption,
            color = colors.textSubtle,
          )
        }
        Icon(
          imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
          contentDescription = null,
          modifier = Modifier.size(14.dp),
          tint = colors.textSubtle,
        )
      }
    }
  }
}

/** Bottom sheet with the tool arguments and captured output. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ToolDetailSheet(
  detail: ToolActivityDetail,
  onDismiss: () -> Unit,
) {
  val colors = ClawTheme.colors
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = colors.surface,
    contentColor = colors.text,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(max = 560.dp)
          .verticalScroll(rememberScrollState())
          .padding(horizontal = 20.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        ToolRowIcon(
          detail = detail,
          tint = if (detail.isError) colors.danger else colors.accent,
          size = 18.dp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
          text = detail.title,
          style = ClawTheme.type.section,
          fontWeight = FontWeight.SemiBold,
          color = if (detail.isError) colors.danger else colors.text,
        )
      }
      if (!detail.detailText.isNullOrBlank()) {
        ToolDetailSection(label = detail.detailLabel ?: "参数", value = detail.detailText)
      }
      if (!detail.output.isNullOrBlank()) {
        ToolDetailSection(label = if (detail.isError) "错误" else "输出", value = detail.output)
      } else if (detail.isError) {
        ToolDetailSection(label = "错误", value = "工具执行失败，没有返回输出。")
      } else if (detail.running) {
        ToolDetailSection(label = "状态", value = "工具正在执行中，完成后会显示输出。")
      }
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun ToolDetailSection(
  label: String,
  value: String,
) {
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Text(
      text = label,
      style = ClawTheme.type.captionSmall,
      color = ClawTheme.colors.textSubtle,
    )
    Text(
      text = value,
      style = ClawTheme.type.mono,
      color = ClawTheme.colors.textMuted,
    )
  }
}
