package com.orion.voicelab.conversation

import android.content.res.AssetManager
import android.os.SystemClock
import com.orion.voicelab.asr.SpeechRecognitionEngine
import com.orion.voicelab.audio.AudioPlaybackEngine
import com.orion.voicelab.audio.SileroVoiceActivityDetector
import com.orion.voicelab.audio.VoiceActivityDetector
import com.orion.voicelab.audio.VoiceInputEngine
import com.orion.voicelab.data.CallTelemetry
import com.orion.voicelab.data.ConversationState
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.llm.LocalLanguageModel
import com.orion.voicelab.tts.LocalSpeechSynthesizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class ConversationEngine(
    private val assets: AssetManager,
    private val speechRecognizer: SpeechRecognitionEngine,
    private val languageModel: LocalLanguageModel,
    private val speechSynthesizer: LocalSpeechSynthesizer,
    private val playbackEngine: AudioPlaybackEngine = AudioPlaybackEngine(),
    private val vadFactory: () -> VoiceActivityDetector = { SileroVoiceActivityDetector(assets) },
    private val sampleRate: Int = SileroVoiceActivityDetector.SAMPLE_RATE
) {
    companion object {
        private const val MIN_SEGMENT_SAMPLES = 4_000 // 250 ms at 16 kHz
        private const val BARGE_IN_CONSECUTIVE_WINDOWS = 10 // ~320 ms sustained speech
        private const val BARGE_IN_MIN_RMS = 0.04f
        private const val ECHO_COOLDOWN_MS = 240L
    }

    interface Callbacks {
        fun onStateChanged(state: ConversationState, statusText: String)
        fun onSpeechDetectedChanged(detected: Boolean)
        fun onAudioLevel(level: Float)
        fun onTurnsUpdated(turns: List<ConversationTurn>)
        fun onStreamingText(text: String)
        fun onTelemetryUpdated(telemetry: CallTelemetry)
        fun onModelsInRamChanged(loaded: Boolean)
        fun onCallEnded()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val inputEngine = VoiceInputEngine(
        sampleRate = sampleRate,
        windowSamples = SileroVoiceActivityDetector.VAD_WINDOW,
        useVoiceCommunicationSource = true
    )

    private val session = ConversationSession()
    private val callActive = AtomicBoolean(false)
    private val startingOrStopping = AtomicBoolean(false)
    private val turnGeneration = AtomicLong(0L)
    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    @Volatile
    private var vad: VoiceActivityDetector? = null
    @Volatile
    private var activeTurnJob: Job? = null
    @Volatile
    private var callbacks: Callbacks? = null
    @Volatile
    private var telemetry = CallTelemetry()
    @Volatile
    private var echoSuppressUntilMs = 0L
    @Volatile
    private var consecutiveBargeInWindows = 0

    fun attachCallbacks(listener: Callbacks?) {
        callbacks = listener
    }

    fun isCallActive(): Boolean = callActive.get()

    fun isMuted(): Boolean = inputEngine.isMuted

    fun setMuted(muted: Boolean) {
        inputEngine.setMuted(muted)
        if (muted) {
            callbacks?.onSpeechDetectedChanged(false)
            callbacks?.onAudioLevel(0f)
        }
    }

    fun selectVoice(voiceId: String) {
        speechSynthesizer.selectVoice(voiceId)
    }

    fun resetSession() {
        session.reset()
        callbacks?.onTurnsUpdated(emptyList())
        callbacks?.onStreamingText("")
    }

    fun startCall() {
        if (callActive.get() || !startingOrStopping.compareAndSet(false, true)) return

        scope.launch {
            try {
                updateState(
                    ConversationState.PROCESSING,
                    "Cargando ASR, LLM y voz TTS en memoria…"
                )

                val asrLoadMs = speechRecognizer.load()
                val ttsLoadMs = speechSynthesizer.load()
                val llmLoadMs = languageModel.load()
                val activeVad = vad ?: vadFactory().also { vad = it }
                activeVad.reset()

                telemetry = telemetry.copy(
                    asrLoadMs = if (asrLoadMs > 0) asrLoadMs else telemetry.asrLoadMs,
                    ttsLoadMs = if (ttsLoadMs > 0) ttsLoadMs else telemetry.ttsLoadMs,
                    llmLoadMs = if (llmLoadMs > 0) llmLoadMs else telemetry.llmLoadMs
                )
                callbacks?.onTelemetryUpdated(telemetry)
                callbacks?.onModelsInRamChanged(true)

                callActive.set(true)
                echoSuppressUntilMs = 0L
                consecutiveBargeInWindows = 0

                val started = inputEngine.start(object : VoiceInputEngine.Listener {
                    private var lastSpeechFlag = false

                    override fun onCaptureStarted() {
                        if (callActive.get()) {
                            updateState(
                                ConversationState.LISTENING,
                                "En llamada · Escuchando en español…"
                            )
                        }
                    }

                    override fun onAudioFrame(samples: FloatArray, rmsNormalized: Float) {
                        if (!callActive.get()) return
                        callbacks?.onAudioLevel(rmsNormalized)

                        val now = SystemClock.elapsedRealtime()
                        if (now < echoSuppressUntilMs) {
                            return
                        }

                        val currentVad = vad ?: return
                        currentVad.acceptWaveform(samples)
                        val speechNow = currentVad.isSpeechDetected()

                        if (speechNow != lastSpeechFlag) {
                            lastSpeechFlag = speechNow
                            callbacks?.onSpeechDetectedChanged(speechNow)
                        }

                        val currentState = _state.value
                        when (currentState) {
                            ConversationState.SPEAKING, ConversationState.PROCESSING -> {
                                if (!inputEngine.isMuted && speechNow && rmsNormalized >= BARGE_IN_MIN_RMS) {
                                    consecutiveBargeInWindows++
                                    if (consecutiveBargeInWindows >= BARGE_IN_CONSECUTIVE_WINDOWS) {
                                        consecutiveBargeInWindows = 0
                                        interruptInternal(fromVoiceBargeIn = true)
                                    }
                                } else {
                                    consecutiveBargeInWindows = 0
                                    if (currentState == ConversationState.SPEAKING) {
                                        // Discard speaker bleed segments while ORION speaks unless barge-in occurred
                                        currentVad.drainSegments(MIN_SEGMENT_SAMPLES)
                                    }
                                }
                            }

                            ConversationState.LISTENING, ConversationState.INTERRUPTED -> {
                                consecutiveBargeInWindows = 0
                                val segments = currentVad.drainSegments(MIN_SEGMENT_SAMPLES)
                                if (segments.isNotEmpty() && _state.value != ConversationState.PROCESSING) {
                                    // Process the latest spoken segment cleanly
                                    val segment = segments.last()
                                    val speechEndedMs = SystemClock.elapsedRealtime()
                                    startTurnPipeline(segment, speechEndedMs)
                                }
                            }

                            ConversationState.IDLE, ConversationState.ERROR -> {
                                currentVad.drainSegments(MIN_SEGMENT_SAMPLES)
                            }
                        }
                    }

                    override fun onError(message: String) {
                        callActive.set(false)
                        updateState(ConversationState.ERROR, "Error de audio: $message")
                        callbacks?.onCallEnded()
                    }

                    override fun onCaptureStopped() {
                        callbacks?.onSpeechDetectedChanged(false)
                        callbacks?.onAudioLevel(0f)
                    }
                })

                if (!started) {
                    callActive.set(false)
                    updateState(ConversationState.ERROR, "No se pudo iniciar la captura de micrófono.")
                    callbacks?.onCallEnded()
                }
            } catch (e: Exception) {
                callActive.set(false)
                updateState(
                    ConversationState.ERROR,
                    "No se pudo iniciar la llamada: ${e.message ?: "error desconocido"}"
                )
                callbacks?.onCallEnded()
            } finally {
                startingOrStopping.set(false)
            }
        }
    }

    fun interruptSpeaking() {
        if (!callActive.get()) return
        interruptInternal(fromVoiceBargeIn = false)
    }

    private fun interruptInternal(fromVoiceBargeIn: Boolean) {
        val currentState = _state.value
        if (currentState != ConversationState.SPEAKING && currentState != ConversationState.PROCESSING) {
            return
        }
        turnGeneration.incrementAndGet()
        activeTurnJob?.cancel()
        activeTurnJob = null

        playbackEngine.stopImmediately()
        speechSynthesizer.cancel()
        languageModel.cancelGeneration()

        updateState(ConversationState.INTERRUPTED, "Interrumpido · Escuchando tu voz…")
        callbacks?.onStreamingText("")
        if (!fromVoiceBargeIn) {
            vad?.reset()
        }
        if (callActive.get()) {
            updateState(ConversationState.LISTENING, "En llamada · Escuchando en español…")
        }
    }

    private fun startTurnPipeline(samples: FloatArray, speechEndedMs: Long) {
        val myGeneration = turnGeneration.incrementAndGet()
        activeTurnJob?.cancel()

        activeTurnJob = scope.launch {
            try {
                updateState(ConversationState.PROCESSING, "Transcribiendo tu voz…")
                val asrResult = speechRecognizer.transcribe(samples, sampleRate)
                if (!callActive.get() || turnGeneration.get() != myGeneration) return@launch

                val userText = asrResult.text.trim()
                if (userText.isBlank() || userText.none { it.isLetterOrDigit() }) {
                    updateState(ConversationState.LISTENING, "En llamada · Escuchando en español…")
                    return@launch
                }

                session.addUserMessage(
                    text = userText,
                    audioMs = asrResult.audioMs,
                    decodeMs = asrResult.decodeMs
                )
                callbacks?.onTurnsUpdated(session.turnsSnapshot())
                updateState(ConversationState.PROCESSING, "ORION está pensando…")

                val sentenceChannel = Channel<String>(Channel.UNLIMITED)
                val chunker = SpeechSentenceChunker()
                val streamedDisplay = StringBuilder()

                var firstAudioStartMs = -1L
                var totalTtsMs = 0L

                val ttsConsumer = async(Dispatchers.IO) {
                    var sessionBegun = false
                    for (sentence in sentenceChannel) {
                        if (!callActive.get() || turnGeneration.get() != myGeneration) break
                        val synthMs = speechSynthesizer.synthesize(sentence) { pcmChunk, sr ->
                            if (!callActive.get() || turnGeneration.get() != myGeneration) {
                                false
                            } else {
                                if (!sessionBegun) {
                                    sessionBegun = true
                                    playbackEngine.beginSession(sr)
                                }
                                if (firstAudioStartMs < 0L) {
                                    firstAudioStartMs = SystemClock.elapsedRealtime() - speechEndedMs
                                    updateState(ConversationState.SPEAKING, "ORION está hablando…")
                                }
                                playbackEngine.writeSamples(pcmChunk, sr)
                            }
                        }
                        totalTtsMs += synthMs
                    }
                    if (sessionBegun && callActive.get() && turnGeneration.get() == myGeneration) {
                        playbackEngine.finishSessionGracefully()
                    }
                }

                val llmResult = languageModel.generateReply(
                    systemPrompt = session.systemPrompt,
                    history = session.historySnapshot()
                ) { token ->
                    if (!callActive.get() || turnGeneration.get() != myGeneration) return@generateReply
                    streamedDisplay.append(token)
                    callbacks?.onStreamingText(streamedDisplay.toString())
                    val readySentences = chunker.appendToken(token)
                    for (s in readySentences) {
                        sentenceChannel.trySend(s)
                    }
                }

                if (!llmResult.cancelled && callActive.get() && turnGeneration.get() == myGeneration) {
                    val trailing = chunker.flush()
                    if (trailing != null) {
                        sentenceChannel.trySend(trailing)
                    } else if (streamedDisplay.isEmpty() && llmResult.text.isNotBlank()) {
                        sentenceChannel.trySend(llmResult.text)
                    }
                }
                sentenceChannel.close()
                ttsConsumer.await()

                if (!callActive.get() || turnGeneration.get() != myGeneration || llmResult.cancelled) {
                    return@launch
                }

                val replyText = llmResult.text.ifBlank { streamedDisplay.toString().trim() }
                if (replyText.isNotBlank()) {
                    val endToSpeech = if (firstAudioStartMs >= 0L) {
                        firstAudioStartMs
                    } else {
                        SystemClock.elapsedRealtime() - speechEndedMs
                    }
                    val turnMetrics = TurnLatencyMetrics(
                        asrMs = asrResult.decodeMs,
                        llmFirstTokenMs = llmResult.firstTokenMs,
                        llmTotalMs = llmResult.totalMs,
                        ttsSynthMs = totalTtsMs,
                        endToSpeechStartMs = endToSpeech
                    )
                    session.addAssistantMessage(replyText, turnMetrics)
                    telemetry = telemetry.withTurn(turnMetrics)
                    callbacks?.onTurnsUpdated(session.turnsSnapshot())
                    callbacks?.onTelemetryUpdated(telemetry)
                }

                callbacks?.onStreamingText("")
                // Prevent ORION's trailing speaker echo from re-triggering VAD
                echoSuppressUntilMs = SystemClock.elapsedRealtime() + ECHO_COOLDOWN_MS
                vad?.reset()
                delay(ECHO_COOLDOWN_MS)
                if (callActive.get() && turnGeneration.get() == myGeneration) {
                    updateState(ConversationState.LISTENING, "En llamada · Escuchando en español…")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (callActive.get() && turnGeneration.get() == myGeneration) {
                    callbacks?.onStreamingText("")
                    updateState(
                        ConversationState.ERROR,
                        "Error en turno de conversación: ${e.message ?: "error desconocido"}"
                    )
                    delay(1200L)
                    if (callActive.get()) {
                        vad?.reset()
                        updateState(ConversationState.LISTENING, "En llamada · Escuchando en español…")
                    }
                }
            }
        }
    }

    fun endCall(unloadModels: Boolean = false) {
        callActive.set(false)
        turnGeneration.incrementAndGet()
        activeTurnJob?.cancel()
        activeTurnJob = null

        inputEngine.stop()
        playbackEngine.stopImmediately()
        speechSynthesizer.cancel()
        languageModel.cancelGeneration()
        vad?.reset()

        if (unloadModels) {
            releaseLoadedModels()
        }

        callbacks?.onStreamingText("")
        callbacks?.onSpeechDetectedChanged(false)
        callbacks?.onAudioLevel(0f)
        updateState(ConversationState.IDLE, "Llamada finalizada. Modelos listos offline.")
        callbacks?.onCallEnded()
    }

    fun releaseLoadedModels() {
        vad?.release()
        vad = null
        speechRecognizer.release()
        speechSynthesizer.unload()
        languageModel.unload()
        playbackEngine.release()
        callbacks?.onModelsInRamChanged(false)
    }

    fun shutdown() {
        endCall(unloadModels = true)
        scope.cancel()
    }

    private fun updateState(newState: ConversationState, message: String) {
        _state.value = newState
        callbacks?.onStateChanged(newState, message)
    }
}
