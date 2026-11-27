package com.orion.voicelab.data

import java.util.Locale

data class AsrStats(
    val count: Int = 0,
    val audioMs: Long = 0L,
    val decodeMs: Long = 0L,
    val maxDecodeMs: Long = 0L
) {
    /** Factor de tiempo real: < 1 significa que decodifica más rápido que el audio. */
    val rtf: Double get() = if (audioMs > 0) decodeMs.toDouble() / audioMs else 0.0
    val avgDecodeMs: Long get() = if (count > 0) decodeMs / count else 0L

    fun withSample(audio: Long, decode: Long): AsrStats = copy(
        count = count + 1,
        audioMs = audioMs + audio,
        decodeMs = decodeMs + decode,
        maxDecodeMs = maxOf(maxDecodeMs, decode)
    )

    fun summaryLine(): String = if (count == 0) {
        "Frases reconocidas: 0"
    } else {
        String.format(
            Locale.getDefault(),
            "Frases: %d · decodif. media %d ms · máx %d ms · RTF %.2f",
            count, avgDecodeMs, maxDecodeMs, rtf
        )
    }
}

data class TranscriptEntry(
    val id: Long,
    val text: String,
    val audioMs: Long,
    val decodeMs: Long
)

enum class ListeningState {
    IDLE,
    LOADING_MODELS,
    LISTENING,
    VOICE_DETECTED,
    STOPPING
}

sealed interface ModelInstallState {
    data object NotInstalled : ModelInstallState
    data class Downloading(val progressPercent: Int) : ModelInstallState
    data object Extracting : ModelInstallState
    data class Installed(val sizeMb: Long) : ModelInstallState
    data class Error(val message: String) : ModelInstallState
}

data class VoiceLabUiState(
    val modelState: ModelInstallState = ModelInstallState.NotInstalled,
    val listeningState: ListeningState = ListeningState.IDLE,
    val statusMessage: String = "",
    val metricsSnapshot: DeviceMetrics.Snapshot? = null,
    val modelSizeMb: Long = 0L,
    val modelLoadMs: Long = -1L,
    val asrStats: AsrStats = AsrStats(),
    val transcripts: List<TranscriptEntry> = emptyList(),
    val peakPssMb: Long = 0L,
    val minAvailMb: Long = Long.MAX_VALUE,
    val startSnapshot: DeviceMetrics.Snapshot? = null,
    val endSnapshot: DeviceMetrics.Snapshot? = null,
    val sessionStartMs: Long = 0L,
    val sessionEndMs: Long = 0L,
    val isBusy: Boolean = false
) {
    val isModelReady: Boolean
        get() = modelState is ModelInstallState.Installed

    val isDownloading: Boolean
        get() = modelState is ModelInstallState.Downloading || modelState is ModelInstallState.Extracting

    val isRunning: Boolean
        get() = listeningState == ListeningState.LISTENING ||
            listeningState == ListeningState.VOICE_DETECTED
}
