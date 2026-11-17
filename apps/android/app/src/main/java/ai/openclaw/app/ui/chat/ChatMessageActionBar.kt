package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessageContent
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.rememberClawHaptics
import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Copy as MiuixCopy
import top.yukonga.miuix.kmp.icon.extended.Refresh as MiuixRefresh
import top.yukonga.miuix.kmp.icon.extended.Share as MiuixShare

/**
 * Displayable reply content of a persisted assistant row, or null when the row
 * owns no answer: user/system rows and reasoning-only chain segments never get
 * an action bar — the bar belongs to the reply itself.
 */
internal fun chatAssistantReplyContent(item: ChatTimelineItem): List<ChatMessageContent>? {
  val message = (item as? ChatTimelineItem.Message)?.message ?: return null
  if (!message.role.trim().equals("assistant", ignoreCase = true)) return null
  val visible = message.content.filter { it.toolActivity == null }
  if (visible.isEmpty() || visible.all { it.type == "thinking" }) return null
  if (visible.all { it.type == "text" && it.text.isNullOrBlank() }) return null
  return visible
}

/**
 * Entry id of the user prompt that opened this reply's turn. Retry rewinds the
 * transcript to that entry and resends its text, regenerating the answer.
 * The list is newest-first, so the question sits at a higher index; chain rows
 * (reasoning/tools) never interrupt the walk. Null when no shareable question
 * precedes the reply (cached history without entry ids, conversation opener).
 */
internal fun chatRetrySourceEntryId(
  items: List<ChatTimelineItem>,
  replyIndex: Int,
): String? {
  for (index in replyIndex + 1 until items.size) {
    val message = (items[index] as? ChatTimelineItem.Message)?.message ?: continue
    if (message.role.trim().equals("user", ignoreCase = true)) {
      return message.entryId?.takeIf(String::isNotBlank)
    }
  }
  return null
}

/** Sink distance of the entrance: the bar settles down into its fixed slot. */
private const val ACTION_BAR_SINK_DP = 6f
private const val ACTION_BAR_ENTRANCE_MS = 360
private const val ACTION_BAR_BLUR_DP = 6f

/** Same long-tail deceleration as the text reveal: fast start, slow landing. */
private val ActionBarEntranceEasing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** Blur needs RenderEffect, i.e. API 31+; older devices just get the fade. */
private val actionBarBlurSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Persistent action bar under one finished agent reply: faint divider, then a
 * row of miuix icons — copy / share on the left, retry on the right.
 *
 * [animateEntrance] is the row's one-shot reveal flag: a reply that just finished
 * generating sinks a few dp into place while its blur dissolves, then the bar
 * stays fixed. History rows compose with the flag cleared and never animate.
 * While the batch share picker owns row taps the bar keeps its layout slot but
 * fades out of the way, so opening the picker never reflows the transcript.
 */
@Composable
internal fun ChatAssistantActionBar(
  messageId: String,
  copyText: String,
  onShare: () -> Unit,
  retryEnabled: Boolean,
  onRetry: (() -> Unit)?,
  animateEntrance: Boolean,
  pickerActive: Boolean,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  var revealed by remember { mutableStateOf(!animateEntrance) }
  LaunchedEffect(Unit) { revealed = true }
  val entrance by animateFloatAsState(
    targetValue = if (revealed) 1f else 0f,
    animationSpec = tween(ACTION_BAR_ENTRANCE_MS, easing = ActionBarEntranceEasing),
    label = "assistantActionBarEntrance",
  )
  val pickerFade by animateFloatAsState(
    targetValue = if (pickerActive) 0f else 1f,
    animationSpec = tween(150),
    label = "assistantActionBarPickerFade",
  )
  // The layer is mounted only while something animates. A permanently attached
  // graphicsLayer/blur would keep every reply's bar on its own offscreen buffer.
  val animating = entrance < 1f || pickerFade < 1f
  val entranceModifier =
    if (animating) {
      val layer =
        Modifier.graphicsLayer {
          alpha = entrance * pickerFade
          translationY = (1f - entrance) * -ACTION_BAR_SINK_DP.dp.toPx()
        }
      // Blur clears ahead of the fade so the icons are sharp before fully opaque.
      val blurRadius = (1f - entrance * 1.4f).coerceIn(0f, 1f) * ACTION_BAR_BLUR_DP
      if (actionBarBlurSupported && blurRadius >= 0.5f) layer.blur(blurRadius.dp) else layer
    } else {
      Modifier
    }
  Column(modifier = modifier.fillMaxWidth().padding(top = 6.dp).then(entranceModifier)) {
    HorizontalDivider(thickness = 0.5.dp, color = ClawTheme.colors.border)
    Row(
      modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ActionBarButton(
        icon = MiuixIcons.Medium.MiuixCopy,
        contentDescription = nativeString("复制全文"),
        enabled = copyText.isNotBlank() && !pickerActive,
        testTag = "chat-action-copy:$messageId",
        onClick = { copyChatText(context, copyText) },
      )
      ActionBarButton(
        icon = MiuixIcons.Medium.MiuixShare,
        contentDescription = nativeString("分享"),
        enabled = !pickerActive,
        testTag = "chat-action-share:$messageId",
        onClick = onShare,
      )
      Spacer(Modifier.weight(1f))
      onRetry?.let { retry ->
        ActionBarButton(
          icon = MiuixIcons.Medium.MiuixRefresh,
          contentDescription = nativeString("重试"),
          enabled = retryEnabled && !pickerActive,
          testTag = "chat-action-retry:$messageId",
          onClick = retry,
        )
      }
    }
  }
}

/** Icon-only bar button: no ripple, the whole surface answers a press with a pure-scale spring. */
@Composable
private fun ActionBarButton(
  icon: ImageVector,
  contentDescription: String,
  enabled: Boolean,
  testTag: String,
  onClick: () -> Unit,
) {
  val haptics = rememberClawHaptics()
  val interaction = remember { MutableInteractionSource() }
  val pressed by interaction.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed && enabled) 0.86f else 1f,
    animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
    label = "assistantActionBarPress",
  )
  Box(
    modifier =
      Modifier
        .testTag(testTag)
        .size(36.dp)
        .clip(CircleShape)
        .clickable(
          interactionSource = interaction,
          indication = null,
          enabled = enabled,
          role = Role.Button,
          onClick = {
            haptics.tap()
            onClick()
          },
        )
        .then(
          if (scale != 1f) {
            Modifier.graphicsLayer {
              scaleX = scale
              scaleY = scale
            }
          } else {
            Modifier
          },
        ),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(ClawTheme.spacing.icon),
      tint = if (enabled) ClawTheme.colors.textMuted else ClawTheme.colors.textSubtle.copy(alpha = 0.5f),
    )
  }
}