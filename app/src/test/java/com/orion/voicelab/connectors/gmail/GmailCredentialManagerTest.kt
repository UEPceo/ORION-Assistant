package com.orion.voicelab.connectors.gmail

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GmailCredentialManagerTest {
    @Test
    fun expiresAccessTokenAndDisconnects() = runBlocking {
        var now = 1_000_000L
        val store = FakeCredentialStore()
        val manager = GmailCredentialManager(store) { now }

        manager.save("temporary-access-token", expiresInSeconds = 60)
        assertTrue(manager.isConnected())

        now += 61_000L
        assertFalse(manager.isConnected())

        manager.disconnect()
        assertFalse(manager.isConnected())
    }

    private class FakeCredentialStore : ConnectorCredentialStore {
        private var value: ByteArray? = null

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
