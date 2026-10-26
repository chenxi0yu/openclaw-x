package ai.openclaw.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionShareApiTest {
  @Test
  fun networkFailureMapsToTheGatewayOfflineCopy() {
    val text = sessionShareFailureText(SessionShareFailure(code = null, serverError = null, retryAfterSeconds = null, network = true))
    assertTrue(text.contains("网关"))
  }

  @Test
  fun serverErrorCopyWinsOverTheStatusCodeMapping() {
    val text =
      sessionShareFailureText(
        SessionShareFailure(code = 413, serverError = "分享内容超出上限", retryAfterSeconds = null),
      )
    assertEquals("分享内容超出上限", text)
  }

  @Test
  fun missingMessagesWithoutServerCopyMapsToReselect() {
    val text = sessionShareFailureText(SessionShareFailure(code = 422, serverError = null, retryAfterSeconds = null))
    assertTrue(text.contains("重新勾选"))
  }

  @Test
  fun rateLimitWithoutServerCopyAppendsTheRetryAfterWait() {
    val text = sessionShareFailureText(SessionShareFailure(code = 429, serverError = null, retryAfterSeconds = 7))
    assertTrue(text.contains("7"))
  }

  @Test
  fun authFailureWithoutServerCopyMapsToReconnect() {
    val text = sessionShareFailureText(SessionShareFailure(code = 401, serverError = null, retryAfterSeconds = null))
    assertTrue(text.contains("重新连接"))
  }

  @Test
  fun unknownStatusFallsBackToTheGenericCopy() {
    val text = sessionShareFailureText(SessionShareFailure(code = 500, serverError = null, retryAfterSeconds = null))
    assertTrue(text.contains("创建分享失败"))
  }

  @Test
  fun managementFallbackIsUsedWhenTheServerSendsNoCopy() {
    val text =
      sessionShareFailureText(
        SessionShareFailure(code = 500, serverError = null, retryAfterSeconds = null),
        fallback = "加载分享链接失败",
      )
    assertEquals("加载分享链接失败", text)
  }
}
