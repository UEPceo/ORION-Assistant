package com.orion.voicelab.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.ListeningState
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.TranscriptEntry
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

class VoiceLabViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val METRICS_TICK_MS = 15_000L
    }

    private val appContext = application.applicationContext
    private val engine = OrionVoiceEngine()
    private var tickerJob: Job? = null
    private var nextTranscriptId = 1L

    private val _uiState = MutableStateFlow(VoiceLabUiState())
    val uiState: StateFlow<VoiceLabUiState> = _uiState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    init {
        refreshModelState()
        refreshMetrics()
    }

    fun refreshModelState() {
        val ready = MoonshineModelManager.modelFilesPresent(appContext.filesDir)
        val sizeMb = MoonshineModelManager.modelSizeMb(appContext.filesDir)
        _uiState.update { state ->
            state.copy(
                modelState = if (ready) {
                    ModelInstallState.Installed(sizeMb)
                } else {
                    ModelInstallState.NotInstalled
                },
                modelSizeMb = sizeMb,
                statusMessage = if (ready) {
                    "Moonshine Base ES instalado. Reconocimiento local disponible sin Internet."
                } else {
                    "Modelo español todavía no instalado. Descárgalo con conexión Wi-Fi. Después funcionará offline."
                }
            )
        }
    }

    fun downloadModel() {
        if (_uiState.value.isDownloading || _uiState.value.isModelReady) return

        _uiState.update {
            it.copy(
                modelState = ModelInstallState.Downloading(0),
                statusMessage = "Descargando modelo oficial Moonshine Base ES…"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                MoonshineModelManager.downloadAndInstall(
                    filesDir = appContext.filesDir,
                    cacheDir = appContext.cacheDir,
                    onProgress = { percent ->
                        _uiState.update {
                            it.copy(modelState = ModelInstallState.Downloading(percent))
                        }
                    },
                    onExtracting = {
                        _uiState.update {
                            it.copy(
                                modelState = ModelInstallState.Extracting,
                                statusMessage = "Extrayendo archivos del modelo offline…"
                            )
                        }
                    }
                )
                refreshModelState()
                refreshMetrics()
            } catch (e: Exception) {
                val msg = e.message ?: "error desconocido"
                _uiState.update {
                    it.copy(
                        modelState = ModelInstallState.Error(msg),
                        statusMessage = "No se pudo instalar el modelo: $msg"
                    )
                }
            }
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(statusMessage = "Permiso de micrófono denegado.")
        }
    }

    fun startListening() {
        if (!MoonshineModelManager.modelFilesPresent(appContext.filesDir)) return
        if (_uiState.value.isBusy) return

        _uiState.update {
            it.copy(
                isBusy = true,
                listeningState = ListeningState.LOADING_MODELS,
                statusMessage = "Cargando Moonshine Base ES y Silero VAD…"
            )
        }

        val started = engine.start(
            assets = appContext.assets,
            modelDirectory = MoonshineModelManager.modelDirectory(appContext.filesDir),
            listener = object : OrionVoiceEngine.Listener {
                override fun onModelsLoaded(loadTimeMs: Long) {
                    val begin = DeviceMetrics.snapshot(appContext, appContext.filesDir)
                    _uiState.update {
                        it.copy(
                            modelLoadMs = loadTimeMs,
                            startSnapshot = begin,
                            endSnapshot = null,
                            sessionStartMs = SystemClock.elapsedRealtime(),
                            sessionEndMs = 0L,
                            peakPssMb = begin.appPssMb,
                            minAvailMb = begin.availRamMb
                        )
                    }
                }

                override fun onListeningStarted() {
                    _uiState.update {
                        it.copy(
                            listeningState = ListeningState.LISTENING,
                            statusMessage = "Escuchando. Habla con naturalidad; la frase se cierra tras una pausa."
                        )
                    }
                    refreshMetrics()
                    startMetricsTicker()
                }

                override fun onSpeechStateChanged(detected: Boolean) {
                    _uiState.update { current ->
                        if (!current.isRunning) current
                        else current.copy(
                            listeningState = if (detected) {
                                ListeningState.VOICE_DETECTED
                            } else {
                                ListeningState.LISTENING
                            },
                            statusMessage = if (detected) {
                                "Voz detectada…"
                            } else {
                                "Escuchando. Habla con naturalidad; la frase se cierra tras una pausa."
                            }
                        )
                    }
                }

                override fun onAudioLevel(level: Float) {
                    if (_uiState.value.isRunning) {
                        _audioLevel.value = level
                    }
                }

                override fun onTranscriptReady(text: String, audioMs: Long, decodeMs: Long) {
                    _uiState.update { current ->
                        val updatedStats = current.asrStats.withSample(audioMs, decodeMs)
                        val updatedTranscripts = if (text.isNotBlank()) {
                            current.transcripts + TranscriptEntry(
                                id = nextTranscriptId++,
                                text = text,
                                audioMs = audioMs,
                                decodeMs = decodeMs
                            )
                        } else {
                            current.transcripts
                        }
                        current.copy(
                            asrStats = updatedStats,
                            transcripts = updatedTranscripts
                        )
                    }
                    refreshMetrics()
                }

                override fun onError(message: String) {
                    _uiState.update {
                        it.copy(
                            statusMessage = if (message.startsWith("Error")) {
                                message
                            } else {
                                "Error: $message"
                            }
                        )
                    }
                }

                override fun onStopped() {
                    stopMetricsTicker()
                    val finish = try {
                        DeviceMetrics.snapshot(appContext, appContext.filesDir)
                    } catch (_: Exception) {
                        null
                    }
                    val ready = MoonshineModelManager.modelFilesPresent(appContext.filesDir)
                    _audioLevel.value = 0f
                    _uiState.update { current ->
                        val hadError = current.statusMessage.startsWith("Error")
                        current.copy(
                            isBusy = false,
                            listeningState = ListeningState.IDLE,
                            endSnapshot = if (finish != null && current.startSnapshot != null) {
                                finish
                            } else {
                                current.endSnapshot
                            },
                            sessionEndMs = if (finish != null && current.startSnapshot != null) {
                                SystemClock.elapsedRealtime()
                            } else {
                                current.sessionEndMs
                            },
                            modelState = if (ready) {
                                ModelInstallState.Installed(
                                    MoonshineModelManager.modelSizeMb(appContext.filesDir)
                                )
                            } else {
                                current.modelState
                            },
                            statusMessage = if (hadError) {
                                current.statusMessage
                            } else {
                                "Escucha detenida. El modelo permanece instalado para uso offline."
                            }
                        )
                    }
                    refreshMetrics()
                }
            }
        )

        if (!started) {
            _uiState.update { it.copy(isBusy = false, listeningState = ListeningState.IDLE) }
        }
    }

    fun stopListening() {
        if (!engine.isRunning) return
        _uiState.update {
            it.copy(
                listeningState = ListeningState.STOPPING,
                statusMessage = "Deteniendo…"
            )
        }
        engine.stop()
    }

    fun refreshMetrics() {
        val snap = DeviceMetrics.snapshot(appContext, appContext.filesDir)
        val sizeMb = MoonshineModelManager.modelSizeMb(appContext.filesDir)
        _uiState.update { current ->
            val runningNow = engine.isRunning
            val newPeakPss = if (runningNow && snap.appPssMb > current.peakPssMb) {
                snap.appPssMb
            } else {
                current.peakPssMb
            }
            val newMinAvail = if (runningNow && snap.availRamMb < current.minAvailMb) {
                snap.availRamMb
            } else {
                current.minAvailMb
            }
            current.copy(
                metricsSnapshot = snap,
                modelSizeMb = sizeMb,
                peakPssMb = newPeakPss,
                minAvailMb = newMinAvail
            )
        }
    }

    fun resetMeasurements() {
        _uiState.update { current ->
            val runningNow = engine.isRunning
            current.copy(
                asrStats = AsrStats(),
                peakPssMb = 0L,
                minAvailMb = Long.MAX_VALUE,
                startSnapshot = if (runningNow) current.startSnapshot else null,
                endSnapshot = if (runningNow) current.endSnapshot else null,
                transcripts = emptyList()
            )
        }
        refreshMetrics()
    }

    fun buildReport(): String {
        val state = _uiState.value
        val now = DeviceMetrics.snapshot(appContext, appContext.filesDir)
        return buildString {
            appendLine("ORION Voice Lab 0.3 — informe de medición")
            appendLine(DeviceMetrics.deviceSummary())
            appendLine("Ahora:")
            appendLine(DeviceMetrics.describe(now))
            appendLine("Modelo en disco: ${state.modelSizeMb} MB")
            appendLine(
                if (state.modelLoadMs >= 0) {
                    "Carga de modelo + VAD: ${state.modelLoadMs} ms"
                } else {
                    "Carga de modelo + VAD: sin medir"
                }
            )
            appendLine(state.asrStats.summaryLine())
            if (state.asrStats.count > 0) {
                appendLine(
                    String.format(
                        Locale.getDefault(),
                        "Audio total reconocido: %.1f s",
                        state.asrStats.audioMs / 1000.0
                    )
                )
            }
            val begin = state.startSnapshot
            if (begin != null) {
                val end = state.endSnapshot
                val endMs = if (end != null && state.sessionEndMs > 0) {
                    state.sessionEndMs
                } else {
                    SystemClock.elapsedRealtime()
                }
                val seconds = (endMs - state.sessionStartMs) / 1000
                appendLine("Sesión de escucha: $seconds s${if (end == null) " (en curso)" else ""}")
                val minAvailText = if (state.minAvailMb == Long.MAX_VALUE) {
                    "n/d"
                } else {
                    "${state.minAvailMb} MB"
                }
                appendLine("Pico de memoria de ORION (PSS): ${state.peakPssMb} MB · mínimo de RAM libre: $minAvailText")
                appendLine(
                    "Batería inicio: ${begin.batteryPercent} % · temp. ${DeviceMetrics.formatTemp(begin.batteryTempC)}"
                )
                if (end != null) {
                    appendLine(
                        "Batería fin: ${end.batteryPercent} % · temp. ${DeviceMetrics.formatTemp(end.batteryTempC)} · térmico: ${end.thermalStatus}"
                    )
                }
            } else {
                appendLine("Sesión de escucha: sin datos todavía")
            }
            appendLine(
                "Parámetros VAD: umbral ${OrionVoiceEngine.VAD_THRESHOLD} · " +
                    "pausa de cierre ${OrionVoiceEngine.VAD_MIN_SILENCE_S} s · " +
                    "máx. frase ${OrionVoiceEngine.VAD_MAX_SPEECH_S} s"
            )
        }
    }

    private fun startMetricsTicker() {
        tickerJob?.cancel()
        tickerJob = viewModelScope.launch {
            while (isActive && engine.isRunning) {
                delay(METRICS_TICK_MS)
                if (engine.isRunning) {
                    refreshMetrics()
                }
            }
        }
    }

    private fun stopMetricsTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    override fun onCleared() {
        stopMetricsTicker()
        engine.destroy()
        super.onCleared()
    }
}
