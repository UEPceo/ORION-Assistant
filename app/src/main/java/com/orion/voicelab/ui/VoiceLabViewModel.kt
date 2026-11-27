package com.orion.voicelab.ui

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orion.voicelab.asr.MoonshineSpeechRecognitionEngine
import com.orion.voicelab.conversation.ConversationEngine
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.CallTelemetry
import com.orion.voicelab.data.ComponentModelState
import com.orion.voicelab.data.ConversationState
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.ListeningState
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.TranscriptEntry
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import com.orion.voicelab.llm.LlamaCppLanguageModel
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.tts.SherpaOnnxSpeechSynthesizer
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
        private const val CALL_TIMER_TICK_MS = 1_000L
    }

    private val appContext = application.applicationContext
    private val engine = OrionVoiceEngine()

    private val speechRecognizer = MoonshineSpeechRecognitionEngine(
        modelDirectory = ModelManager.asrDirectory(appContext.filesDir)
    )
    private val languageModel = LlamaCppLanguageModel(
        contentResolver = appContext.contentResolver,
        modelFile = ModelManager.llmModelFile(appContext.filesDir)
    )
    private val speechSynthesizer = SherpaOnnxSpeechSynthesizer(
        filesDir = appContext.filesDir
    )
    private val conversationEngine = ConversationEngine(
        assets = appContext.assets,
        speechRecognizer = speechRecognizer,
        languageModel = languageModel,
        speechSynthesizer = speechSynthesizer
    )

    private var tickerJob: Job? = null
    private var callTimerJob: Job? = null
    private var nextTranscriptId = 1L

    private val _uiState = MutableStateFlow(
        VoiceLabUiState(
            availableVoices = speechSynthesizer.availableVoices(),
            selectedVoiceId = speechSynthesizer.selectedVoice().id
        )
    )
    val uiState: StateFlow<VoiceLabUiState> = _uiState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    init {
        attachConversationCallbacks()
        refreshModelState()
        refreshMetrics()
    }

    private fun attachConversationCallbacks() {
        conversationEngine.attachCallbacks(object : ConversationEngine.Callbacks {
            override fun onStateChanged(state: ConversationState, statusText: String) {
                _uiState.update { current ->
                    current.copy(
                        conversationState = state,
                        callStatusMessage = statusText,
                        isCallActive = conversationEngine.isCallActive()
                    )
                }
                if (state == ConversationState.SPEAKING || state == ConversationState.LISTENING) {
                    refreshMetrics()
                }
            }

            override fun onSpeechDetectedChanged(detected: Boolean) {
                _uiState.update { it.copy(isSpeechDetected = detected) }
            }

            override fun onAudioLevel(level: Float) {
                if (conversationEngine.isCallActive()) {
                    _audioLevel.value = level
                }
            }

            override fun onTurnsUpdated(turns: List<ConversationTurn>) {
                _uiState.update { it.copy(conversationTurns = turns) }
            }

            override fun onStreamingText(text: String) {
                _uiState.update { it.copy(streamingAssistantText = text) }
            }

            override fun onTelemetryUpdated(telemetry: CallTelemetry) {
                _uiState.update { it.copy(callTelemetry = telemetry) }
                refreshMetrics()
            }

            override fun onModelsInRamChanged(loaded: Boolean) {
                _uiState.update { it.copy(areLoadedModelsInRam = loaded) }
                refreshMetrics()
            }

            override fun onCallEnded() {
                stopCallTimer()
                stopMetricsTicker()
                val finish = try {
                    DeviceMetrics.snapshot(appContext, appContext.filesDir)
                } catch (_: Exception) {
                    null
                }
                _audioLevel.value = 0f
                _uiState.update { current ->
                    current.copy(
                        isCallActive = false,
                        isSpeechDetected = false,
                        endSnapshot = finish ?: current.endSnapshot,
                        sessionEndMs = if (current.sessionStartMs > 0L) {
                            SystemClock.elapsedRealtime()
                        } else {
                            current.sessionEndMs
                        }
                    )
                }
                refreshMetrics()
            }
        })
    }

    fun refreshModelState() {
        val asrReady = ModelManager.isAsrInstalled(appContext.filesDir)
        val asrMb = ModelManager.asrSizeMb(appContext.filesDir)
        val llmReady = ModelManager.isLlmInstalled(appContext.filesDir)
        val llmMb = ModelManager.llmSizeMb(appContext.filesDir)
        val ttsReady = ModelManager.isTtsInstalled(appContext.filesDir)
        val ttsMb = ModelManager.ttsSizeMb(appContext.filesDir)

        _uiState.update { state ->
            val nextModelState = if (asrReady) {
                ModelInstallState.Installed(asrMb)
            } else if (state.isDownloading) {
                state.modelState
            } else {
                ModelInstallState.NotInstalled
            }

            val nextLlmState = if (llmReady) {
                ComponentModelState.Installed(llmMb)
            } else if (
                state.llmModelState is ComponentModelState.Downloading ||
                state.llmModelState is ComponentModelState.Extracting
            ) {
                state.llmModelState
            } else {
                ComponentModelState.NotInstalled
            }

            val nextTtsState = if (ttsReady) {
                ComponentModelState.Installed(ttsMb)
            } else if (
                state.ttsModelState is ComponentModelState.Downloading ||
                state.ttsModelState is ComponentModelState.Extracting
            ) {
                state.ttsModelState
            } else {
                ComponentModelState.NotInstalled
            }

            val allReady = asrReady && llmReady && ttsReady
            state.copy(
                modelState = nextModelState,
                modelSizeMb = asrMb,
                llmModelState = nextLlmState,
                ttsModelState = nextTtsState,
                statusMessage = if (asrReady) {
                    "Moonshine Base ES instalado. Reconocimiento local disponible sin Internet."
                } else {
                    "Modelo español todavía no instalado. Descárgalo con conexión Wi-Fi. Después funcionará offline."
                },
                callStatusMessage = if (state.callStatusMessage.isNotBlank() && state.isCallActive) {
                    state.callStatusMessage
                } else if (allReady) {
                    "ASR, LLM y voz TTS listos. Conversación 100 % offline preparada."
                } else {
                    "Descarga los modelos locales pendientes para conversar sin conexión."
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

    fun downloadLlmModel() {
        val cur = _uiState.value.llmModelState
        if (cur is ComponentModelState.Downloading || cur is ComponentModelState.Installed) return

        _uiState.update {
            it.copy(
                llmModelState = ComponentModelState.Downloading(0, 0L, 1065L),
                callStatusMessage = "Descargando Qwen2.5-1.5B-Instruct (Q4_K_M, ~1.04 GB)…"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ModelManager.downloadAndInstallLlm(appContext.filesDir) { percent, downMb, totalMb ->
                    _uiState.update {
                        it.copy(
                            llmModelState = ComponentModelState.Downloading(
                                progressPercent = percent,
                                downloadedMb = downMb,
                                totalMb = totalMb
                            )
                        )
                    }
                }
                refreshModelState()
                refreshMetrics()
            } catch (e: Exception) {
                val msg = e.message ?: "error desconocido"
                _uiState.update {
                    it.copy(
                        llmModelState = ComponentModelState.Error(msg),
                        callStatusMessage = "No se pudo descargar el LLM: $msg"
                    )
                }
            }
        }
    }

    fun downloadTtsModel() {
        val cur = _uiState.value.ttsModelState
        if (
            cur is ComponentModelState.Downloading ||
            cur is ComponentModelState.Extracting ||
            cur is ComponentModelState.Installed
        ) {
            return
        }

        _uiState.update {
            it.copy(
                ttsModelState = ComponentModelState.Downloading(0, 0L, 24L),
                callStatusMessage = "Descargando voz Piper Sharvard ES (~23.5 MB)…"
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                ModelManager.downloadAndInstallTts(
                    filesDir = appContext.filesDir,
                    cacheDir = appContext.cacheDir,
                    onProgress = { percent, downMb, totalMb ->
                        _uiState.update {
                            it.copy(
                                ttsModelState = ComponentModelState.Downloading(
                                    progressPercent = percent,
                                    downloadedMb = downMb,
                                    totalMb = totalMb
                                )
                            )
                        }
                    },
                    onExtracting = {
                        _uiState.update {
                            it.copy(
                                ttsModelState = ComponentModelState.Extracting,
                                callStatusMessage = "Instalando voces españolas y datos fonéticos…"
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
                        ttsModelState = ComponentModelState.Error(msg),
                        callStatusMessage = "No se pudo instalar la voz TTS: $msg"
                    )
                }
            }
        }
    }

    fun startCall() {
        if (!_uiState.value.areAllConversationalModelsReady) {
            _uiState.update {
                it.copy(callStatusMessage = "Instala ASR, LLM y voz TTS antes de iniciar la llamada.")
            }
            return
        }
        if (engine.isRunning) {
            engine.stop()
        }
        if (conversationEngine.isCallActive()) return

        val begin = DeviceMetrics.snapshot(appContext, appContext.filesDir)
        _uiState.update {
            it.copy(
                isCallActive = true,
                callDurationSeconds = 0L,
                startSnapshot = begin,
                endSnapshot = null,
                sessionStartMs = SystemClock.elapsedRealtime(),
                sessionEndMs = 0L,
                peakPssMb = maxOf(it.peakPssMb, begin.appPssMb),
                minAvailMb = minOf(it.minAvailMb, begin.availRamMb)
            )
        }
        conversationEngine.startCall()
        startCallTimer()
        startMetricsTicker()
    }

    fun endCall() {
        if (!conversationEngine.isCallActive() && _uiState.value.conversationState == ConversationState.IDLE) {
            return
        }
        conversationEngine.endCall(unloadModels = false)
    }

    fun toggleMute() {
        val nextMuted = !conversationEngine.isMuted()
        conversationEngine.setMuted(nextMuted)
        _uiState.update { it.copy(isMuted = nextMuted) }
    }

    fun interruptCall() {
        conversationEngine.interruptSpeaking()
    }

    fun selectVoice(voiceId: String) {
        conversationEngine.selectVoice(voiceId)
        _uiState.update { it.copy(selectedVoiceId = voiceId) }
    }

    fun releaseLoadedModels() {
        if (conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = true)
        } else {
            conversationEngine.releaseLoadedModels()
        }
        _uiState.update {
            it.copy(
                callStatusMessage = "Memoria RAM liberada. Los modelos permanecen instalados en disco."
            )
        }
    }

    fun resetCallSession() {
        conversationEngine.resetSession()
        _uiState.update {
            it.copy(
                conversationTurns = emptyList(),
                streamingAssistantText = "",
                callTelemetry = CallTelemetry(
                    asrLoadMs = it.callTelemetry.asrLoadMs,
                    llmLoadMs = it.callTelemetry.llmLoadMs,
                    ttsLoadMs = it.callTelemetry.ttsLoadMs
                )
            )
        }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                statusMessage = "Permiso de micrófono denegado.",
                callStatusMessage = "Permiso de micrófono requerido para hablar con ORION."
            )
        }
    }

    fun startListening() {
        if (!MoonshineModelManager.modelFilesPresent(appContext.filesDir)) return
        if (_uiState.value.isBusy) return
        if (conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = false)
        }

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
            val runningNow = engine.isRunning || conversationEngine.isCallActive()
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
            val runningNow = engine.isRunning || conversationEngine.isCallActive()
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
        val llmMb = ModelManager.llmSizeMb(appContext.filesDir)
        val ttsMb = ModelManager.ttsSizeMb(appContext.filesDir)
        val callTel = state.callTelemetry
        return buildString {
            appendLine("ORION Voice Lab 0.4 — informe de medición")
            appendLine(DeviceMetrics.deviceSummary())
            appendLine("Ahora:")
            appendLine(DeviceMetrics.describe(now))
            appendLine("Modelos en disco: ASR ${state.modelSizeMb} MB · LLM $llmMb MB · TTS $ttsMb MB")
            appendLine(
                if (state.modelLoadMs >= 0) {
                    "Carga ASR + VAD (Lab): ${state.modelLoadMs} ms"
                } else {
                    "Carga ASR + VAD (Lab): sin medir"
                }
            )
            if (callTel.asrLoadMs >= 0 || callTel.llmLoadMs >= 0 || callTel.ttsLoadMs >= 0) {
                appendLine(
                    "Carga llamada — ASR: ${callTel.asrLoadMs} ms · LLM: ${callTel.llmLoadMs} ms · TTS: ${callTel.ttsLoadMs} ms"
                )
            }
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
            if (callTel.llmTurnsCount > 0) {
                appendLine(
                    "Turnos conversacionales: ${callTel.llmTurnsCount} · LLM media ${callTel.avgLlmMs} ms · " +
                        "último 1er token ${callTel.lastLlmFirstTokenMs} ms · TTS ${callTel.lastTtsSynthMs} ms · " +
                        "voz→respuesta ${callTel.lastEndToSpeechMs} ms"
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
                appendLine("Sesión activa: $seconds s${if (end == null) " (en curso)" else ""}")
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
            while (isActive && (engine.isRunning || conversationEngine.isCallActive())) {
                delay(METRICS_TICK_MS)
                if (engine.isRunning || conversationEngine.isCallActive()) {
                    refreshMetrics()
                }
            }
        }
    }

    private fun stopMetricsTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (isActive && conversationEngine.isCallActive()) {
                delay(CALL_TIMER_TICK_MS)
                if (conversationEngine.isCallActive()) {
                    _uiState.update { it.copy(callDurationSeconds = it.callDurationSeconds + 1L) }
                }
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
    }

    override fun onCleared() {
        stopCallTimer()
        stopMetricsTicker()
        engine.destroy()
        conversationEngine.shutdown()
        super.onCleared()
    }
}

