package ai.openclaw.app.ui

import ai.openclaw.app.ComposerBarStyle
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.ui.chat.ComposerSendArrow
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawGlassTopBarButton
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.ClawTopBarButtonTopInset
import ai.openclaw.app.ui.design.ClawTopBarContentHeight
import ai.openclaw.app.ui.design.MiuixGroup
import ai.openclaw.app.ui.design.MiuixSwitchRow
import ai.openclaw.app.ui.design.MiuixValueRow
import ai.openclaw.app.ui.design.clawBackdropTopBar
import ai.openclaw.app.ui.design.clawGlassEdge
import ai.openclaw.app.ui.design.rememberClawBackdrop
import ai.openclaw.app.ui.design.rememberClawMorphMenuState
import ai.openclaw.app.ui.design.rememberClawMorphTrigger
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add as MiuixAdd
import top.yukonga.miuix.kmp.icon.extended.Mic as MiuixMic

private val ComposerPreviewCardRadius = 20.dp
private val ComposerPreviewBarRadius = 26.dp
private val ComposerPreviewBlur = 8.dp

/**
 * Input-bar style picker. The preview and the per-style settings share one
 * transition so switching the style reads as a single blurred swap: the old
 * bars/settings defocus on their way out while the incoming pair sharpens, and
 * the box height folds or unfolds with the content.
 */
@Composable
internal fun ComposerBarSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  BackHandler(onBack = onBack)

  val style by viewModel.composerBarStyle.collectAsState()
  val liveTalkVisible by viewModel.chatLiveTalkVisible.collectAsState()
  val voiceInputVisible by viewModel.chatVoiceInputVisible.collectAsState()

  val backdrop = rememberClawBackdrop()
  val styleMenuState = rememberClawMorphMenuState()
  val styleTrigger = rememberClawMorphTrigger(styleMenuState)
  val scrollState = rememberScrollState()
  val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val topBarHeight = statusBarHeight + ClawTopBarContentHeight
  val density = LocalDensity.current
  val collapsePx = with(density) { 72.dp.toPx() }
  val chromeThreshold = with(density) { 8.dp.toPx() }
  val scrollProgress by
    remember(scrollState, collapsePx) {
      derivedStateOf { (scrollState.value / collapsePx).coerceIn(0f, 1f) }
    }
  val chromeAlpha by
    animateFloatAsState(
      targetValue = if (scrollState.value > chromeThreshold) 1f else 0f,
      animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "composerBarTopChrome",
    )
  val topTitleAlpha by
    animateFloatAsState(
      targetValue = if (scrollProgress > 0.55f) 1f else 0f,
      animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "composerBarTopTitle",
    )
  val styleTransition = updateTransition(targetState = style, label = "composerBarStyle")

  Box(modifier = Modifier.fillMaxSize()) {
    // Same split as Appearance: the top bar samples this layer, so it stays a
    // sibling. A sampler inside the layer it samples recurses until the render
    // thread overflows.
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .layerBackdrop(backdrop)
          .background(ClawTheme.colors.canvas),
    ) {
      ClawScaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
        contentPadding = PaddingValues(start = 14.dp, top = 0.dp, end = 14.dp, bottom = 0.dp),
      ) {
        Column(
          modifier =
            Modifier
              .fillMaxSize()
              .verticalScroll(scrollState)
              .padding(top = topBarHeight)
              .padding(bottom = 24.dp),
        ) {
          Text(
            text = "输入栏",
            style =
              ClawTheme.type.display.copy(
                fontSize = 30.sp,
                lineHeight = 36.sp,
                fontWeight = FontWeight.SemiBold,
              ),
            color = ClawTheme.colors.text,
            modifier =
              Modifier
                .padding(start = 4.dp, top = 8.dp, bottom = 18.dp)
                .graphicsLayer {
                  alpha = (1f - scrollProgress / 0.6f).coerceIn(0f, 1f)
                  translationY = -scrollProgress * 14.dp.toPx()
                },
          )

          ComposerStylePreviewCard(
            transition = styleTransition,
            modifier = Modifier.fillMaxWidth(),
          )

          Spacer(modifier = Modifier.height(18.dp))

          MiuixGroup {
            MiuixValueRow(
              title = "样式",
              value = style.displayLabel,
              reserveIconSpace = false,
              trailingIcon = Icons.Default.UnfoldMore,
              trailingModifier = styleTrigger.modifier,
              onClick = {
                styleTrigger.open(origin = TransformOrigin(1f, 0f), panelWidth = 200.dp) {
                  ComposerBarStyle.entries.forEach { entry ->
                    ClawGlassMenuItem(
                      label = entry.displayLabel,
                      selected = entry == style,
                      onClick = { viewModel.setComposerBarStyle(entry) },
                    )
                  }
                }
              },
            )
            styleTransition.AnimatedContent(
              transitionSpec = {
                (fadeIn(animationSpec = tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(240))) togetherWith
                  fadeOut(animationSpec = tween(160))
              },
            ) { target ->
              val blurFraction by
                styleTransition.animateFloat(transitionSpec = { tween(220) }, label = "composerBarDetailsBlur") { state ->
                  if (state == target) 0f else 1f
                }
              Box(modifier = Modifier.blur(ComposerPreviewBlur * blurFraction)) {
                when (target) {
                  ComposerBarStyle.Classic -> {
                    Column {
                      MiuixSwitchRow(
                        title = "实时语音显示",
                        subtitle = "关闭后输入框为空时显示发送按钮",
                        checked = liveTalkVisible,
                        onCheckedChange = viewModel::setChatLiveTalkVisible,
                        reserveIconSpace = false,
                      )
                      MiuixSwitchRow(
                        title = "短语音输入显示",
                        subtitle = "显示麦克风，支持听写和语音消息",
                        checked = voiceInputVisible,
                        onCheckedChange = viewModel::setChatVoiceInputVisible,
                        reserveIconSpace = false,
                      )
                    }
                  }

                  ComposerBarStyle.Simple -> {
                    ComposerSimpleDetailsPlaceholder()
                  }
                }
              }
            }
          }
        }
      }
    }

    Box(
      modifier = Modifier.fillMaxWidth().height(topBarHeight).clawBackdropTopBar(backdrop),
    ) {
      ClawGlassTopBarButton(
        contentDescription = "返回",
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
          text = "输入栏",
          style = ClawTheme.type.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
          color = ClawTheme.colors.text,
          modifier =
            Modifier.graphicsLayer {
              alpha = topTitleAlpha
              translationY = (1f - topTitleAlpha) * 8.dp.toPx()
            },
          maxLines = 1,
        )
      }
    }

    // Sibling of the sampled layer, same as the Appearance page.
    ClawMorphMenuOverlay(
      state = styleMenuState,
      onDismissRequest = { styleMenuState.dismiss() },
      backdrop = backdrop,
      modifier = Modifier.fillMaxSize(),
    )
  }
}

/** Simple keeps no tunables yet; the row keeps the group from collapsing to nothing. */
@Composable
private fun ComposerSimpleDetailsPlaceholder() {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(
      text = "更多设置",
      style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
      color = ClawTheme.colors.text,
    )
    Text(
      text = "简约输入栏暂无可调设置",
      style = ClawTheme.type.caption,
      color = ClawTheme.colors.textMuted,
    )
  }
}

@Composable
private fun ComposerStylePreviewCard(
  transition: Transition<ComposerBarStyle>,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val shape = RoundedCornerShape(ComposerPreviewCardRadius)
  val wallpaper =
    Brush.linearGradient(
      0f to colors.primary.copy(alpha = if (dark) 0.20f else 0.10f).compositeOver(colors.canvas),
      0.5f to colors.canvas,
      1f to colors.secondary.copy(alpha = if (dark) 0.14f else 0.06f).compositeOver(colors.canvas),
    )
  Box(
    modifier =
      modifier
        .clip(shape)
        .background(wallpaper)
        .clawGlassEdge(cornerRadius = ComposerPreviewCardRadius, dark = dark)
        .semantics { contentDescription = "输入栏预览" }
        .padding(horizontal = 16.dp, vertical = 16.dp),
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
      PreviewMessageBubble(
        fraction = 0.62f,
        alignEnd = false,
        color = if (dark) Color.White.copy(alpha = 0.10f) else Color.White.copy(alpha = 0.72f),
      )
      PreviewMessageBubble(
        fraction = 0.46f,
        alignEnd = true,
        color = colors.primary.copy(alpha = if (dark) 0.28f else 0.16f),
      )
      Spacer(modifier = Modifier.height(8.dp))
      transition.AnimatedContent(
        transitionSpec = {
          (fadeIn(animationSpec = tween(240)) + scaleIn(initialScale = 0.98f, animationSpec = tween(240))) togetherWith
            fadeOut(animationSpec = tween(160))
        },
      ) { target ->
        val blurFraction by
          transition.animateFloat(transitionSpec = { tween(220) }, label = "composerBarPreviewBlur") { state ->
            if (state == target) 0f else 1f
          }
        Box(modifier = Modifier.blur(ComposerPreviewBlur * blurFraction)) {
          when (target) {
            ComposerBarStyle.Classic -> ClassicComposerPreview()
            ComposerBarStyle.Simple -> SimpleComposerPreview()
          }
        }
      }
    }
  }
}

@Composable
private fun PreviewMessageBubble(
  fraction: Float,
  alignEnd: Boolean,
  color: Color,
) {
  Box(modifier = Modifier.fillMaxWidth(), contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth(fraction)
          .height(14.dp)
          .clip(RoundedCornerShape(7.dp))
          .background(color),
    )
  }
}

@Composable
private fun ClassicComposerPreview() {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val shape = RoundedCornerShape(ComposerPreviewBarRadius)
  Column(
    modifier =
      Modifier
        .fillMaxWidth()
        .clip(shape)
        .background((if (dark) colors.surfaceRaised else Color.White).copy(alpha = if (dark) 0.46f else 0.62f))
        .clawGlassEdge(cornerRadius = ComposerPreviewBarRadius, dark = dark)
        .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
  ) {
    Text(
      text = nativeString("Message OpenClaw"),
      style = ClawTheme.type.caption.copy(fontSize = 13.sp),
      color = colors.textMuted,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
      modifier = Modifier.fillMaxWidth().height(30.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Icon(
        imageVector = MiuixIcons.Medium.MiuixAdd,
        contentDescription = null,
        tint = colors.textMuted,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(8.dp))
      Text(
        text = "自动",
        style = ClawTheme.type.caption.copy(fontSize = 11.sp),
        color = colors.textMuted,
        maxLines = 1,
      )
      Spacer(modifier = Modifier.weight(1f))
      Icon(
        imageVector = MiuixIcons.Medium.MiuixMic,
        contentDescription = null,
        tint = colors.textSubtle,
        modifier = Modifier.size(18.dp),
      )
      Spacer(modifier = Modifier.width(6.dp))
      PreviewSendButton(size = 30.dp, iconSize = 16.dp)
    }
  }
}

@Composable
private fun SimpleComposerPreview() {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val glassFill = (if (dark) colors.surfaceRaised else Color.White).copy(alpha = if (dark) 0.46f else 0.62f)
  Row(
    modifier = Modifier.fillMaxWidth().height(50.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    Box(
      modifier =
        Modifier
          .size(50.dp)
          .clip(CircleShape)
          .background(glassFill)
          .clawGlassEdge(cornerRadius = 25.dp, dark = dark),
      contentAlignment = Alignment.Center,
    ) {
      Icon(
        imageVector = MiuixIcons.Medium.MiuixAdd,
        contentDescription = null,
        tint = colors.text,
        modifier = Modifier.size(22.dp),
      )
    }
    Row(
      modifier =
        Modifier
          .weight(1f)
          .height(50.dp)
          .clip(RoundedCornerShape(25.dp))
          .background(glassFill)
          .clawGlassEdge(cornerRadius = 25.dp, dark = dark)
          .padding(start = 18.dp, end = 7.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        text = nativeString("Message OpenClaw"),
        style = ClawTheme.type.caption.copy(fontSize = 13.sp),
        color = colors.textMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f),
      )
      Spacer(modifier = Modifier.width(8.dp))
      PreviewSendButton(size = 36.dp, iconSize = 18.dp)
    }
  }
}

@Composable
private fun PreviewSendButton(
  size: Dp,
  iconSize: Dp,
) {
  Box(
    modifier = Modifier.size(size).clip(CircleShape).background(ClawTheme.colors.primary),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      imageVector = ComposerSendArrow,
      contentDescription = null,
      tint = ClawTheme.colors.primaryText,
      modifier = Modifier.size(iconSize),
    )
  }
}
