package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId

/**
 * Persists the minimum token metadata needed for future GitHub API requests.
 * The serialized token is passed immediately to the Keystore-backed store.
 */
class GitHubCredentialManager(
    private val credentialStore: ConnectorCredentialStore
) {
    suspend fun save(result: GitHubDeviceAuthorizationResult.Authorized) {
        val credentialBytes = result.accessToken.toByteArray(Charsets.UTF_8)
        try {
            credentialStore.save(ConnectorId.GITHUB, credentialBytes)
        } finally {
            credentialBytes.fill(0)
        }
    }

    suspend fun isConnected(): Boolean {
        val stored = credentialStore.read(ConnectorId.GITHUB) ?: return false
        stored.fill(0)
        return true
    }

    suspend fun disconnect() {
        credentialStore.delete(ConnectorId.GITHUB)
    }
}
