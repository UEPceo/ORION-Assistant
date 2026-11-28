package com.orion.voicelab.memory

import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.data.ContactProfile
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.MemoryItem
import com.orion.voicelab.data.MemorySuggestion
import com.orion.voicelab.data.PhoneCallRecord
import com.orion.voicelab.data.TaskItem
import com.orion.voicelab.data.TaskStatus
import com.orion.voicelab.data.TurnContext
import com.orion.voicelab.tasks.SpanishDateTimeParser
import java.text.Normalizer
import java.util.Locale

internal object MemoryTextSimilarity {
    private val spanishStopWords = setOf(
        "a", "al", "algo", "con", "como", "de", "del", "el", "ella", "ellas", "ellos",
        "en", "es", "esa", "ese", "eso", "esta", "este", "esto", "la", "las", "le",
        "les", "lo", "los", "me", "mi", "mis", "mucho", "muy", "no", "nos", "o",
        "para", "pero", "por", "que", "se", "si", "sin", "sobre", "su", "sus",
        "te", "tu", "tus", "un", "una", "uno", "unos", "y", "ya", "yo"
    )

    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun tokenizeSignificant(text: String): Set<String> =
        normalize(text)
            .split(' ')
            .filter { it.length >= 2 && it !in spanishStopWords }
            .toSet()

    fun isNearDuplicate(a: String, b: String): Boolean {
        val normA = normalize(a)
        val normB = normalize(b)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA == normB) return true
        if ((normA.length >= 12 && normB.contains(normA)) || (normB.length >= 12 && normA.contains(normB))) {
            return true
        }
        val tokensA = tokenizeSignificant(a)
        val tokensB = tokenizeSignificant(b)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false
        val intersection = tokensA.intersect(tokensB).size
        val union = tokensA.union(tokensB).size
        return union > 0 && (intersection.toDouble() / union.toDouble()) >= 0.72
    }
}

object MemoryCandidateExtractor {

    private data class CandidateRule(
        val regex: Regex,
        val category: MemoryCategory,
        val importance: Int,
        val formatter: (MatchResult) -> String?
    )

    private val rules = listOf(
        CandidateRule(
            regex = Regex("""(?:me llamo|puedes llamarme|llamame|dime)\s+([A-ZÁÉÍÓÚÑa-záéíóúñ][A-ZÁÉÍÓÚÑa-záéíóúñ\-']{1,24}(?:\s+[A-ZÁÉÍÓÚÑa-záéíóúñ\-']{2,20})?)""", RegexOption.IGNORE_CASE),
            category = MemoryCategory.IDENTITY,
            importance = 5,
            formatter = { m ->
                val rawName = m.groupValues[1].trim()
                    .replace(Regex("""^(?:por\s+favor|asi)\s+""", RegexOption.IGNORE_CASE), "")
                    .trim()
                if (rawName.length in 2..40 && !rawName.contains("no ", ignoreCase = true)) {
                    "El usuario prefiere que le llamen $rawName"
                } else {
                    null
                }
            }
        ),
        CandidateRule(
            regex = Regex("""(?:recuerda que|no olvides que|apunta que|ten en cuenta que|guarda que)\s+(.{6,140})""", RegexOption.IGNORE_CASE),
            category = MemoryCategory.GENERAL,
            importance = 5,
            formatter = { m -> formatCleanFact(m.groupValues[1]) }
        ),
        CandidateRule(
            regex = Regex("""(?:estoy trabajando en|mi proyecto(?:\s+actual)?\s+(?:es|se llama)|estamos desarrollando|tengo un proyecto(?:\s+de)?)\s+(.{4,120})""", RegexOption.IGNORE_CASE),
            category = MemoryCategory.PROJECT,
            importance = 4,
            formatter = { m ->
                val detail = cleanClause(m.groupValues[1]) ?: return@CandidateRule null
                "Proyecto activo del usuario: $detail"
            }
        ),
        CandidateRule(
            regex = Regex("""(?:todas las mañanas|todas las noches|todos los dias|todos los días|cada dia|cada día|normalmente suelo|suelo)\s+(.{6,120})""", RegexOption.IGNORE_CASE),
            category = MemoryCategory.ROUTINE,
            importance = 3,
            formatter = { m ->
                val detail = cleanClause(m.groupValues[1]) ?: return@CandidateRule null
                "Rutina del usuario: $detail"
            }
        ),
        CandidateRule(
            regex = Regex("""(?:prefiero que|me gusta que|no me gusta que|hablame|háblame|respóndeme|respondeme)\s+(.{5,110})""", RegexOption.IGNORE_CASE),
            category = MemoryCategory.PREFERENCE,
            importance = 4,
            formatter = { m ->
                val phrase = cleanClause(m.groupValues[0]) ?: return@CandidateRule null
                "Preferencia de comunicación: $phrase"
            }
        ),
        CandidateRule(
            regex = Regex("""(?:he decidido|decidimos|vamos a usar)\s+(.{6,120})""", RegexOption.IGNORE_CASE),
            category = MemoryCategory.DECISION,
            importance = 4,
            formatter = { m ->
                val detail = cleanClause(m.groupValues[1]) ?: return@CandidateRule null
                "Decisión tomada: $detail"
            }
        )
    )

    fun extractCandidates(
        userUtterance: String,
        existingMemories: List<MemoryItem>,
        pendingSuggestions: List<MemorySuggestion> = emptyList(),
        sourceConversationId: Long? = null
    ): List<MemorySuggestion> {
        val text = userUtterance.trim()
        if (text.length < 8) return emptyList()

        val found = mutableListOf<MemorySuggestion>()
        for (rule in rules) {
            val match = rule.regex.find(text) ?: continue
            val content = rule.formatter(match) ?: continue
            val alreadyStored = existingMemories.any {
                MemoryTextSimilarity.isNearDuplicate(it.content, content)
            }
            val alreadyPending = pendingSuggestions.any {
                MemoryTextSimilarity.isNearDuplicate(it.content, content)
            } || found.any {
                MemoryTextSimilarity.isNearDuplicate(it.content, content)
            }
            if (!alreadyStored && !alreadyPending) {
                found.add(
                    MemorySuggestion(
                        id = "${rule.category.name}_${content.hashCode()}_${sourceConversationId ?: 0L}",
                        category = rule.category,
                        content = content,
                        importance = rule.importance,
                        sourceConversationId = sourceConversationId
                    )
                )
            }
        }
        return found.take(2)
    }

    private fun cleanClause(raw: String): String? {
        val trimmed = raw
            .substringBefore('.')
            .substringBefore('?')
            .substringBefore('!')
            .trim()
            .trimEnd(',', ';', ':')
        return if (trimmed.length >= 4) trimmed.take(140) else null
    }

    private fun formatCleanFact(raw: String): String? {
        val clause = cleanClause(raw) ?: return null
        return clause.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }
}

object MemorySelector {

    private val projectCues = setOf("proyecto", "codigo", "app", "orion", "modelo", "trabajo", "desarrollo", "tarea")
    private val routineCues = setOf("rutina", "mañana", "noche", "horario", "desayuno", "entreno", "dia", "siempre")
    private val preferenceCues = setOf("prefiero", "tono", "breve", "largo", "formal", "estilo", "respuesta", "voz")

    fun selectRelevantMemories(
        query: String,
        recentHistory: List<ChatMessage>,
        allMemories: List<MemoryItem>,
        maxItems: Int = 6,
        maxTotalChars: Int = 650
    ): List<MemoryItem> {
        if (allMemories.isEmpty() || maxItems <= 0 || maxTotalChars <= 0) return emptyList()

        val combinedQueryText = buildString {
            append(query)
            recentHistory.takeLast(2).forEach { msg ->
                append(' ')
                append(msg.content)
            }
        }
        val queryTokens = MemoryTextSimilarity.tokenizeSignificant(combinedQueryText)

        val scored = allMemories.map { item ->
            var score = item.importance * 0.45
            if (item.category == MemoryCategory.IDENTITY) {
                score += 2.6
            }
            val itemTokens = MemoryTextSimilarity.tokenizeSignificant(item.content)
            val overlap = queryTokens.intersect(itemTokens).size
            if (overlap > 0) {
                score += overlap * 3.5
            }
            if (item.category == MemoryCategory.PROJECT && queryTokens.any { it in projectCues }) {
                score += 2.0
            }
            if (item.category == MemoryCategory.ROUTINE && queryTokens.any { it in routineCues }) {
                score += 2.0
            }
            if (item.category == MemoryCategory.PREFERENCE && queryTokens.any { it in preferenceCues }) {
                score += 1.6
            }
            item to score
        }.sortedWith(
            compareByDescending<Pair<MemoryItem, Double>> { it.second }
                .thenByDescending { it.first.importance }
                .thenByDescending { it.first.updatedAtMs }
        )

        val selected = mutableListOf<MemoryItem>()
        var charBudget = 0
        for ((item, _) in scored) {
            if (selected.size >= maxItems) break
            val entryLen = item.category.label.length + item.content.length + 6
            if (selected.isNotEmpty() && charBudget + entryLen > maxTotalChars) continue
            selected.add(item)
            charBudget += entryLen
        }
        return selected
    }
}

object ContextPromptBuilder {

    const val DEFAULT_MAX_HISTORY_MESSAGES = 10
    const val DEFAULT_MAX_HISTORY_CHARS = 2_400

    fun buildTurnContext(
        identity: AssistantIdentity,
        allMemories: List<MemoryItem>,
        conversationSummary: String,
        recentHistory: List<ChatMessage>,
        currentQuery: String,
        activeCoworker: ContactProfile? = null,
        recentPhoneCalls: List<PhoneCallRecord> = emptyList(),
        activeTasks: List<TaskItem> = emptyList(),
        maxHistoryMessages: Int = DEFAULT_MAX_HISTORY_MESSAGES,
        maxHistoryChars: Int = DEFAULT_MAX_HISTORY_CHARS
    ): TurnContext {
        val selectedMemories = if (identity.memoryEnabled) {
            MemorySelector.selectRelevantMemories(
                query = currentQuery,
                recentHistory = recentHistory,
                allMemories = allMemories
            )
        } else {
            emptyList()
        }

        val relevantCalls = selectRelevantCalls(currentQuery, recentPhoneCalls)
        val relevantTasks = selectRelevantTasks(currentQuery, activeTasks)

        val systemPrompt = buildSystemPrompt(
            identity = identity,
            selectedMemories = selectedMemories,
            conversationSummary = conversationSummary,
            activeCoworker = activeCoworker,
            phoneCallHighlights = relevantCalls,
            activeTaskHighlights = relevantTasks
        )

        val trimmedHistory = trimHistoryToBudget(
            history = recentHistory,
            maxMessages = maxHistoryMessages,
            maxChars = maxHistoryChars
        )

        return TurnContext(
            systemPrompt = systemPrompt,
            recentHistory = trimmedHistory,
            usedMemories = selectedMemories
        )
    }

    fun buildSystemPrompt(
        identity: AssistantIdentity,
        selectedMemories: List<MemoryItem> = emptyList(),
        conversationSummary: String = "",
        activeCoworker: ContactProfile? = null,
        phoneCallHighlights: List<PhoneCallRecord> = emptyList(),
        activeTaskHighlights: List<TaskItem> = emptyList()
    ): String = buildString {
        if (activeCoworker != null && !activeCoworker.isPrimary) {
            append("Tu nombre es ${activeCoworker.name}. ")
            append("Eres un Co-worker (${activeCoworker.roleTitle}) que complementa a ${identity.assistantName} (ORION Principal). ")
            append(activeCoworker.specialtyPrompt.trim())
            append("\nEstilo conversacional por voz: claro, breve (2 o 3 frases como máximo), en español natural y sin Markdown.")
        } else {
            append("Tu nombre es ${identity.assistantName}. ")
            append(identity.basePrompt.trim())
        }
        append("\nPersonalidad: ${identity.personality.trim()}.")
        append("\nTono: ${identity.tone.trim()}. ${identity.formality.promptInstruction}")
        append("\nRegla de veracidad: utiliza solo los recuerdos y el contexto incluidos abajo; nunca inventes hechos ni recuerdos pasados.")

        if (selectedMemories.isNotEmpty()) {
            append("\n\nMemoria persistente del usuario (úsalos con naturalidad sin repetirlos literalmente):")
            for (mem in selectedMemories) {
                append("\n- [${mem.category.label}] ${mem.content}")
            }
        }

        if (phoneCallHighlights.isNotEmpty()) {
            append("\n\nRegistro reciente de llamadas locales:")
            for (call in phoneCallHighlights) {
                val detail = listOf(call.reason, call.userNotes, call.outcome, call.scheduledNote)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                val suffix = if (detail.isNotBlank()) " — $detail" else ""
                val pendingTag = if (call.followUpPending) " [Pendiente]" else ""
                append("\n- ${call.contactName} (${call.status.displayLabel})$pendingTag$suffix")
            }
        }

        if (activeTaskHighlights.isNotEmpty()) {
            val nowMs = System.currentTimeMillis()
            append("\n\nTareas y recordatorios pendientes del usuario:")
            var usedChars = 0
            for (task in activeTaskHighlights) {
                val overdueTag = if (task.isOverdue(nowMs)) " [Atrasada]" else ""
                val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
                    task.dueDateEpochDay,
                    task.dueTimeMinutes,
                    task.reminderAtMs,
                    nowMs
                )
                val contactTag = task.contactName?.takeIf { it.isNotBlank() }?.let { " · Contacto: $it" }.orEmpty()
                val line = "\n- ${task.title} (${task.priority.label}, $whenStr)$overdueTag$contactTag"
                if (usedChars > 0 && usedChars + line.length > 640) break
                append(line)
                usedChars += line.length
            }
        }

        if (conversationSummary.isNotBlank()) {
            append("\n\nResumen previo de esta conversación:")
            append('\n')
            append(conversationSummary.trim().take(480))
        }
    }

    private fun selectRelevantTasks(
        query: String,
        tasks: List<TaskItem>
    ): List<TaskItem> {
        val pending = tasks.filter { it.status == TaskStatus.PENDING }
        if (pending.isEmpty()) return emptyList()
        val nowMs = System.currentTimeMillis()
        val normalizedQuery = MemoryTextSimilarity.normalize(query)
        val broadAgendaQuery = listOf("agenda", "calendario", "tarea", "pendiente", "recordatorio", "hoy", "manana", "esta semana")
            .any(normalizedQuery::contains)
        val queryTokens = MemoryTextSimilarity.tokenizeSignificant(query)
        return pending.sortedWith(
            compareByDescending<TaskItem> { task ->
                val textTokens = MemoryTextSimilarity.tokenizeSignificant(
                    "${task.title} ${task.category} ${task.contactName.orEmpty()} ${task.description} ${task.notes}"
                )
                queryTokens.intersect(textTokens).size
            }
                .thenByDescending { if (it.isOverdue(nowMs)) 1 else 0 }
                .thenBy { it.dueDateEpochDay ?: Long.MAX_VALUE }
                .thenByDescending { it.priority.sortRank }
        ).take(if (broadAgendaQuery) 6 else 4)
    }

    private fun selectRelevantCalls(
        query: String,
        records: List<PhoneCallRecord>
    ): List<PhoneCallRecord> {
        if (records.isEmpty()) return emptyList()
        val queryTokens = MemoryTextSimilarity.tokenizeSignificant(query)
        val hasPhoneCue = queryTokens.any {
            it in setOf("llama", "llame", "llamada", "llamadas", "telefono", "pendiente", "devolver", "hablar", "contacto")
        }
        val filtered = if (hasPhoneCue) {
            records.take(3)
        } else {
            records.filter { call ->
                val nameTokens = MemoryTextSimilarity.tokenizeSignificant(call.contactName)
                call.followUpPending || queryTokens.intersect(nameTokens).isNotEmpty()
            }.take(2)
        }
        return filtered
    }

    internal fun trimHistoryToBudget(
        history: List<ChatMessage>,
        maxMessages: Int,
        maxChars: Int
    ): List<ChatMessage> {
        if (history.isEmpty() || maxMessages <= 0 || maxChars <= 0) return emptyList()
        val chosen = ArrayDeque<ChatMessage>()
        var chars = 0
        for (msg in history.asReversed()) {
            if (msg.role == ChatRole.SYSTEM) continue
            if (chosen.size >= maxMessages) break
            val msgChars = msg.content.length + 16
            if (chosen.isNotEmpty() && chars + msgChars > maxChars) break
            chosen.addFirst(msg)
            chars += msgChars
        }
        return chosen.toList()
    }
}
