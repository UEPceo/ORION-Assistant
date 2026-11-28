package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitHubCredentialManagerTest {
    @Test
    fun savesAndDetectsCredentialThenDisconnects() = runBlocking {
        val store = FakeCredentialStore()
        val manager = GitHubCredentialManager(store)
        val token = "test-token-not-for-production"

        manager.save(
            GitHubDeviceAuthorizationResult.Authorized(
                accessToken = token,
                tokenType = "bearer",
                scope = "",
                expiresInSeconds = 28_800,
                refreshToken = "test-refresh-token",
                refreshTokenExpiresInSeconds = 15_897_600
            )
        )

        assertTrue(manager.isConnected())
        val stored = org.json.JSONObject(String(store.value!!, Charsets.UTF_8))
        assertEquals(token, stored.getString("access_token"))
        assertEquals("test-refresh-token", stored.getString("refresh_token"))

        manager.disconnect()

        assertFalse(manager.isConnected())
    }


    @Test
    fun expiredAccessTokenRefreshesAndRotatesBothTokens() = runBlocking {
        var now = 5_000_000L
        val store = FakeCredentialStore()
        val manager = GitHubCredentialManager(store) { now }
        manager.save(authorized("old-access", "old-refresh", 30, 3_600))
        now += 40_000L
        var calls = 0
        val refresher = GitHubTokenRefresher {
            calls++
            Result.success(authorized("new-access", "new-refresh", 3_600, 86_400))
        }

        assertEquals("new-access", manager.getValidAccessToken(refresher))
        assertEquals(1, calls)
        val stored = org.json.JSONObject(String(store.value!!, Charsets.UTF_8))
        assertEquals("new-refresh", stored.getString("refresh_token"))
    }

    @Test
    fun concurrentRequestsDoNotReuseRotatingRefreshToken() = runBlocking {
        var now = 8_000_000L
        val store = FakeCredentialStore()
        val manager = GitHubCredentialManager(store) { now }
        manager.save(authorized("old-access", "single-use-refresh", 10, 3_600))
        now += 20_000L
        var calls = 0
        val refresher = GitHubTokenRefresher {
            calls++
            delay(40)
            Result.success(authorized("new-access", "rotated-refresh", 3_600, 86_400))
        }

        val first = async { manager.getValidAccessToken(refresher) }
        val second = async { manager.getValidAccessToken(refresher) }
        assertEquals("new-access", first.await())
        assertEquals("new-access", second.await())
        assertEquals(1, calls)
    }

    @Test
    fun transientRefreshNetworkFailureIsNotReportedAsExpired() = runBlocking {
        var now = 9_000_000L
        val store = FakeCredentialStore()
        val manager = GitHubCredentialManager(store) { now }
        manager.save(authorized("old-access", "refresh-token", 10, 3_600))
        now += 20_000L
        val refresher = GitHubTokenRefresher {
            Result.failure(IllegalStateException("Network unreachable"))
        }

        val error = runCatching { manager.getValidAccessToken(refresher) }.exceptionOrNull()
        assertEquals("Network unreachable", error?.message)
        assertTrue(manager.isConnected())
    }

    @Test
    fun invalidRefreshGrantRequiresAuthorizationAgain() = runBlocking {
        var now = 10_000_000L
        val store = FakeCredentialStore()
        val manager = GitHubCredentialManager(store) { now }
        manager.save(authorized("old-access", "invalid-refresh", 10, 3_600))
        now += 20_000L
        val refresher = GitHubTokenRefresher {
            Result.failure(IllegalStateException("La sesión de GitHub ha caducado."))
        }

        val error = runCatching { manager.getValidAccessToken(refresher) }.exceptionOrNull()
        assertTrue(error is GitHubCredentialManager.ReauthorizationRequiredException)
    }

    private fun authorized(
        access: String,
        refresh: String,
        accessSeconds: Long,
        refreshSeconds: Long
    ) = GitHubDeviceAuthorizationResult.Authorized(
        accessToken = access,
        tokenType = "bearer",
        scope = "",
        expiresInSeconds = accessSeconds,
        refreshToken = refresh,
        refreshTokenExpiresInSeconds = refreshSeconds
    )

    private class FakeCredentialStore : ConnectorCredentialStore {
        var value: ByteArray? = null

        override suspend fun save(connectorId: ConnectorId, credential: ByteArray) {
            value = credential.copyOf()
        }

        override suspend fun read(connectorId: ConnectorId): ByteArray? = value?.copyOf()

        override suspend fun delete(connectorId: ConnectorId) {
            value?.fill(0)
            value = null
        }
    }
}
