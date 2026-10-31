package ai.openclaw.app.ui.chat

import ai.openclaw.app.GatewayModelSummary
import ai.openclaw.app.chat.ChatThinkingLevelOption
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.design.ClawGlassMenuDefaults
import ai.openclaw.app.ui.design.ClawMorphMenuDefaults
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawMorphMenuState
import ai.openclaw.app.ui.design.ClawMorphTrigger
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.LucideCameraIcon
import ai.openclaw.app.ui.design.clawGlassEdge
import ai.openclaw.app.ui.design.clawPressDrag
import ai.openclaw.app.ui.design.rememberClawPressDrag
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import ai.openclaw.app.ui.design.rememberClawHaptics
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
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.graphics.TransformOrigin
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeSourceSelection
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ExpandLess as MiuixExpandLess
import top.yukonga.miuix.kmp.icon.extended.File as MiuixFile
import top.yukonga.miuix.kmp.icon.extended.Layers as MiuixLayers
import top.yukonga.miuix.kmp.icon.extended.MindMap as MiuixMindMap
import top.yukonga.miuix.kmp.icon.extended.Photos as MiuixPhotos

/**
 * Turns the ＋ disc itself into the card: it scales from the button's bounds to
 * the panel's final rect, pivoting on the bottom-left corner the overlay pins
 * the glass to, and only fades out at the very end when the real glass takes
 * over. The header capsule never needs this (its trigger already has the
 * panel's width); a 48dp disc would otherwise just blink out.
 */
internal fun Modifier.simpleComposerButtonMorph(
  trigger: ClawMorphTrigger,
  panelWidth: Dp,
): Modifier =
  graphicsLayer {
    if (trigger.state.owner !== trigger) return@graphicsLayer
    val state = trigger.state
    val fraction = state.fraction.value
    if (fraction <= 0f) return@graphicsLayer
    val panelWidthPx = panelWidth.toPx()
    val panelHeightPx = if (state.panelHeightPx > 0) state.panelHeightPx.toFloat() else size.height
    transformOrigin = TransformOrigin(0f, 1f)
    scaleX = 1f + (panelWidthPx / size.width - 1f) * fraction
    scaleY = 1f + (panelHeightPx / size.height - 1f) * fraction
    // The header trigger's exit recoil: only the overshoot below zero moves, and
    // it travels toward the collapsed corner, so closing springs back into the
    // disc instead of stopping dead.
    val pulse = (-state.settleBounce.value).coerceAtLeast(0f)
    val bouncePx = pulse * ClawMorphMenuDefaults.BounceTravelDp.toPx()
    translationX = -bouncePx
    translationY = bouncePx
    // Keep the disc through the growth spring's overshoot so the swell visibly
    // settles past the card's rect, then hand over to the crisp glass.
    alpha = 1f - ((fraction - 0.62f) / 0.33f).coerceIn(0f, 1f)
  }

/** Fades the ＋ glyph before its disc swells into the card. */
internal fun Modifier.simpleComposerGlyphFade(trigger: ClawMorphTrigger): Modifier =
  graphicsLayer {
    if (trigger.state.owner !== trigger) return@graphicsLayer
    alpha = 1f - (trigger.state.fraction.value * 4f).coerceIn(0f, 1f)
  }

/** Wide enough to read as its own sheet, not a header dropdown. */
internal val SimpleComposerMenuPanelWidth = 280.dp

private val SimpleComposerMenuRowMinHeight = 64.dp
private val SimpleComposerMenuActionMinHeight = 56.dp
private val SimpleComposerMenuHeaderFallback = 64.dp
private val SimpleComposerMenuDividerBlock = 15.dp

internal enum class SimpleComposerMenuDrill {
  Model,
  Thinking,
}

internal data class SimpleComposerMenuDrillHeader(
  val icon: ImageVector,
  val title: String,
  val value: String,
)

private data class SimpleComposerMenuDrillShown(
  val kind: SimpleComposerMenuDrill,
  val header: SimpleComposerMenuDrillHeader,
  val body: (@Composable ColumnScope.() -> Unit)?,
)

/**
 * State for the simple bar's ＋ card: the primary card is the shared morph
 * surface, while drilling owns a second animation (primary recession + nested
 * panel growth). Kept outside ChatComposer because the pane-level overlay draws
 * both cards above the transcript.
 */
internal class SimpleComposerMenuState {
  val morph = ClawMorphMenuState()

  var drill by mutableStateOf<SimpleComposerMenuDrill?>(null)
    private set
  var drillHeader by mutableStateOf<SimpleComposerMenuDrillHeader?>(null)
    private set
  var drillBody by mutableStateOf<(@Composable ColumnScope.() -> Unit)?>(null)
    private set
  val rowBounds = mutableStateMapOf<SimpleComposerMenuDrill, Rect>()


  /**
   * Row bounds are only recorded once the open animation has settled. Writing
   * them from onGloballyPositioned during the morph mutated snapshot state on
   * every layout pass (the panel resizes each frame), which forced extra
   * composition work mid-animation.
   */
  var rowsMeasured by mutableStateOf(false)
    private set

  fun enableRowMeasurement() {
    rowsMeasured = true
  }


  fun prepareOpen() {
    rowsMeasured = false
    drill = null
    drillHeader = null
    drillBody = null
    rowBounds.clear()
  }

  fun drillInto(
    kind: SimpleComposerMenuDrill,
    header: SimpleComposerMenuDrillHeader,
    body: @Composable ColumnScope.() -> Unit,
  ) {
    if (drill != null) return
    drillHeader = header
    drillBody = body
    drill = kind
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

@Composable
internal fun SimpleComposerMenuOverlay(
  state: SimpleComposerMenuState,
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
  var shown by remember { mutableStateOf<SimpleComposerMenuDrillShown?>(null) }
  var headerHeightPx by remember { mutableIntStateOf(0) }
  var listHeightPx by remember { mutableIntStateOf(0) }

  LaunchedEffect(state.drill) {
    val kind = state.drill
    val header = state.drillHeader
    if (kind != null && header != null) {
      shown = SimpleComposerMenuDrillShown(kind, header, state.drillBody)
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
    if (state.morph.expanded) {
      kotlinx.coroutines.delay(380)
      state.enableRowMeasurement()
    } else {
      progress.snapTo(0f)
      titleTravel.snapTo(0f)
      state.morph.surfaceScale.snapTo(1f)
      shown = null
      headerHeightPx = 0
      listHeightPx = 0
    }
  }

  Box(modifier = modifier.fillMaxSize()) {
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
      val panelTop = anchor.top + anchor.height - panelHeight
      val panelBottom = panelTop + panelHeight
      val fallbackRowTop = panelTop + with(density) { 8.dp.toPx() }
      val rowTop = state.rowBounds[snapshot.kind]?.top ?: fallbackRowTop
      val headerPx =
        if (headerHeightPx > 0) headerHeightPx.toFloat() else with(density) { SimpleComposerMenuHeaderFallback.toPx() }
      val dividerPx = with(density) { SimpleComposerMenuDividerBlock.toPx() }
      val minimumNestedHeight = headerPx + dividerPx
      // Starts exactly on the clicked row (so the title is continuous) and
      // springs up to its new seat with the bounce above. A row near the
      // panel's bottom edge (or a panel caught mid-animation) can leave less
      // room than the nested header needs; lift the card then instead of
      // constructing an empty height range.
      val top =
        min(
          rowTop - with(density) { 18.dp.toPx() } * titleTravel.value,
          panelBottom - minimumNestedHeight,
        )
      val left = anchor.left + with(density) { 4.dp.toPx() } + with(density) { 6.dp.toPx() } * p
      val maxHeight = min(panelBottom - top, panelHeight.toFloat()).coerceAtLeast(0f)
      val maxListPx = (maxHeight - minimumNestedHeight).coerceAtLeast(0f)
      val minNestedHeight = minOf(minimumNestedHeight, maxHeight)
      val targetHeight =
        (minimumNestedHeight + min(listHeightPx.toFloat(), maxListPx))
          .coerceAtMost(maxHeight)
          .coerceAtLeast(minNestedHeight)
      val height = (headerPx + (targetHeight - headerPx) * p).coerceIn(minOf(headerPx, maxHeight), maxHeight)

      SimpleComposerMenuSecondaryCard(
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
private fun SimpleComposerMenuSecondaryCard(
  shown: SimpleComposerMenuDrillShown,
  state: SimpleComposerMenuState,
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
        .clawPressDrag(pressDrag)
        .clip(shape)
        .simpleMenuGlass(backdrop = backdrop, hazeState = hazeState, shape = shape, dark = dark)
        // Empty card areas must not fall through to the primary card's rows.
        .clickable(interactionSource = cardInteraction, indication = null, onClick = {})
        .then(pressDrag.modifier)
        .then(pressDrag.gestureModifier),
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      SimpleComposerMenuDrillRow(
        icon = shown.header.icon,
        title = shown.header.title,
        value = shown.header.value,
        enabled = true,
        clickable = true,
        trailing = MiuixIcons.Medium.MiuixExpandLess,
        onClick = state::endDrill,
        modifier = Modifier.onSizeChanged { onHeaderHeight(it.height) },
      )
      SimpleComposerMenuDivider()
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
private fun Modifier.simpleMenuGlass(
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

@Composable
internal fun SimpleComposerMenuPrimaryContent(
  state: SimpleComposerMenuState,
  modelLabel: String,
  modelEnabled: Boolean,
  modelBody: @Composable ColumnScope.() -> Unit,
  thinkingLabel: String,
  thinkingEnabled: Boolean,
  thinkingBody: @Composable ColumnScope.() -> Unit,
  onCamera: () -> Unit,
  onPhotos: () -> Unit,
  onFiles: () -> Unit,
) {
  val interactive = state.drill == null
  SimpleComposerMenuDrillRow(
    icon = MiuixIcons.Medium.MiuixLayers,
    title = "模型选择",
    value = modelLabel,
    enabled = modelEnabled,
    clickable = interactive && modelEnabled,
    trailing = Icons.Default.UnfoldMore,
    onClick = {
      state.drillInto(
        kind = SimpleComposerMenuDrill.Model,
        header = SimpleComposerMenuDrillHeader(MiuixIcons.Medium.MiuixLayers, "模型选择", modelLabel),
        body = modelBody,
      )
    },
    modifier =
      Modifier.simpleMenuRowBounds(
        host = { state.morph.hostCoords },
        onBounds = { if (state.rowsMeasured) state.rowBounds[SimpleComposerMenuDrill.Model] = it },
      ),
  )
  SimpleComposerMenuDrillRow(
    icon = MiuixIcons.Medium.MiuixMindMap,
    title = "思考等级",
    value = thinkingLabel,
    enabled = thinkingEnabled,
    clickable = interactive && thinkingEnabled,
    trailing = Icons.Default.UnfoldMore,
    onClick = {
      state.drillInto(
        kind = SimpleComposerMenuDrill.Thinking,
        header = SimpleComposerMenuDrillHeader(MiuixIcons.Medium.MiuixMindMap, "思考等级", thinkingLabel),
        body = thinkingBody,
      )
    },
    modifier =
      Modifier.simpleMenuRowBounds(
        host = { state.morph.hostCoords },
        onBounds = { if (state.rowsMeasured) state.rowBounds[SimpleComposerMenuDrill.Thinking] = it },
      ),
  )
  SimpleComposerMenuDivider()
  SimpleComposerMenuActionRow(
    icon = LucideCameraIcon,
    label = "相机",
    clickable = interactive,
    onClick = onCamera,
  )
  SimpleComposerMenuActionRow(
    icon = MiuixIcons.Medium.MiuixPhotos,
    label = "图片",
    clickable = interactive,
    onClick = onPhotos,
  )
  SimpleComposerMenuActionRow(
    icon = MiuixIcons.Medium.MiuixFile,
    label = "文件",
    clickable = interactive,
    onClick = onFiles,
  )
}

@Composable
private fun SimpleComposerMenuDrillRow(
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
        .heightIn(min = SimpleComposerMenuRowMinHeight)
        .then(rowClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = if (enabled) colors.text else colors.textSubtle,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = title,
      style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      color = if (enabled) colors.text else colors.textSubtle,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    Text(
      text = value,
      style = ClawTheme.type.caption.copy(fontSize = 14.sp),
      color = colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.widthIn(max = 96.dp),
    )
    Icon(
      imageVector = trailing,
      contentDescription = null,
      tint = colors.textSubtle,
      modifier = Modifier.size(16.dp),
    )
  }
}

@Composable
private fun SimpleComposerMenuActionRow(
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
        .heightIn(min = SimpleComposerMenuActionMinHeight)
        .then(rowClick)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = ClawTheme.colors.text,
      modifier = Modifier.size(22.dp),
    )
    Text(
      text = label,
      style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      color = ClawTheme.colors.text,
      maxLines = 1,
    )
  }
}

@Composable
private fun SimpleComposerMenuDivider() {
  Box(
    modifier =
      Modifier
        .padding(horizontal = 18.dp, vertical = 7.dp)
        .fillMaxWidth()
        .height(1.dp)
        .background(ClawTheme.colors.border.copy(alpha = 0.6f)),
  )
}

@Composable
internal fun SimpleComposerModelOptions(
  models: List<GatewayModelSummary>,
  selectedRef: String?,
  onSelect: (String) -> Unit,
) {
  if (models.isEmpty()) {
    Text(
      text = "暂无可选模型",
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textMuted,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
    )
    return
  }
  models.forEach { model ->
    val ref = model.providerQualifiedRef()
    SimpleComposerMenuOptionRow(
      label = model.name.ifBlank { ref.substringAfterLast('/') },
      selected = selectedRef?.equals(ref, ignoreCase = true) == true,
      enabled = model.available != false,
      onClick = { onSelect(ref) },
    )
  }
}

@Composable
internal fun SimpleComposerThinkingOptions(
  options: List<ChatThinkingLevelOption>,
  selectedId: String,
  onSelect: (String) -> Unit,
) {
  options.sortedBy(::chatThinkingLevelRank).forEach { option ->
    SimpleComposerMenuOptionRow(
      label = chatThinkingOptionLabel(option),
      selected = option.id.trim().equals(selectedId.trim(), ignoreCase = true),
      enabled = true,
      onClick = { onSelect(option.id) },
    )
  }
}

/** Gateway levels arrive in any order; the card always reads off → max. */
private val chatThinkingOrder = listOf("off", "minimal", "low", "medium", "high", "xhigh", "adaptive", "max")

private fun chatThinkingLevelRank(option: ChatThinkingLevelOption): Int {
  val rank = chatThinkingOrder.indexOf(option.id.trim().lowercase())
  return if (rank < 0) chatThinkingOrder.size else rank
}

@Composable
private fun SimpleComposerMenuOptionRow(
  label: String,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
) {
  val colors = ClawTheme.colors
  val haptics = rememberClawHaptics()
  val optionInteraction = remember { MutableInteractionSource() }
  val optionClick =
    if (enabled) {
      Modifier.clickable(
        interactionSource = optionInteraction,
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
        .heightIn(min = 52.dp)
        .clip(RoundedCornerShape(14.dp))
        .then(optionClick)
        .background(if (selected) colors.accentSoft else Color.Transparent)
        .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Text(
      text = label,
      style =
        ClawTheme.type.body.copy(
          fontSize = 16.sp,
          fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        ),
      color =
        when {
          !enabled -> colors.textSubtle
          selected -> colors.primary
          else -> colors.text
        },
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f),
    )
    if (selected) {
      Icon(
        imageVector = Icons.Default.Check,
        contentDescription = nativeString("Selected"),
        tint = colors.primary,
        modifier = Modifier.size(18.dp),
      )
    }
  }
}

private fun Modifier.simpleMenuRowBounds(
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
