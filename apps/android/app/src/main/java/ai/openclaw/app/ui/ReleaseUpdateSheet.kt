package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.rememberClawHaptics
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

internal data class ReleaseUpdatePage(
  val icon: ImageVector,
  val tint: Color,
  val title: String,
  val body: String,
)

/**
 * Release-notes sheet anchored to the bottom edge: a grab handle, the version
 * as its title, then left-flushed notes. It slides up on a long decelerating
 * curve and fades its contents in once the surface has settled. Pages swipe
 * horizontally; the right pill advances until the last page, where it becomes
 * the entry action. The caller keeps this composed and toggles [visible] so the
 * exit curve can play.
 */
@Composable
internal fun ReleaseUpdateSheet(
  visible: Boolean,
  version: String,
  pages: List<ReleaseUpdatePage>,
  onDismiss: () -> Unit,
  onFinish: () -> Unit,
  modifier: Modifier = Modifier,
) {
  if (pages.isEmpty()) return
  BackHandler(enabled = visible, onBack = onDismiss)
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  val navBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
  val sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
  val sheetColor = if (dark) colors.surface else colors.canvas
  val scrimInteraction = remember { MutableInteractionSource() }
  val pagerState = rememberPagerState(pageCount = { pages.size })
  val scope = rememberCoroutineScope()
  // The face holds still while the sheet decelerates, then arrives; on the way
  // out it leaves immediately so nothing trails the sliding surface.
  val contentAlpha by
    animateFloatAsState(
      targetValue = if (visible) 1f else 0f,
      animationSpec =
        tween(
          durationMillis = if (visible) 220 else 100,
          delayMillis = if (visible) 220 else 0,
        ),
      label = "releaseIntroContent",
    )

  Box(modifier = modifier.fillMaxSize()) {
    AnimatedVisibility(
      visible = visible,
      enter = fadeIn(animationSpec = tween(200)),
      exit = fadeOut(animationSpec = tween(160)),
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (dark) 0.42f else 0.28f))
            // The scrim is modal: taps must not reach the shell behind it.
            .clickable(
              interactionSource = scrimInteraction,
              indication = null,
              onClick = {},
            ),
      )
    }
    AnimatedVisibility(
      visible = visible,
      modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
      enter =
        slideInVertically(
          initialOffsetY = { height -> height },
          animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        ),
      exit =
        slideOutVertically(
          targetOffsetY = { height -> height },
          animationSpec = tween(durationMillis = 240, easing = FastOutLinearInEasing),
        ),
    ) {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .clip(sheetShape)
            .background(sheetColor)
            .padding(bottom = navBarBottom),
      ) {
        Column(modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = contentAlpha }) {
          Box(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            contentAlignment = Alignment.Center,
          ) {
            Box(
              modifier =
                Modifier
                  .width(36.dp)
                  .height(4.dp)
                  .clip(CircleShape)
                  .background(colors.borderStrong),
            )
          }
          Text(
            text = version,
            style =
              ClawTheme.type.body.copy(
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
              ),
            color = colors.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
          )
          if (pages.size == 1) {
            ReleaseUpdatePageCard(pages.first(), Modifier.padding(horizontal = 16.dp))
          } else {
            HorizontalPager(
              state = pagerState,
              modifier = Modifier.fillMaxWidth().height(230.dp),
            ) { index ->
              ReleaseUpdatePageCard(pages[index], Modifier.padding(horizontal = 16.dp))
            }
          }
          if (pages.size > 1) {
            ReleasePageDots(
              pageCount = pages.size,
              current = pagerState.currentPage,
              activeColor = pages[pagerState.currentPage].tint,
            )
          }
          val lastPage = pagerState.currentPage == pages.lastIndex
          Row(
            modifier =
              Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            ReleaseSheetPill(
              text = "知道了",
              primary = false,
              onClick = onDismiss,
              modifier = Modifier.weight(1f),
            )
            ReleaseSheetPill(
              text = if (lastPage) "马上体验" else "下一页",
              primary = true,
              onClick = {
                if (lastPage) {
                  onFinish()
                } else {
                  scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
              },
              modifier = Modifier.weight(1f),
            )
          }
        }
      }
    }
  }
}

/**
 * One note block on a white rounded card, matching the list-card language of
 * the reference sheet: a left-flushed heading with its icon, then the body on
 * its own line underneath.
 */
@Composable
private fun ReleaseUpdatePageCard(page: ReleaseUpdatePage, modifier: Modifier = Modifier) {
  val colors = ClawTheme.colors
  val dark = colors.canvas.luminance() < 0.5f
  Column(
    modifier =
      modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(20.dp))
        .background(if (dark) colors.surfaceRaised else colors.surface)
        .padding(horizontal = 16.dp, vertical = 16.dp),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Icon(
        imageVector = page.icon,
        contentDescription = null,
        tint = page.tint,
        modifier = Modifier.size(24.dp),
      )
      Text(
        text = page.title,
        style =
          ClawTheme.type.body.copy(
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
          ),
        color = colors.text,
      )
    }
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = page.body,
      style = ClawTheme.type.body.copy(fontSize = 15.sp, lineHeight = 23.sp),
      color = colors.textMuted,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

@Composable
private fun ReleasePageDots(
  pageCount: Int,
  current: Int,
  activeColor: Color,
) {
  Row(
    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically,
  ) {
    repeat(pageCount) { index ->
      Box(
        modifier =
          Modifier
            .padding(horizontal = 3.dp)
            .size(6.dp)
            .clip(CircleShape)
            .background(if (index == current) activeColor else ClawTheme.colors.borderStrong),
      )
    }
  }
}

@Composable
private fun ReleaseSheetPill(
  text: String,
  primary: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val colors = ClawTheme.colors
  val haptics = rememberClawHaptics()
  Box(
    modifier =
      modifier
        .height(50.dp)
        .clip(CircleShape)
        .background(if (primary) colors.primary else colors.surfacePressed)
        .clickable(
          interactionSource = remember { MutableInteractionSource() },
          indication = null,
          role = Role.Button,
          onClick = {
            haptics.tap()
            onClick()
          },
        ),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = text,
      style = ClawTheme.type.body.copy(fontSize = 16.sp, fontWeight = FontWeight.Medium),
      color = if (primary) colors.primaryText else colors.text,
    )
  }
}
