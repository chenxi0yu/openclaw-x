package ai.openclaw.app.ui

import ai.openclaw.app.AppLanguage
import ai.openclaw.app.AppearanceStartupPage
import ai.openclaw.app.AppearanceThemeFamily
import ai.openclaw.app.AppearanceThemeMode
import ai.openclaw.app.MainViewModel
import ai.openclaw.app.appearanceAccentPalette
import ai.openclaw.app.appearanceAccentPreferenceValue
import ai.openclaw.app.currentAppLanguage
import ai.openclaw.app.currentSystemLanguageTag
import ai.openclaw.app.i18n.nativeString
import ai.openclaw.app.setAppLanguage
import ai.openclaw.app.ui.design.ClawGlassMenuItem
import ai.openclaw.app.ui.design.ClawGlassTopBarButton
import ai.openclaw.app.ui.design.ClawMorphMenuOverlay
import ai.openclaw.app.ui.design.ClawScaffold
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.ClawTopBarButtonTopInset
import ai.openclaw.app.ui.design.ClawTopBarContentHeight
import ai.openclaw.app.ui.design.MAX_APP_TEXT_SCALE
import ai.openclaw.app.ui.design.MIN_APP_TEXT_SCALE
import ai.openclaw.app.ui.design.MiuixGroup
import ai.openclaw.app.ui.design.MiuixGroupLabel
import ai.openclaw.app.ui.design.MiuixSwitchRow
import ai.openclaw.app.ui.design.MiuixValueRow
import ai.openclaw.app.ui.design.clawBackdropTopBar
import ai.openclaw.app.ui.design.clawPagePush
import ai.openclaw.app.ui.design.rememberClawBackdrop
import ai.openclaw.app.ui.design.rememberClawMorphMenuState
import ai.openclaw.app.ui.design.rememberClawMorphTrigger
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.backdrops.layerBackdrop
import kotlin.math.roundToInt

/** HyperOS selection blue; the reference keeps selection chrome blue even when
 * the chosen accent color is something else. */
private val AppearanceSelectionBlue = Color(0xFF3482FF)

/**
 * HyperOS-style personalization: the light/dark cards and horizontal theme rails
 * replace the old panel stack. Composer visibility lives here because it is a
 * device preference rather than a gateway setting.
 */
@Composable
internal fun AppearanceSettingsScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
) {
  var composerBarPageOpen by rememberSaveable { mutableStateOf(false) }
  // Arriving from the release intro's "马上体验": open the composer sub-page
  // once, then clear the request so Back lands on the overview.
  val composerBarRequested by viewModel.composerBarSettingsRequest.collectAsState()
  LaunchedEffect(composerBarRequested) {
    if (composerBarRequested) {
      composerBarPageOpen = true
      viewModel.clearComposerBarSettingsRequest()
    }
  }
  AnimatedContent(
    targetState = composerBarPageOpen,
    transitionSpec = { clawPagePush(forward = targetState) },
    label = "appearance route",
  ) { showComposerBar ->
    if (showComposerBar) {
      ComposerBarSettingsScreen(viewModel = viewModel, onBack = { composerBarPageOpen = false })
    } else {
      AppearanceOverviewScreen(
        viewModel = viewModel,
        onBack = onBack,
        onOpenComposerBar = { composerBarPageOpen = true },
      )
    }
  }
}

@Composable
private fun AppearanceOverviewScreen(
  viewModel: MainViewModel,
  onBack: () -> Unit,
  onOpenComposerBar: () -> Unit,
) {
  val themeMode by viewModel.appearanceThemeMode.collectAsState()
  val themeFamily by viewModel.appearanceThemeFamily.collectAsState()
  val accentArgb by viewModel.appearanceAccentArgb.collectAsState()
  val textScale by viewModel.appearanceTextScale.collectAsState()
  val hapticsEnabled by viewModel.hapticsEnabled.collectAsState()
  val composerBarStyle by viewModel.composerBarStyle.collectAsState()
  val startupPage by viewModel.appearanceStartupPage.collectAsState()

  val context = LocalContext.current
  val density = LocalDensity.current
  val systemDark = isSystemInDarkTheme()
  val followSystem = themeMode == AppearanceThemeMode.System
  val effectiveDark = themeMode.isDark(systemDark)
  var textScaleDraft by remember(textScale) { mutableFloatStateOf(textScale) }
  var appLanguage by remember { mutableStateOf(currentAppLanguage()) }
  var showLanguageSheet by remember { mutableStateOf(false) }
  val systemLanguageTag = currentSystemLanguageTag(context)

  val backdrop = rememberClawBackdrop()
  val startupMenuState = rememberClawMorphMenuState()
  val startupTrigger = rememberClawMorphTrigger(startupMenuState)
  val scrollState = rememberScrollState()
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
      label = "appearanceTopChrome",
    )
  val topTitleAlpha by
    animateFloatAsState(
      targetValue = if (scrollProgress > 0.55f) 1f else 0f,
      animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
      label = "appearanceTopTitle",
    )
  val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
  val topBarHeight = statusBarHeight + ClawTopBarContentHeight

  fun chooseMode(mode: AppearanceThemeMode) {
    viewModel.setAppearanceThemeMode(mode)
  }

  Box(modifier = Modifier.fillMaxSize()) {
    // The top bar samples this layer, so the bar stays a sibling below instead
    // of a child: a sampler inside its own layer recurses through the RenderNode
    // tree (the update page hit that stack overflow).
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
            text = "外观",
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

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            AppearanceModeCard(
              dark = false,
              selected = !effectiveDark,
              onClick = { chooseMode(AppearanceThemeMode.Light) },
              modifier = Modifier.weight(1f),
            )
            AppearanceModeCard(
              dark = true,
              selected = effectiveDark,
              onClick = { chooseMode(AppearanceThemeMode.Dark) },
              modifier = Modifier.weight(1f),
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          MiuixGroup {
            MiuixSwitchRow(
              title = "跟随系统",
              subtitle = "浅色/深色随系统设置切换",
              checked = followSystem,
              onCheckedChange = { enabled ->
                chooseMode(
                  when {
                    enabled -> AppearanceThemeMode.System
                    systemDark -> AppearanceThemeMode.Dark
                    else -> AppearanceThemeMode.Light
                  },
                )
              },
              reserveIconSpace = false,
            )
          }

          MiuixGroupLabel(text = "主题", modifier = Modifier.padding(top = 18.dp))
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            AppearanceThemeFamily.entries.forEach { family ->
              AppearanceThemeFamilyCard(
                family = family,
                selected = family == themeFamily,
                onClick = { viewModel.setAppearanceThemeFamily(family) },
              )
            }
          }

          MiuixGroupLabel(text = "强调色", modifier = Modifier.padding(top = 14.dp))
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
          ) {
            (listOf<Long?>(null) + appearanceAccentPalette).forEach { candidate ->
              AppearanceAccentSwatch(
                argb = candidate,
                previewArgb = candidate ?: themeFamily.previewAccentArgb,
                selected = candidate == accentArgb,
                onClick = { viewModel.setAppearanceAccentArgb(candidate) },
              )
            }
          }

          MiuixGroupLabel(text = "显示", modifier = Modifier.padding(top = 18.dp))
          MiuixGroup {
            Column(
              modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(
                  text = "字体大小",
                  style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                  color = ClawTheme.colors.text,
                  modifier = Modifier.weight(1f),
                )
                Text(
                  text = "${(textScaleDraft * 100).roundToInt()}%",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
              AppearanceTextScaleSlider(
                value = textScaleDraft,
                onValueChange = { textScaleDraft = it },
                onValueChangeFinished = { viewModel.setAppearanceTextScale(textScaleDraft) },
              )
            }
            MiuixValueRow(
              title = "输入栏",
              value = composerBarStyle.displayLabel,
              reserveIconSpace = false,
              onClick = onOpenComposerBar,
            )
            MiuixValueRow(
              title = "启动第一页",
              value = startupPage.displayLabel,
              reserveIconSpace = false,
              trailingIcon = Icons.Default.UnfoldMore,
              trailingModifier = startupTrigger.modifier,
              onClick = {
                startupTrigger.open(origin = TransformOrigin(1f, 0f), panelWidth = 200.dp) {
                  AppearanceStartupPage.entries.forEach { page ->
                    ClawGlassMenuItem(
                      label = page.displayLabel,
                      selected = page == startupPage,
                      onClick = { viewModel.setAppearanceStartupPage(page) },
                    )
                  }
                }
              },
            )
          }

          MiuixGroupLabel(text = "震动", modifier = Modifier.padding(top = 18.dp))
          MiuixGroup {
            MiuixSwitchRow(
              title = "软件震感",
              subtitle = "聊天控件点击时振动",
              checked = hapticsEnabled,
              onCheckedChange = viewModel::setHapticsEnabled,
              reserveIconSpace = false,
            )
          }

          MiuixGroupLabel(text = "存储", modifier = Modifier.padding(top = 18.dp))
          MiuixGroup {
            MiuixValueRow(
              title = "图片缓存",
              value = "清理缓存",
              reserveIconSpace = false,
              onClick = {
                viewModel.clearChatImageCache()
                Toast.makeText(context, "已清理图片缓存", Toast.LENGTH_SHORT).show()
              },
            )
          }

          MiuixGroupLabel(text = "语言", modifier = Modifier.padding(top = 18.dp))
          MiuixGroup {
            MiuixValueRow(
              title = "系统语言",
              value = appLanguageTitle(appLanguage),
              reserveIconSpace = false,
              onClick = { showLanguageSheet = true },
            )
          }
        }
      }
    }

    Box(
      modifier = Modifier.fillMaxWidth().height(topBarHeight).clawBackdropTopBar(backdrop),
    ) {
      ClawGlassTopBarButton(
        contentDescription = "返回",
        // Same chrome as the Settings first-level page: a bare chevron at the
        // top, with the glass disc fading in only once content is sliding under
        // the bar.
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
          text = "外观",
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

    // Sibling of the sampled layer, same as the update-source picker.
    ClawMorphMenuOverlay(
      state = startupMenuState,
      onDismissRequest = { startupMenuState.dismiss() },
      backdrop = backdrop,
      modifier = Modifier.fillMaxSize(),
    )
  }

  if (showLanguageSheet) {
    AppLanguagePickerSheet(
      current = appLanguage,
      systemLanguageTag = systemLanguageTag,
      onDismiss = { showLanguageSheet = false },
      onSelect = { language ->
        showLanguageSheet = false
        appLanguage = language
        setAppLanguage(language)
      },
    )
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearanceTextScaleSlider(
  value: Float,
  onValueChange: (Float) -> Unit,
  onValueChangeFinished: () -> Unit,
) {
  val fraction = ((value - MIN_APP_TEXT_SCALE) / (MAX_APP_TEXT_SCALE - MIN_APP_TEXT_SCALE)).coerceIn(0f, 1f)
  Slider(
    value = value,
    onValueChange = onValueChange,
    onValueChangeFinished = onValueChangeFinished,
    valueRange = MIN_APP_TEXT_SCALE..MAX_APP_TEXT_SCALE,
    modifier = Modifier.fillMaxWidth().height(40.dp),
    thumb = {
      // HyperOS uses a chunky pill track and an almost track-height white disc,
      // not Material's thin line with a vertical-bar thumb.
      Box(
        modifier =
          Modifier
            .size(30.dp)
            .shadow(3.dp, CircleShape)
            .background(Color.White, CircleShape)
            .border(0.5.dp, Color.Black.copy(alpha = 0.06f), CircleShape),
      )
    },
    track = {
      Box(
        modifier =
          Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(CircleShape)
            .background(ClawTheme.colors.borderStrong),
      ) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth(fraction)
              .fillMaxHeight()
              .clip(CircleShape)
              .background(AppearanceSelectionBlue),
        )
      }
    },
  )
}

@Composable
private fun AppearanceModeCard(
  dark: Boolean,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shape = RoundedCornerShape(18.dp)
  val cardColors =
    if (dark) {
      listOf(Color(0xFF2E5BDA), Color(0xFF5AA0FF))
    } else {
      listOf(Color(0xFF6E9CF4), Color(0xFF9CC8FF))
    }
  val blockColor = if (dark) Color(0xFF183778).copy(alpha = 0.55f) else Color.White.copy(alpha = 0.38f)
  val barColor = if (dark) Color(0xFF9BC5FF) else Color.White.copy(alpha = 0.72f)
  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(132.dp)
          .clip(shape)
          .background(Brush.verticalGradient(cardColors))
          .border(3.dp, if (selected) AppearanceSelectionBlue else Color.Transparent, shape)
          .clickable(role = Role.RadioButton, onClick = onClick)
          .padding(14.dp),
    ) {
      Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
      ) {
        repeat(2) {
          Box(
            modifier =
              Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(blockColor)
                .padding(horizontal = 10.dp, vertical = 8.dp),
          ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
              Box(
                modifier =
                  Modifier
                    .fillMaxWidth(0.9f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor),
              )
              Box(
                modifier =
                  Modifier
                    .fillMaxWidth(0.62f)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(barColor.copy(alpha = 0.7f)),
              )
            }
          }
        }
      }
    }
    Text(
      text = if (dark) "深色模式" else "浅色模式",
      style = ClawTheme.type.caption.copy(fontSize = 14.sp),
      color = if (selected) AppearanceSelectionBlue else ClawTheme.colors.textMuted,
      modifier = Modifier.padding(top = 8.dp),
    )
  }
}

@Composable
private fun AppearanceThemeFamilyCard(
  family: AppearanceThemeFamily,
  selected: Boolean,
  onClick: () -> Unit,
) {
  val shape = RoundedCornerShape(14.dp)
  Column(modifier = Modifier.width(112.dp)) {
    Box(
      modifier =
        Modifier
          .fillMaxWidth()
          .height(76.dp)
          .clip(shape)
          // Previews sit on white paper: the theme canvas colors are nearly
          // black, and a rail of black tiles read as holes on the light page.
          .background(Color.White)
          .border(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) AppearanceSelectionBlue else ClawTheme.colors.border.copy(alpha = 0.7f),
            shape = shape,
          )
          .clickable(role = Role.RadioButton, onClick = onClick)
          .padding(10.dp),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(
          modifier =
            Modifier
              .fillMaxWidth()
              .height(24.dp)
              .clip(RoundedCornerShape(8.dp))
              .background(Color(family.previewAccentArgb)),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          Box(
            modifier =
              Modifier
                .width(34.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(family.previewSecondaryArgb)),
          )
          Box(
            modifier =
              Modifier
                .width(22.dp)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ClawTheme.colors.borderStrong),
          )
        }
      }
    }
    Text(
      text = family.displayLabel,
      style = ClawTheme.type.caption.copy(fontSize = 13.sp),
      color = if (selected) AppearanceSelectionBlue else ClawTheme.colors.textMuted,
      maxLines = 1,
      modifier = Modifier.padding(start = 2.dp, top = 6.dp),
    )
  }
}

internal fun appearanceAccentSwatchDescription(argb: Long?): String =
  listOf(
    nativeString("Accent color"),
    appearanceAccentPreferenceValue(argb)?.uppercase() ?: nativeString("Default"),
  ).joinToString(", ")

@Composable
private fun AppearanceAccentSwatch(
  argb: Long?,
  previewArgb: Long,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Box(
    modifier =
      Modifier
        .size(48.dp)
        .clickable(role = Role.RadioButton, onClick = onClick)
        .semantics {
          contentDescription = appearanceAccentSwatchDescription(argb)
          this.selected = selected
        },
    contentAlignment = Alignment.Center,
  ) {
    Box(
      modifier =
        Modifier
          .size(34.dp)
          .clip(CircleShape)
          .background(Color(previewArgb))
          .border(
            width = if (selected) 3.dp else 1.dp,
            color = if (selected) AppearanceSelectionBlue else ClawTheme.colors.borderStrong,
            shape = CircleShape,
          ),
      contentAlignment = Alignment.Center,
    ) {
      if (selected) {
        Icon(
          imageVector = Icons.Default.Check,
          contentDescription = null,
          tint = Color.White,
          modifier = Modifier.size(18.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppLanguagePickerSheet(
  current: AppLanguage,
  systemLanguageTag: String,
  onDismiss: () -> Unit,
  onSelect: (AppLanguage) -> Unit,
) {
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    containerColor = ClawTheme.colors.surface,
    dragHandle = null,
  ) {
    Column(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp)
          .padding(top = 20.dp, bottom = 20.dp),
    ) {
      Text(
        text = "系统语言",
        style = ClawTheme.type.section.copy(fontSize = 18.sp, fontWeight = FontWeight.Medium),
        color = ClawTheme.colors.text,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
      )
      Text(
        text = "上下滑动选择应用语言",
        style = ClawTheme.type.caption,
        color = ClawTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
      )
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .heightIn(max = 460.dp)
            .verticalScroll(rememberScrollState()),
      ) {
        AppLanguage.entries.forEach { language ->
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .clickable(role = Role.Button) { onSelect(language) }
                .padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              Text(
                text = appLanguageTitle(language),
                style = ClawTheme.type.body.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium),
                color = ClawTheme.colors.text,
              )
              if (language == AppLanguage.System) {
                Text(
                  text = "跟随 Android · $systemLanguageTag",
                  style = ClawTheme.type.caption,
                  color = ClawTheme.colors.textMuted,
                )
              }
            }
            if (language == current) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = nativeString("Selected"),
                tint = AppearanceSelectionBlue,
                modifier = Modifier.size(18.dp),
              )
            }
          }
        }
      }
    }
  }
}

private fun appLanguageTitle(language: AppLanguage): String =
  if (language == AppLanguage.System) "跟随系统" else language.displayName
