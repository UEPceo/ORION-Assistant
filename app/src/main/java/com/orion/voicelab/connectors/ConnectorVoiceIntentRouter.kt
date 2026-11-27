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

        val messageId = Regex("""(?:abre|lee|muestra|consulta) (?:el )?(?:correo|mensaje) (?:con )?(?:id )?([A-Za-z0-9_-]{10,200})""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
        if (messageId != null) {
            return RoutedAction(ConnectorAction(
                ConnectorId.GMAIL, "get_message", mapOf("message_id" to messageId)
            ))
        }

        val isMailAction = containsAny(normalized, "busca", "buscar", "encuentra", "localiza", "muestra", "enséñame", "revisa", "consulta", "lee", "abre")
        val latestMailRequest = containsAny(normalized, "último correo", "ultimo correo", "correo más reciente", "correo mas reciente")
        if (latestMailRequest && containsAny(normalized, "lee", "leer", "abre", "muestra", "busca", "dime")) {
            val senderMatch = Regex("""(?:último|ultimo) correo de (.+)$|correo (?:más reciente|mas reciente) de (.+)$""", RegexOption.IGNORE_CASE)
                .find(text)
            val sender = senderMatch?.let { it.groupValues[1].ifBlank { it.groupValues[2] }.trim() }
            val query = if (sender.isNullOrBlank()) "newer_than:7d" else "newer_than:30d from:$sender"
            return RoutedAction(ConnectorAction(
                ConnectorId.GMAIL, "read_latest_message", mapOf("query" to query.take(256))
            ))
        }

        if (containsAny(normalized, "gmail", "mi correo", "mis correos", "correo electrónico", "emails") ||
            (normalized.contains("correo") && isMailAction)
        ) {
            val rawQuery = text.substringAfter(':', "").trim().takeIf { it.isNotBlank() }
                ?: stripLeadingIntent(text, listOf(
                    "busca", "buscar", "encuentra", "localiza", "muestra", "enséñame",
                    "revisa", "consulta", "lee", "últimos correos", "ultimos correos"
                ))
            val query = rawQuery
                .replace(Regex("""^(?:en\s+)?(?:gmail|mi correo|mis correos|correo electrónico)\s*""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""\s+(?:en\s+)?gmail$""", RegexOption.IGNORE_CASE), "")
                .replace(Regex("""^(?:un\s+)?correo\s+de\s+""", RegexOption.IGNORE_CASE), "from:")
                .replace(Regex("""^(?:un\s+)?correo\s+sobre\s+""", RegexOption.IGNORE_CASE), "subject:")
                .trim()
            val recentOnly = normalized.contains("últimos correos") || normalized.contains("ultimos correos") ||
                normalized.contains("correos recientes")
            val recentTail = Regex("""^(?:los\s+)?(?:últimos|ultimos|recientes)\s+correos(?:\s+de)?\s*""", RegexOption.IGNORE_CASE)
                .find(query)?.let { query.drop(it.value.length).trim() }
            val effectiveQuery = if (recentOnly && recentTail != null) {
                if (recentTail.isBlank()) "" else "from:$recentTail"
            } else query
            val gmailQuery = when {
                effectiveQuery.isBlank() && recentOnly -> "newer_than:7d"
                effectiveQuery.isBlank() -> "in:anywhere"
                recentOnly -> "newer_than:7d $effectiveQuery".take(256)
                effectiveQuery.lowercase().contains("último") || effectiveQuery.lowercase().contains("ultimo") ||
                    effectiveQuery.lowercase().contains("reciente") -> "newer_than:7d $effectiveQuery".take(256)
                else -> effectiveQuery.take(256)
            }
            return RoutedAction(ConnectorAction(
                connectorId = ConnectorId.GMAIL,
                operation = "search_messages",
                arguments = mapOf("query" to gmailQuery, "limit" to "8")
            ))
        }

        val pescaiaMention = containsAny(normalized, "pescaia", "pesca ia", "en la web de pesca")
        if (pescaiaMention) {
            val query = text.replace(Regex("""(?i)^.*?(?:consulta|busca|mira|revisa|abre)?\s*(?:en\s+)?(?:pescaia(?:\s+surfcasting)?|pesca\s+ia)\s*[:,]?\s*"""), "").trim()
                .ifBlank { "playas y metodología" }
            return RoutedAction(ConnectorAction(
                ConnectorId.PESCAIA, "search_public", mapOf("query" to query.take(240))
            ))
        }

        val mapsMention = containsAny(normalized, "google maps", "en maps", "abre maps", "mapa de")
        if (mapsMention) {
            val navigate = containsAny(normalized, "navega", "llévame", "llevame", "cómo llego", "como llego", "ruta")
            val destination = text.replace(Regex("""(?i)^.*?(?:google\s+maps|maps|mapa(?:\s+de)?)\s*"""), "")
                .replace(Regex("""(?i)^(?:a|hasta|hacia)\s+"""), "")
                .replace(Regex("""(?i)\\s+(?:a pie|andando|caminando|en coche|en transporte público|en transporte publico)$"""), "")
                .trim().ifBlank { "" }
            return RoutedAction(ConnectorAction(
                ConnectorId.GOOGLE_MAPS,
                if (navigate) "navigate" else "search_place",
                mapOf("destination" to destination, "mode" to when {
                    containsAny(normalized, "a pie", "andando", "caminando") -> "walking"
                    containsAny(normalized, "transporte público", "transporte publico", "metro", "autobús", "autobus") -> "transit"
                    else -> "driving"
                })
            ))
        }

        if (containsAny(normalized, "mis repositorios", "repositorios de github", "repos de github", "lista mis repos")) {
            return RoutedAction(ConnectorAction(
                ConnectorId.GITHUB, "list_repositories", mapOf("limit" to "10")
            ))
        }

        val repository = repositoryPattern.find(text)?.value ?: lastRepository
        val issueNumber = Regex("""(?:incidencia|issue)\s+#?(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
        val operation = when {
            issueNumber != null && containsAny(normalized, "detalle", "detalles", "información", "informacion", "describe", "abre") -> "get_issue"
            containsAny(normalized, "commit", "commits", "cambios recientes", "últimos cambios", "ultimos cambios", "historial de cambios") -> "list_commits"
            Regex("""\bci\b""").containsMatchIn(normalized) || containsAny(normalized, "compilación", "compilacion", "workflow", "actions", "integración continua", "integracion continua", "ha terminado de compilar") -> "list_workflow_runs"
            containsAny(normalized, "archivos", "carpetas", "estructura del repo", "estructura del repositorio", "contenido del repositorio") -> "list_files"
            containsAny(normalized, "incidencias", "issues", "problemas abiertos", "tareas abiertas") -> "list_issues"
            else -> null
        } ?: return null

        val repo = repository.orEmpty()
        val args = buildMap {
            if (repo.isNotBlank()) put("repository", repo)
            if (operation == "get_issue" && issueNumber != null) put("number", issueNumber)
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
