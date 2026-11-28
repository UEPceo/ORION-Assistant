package com.orion.voicelab.connectors.github

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import android.util.Base64
import com.orion.voicelab.engineering.CodeChangeProposal
import com.orion.voicelab.engineering.CodeChangeValidator
import com.orion.voicelab.engineering.CodeFileOperation

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
data class GitHubWorkflowRunSummary(
    val name: String,
    val status: String,
    val conclusion: String,
    val branch: String,
    val htmlUrl: String,
    val updatedAt: String,
    val runId: Long = 0L,
    val headSha: String = "",
    val event: String = "",
    val runAttempt: Int = 1
)
data class GitHubWorkflowStepSummary(val name: String, val status: String, val conclusion: String, val number: Int)
data class GitHubWorkflowJobSummary(
    val jobId: Long,
    val name: String,
    val status: String,
    val conclusion: String,
    val startedAt: String,
    val completedAt: String,
    val steps: List<GitHubWorkflowStepSummary>
) {
    val failedSteps: List<GitHubWorkflowStepSummary>
        get() = steps.filter { it.conclusion == "failure" }
}
data class GitHubFileSummary(val name: String, val path: String, val type: String, val htmlUrl: String)
data class GitHubFileContent(val name: String, val path: String, val content: String, val sha: String, val sizeBytes: Int, val htmlUrl: String)
data class GitHubBranchSummary(val name: String, val sha: String)
data class GitHubTreeEntry(val path: String, val type: String, val mode: String, val sha: String)
data class GitHubCommitResult(val commitSha: String, val branch: String)
data class GitHubPullRequestSummary(val number: Int, val title: String, val url: String, val draft: Boolean)

/**
 * GitHub REST adapter for an authorized GitHub App user token.
 *
 * Repository writes are restricted to explicit branch/commit/PR operations. Multi-file
 * patches use the Git Data API and update the branch ref only after the complete commit
 * has been created, avoiding a partially applied set of Contents API writes.
 */
class GitHubApiClient(
    private val credentials: GitHubCredentialManager,
    private val authenticator: GitHubDeviceFlowAuthenticator,
    private val requestOverride: (suspend (path: String) -> Any)? = null,
    private val transportOverride: (suspend (method: String, path: String, body: JSONObject?) -> Any)? = null
) {
    suspend fun listRepositories(page: Int = 1, perPage: Int = 30): Result<List<GitHubRepositorySummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val safePage = page.coerceIn(1, MAX_PAGE)
                val safeLimit = perPage.coerceIn(1, MAX_PER_PAGE)
                val json = requestJson("/user/repos?visibility=all&affiliation=owner,collaborator,organization_member&sort=updated&per_page=$safeLimit&page=$safePage")
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
                require(isValidRepository(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
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
                require(isValidRepository(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
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
                require(isValidRepository(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
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
                require(isValidRepository(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
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
                            updatedAt = item.optString("updated_at"),
                            runId = item.optLong("id"),
                            headSha = item.optString("head_sha").take(40),
                            event = item.optString("event").take(40),
                            runAttempt = item.optInt("run_attempt", 1).coerceAtLeast(1)
                        ))
                    }
                }
            }
        }

    /**
     * Returns bounded, structured job/step metadata for one Actions run.
     * This endpoint does not download logs or execute project code.
     */
    suspend fun listWorkflowRunJobs(repository: String, runId: Long): Result<List<GitHubWorkflowJobSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isValidRepository(repository)) { "Indica un repositorio válido." }
                require(runId > 0L) { "Identificador de ejecución no válido." }
                val root = requestObject("/repos/$repository/actions/runs/$runId/jobs?per_page=100")
                val jobs = root.optJSONArray("jobs") ?: JSONArray()
                buildList {
                    for (index in 0 until minOf(jobs.length(), MAX_WORKFLOW_JOBS)) {
                        val item = jobs.optJSONObject(index) ?: continue
                        val rawSteps = item.optJSONArray("steps") ?: JSONArray()
                        val steps = buildList {
                            for (stepIndex in 0 until minOf(rawSteps.length(), MAX_WORKFLOW_STEPS)) {
                                val step = rawSteps.optJSONObject(stepIndex) ?: continue
                                add(GitHubWorkflowStepSummary(
                                    name = step.optString("name").take(160),
                                    status = step.optString("status").take(32),
                                    conclusion = step.optString("conclusion").take(32),
                                    number = step.optInt("number")
                                ))
                            }
                        }
                        add(GitHubWorkflowJobSummary(
                            jobId = item.optLong("id"),
                            name = item.optString("name").take(160),
                            status = item.optString("status").take(32),
                            conclusion = item.optString("conclusion").take(32),
                            startedAt = item.optString("started_at").take(40),
                            completedAt = item.optString("completed_at").take(40),
                            steps = steps
                        ))
                    }
                }
            }
        }

    suspend fun listFiles(repository: String, path: String = "", branch: String? = null): Result<List<GitHubFileSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isValidRepository(repository)) { "Indica un repositorio válido con formato propietario/nombre." }
                require(path.isBlank() || CodeChangeValidator.isSafePath(path)) { "Ruta de repositorio no válida." }
                branch?.takeIf { it.isNotBlank() }?.let { require(isSafeBranch(it)) { "Rama no válida." } }
                val safePath = path.trim('/').split('/').filter { it.isNotBlank() }
                    .joinToString("/") { java.net.URLEncoder.encode(it, Charsets.UTF_8.name()).replace("+", "%20") }
                val query = branch?.takeIf { it.isNotBlank() }?.let {
                    "?ref=" + java.net.URLEncoder.encode(it, Charsets.UTF_8.name())
                }.orEmpty()
                val endpoint = "/repos/$repository/contents${if (safePath.isBlank()) "" else "/$safePath"}$query"
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

    suspend fun getBranchHead(repository: String, branch: String): Result<GitHubBranchSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isValidRepository(repository)) { "Repositorio no válido." }
                require(isSafeBranch(branch)) { "Nombre de rama no válido." }
                val item = requestObject("/repos/$repository/branches/${encodeBranchPath(branch)}")
                val sha = item.optJSONObject("commit")?.optString("sha").orEmpty()
                require(sha.matches(Regex("[a-fA-F0-9]{40}"))) { "GitHub no devolvió el SHA de la rama." }
                GitHubBranchSummary(item.optString("name", branch), sha)
            }
        }

    suspend fun getFileTreeMetadata(
        repository: String,
        branch: String,
        selectedPaths: List<String>
    ): Result<Map<String, GitHubTreeEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidRepository(repository) && isSafeBranch(branch)) { "Repositorio o rama no válidos." }
            require(selectedPaths.isNotEmpty() && selectedPaths.size <= CodeChangeValidator.MAX_FILES) {
                "La selección de archivos no es válida."
            }
            selectedPaths.forEach { require(CodeChangeValidator.isSafePath(it)) { "Ruta no permitida." } }
            val head = getBranchHead(repository, branch).getOrThrow()
            val commit = requestObject("/repos/$repository/git/commits/${head.sha}")
            val treeSha = commit.optJSONObject("tree")?.optString("sha").orEmpty()
            require(treeSha.matches(Regex("[a-fA-F0-9]{40}"))) { "GitHub no devolvió el árbol de la rama." }
            val tree = requestObject("/repos/$repository/git/trees/$treeSha?recursive=1")
            require(!tree.optBoolean("truncated", false)) { "El árbol del repositorio está truncado; no se puede validar con seguridad." }
            val requested = selectedPaths.toSet()
            val entries = linkedMapOf<String, GitHubTreeEntry>()
            val items = tree.optJSONArray("tree") ?: JSONArray()
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path in requested) {
                    entries[path] = GitHubTreeEntry(path, item.optString("type"), item.optString("mode"), item.optString("sha"))
                }
            }
            require(entries.keys.containsAll(requested)) { "Uno o varios archivos no existen en la rama seleccionada." }
            entries
        }
    }

    suspend fun getFile(repository: String, path: String, branch: String? = null): Result<GitHubFileContent> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isValidRepository(repository)) { "Repositorio no válido." }
                require(CodeChangeValidator.isSafePath(path)) { "Ruta de archivo no válida." }
                val encodedPath = path.split('/').joinToString("/") { encodePathSegment(it) }
                val ref = branch?.takeIf { it.isNotBlank() }?.let {
                    require(isSafeBranch(it)) { "Rama no válida." }
                    "?ref=${encodePathSegment(it)}"
                }.orEmpty()
                val item = requestObject("/repos/$repository/contents/$encodedPath$ref")
                require(item.optString("type") == "file") { "La ruta seleccionada no es un archivo." }
                require(item.optString("encoding") == "base64") { "GitHub no devolvió el archivo en formato Base64." }
                val encoded = item.optString("content").replace(Regex("\\s+"), "")
                require(encoded.isNotBlank() && encoded.length <= MAX_FILE_CONTENT_BASE64) {
                    "El archivo está vacío o supera el límite de lectura de ORION."
                }
                val decoded = Base64.decode(encoded, Base64.DEFAULT)
                require(decoded.size <= MAX_FILE_CONTENT_BYTES) { "El archivo supera el límite de lectura de ORION." }
                val text = String(decoded, Charsets.UTF_8)
                require(!text.contains('\u0000')) { "El archivo parece binario y no se mostrará como código." }
                GitHubFileContent(
                    name = item.optString("name").take(200),
                    path = item.optString("path").take(500),
                    content = text,
                    sha = item.optString("sha"),
                    sizeBytes = decoded.size,
                    htmlUrl = item.optString("html_url")
                )
            }
        }

    suspend fun createBranch(repository: String, branch: String, baseBranch: String): Result<GitHubBranchSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(isValidRepository(repository)) { "Repositorio no válido." }
                require(isSafeBranch(branch) && isSafeBranch(baseBranch)) { "Nombre de rama no válido." }
                require(!branch.equals("main", true) && !branch.equals("master", true)) {
                    "ORION no puede crear cambios directamente en main/master."
                }
                val base = requestObject("/repos/$repository/branches/${encodeBranchPath(baseBranch)}")
                val baseSha = base.optJSONObject("commit")?.optString("sha").orEmpty()
                require(baseSha.matches(Regex("[a-fA-F0-9]{40}"))) { "GitHub no devolvió el SHA de la rama base." }
                val payload = JSONObject()
                    .put("ref", "refs/heads/$branch")
                    .put("sha", baseSha)
                val created = requestObject("/repos/$repository/git/refs", "POST", payload)
                val objectSha = created.optJSONObject("object")?.optString("sha").orEmpty()
                require(objectSha.isNotBlank()) { "GitHub no confirmó la creación de la rama." }
                GitHubBranchSummary(created.optString("ref").removePrefix("refs/heads/"), objectSha)
            }
        }

    /**
     * Applies a validated multi-file patch as one Git commit on an existing work branch.
     * expectedHeadSha prevents applying a proposal to a branch that changed after review.
     * expectedBlobSha prevents overwriting/deleting files whose content changed meanwhile.
     */
    suspend fun commitChanges(
        proposal: CodeChangeProposal,
        expectedHeadSha: String
    ): Result<GitHubCommitResult> = withContext(Dispatchers.IO) {
        runCatching {
            val validation = CodeChangeValidator.validate(proposal)
            require(validation.valid) { validation.errors.joinToString(" ") }
            require(isValidRepository(proposal.repository)) { "Repositorio no válido." }
            require(expectedHeadSha.matches(Regex("[a-fA-F0-9]{40}"))) { "SHA base no válido." }
            require(proposal.workBranch != "main" && proposal.workBranch != "master") {
                "Los cambios deben aplicarse en una rama de trabajo."
            }

            val branchRef = requestObject(
                "/repos/${proposal.repository}/git/ref/heads/${encodeBranchPath(proposal.workBranch)}"
            )
            val currentHead = branchRef.optJSONObject("object")?.optString("sha").orEmpty()
            require(currentHead.equals(expectedHeadSha, ignoreCase = true)) {
                "La rama ha cambiado desde la revisión. Actualiza la propuesta antes de escribir."
            }

            val commit = requestObject("/repos/${proposal.repository}/git/commits/$currentHead")
            val baseTreeSha = commit.optJSONObject("tree")?.optString("sha").orEmpty()
            require(baseTreeSha.matches(Regex("[a-fA-F0-9]{40}"))) { "No se pudo leer el árbol base." }
            val baseTree = requestObject("/repos/${proposal.repository}/git/trees/$baseTreeSha?recursive=1")
            require(!baseTree.optBoolean("truncated")) {
                "El árbol del repositorio supera el límite de inspección; no se aplicarán cambios parciales."
            }
            data class TreeEntry(val type: String, val mode: String, val sha: String)
            val existing = mutableMapOf<String, TreeEntry>()
            val treeItems = baseTree.optJSONArray("tree") ?: JSONArray()
            for (index in 0 until treeItems.length()) {
                val item = treeItems.optJSONObject(index) ?: continue
                val path = item.optString("path")
                if (path.isNotBlank()) existing[path] = TreeEntry(item.optString("type"), item.optString("mode"), item.optString("sha"))
            }

            val entries = JSONArray()
            proposal.changes.forEach { change ->
                val currentEntry = existing[change.path]
                val segments = change.path.split('/')
                (0 until segments.lastIndex).forEach { index ->
                    val ancestor = segments.take(index + 1).joinToString("/")
                    val parent = existing[ancestor]
                    require(parent == null || (parent.type == "tree" && parent.mode == "040000")) {
                        "La ruta atraviesa un archivo, enlace simbólico o submódulo existente: $ancestor."
                    }
                }
                when (change.operation) {
                    CodeFileOperation.CREATE -> {
                        require(currentEntry == null) { "La ruta ya existe: ${change.path}." }
                        require(existing.keys.none { it.startsWith(change.path + "/") }) {
                            "La ruta ya contiene un directorio con archivos: ${change.path}."
                        }
                    }
                    CodeFileOperation.UPDATE, CodeFileOperation.DELETE -> require(
                        currentEntry != null && currentEntry.type == "blob" &&
                            (currentEntry.mode == "100644" || currentEntry.mode == "100755") &&
                            currentEntry.sha.equals(change.expectedBlobSha, ignoreCase = true)
                    ) { "El archivo cambió o no es un archivo regular desde la revisión: ${change.path}." }
                }

                val entry = JSONObject()
                    .put("path", change.path)
                    .put("mode", currentEntry?.mode ?: "100644")
                    .put("type", "blob")
                if (change.operation == CodeFileOperation.DELETE) {
                    entry.put("sha", JSONObject.NULL)
                } else {
                    val bytes = change.content.orEmpty().toByteArray(Charsets.UTF_8)
                    val blob = requestObject(
                        "/repos/${proposal.repository}/git/blobs",
                        "POST",
                        JSONObject()
                            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                            .put("encoding", "base64")
                    )
                    bytes.fill(0)
                    val blobSha = blob.optString("sha")
                    require(blobSha.matches(Regex("[a-fA-F0-9]{40}"))) { "GitHub no confirmó el blob de ${change.path}." }
                    entry.put("sha", blobSha)
                }
                entries.put(entry)
            }

            val newTree = requestObject(
                "/repos/${proposal.repository}/git/trees",
                "POST",
                JSONObject().put("base_tree", baseTreeSha).put("tree", entries)
            )
            val newTreeSha = newTree.optString("sha")
            require(newTreeSha.matches(Regex("[a-fA-F0-9]{40}"))) { "GitHub no confirmó el árbol de cambios." }

            val newCommit = requestObject(
                "/repos/${proposal.repository}/git/commits",
                "POST",
                JSONObject()
                    .put("message", proposal.title.take(120))
                    .put("tree", newTreeSha)
                    .put("parents", JSONArray().put(currentHead))
            )
            val newCommitSha = newCommit.optString("sha")
            require(newCommitSha.matches(Regex("[a-fA-F0-9]{40}"))) { "GitHub no confirmó el commit." }

            requestObject(
                "/repos/${proposal.repository}/git/refs/heads/${encodeBranchPath(proposal.workBranch)}",
                "PATCH",
                JSONObject().put("sha", newCommitSha).put("force", false)
            )
            GitHubCommitResult(newCommitSha, proposal.workBranch)
        }
    }

    suspend fun createPullRequest(
        repository: String,
        title: String,
        body: String,
        head: String,
        base: String,
        draft: Boolean = true
    ): Result<GitHubPullRequestSummary> = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidRepository(repository)) { "Repositorio no válido." }
            require(isSafeBranch(head) && isSafeBranch(base)) { "Rama no válida." }
            require(!head.equals(base, true)) { "La rama de origen y destino deben ser distintas." }
            require(title.isNotBlank() && title.length <= 256) { "Título de pull request no válido." }
            val payload = JSONObject()
                .put("title", title)
                .put("body", body.take(20_000))
                .put("head", head)
                .put("base", base)
                .put("draft", draft)
            val result = requestObject("/repos/$repository/pulls", "POST", payload)
            val number = result.optInt("number")
            val url = result.optString("html_url")
            require(number > 0 && url.startsWith("https://github.com/")) {
                "GitHub no confirmó la creación del pull request."
            }
            GitHubPullRequestSummary(number, result.optString("title"), url, result.optBoolean("draft", draft))
        }
    }

    private fun isSafeBranch(branch: String): Boolean {
        if (branch.isBlank() || branch.length > 120 || branch.startsWith("/") || branch.endsWith("/") ||
            branch.contains("..") || branch.contains("//") || branch.contains("@{") ||
            branch.endsWith(".") || branch.endsWith(".lock")
        ) return false
        return branch.split('/').all { segment ->
            segment.isNotBlank() && !segment.startsWith('.') && !segment.endsWith('.')
        } && branch.matches(Regex("[A-Za-z0-9][A-Za-z0-9._/-]{0,119}"))
    }

    private fun encodePathSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private fun encodeBranchPath(value: String): String =
        value.split('/').joinToString("/") { encodePathSegment(it) }

    private suspend fun requestJson(path: String): JSONArray {
        val value = requestAny(path)
        return value as? JSONArray ?: throw IllegalStateException("Respuesta inesperada de GitHub.")
    }

    private suspend fun requestObject(path: String): JSONObject {
        val value = requestAny(path)
        return value as? JSONObject ?: throw IllegalStateException("Respuesta inesperada de GitHub.")
    }

    private suspend fun requestObject(path: String, method: String, body: JSONObject): JSONObject {
        val value = requestAny(path, method, body)
        return value as? JSONObject ?: throw IllegalStateException("Respuesta inesperada de GitHub.")
    }

    private suspend fun requestAny(path: String, method: String = "GET", body: JSONObject? = null): Any {
        if (method == "GET") requestOverride?.let { return it(path) }
        transportOverride?.let { return it(method, path, body) }
        val token = try { credentials.getValidAccessToken(authenticator) } catch (_: GitHubCredentialManager.ReauthorizationRequiredException) { throw ReauthorizationRequiredException() } ?: throw ReauthorizationRequiredException()
        val connection = (URL("$API_BASE$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", "ORION-Android")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
        }
        return try {
            if (body != null) {
                val payload = body.toString().toByteArray(Charsets.UTF_8)
                connection.outputStream.use { it.write(payload) }
            }
            val status = connection.responseCode
            if (status == 401) throw ReauthorizationRequiredException()
            if (status !in 200..299) {
                val errorBody = connection.errorStream?.use { it.readBounded(8_192) }
                throw GitHubApiException(status, errorBody?.let { String(it, Charsets.UTF_8) }.orEmpty())
            }
            val responseBytes = connection.inputStream.use { it.readBounded(MAX_RESPONSE_BYTES) }
            val responseText = String(responseBytes, Charsets.UTF_8)
            if (responseText.isBlank()) JSONObject()
            else if (responseText.trimStart().startsWith("[")) JSONArray(responseText) else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    class ReauthorizationRequiredException : IllegalStateException("La autorización de GitHub ha caducado. Vuelve a conectar la cuenta.")
    class GitHubApiException(status: Int, detail: String = "") : IllegalStateException(
        "GitHub no pudo completar la operación (HTTP $status)." +
            if (detail.isBlank()) "" else " ${detail.take(300)}"
    )

    internal companion object {
        const val API_BASE = "https://api.github.com"
        const val API_VERSION = "2022-11-28"
        const val MAX_PAGE = 100
        const val MAX_PER_PAGE = 100
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val MAX_WORKFLOW_JOBS = 100
        const val MAX_WORKFLOW_STEPS = 100
        const val MAX_FILE_CONTENT_BYTES = 512 * 1024
        const val MAX_FILE_CONTENT_BASE64 = MAX_FILE_CONTENT_BYTES * 2
        const val MAX_DESCRIPTION_LENGTH = 500
        const val MAX_TITLE_LENGTH = 300
        const val TIMEOUT_MS = 15_000
        val REPOSITORY_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99}")
        fun isValidRepository(value: String): Boolean = REPOSITORY_PATTERN.matches(value) && value.split('/').none { it == "." || it == ".." }
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
