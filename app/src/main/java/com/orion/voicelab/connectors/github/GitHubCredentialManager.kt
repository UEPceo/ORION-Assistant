package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import org.json.JSONObject
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists GitHub App user access and refresh tokens through the Keystore-backed store.
 * Access tokens are short-lived; refresh tokens are rotated when a new access token is issued.
 */
class GitHubCredentialManager(
    private val credentialStore: ConnectorCredentialStore,
    private val nowMs: () -> Long = System::currentTimeMillis
) {
    private val refreshMutex = Mutex()
    suspend fun save(result: GitHubDeviceAuthorizationResult.Authorized) {
        val now = nowMs()
        val payload = JSONObject()
            .put("access_token", result.accessToken)
            .put("refresh_token", result.refreshToken.orEmpty())
            .put(
                "access_expires_at_ms",
                result.expiresInSeconds?.let { now + it * 1_000L } ?: Long.MAX_VALUE
            )
            .put(
                "refresh_expires_at_ms",
                result.refreshTokenExpiresInSeconds?.let { now + it * 1_000L } ?: Long.MAX_VALUE
            )
            .toString()
        val bytes = payload.toByteArray(Charsets.UTF_8)
        try {
            credentialStore.save(ConnectorId.GITHUB, bytes)
        } finally {
            bytes.fill(0)
        }
    }

    suspend fun isConnected(): Boolean {
        val stored = readStoredCredential() ?: return false
        return stored.accessToken.isNotBlank() &&
            (stored.accessExpiresAtMs > nowMs() || (
                stored.refreshToken.isNotBlank() && stored.refreshExpiresAtMs > nowMs()
            ))
    }

    /** Returns a valid access token, rotating it with the GitHub App refresh token if needed. */
    suspend fun getValidAccessToken(
        authenticator: GitHubDeviceFlowAuthenticator
    ): String? = refreshMutex.withLock {
        val stored = readStoredCredential() ?: return@withLock null
        if (stored.accessToken.isNotBlank() && stored.accessExpiresAtMs > nowMs() + TOKEN_EXPIRY_SKEW_MS) return@withLock stored.accessToken
        if (stored.refreshToken.isBlank() || stored.refreshExpiresAtMs <= nowMs()) return@withLock null
        val refreshed = authenticator.refreshAccessToken(stored.refreshToken).getOrElse { error ->
            if (error.message.orEmpty().contains("caducado", ignoreCase = true)) throw ReauthorizationRequiredException()
            throw error
        }
        save(refreshed)
        refreshed.accessToken
    }

    suspend fun disconnect() {
        credentialStore.delete(ConnectorId.GITHUB)
    }

    private suspend fun readStoredCredential(): StoredCredential? {
        val bytes = credentialStore.read(ConnectorId.GITHUB) ?: return null
        return try {
            val payload = JSONObject(String(bytes, Charsets.UTF_8))
            StoredCredential(
                accessToken = payload.optString("access_token"),
                refreshToken = payload.optString("refresh_token"),
                accessExpiresAtMs = payload.optLong("access_expires_at_ms", 0L),
                refreshExpiresAtMs = payload.optLong("refresh_expires_at_ms", 0L)
            )
        } catch (_: Exception) {
            null
        } finally {
            bytes.fill(0)
        }
    }

    private data class StoredCredential(
        val accessToken: String,
        val refreshToken: String,
        val accessExpiresAtMs: Long,
        val refreshExpiresAtMs: Long
    )

    class ReauthorizationRequiredException : IllegalStateException("La sesión de GitHub requiere una nueva autorización.")

    private companion object {
        const val TOKEN_EXPIRY_SKEW_MS = 60_000L
    }
}
