package com.orion.voicelab.connectors.gmail

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import org.json.JSONObject

/**
 * Stores a short-lived Google access token encrypted by the shared Android Keystore store.
 * No refresh token or client secret is stored in the Android app.
 */
class GmailCredentialManager(
    private val credentialStore: ConnectorCredentialStore,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    suspend fun save(accessToken: String, expiresInSeconds: Long = DEFAULT_TOKEN_LIFETIME_SECONDS) {
        require(accessToken.isNotBlank()) { "Google access token cannot be empty" }
        val payload = JSONObject()
            .put("access_token", accessToken)
            .put("expires_at_ms", nowMs() + expiresInSeconds.coerceAtLeast(60L) * 1_000L)
            .toString()
        val bytes = payload.toByteArray(Charsets.UTF_8)
        try {
            credentialStore.save(ConnectorId.GMAIL, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun isConnected(): Boolean {
        val bytes = credentialStore.read(ConnectorId.GMAIL) ?: return false
        return try {
            val payload = JSONObject(String(bytes, Charsets.UTF_8))
            payload.optLong("expires_at_ms", 0L) > nowMs() &&
                payload.optString("access_token").isNotBlank()
        } catch (_: Exception) {
            false
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun disconnect() {
        credentialStore.delete(ConnectorId.GMAIL)
    }

    private companion object {
        const val DEFAULT_TOKEN_LIFETIME_SECONDS = 3_600L
    }
}
