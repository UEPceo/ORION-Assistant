package com.orion.voicelab.knowledge

data class ExplicitKnowledgeLearningRequest(val url: String, val topic: String)

/**
 * Learning is opt-in: ordinary questions and pasted links never enter the corpus.
 * The user must issue a clear learning command and provide an HTTPS source URL.
 */
object ExplicitKnowledgeLearningIntent {
    private val urlPattern = Regex("""https://[^\s<>]+""", RegexOption.IGNORE_CASE)
    private val commands = listOf(
        "aprende de esta fuente",
        "aprende de este enlace",
        "incorpora esta fuente",
        "añade esta fuente a tu conocimiento",
        "anade esta fuente a tu conocimiento"
    )

    fun route(text: String): ExplicitKnowledgeLearningRequest? {
        val normalized = text.trim()
        val command = commands.firstOrNull { normalized.contains(it, ignoreCase = true) } ?: return null
        val match = urlPattern.find(normalized) ?: return null
        val url = match.value.trimEnd('.', ',', ';', ')', ']', '>')
        if (url.length > 1_500) return null
        val topic = normalized.replace(match.value, " ")
            .replace(Regex(Regex.escape(command), RegexOption.IGNORE_CASE), " ")
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', '-', ',')
            .take(240)
        return ExplicitKnowledgeLearningRequest(url, topic)
    }
}
