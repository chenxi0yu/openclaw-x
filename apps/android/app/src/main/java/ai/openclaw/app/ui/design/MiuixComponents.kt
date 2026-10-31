package ai.openclaw.app.ui.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HyperOS/MIUI-style settings building blocks.
 *
 * Visual grammar borrowed from MIUI settings: a colored squircle chip leads each
 * row, rows live in borderless rounded groups with inset hairline dividers, and
 * section titles float above their group. Colors come from ClawTheme so the pages
 * follow the app's theme families instead of hardcoding MIUI's blue.
 */
private val MiuixGroupRadius = 20.dp
private val MiuixChipSize = 30.dp
private val MiuixChipRadius = 10.dp
private val MiuixChipIconSize = 18.dp
private val MiuixRowHorizontalPadding = 16.dp
private val MiuixRowVerticalPadding = 12.dp

/** Icon chip background tones. Semantically assigned per row, MIUI-style. */
internal enum class MiuixTone {
  Accent,
  Success,
  Warning,
  Danger,
  Neutral,
}

@Composable
private fun MiuixTone.containerColor(): Color =
  when (this) {
    MiuixTone.Accent -> ClawTheme.colors.primary
    MiuixTone.Success -> ClawTheme.colors.success
    MiuixTone.Warning -> ClawTheme.colors.warning
    MiuixTone.Danger -> ClawTheme.colors.danger
    MiuixTone.Neutral -> ClawTheme.colors.surfacePressed
  }

@Composable
private fun MiuixTone.contentColor(): Color =
  when (this) {
    // Neutral chips keep the app's text color; tinted chips are saturated enough for white glyphs.
    MiuixTone.Neutral -> ClawTheme.colors.text
    else -> Color.White
  }

@Composable
private fun MiuixIconChip(
  icon: ImageVector,
  tone: MiuixTone,
) {
  Box(
    modifier = Modifier.size(MiuixChipSize).clip(RoundedCornerShape(MiuixChipRadius)).background(tone.containerColor()),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = tone.contentColor(),
      modifier = Modifier.size(MiuixChipIconSize),
    )
  }
}

/** Borderless rounded group that hosts MIUI-style rows. */
@Composable
internal fun MiuixGroup(
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Surface(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(MiuixGroupRadius),
    color = ClawTheme.colors.surface,
    contentColor = ClawTheme.colors.text,
  ) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
      content()
    }
  }
}

/** Small muted title floating above a [MiuixGroup]. */
@Composable
internal fun MiuixGroupLabel(
  text: String,
  modifier: Modifier = Modifier,
) {
  Text(
    text = text,
    style = ClawTheme.type.caption,
    color = ClawTheme.colors.textMuted,
    modifier = modifier.padding(horizontal = MiuixRowHorizontalPadding + 4.dp, vertical = 2.dp),
  )
}

/** Inset hairline that visually starts beside the row content, after the icon column. */
@Composable
internal fun MiuixGroupDivider(
  modifier: Modifier = Modifier,
  startInset: Dp = MiuixRowHorizontalPadding + MiuixChipSize + 12.dp,
) {
  Box(
    modifier =
      modifier
        .padding(start = startInset)
        .fillMaxWidth()
        .height(1.dp)
        .background(ClawTheme.colors.border.copy(alpha = 0.6f)),
  )
}

/**
 * MIUI-style circular selection control: a hairline ring that fills with the
 * theme's primary color and a check glyph. The visible disc stays 22dp while the
 * touch target keeps the full 48dp, matching the settings rows' geometry.
 */
@Composable
internal fun MiuixCheckCircle(
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  contentDescription: String? = null,
) {
  val colors = ClawTheme.colors
  val interaction = remember { MutableInteractionSource() }
  val fill by
    animateColorAsState(
      targetValue = if (checked) colors.primary else Color.Transparent,
      animationSpec = tween(140),
      label = "miuix check fill",
    )
  val ring by
    animateColorAsState(
      targetValue = if (checked) colors.primary else colors.borderStrong,
      animationSpec = tween(140),
      label = "miuix check ring",
    )
  Box(
    modifier =
      modifier
        .size(ClawTheme.spacing.touchTarget)
        .toggleable(
          value = checked,
          interactionSource = interaction,
          indication = null,
          enabled = enabled,
          role = Role.Checkbox,
          onValueChange = onCheckedChange,
        )
        .semantics { contentDescription?.let { this.contentDescription = it } },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(22.dp)
          .clip(CircleShape)
          .background(fill)
          .border(1.5.dp, ring, CircleShape),
      contentAlignment = Alignment.Center,
    ) {
      AnimatedVisibility(
        visible = checked,
        enter =
          fadeIn(tween(100)) +
            scaleIn(initialScale = 0.55f, animationSpec = spring(dampingRatio = 0.5f, stiffness = 500f)),
        exit = fadeOut(tween(80)) + scaleOut(targetScale = 0.55f, animationSpec = tween(80)),
      ) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = colors.primaryText,
          modifier = Modifier.size(15.dp),
        )
      }
    }
  }
}

/**
 * Standard MIUI settings row content: chip icon, title with optional subtitle, trailing slot.
 *
 * Rows without an icon still reserve the chip slot so dividers align down the group.
 */

/** MIUI row whose trailing slot is the app's Material switch. */
@Composable
internal fun MiuixSwitchRow(
  title: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  tone: MiuixTone = MiuixTone.Neutral,
  reserveIconSpace: Boolean = true,
  enabled: Boolean = true,
) {
  MiuixRowContent(
    modifier = modifier,
    title = title,
    subtitle = subtitle,
    icon = icon,
    tone = tone,
    reserveIconSpace = reserveIconSpace,
    enabled = enabled,
    onClick = if (enabled) { { onCheckedChange(!checked) } } else null,
    trailing = {
      Switch(
        checked = checked,
        onCheckedChange = if (enabled) onCheckedChange else null,
        enabled = enabled,
      )
    },
  )
}

/**
 * MIUI row showing a current value with a disclosure arrow. While [loading],
 * the trailing slot becomes a spinner and the value disappears.
 */
@Composable
internal fun MiuixValueRow(
  title: String,
  value: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  subtitleColor: Color? = null,
  icon: ImageVector? = null,
  tone: MiuixTone = MiuixTone.Neutral,
  loading: Boolean = false,
  reserveIconSpace: Boolean = true,
  onClick: (() -> Unit)? = null,
  showChevron: Boolean = onClick != null,
  /** Replaces the disclosure chevron, e.g. the up/down glyph of a picker row. */
  trailingIcon: ImageVector? = null,
  /** Applied to the trailing cluster so a morph menu can anchor to it. */
  trailingModifier: Modifier = Modifier,
) {
  MiuixRowContent(
    modifier = modifier,
    title = title,
    subtitle = subtitle,
    subtitleColor = subtitleColor,
    icon = icon,
    tone = tone,
    reserveIconSpace = reserveIconSpace,
    onClick = onClick,
    trailing = {
      if (loading) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = ClawTheme.colors.textMuted,
        )
      } else {
        Row(
          modifier = trailingModifier,
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          if (value.isNotEmpty()) {
            Text(
              text = value,
              style = ClawTheme.type.body.copy(fontSize = 14.sp),
              color = ClawTheme.colors.textMuted,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
          }
          if (showChevron || trailingIcon != null) {
            Icon(
              imageVector = trailingIcon ?: Icons.AutoMirrored.Filled.KeyboardArrowRight,
              contentDescription = null,
              tint = ClawTheme.colors.textSubtle,
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }
    },
  )
}

private val MiuixChipToTextSpacing = 12.dp

/**
 * MIUI row that hosts an inline editable value: label above, borderless field below.
 *
 * The field carries no chrome of its own because the group is the visual container;
 * monospace keeps URLs and match rules readable while typing.
 */
@Composable
internal fun MiuixInputRow(
  title: String,
  value: String,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  icon: ImageVector? = null,
  tone: MiuixTone = MiuixTone.Neutral,
  placeholder: String = "",
  enabled: Boolean = true,
  monospace: Boolean = false,
  keyboardType: KeyboardType = KeyboardType.Uri,
  autoCorrectEnabled: Boolean = false,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = MiuixRowHorizontalPadding, vertical = MiuixRowVerticalPadding),
    verticalAlignment = Alignment.Top,
    horizontalArrangement = Arrangement.spacedBy(MiuixChipToTextSpacing),
  ) {
    if (icon != null) {
      MiuixIconChip(icon = icon, tone = tone)
    } else {
      Spacer(modifier = Modifier.width(MiuixChipSize))
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = ClawTheme.type.body,
          color = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
        )
        if (!subtitle.isNullOrBlank()) {
          Text(
            text = subtitle,
            style = ClawTheme.type.caption,
            color = ClawTheme.colors.textMuted,
          )
        }
      }
      Box(modifier = Modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
          Text(
            text = placeholder,
            style = ClawTheme.type.body,
            color = ClawTheme.colors.textSubtle,
          )
        }
        BasicTextField(
          value = value,
          onValueChange = onValueChange,
          enabled = enabled,
          modifier = Modifier.fillMaxWidth(),
          textStyle =
            ClawTheme.type.body.copy(
              color = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
              fontFamily = if (monospace) FontFamily.Monospace else null,
            ),
          cursorBrush = SolidColor(ClawTheme.colors.text),
          keyboardOptions = KeyboardOptions(keyboardType = keyboardType, autoCorrectEnabled = autoCorrectEnabled),
          singleLine = true,
        )
      }
    }
  }
}

@Composable
private fun MiuixRowContent(
  title: String,
  modifier: Modifier = Modifier,
  subtitle: String? = null,
  subtitleColor: Color? = null,
  icon: ImageVector? = null,
  tone: MiuixTone = MiuixTone.Neutral,
  enabled: Boolean = true,
  reserveIconSpace: Boolean = true,
  onClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .then(
          if (onClick != null) {
            Modifier.clickable(enabled = enabled, onClick = onClick)
          } else {
            Modifier
          },
        )
        .padding(horizontal = MiuixRowHorizontalPadding, vertical = MiuixRowVerticalPadding),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(MiuixChipToTextSpacing),
  ) {
    when {
      icon != null -> MiuixIconChip(icon = icon, tone = tone)
      // Icon-less rows keep the chip slot only when the group mixes iconised
      // rows and the dividers must align down the page; HyperOS-style info
      // cards turn the slot off to align with plain label/value rows.
      reserveIconSpace -> Spacer(modifier = Modifier.width(MiuixChipSize))
    }
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      // Row typography follows the miuix reference: 17sp medium titles, small
      // muted subtitles.
      Text(
        text = title,
        style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
        color = if (enabled) ClawTheme.colors.text else ClawTheme.colors.textSubtle,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (!subtitle.isNullOrBlank()) {
        Text(
          text = subtitle,
          style = ClawTheme.type.caption,
          color = subtitleColor ?: ClawTheme.colors.textMuted,
          maxLines = 3,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    trailing?.invoke()
  }
}
