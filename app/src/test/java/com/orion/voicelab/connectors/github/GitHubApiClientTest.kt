package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitHubApiClientTest {
    @Test
    fun repositoryListingUsesExplicitAffiliationsAndMapsResults() = runBlocking {
        val store = FakeCredentialStore()
        val credentials = GitHubCredentialManager(store)
        credentials.save(
            GitHubDeviceAuthorizationResult.Authorized(
                accessToken = "test-token", tokenType = "bearer", scope = "",
                expiresInSeconds = 3_600, refreshToken = "refresh-token",
                refreshTokenExpiresInSeconds = 86_400
            )
        )
        var requestedPath = ""
        val client = GitHubApiClient(credentials, GitHubDeviceFlowAuthenticator("test-client")) { path ->
            requestedPath = path
            JSONArray().put(
                org.json.JSONObject()
                    .put("full_name", "owner/repository")
                    .put("description", "description")
                    .put("private", false)
                    .put("default_branch", "main")
                    .put("html_url", "https://github.com/owner/repository")
            )
        }

        val repositories = client.listRepositories().getOrThrow()

        assertTrue(requestedPath.contains("per_page=30"))
        assertTrue(requestedPath.contains("visibility=all"))
        assertTrue(requestedPath.contains("affiliation=owner,collaborator,organization_member"))
        assertEquals("owner/repository", repositories.single().fullName)
    }

    @Test
    fun traversalRepositoryIsRejectedBeforeTransport() = runBlocking {
        val store = FakeCredentialStore()
        val credentials = GitHubCredentialManager(store)
        credentials.save(
            GitHubDeviceAuthorizationResult.Authorized(
                accessToken = "test-token", tokenType = "bearer", scope = "",
                expiresInSeconds = 3_600, refreshToken = "refresh-token",
                refreshTokenExpiresInSeconds = 86_400
            )
        )
        var called = false
        val client = GitHubApiClient(credentials, GitHubDeviceFlowAuthenticator("test-client")) {
            called = true
            JSONArray()
        }

        val result = client.listIssues("owner/..")
        assertFalse(result.isSuccess)
        assertFalse(called)
    }

    private class FakeCredentialStore : ConnectorCredentialStore {
        private var value: ByteArray? = null
        override suspend fun save(connectorId: ConnectorId, credential: ByteArray) { value = credential.copyOf() }
        override suspend fun read(connectorId: ConnectorId): ByteArray? = value?.copyOf()
        override suspend fun delete(connectorId: ConnectorId) { value?.fill(0); value = null }
    }
}
