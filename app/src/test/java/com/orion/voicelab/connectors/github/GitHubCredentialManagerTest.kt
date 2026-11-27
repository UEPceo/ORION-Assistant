package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.runBlocking
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
