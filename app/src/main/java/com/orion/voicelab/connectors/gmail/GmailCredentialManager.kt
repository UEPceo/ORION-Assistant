package com.orion.voicelab.connectors.gmail

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stores a short-lived Google access token encrypted by the shared Android Keystore store.
 * No refresh token or client secret is stored in the Android app.
 */
class GmailCredentialManager(
    private val credentialStore: ConnectorCredentialStore,
    private val silentRefresh: (suspend () -> RefreshedToken?)? = null,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val refreshMutex = Mutex()

    data class RefreshedToken(val accessToken: String, val expiresInSeconds: Long)
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

    /** Returns the current access token only while it is safely before expiry. */
    suspend fun getValidAccessToken(): String? = refreshMutex.withLock {
        val bytes = credentialStore.read(ConnectorId.GMAIL) ?: return@withLock null
        val current = try {
            val payload = JSONObject(String(bytes, Charsets.UTF_8))
            payload.optString("access_token") to payload.optLong("expires_at_ms", 0L)
        } catch (_: Exception) { null } finally { bytes.fill(0) }
        if (current == null) return@withLock null
        val (token, expiresAt) = current
        if (token.isNotBlank() && expiresAt > nowMs() + TOKEN_EXPIRY_SKEW_MS) return@withLock token
        val refresh = silentRefresh ?: return@withLock null
        // Google Identity returns a token directly when prior consent is still valid.
        // If it requires an interactive resolution, leave reconnection to the UI.
        val renewed = refresh() ?: return@withLock null
        save(renewed.accessToken, renewed.expiresInSeconds)
        renewed.accessToken
    }

    suspend fun isConnected(): Boolean {
        val bytes = credentialStore.read(ConnectorId.GMAIL) ?: return false
        return try {
            val payload = JSONObject(String(bytes, Charsets.UTF_8))
            payload.optString("access_token").isNotBlank() &&
                (payload.optLong("expires_at_ms", 0L) > nowMs() || silentRefresh != null)
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
        const val TOKEN_EXPIRY_SKEW_MS = 60_000L
    }
}
