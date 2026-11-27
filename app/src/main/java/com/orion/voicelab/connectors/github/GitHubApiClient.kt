package com.orion.voicelab.connectors.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubRepositorySummary(
    val fullName: String,
    val description: String,
    val private: Boolean,
    val defaultBranch: String,
    val htmlUrl: String
)

data class GitHubIssueSummary(
    val number: Int,
    val title: String,
    val state: String,
    val htmlUrl: String,
    val updatedAt: String
)

/**
 * Minimal read-only GitHub REST adapter for an authorized GitHub App user token.
 * Write endpoints are deliberately not implemented here.
 */
class GitHubApiClient(
    private val credentials: GitHubCredentialManager,
    private val authenticator: GitHubDeviceFlowAuthenticator
) {
    suspend fun listRepositories(page: Int = 1, perPage: Int = 20): Result<List<GitHubRepositorySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safePage = page.coerceIn(1, MAX_PAGE)
                val safeLimit = perPage.coerceIn(1, MAX_PER_PAGE)
                val json = requestJson("/user/repos?sort=updated&per_page=$safeLimit&page=$safePage")
                buildList {
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        add(
                            GitHubRepositorySummary(
                                fullName = item.optString("full_name"),
                                description = item.optString("description").take(MAX_DESCRIPTION_LENGTH),
                                private = item.optBoolean("private"),
                                defaultBranch = item.optString("default_branch"),
                                htmlUrl = item.optString("html_url")
                            )
                        )
                    }
                }
            }
        }

    suspend fun listIssues(repository: String, state: String = "open", page: Int = 1): Result<List<GitHubIssueSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(REPOSITORY_PATTERN.matches(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
                val safeState = state.takeIf { it in setOf("open", "closed", "all") } ?: "open"
                val safePage = page.coerceIn(1, MAX_PAGE)
                val json = requestJson("/repos/$repository/issues?state=$safeState&per_page=$MAX_PER_PAGE&page=$safePage")
                buildList {
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        // Pull requests are issues in this endpoint; exclude them from issue-only results.
                        if (item.has("pull_request")) continue
                        add(
                            GitHubIssueSummary(
                                number = item.optInt("number"),
                                title = item.optString("title").take(MAX_TITLE_LENGTH),
                                state = item.optString("state"),
                                htmlUrl = item.optString("html_url"),
                                updatedAt = item.optString("updated_at")
                            )
                        )
                    }
                }
            }
        }

    private suspend fun requestJson(path: String): JSONArray {
        val token = credentials.getValidAccessToken(authenticator) ?: throw ReauthorizationRequiredException()
        val connection = (URL("$API_BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "ORION-Android")
        }
        try {
            val status = connection.responseCode
            if (status == 401) throw ReauthorizationRequiredException()
            if (status !in 200..299) throw GitHubApiException(status)
            val body = connection.inputStream.use { it.readBytes() }
            if (body.size > MAX_RESPONSE_BYTES) throw IllegalStateException("Respuesta de GitHub demasiado grande.")
            return JSONArray(String(body, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    class ReauthorizationRequiredException : IllegalStateException("La autorización de GitHub ha caducado. Vuelve a conectar la cuenta.")
    class GitHubApiException(status: Int) : IllegalStateException("GitHub no pudo completar la consulta (HTTP $status).")

    private companion object {
        const val API_BASE = "https://api.github.com"
        const val API_VERSION = "2022-11-28"
        const val MAX_PAGE = 100
        const val MAX_PER_PAGE = 30
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_TITLE_LENGTH = 300
        const val TIMEOUT_MS = 15_000
        val REPOSITORY_PATTERN = Regex("[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}")
    }
}
