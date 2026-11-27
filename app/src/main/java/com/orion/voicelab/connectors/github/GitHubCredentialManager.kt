package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import org.json.JSONObject

/**
 * Persists the minimum token metadata needed for future GitHub API requests.
 * The serialized token is passed immediately to the Keystore-backed store.
 */
class GitHubCredentialManager(
    private val credentialStore: ConnectorCredentialStore
) {
    suspend fun save(result: GitHubDeviceAuthorizationResult.Authorized) {
        val serialized = JSONObject()
            .put("access_token", result.accessToken)
            .put("token_type", result.tokenType)
            .put("scope", result.scope)
            .toString()
        credentialStore.save(ConnectorId.GITHUB, serialized.toByteArray(Charsets.UTF_8))
    }

    suspend fun isConnected(): Boolean =
        credentialStore.read(ConnectorId.GITHUB) != null

    suspend fun disconnect() {
        credentialStore.delete(ConnectorId.GITHUB)
    }
}
