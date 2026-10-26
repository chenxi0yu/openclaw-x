package ai.openclaw.app

import ai.openclaw.app.gateway.GatewayTlsParams
import ai.openclaw.app.gateway.buildGatewayTlsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** The durable profile a shared-secret connection resolves to on a single-user Gateway. */
internal const val GATEWAY_OWNER_PROFILE_ID = "gateway-owner"

internal const val GATEWAY_PROFILE_REFRESH_TTL_MS = 5 * 60_000L
private const val PROFILE_AVATAR_CACHE_SOURCE_PREFIX = "profile-avatar:"
private const val MAX_PROFILE_AVATAR_BYTES = 2 * 1024 * 1024
private const val MAX_PROFILE_DISPLAY_NAME_LENGTH = 256

/** A Gateway-owned person profile, not the local node/device name. */
internal data class GatewayProfile(
  val gatewayId: String,
  val id: String,
  val displayName: String?,
  val emails: List<String> = emptyList(),
  val avatarMime: String? = null,
  val hasAvatar: Boolean = false,
  val updatedAtMs: Long = 0L,
  /** Local-only revision from users.setAvatar; the read schema does not expose it. */
  val avatarRevision: String? = null,
  val canEdit: Boolean = false,
  val mergedInto: String? = null,
  val githubLogin: String? = null,
) {
  val avatarCacheSource: String
    get() = "$PROFILE_AVATAR_CACHE_SOURCE_PREFIX$id"
}

/**
 * Small local mirror so Settings can paint the account card before the Gateway round-trip.
 * The avatar bytes live in the existing disposable Room image cache; this holds text only.
 */
@Serializable
internal data class CachedGatewayProfile(
  val gatewayId: String,
  val id: String,
  val displayName: String? = null,
  val emails: List<String> = emptyList(),
  val avatarMime: String? = null,
  val hasAvatar: Boolean = false,
  val updatedAtMs: Long = 0L,
  val avatarRevision: String? = null,
  val canEdit: Boolean = false,
  val githubLogin: String? = null,
  val fetchedAtMs: Long = 0L,
)

internal sealed interface GatewayProfileState {
  data object Loading : GatewayProfileState

  data object Disconnected : GatewayProfileState

  data object Unsupported : GatewayProfileState

  data class Ready(val profile: GatewayProfile) : GatewayProfileState

  data class Failed(val message: String?) : GatewayProfileState
}

internal sealed interface GatewayProfileRefreshResult {
  data object Success : GatewayProfileRefreshResult

  data object Disconnected : GatewayProfileRefreshResult

  data object Unsupported : GatewayProfileRefreshResult

  data object Forbidden : GatewayProfileRefreshResult

  data class Failed(val message: String?) : GatewayProfileRefreshResult
}

internal sealed interface GatewayProfileWriteResult {
  data object Success : GatewayProfileWriteResult

  data object Disconnected : GatewayProfileWriteResult

  data object Unsupported : GatewayProfileWriteResult

  data object Forbidden : GatewayProfileWriteResult

  data class Failed(val message: String?) : GatewayProfileWriteResult
}

internal data class GatewayProfileAvatarImage(
  val bytes: ByteArray,
  val mimeType: String?,
)

internal data class GatewayProfileAvatarUpload(
  val mimeType: String,
  val base64: String,
  val bytes: ByteArray,
)

internal sealed interface GatewayProfileAvatarFetchResult {
  data class Success(val image: GatewayProfileAvatarImage) : GatewayProfileAvatarFetchResult

  data object NotFound : GatewayProfileAvatarFetchResult

  data class Failure(
    val code: Int?,
    val network: Boolean = false,
  ) : GatewayProfileAvatarFetchResult
}

internal fun parseGatewaySelfProfile(
  json: Json,
  raw: String,
  gatewayId: String,
): GatewayProfile? =
  raw
    .let { runCatching { json.parseToJsonElement(it).jsonObject["profile"] }.getOrNull() }
    ?.let { parseGatewayProfileObject(it, gatewayId) }

internal fun parseGatewayProfileList(
  json: Json,
  raw: String,
  gatewayId: String,
): List<GatewayProfile> =
  runCatching {
    (json.parseToJsonElement(raw).jsonObject["profiles"] as? JsonArray).orEmpty()
      .mapNotNull { parseGatewayProfileObject(it, gatewayId) }
  }.getOrDefault(emptyList())

internal fun selectGatewayProfile(profiles: List<GatewayProfile>): GatewayProfile? {
  val active = profiles.filter { it.mergedInto == null }
  return active.firstOrNull { it.id == GATEWAY_OWNER_PROFILE_ID }
    ?: active.singleOrNull()
}

internal fun validGatewayProfileDisplayName(value: String?): String? {
  val trimmed = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
  return trimmed.take(MAX_PROFILE_DISPLAY_NAME_LENGTH)
}

internal fun GatewayProfile.toCachedProfile(fetchedAtMs: Long): CachedGatewayProfile =
  CachedGatewayProfile(
    gatewayId = gatewayId,
    id = id,
    displayName = displayName,
    emails = emails,
    avatarMime = avatarMime,
    hasAvatar = hasAvatar,
    updatedAtMs = updatedAtMs,
    avatarRevision = avatarRevision,
    canEdit = canEdit,
    githubLogin = githubLogin,
    fetchedAtMs = fetchedAtMs,
  )

internal fun CachedGatewayProfile.toGatewayProfile(): GatewayProfile =
  GatewayProfile(
    gatewayId = gatewayId,
    id = id,
    displayName = displayName,
    emails = emails,
    avatarMime = avatarMime,
    hasAvatar = hasAvatar,
    updatedAtMs = updatedAtMs,
    avatarRevision = avatarRevision,
    canEdit = canEdit,
    githubLogin = githubLogin,
  )

private fun parseGatewayProfileObject(
  element: JsonElement?,
  gatewayId: String,
): GatewayProfile? {
  val obj = element as? JsonObject ?: return null
  val id = obj.string("id") ?: return null
  val emails =
    (obj["emails"] as? JsonArray).orEmpty()
      .mapNotNull { (it as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim()?.takeIf(String::isNotEmpty) }
      .distinct()
  return GatewayProfile(
    gatewayId = gatewayId,
    id = id,
    displayName = obj.string("displayName"),
    emails = emails,
    avatarMime = obj.string("avatarMime"),
    hasAvatar = obj.boolean("hasAvatar") == true,
    updatedAtMs = obj.long("updatedAt") ?: 0L,
    mergedInto = obj.string("mergedInto"),
    githubLogin = (obj["githubIdentity"] as? JsonObject)?.string("login"),
  )
}

private fun JsonObject.string(key: String): String? =
  (this[key] as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content?.trim()?.takeIf(String::isNotEmpty)

private fun JsonObject.boolean(key: String): Boolean? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

private fun JsonObject.long(key: String): Long? =
  (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull()

private val profileAvatarHttpClient: OkHttpClient by lazy {
  OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()
}

/** Fetches the authenticated profile avatar without putting the gateway token in a URL. */
internal suspend fun fetchGatewayProfileAvatar(
  page: NodeRuntime.GatewayControlPage,
  profileId: String,
  revision: Long,
): GatewayProfileAvatarFetchResult =
  withContext(Dispatchers.IO) {
    val baseUrl = page.baseUrl.toHttpUrlOrNull() ?: return@withContext GatewayProfileAvatarFetchResult.Failure(code = null, network = true)
    val url =
      baseUrl
        .newBuilder()
        .addPathSegment("api")
        .addPathSegment("users")
        .addPathSegment(profileId)
        .addPathSegment("avatar")
        .addQueryParameter("v", revision.toString())
        .build()
    val request =
      Request
        .Builder()
        .url(url)
        .header("Accept", "image/*")
        .apply {
          val bearer = page.token?.trim()?.takeIf(String::isNotEmpty) ?: page.password?.trim()?.takeIf(String::isNotEmpty)
          if (bearer != null) header("Authorization", "Bearer $bearer")
        }.build()
    val client = profileAvatarHttpClient(page) ?: return@withContext GatewayProfileAvatarFetchResult.Failure(code = null, network = true)
    try {
      client.newCall(request).execute().use { response ->
        if (response.code == 404) return@use GatewayProfileAvatarFetchResult.NotFound
        if (!response.isSuccessful) {
          return@use GatewayProfileAvatarFetchResult.Failure(code = response.code)
        }
        val contentLength = response.body.contentLength()
        if (contentLength > MAX_PROFILE_AVATAR_BYTES) {
          return@use GatewayProfileAvatarFetchResult.Failure(code = response.code)
        }
        val bytes = response.body.bytes()
        if (bytes.isEmpty() || bytes.size > MAX_PROFILE_AVATAR_BYTES) {
          return@use GatewayProfileAvatarFetchResult.Failure(code = response.code)
        }
        val mime = response.header("Content-Type")?.substringBefore(';')?.trim()?.takeIf { it.startsWith("image/") }
        GatewayProfileAvatarFetchResult.Success(GatewayProfileAvatarImage(bytes = bytes, mimeType = mime))
      }
    } catch (_: IOException) {
      GatewayProfileAvatarFetchResult.Failure(code = null, network = true)
    }
  }

private fun profileAvatarHttpClient(page: NodeRuntime.GatewayControlPage): OkHttpClient? {
  val fingerprint = page.tlsFingerprintSha256?.trim()?.takeIf(String::isNotEmpty) ?: return profileAvatarHttpClient
  val tls =
    buildGatewayTlsConfig(
      GatewayTlsParams(
        required = true,
        expectedFingerprint = fingerprint,
        allowTOFU = false,
        stableId = "profile-avatar",
      ),
    ) ?: return null
  return OkHttpClient.Builder()
    .sslSocketFactory(tls.sslSocketFactory, tls.trustManager)
    .hostnameVerifier(tls.hostnameVerifier)
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()
}
