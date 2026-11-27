package com.orion.voicelab.llm

import com.orion.voicelab.data.ChatMessage

data class LlmGenerationResult(
    val text: String,
    val firstTokenMs: Long,
    val totalMs: Long,
    val tokensGenerated: Int,
    val cancelled: Boolean
)

interface LocalLanguageModel {
    val modelDisplayName: String
    val modelLicense: String

    fun isModelInstalled(): Boolean
    fun isLoaded(): Boolean
    fun modelSizeMb(): Long

    suspend fun load(): Long

    suspend fun generateReply(
        systemPrompt: String,
        history: List<ChatMessage>,
        onToken: (String) -> Unit
    ): LlmGenerationResult

    fun cancelGeneration()

    fun unload()
}
