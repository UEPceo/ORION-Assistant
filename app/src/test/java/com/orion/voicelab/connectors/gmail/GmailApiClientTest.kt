package com.orion.voicelab.connectors.gmail

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GmailApiClientTest {
    @Test
    fun openingMessageRequestsMetadataOnlyAndNeverReturnsBody() = runBlocking {
        val store = FakeCredentialStore()
        val credentials = GmailCredentialManager(store)
        credentials.save("unit-test-token")
        var requestedUrl = ""
        val client = GmailApiClient(credentials) { url, token ->
            requestedUrl = url
            assertEquals("unit-test-token", token)
            JSONObject(
                """{"id":"message-123","threadId":"thread-1","snippet":"untrusted email snippet","payload":{"mimeType":"text/plain","headers":[{"name":"From","value":"sender@example.test"},{"name":"Subject","value":"Subject"},{"name":"Date","value":"Today"}],"body":{"data":"dW50cnVzdGVkIGVtYWlsIGJvZHk="}}}"""
            )
        }

        val message = client.getMessage("message-123").getOrThrow()

        assertTrue(requestedUrl.contains("format=metadata"))
        assertFalse(requestedUrl.contains("format=full"))
        assertEquals("sender@example.test", message.from)
        assertTrue(message.bodyText.isBlank())
        assertEquals("untrusted email snippet", message.snippet)
    }

    private class FakeCredentialStore : ConnectorCredentialStore {
        private var value: ByteArray? = null
        override suspend fun save(connectorId: ConnectorId, credential: ByteArray) { value = credential.copyOf() }
        override suspend fun read(connectorId: ConnectorId): ByteArray? = value?.copyOf()
        override suspend fun delete(connectorId: ConnectorId) { value?.fill(0); value = null }
    }
}
