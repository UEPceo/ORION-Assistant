package com.orion.voicelab.connectors.gmail

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
class GmailCredentialManagerTest {
    @Test
    fun expiresAccessTokenAndDisconnects() = runBlocking {
        var now = 1_000_000L
        val store = FakeCredentialStore()
        val manager = GmailCredentialManager(store) { now }

        manager.save("temporary-access-token", expiresInSeconds = 120)
        assertTrue(manager.isConnected())
        assertEquals("temporary-access-token", manager.getValidAccessToken())

        now += 61_000L
        assertTrue(manager.isConnected())
        assertTrue(manager.getValidAccessToken() == null)

        now += 60_000L
        assertFalse(manager.isConnected())

        manager.disconnect()
        assertFalse(manager.isConnected())
    }


    @Test
    fun expiredTokenIsSilentlyRenewedAfterPriorConsent() = runBlocking {
        var now = 2_000_000L
        var refreshCount = 0
        val store = FakeCredentialStore()
        val manager = GmailCredentialManager(
            credentialStore = store,
            silentRefresh = {
                refreshCount++
                GmailCredentialManager.RefreshedToken("renewed-token", 3_600)
            },
            nowMs = { now }
        )
        manager.save("old-token", expiresInSeconds = 60)
        now += 61_000L

        assertEquals("renewed-token", manager.getValidAccessToken())
        assertEquals(1, refreshCount)
        assertTrue(manager.isConnected())
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
