package ai.openclaw.app.ui

import ai.openclaw.app.R
import ai.openclaw.app.chat.ChatSessionEntry
import ai.openclaw.app.chat.isSessionRunActive
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassMenu
import ai.openclaw.app.ui.design.ClawGlassMenuDefaults
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.rememberClawHaptics
import ai.openclaw.app.ui.design.sessionColor
import ai.openclaw.app.ui.design.sessionColorStripe
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CancellationException
import kotlin.math.abs

@Composable
internal fun sidebarSearchLabel(): String = nativeString("Search sessions")

@Composable
internal fun SidebarSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  palette: SidebarPalette,
  modifier: Modifier = Modifier,
) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.fillMaxWidth().testTag("sidebar-search"),
    singleLine = true,
    label = { Text(sidebarSearchLabel()) },
    leadingIcon = {
      Icon(
        imageVector = Icons.Default.Search,
        contentDescription = null,
      )
    },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(48.dp)) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = nativeString("Clear session search"),
          )
        }
      }
    },
    colors =
      OutlinedTextFieldDefaults.colors(
        focusedTextColor = palette.text,
        unfocusedTextColor = palette.text,
        focusedContainerColor = palette.elevated,
        unfocusedContainerColor = palette.elevated,
        cursorColor = palette.text,
        focusedBorderColor = ClawTheme.colors.primary,
        unfocusedBorderColor = palette.hairline,
        focusedLabelColor = palette.text,
        unfocusedLabelColor = palette.muted,
        focusedLeadingIconColor = palette.text,
        unfocusedLeadingIconColor = palette.muted,
        focusedTrailingIconColor = palette.text,
        unfocusedTrailingIconColor = palette.muted,
      ),
  )
}

@Composable
internal fun SidebarSectionTitle(
  label: String,
  palette: SidebarPalette,
  modifier: Modifier = Modifier,
  /**
   * Shows a small spinner after the label. A refresh in flight is a badge next to
   * the heading, never a row of its own: a dedicated "Loading" row above the list
   * appears and disappears again a moment later, which shifts every thread down
   * and back up and reads as a flicker while the drawer is still opening.
   */
  loading: Boolean = false,
) {
  Row(
    modifier = modifier.semantics { heading() }.padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    Text(
      text = label,
      style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
      color = palette.muted,
      maxLines = 1,
    )
    // Fading in place keeps the heading still: the spinner only ever claims space
    // to the right of the text, so nothing below it moves.
    AnimatedVisibility(
      visible = loading,
      enter = fadeIn(animationSpec = tween(durationMillis = 150)),
      exit = fadeOut(animationSpec = tween(durationMillis = 150)),
    ) {
      CircularProgressIndicator(
        modifier = Modifier.size(12.dp),
        color = palette.muted,
        strokeWidth = 1.6.dp,
      )
    }
  }
}

@Composable
internal fun SidebarCollapsibleHeader(
  label: String,
  expanded: Boolean,
  palette: SidebarPalette,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null,
  iconPainter: Painter? = null,
  iconContent: (@Composable () -> Unit)? = null,
  iconTint: Color = palette.text,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 44.dp)
        .clip(RoundedCornerShape(ClawGlassMenuDefaults.CornerRadius))
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Icon(
      imageVector =
        if (expanded) {
          Icons.Default.KeyboardArrowDown
        } else {
          Icons.AutoMirrored.Filled.KeyboardArrowRight
        },
      contentDescription = null,
      tint = palette.muted,
      modifier = Modifier.size(18.dp),
    )
    iconContent?.invoke()
    icon?.let {
      Icon(
        imageVector = it,
        contentDescription = null,
        tint = palette.text,
        modifier = Modifier.size(18.dp),
      )
    }
    iconPainter?.let {
      Icon(
        painter = it,
        contentDescription = null,
        tint = iconTint,
        modifier = Modifier.size(18.dp),
      )
    }
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
      color = palette.text,
      modifier = Modifier.weight(1f),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    trailingContent?.invoke()
  }
}

@Composable
internal fun SidebarActionRow(
  label: String,
  icon: ImageVector,
  palette: SidebarPalette,
  onClick: () -> Unit,
) {
  SidebarRowSurface(selected = null, palette = palette, onClick = onClick) {
    Spacer(modifier = Modifier.size(28.dp))
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
      color = palette.muted,
      modifier = Modifier.weight(1f),
      maxLines = 1,
    )
    Icon(imageVector = icon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(18.dp))
  }
}

@Composable
internal fun SidebarNavigationRow(
  destination: SidebarDestination,
  rowHost: SidebarRowHost,
  selected: Boolean,
  pinned: Boolean? = null,
  palette: SidebarPalette,
  onClick: () -> Unit,
  onMove: (Int) -> Unit,
  onDragActiveChange: (Boolean) -> Unit,
) {
  val thresholdPx = with(LocalDensity.current) { 48.dp.toPx() }
  val haptics = rememberClawHaptics()
  val currentOnMove by rememberUpdatedState(onMove)
  val currentOnDragActiveChange by rememberUpdatedState(onDragActiveChange)
  val pinStateDescription =
    pinned?.let { nativeString(if (it) "Pinned" else "Not pinned") }
  var dragOffset by remember(destination) { mutableFloatStateOf(0f) }
  var dragging by remember(destination) { mutableStateOf(false) }
  var dragGeneration by remember(destination) { mutableLongStateOf(0L) }
  val visualDragging = dragging && dragGeneration == rowHost.generation
  val finishDrag = {
    dragOffset = 0f
    if (dragging) {
      dragging = false
      currentOnDragActiveChange(false)
    }
  }

  Box(
    modifier =
      Modifier
        .fillMaxWidth()
        .zIndex(if (visualDragging) 1f else 0f)
        .graphicsLayer {
          translationY = if (visualDragging) dragOffset else 0f
          scaleX = if (visualDragging) 1.015f else 1f
          scaleY = if (visualDragging) 1.015f else 1f
          shadowElevation = if (visualDragging) 10.dp.toPx() else 0f
        }
        .semantics {
          if (pinStateDescription != null) stateDescription = pinStateDescription
        }.pointerInput(destination, thresholdPx) {
          detectSidebarRowDrag(
            rowHost = rowHost,
            onDragStart = { generation ->
              dragOffset = 0f
              dragGeneration = generation
              dragging = true
              haptics.longPress()
              currentOnDragActiveChange(true)
            },
            onDragEnd = finishDrag,
            onDragCancel = finishDrag,
          ) { change, dragAmount ->
            change.consume()
            dragOffset += dragAmount.y
            if (abs(dragOffset) >= thresholdPx) {
              val direction = if (dragOffset < 0f) -1 else 1
              currentOnMove(direction)
              dragOffset -= direction * thresholdPx
            }
          }
        },
  ) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .clip(RoundedCornerShape(ClawGlassMenuDefaults.CornerRadius))
          .background(if (selected) palette.selection else if (visualDragging) palette.elevated else Color.Transparent)
          .clickable(role = Role.Button, onClick = onClick)
          .padding(horizontal = 12.dp, vertical = 8.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Icon(
        imageVector = destination.icon,
        contentDescription = null,
        modifier = Modifier.size(22.dp),
        tint = palette.text,
      )
      Text(
        text = destination.localizedLabel(),
        style = ClawTheme.type.body.copy(fontSize = 16.sp, lineHeight = 22.sp),
        modifier = Modifier.weight(1f),
        maxLines = 1,
      )
      if (pinned == true) {
        Icon(
          painter = painterResource(R.drawable.ic_web_check),
          contentDescription = nativeString("Pinned"),
          tint = palette.text,
          modifier = Modifier.size(18.dp),
        )
      }
    }
    if (visualDragging) {
      HorizontalDivider(
        color = ClawTheme.colors.primary,
        thickness = 2.dp,
        modifier = Modifier.align(if (dragOffset < 0f) Alignment.TopCenter else Alignment.BottomCenter),
      )
    }
  }
}

internal enum class SidebarSessionActivity {
  Queued,
  Running,
  Unread,
  Failed,
}

internal fun sidebarSessionActivity(
  status: String?,
  lastRunError: String?,
  hasActiveRun: Boolean?,
  unread: Boolean,
  continuing: Boolean = false,
): SidebarSessionActivity? {
  val normalizedStatus = status?.trim()?.lowercase()
  val active = isSessionRunActive(hasActiveRun, normalizedStatus)
  return when {
    !lastRunError.isNullOrBlank() ||
      normalizedStatus == "failed" ||
      normalizedStatus == "timeout" ||
      normalizedStatus == "killed" ||
      normalizedStatus == "error" -> SidebarSessionActivity.Failed

    normalizedStatus == "queued" && active -> SidebarSessionActivity.Queued

    continuing || active -> SidebarSessionActivity.Running

    unread -> SidebarSessionActivity.Unread

    else -> null
  }
}

@Composable
internal fun SidebarSessionActivityIndicator(
  activity: SidebarSessionActivity,
  palette: SidebarPalette,
) {
  when (activity) {
    SidebarSessionActivity.Queued -> {
      Icon(
        imageVector = Icons.Default.HourglassEmpty,
        contentDescription = nativeString("Queued"),
        modifier = Modifier.size(15.dp),
        tint = palette.muted,
      )
    }

    SidebarSessionActivity.Running -> {
      CircularProgressIndicator(
        modifier = Modifier.size(15.dp).clearAndSetSemantics { stateDescription = nativeString("Working") },
        color = ClawTheme.colors.primary,
        strokeWidth = 2.dp,
      )
    }

    SidebarSessionActivity.Unread -> {
      Box(
        modifier =
          Modifier
            .size(7.dp)
            .clip(CircleShape)
            .background(ClawTheme.colors.primary)
            .clearAndSetSemantics { stateDescription = nativeString("Needs attention") },
      )
    }

    SidebarSessionActivity.Failed -> {
      Icon(
        imageVector = Icons.Default.ErrorOutline,
        contentDescription = nativeString("Run failed"),
        modifier = Modifier.size(16.dp),
        tint = ClawTheme.colors.danger,
      )
    }
  }
}

@Composable
internal fun SidebarSessionRow(
  session: ChatSessionEntry,
  selected: Boolean,
  palette: SidebarPalette,
  onClick: () -> Unit,
  menuContent: (@Composable ColumnScope.() -> Unit)? = null,
) {
  val activity =
    sidebarSessionActivity(
      status = session.status,
      lastRunError = session.lastRunError,
      hasActiveRun = session.hasActiveRun,
      unread = session.unread == true,
    )
  val sessionStateDescription =
    when (activity) {
      SidebarSessionActivity.Failed -> nativeString("Run failed")
      SidebarSessionActivity.Queued -> nativeString("Queued")
      SidebarSessionActivity.Running -> nativeString("Working")
      SidebarSessionActivity.Unread -> nativeString("Needs attention")
      null -> nativeString("Selected").takeIf { selected }
    }
  SidebarRowSurface(
    selected = selected,
    stateDescription = sessionStateDescription,
    palette = palette,
    stripeColor = ClawTheme.colors.sessionColor(session.color),
    onClick = onClick,
    menuContent = menuContent,
  ) {
    Column(modifier = Modifier.weight(1f)) {
      Text(
        text = sidebarSessionTitle(session),
        style = ClawTheme.type.body.copy(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        color = palette.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = sidebarSessionSubtitle(session, sessionStateDescription),
        style = ClawTheme.type.caption.copy(fontSize = 12.sp, lineHeight = 16.sp),
        color = palette.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    activity?.let {
      SidebarSessionActivityIndicator(activity = it, palette = palette)
    }
    if (session.pinned == true) {
      Icon(
        imageVector = Icons.Default.PushPin,
        contentDescription = nativeString("Pinned"),
        modifier = Modifier.size(13.dp),
        tint = palette.muted,
      )
    }
  }
}

@Composable
internal fun SidebarRowSurface(
  selected: Boolean?,
  stateDescription: String? = null,
  palette: SidebarPalette,
  enabled: Boolean = true,
  stripeColor: Color? = null,
  onClick: () -> Unit,
  // Supplying a menu is what arms the long-press gesture: rows without actions
  // keep their plain tap behavior and never consume a long press.
  menuContent: (@Composable ColumnScope.() -> Unit)? = null,
  contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
  content: @Composable RowScope.() -> Unit,
) {
  val haptics = rememberClawHaptics()
  var menuExpanded by remember { mutableStateOf(false) }
  val interactionModifier =
    if (menuContent == null) {
      if (selected == null) {
        Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
      } else {
        Modifier.selectable(enabled = enabled, selected = selected, role = Role.Button, onClick = onClick)
      }
    } else {
      // combinedClickable is the only gesture that suppresses the tap after a long
      // press, so selection state moves into semantics instead of Modifier.selectable.
      Modifier.combinedClickable(
        enabled = enabled,
        role = Role.Button,
        onClick = onClick,
        onLongClick = {
          haptics.longPress()
          menuExpanded = true
        },
        onLongClickLabel = nativeString("Session actions"),
      )
    }
  Box(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .clip(RoundedCornerShape(ClawGlassMenuDefaults.CornerRadius))
          .background(if (selected == true) palette.selection else Color.Transparent)
          .sessionColorStripe(stripeColor)
          .then(interactionModifier)
          .then(
            if (selected == null || menuContent == null) {
              Modifier
            } else {
              Modifier.semantics { this.selected = selected }
            },
          ).then(
            if (stateDescription == null) {
              Modifier
            } else {
              Modifier.semantics { this.stateDescription = stateDescription }
            },
          ).padding(contentPadding),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      content = content,
    )
    if (menuContent != null) {
      ClawGlassMenu(
        expanded = menuExpanded,
        onDismissRequest = { menuExpanded = false },
        // A little inset from the row's leading edge so the card reads as a
        // separate layer rather than an extension of the row.
        offset = DpOffset(10.dp, (-6).dp),
      ) {
        menuContent()
      }
    }
  }
}

private suspend fun PointerInputScope.detectSidebarRowDrag(
  rowHost: SidebarRowHost?,
  onDragStart: (Long) -> Unit,
  onDragEnd: () -> Unit,
  onDragCancel: () -> Unit,
  onDrag: (PointerInputChange, Offset) -> Unit,
) {
  awaitEachGesture {
    var claimed = false
    try {
      val down = awaitFirstDown(requireUnconsumed = false)
      val generation = rowHost?.generation ?: 0L
      val longPress = awaitLongPressOrCancellation(down.id)
      if (longPress != null) {
        claimed = generation == (rowHost?.generation ?: 0L)
        if (claimed) onDragStart(generation)
        // Retain consumption through release, even after the row loses mutation authority.
        val ended =
          drag(longPress.id) { change ->
            if (claimed && generation == (rowHost?.generation ?: 0L)) {
              onDrag(change, change.positionChange())
            }
            change.consume()
          }
        if (ended) currentEvent.changes.forEach { if (it.changedToUp()) it.consume() }
        if (claimed) {
          claimed = false
          if (ended && generation == (rowHost?.generation ?: 0L)) onDragEnd() else onDragCancel()
        }
      }
    } catch (cancel: CancellationException) {
      if (claimed) onDragCancel()
      throw cancel
    }
  }
}

internal fun sidebarSessionSubtitle(
  session: ChatSessionEntry,
  activeRunLabel: String?,
  nowMs: Long = System.currentTimeMillis(),
): String =
  sessionListSubtitle(
    session = session,
    fallback = sessionSourceLabel(session.key),
    nowMs = nowMs,
    activeRunLabel = activeRunLabel,
  )
