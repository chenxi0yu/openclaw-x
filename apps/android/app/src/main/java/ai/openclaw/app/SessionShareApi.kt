package ai.openclaw.app

import ai.openclaw.app.i18n.nativeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Gateway session share contract. Shares are created, listed and revoked through
 * the plugin's REST surface — never `/tools/invoke`, which wraps failures in a
 * generic tool error and drops the server's error copy. Creation carries the
 * session KEY (`agent:…` form), not the session id: one key can own many
 * rotating window ids.
 */
@Serializable
internal data class SessionShareCreateResult(
  val id: String,
  val url: String,
  @SerialName("expires_at") val expiresAt: String? = null,
  val truncated: Boolean = false,
  val missingMessageIds: List<String> = emptyList(),
  val sessions: List<SessionShareSessionSummary> = emptyList(),
)

@Serializable
internal data class SessionShareSessionSummary(
  val id: String? = null,
  val title: String? = null,
  val messages: Int = 0,
)

/** One live share from `GET /__openclaw__/share/list`. */
@Serializable
internal data class SessionShareSummary(
  val id: String,
  val url: String = "",
  val title: String = "",
  val source: String = "",
  val size: Long = 0L,
  @SerialName("created_at") val createdAt: String? = null,
  @SerialName("expires_at") val expiresAt: String? = null,
  @SerialName("remaining_seconds") val remainingSeconds: Long = 0L,
)

@Serializable
private data class SessionShareListResponse(
  val shares: List<SessionShareSummary> = emptyList(),
)

/**
 * [code] is the HTTP status (null when the request never reached the gateway),
 * [serverError] the gateway's own user-facing error copy, [retryAfterSeconds]
 * the parsed `Retry-After` on 429, [network] true for transport-level failures.
 */
internal data class SessionShareFailure(
  val code: Int?,
  val serverError: String?,
  val retryAfterSeconds: Long?,
  val network: Boolean = false,
)

internal sealed interface SessionShareOutcome {
  data class Success(val result: SessionShareCreateResult) : SessionShareOutcome

  data class Failure(val failure: SessionShareFailure) : SessionShareOutcome
}

internal sealed interface SessionShareListOutcome {
  data class Success(val shares: List<SessionShareSummary>) : SessionShareListOutcome

  data class Failure(val failure: SessionShareFailure) : SessionShareListOutcome
}

internal sealed interface SessionShareDeleteOutcome {
  data object Success : SessionShareDeleteOutcome

  /** Already expired or revoked elsewhere: the caller can drop the row. */
  data object NotFound : SessionShareDeleteOutcome

  data class Failure(val failure: SessionShareFailure) : SessionShareDeleteOutcome
}

private val shareJson = Json { ignoreUnknownKeys = true }

private val shareHttpClient: OkHttpClient by lazy {
  OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .build()
}

private val shareIdPattern = Regex("^[A-Za-z0-9_-]{22}$")

private fun shareNetworkFailure() =
  SessionShareFailure(code = null, serverError = null, retryAfterSeconds = null, network = true)

/**
 * Auth follows the app's existing gateway credential: `Bearer` operator token
 * (or password) for direct gateways; behind the trusted reverse proxy the proxy
 * injects its own headers, so the client sends nothing extra.
 */
private fun shareRequestBuilder(
  page: NodeRuntime.GatewayControlPage,
  path: String,
): Request.Builder? {
  val url = "${page.baseUrl}$path".toHttpUrlOrNull() ?: return null
  return Request
    .Builder()
    .url(url)
    .header("Accept", "application/json")
    .apply {
      val bearer =
        page.token?.trim()?.takeIf(String::isNotEmpty)
          ?: page.password?.trim()?.takeIf(String::isNotEmpty)
      if (bearer != null) header("Authorization", "Bearer $bearer")
    }
}

private fun parseShareFailure(
  response: Response,
  body: String,
): SessionShareFailure {
  val serverError =
    runCatching {
      shareJson.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull
    }.getOrNull()
  return SessionShareFailure(
    code = response.code,
    serverError = serverError?.takeIf { it.isNotBlank() },
    retryAfterSeconds = response.header("Retry-After")?.toLongOrNull(),
  )
}

internal suspend fun createSessionShareRequest(
  page: NodeRuntime.GatewayControlPage,
  sessionKey: String,
  messageIds: List<String>,
  ttlSeconds: Long = 86_400L,
): SessionShareOutcome =
  withContext(Dispatchers.IO) {
    if (messageIds.isEmpty()) {
      return@withContext SessionShareOutcome.Failure(shareNetworkFailure())
    }
    val builder =
      shareRequestBuilder(page, "/__openclaw__/share/create")
        ?: return@withContext SessionShareOutcome.Failure(shareNetworkFailure())
    val payload =
      buildJsonObject {
        put("session", sessionKey)
        put("messages", JsonArray(messageIds.map(::JsonPrimitive)))
        put("ttl", ttlSeconds)
      }
    val request =
      builder
        .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
        .build()
    try {
      shareHttpClient.newCall(request).execute().use { response ->
        val body = response.body.string()
        if (!response.isSuccessful) {
          return@use SessionShareOutcome.Failure(parseShareFailure(response, body))
        }
        val result =
          runCatching { shareJson.decodeFromString<SessionShareCreateResult>(body) }.getOrNull()
            ?: return@use SessionShareOutcome.Failure(
              SessionShareFailure(code = response.code, serverError = null, retryAfterSeconds = null),
            )
        SessionShareOutcome.Success(result)
      }
    } catch (_: IOException) {
      SessionShareOutcome.Failure(shareNetworkFailure())
    }
  }

/** Lists live shares; expired entries are purged server-side before the response. */
internal suspend fun listSessionSharesRequest(
  page: NodeRuntime.GatewayControlPage,
): SessionShareListOutcome =
  withContext(Dispatchers.IO) {
    val request =
      shareRequestBuilder(page, "/__openclaw__/share/list")?.get()?.build()
        ?: return@withContext SessionShareListOutcome.Failure(shareNetworkFailure())
    try {
      shareHttpClient.newCall(request).execute().use { response ->
        val body = response.body.string()
        if (!response.isSuccessful) {
          return@use SessionShareListOutcome.Failure(parseShareFailure(response, body))
        }
        val parsed =
          runCatching { shareJson.decodeFromString<SessionShareListResponse>(body) }.getOrNull()
            ?: return@use SessionShareListOutcome.Failure(
              SessionShareFailure(code = response.code, serverError = null, retryAfterSeconds = null),
            )
        SessionShareListOutcome.Success(parsed.shares.filter { it.id.isNotBlank() })
      }
    } catch (_: IOException) {
      SessionShareListOutcome.Failure(shareNetworkFailure())
    }
  }

/** Revokes one share; a 404 means it already expired or was deleted elsewhere. */
internal suspend fun deleteSessionShareRequest(
  page: NodeRuntime.GatewayControlPage,
  id: String,
): SessionShareDeleteOutcome =
  withContext(Dispatchers.IO) {
    if (!shareIdPattern.matches(id)) return@withContext SessionShareDeleteOutcome.NotFound
    val request =
      shareRequestBuilder(page, "/__openclaw__/share/item/$id")?.delete()?.build()
        ?: return@withContext SessionShareDeleteOutcome.Failure(shareNetworkFailure())
    try {
      shareHttpClient.newCall(request).execute().use { response ->
        when {
          response.isSuccessful -> SessionShareDeleteOutcome.Success
          response.code == 404 -> SessionShareDeleteOutcome.NotFound
          else -> SessionShareDeleteOutcome.Failure(parseShareFailure(response, response.body.string()))
        }
      }
    } catch (_: IOException) {
      SessionShareDeleteOutcome.Failure(shareNetworkFailure())
    }
  }

/**
 * User-facing copy for a failed share operation. The gateway's own error text
 * wins (it is written for operators); [fallback] covers statuses it does not
 * describe for the current action.
 */
internal fun sessionShareFailureText(
  failure: SessionShareFailure,
  fallback: String = nativeString("创建分享失败，请稍后重试"),
): String {
  if (failure.network) return nativeString("无法连接网关，请稍后重试")
  failure.serverError?.let { return it }
  return when (failure.code) {
    401 -> nativeString("网关鉴权未通过，请重新连接网关后再试")
    403 -> nativeString("当前网关账号没有分享管理权限")
    404 -> nativeString("需要网关安装分享插件才可使用网页分享")
    413 -> nativeString("选中的内容太多，请减少勾选后再分享")
    422 -> nativeString("部分勾选的消息已不存在，请重新勾选后再分享")
    429 -> {
      val wait = failure.retryAfterSeconds?.takeIf { it > 0 }
      if (wait == null) {
        nativeString("操作太频繁，请稍后再试")
      } else {
        nativeString("操作太频繁，约 \$wait 秒后再试", wait)
      }
    }
    else -> fallback
  }
}
