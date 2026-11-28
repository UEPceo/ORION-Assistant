package com.orion.voicelab.connectors

import com.orion.voicelab.connectors.gmail.GmailApiClient
import com.orion.voicelab.connectors.gmail.GmailCredentialManager
import com.orion.voicelab.connectors.github.GitHubApiClient
import com.orion.voicelab.connectors.github.GitHubCredentialManager
import com.orion.voicelab.engineering.CodeChangeProposal
import com.orion.voicelab.engineering.CodeFileChange
import com.orion.voicelab.engineering.CodeFileOperation
import org.json.JSONArray
import org.json.JSONObject

/** Provider implementations exposed only through ConnectorDispatcher. */
class GmailOrionConnector(
    private val credentials: GmailCredentialManager,
    private val api: GmailApiClient
) : OrionConnector {
    override val descriptor = ConnectorDescriptor(
        id = ConnectorId.GMAIL,
        displayName = "Gmail",
        description = "Búsqueda y lectura limitada de metadatos de correo.",
        capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
        isImplemented = true
    )

    override suspend fun connectionState(): ConnectorConnectionState =
        if (credentials.isConnected()) ConnectorConnectionState.CONNECTED
        else ConnectorConnectionState.EXPIRED

    override suspend fun execute(action: ConnectorAction): ConnectorResult {
        if (action.connectorId != ConnectorId.GMAIL) {
            return ConnectorResult.Failure("Operación de Gmail no disponible.", false)
        }
        if (action.operation == "read_latest_message") {
            val query = action.arguments["query"] ?: "newer_than:7d"
            return api.searchMessages(query, 1).fold(
                onSuccess = { messages ->
                    val first = messages.firstOrNull()
                    if (first == null) ConnectorResult.Success("No he encontrado correos recientes que coincidan.")
                    else api.getMessage(first.id).fold(
                        onSuccess = { message ->
                            ConnectorResult.Success(
                                "Correo más reciente. De: ${message.from}. Asunto: ${message.subject}. Fecha: ${message.date}.")
                        },
                        onFailure = {
                            ConnectorResult.Failure("No se pudo abrir el correo más reciente.", true)
                        }
                    )
                },
                onFailure = {
                    ConnectorResult.Failure("No se pudo buscar el correo más reciente.", true)
                }
            )
        }
        if (action.operation == "get_message") {
            val id = action.arguments["message_id"].orEmpty()
            if (id.isBlank()) return ConnectorResult.NeedsUserInput("Indica el identificador del correo.")
            return api.getMessage(id).fold(
                onSuccess = { message ->
                    ConnectorResult.Success(
                        "De: ${message.from}. Asunto: ${message.subject}. Fecha: ${message.date}.")
                },
                onFailure = {
                    ConnectorResult.Failure(
                        if (it is GmailApiClient.ReauthorizationRequiredException) "La conexión de Gmail ha caducado. Vuelve a conectar la cuenta."
                        else "No se pudo abrir ese correo.",
                        true
                    )
                }
            )
        }
        if (action.operation != "search_messages") return ConnectorResult.Failure("Operación de Gmail no disponible.", false)
        val query = action.arguments["query"].orEmpty().trim()
        if (query.isBlank()) return ConnectorResult.NeedsUserInput("¿Qué correo o remitente quieres buscar?")
        return api.searchMessages(query, action.arguments["limit"]?.toIntOrNull() ?: 5)
            .fold(
                onSuccess = { messages ->
                    if (messages.isEmpty()) ConnectorResult.Success("No he encontrado correos que coincidan.")
                    else ConnectorResult.Success(
                        messages.take(5).joinToString("\n") {
                            "De: ${it.from.ifBlank { "remitente desconocido" }}. Asunto: ${it.subject.ifBlank { "sin asunto" }}. Fecha: ${it.date}."
                        }
                    )
                },
                onFailure = {
                    ConnectorResult.Failure(
                        if (it is GmailApiClient.ReauthorizationRequiredException)
                            "La conexión de Gmail ha caducado. Vuelve a conectar la cuenta."
                        else "No se pudo consultar Gmail. Comprueba la conexión e inténtalo de nuevo.",
                        true
                    )
                }
            )
    }
}

class GitHubOrionConnector(
    private val credentials: GitHubCredentialManager,
    private val api: GitHubApiClient
) : OrionConnector {
    override val descriptor = ConnectorDescriptor(
        id = ConnectorId.GITHUB,
        displayName = "GitHub",
        description = "Inspección y programación de repositorios: ramas, cambios atómicos y pull requests.",
        capabilities = setOf(
            ConnectorCapability.SEARCH, ConnectorCapability.READ,
            ConnectorCapability.CREATE, ConnectorCapability.UPDATE, ConnectorCapability.COMMENT
        ),
        isImplemented = true
    )

    override suspend fun connectionState(): ConnectorConnectionState =
        if (credentials.isConnected()) ConnectorConnectionState.CONNECTED
        else ConnectorConnectionState.EXPIRED

    override suspend fun execute(action: ConnectorAction): ConnectorResult {
        if (action.connectorId != ConnectorId.GITHUB) {
            return ConnectorResult.Failure("Operación de GitHub no disponible.", false)
        }
        return when (action.operation) {
            "get_file" -> {
                val repository = action.arguments["repository"].orEmpty()
                val filePath = action.arguments["path"].orEmpty()
                if (repository.isBlank() || filePath.isBlank()) {
                    return ConnectorResult.NeedsUserInput("Indica el repositorio y la ruta exacta del archivo.")
                }
                api.getFile(repository, filePath, action.arguments["branch"]).fold(
                    onSuccess = { file ->
                        ConnectorResult.Success(
                            "Archivo ${file.path} (${file.sizeBytes} bytes, SHA ${file.sha}). Contenido:\\n${file.content}",
                            file.sha
                        )
                    },
                    onFailure = { githubFailure(it) }
                )
            }
            "create_branch" -> {
                val repository = action.arguments["repository"].orEmpty()
                val branch = action.arguments["branch"].orEmpty()
                val base = action.arguments["base_branch"].orEmpty()
                if (repository.isBlank() || branch.isBlank() || base.isBlank()) {
                    return ConnectorResult.NeedsUserInput("Indica repositorio, nombre de rama y rama base.")
                }
                api.createBranch(repository, branch, base).fold(
                    onSuccess = { ConnectorResult.Success("Rama creada: ${it.name}.", it.sha) },
                    onFailure = { githubFailure(it) }
                )
            }
            "commit_changes" -> {
                val raw = action.arguments["proposal_json"].orEmpty()
                val expectedHead = action.arguments["expected_head_sha"].orEmpty()
                val proposal = parseCodeChangeProposal(raw)
                    ?: return ConnectorResult.Failure("La propuesta de cambios no tiene un formato válido.", false)
                api.commitChanges(proposal, expectedHead).fold(
                    onSuccess = { ConnectorResult.Success("Cambios aplicados en ${it.branch}. Commit: ${it.commitSha}.", it.commitSha) },
                    onFailure = { githubFailure(it) }
                )
            }
            "create_pull_request" -> {
                val repository = action.arguments["repository"].orEmpty()
                val title = action.arguments["title"].orEmpty()
                val body = action.arguments["body"].orEmpty()
                val head = action.arguments["head"].orEmpty()
                val base = action.arguments["base"].orEmpty()
                val draft = action.arguments["draft"]?.toBooleanStrictOrNull() ?: true
                if (repository.isBlank() || title.isBlank() || head.isBlank() || base.isBlank()) {
                    return ConnectorResult.NeedsUserInput("Indica repositorio, título, rama de trabajo y rama destino.")
                }
                api.createPullRequest(repository, title, body, head, base, draft).fold(
                    onSuccess = { ConnectorResult.Success("Pull request ${if (it.draft) "de borrador " else ""}#${it.number} creado: ${it.url}", it.number.toString()) },
                    onFailure = { githubFailure(it) }
                )
            }
            "list_repositories" -> api.listRepositories(
                page = action.arguments["page"]?.toIntOrNull() ?: 1,
                perPage = action.arguments["limit"]?.toIntOrNull() ?: 30
            ).fold(
                onSuccess = { repos ->
                    if (repos.isEmpty()) ConnectorResult.Success("No he encontrado repositorios.")
                    else ConnectorResult.Success(repos.take(30).joinToString("\n") {
                        "${it.fullName} — ${if (it.private) "privado" else "público"}; rama: ${it.defaultBranch}; ${it.description}"
                    })
                },
                onFailure = { githubFailure(it) }
            )
            "get_issue" -> {
                val repository = action.arguments["repository"].orEmpty()
                val number = action.arguments["number"]?.toIntOrNull()
                    ?: return ConnectorResult.NeedsUserInput("Indica el número de la incidencia.")
                if (repository.isBlank()) return ConnectorResult.NeedsUserInput("¿De qué repositorio es la incidencia?")
                api.getIssue(repository, number).fold(
                    onSuccess = { issue ->
                        ConnectorResult.Success(
                            "#${issue.number}: ${issue.title}. Estado: ${issue.state}. Autor: ${issue.author}. Actualizada: ${issue.updatedAt}. Descripción: ${issue.body}"
                        )
                    },
                    onFailure = { githubFailure(it) }
                )
            }
            "list_commits" -> {
                val repository = action.arguments["repository"].orEmpty()
                if (repository.isBlank()) return ConnectorResult.NeedsUserInput("¿De qué repositorio quieres consultar los commits?")
                api.listCommits(repository).fold(
                    onSuccess = { commits ->
                        if (commits.isEmpty()) ConnectorResult.Success("No he encontrado commits.")
                        else ConnectorResult.Success(commits.take(10).joinToString("\n") {
                            "${it.sha} — ${it.message} — ${it.author} — ${it.committedAt}"
                        })
                    },
                    onFailure = { githubFailure(it) }
                )
            }
            "list_workflow_runs" -> {
                val repository = action.arguments["repository"].orEmpty()
                if (repository.isBlank()) return ConnectorResult.NeedsUserInput("¿De qué repositorio quieres consultar los CI?")
                api.listWorkflowRuns(repository).fold(
                    onSuccess = { runs ->
                        if (runs.isEmpty()) ConnectorResult.Success("No hay ejecuciones de GitHub Actions disponibles.")
                        else ConnectorResult.Success(runs.take(10).joinToString("\n") {
                            "${it.name}: ${it.status}${it.conclusion.takeIf(String::isNotBlank)?.let { value -> " ($value)" }.orEmpty()} — rama ${it.branch} — ${it.updatedAt}"
                        })
                    },
                    onFailure = { githubFailure(it) }
                )
            }
            "list_files" -> {
                val repository = action.arguments["repository"].orEmpty()
                if (repository.isBlank()) return ConnectorResult.NeedsUserInput("¿De qué repositorio quieres listar archivos?")
                api.listFiles(repository, action.arguments["path"].orEmpty()).fold(
                    onSuccess = { files ->
                        if (files.isEmpty()) ConnectorResult.Success("La ruta no contiene archivos o no existe.")
                        else ConnectorResult.Success(files.take(30).joinToString("\n") {
                            "${it.type}: ${it.path}"
                        })
                    },
                    onFailure = { githubFailure(it) }
                )
            }
            "list_issues" -> {
                val repository = action.arguments["repository"].orEmpty()
                if (repository.isBlank()) return ConnectorResult.NeedsUserInput(
                    "Indica el repositorio con formato propietario/nombre."
                )
                api.listIssues(
                    repository = repository,
                    state = action.arguments["state"] ?: "open",
                    page = action.arguments["page"]?.toIntOrNull() ?: 1
                ).fold(
                    onSuccess = { issues ->
                        if (issues.isEmpty()) ConnectorResult.Success("No hay incidencias que coincidan.")
                        else ConnectorResult.Success(issues.take(10).joinToString("\n") {
                            "#${it.number} [${it.state}] ${it.title} (actualizada: ${it.updatedAt})"
                        })
                    },
                    onFailure = { githubFailure(it) }
                )
            }
            else -> ConnectorResult.Failure("Operación de GitHub no disponible.", false)
        }
    }

    private fun parseCodeChangeProposal(raw: String): CodeChangeProposal? = runCatching {
        require(raw.isNotBlank() && raw.length <= 1_100_000)
        val root = JSONObject(raw)
        val items = root.optJSONArray("changes") ?: JSONArray()
        val changes = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val operation = when (item.optString("operation").uppercase()) {
                    "CREATE" -> CodeFileOperation.CREATE
                    "UPDATE" -> CodeFileOperation.UPDATE
                    "DELETE" -> CodeFileOperation.DELETE
                    else -> error("Operación de archivo no permitida.")
                }
                add(
                    CodeFileChange(
                        path = item.optString("path"),
                        operation = operation,
                        content = if (item.isNull("content")) null else item.optString("content"),
                        expectedBlobSha = item.optString("expectedBlobSha").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
        CodeChangeProposal(
            repository = root.optString("repository"),
            baseBranch = root.optString("baseBranch"),
            workBranch = root.optString("workBranch"),
            title = root.optString("title"),
            summary = root.optString("summary"),
            changes = changes
        )
    }.getOrNull()

    private fun githubFailure(error: Throwable): ConnectorResult.Failure =
        ConnectorResult.Failure(
            if (error is GitHubApiClient.ReauthorizationRequiredException)
                "La conexión de GitHub ha caducado. Vuelve a conectar la cuenta."
            else "No se pudo consultar GitHub. Comprueba la conexión e inténtalo de nuevo.",
            true
        )
}
