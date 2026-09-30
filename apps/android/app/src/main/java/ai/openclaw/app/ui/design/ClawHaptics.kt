package ai.openclaw.app.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * App-wide haptics switch, provided by the shell from the appearance preference.
 * Everything that wants to buzz goes through [rememberClawHaptics] so one toggle
 * in Settings silences the whole app.
 */
internal val LocalClawHapticsEnabled: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

internal interface ClawHaptics {
  /** Short tick for ordinary button presses. */
  fun tap()

  /** Light tick for discrete drags (slider stops). */
  fun tick()

  /** Firm feedback for long press. */
  fun longPress()
}

@Composable
internal fun rememberClawHaptics(): ClawHaptics {
  val enabled = LocalClawHapticsEnabled.current
  val feedback = LocalHapticFeedback.current
  return remember(enabled, feedback) { ClawHapticsDelegate(feedback, enabled) }
}

private class ClawHapticsDelegate(
  private val feedback: HapticFeedback,
  private val enabled: Boolean,
) : ClawHaptics {
  override fun tap() = fire(HapticFeedbackType.ContextClick)

  override fun tick() = fire(HapticFeedbackType.TextHandleMove)

  override fun longPress() = fire(HapticFeedbackType.LongPress)

  private fun fire(type: HapticFeedbackType) {
    if (enabled) feedback.performHapticFeedback(type)
  }
}
