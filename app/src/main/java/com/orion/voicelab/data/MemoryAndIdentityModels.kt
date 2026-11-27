package com.orion.voicelab.data

enum class MemoryCategory(val label: String) {
    IDENTITY("Identidad"),
    PREFERENCE("Preferencia"),
    ROUTINE("Rutina"),
    PROJECT("Proyecto"),
    DECISION("Decisión"),
    GENERAL("General");

    companion object {
        fun fromStored(raw: String?): MemoryCategory =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: GENERAL
    }
}

data class MemoryItem(
    val id: Long = 0L,
    val category: MemoryCategory,
    val content: String,
    val importance: Int = 3,
    val sourceConversationId: Long? = null,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
)

data class MemorySuggestion(
    val id: String,
    val category: MemoryCategory,
    val content: String,
    val importance: Int = 3,
    val sourceConversationId: Long? = null
)

enum class FormalityLevel(
    val displayName: String,
    val promptInstruction: String
) {
    CERCANO(
        displayName = "Cercano",
        promptInstruction = "Tutea de forma natural, cercana y humana."
    ),
    EQUILIBRADO(
        displayName = "Equilibrado",
        promptInstruction = "Mantén un trato cordial, claro y respetuoso sin rigidez."
    ),
    FORMAL(
        displayName = "Formal",
        promptInstruction = "Trata de usted con claridad, cortesía y precisión."
    );

    companion object {
        fun fromStored(raw: String?): FormalityLevel =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: CERCANO
    }
}

data class AssistantIdentity(
    val assistantName: String = DEFAULT_NAME,
    val personality: String = DEFAULT_PERSONALITY,
    val tone: String = DEFAULT_TONE,
    val formality: FormalityLevel = FormalityLevel.CERCANO,
    val basePrompt: String = DEFAULT_BASE_PROMPT,
    val memoryEnabled: Boolean = true,
    val updatedAtMs: Long = 0L
) {
    companion object {
        const val DEFAULT_NAME = "ORION"
        const val DEFAULT_PERSONALITY = "Natural, directo, cercano, resolutivo y útil"
        const val DEFAULT_TONE = "Conversacional, calmado y humano"
        const val DEFAULT_BASE_PROMPT =
            "Eres un asistente personal autónomo por voz en español. " +
                "Respondes como en una llamada telefónica real: claro, breve (2 o 3 frases como máximo), " +
                "sin listas con asteriscos ni formato Markdown y sin lenguaje robótico."
    }
}

data class ConversationSummary(
    val id: Long,
    val title: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val messageCount: Int,
    val lastPreview: String = "",
    val summaryText: String = ""
)

data class TurnContext(
    val systemPrompt: String,
    val recentHistory: List<ChatMessage>,
    val usedMemories: List<MemoryItem> = emptyList()
)

data class ContactProfile(
    val id: Long = PRIMARY_CONTACT_ID,
    val name: String,
    val roleTitle: String,
    val specialtyPrompt: String,
    val voiceId: String = "sharvard_f",
    val isPrimary: Boolean = false,
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L
) {
    companion object {
        const val PRIMARY_CONTACT_ID = 0L
        const val DEFAULT_PRIMARY_ROLE = "ORION Principal · Coordinador"

        fun primaryFromIdentity(identity: AssistantIdentity, voiceId: String = "sharvard_f"): ContactProfile =
            ContactProfile(
                id = PRIMARY_CONTACT_ID,
                name = identity.assistantName.ifBlank { AssistantIdentity.DEFAULT_NAME },
                roleTitle = DEFAULT_PRIMARY_ROLE,
                specialtyPrompt = identity.basePrompt,
                voiceId = voiceId,
                isPrimary = true,
                updatedAtMs = identity.updatedAtMs
            )
    }
}
