package ai.openclaw.app.ui.chat

import ai.openclaw.app.ui.TabletopPaneBounds
import ai.openclaw.app.ui.foldSafeRegion
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.recalculateWindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.window.layout.DisplayFeature

private enum class ChatPaneSlot { Header, Body }

@Composable
internal fun ChatPaneLayout(
  tabletopPanes: TabletopPaneBounds?,
  features: List<DisplayFeature>,
  minimumInputHeight: Dp,
  minimumHeaderHeight: Dp,
  minimumReaderHeight: Dp,
  touchTarget: Dp,
  modifier: Modifier = Modifier,
  headerModifier: Modifier = Modifier,
  /** Applied to the transcript source sampled by the header and composer glass. */
  bodyModifier: Modifier = Modifier,
  header: @Composable (compact: Boolean, tabletop: Boolean) -> Unit,
  /**
   * Receives the height (plus gap) the header occupies so the caller can turn it
   * into scroll padding: the list itself spans the full height underneath the
   * bar — that overlap is what gives the bar something to blur — and the
   * reserve keeps content from resting under it while scrolled to an edge.
   */
  transcript: @Composable (headerReserve: Dp) -> Unit,
  status: @Composable () -> Unit,
  composer: @Composable (compact: Boolean, tabletop: Boolean) -> Unit,
) {
  SubcomposeLayout(modifier.fillMaxSize()) { constraints ->
    val width = constraints.maxWidth
    val height = constraints.maxHeight
    val padding = 10.dp.roundToPx()
    val gap = 8.dp.roundToPx()
    val inputFloor = minimumInputHeight.roundToPx()
    val headerFloor = minimumHeaderHeight.roundToPx()
    val readerFloor = minimumReaderHeight.roundToPx()
    val statusFloor = touchTarget.roundToPx()
    val widthFloor = 320.dp.roundToPx()
    layout(width, height) {
      // This host is already inside scaffold/IME padding. Translate window facts only here.
      val origin = coordinates?.positionInWindow()?.round() ?: IntOffset.Zero
      val host = IntRect(origin, IntSize(width, height))
      val upper = tabletopPanes?.upper?.intersect(host)
      val lower = tabletopPanes?.lower?.intersect(host)
      val tabletop =
        upper != null && lower != null &&
          upper.width >= widthFloor && lower.width >= widthFloor &&
          upper.height >= headerFloor + readerFloor + statusFloor + padding * 2 + gap * 2 &&
          lower.height >= inputFloor + padding * 2
      val fallback = if (tabletop) host else foldSafeRegion(host, features, layoutDirection)
      val upperBounds = (if (tabletop) checkNotNull(upper) else fallback).translate(-origin)
      val lowerBounds = (if (tabletop) checkNotNull(lower) else fallback).translate(-origin)
      val lowerHeight = lowerBounds.height - if (tabletop) padding * 2 else 0
      val compact = lowerHeight < inputFloor + statusFloor * 2 + padding * 2 + gap * 2
      val inset = if (tabletop || !compact) padding else 0
      val spacing = if (tabletop || !compact) gap else 0
      val upperHeight = (upperBounds.height - inset * 2).coerceAtLeast(0)
      val lowerAvailable = (lowerBounds.height - inset * 2).coerceAtLeast(0)
      val headerLimit = if (tabletop) headerFloor else (upperHeight - inputFloor).coerceAtLeast(0)

      // The header is measured on its own: the transcript has to know how tall it
      // is before it can reserve the same room at its own top.
      val headerPlaceable =
        subcompose(ChatPaneSlot.Header) {
          Box(modifier = headerModifier) { header(compact, tabletop) }
        }.single().measure(
          Constraints(
            minWidth = upperBounds.width,
            maxWidth = upperBounds.width,
            maxHeight = headerLimit,
          ),
        )
      val headerGap = if (headerPlaceable.height > 0) spacing else 0
      val headerReservePx = headerPlaceable.height + headerGap
      val headerReserve = headerReservePx.toDp()

      val bodyPlaceable =
        subcompose(ChatPaneSlot.Body) {
          Layout(
            content = {
              // No top padding here: padding would shrink the list and push it
              // out from under the bar, leaving the bar blurring empty space.
              // The list gets the full height (see the measure below) and the
              // caller inserts the reserve as its own scroll padding.
              Box(bodyModifier.recalculateWindowInsets().clipToBounds()) {
                transcript(headerReserve)
              }
              Box(Modifier.clipToBounds().verticalScroll(rememberScrollState())) {
                if (tabletop) Column { status() }
              }
              Box(Modifier.clipToBounds()) { composer(compact, tabletop) }
            },
          ) { measurables, _ ->
            val composerLimit =
              if (tabletop) {
                lowerAvailable
              } else {
                (lowerAvailable - headerReservePx - spacing).coerceAtLeast(0)
              }
            val composerPlaceable =
              measurables[2].measure(
                Constraints(
                  minWidth = lowerBounds.width,
                  maxWidth = lowerBounds.width,
                  maxHeight = composerLimit,
                ),
              )
            val statusLimit =
              if (tabletop) {
                (upperHeight - headerReservePx - readerFloor - spacing).coerceAtLeast(0)
              } else {
                0
              }
            val statusPlaceable =
              measurables[1].measure(
                Constraints(
                  minWidth = upperBounds.width,
                  maxWidth = upperBounds.width,
                  maxHeight = statusLimit,
                ),
              )
            val statusGap = if (statusPlaceable.height > 0) spacing else 0
            val readerHeight =
              (
                upperHeight - headerReservePx - statusPlaceable.height - statusGap -
                  if (tabletop) 0 else spacing
              ).coerceAtLeast(0)
            val readerPlaceable =
              measurables[0].measure(
                Constraints.fixed(upperBounds.width, readerHeight + headerReservePx),
              )
            layout(width, height) {
              readerPlaceable.place(upperBounds.left, upperBounds.top + inset)
              statusPlaceable.place(upperBounds.left, upperBounds.bottom - inset - statusPlaceable.height)
              composerPlaceable.place(lowerBounds.left, lowerBounds.bottom - inset - composerPlaceable.height)
            }
          }
        }.single().measure(Constraints.fixed(width, height))

      bodyPlaceable.place(0, 0)
      // Placed last so the header sits on top of the transcript. Pinned to the
      // pane's very top edge (no `inset`): the host reserves no status-bar inset
      // for the chat pane, and the bar has to start at that edge for its glass
      // to cover the status bar. It pads its own content back down inside.
      headerPlaceable.place(upperBounds.left, upperBounds.top)
    }
  }
}
