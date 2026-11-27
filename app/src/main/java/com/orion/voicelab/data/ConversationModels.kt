package com.orion.voicelab.data

enum class ConversationState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    INTERRUPTED,
    ERROR
}

enum class ChatRole {
    SYSTEM,
    USER,
    ASSISTANT
}

data class ChatMessage(
    val role: ChatRole,
    val content: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class TurnLatencyMetrics(
    val asrMs: Long,
    val llmFirstTokenMs: Long,
    val llmTotalMs: Long,
    val ttsSynthMs: Long,
    val endToSpeechStartMs: Long
)

data class ConversationTurn(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val audioMs: Long = 0L,
    val decodeMs: Long = 0L,
    val latency: TurnLatencyMetrics? = null
)

data class VoiceProfile(
    val id: String,
    val displayName: String,
    val speakerId: Int,
    val description: String
)

enum class LocalModelKind {
    ASR,
    LLM,
    TTS
}

sealed interface ComponentModelState {
    data object NotInstalled : ComponentModelState
    data class Downloading(
        val progressPercent: Int,
        val downloadedMb: Long,
        val totalMb: Long
    ) : ComponentModelState
    data object Extracting : ComponentModelState
    data class Installed(val sizeMb: Long) : ComponentModelState
    data class Error(val message: String) : ComponentModelState
}

data class CallTelemetry(
    val asrLoadMs: Long = -1L,
    val llmLoadMs: Long = -1L,
    val ttsLoadMs: Long = -1L,
    val lastLlmFirstTokenMs: Long = -1L,
    val lastLlmTotalMs: Long = -1L,
    val avgLlmMs: Long = 0L,
    val lastTtsSynthMs: Long = -1L,
    val lastEndToSpeechMs: Long = -1L,
    val llmTurnsCount: Int = 0,
    val totalLlmMs: Long = 0L
) {
    fun withTurn(latency: TurnLatencyMetrics): CallTelemetry {
        val newCount = llmTurnsCount + 1
        val newTotal = totalLlmMs + latency.llmTotalMs
        return copy(
            lastLlmFirstTokenMs = latency.llmFirstTokenMs,
            lastLlmTotalMs = latency.llmTotalMs,
            avgLlmMs = newTotal / newCount,
            lastTtsSynthMs = latency.ttsSynthMs,
            lastEndToSpeechMs = latency.endToSpeechStartMs,
            llmTurnsCount = newCount,
            totalLlmMs = newTotal
        )
    }
}
