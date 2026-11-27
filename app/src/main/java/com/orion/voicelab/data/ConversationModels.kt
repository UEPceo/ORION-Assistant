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
    val llmTokensGenerated: Int = 0,
    val llmTokensPerSec: Double = 0.0,
    val ttsSynthMs: Long,
    val synthesizedAudioMs: Long = 0L,
    val endToSpeechStartMs: Long,
    val responseDurationMs: Long = 0L
)

data class ConversationTurn(
    val id: Long,
    val role: ChatRole,
    val text: String,
    val audioMs: Long = 0L,
    val decodeMs: Long = 0L,
    val latency: TurnLatencyMetrics? = null,
    val timestampMs: Long = System.currentTimeMillis()
)

data class VoiceProfile(
    val id: String,
    val displayName: String,
    val speakerId: Int,
    val description: String,
    val modelDirectoryName: String = "",
    val modelFileName: String = "",
    val downloadUrl: String? = null,
    val isInstalled: Boolean = true
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

data class AudioHardwareStatus(
    val isCapturing: Boolean = false,
    val isMuted: Boolean = false,
    val audioSourceName: String = "inactivo",
    val sampleRateHz: Int = 16_000,
    val aecAvailable: Boolean = false,
    val aecActive: Boolean = false,
    val nsAvailable: Boolean = false,
    val nsActive: Boolean = false,
    val isPlaying: Boolean = false,
    val audioOutputRoute: String = "Altavoz"
)

data class CallTelemetry(
    val asrLoadMs: Long = -1L,
    val llmLoadMs: Long = -1L,
    val ttsLoadMs: Long = -1L,
    val lastAsrMs: Long = -1L,
    val lastLlmFirstTokenMs: Long = -1L,
    val lastLlmTotalMs: Long = -1L,
    val lastLlmTokensPerSec: Double = 0.0,
    val avgLlmMs: Long = 0L,
    val lastTtsSynthMs: Long = -1L,
    val lastSynthesizedAudioMs: Long = -1L,
    val lastEndToSpeechMs: Long = -1L,
    val lastResponseDurationMs: Long = -1L,
    val llmTurnsCount: Int = 0,
    val totalLlmMs: Long = 0L
) {
    fun withTurn(latency: TurnLatencyMetrics): CallTelemetry {
        val newCount = llmTurnsCount + 1
        val newTotal = totalLlmMs + latency.llmTotalMs
        return copy(
            lastAsrMs = latency.asrMs,
            lastLlmFirstTokenMs = latency.llmFirstTokenMs,
            lastLlmTotalMs = latency.llmTotalMs,
            lastLlmTokensPerSec = latency.llmTokensPerSec,
            avgLlmMs = newTotal / newCount,
            lastTtsSynthMs = latency.ttsSynthMs,
            lastSynthesizedAudioMs = latency.synthesizedAudioMs,
            lastEndToSpeechMs = latency.endToSpeechStartMs,
            lastResponseDurationMs = latency.responseDurationMs,
            llmTurnsCount = newCount,
            totalLlmMs = newTotal
        )
    }
}
