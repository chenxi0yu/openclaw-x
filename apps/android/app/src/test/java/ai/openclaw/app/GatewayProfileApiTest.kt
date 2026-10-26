package ai.openclaw.app

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GatewayProfileApiTest {
  private val json = Json { ignoreUnknownKeys = true }

  @Test
  fun parsesSelfProfileFields() {
    val profile =
      parseGatewaySelfProfile(
        json = json,
        raw =
          """
          {
            "profile": {
              "id": "gateway-owner",
              "displayName": "小虾",
              "avatarMime": "image/webp",
              "mergedInto": null,
              "createdAt": 10,
              "updatedAt": 20,
              "emails": ["me@example.com", " "],
              "githubIdentity": {
                "login": "octocat",
                "profileUrl": "https://github.com/octocat",
                "avatarUrl": "https://avatars.githubusercontent.com/u/1"
              },
              "hasAvatar": true
            }
          }
          """.trimIndent(),
        gatewayId = "gateway-a",
      )!!

    assertEquals("gateway-a", profile.gatewayId)
    assertEquals("gateway-owner", profile.id)
    assertEquals("小虾", profile.displayName)
    assertEquals(listOf("me@example.com"), profile.emails)
    assertEquals("image/webp", profile.avatarMime)
    assertEquals(20L, profile.updatedAtMs)
    assertEquals(true, profile.hasAvatar)
    assertEquals("octocat", profile.githubLogin)
    assertNull(profile.mergedInto)
  }

  @Test
  fun selectsOwnerProfileAndIgnoresMergedRows() {
    val profiles =
      parseGatewayProfileList(
        json = json,
        raw =
          """
          {
            "profiles": [
              {"id": "merged", "displayName": "Old", "mergedInto": "gateway-owner", "emails": [], "hasAvatar": false, "updatedAt": 1},
              {"id": "gateway-owner", "displayName": "Owner", "mergedInto": null, "emails": [], "hasAvatar": false, "updatedAt": 2},
              {"id": "other", "displayName": "Other", "mergedInto": null, "emails": [], "hasAvatar": false, "updatedAt": 3}
            ]
          }
          """.trimIndent(),
        gatewayId = "gateway-a",
      )

    assertEquals("gateway-owner", selectGatewayProfile(profiles)?.id)
  }

  @Test
  fun selectsTheOnlyActiveProfileWhenOwnerIdIsAbsent() {
    val profiles =
      listOf(
        GatewayProfile(
          gatewayId = "gateway-a",
          id = "person-1",
          displayName = "Person",
          mergedInto = null,
        ),
        GatewayProfile(
          gatewayId = "gateway-a",
          id = "person-2",
          displayName = "Merged",
          mergedInto = "person-1",
        ),
      )

    assertEquals("person-1", selectGatewayProfile(profiles)?.id)
    assertNull(selectGatewayProfile(profiles.filter { it.id == "person-2" }))
  }

  @Test
  fun cachedProfileRoundTripPreservesPresentationFields() {
    val profile =
      GatewayProfile(
        gatewayId = "gateway-a",
        id = "gateway-owner",
        displayName = "小虾",
        emails = listOf("me@example.com"),
        avatarMime = "image/webp",
        hasAvatar = true,
        updatedAtMs = 20L,
        avatarRevision = "rev-1",
        canEdit = true,
        githubLogin = "octocat",
      )
    val cache = profile.toCachedProfile(fetchedAtMs = 30L)
    val decoded =
      json.decodeFromString(
        CachedGatewayProfile.serializer(),
        json.encodeToString(CachedGatewayProfile.serializer(), cache),
      )

    assertEquals(profile, decoded.toGatewayProfile())
    assertEquals(30L, decoded.fetchedAtMs)
    assertEquals(true, decoded.canEdit)
  }

  @Test
  fun displayNameIsTrimmedAndBounded() {
    assertEquals(null, validGatewayProfileDisplayName("  "))
    assertEquals("Name", validGatewayProfileDisplayName(" Name "))
    assertEquals(256, validGatewayProfileDisplayName("x".repeat(300))?.length)
  }
}
