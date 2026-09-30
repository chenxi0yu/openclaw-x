package ai.openclaw.app.ui

import ai.openclaw.app.ui.design.ClawGlassHost
import ai.openclaw.app.ui.design.ClawTheme
import ai.openclaw.app.ui.design.rememberClawHaptics
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.window.layout.DisplayFeature
import androidx.window.layout.FoldingFeature

internal data class BookPaneBounds(val start: IntRect, val end: IntRect)

private val SidebarDrawerMaxWidth = 360.dp

internal fun bookPaneBounds(
  host: IntRect,
  features: List<DisplayFeature>,
  direction: LayoutDirection,
  density: Density,
): BookPaneBounds? {
  val separator =
    features.filterIsInstance<FoldingFeature>().singleOrNull {
      (it.isSeparating || it.occlusionType == FoldingFeature.OcclusionType.FULL) &&
        it.orientation == FoldingFeature.Orientation.VERTICAL &&
        it.bounds.left > host.left && it.bounds.right < host.right &&
        it.bounds.top <= host.top && it.bounds.bottom >= host.bottom
    } ?: return null
  val left = host.copy(right = separator.bounds.left)
  val right = host.copy(left = separator.bounds.right)
  val panes = if (direction == LayoutDirection.Ltr) BookPaneBounds(left, right) else BookPaneBounds(right, left)
  with(density) {
    if (panes.start.width < 280.dp.roundToPx() || panes.end.width < 320.dp.roundToPx() || host.height < 320.dp.roundToPx()) return null
  }
  if (foldSafeRegion(left, features, direction) != left || foldSafeRegion(right, features, direction) != right) return null
  return panes
}

@Composable
internal fun SidebarNavigationShell(
  drawerState: DrawerState,
  bookPanes: BookPaneBounds? = null,
  sidebarBand: IntRect? = null,
  gesturesEnabled: Boolean = true,
  edgeSwipeEnabled: Boolean = true,
  drawerContent: @Composable () -> Unit,
  content: @Composable () -> Unit,
) {
  val currentSidebar by rememberUpdatedState(drawerContent)
  val sidebar = remember { movableContentOf { currentSidebar() } }
  val haptics = rememberClawHaptics()
  val density = LocalDensity.current

  LaunchedEffect(drawerState) {
    snapshotFlow { drawerState.currentValue }.distinctUntilChanged().drop(1).collect { value ->
      if (value == DrawerValue.Open) haptics.tap()
    }
  }

  if (bookPanes != null) {
    // --- Permanent sidebar (foldable/dual-screen) ---
    Box(modifier = Modifier.fillMaxSize()) {
      Layout(
        content = {
          Box(Modifier.fillMaxSize()) { content() }
          Box(Modifier.fillMaxSize().testTag("sidebar-permanent")) { ClawGlassHost(Modifier.fillMaxSize()) { sidebar() } }
        },
        modifier = Modifier.fillMaxSize(),
      ) { measurables, constraints ->
        val db = bookPanes.end
        val dp = measurables[0].measure(Constraints.fixed(db.width, db.height))
        val sp = measurables[1].measure(Constraints.fixed(bookPanes.start.width, bookPanes.start.height))
        layout(constraints.maxWidth, constraints.maxHeight) { dp.place(db.left, db.top); sp.place(bookPanes.start.left, bookPanes.start.top) }
      }
    }
  } else {
    val drawerWidthPx = with(density) { SidebarDrawerMaxWidth.toPx() }

    // ModalNavigationDrawer handles ALL gestures natively:
    // - Edge swipe to open (left edge)
    // - Drag to close (when open)
    // - Tap on scrim to close
    // The `gesturesEnabled` flag controls whether drag gestures work at all.
    //
    // For push drawer effect: we use drawerState.offset (px from left, negative when
    // closed) to drive content translationX. When drawer is fully open (offset=0),
    // content is pushed right by drawerWidthPx.
    ModalNavigationDrawer(
      drawerState = drawerState,
      gesturesEnabled = gesturesEnabled && edgeSwipeEnabled,
      scrimColor = Color.Black.copy(alpha = 0.35f),
      drawerContent = {
        ModalDrawerSheet(
          modifier = Modifier.width(SidebarDrawerMaxWidth),
          drawerContainerColor = ClawTheme.colors.canvas,
        ) {
          ClawGlassHost(modifier = Modifier.requiredWidth(SidebarDrawerMaxWidth).fillMaxHeight()) {
            sidebar()
          }
        }
      },
    ) {
      // Content wrapper: pushed right by drawer offset
      val offset = drawerState.currentOffset // negative px when closed, 0 when open
      Box(
        modifier = Modifier
          .fillMaxSize()
          .graphicsLayer {
            // offset goes from -drawerWidthPx (closed) to 0 (open)
            // We want content translationX: 0 (closed) → drawerWidthPx (open)
            translationX = drawerWidthPx + offset
          }
          .clipToBounds(),
      ) {
        Box(Modifier.fillMaxSize()) { content() }
      }
    }
  }
}