package com.orion.voicelab.llm

import com.orion.voicelab.data.ChatMessage

data class LlmGenerationResult(
    val text: String,
    val firstTokenMs: Long,
    val totalMs: Long,
    val tokensGenerated: Int,
    val cancelled: Boolean
) {
    val tokensPerSecond: Double
        get() = if (totalMs > 0L && tokensGenerated > 0) {
            tokensGenerated * 1000.0 / totalMs.toDouble()
        } else {
            0.0
        }
}

interface LocalLanguageModel {
    val profileId: String get() = "lite"
    /** Effective native context after RAM-based downscaling; default is the base profile window. */
    val contextWindowTokens: Int get() = 2048
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
