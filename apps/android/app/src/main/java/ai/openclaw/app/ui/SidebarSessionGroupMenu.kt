package ai.openclaw.app.ui

import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassMenuDefaults
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawMorphMenuDefaults
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawMorphMenuState
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.LocalClawMorphMenuHost
import ai.openclaw.app.ui.design.clawGlassEdge
import ai.openclaw.app.ui.design.clawMorphTrigger
import ai.openclaw.app.ui.design.clawPressDrag
import ai.openclaw.app.ui.design.rememberClawHaptics
import ai.openclaw.app.ui.design.rememberClawPressDrag
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete as MiuixDelete
import top.yukonga.miuix.kmp.icon.extended.ExpandLess as MiuixExpandLess
import top.yukonga.miuix.kmp.icon.extended.More as MiuixMore
import top.yukonga.miuix.kmp.icon.extended.Rename as MiuixRename

/**
 * Nested card that grows out of a long-pressed session row's group action.
 *
 * Mirrors the simple composer's drill-down (primary card recedes, nested card
 * springs out of the clicked row, its title travels up on an under-damped
 * spring), kept sidebar-local so the composer's accepted motion stays untouched.
 * Only the anchor direction differs: sidebar menus pin top-right, so the nested
 * card seats against the primary card's right edge instead of its bottom edge.
 */
internal data class SidebarDrillHeader(
  val icon: ImageVector,
  val title: String,
  val value: String,
)

private data class SidebarDrillShown(
  val key: String,
  val header: SidebarDrillHeader,
  val body: (@Composable ColumnScope.() -> Unit)?,
)

private val SidebarDrillHeaderFallback = ClawGlassMenuDefaults.ItemHeight
private val SidebarDrillDividerBlock = 15.dp

/** Drill state riding on the sidebar's single morph surface. */
internal class SidebarDrillState(val morph: ClawMorphMenuState) {
  var drill by mutableStateOf<String?>(null)
    private set
  var drillHeader by mutableStateOf<SidebarDrillHeader?>(null)
    private set
  var drillBody by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null)
    private set

  /**
   * Row tops in the overlay's coordinate space, recorded by every drillable row.
   * A plain map on purpose: the primary card resizes every frame while it morphs,
   * so writing snapshot state from layout would recompose mid-animation. Nothing
   * observes these values during layout; the drill reads them once when it starts.
   */
  val rowBounds = mutableMapOf<String, Rect>()

  fun prepareOpen() {
    drill = null
    drillHeader = null
    drillBody = null
    rowBounds.clear()
  }

  fun drillInto(
    key: String,
    header: SidebarDrillHeader,
    body: @Composable ColumnScope.() -> Unit,
  ) {
    if (drill != null) return
    drillHeader = header
    drillBody = body
    drill = key
  }

  /** Back to the primary card; the nested panel collapses with the reverse spring. */
  fun endDrill() {
    if (drill == null) return
    drill = null
    drillHeader = null
    drillBody = null
  }

  fun closeAll() {
    endDrill()
    rowBounds.clear()
    morph.dismiss()
  }
}

/** The sidebar's drill layer, provided next to [LocalClawMorphMenuHost]. */
internal val LocalSidebarDrillHost = compositionLocalOf<SidebarDrillState?> { null }

@Composable
internal fun SidebarDrillOverlay(
  state: SidebarDrillState,
  backdrop: Backdrop?,
  hazeState: HazeState?,
  modifier: Modifier = Modifier,
) {
  val density = LocalDensity.current
  val progress = remember { Animatable(0f) }
  // The clicked row's title travels from its list position to the top of the
  // nested card on its own under-damped spring, so it visibly moves with a
  // settle instead of being swapped in place.
  val titleTravel = remember { Animatable(0f) }
  var shown by remember { mutableStateOf<SidebarDrillShown?>(null) }
  var headerHeightPx by remember { mutableIntStateOf(0) }
  var listHeightPx by remember { mutableIntStateOf(0) }
  var overlayHeightPx by remember { mutableIntStateOf(0) }

  LaunchedEffect(state.drill) {
    val key = state.drill
    val header = state.drillHeader
    if (key != null && header != null) {
      shown = SidebarDrillShown(key, header, state.drillBody)
      headerHeightPx = 0
      listHeightPx = 0
      coroutineScope {
        launch {
          progress.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.68f, stiffness = 320f, visibilityThreshold = 0.001f),
          )
        }
        launch {
          titleTravel.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.55f, stiffness = 260f, visibilityThreshold = 0.001f),
          )
        }
        launch {
          state.morph.surfaceScale.animateTo(
            targetValue = 0.97f,
            animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
          )
        }
      }
    } else if (shown != null) {
      coroutineScope {
        launch {
          progress.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f, visibilityThreshold = 0.001f),
          )
        }
        launch {
          // Under-damped on the way back too: the title dips past its row and
          // settles, matching the opening spring's recoil.
          titleTravel.animateTo(
            targetValue = 0f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 280f, visibilityThreshold = 0.001f),
          )
        }
        launch {
          state.morph.surfaceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = 420f),
          )
        }
      }
      shown = null
    }
  }

  LaunchedEffect(state.morph.expanded) {
    if (!state.morph.expanded) {
      progress.snapTo(0f)
      titleTravel.snapTo(0f)
      state.morph.surfaceScale.snapTo(1f)
      shown = null
      headerHeightPx = 0
      listHeightPx = 0
    }
  }

  Box(modifier = modifier.fillMaxSize().onSizeChanged { overlayHeightPx = it.height }) {
    ClawMorphMenuOverlay(
      state = state.morph,
      onDismissRequest = state::closeAll,
      backdrop = backdrop,
      modifier = Modifier.fillMaxSize(),
    )
    // Composed after the primary overlay so Back unwinds the nested panel
    // before either menu closes.
    if (state.morph.expanded) {
      BackHandler {
        if (state.drill != null) state.endDrill() else state.closeAll()
      }
    }
    val snapshot = shown
    val anchor = state.morph.anchor
    val panelHeight = state.morph.panelHeightPx
    val p = progress.value
    if (snapshot != null && anchor != null && panelHeight > 0 && p > 0.001f) {
      // Sidebar menus pin top-right, so the primary card hangs off the anchor's
      // right edge and top.
      val panelWidthPx = with(density) { state.morph.panelWidth.toPx() }
      val pivotX = state.morph.anchorOrigin.pivotFractionX
      val pivotY = state.morph.anchorOrigin.pivotFractionY
      val panelLeft = anchor.left + pivotX * (anchor.width - panelWidthPx)
      val panelTop = anchor.top + pivotY * (anchor.height - panelHeight)
      val headerPx =
        if (headerHeightPx > 0) headerHeightPx.toFloat() else with(density) { SidebarDrillHeaderFallback.toPx() }
      val dividerPx = with(density) { SidebarDrillDividerBlock.toPx() }
      val minimumNestedHeight = headerPx + dividerPx
      // The nested card continues the clicked row: its header starts on that row
      // and springs 18dp down to its seat, so the title reads as the same line
      // travelling rather than a new panel flashing in. Unlike the composer
      // (whose card grows upward inside a fixed-height sheet) this card is free to
      // run past the primary card's bottom edge, so the list keeps its full height.
      val rowTop = state.rowBounds[snapshot.key]?.top ?: (panelTop + with(density) { 8.dp.toPx() })
      val top = rowTop + with(density) { 18.dp.toPx() } * (1f - titleTravel.value)
      val left = panelLeft + with(density) { 4.dp.toPx() } + with(density) { 6.dp.toPx() } * p
      val availableBelow =
        if (overlayHeightPx > 0) {
          (overlayHeightPx - top - with(density) { 16.dp.toPx() }).coerceAtLeast(minimumNestedHeight)
        } else {
          minimumNestedHeight
        }
      val maxListPx = (availableBelow - minimumNestedHeight).coerceAtLeast(0f)
      val targetHeight =
        (minimumNestedHeight + min(listHeightPx.toFloat(), maxListPx))
          .coerceAtLeast(minimumNestedHeight)
      // Grow from the header alone: starting at the full height popped the card
      // into place before the spring could read as an expansion.
      val height = (headerPx + (targetHeight - headerPx) * p).coerceIn(headerPx, availableBelow)

      SidebarDrillSecondaryCard(
        shown = snapshot,
        state = state,
        backdrop = backdrop,
        hazeState = hazeState,
        progress = p,
        onHeaderHeight = { headerHeightPx = it },
        onListHeight = { listHeightPx = it },
        modifier =
          Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .width(state.morph.panelWidth)
            .height(with(density) { height.toDp() }),
      )
    }
  }
}

@Composable
private fun SidebarDrillSecondaryCard(
  shown: SidebarDrillShown,
  state: SidebarDrillState,
  backdrop: Backdrop?,
  hazeState: HazeState?,
  progress: Float,
  onHeaderHeight: (Int) -> Unit,
  onListHeight: (Int) -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val shape = RoundedCornerShape(ClawMorphMenuDefaults.CornerRadius)
  val pressDrag = rememberClawPressDrag(glow = dark)
  val cardInteraction = remember { MutableInteractionSource() }
  val contentAlpha = ((progress - 0.15f) / 0.45f).coerceIn(0f, 1f)

  Box(
    modifier =
      modifier
        // Fade with the growth spring: the card's height already animates, but the
        // glass would otherwise be fully opaque on the first frame, which read as
        // the panel flashing in rather than expanding.
        .graphicsLayer { alpha = (progress * 3f).coerceIn(0f, 1f) }
        .clawPressDrag(pressDrag)
        .clip(shape)
        .sidebarDrillGlass(backdrop = backdrop, hazeState = hazeState, shape = shape, dark = dark)
        // Empty card areas must not fall through to the primary card's rows.
        .clickable(interactionSource = cardInteraction, indication = null, onClick = {})
        .then(pressDrag.modifier)
        .then(pressDrag.gestureModifier),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      SidebarDrillRow(
        icon = shown.header.icon,
        title = shown.header.title,
        value = shown.header.value,
        enabled = true,
        clickable = true,
        trailing = MiuixIcons.Medium.MiuixExpandLess,
        onClick = state::endDrill,
        modifier = Modifier.onSizeChanged { onHeaderHeight(it.height) },
      )
      SidebarDrillDivider()
      Column(
        modifier =
          Modifier
            .weight(1f)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .graphicsLayer { alpha = contentAlpha },
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxWidth()
              .padding(horizontal = 6.dp, vertical = 4.dp)
              .onSizeChanged { onListHeight(it.height) },
        ) {
          shown.body?.invoke(this)
        }
      }
    }
  }
}

@Composable
private fun Modifier.sidebarDrillGlass(
  backdrop: Backdrop?,
  hazeState: HazeState?,
  shape: Shape,
  dark: Boolean,
): Modifier {
  val colors = ClawTheme.colors
  val fill = if (dark) colors.surfaceRaised else Color.White
  val tintAlpha = if (dark) ClawGlassMenuDefaults.TintAlphaDark else ClawGlassMenuDefaults.TintAlphaLight
  val canvas = colors.canvas
  val hazeStyle =
    remember(dark, fill, tintAlpha, canvas) {
      HazeBlurStyle {
        blurEnabled(true)
        blurRadius(20.dp)
        noiseFactor(if (dark) 0.04f else 0.03f)
        backgroundColor(canvas)
        colorEffects(
          listOf(
            HazeColorEffect.tint(fill.copy(alpha = tintAlpha)),
            // Same additive white as the primary card: keeps Haze from greying.
            HazeColorEffect.tint(Color.White.copy(alpha = if (dark) 0.03f else 0.07f), BlendMode.Plus),
          ),
        )
        fallbackColorEffect(HazeColorEffect.tint(fill.copy(alpha = 0.92f)))
      }
    }
  if (hazeState != null) {
    return this
      .clip(shape)
      .background(fill.copy(alpha = tintAlpha))
      .hazeBlur(HazeInput.Sources(hazeState, HazeSourceSelection.All), hazeStyle)
      .clawGlassEdge(cornerRadius = ClawMorphMenuDefaults.CornerRadius, dark = dark)
  }
  val withSurface =
    if (backdrop == null) {
      background(fill.copy(alpha = 0.96f))
    } else {
      // Lambdas must be stable: a new identity makes drawBackdrop rebuild its
      // RenderEffect, which read as the blur flashing on every frame.
      val shapeLambda = remember(shape) { { shape } }
      val effects: com.kyant.backdrop.BackdropEffectScope.() -> Unit =
        remember {
          {
            vibrancy()
            blur(ClawGlassMenuDefaults.BlurRadius.toPx())
            lens(8.dp.toPx(), 24.dp.toPx())
          }
        }
      val surface: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit =
        remember(fill, tintAlpha) { { drawRect(fill.copy(alpha = tintAlpha)) } }
      drawBackdrop(
        backdrop = backdrop,
        shape = shapeLambda,
        effects = effects,
        onDrawSurface = surface,
      )
    }
  return withSurface.clawGlassEdge(cornerRadius = ClawMorphMenuDefaults.CornerRadius, dark = dark)
}

/** Nested-card header row: icon + title, the current value, and a back chevron. */
@Composable
private fun SidebarDrillRow(
  icon: ImageVector,
  title: String,
  value: String,
  enabled: Boolean,
  clickable: Boolean,
  trailing: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val haptics = rememberClawHaptics()
  val rowInteraction = remember { MutableInteractionSource() }
  val rowClick =
    if (clickable) {
      Modifier.clickable(
        interactionSource = rowInteraction,
        indication = null,
        role = Role.Button,
      ) {
        haptics.tap()
        onClick()
      }
    } else {
      Modifier
    }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = ClawGlassMenuDefaults.ItemHeight)
        .then(rowClick)
        .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (enabled) colors.text else colors.textSubtle,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = title,
      style = ClawTheme.type.body.copy(fontSize = 14.sp),
      color = if (enabled) colors.text else colors.textSubtle,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = ClawTheme.type.caption,
      color = colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 128.dp),
    )
    Icon(
      imageVector = trailing,
      contentDescription = null,
      tint = colors.textSubtle,
      modifier = Modifier.size(16.dp),
    )
  }
}

/** Icon + label row used for "new group" and other plain actions. */
@Composable
internal fun SidebarDrillActionRow(
  icon: ImageVector,
  label: String,
  clickable: Boolean,
  onClick: () -> Unit,
) {
  val haptics = rememberClawHaptics()
  val rowInteraction = remember { MutableInteractionSource() }
  val rowClick =
    if (clickable) {
      Modifier.clickable(
        interactionSource = rowInteraction,
        indication = null,
        role = Role.Button,
      ) {
        haptics.tap()
        onClick()
      }
    } else {
      Modifier
    }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = ClawGlassMenuDefaults.ItemHeight)
        .then(rowClick)
        .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = ClawTheme.colors.text,
      modifier = Modifier.size(18.dp),
    )
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp),
      color = ClawTheme.colors.text,
      maxLines = 1,
    )
  }
}

/** Selectable option row inside a drill body; the current choice carries a check. */
@Composable
internal fun SidebarDrillOptionRow(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val colors = ClawTheme.colors
  val haptics = rememberClawHaptics()
  val optionInteraction = remember { MutableInteractionSource() }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .heightIn(min = ClawGlassMenuDefaults.ItemHeight)
        .clip(RoundedCornerShape(ClawGlassMenuDefaults.ItemShapeRadius))
        .clickable(
          interactionSource = optionInteraction,
          indication = null,
          role = Role.Button,
        ) {
          haptics.tap()
          onClick()
        }
        .background(if (selected) colors.accentSoft else Color.Transparent)
        .padding(horizontal = 12.dp, vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 14.sp),
      color = if (selected) colors.primary else colors.text,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = nativeString("Selected"),
        tint = colors.primary,
        modifier = Modifier.size(16.dp),
      )
    }
  }
}

@Composable
internal fun SidebarDrillDivider() {
  Box(
    modifier =
      Modifier
        .padding(horizontal = 18.dp, vertical = 7.dp)
        .fillMaxWidth()
        .height(1.dp)
        .background(ClawTheme.colors.border.copy(alpha = 0.6f)),
  )
}

internal fun Modifier.sidebarDrillRowBounds(
  host: () -> LayoutCoordinates?,
  onBounds: (Rect) -> Unit,
): Modifier =
  onGloballyPositioned { coords ->
    val hostCoords = host()
    val topLeft =
      if (hostCoords != null && hostCoords.isAttached && coords.isAttached) {
        hostCoords.localPositionOf(coords, Offset.Zero)
      } else {
        coords.positionInRoot()
      }
    onBounds(Rect(topLeft, Size(coords.size.width.toFloat(), coords.size.height.toFloat())))
  }

/**
 * Recent-list group header. Collapsing lives on the right (chevron), with the
 * group's own actions (rename/delete) in a morph card opened from the button
 * beside it. Structural sections ("Ungrouped") only collapse.
 */
@Composable
internal fun SidebarGroupHeader(
  title: String,
  canManage: Boolean,
  expanded: Boolean,
  palette: SidebarPalette,
  onToggle: () -> Unit,
  onRename: () -> Unit,
  onDelete: () -> Unit,
) {
  val haptics = rememberClawHaptics()
  val menuTrigger = clawMorphTrigger(LocalClawMorphMenuHost.current)
  Row(
    modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp).padding(start = 24.dp, end = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Row(
      modifier =
        Modifier
          .weight(1f)
          .heightIn(min = 40.dp)
          .clip(RoundedCornerShape(ClawGlassMenuDefaults.CornerRadius))
          .clickable(role = Role.Button, onClick = onToggle)
          .padding(horizontal = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = title,
        style = ClawTheme.type.caption.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
        color = palette.muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    if (canManage) {
      // A plain clickable, not IconButton: the Material control enforces a 48dp
      // touch box and would stretch this caption row taller than the list rows.
      Box(
        modifier =
          menuTrigger.modifier
            .size(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(
              interactionSource = remember { MutableInteractionSource() },
              indication = null,
              role = Role.Button,
            ) {
              haptics.tap()
              menuTrigger.open(panelWidth = 200.dp) {
                ClawGlassMenuItem(
                  label = nativeString("Rename"),
                  icon = MiuixIcons.Medium.MiuixRename,
                  onClick = onRename,
                )
                ClawGlassMenuItem(
                  label = nativeString("Delete"),
                  icon = MiuixIcons.Medium.MiuixDelete,
                  danger = true,
                  onClick = onDelete,
                )
              }
            },
        contentAlignment = Alignment.Center,
      ) {
        Icon(
          imageVector = MiuixIcons.Medium.MiuixMore,
          contentDescription = nativeString("Group actions"),
          tint = palette.muted,
          modifier = Modifier.size(16.dp),
        )
      }
    }
    Box(
      modifier =
        Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(20.dp))
          .clickable(role = Role.Button, onClick = onToggle),
      contentAlignment = Alignment.Center,
    ) {
      SidebarChevron(expanded = expanded, palette = palette)
    }
  }
}