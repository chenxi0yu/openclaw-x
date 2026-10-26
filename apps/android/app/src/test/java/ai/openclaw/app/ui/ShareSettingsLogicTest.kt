package ai.openclaw.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class ShareSettingsLogicTest {
  @Test
  fun remainingTextUsesTheLongestUsefulUnits() {
    assertEquals("剩余 2 天 3 小时", shareRemainingText(2L * 86_400L + 3L * 3_600L + 600L))
    assertEquals("剩余 2 天", shareRemainingText(2L * 86_400L))
    assertEquals("剩余 3 小时 12 分", shareRemainingText(3L * 3_600L + 12L * 60L))
    assertEquals("剩余 3 小时", shareRemainingText(3L * 3_600L))
    assertEquals("剩余 12 分", shareRemainingText(12L * 60L))
  }

  @Test
  fun remainingTextHandlesTheLastMinuteAndExpiry() {
    assertEquals("即将失效", shareRemainingText(59L))
    assertEquals("即将失效", shareRemainingText(1L))
    assertEquals("已过期", shareRemainingText(0L))
    assertEquals("已过期", shareRemainingText(-5L))
  }

  @Test
  fun remainingSecondsRoundUpSoTheLastSecondStillReadsAsLive() {
    assertEquals(0L, shareRemainingSeconds(deadlineElapsedMs = 1_000L, nowElapsedMs = 1_000L))
    assertEquals(1L, shareRemainingSeconds(deadlineElapsedMs = 1_001L, nowElapsedMs = 1_000L))
    assertEquals(2L, shareRemainingSeconds(deadlineElapsedMs = 1_001L, nowElapsedMs = 0L))
  }
}
