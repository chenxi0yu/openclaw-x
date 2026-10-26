package ai.openclaw.app.ui.chat

import ai.openclaw.app.chat.ChatMessage
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawCircularButtonDefaults
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.LucideLinkIcon
import ai.openclaw.app.ui.design.LucideTextIcon
import ai.openclaw.app.ui.design.MiuixCheckCircle
import ai.openclaw.app.ui.design.WeChatBrandIcon
import ai.openclaw.app.ui.design.clawLiquidButton
import ai.openclaw.app.ui.design.clawPressDrag
import ai.openclaw.app.ui.design.rememberClawHaptics
import ai.openclaw.app.ui.design.rememberClawPressDrag
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import java.util.Locale

/**
 * Horizontal room the checkbox rail needs. Agent rows shift right by it while
 * user rows already sit clear of the rail, so only agent bubbles move.
 */
internal val ChatShareSelectionGutter = 40.dp

/** Whether this transcript row is a user/assistant bubble the share picker can take. */
internal fun chatShareableMessage(item: ChatTimelineItem): ChatMessage? {
  val message = (item as? ChatTimelineItem.Message)?.message ?: return null
  val role = message.role.trim().lowercase(Locale.US)
  if (role != "user" && role != "assistant") return null
  if (message.content.none { it.toolActivity == null }) return null
  // The share create endpoint addresses messages by their server-side transcript
  // id (`__openclaw.id`, surfaced as entryId). ChatMessage.id is a client-local
  // random UUID that the gateway has never heard of; rows without an entryId
  // (still streaming, legacy history) cannot be shared.
  if (message.entryId.isNullOrBlank()) return null
  return message
}

/** Server-side transcript id of a shareable row, or null when the row cannot be shared. */
internal fun chatShareableEntryId(item: ChatTimelineItem): String? =
  chatShareableMessage(item)?.entryId

/** Selectable bubbles in conversation order (oldest first), one entry per message id. */
internal fun chatShareableMessages(items: List<ChatTimelineItem>): List<ChatMessage> =
  items
    .asReversed()
    .mapNotNull(::chatShareableMessage)
    .distinctBy { it.entryId }

/**
 * Default batch selection for a long-pressed bubble: the bubble itself plus the
 * user question that opened its turn. A pressed user bubble selects only itself.
 * Keys are server-side transcript ids (entryId), the same ids the create
 * endpoint accepts.
 */
internal fun chatShareSelectionSeed(
  items: List<ChatTimelineItem>,
  entryId: String,
): Set<String> {
  val index = items.indexOfFirst { (it as? ChatTimelineItem.Message)?.message?.entryId == entryId }
  if (index < 0) return emptySet()
  val pressed = chatShareableMessage(items[index]) ?: return emptySet()
  val pressedId = pressed.entryId ?: return emptySet()
  val selected = linkedSetOf(pressedId)
  if (pressed.role.trim().equals("assistant", ignoreCase = true)) {
    // The list is newest-first, so the question sits at a higher index. Chain
    // rows (reasoning/tools) never interrupt the walk; the first user bubble is
    // the input that started this turn.
    for (next in index + 1 until items.size) {
      val candidate = chatShareableMessage(items[next]) ?: continue
      val candidateId = candidate.entryId ?: continue
      if (candidate.role.trim().equals("user", ignoreCase = true)) {
        selected += candidateId
        break
      }
    }
  }
  return selected
}

/**
 * Transparent layer that swallows every touch. Used over the header capsule and
 * the composer while the share picker is open; the transcript stays scrollable.
 */
internal fun Modifier.chatShareTouchShield(enabled: Boolean): Modifier =
  if (!enabled) {
    this
  } else {
    pointerInput(Unit) {
      // The detector consumes the down event, so nothing behind the layer sees a
      // tap, long press, or click.
      detectTapGestures(onTap = {}, onLongPress = {})
    }
  }

/**
 * Full-screen blocker behind the share card: nothing behind it scrolls or
 * reacts, a tap on blank space (or back) dismisses the card. The shell
 * drawer's own edge swipe is disabled while the card is open, so this layer
 * also owns the left-edge swipe — a rightward drag dismisses the card,
 * mirroring the sidebar gesture direction. Nothing is uploaded until a link
 * action runs, so dismissal has no server-side cleanup.
 */
internal fun Modifier.chatShareBlankDismiss(onDismiss: () -> Unit): Modifier =
  pointerInput(Unit) {
    detectTapGestures(onTap = { onDismiss() }, onLongPress = {})
  }.pointerInput(Unit) {
    val edgeWidth = 32.dp.toPx()
    val dismissDistance = 40.dp.toPx()
    awaitEachGesture {
      val down = awaitFirstDown(requireUnconsumed = false)
      if (down.position.x > edgeWidth) return@awaitEachGesture
      val startX = down.position.x
      while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id } ?: continue
        if (change.position.x - startX >= dismissDistance) {
          onDismiss()
          break
        }
        if (!change.pressed) break
      }
    }
  }

/**
 * The placement-only inset that glides a row right to clear the checkbox rail.
 *
 * Extracted because the transcript's process rows (thinking, tool chains,
 * folded step groups) are siblings of the message bubbles, not children: they
 * need the same spring so an answer and its process stay aligned while the
 * picker opens.
 */
@Composable
internal fun rememberChatShareStartInset(
  selectionActive: Boolean,
  shiftContent: Boolean,
): Dp {
  val shift by
    animateFloatAsState(
      targetValue = if (selectionActive && shiftContent) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.74f, stiffness = 360f, visibilityThreshold = 0.001f),
      label = "chat share message shift",
    )
  // The damped spring over-shoots both ends, so clamp the inset to >= 0; the
  // overshoot past the rail (shift > 1) is harmless and momentary.
  return (ChatShareSelectionGutter * shift).coerceAtLeast(0.dp)
}

/**
 * One message row inside the share picker. Agent rows glide right on a spring to
 * clear the checkbox rail via a placement-only offset, so text never reflows;
 * the checkbox itself sits at the row's left, vertically centred. The layout is
 * not otherwise reordered: the row keeps its slot in the transcript.
 */
@Composable
internal fun ChatShareSelectionRow(
  selectable: Boolean,
  shiftContent: Boolean,
  selectionActive: Boolean,
  selected: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String? = null,
  content: @Composable () -> Unit,
) {
  // Seeded with the current value so a row composed while the picker is already
  // open does not replay the entrance when it scrolls into view.
  val checkboxVisibility = remember { MutableTransitionState(selectionActive) }
  LaunchedEffect(selectionActive) { checkboxVisibility.targetState = selectionActive }

  val startInset = rememberChatShareStartInset(selectionActive, shiftContent)
  // Tapping a message body also toggles it while the picker is open, not only the
  // checkbox at the rail. The checkbox sits on top in the Box, so its own
  // clickable consumes the touch and never double-toggles with the row.
  val rowInteraction = remember { MutableInteractionSource() }

  Box(modifier.fillMaxWidth()) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          // Placement-only shift: the bubble keeps its measured width and exact
          // line wrapping, so opening the picker never reflows text. A full-width
          // answer runs past the right edge instead of squeezing — that is the
          // accepted trade against the jarring rewrap.
          .offset(x = startInset)
          .clickable(
            interactionSource = rowInteraction,
            indication = null,
            enabled = selectionActive && selectable,
            onClick = onToggle,
          ),
    ) {
      content()
    }
    if (selectable) {
      AnimatedVisibility(
        visibleState = checkboxVisibility,
        modifier = Modifier.align(Alignment.CenterStart).offset(x = (-4).dp),
        enter =
          fadeIn(tween(90)) +
            scaleIn(
              initialScale = 0.5f,
              transformOrigin = TransformOrigin(0f, 0.5f),
              animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
            ),
        exit =
          fadeOut(tween(90)) +
            scaleOut(
              targetScale = 0.6f,
              transformOrigin = TransformOrigin(0f, 0.5f),
              animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
            ),
      ) {
        MiuixCheckCircle(
          checked = selected,
          onCheckedChange = { onToggle() },
          contentDescription = nativeString("选择消息"),
          modifier = if (testTag == null) Modifier else Modifier.testTag(testTag),
        )
      }
    }
  }
}

/**
 * Floating selection bar: cancel, select the whole session, confirm. It matches
 * the header capsule's material (liquid glass, pill silhouette, press-drag) and
 * springs up from the bottom centre; dismissing bounces it back down.
 */
@Composable
internal fun ChatShareSelectionBar(
  visible: Boolean,
  selectedCount: Int,
  allSelected: Boolean,
  onCancel: () -> Unit,
  onSelectAll: () -> Unit,
  onConfirm: () -> Unit,
  modifier: Modifier = Modifier,
  backdrop: Backdrop? = null,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter =
      fadeIn(tween(90)) +
        scaleIn(
          initialScale = 0.62f,
          transformOrigin = TransformOrigin(0.5f, 1f),
          animationSpec = spring(dampingRatio = 0.55f, stiffness = 420f, visibilityThreshold = 0.001f),
        ) +
        slideInVertically(
          initialOffsetY = { it },
          animationSpec = spring(dampingRatio = 0.68f, stiffness = 380f),
        ),
    exit =
      fadeOut(tween(170)) +
        scaleOut(
          targetScale = 0.72f,
          transformOrigin = TransformOrigin(0.5f, 1f),
          animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        ) +
        slideOutVertically(
          targetOffsetY = { it },
          animationSpec = spring(dampingRatio = 0.6f, stiffness = 420f),
        ),
  ) {
    val colors = ClawTheme.colors
    val dark = colors.canvas.luminance() < 0.5f
    val surface = colors.surfaceRaised
    val pillShape = RoundedCornerShape(percent = 50)
    val pressDrag = rememberClawPressDrag(glow = dark)
    val haptics = rememberClawHaptics()
    Row(
      modifier =
        Modifier
          .testTag("chat-share-selection-bar")
          .clawPressDrag(pressDrag)
          .height(ClawTheme.spacing.touchTarget)
          .shadow(
            elevation = ClawCircularButtonDefaults.Elevation,
            shape = pillShape,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f),
            clip = false,
          ).clip(pillShape)
          .clawLiquidButton(backdrop, surface, dark)
          .then(pressDrag.modifier)
          .then(pressDrag.gestureModifier)
          .padding(horizontal = 6.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      ChatShareSelectionAction(
        label = nativeString("取消"),
        onClick = {
          haptics.tap()
          onCancel()
        },
      )
      ChatShareSelectionAction(
        label = nativeString("全选"),
        enabled = !allSelected,
        onClick = {
          haptics.tap()
          onSelectAll()
        },
      )
      ChatShareSelectionAction(
        label = nativeString("确认"),
        enabled = selectedCount > 0,
        emphasized = true,
        onClick = {
          haptics.tap()
          onConfirm()
        },
      )
    }
  }
}

/** Everything the share card needs; the gateway create happens lazily on link actions. */
internal data class ChatSharePanelPayload(
  val sessionKey: String,
  val messageIds: List<String>,
  val title: String,
  val text: String,
)

/** The two actions that need a share link; the create runs when one is tapped. */
internal enum class ChatShareLinkAction {
  CopyLink,
  WeChat,
}

/**
 * Share card the selection capsule turns into on confirm. Same liquid-glass
 * material and bottom-centre anchor as the capsule, expanding upward from its
 * centre; three fixed actions in a row, icon tile above the label. Nothing is
 * uploaded until a link action is tapped — blank-area dismissal leaves nothing
 * behind, so no delete endpoint is needed.
 */
@Composable
internal fun ChatSharePanel(
  visible: Boolean,
  linkLoading: Boolean = false,
  wechatLoading: Boolean = false,
  onCopyLink: () -> Unit = {},
  onShareWeChat: () -> Unit = {},
  onCopyText: () -> Unit = {},
  modifier: Modifier = Modifier,
  backdrop: Backdrop? = null,
) {
  AnimatedVisibility(
    visible = visible,
    modifier = modifier,
    enter =
      fadeIn(tween(120)) +
        scaleIn(
          initialScale = 0.7f,
          transformOrigin = TransformOrigin(0.5f, 1f),
          animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f, visibilityThreshold = 0.001f),
        ) +
        slideInVertically(
          initialOffsetY = { it / 3 },
          animationSpec = spring(dampingRatio = 0.7f, stiffness = 360f),
        ),
    exit =
      fadeOut(tween(140)) +
        scaleOut(
          targetScale = 0.8f,
          transformOrigin = TransformOrigin(0.5f, 1f),
          animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        ) +
        slideOutVertically(
          targetOffsetY = { it / 3 },
          animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        ),
  ) {
    val colors = ClawTheme.colors
    val dark = colors.canvas.luminance() < 0.5f
    val surface = colors.surfaceRaised
    val cardShape = RoundedCornerShape(20.dp)
    val pressDrag = rememberClawPressDrag(glow = dark)
    Row(
      modifier =
        Modifier
          .testTag("chat-share-panel")
          .clawPressDrag(pressDrag)
          .shadow(
            elevation = ClawCircularButtonDefaults.Elevation,
            shape = cardShape,
            ambientColor = Color.Black.copy(alpha = if (dark) 0.34f else 0.16f),
            spotColor = Color.Black.copy(alpha = if (dark) 0.58f else 0.30f),
            clip = false,
          ).clip(cardShape)
          .clawLiquidButton(backdrop, surface, dark, cardShape)
          .then(pressDrag.modifier)
          .then(pressDrag.gestureModifier)
          .padding(horizontal = 20.dp, vertical = 14.dp),
      horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
      ChatSharePanelAction(
        label = nativeString("复制链接"),
        icon = LucideLinkIcon,
        loading = linkLoading,
        // Both link actions hit the same create endpoint; block the other one
        // while either is in flight so a tap storm cannot create two shares.
        enabled = !linkLoading && !wechatLoading,
        testTag = "chat-share-action-copy-link",
        onClick = onCopyLink,
      )
      ChatSharePanelAction(
        label = nativeString("微信好友"),
        icon = WeChatBrandIcon,
        loading = wechatLoading,
        enabled = !linkLoading && !wechatLoading,
        testTag = "chat-share-action-wechat",
        onClick = onShareWeChat,
      )
      ChatSharePanelAction(
        label = nativeString("复制文本"),
        icon = LucideTextIcon,
        testTag = "chat-share-action-copy-text",
        onClick = onCopyText,
      )
    }
  }
}

@Composable
private fun ChatSharePanelAction(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit,
  enabled: Boolean = true,
  loading: Boolean = false,
  testTag: String? = null,
) {
  val colors = ClawTheme.colors
  val interaction = remember { MutableInteractionSource() }
  Column(
    modifier =
      Modifier
        .then(if (testTag == null) Modifier else Modifier.testTag(testTag))
        .clip(RoundedCornerShape(16.dp))
        .clickable(
          interactionSource = interaction,
          indication = null,
          enabled = enabled,
          role = Role.Button,
          onClick = onClick,
        ).padding(horizontal = 2.dp, vertical = 4.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier
          .size(46.dp)
          .clip(RoundedCornerShape(14.dp))
          .background(colors.text.copy(alpha = if (colors.canvas.luminance() < 0.5f) 0.16f else 0.12f)),
      contentAlignment = Alignment.Center,
    ) {
      if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.size(20.dp),
          strokeWidth = 2.dp,
          color = colors.text,
        )
      } else {
        Icon(
          imageVector = icon,
          contentDescription = label,
          tint = if (enabled) colors.text else colors.textSubtle,
          modifier = Modifier.size(22.dp),
        )
      }
    }
    Spacer(Modifier.height(6.dp))
    Text(
      text = label,
      style = ClawTheme.type.caption.copy(fontSize = 12.sp),
      color = if (enabled) colors.text else colors.textSubtle,
      maxLines = 1,
    )
  }
}

@Composable
private fun ChatShareSelectionAction(
  label: String,
  onClick: () -> Unit,
  enabled: Boolean = true,
  emphasized: Boolean = false,
) {
  val colors = ClawTheme.colors
  val interaction = remember { MutableInteractionSource() }
  Box(
    modifier =
      Modifier
        .height(36.dp)
        .clip(RoundedCornerShape(percent = 50))
        .clickable(
          interactionSource = interaction,
          indication = null,
          enabled = enabled,
          role = Role.Button,
          onClick = onClick,
        ).padding(horizontal = 14.dp),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = label,
      style = ClawTheme.type.label.copy(fontSize = 14.sp),
      color =
        when {
          !enabled -> colors.textSubtle
          emphasized -> colors.primary
          else -> colors.text
        },
      maxLines = 1,
    )
  }
}
