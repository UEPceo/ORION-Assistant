package com.orion.voicelab.connectors.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
data class GitHubIssueDetail(val number: Int, val title: String, val state: String, val body: String, val author: String, val htmlUrl: String, val updatedAt: String)

data class GitHubCommitSummary(val sha: String, val message: String, val author: String, val committedAt: String, val htmlUrl: String)
data class GitHubWorkflowRunSummary(val name: String, val status: String, val conclusion: String, val branch: String, val htmlUrl: String, val updatedAt: String)
data class GitHubFileSummary(val name: String, val path: String, val type: String, val htmlUrl: String)

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

    suspend fun getIssue(repository: String, number: Int): Result<GitHubIssueDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(REPOSITORY_PATTERN.matches(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
                require(number > 0) { "Número de incidencia no válido." }
                val item = requestObject("/repos/$repository/issues/$number")
                GitHubIssueDetail(
                    number = item.optInt("number"),
                    title = item.optString("title").take(MAX_TITLE_LENGTH),
                    state = item.optString("state"),
                    body = item.optString("body").take(2_000),
                    author = item.optJSONObject("user")?.optString("login").orEmpty().take(100),
                    htmlUrl = item.optString("html_url"),
                    updatedAt = item.optString("updated_at")
                )
            }
        }

    suspend fun listCommits(repository: String, page: Int = 1): Result<List<GitHubCommitSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(REPOSITORY_PATTERN.matches(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
                val json = requestJson("/repos/$repository/commits?per_page=20&page=${page.coerceIn(1, MAX_PAGE)}")
                buildList {
                    for (i in 0 until json.length()) {
                        val item = json.optJSONObject(i) ?: continue
                        val commit = item.optJSONObject("commit") ?: continue
                        val author = commit.optJSONObject("author")
                        add(GitHubCommitSummary(
                            sha = item.optString("sha").take(12),
                            message = commit.optString("message").lineSequence().firstOrNull().orEmpty().take(MAX_TITLE_LENGTH),
                            author = author?.optString("name").orEmpty().take(100),
                            committedAt = author?.optString("date").orEmpty(),
                            htmlUrl = item.optString("html_url")
                        ))
                    }
                }
            }
        }

    suspend fun listWorkflowRuns(repository: String, page: Int = 1): Result<List<GitHubWorkflowRunSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(REPOSITORY_PATTERN.matches(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
                val root = requestObject("/repos/$repository/actions/runs?per_page=20&page=${page.coerceIn(1, MAX_PAGE)}")
                val runs = root.optJSONArray("workflow_runs") ?: JSONArray()
                buildList {
                    for (i in 0 until runs.length()) {
                        val item = runs.optJSONObject(i) ?: continue
                        add(GitHubWorkflowRunSummary(
                            name = item.optString("name").take(120),
                            status = item.optString("status"),
                            conclusion = item.optString("conclusion"),
                            branch = item.optString("head_branch").take(120),
                            htmlUrl = item.optString("html_url"),
                            updatedAt = item.optString("updated_at")
                        ))
                    }
                }
            }
        }

    suspend fun listFiles(repository: String, path: String = "", branch: String? = null): Result<List<GitHubFileSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(REPOSITORY_PATTERN.matches(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
                require(path.length <= 300 && !path.split('/').any { it == ".." }) { "Ruta de repositorio no válida." }
                val safePath = path.trim('/').split('/').filter { it.isNotBlank() }
                    .joinToString("/") { java.net.URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
                val safeBranch = branch?.takeIf { it.isNotBlank() }?.let {
                    "&ref=" + java.net.URLEncoder.encode(it.take(120), Charsets.UTF_8.name())
                }.orEmpty()
                val endpoint = "/repos/$repository/contents${if (safePath.isBlank()) "" else "/$safePath"}?$safeBranch"
                val json = requestAny(endpoint)
                val entries = if (json is JSONArray) json else JSONArray().put(json)
                buildList {
                    for (i in 0 until entries.length()) {
                        val item = entries.optJSONObject(i) ?: continue
                        add(GitHubFileSummary(
                            name = item.optString("name").take(200),
                            path = item.optString("path").take(500),
                            type = item.optString("type"),
                            htmlUrl = item.optString("html_url")
                        ))
                    }
                }
            }
        }

    private suspend fun requestJson(path: String): JSONArray {
        val value = requestAny(path)
        return value as? JSONArray ?: throw IllegalStateException("Respuesta inesperada de GitHub.")
    }

    private suspend fun requestObject(path: String): JSONObject {
        val value = requestAny(path)
        return value as? JSONObject ?: throw IllegalStateException("Respuesta inesperada de GitHub.")
    }

    private suspend fun requestAny(path: String): Any {
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
            val body = connection.inputStream.use { it.readBounded(MAX_RESPONSE_BYTES) }
            val text = String(body, Charsets.UTF_8)
            if (text.trimStart().startsWith("[")) JSONArray(text) else JSONObject(text)
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


private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8_192))
    val buffer = ByteArray(8_192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) {
            throw IllegalStateException("Respuesta del servicio externo demasiado grande.")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
