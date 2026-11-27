package com.orion.voicelab.persistence

import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.data.ContactProfile
import com.orion.voicelab.data.ConversationSummary
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.FormalityLevel
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.MemoryItem
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.memory.MemoryTextSimilarity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConversationRepository(
    private val dao: ConversationDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val DEFAULT_CONVERSATION_TITLE = "Nueva conversación"
        private const val RECENT_DETAIL_WINDOW = 8
        private const val MAX_SUMMARY_CHARS = 480
    }

    fun observeConversations(): Flow<List<ConversationSummary>> =
        dao.observeConversationRows().map { rows ->
            rows.map { row ->
                ConversationSummary(
                    id = row.id,
                    title = row.title,
                    createdAtMs = row.createdAtMs,
                    updatedAtMs = row.updatedAtMs,
                    messageCount = row.messageCount,
                    lastPreview = row.lastPreview.orEmpty(),
                    summaryText = row.summaryText
                )
            }
        }

    suspend fun createConversation(title: String = DEFAULT_CONVERSATION_TITLE): Long {
        val now = clockMs()
        return dao.insertConversation(
            ConversationEntity(
                title = title.trim().ifBlank { DEFAULT_CONVERSATION_TITLE },
                createdAtMs = now,
                updatedAtMs = now,
                summaryText = ""
            )
        )
    }

    suspend fun ensureLatestOrCreateConversation(): Long {
        val latest = dao.getLatestConversation()
        return latest?.id ?: createConversation()
    }

    suspend fun getConversationSummary(conversationId: Long): ConversationSummary? {
        val conv = dao.getConversationById(conversationId) ?: return null
        val messages = dao.getMessagesForConversation(conversationId)
        return ConversationSummary(
            id = conv.id,
            title = conv.title,
            createdAtMs = conv.createdAtMs,
            updatedAtMs = conv.updatedAtMs,
            messageCount = messages.size,
            lastPreview = messages.lastOrNull()?.text.orEmpty(),
            summaryText = conv.summaryText
        )
    }

    suspend fun loadConversationTurns(conversationId: Long): List<ConversationTurn> =
        dao.getMessagesForConversation(conversationId).map { entity ->
            entity.toDomainTurn()
        }

    suspend fun appendUserMessage(
        conversationId: Long,
        text: String,
        audioMs: Long,
        decodeMs: Long
    ): ConversationTurn {
        val now = clockMs()
        val id = dao.insertMessage(
            ConversationMessageEntity(
                conversationId = conversationId,
                role = ChatRole.USER.name,
                text = text,
                createdAtMs = now,
                audioMs = audioMs,
                decodeMs = decodeMs
            )
        )
        val existing = dao.getConversationById(conversationId)
        if (existing != null) {
            if (existing.title == DEFAULT_CONVERSATION_TITLE) {
                dao.updateTitleAndTimestamp(conversationId, deriveTitle(text), now)
            } else {
                refreshConversationMetadata(conversationId, now)
            }
        }
        return ConversationTurn(
            id = id,
            role = ChatRole.USER,
            text = text,
            audioMs = audioMs,
            decodeMs = decodeMs,
            timestampMs = now
        )
    }

    suspend fun appendAssistantMessage(
        conversationId: Long,
        text: String,
        latency: TurnLatencyMetrics
    ): ConversationTurn {
        val now = clockMs()
        val id = dao.insertMessage(
            ConversationMessageEntity(
                conversationId = conversationId,
                role = ChatRole.ASSISTANT.name,
                text = text,
                createdAtMs = now,
                llmFirstTokenMs = latency.llmFirstTokenMs,
                llmTotalMs = latency.llmTotalMs,
                llmTokensGenerated = latency.llmTokensGenerated,
                llmTokensPerSec = latency.llmTokensPerSec,
                ttsSynthMs = latency.ttsSynthMs,
                synthesizedAudioMs = latency.synthesizedAudioMs,
                endToSpeechStartMs = latency.endToSpeechStartMs,
                responseDurationMs = latency.responseDurationMs
            )
        )
        refreshConversationMetadata(conversationId, now)
        return ConversationTurn(
            id = id,
            role = ChatRole.ASSISTANT,
            text = text,
            latency = latency,
            timestampMs = now
        )
    }

    suspend fun deleteConversation(conversationId: Long) {
        dao.deleteConversationById(conversationId)
    }

    private suspend fun refreshConversationMetadata(conversationId: Long, now: Long) {
        val messages = dao.getMessagesForConversation(conversationId)
        val summary = buildRollingSummary(messages)
        dao.updateTimestampAndSummary(conversationId, now, summary)
    }

    private fun buildRollingSummary(messages: List<ConversationMessageEntity>): String {
        if (messages.size <= RECENT_DETAIL_WINDOW) return ""
        val older = messages.dropLast(RECENT_DETAIL_WINDOW)
        val snippets = mutableListOf<String>()
        var totalChars = 0
        for (msg in older.takeLast(6)) {
            val prefix = if (msg.role == ChatRole.USER.name) "U" else "A"
            val clipped = msg.text.replace('\n', ' ').trim().let {
                if (it.length > 90) it.take(87) + "…" else it
            }
            val line = "$prefix: $clipped"
            if (totalChars + line.length + 3 > MAX_SUMMARY_CHARS) break
            snippets.add(line)
            totalChars += line.length + 3
        }
        return snippets.joinToString(" | ")
    }

    private fun deriveTitle(firstMessage: String): String {
        val normalized = firstMessage.replace('\n', ' ').trim()
        if (normalized.isEmpty()) return DEFAULT_CONVERSATION_TITLE
        return if (normalized.length <= 44) normalized else normalized.take(41).trimEnd() + "…"
    }

    private fun ConversationMessageEntity.toDomainTurn(): ConversationTurn {
        val chatRole = if (role == ChatRole.USER.name) ChatRole.USER else ChatRole.ASSISTANT
        val latencyMetrics = if (chatRole == ChatRole.ASSISTANT && llmTotalMs >= 0L) {
            TurnLatencyMetrics(
                asrMs = decodeMs.coerceAtLeast(0L),
                llmFirstTokenMs = llmFirstTokenMs.coerceAtLeast(0L),
                llmTotalMs = llmTotalMs,
                llmTokensGenerated = llmTokensGenerated,
                llmTokensPerSec = llmTokensPerSec,
                ttsSynthMs = ttsSynthMs.coerceAtLeast(0L),
                synthesizedAudioMs = synthesizedAudioMs,
                endToSpeechStartMs = endToSpeechStartMs.coerceAtLeast(0L),
                responseDurationMs = responseDurationMs
            )
        } else {
            null
        }
        return ConversationTurn(
            id = id,
            role = chatRole,
            text = text,
            audioMs = audioMs,
            decodeMs = decodeMs,
            latency = latencyMetrics,
            timestampMs = createdAtMs
        )
    }
}

class MemoryRepository(
    private val dao: MemoryDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    fun observeMemories(): Flow<List<MemoryItem>> =
        dao.observeAllMemories().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getAllMemories(): List<MemoryItem> =
        dao.getAllMemories().map { it.toDomain() }

    suspend fun addMemory(
        category: MemoryCategory,
        content: String,
        importance: Int = 3,
        sourceConversationId: Long? = null
    ): Long {
        val clean = content.trim()
        require(clean.isNotEmpty()) { "El recuerdo no puede estar vacío" }
        val boundedImportance = importance.coerceIn(1, 5)
        val now = clockMs()

        val existing = dao.getAllMemories()
        val duplicate = existing.firstOrNull { current ->
            MemoryTextSimilarity.isNearDuplicate(current.content, clean)
        }
        if (duplicate != null) {
            dao.updateMemory(
                id = duplicate.id,
                category = category.name,
                content = clean,
                importance = maxOf(duplicate.importance, boundedImportance),
                updatedAtMs = now
            )
            return duplicate.id
        }

        return dao.insertMemory(
            MemoryItemEntity(
                category = category.name,
                content = clean,
                importance = boundedImportance,
                sourceConversationId = sourceConversationId,
                createdAtMs = now,
                updatedAtMs = now
            )
        )
    }

    suspend fun updateMemory(
        id: Long,
        category: MemoryCategory,
        content: String,
        importance: Int
    ) {
        val clean = content.trim()
        require(clean.isNotEmpty()) { "El recuerdo no puede estar vacío" }
        dao.updateMemory(
            id = id,
            category = category.name,
            content = clean,
            importance = importance.coerceIn(1, 5),
            updatedAtMs = clockMs()
        )
    }

    suspend fun deleteMemory(id: Long) {
        dao.deleteMemoryById(id)
    }

    suspend fun clearAllMemories() {
        dao.deleteAllMemories()
    }

    private fun MemoryItemEntity.toDomain(): MemoryItem = MemoryItem(
        id = id,
        category = MemoryCategory.fromStored(category),
        content = content,
        importance = importance,
        sourceConversationId = sourceConversationId,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}

class IdentityRepository(
    private val dao: IdentityDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    fun observeIdentity(): Flow<AssistantIdentity> =
        dao.observeIdentity().map { entity ->
            entity?.toDomain() ?: AssistantIdentity()
        }

    suspend fun ensureInitialized(): AssistantIdentity {
        val current = dao.getIdentity()
        if (current != null) return current.toDomain()
        val defaults = AssistantIdentity(updatedAtMs = clockMs())
        dao.upsertIdentity(defaults.toEntity())
        return defaults
    }

    suspend fun getIdentity(): AssistantIdentity =
        dao.getIdentity()?.toDomain() ?: ensureInitialized()

    suspend fun saveIdentity(identity: AssistantIdentity) {
        val sanitized = identity.copy(
            assistantName = identity.assistantName.trim().ifBlank { AssistantIdentity.DEFAULT_NAME },
            personality = identity.personality.trim().ifBlank { AssistantIdentity.DEFAULT_PERSONALITY },
            tone = identity.tone.trim().ifBlank { AssistantIdentity.DEFAULT_TONE },
            basePrompt = identity.basePrompt.trim().ifBlank { AssistantIdentity.DEFAULT_BASE_PROMPT },
            updatedAtMs = clockMs()
        )
        dao.upsertIdentity(sanitized.toEntity())
    }

    suspend fun setMemoryEnabled(enabled: Boolean) {
        val current = getIdentity()
        saveIdentity(current.copy(memoryEnabled = enabled))
    }

    suspend fun resetToDefaults() {
        val current = getIdentity()
        saveIdentity(
            AssistantIdentity(
                memoryEnabled = current.memoryEnabled,
                updatedAtMs = clockMs()
            )
        )
    }

    private fun AssistantIdentityEntity.toDomain(): AssistantIdentity = AssistantIdentity(
        assistantName = assistantName,
        personality = personality,
        tone = tone,
        formality = FormalityLevel.fromStored(formality),
        basePrompt = basePrompt,
        memoryEnabled = memoryEnabled,
        updatedAtMs = updatedAtMs
    )

    private fun AssistantIdentity.toEntity(): AssistantIdentityEntity = AssistantIdentityEntity(
        id = 1,
        assistantName = assistantName,
        personality = personality,
        tone = tone,
        formality = formality.name,
        basePrompt = basePrompt,
        memoryEnabled = memoryEnabled,
        updatedAtMs = updatedAtMs
    )
}

class ContactRepository(
    private val dao: ContactDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        const val DEFAULT_ROLE = "Co-worker especialista"
        const val DEFAULT_SPECIALTY_PROMPT =
            "Complementas a ORION Principal aportando apoyo directo, práctico y conciso en tu especialidad."
    }

    fun observeCoworkers(): Flow<List<ContactProfile>> =
        dao.observeCoworkers().map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun getAllCoworkers(): List<ContactProfile> =
        dao.getAllCoworkers().map { it.toDomain() }

    suspend fun addCoworker(
        name: String,
        roleTitle: String,
        specialtyPrompt: String,
        voiceId: String = "sharvard_f"
    ): Long {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "El nombre del contacto no puede estar vacío" }
        val now = clockMs()
        return dao.insertCoworker(
            CoworkerContactEntity(
                name = cleanName,
                roleTitle = roleTitle.trim().ifBlank { DEFAULT_ROLE },
                specialtyPrompt = specialtyPrompt.trim().ifBlank { DEFAULT_SPECIALTY_PROMPT },
                voiceId = voiceId.trim().ifBlank { "sharvard_f" },
                createdAtMs = now,
                updatedAtMs = now
            )
        )
    }

    suspend fun updateCoworker(
        id: Long,
        name: String,
        roleTitle: String,
        specialtyPrompt: String,
        voiceId: String
    ) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "El nombre del contacto no puede estar vacío" }
        dao.updateCoworker(
            id = id,
            name = cleanName,
            roleTitle = roleTitle.trim().ifBlank { DEFAULT_ROLE },
            specialtyPrompt = specialtyPrompt.trim().ifBlank { DEFAULT_SPECIALTY_PROMPT },
            voiceId = voiceId.trim().ifBlank { "sharvard_f" },
            updatedAtMs = clockMs()
        )
    }

    suspend fun deleteCoworker(id: Long) {
        dao.deleteCoworkerById(id)
    }

    private fun CoworkerContactEntity.toDomain(): ContactProfile = ContactProfile(
        id = id,
        name = name,
        roleTitle = roleTitle,
        specialtyPrompt = specialtyPrompt,
        voiceId = voiceId,
        isPrimary = false,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs
    )
}
