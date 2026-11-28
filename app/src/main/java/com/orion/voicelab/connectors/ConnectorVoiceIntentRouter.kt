package com.orion.voicelab.connectors

/** Deterministic connector intent parser. It accepts natural phrasing but emits only typed allowlisted actions. */
object ConnectorVoiceIntentRouter {
    data class RoutedAction(
        val action: ConnectorAction,
        val userRequestedExactAction: Boolean = true
    )

    private val repositoryPattern = Regex("""([A-Za-z0-9][A-Za-z0-9_.-]{0,99}/[A-Za-z0-9][A-Za-z0-9_.-]{0,99})""")

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
            val brand = Regex("""(?i)\b(?:pescaia(?:\s+surfcasting)?|pesca\s+ia|la\s+web\s+de\s+pesca)\b""").find(text)
            val before = brand?.let { text.substring(0, it.range.first) }.orEmpty()
            val after = brand?.let { text.substring(it.range.last + 1) }.orEmpty()
            val prefixQuery = before.replace(Regex("""(?i)^\s*(?:(?:consulta|busca|mira|revisa|abre)\s+)?"""), "")
                .replace(Regex("""(?i)\s+en\s*$"""), "").trim()
            val suffixQuery = after.replace(Regex("""(?i)^\s*(?:sobre|acerca\s+de|de|:|,)\s*"""), "").trim()
            val query = (suffixQuery.ifBlank { prefixQuery }).ifBlank { "playas y metodología" }
            return RoutedAction(ConnectorAction(ConnectorId.PESCAIA, "search_public", mapOf("query" to query.take(240))))
        }

        val mapsMention = containsAny(normalized, "google maps", "en maps", "abre maps", "mapa de")
        if (mapsMention) {
            val navigate = containsAny(normalized, "navega", "llévame", "llevame", "cómo llego", "como llego", "ruta")
            val destination = text
                .replace(Regex("""(?i)\bgoogle\s+maps\b|\bmaps\b"""), " ")
                .replace(Regex("""(?i)\s+(?:a pie|andando|caminando|en coche|en transporte público|en transporte publico)$"""), " ")
                .replace(Regex("""(?i)\b(?:llévame|llevame|abre|abrir|busca|buscar|navega|navegar|quiero|ponme|ruta|cómo\s+llego|como\s+llego|mapa)\b"""), " ")
                .replace(Regex("""(?i)\b(?:en|a|hasta|hacia|para)\b"""), " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
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

        val codeRepository = repositoryPattern.find(text)?.value ?: lastRepository
        if (containsAny(normalized, "crea una rama", "crear una rama", "crea rama", "crear rama")) {
            val branch = Regex("""(?i)(?:rama|branch)\s+(?:llamada\s+|de\s+)?([A-Za-z0-9._/-]{1,120})""")
                .find(text)?.groupValues?.get(1)
            val base = Regex("""(?i)(?:desde|basada en|partiendo de)\s+(main|master|develop|development)""")
                .find(text)?.groupValues?.get(1) ?: "main"
            return RoutedAction(ConnectorAction(
                connectorId = ConnectorId.GITHUB,
                operation = "create_branch",
                arguments = buildMap {
                    codeRepository?.let { put("repository", it) }
                    branch?.let { put("branch", it) }
                    put("base_branch", base)
                },
                risk = ConnectorRisk.REVERSIBLE_WRITE
            ))
        }

        if (containsAny(normalized, "abre un pull request", "crea un pull request", "crear pull request", "abre pull request")) {
            val head = Regex("""(?i)(?:desde|rama)\s+([A-Za-z0-9._/-]{1,120})""")
                .find(text)?.groupValues?.get(1)
            val base = Regex("""(?i)(?:hacia|contra|a)\s+(main|master|develop|development)""")
                .find(text)?.groupValues?.get(1) ?: "main"
            return RoutedAction(ConnectorAction(
                connectorId = ConnectorId.GITHUB,
                operation = "create_pull_request",
                arguments = buildMap {
                    codeRepository?.let { put("repository", it) }
                    head?.let { put("head", it) }
                    put("base", base)
                    put("draft", "true")
                    val title = Regex("""(?i)(?:titulado|título|titulo)\s+(.+)$""").find(text)?.groupValues?.get(1)
                    title?.let { put("title", it.take(200)) }
                },
                risk = ConnectorRisk.REVERSIBLE_WRITE
            ))
        }

        if (containsAny(normalized, "mis repositorios", "repositorios de github", "repos de github", "lista mis repos")) {
            val page = Regex("""(?:página|pagina)\s+(\d+)""", RegexOption.IGNORE_CASE)
                .find(text)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 100) ?: 1
            return RoutedAction(ConnectorAction(
                ConnectorId.GITHUB, "list_repositories", mapOf("limit" to "30", "page" to page.toString())
            ))
        }

        val repository = repositoryPattern.find(text)?.value ?: lastRepository
        val issueNumber = Regex("""(?:incidencia|issue)\s+#?(\d+)""", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)
        val operation = when {
            issueNumber != null && containsAny(normalized, "detalle", "detalles", "información", "informacion", "describe", "abre") -> "get_issue"
            containsAny(normalized, "commit", "commits", "cambios recientes", "últimos cambios", "ultimos cambios", "historial de cambios") -> "list_commits"
            Regex("""\bci\b""").containsMatchIn(normalized) || containsAny(normalized, "compilación", "compilacion", "workflow", "actions", "integración continua", "integracion continua", "ha terminado de compilar") -> "list_workflow_runs"
            containsAny(normalized, "lee el archivo", "lee archivo", "muestra el archivo", "muestra archivo", "abre el archivo", "abre archivo") -> "get_file"
            containsAny(normalized, "archivos", "carpetas", "estructura del repo", "estructura del repositorio", "contenido del repositorio") -> "list_files"
            containsAny(normalized, "incidencias", "issues", "problemas abiertos", "tareas abiertas") -> "list_issues"
            else -> null
        } ?: return null

        val repo = repository.orEmpty()
        val args = buildMap {
            if (repo.isNotBlank()) put("repository", repo)
            if (operation == "get_issue" && issueNumber != null) put("number", issueNumber)
            if (operation == "list_issues") put("state", if (normalized.contains("cerrad")) "closed" else "open")
            if (operation == "list_files" || operation == "get_file") {
                val pathMatch = if (operation == "get_file") {
                    Regex("""(?:archivo|fichero)(?:\s+llamado)?\s+([A-Za-z0-9_./-]{1,250})""", RegexOption.IGNORE_CASE).find(text)
                } else {
                    Regex("""(?:en la ruta|en carpeta|carpeta|directorio)\s+([A-Za-z0-9_./-]{1,250})""", RegexOption.IGNORE_CASE).find(text)
                }
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
