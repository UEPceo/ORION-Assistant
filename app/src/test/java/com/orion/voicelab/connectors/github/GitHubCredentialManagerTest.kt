package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
                scope = "read:user"
            )
        )

        assertTrue(manager.isConnected())
        assertArrayEquals(token.toByteArray(Charsets.UTF_8), store.value!!)

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
