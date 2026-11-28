package com.orion.voicelab.connectors

import com.orion.voicelab.connectors.gmail.GmailApiClient
import com.orion.voicelab.connectors.gmail.GmailCredentialManager
import com.orion.voicelab.connectors.github.GitHubApiClient
import com.orion.voicelab.connectors.github.GitHubCredentialManager

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
        description = "Consulta de repositorios, incidencias, commits, archivos y estado de CI.",
        capabilities = setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
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
            "list_repositories" -> api.listRepositories(
                page = action.arguments["page"]?.toIntOrNull() ?: 1,
                perPage = action.arguments["limit"]?.toIntOrNull() ?: 10
            ).fold(
                onSuccess = { repos ->
                    if (repos.isEmpty()) ConnectorResult.Success("No he encontrado repositorios.")
                    else ConnectorResult.Success(repos.take(10).joinToString("\n") {
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

    private fun githubFailure(error: Throwable): ConnectorResult.Failure =
        ConnectorResult.Failure(
            if (error is GitHubApiClient.ReauthorizationRequiredException)
                "La conexión de GitHub ha caducado. Vuelve a conectar la cuenta."
            else "No se pudo consultar GitHub. Comprueba la conexión e inténtalo de nuevo.",
            true
        )
}
