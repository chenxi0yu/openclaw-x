package ai.openclaw.app.ui

import ai.openclaw.app.GatewayAgentSummary
import ai.openclaw.app.selectableAgents
import ai.openclaw.app.ui.design.ClawAgentAvatar
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.LocalClawMorphMenuHost
import ai.openclaw.app.ui.design.agentAvatarSource
import ai.openclaw.app.ui.design.clawMorphTrigger
import ai.openclaw.app.ui.design.rememberClawHaptics
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal data class AgentPickerState(
  val agents: List<GatewayAgentSummary>,
  val selectedAgentId: String?,
) {
  val selected: GatewayAgentSummary?
    get() = agents.firstOrNull { it.id == selectedAgentId }
}

internal fun agentPickerState(
  agents: List<GatewayAgentSummary>,
  selectedAgentId: String?,
  fallbackToFirst: Boolean = true,
): AgentPickerState {
  val selectable = agents.selectableAgents().distinctBy(GatewayAgentSummary::id)
  val requestedAgentId = selectedAgentId?.trim()?.takeIf(String::isNotEmpty)
  val effectiveAgentId =
    selectable
      .firstOrNull { it.id == requestedAgentId }
      ?.id
      ?: if (fallbackToFirst) selectable.firstOrNull()?.id else requestedAgentId
  return AgentPickerState(
    agents = selectable,
    selectedAgentId = effectiveAgentId,
  )
}

internal fun agentPickerName(agent: GatewayAgentSummary): String = agent.name?.trim()?.takeIf(String::isNotEmpty) ?: agent.id

internal fun agentPickerLabel(state: AgentPickerState): String? = state.selected?.let(::agentPickerName) ?: state.selectedAgentId

@Composable
internal fun AgentPicker(
  state: AgentPickerState,
  onSelectAgent: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val selectedAgentId = state.selectedAgentId ?: return
  val label = agentPickerLabel(state) ?: return
  val morphTrigger = clawMorphTrigger(LocalClawMorphMenuHost.current)
  val haptics = rememberClawHaptics()
  val interaction = remember { MutableInteractionSource() }

  Box(modifier = modifier) {
    Row(
      modifier =
        Modifier
          .widthIn(max = 220.dp)
          .height(40.dp)
          .clip(RoundedCornerShape(percent = 50))
          // The whole capsule is the morph trigger: it fades and glides into the
          // panel while the glass grows out of these bounds.
          .then(morphTrigger.modifier)
          .clickable(
            interactionSource = interaction,
            indication = null,
            role = Role.Button,
          ) {
            haptics.tap()
            morphTrigger.open(
              origin = TransformOrigin(0f, 0f),
              panelWidth = 240.dp,
            ) {
              state.agents.forEach { agent ->
                ClawGlassMenuItem(
                  label = agentPickerName(agent),
                  leading = { AgentPickerAvatar(agent = agent, size = 26) },
                  selected = agent.id == selectedAgentId,
                  onClick = { onSelectAgent(agent.id) },
                )
              }
            }
          }
          .padding(start = 6.dp, end = 12.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      state.selected?.let { selected -> AgentPickerAvatar(agent = selected, size = 28) }
      Text(
        text = label,
        modifier = Modifier.weight(1f),
        style = ClawTheme.type.body.copy(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
        color = ClawTheme.colors.text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
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

@Composable
private fun AgentPickerAvatar(
  agent: GatewayAgentSummary,
  size: Int,
) {
  val avatarSize = size.dp
  ClawAgentAvatar(source = agentAvatarSource(agent), size = avatarSize) {
    Box(
      modifier = Modifier.size(avatarSize).clip(CircleShape).background(ClawTheme.colors.surfacePressed),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = agent.emoji?.trim()?.takeIf(String::isNotEmpty) ?: agentPickerName(agent).take(1).uppercase(),
        style = ClawTheme.type.caption,
      )
    }
  }
}
