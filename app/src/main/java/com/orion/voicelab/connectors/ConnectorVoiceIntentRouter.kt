package com.orion.voicelab.connectors

/** Deterministic connector intent parser. It accepts natural phrasing but emits only typed allowlisted actions. */
object ConnectorVoiceIntentRouter {
    data class RoutedAction(
        val action: ConnectorAction,
        val userRequestedExactAction: Boolean = true
    )

    private val repositoryPattern = Regex("""([A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100})""")

    fun route(text: String, lastRepository: String? = null): RoutedAction? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        if (containsAny(normalized, "gmail", "mi correo", "mis correos", "correo electrónico", "emails")) {
            val rawQuery = text.substringAfter(':', "").trim().takeIf { it.isNotBlank() }
                ?: stripLeadingIntent(text, listOf(
                    "busca", "buscar", "encuentra", "localiza", "muestra", "enséñame",
                    "revisa", "consulta", "lee", "últimos correos", "ultimos correos"
                ))
            val query = rawQuery.replace(
                Regex("""^(?:en\s+)?(?:gmail|mi correo|mis correos|correo electrónico)\s*""", RegexOption.IGNORE_CASE),
                ""
            ).trim()
            val gmailQuery = when {
                query.isBlank() -> "in:anywhere"
                query.lowercase().contains("último") || query.lowercase().contains("ultimo") ||
                    query.lowercase().contains("reciente") -> "newer_than:7d $query".take(256)
                else -> query.take(256)
            }
            return RoutedAction(ConnectorAction(
                connectorId = ConnectorId.GMAIL,
                operation = "search_messages",
                arguments = mapOf("query" to gmailQuery, "limit" to "8")
            ))
        }

        if (containsAny(normalized, "mis repositorios", "repositorios de github", "repos de github", "lista mis repos")) {
            return RoutedAction(ConnectorAction(
                ConnectorId.GITHUB, "list_repositories", mapOf("limit" to "10")
            ))
        }

        val repository = repositoryPattern.find(text)?.value ?: lastRepository
        val operation = when {
            containsAny(normalized, "commit", "commits", "cambios recientes", "últimos cambios", "ultimos cambios", "historial de cambios") -> "list_commits"
            containsAny(normalized, "ci", "compilación", "compilacion", "workflow", "actions", "integración continua", "integracion continua", "ha terminado de compilar") -> "list_workflow_runs"
            containsAny(normalized, "archivos", "carpetas", "estructura del repo", "estructura del repositorio", "contenido del repositorio") -> "list_files"
            containsAny(normalized, "incidencias", "issues", "problemas abiertos", "tareas abiertas") -> "list_issues"
            else -> null
        } ?: return null

        val repo = repository.orEmpty()
        val args = buildMap {
            if (repo.isNotBlank()) put("repository", repo)
            if (operation == "list_issues") put("state", if (normalized.contains("cerrad")) "closed" else "open")
            if (operation == "list_files") {
                val pathMatch = Regex("""(?:en la ruta|en carpeta|carpeta|directorio)\s+([A-Za-z0-9_./-]{1,250})""", RegexOption.IGNORE_CASE).find(text)
                if (pathMatch != null) put("path", pathMatch.groupValues[1].trim('/'))
            }
        }
        return RoutedAction(ConnectorAction(ConnectorId.GITHUB, operation, args))
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean =
        phrases.any(text::contains)

    private fun stripLeadingIntent(text: String, prefixes: List<String>): String {
        val lower = text.lowercase()
        val prefix = prefixes.firstOrNull { lower.startsWith(it) } ?: return text.trim()
        return text.drop(prefix.length).trim().trimStart(':', ',', ' ')
    }
}
