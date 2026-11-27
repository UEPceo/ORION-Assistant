package com.orion.voicelab.connectors

/** Deterministic, narrow voice-command parser. It never lets the LLM invent provider actions. */
object ConnectorVoiceIntentRouter {
    data class RoutedAction(
        val action: ConnectorAction,
        val userRequestedExactAction: Boolean = true
    )

    fun route(text: String): RoutedAction? {
        val normalized = text.trim().lowercase()
        if (normalized.isBlank()) return null

        val gmailTrigger = listOf(
            "busca en gmail", "busca en mi correo", "buscar correo",
            "busca un correo", "revisa gmail", "consulta gmail"
        ).any(normalized::startsWith)
        if (gmailTrigger) {
            val query = text.substringAfter(':', "").trim().takeIf { it.isNotBlank() }
                ?: extractAfterTrigger(text, listOf(
                    "busca en gmail", "busca en mi correo", "buscar correo",
                    "busca un correo", "revisa gmail", "consulta gmail"
                )).orEmpty()
            if (query.isBlank()) {
                return RoutedAction(
                    ConnectorAction(ConnectorId.GMAIL, "search_messages", mapOf("query" to ""))
                )
            }
            return RoutedAction(
                ConnectorAction(
                    connectorId = ConnectorId.GMAIL,
                    operation = "search_messages",
                    arguments = mapOf("query" to query.take(256), "limit" to "5")
                )
            )
        }

        if (listOf("lista mis repositorios", "muestra mis repositorios", "consulta mis repositorios",
                "lista los repositorios de github").any(normalized::startsWith)
        ) {
            return RoutedAction(
                ConnectorAction(
                    ConnectorId.GITHUB,
                    "list_repositories",
                    mapOf("limit" to "10")
                )
            )
        }

        val issueMatch = Regex(
            """(?:lista|muestra|consulta|busca) (?:las )?(?:incidencias|issues) (?:de|del repositorio) ([a-z0-9_.-]{1,100}/[a-z0-9_.-]{1,100})""",
            RegexOption.IGNORE_CASE
        ).find(text)
        if (issueMatch != null) {
            return RoutedAction(
                ConnectorAction(
                    ConnectorId.GITHUB,
                    "list_issues",
                    mapOf("repository" to issueMatch.groupValues[1], "state" to "open")
                )
            )
        }
        return null
    }

    private fun extractAfterTrigger(text: String, triggers: List<String>): String? {
        val trigger = triggers.firstOrNull { text.lowercase().startsWith(it) } ?: return null
        return text.drop(trigger.length).trim().trimStart(':', ',', ' ')
    }
}
