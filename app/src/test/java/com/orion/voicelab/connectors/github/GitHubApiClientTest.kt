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
        val client = GitHubApiClient(
            credentials,
            GitHubDeviceFlowAuthenticator("test-client"),
            requestOverride = { path ->
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
        )

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
        val client = GitHubApiClient(
            credentials,
            GitHubDeviceFlowAuthenticator("test-client"),
            requestOverride = {
                called = true
                JSONArray()
            }
        )

        val result = client.listIssues("owner/..")
        assertFalse(result.isSuccess)
        assertFalse(called)
    }

    @Test
    fun workflowRunListingPreservesRunIdentityForCorrelation() = runBlocking {
        val store = FakeCredentialStore()
        val credentials = GitHubCredentialManager(store)
        credentials.save(GitHubDeviceAuthorizationResult.Authorized(
            accessToken = "test-token", tokenType = "bearer", scope = "",
            expiresInSeconds = 3_600, refreshToken = "refresh-token",
            refreshTokenExpiresInSeconds = 86_400
        ))
        val client = GitHubApiClient(credentials, GitHubDeviceFlowAuthenticator("test-client"),
            requestOverride = {
                org.json.JSONObject().put("workflow_runs", JSONArray().put(
                    org.json.JSONObject()
                        .put("id", 98765L).put("name", "ORION quality checks")
                        .put("status", "completed").put("conclusion", "failure")
                        .put("head_branch", "main").put("head_sha", "a".repeat(40))
                        .put("event", "push").put("run_attempt", 2)
                        .put("html_url", "https://github.com/owner/repo/actions/runs/98765")
                        .put("updated_at", "2026-11-28T10:00:00Z")
                ))
            })
        val run = client.listWorkflowRuns("owner/repo").getOrThrow().single()
        assertEquals(98765L, run.runId)
        assertEquals("a".repeat(40), run.headSha)
        assertEquals(2, run.runAttempt)
        assertEquals("push", run.event)
    }

    @Test
    fun workflowJobListingReturnsOnlyBoundedStructuredStepMetadata() = runBlocking {
        val store = FakeCredentialStore()
        val credentials = GitHubCredentialManager(store)
        credentials.save(GitHubDeviceAuthorizationResult.Authorized(
            accessToken = "test-token", tokenType = "bearer", scope = "",
            expiresInSeconds = 3_600, refreshToken = "refresh-token",
            refreshTokenExpiresInSeconds = 86_400
        ))
        var requested = ""
        val client = GitHubApiClient(credentials, GitHubDeviceFlowAuthenticator("test-client"),
            requestOverride = { path ->
                requested = path
                org.json.JSONObject().put("jobs", JSONArray().put(
                    org.json.JSONObject()
                        .put("id", 123L).put("name", "Unit tests and Robolectric")
                        .put("status", "completed").put("conclusion", "failure")
                        .put("started_at", "2026-11-28T10:00:00Z")
                        .put("completed_at", "2026-11-28T10:02:00Z")
                        .put("steps", JSONArray()
                            .put(org.json.JSONObject().put("name", "Checkout").put("status", "completed").put("conclusion", "success").put("number", 1))
                            .put(org.json.JSONObject().put("name", "Run tests").put("status", "completed").put("conclusion", "failure").put("number", 2))
                        )
                ))
            })
        val job = client.listWorkflowRunJobs("owner/repo", 456L).getOrThrow().single()
        assertTrue(requested.endsWith("/actions/runs/456/jobs?per_page=100"))
        assertEquals(123L, job.jobId)
        assertEquals("Unit tests and Robolectric", job.name)
        assertEquals("failure", job.failedSteps.single().conclusion)
        assertEquals("Run tests", job.failedSteps.single().name)
    }

    @Test
    fun invalidWorkflowRunIdIsRejectedBeforeTransport() = runBlocking {
        val store = FakeCredentialStore()
        val credentials = GitHubCredentialManager(store)
        var called = false
        val client = GitHubApiClient(credentials, GitHubDeviceFlowAuthenticator("test-client"),
            requestOverride = { called = true; org.json.JSONObject() })
        assertFalse(client.listWorkflowRunJobs("owner/repo", 0L).isSuccess)
        assertFalse(called)
    }

    private class FakeCredentialStore : ConnectorCredentialStore {
        private var value: ByteArray? = null
        override suspend fun save(connectorId: ConnectorId, credential: ByteArray) { value = credential.copyOf() }
        override suspend fun read(connectorId: ConnectorId): ByteArray? = value?.copyOf()
        override suspend fun delete(connectorId: ConnectorId) { value?.fill(0); value = null }
    }
}
