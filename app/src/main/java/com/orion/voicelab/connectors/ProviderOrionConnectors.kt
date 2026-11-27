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
        if (action.connectorId != ConnectorId.GMAIL || action.operation != "search_messages") {
            return ConnectorResult.Failure("Operación de Gmail no disponible.", false)
        }
        val query = action.arguments["query"].orEmpty().trim()
        if (query.isBlank()) return ConnectorResult.NeedsUserInput("¿Qué correo o remitente quieres buscar?")
        return api.searchMessages(query, action.arguments["limit"]?.toIntOrNull() ?: 5)
            .fold(
                onSuccess = { messages ->
                    if (messages.isEmpty()) ConnectorResult.Success("No he encontrado correos que coincidan.")
                    else ConnectorResult.Success(
                        messages.take(5).joinToString("\n") {
                            "De: ${it.from.ifBlank { "remitente desconocido" }}. Asunto: ${it.subject.ifBlank { "sin asunto" }}. Fecha: ${it.date}. Resumen: ${it.snippet}"
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
        description = "Consulta de repositorios e incidencias.",
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
