package com.orion.voicelab.connectors.github

import com.orion.voicelab.connectors.ConnectorCredentialStore
import com.orion.voicelab.connectors.ConnectorId
import com.orion.voicelab.engineering.CodeChangeProposal
import com.orion.voicelab.engineering.CodeFileChange
import com.orion.voicelab.engineering.CodeFileOperation
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GitHubApiClientWriteTest {
    private val head = "a".repeat(40)
    private val baseTree = "b".repeat(40)
    private val blob = "c".repeat(40)
    private val newTree = "d".repeat(40)
    private val newCommit = "e".repeat(40)

    private fun client(
        transport: suspend (String, String, JSONObject?) -> Any
    ): GitHubApiClient {
        val store = FakeCredentialStore()
        val credentials = GitHubCredentialManager(store)
        runBlocking {
            credentials.save(
                GitHubDeviceAuthorizationResult.Authorized(
                    accessToken = "test-token", tokenType = "bearer", scope = "",
                    expiresInSeconds = 3_600, refreshToken = "refresh-token",
                    refreshTokenExpiresInSeconds = 86_400
                )
            )
        }
        return GitHubApiClient(
            credentials,
            GitHubDeviceFlowAuthenticator("test-client"),
            requestOverride = null,
            transportOverride = transport
        )
    }

    @Test
    fun createsBranchFromExplicitBaseRef() = runBlocking {
        val calls = mutableListOf<Pair<String, String>>()
        val api = client { method, path, body ->
            calls += method to path
            when (method to path) {
                "GET" to "/repos/owner/repo/branches/main" ->
                    JSONObject().put("commit", JSONObject().put("sha", head))
                "POST" to "/repos/owner/repo/git/refs" -> {
                    assertEquals("refs/heads/orion/task", body?.optString("ref"))
                    JSONObject().put("ref", "refs/heads/orion/task")
                        .put("object", JSONObject().put("sha", head))
                }
                else -> error("Unexpected request: $method $path")
            }
        }

        val result = api.createBranch("owner/repo", "orion/task", "main").getOrThrow()
        assertEquals("orion/task", result.name)
        assertEquals(head, result.sha)
        assertEquals(2, calls.size)
    }

    @Test
    fun commitsValidatedMultiFilePatchAtomicallyOnWorkBranch() = runBlocking {
        val calls = mutableListOf<String>()
        val api = client { method, path, body ->
            calls += "$method $path"
            when {
                method == "GET" && path.endsWith("/git/ref/heads/orion/task") ->
                    JSONObject().put("object", JSONObject().put("sha", head))
                method == "GET" && path.endsWith("/git/commits/$head") ->
                    JSONObject().put("tree", JSONObject().put("sha", baseTree))
                method == "GET" && path.endsWith("/git/trees/$baseTree?recursive=1") ->
                    JSONObject().put("tree", JSONArray())
                method == "POST" && path.endsWith("/git/blobs") -> {
                    assertEquals("base64", body?.optString("encoding"))
                    JSONObject().put("sha", blob)
                }
                method == "POST" && path.endsWith("/git/trees") -> {
                    val entries = body?.optJSONArray("tree") ?: JSONArray()
                    assertEquals(1, entries.length())
                    assertEquals("app/NewFile.kt", entries.getJSONObject(0).optString("path"))
                    JSONObject().put("sha", newTree)
                }
                method == "POST" && path.endsWith("/git/commits") -> {
                    assertEquals(head, body?.optJSONArray("parents")?.optString(0))
                    JSONObject().put("sha", newCommit)
                }
                method == "PATCH" && path.endsWith("/git/refs/heads/orion/task") ->
                    JSONObject().put("object", JSONObject().put("sha", newCommit))
                else -> error("Unexpected request: $method $path")
            }
        }
        val proposal = CodeChangeProposal(
            repository = "owner/repo",
            baseBranch = "main",
            workBranch = "orion/task",
            title = "Add new file",
            summary = "Create a source file.",
            changes = listOf(
                CodeFileChange("app/NewFile.kt", CodeFileOperation.CREATE, "class NewFile")
            )
        )

        val result = api.commitChanges(proposal, head).getOrThrow()
        assertEquals(newCommit, result.commitSha)
        assertEquals("orion/task", result.branch)
        assertTrue(calls.last().startsWith("PATCH "))
        assertTrue(calls.any { it.contains("/git/trees") })
        assertTrue(calls.any { it.contains("/git/commits") })
    }

    @Test
    fun refusesToCommitWhenWorkBranchMovedSinceReview() = runBlocking {
        val api = client { method, path, _ ->
            when {
                method == "GET" && path.endsWith("/git/ref/heads/orion/task") ->
                    JSONObject().put("object", JSONObject().put("sha", "f".repeat(40)))
                else -> error("The stale branch must be rejected before more requests.")
            }
        }
        val proposal = CodeChangeProposal(
            repository = "owner/repo",
            baseBranch = "main",
            workBranch = "orion/task",
            title = "Change",
            summary = "Test stale branch.",
            changes = listOf(CodeFileChange("A.kt", CodeFileOperation.CREATE, "class A"))
        )

        val result = api.commitChanges(proposal, head)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("ha cambiado"))
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
