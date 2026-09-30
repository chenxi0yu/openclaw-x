package ai.openclaw.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionKeyTest {
  @Test
  fun buildNodeMainSessionKeyUsesSharedMainKey() {
    val key = buildNodeMainSessionKey(deviceId = "1234567890abcdef", agentId = "ops")
    assertEquals("agent:ops:main", key)
  }

  @Test
  fun buildAndroidAppSessionLabelIncludesDeviceDisplayName() {
    assertEquals("OpenClaw App", buildAndroidAppSessionLabel(null, "1234567890abcdef"))
    assertEquals(
      "OpenClaw App · Pixel",
      buildAndroidAppSessionLabel(" Pixel ", "1234567890abcdef"),
    )
  }

  @Test
  fun buildAndroidAppSessionLabelPreservesUtf16BoundariesAtDisplayNameLimit() {
    val deviceId = "1234567890abcdef"
    val splitPairPrefix = "a".repeat(95)
    assertEquals(
      "OpenClaw App · $splitPairPrefix",
      buildAndroidAppSessionLabel("$splitPairPrefix😀tail", deviceId),
    )

    val completePairPrefix = "a".repeat(94)
    assertEquals(
      "OpenClaw App · $completePairPrefix😀",
      buildAndroidAppSessionLabel("$completePairPrefix😀tail", deviceId),
    )
  }

  @Test
  fun resolveAgentIdFromMainSessionKeyParsesCanonicalAgentKey() {
    assertEquals("ops", resolveAgentIdFromMainSessionKey("agent:ops:main"))
    assertNull(resolveAgentIdFromMainSessionKey("global"))
  }
}
