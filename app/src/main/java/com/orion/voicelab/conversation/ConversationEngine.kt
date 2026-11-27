package com.orion.voicelab.conversation

import android.content.res.AssetManager
import android.os.SystemClock
import com.orion.voicelab.asr.SpeechRecognitionEngine
import com.orion.voicelab.audio.AudioCaptureCapabilities
import com.orion.voicelab.audio.AudioOutputSink
import com.orion.voicelab.audio.AudioPlaybackEngine
import com.orion.voicelab.audio.AudioRoutingController
import com.orion.voicelab.audio.SileroVoiceActivityDetector
import com.orion.voicelab.audio.VoiceActivityDetector
import com.orion.voicelab.audio.VoiceInputController
import com.orion.voicelab.audio.VoiceInputEngine
import com.orion.voicelab.data.AudioHardwareStatus
import com.orion.voicelab.data.CallTelemetry
import com.orion.voicelab.data.ConversationState
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.llm.LocalLanguageModel
import com.orion.voicelab.tts.LocalSpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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

class ConversationEngine internal constructor(
    private val speechRecognizer: SpeechRecognitionEngine,
    private val languageModel: LocalLanguageModel,
    private val speechSynthesizer: LocalSpeechSynthesizer,
    private val playbackEngine: AudioOutputSink = AudioPlaybackEngine(),
    private val audioRoutingController: AudioRoutingController? = null,
    private val vadFactory: () -> VoiceActivityDetector,
    private val sampleRate: Int = SileroVoiceActivityDetector.SAMPLE_RATE,
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    inputFactory: (() -> VoiceInputController)? = null
) {
    constructor(
        assets: AssetManager,
        speechRecognizer: SpeechRecognitionEngine,
        languageModel: LocalLanguageModel,
        speechSynthesizer: LocalSpeechSynthesizer,
        audioRoutingController: AudioRoutingController? = null,
        playbackEngine: AudioPlaybackEngine = AudioPlaybackEngine(),
        vadFactory: () -> VoiceActivityDetector = { SileroVoiceActivityDetector(assets) },
        sampleRate: Int = SileroVoiceActivityDetector.SAMPLE_RATE
    ) : this(
        speechRecognizer = speechRecognizer,
        languageModel = languageModel,
        speechSynthesizer = speechSynthesizer,
        playbackEngine = playbackEngine,
        audioRoutingController = audioRoutingController,
        vadFactory = vadFactory,
        sampleRate = sampleRate,
        clockMs = { SystemClock.elapsedRealtime() }
    )

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
        fun onModelLoadStatesChanged(asrLoaded: Boolean, llmLoaded: Boolean, ttsLoaded: Boolean) {}
        fun onAudioHardwareChanged(status: AudioHardwareStatus) {}
        fun onErrorRecorded(message: String) {}
        fun onCallEnded()
    }

    private val scope = CoroutineScope(SupervisorJob() + workerDispatcher)
    private val inputEngine: VoiceInputController = inputFactory?.invoke() ?: VoiceInputEngine(
        sampleRate = sampleRate,
        windowSamples = SileroVoiceActivityDetector.VAD_WINDOW,
        useVoiceCommunicationSource = true
    )

    private val session = ConversationSession()
    private val callActive = AtomicBoolean(false)
    private val callRequested = AtomicBoolean(false)
    private val turnInFlight = AtomicBoolean(false)
    private val callSessionEpoch = AtomicLong(0L)
    private val turnGeneration = AtomicLong(0L)
    private val _state = MutableStateFlow(ConversationState.IDLE)
    val state: StateFlow<ConversationState> = _state.asStateFlow()

    @Volatile
    private var vad: VoiceActivityDetector? = null
    @Volatile
    private var activeTurnJob: Job? = null
    @Volatile
    private var startJob: Job? = null
    @Volatile
    private var callbacks: Callbacks? = null
    @Volatile
    private var telemetry = CallTelemetry()
    @Volatile
    private var audioHardware = AudioHardwareStatus(sampleRateHz = sampleRate)
    @Volatile
    private var echoSuppressUntilMs = 0L
    @Volatile
    private var consecutiveBargeInWindows = 0

    init {
        playbackEngine.setPlaybackStateListener { playing ->
            updateAudioHardware { it.copy(isPlaying = playing) }
        }
    }

    fun attachCallbacks(listener: Callbacks?) {
        callbacks = listener
        listener?.let {
            notifyModelLoadStates()
            it.onAudioHardwareChanged(audioHardware)
        }
    }

    fun isCallActive(): Boolean = callActive.get() || callRequested.get()

    fun isMuted(): Boolean = inputEngine.isMuted

    fun setMuted(muted: Boolean) {
        inputEngine.setMuted(muted)
        updateAudioHardware { it.copy(isMuted = muted) }
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
        if (callActive.get() || !callRequested.compareAndSet(false, true)) return

        val sessionEpoch = callSessionEpoch.incrementAndGet()
        updateState(
            ConversationState.PROCESSING,
            "Cargando ASR, LLM y voz TTS en memoria…"
        )

        startJob = scope.launch {
            var routingStarted = false
            try {
                val asrLoadMs = speechRecognizer.load()
                notifyModelLoadStates()
                if (!isSessionValid(sessionEpoch)) return@launch

                val ttsLoadMs = speechSynthesizer.load()
                notifyModelLoadStates()
                if (!isSessionValid(sessionEpoch)) return@launch

                val llmLoadMs = languageModel.load()
                notifyModelLoadStates()
                if (!isSessionValid(sessionEpoch)) return@launch

                val activeVad = vad ?: vadFactory().also { vad = it }
                activeVad.reset()

                telemetry = telemetry.copy(
                    asrLoadMs = if (asrLoadMs > 0) asrLoadMs else telemetry.asrLoadMs,
                    ttsLoadMs = if (ttsLoadMs > 0) ttsLoadMs else telemetry.ttsLoadMs,
                    llmLoadMs = if (llmLoadMs > 0) llmLoadMs else telemetry.llmLoadMs
                )
                callbacks?.onTelemetryUpdated(telemetry)
                callbacks?.onModelsInRamChanged(true)

                val initialRoute = audioRoutingController?.startCallRouting { route ->
                    updateAudioHardware { it.copy(audioOutputRoute = route) }
                } ?: audioHardware.audioOutputRoute
                routingStarted = audioRoutingController != null
                updateAudioHardware { it.copy(audioOutputRoute = initialRoute) }

                if (!isSessionValid(sessionEpoch)) {
                    if (routingStarted) audioRoutingController?.stopCallRouting()
                    return@launch
                }

                echoSuppressUntilMs = 0L
                consecutiveBargeInWindows = 0
                callActive.set(true)

                val started = inputEngine.start(object : VoiceInputEngine.Listener {
                    private var lastSpeechFlag = false

                    override fun onCaptureStarted(capabilities: AudioCaptureCapabilities) {
                        if (!isSessionValid(sessionEpoch) || !callActive.get()) return
                        updateAudioHardware {
                            it.copy(
                                isCapturing = true,
                                isMuted = inputEngine.isMuted,
                                audioSourceName = capabilities.audioSourceName,
                                sampleRateHz = capabilities.sampleRateHz,
                                aecAvailable = capabilities.aecAvailable,
                                aecActive = capabilities.aecActive,
                                nsAvailable = capabilities.nsAvailable,
                                nsActive = capabilities.nsActive
                            )
                        }
                        updateState(
                            ConversationState.LISTENING,
                            "En llamada · Escuchando en español…"
                        )
                    }

                    override fun onAudioFrame(samples: FloatArray, rmsNormalized: Float) {
                        if (!callActive.get() || callSessionEpoch.get() != sessionEpoch) return
                        callbacks?.onAudioLevel(rmsNormalized)

                        val now = clockMs()
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
                                        currentVad.drainSegments(MIN_SEGMENT_SAMPLES)
                                    }
                                }
                            }

                            ConversationState.LISTENING, ConversationState.INTERRUPTED -> {
                                consecutiveBargeInWindows = 0
                                val segments = currentVad.drainSegments(MIN_SEGMENT_SAMPLES)
                                if (segments.isNotEmpty() &&
                                    _state.value != ConversationState.PROCESSING &&
                                    !turnInFlight.get()
                                ) {
                                    val segment = segments.last()
                                    val speechEndedMs = clockMs()
                                    startTurnPipeline(segment, speechEndedMs, sessionEpoch)
                                }
                            }

                            ConversationState.IDLE, ConversationState.ERROR -> {
                                currentVad.drainSegments(MIN_SEGMENT_SAMPLES)
                            }
                        }
                    }

                    override fun onError(message: String) {
                        if (callSessionEpoch.get() != sessionEpoch) return
                        callActive.set(false)
                        callRequested.set(false)
                        turnInFlight.set(false)
                        audioRoutingController?.stopCallRouting()
                        updateAudioHardware { it.copy(isCapturing = false, isPlaying = false) }
                        callbacks?.onErrorRecorded("Error de audio: $message")
                        updateState(ConversationState.ERROR, "Error de audio: $message")
                        callbacks?.onCallEnded()
                    }

                    override fun onCaptureStopped() {
                        updateAudioHardware { it.copy(isCapturing = false) }
                        callbacks?.onSpeechDetectedChanged(false)
                        callbacks?.onAudioLevel(0f)
                    }
                })

                if (!started) {
                    callActive.set(false)
                    callRequested.set(false)
                    if (routingStarted) audioRoutingController?.stopCallRouting()
                    val msg = "No se pudo iniciar la captura de micrófono."
                    callbacks?.onErrorRecorded(msg)
                    updateState(ConversationState.ERROR, msg)
                    callbacks?.onCallEnded()
                }
            } catch (e: CancellationException) {
                if (routingStarted) audioRoutingController?.stopCallRouting()
                callActive.set(false)
                callRequested.set(false)
                notifyModelLoadStates()
                throw e
            } catch (e: Exception) {
                if (routingStarted) audioRoutingController?.stopCallRouting()
                callActive.set(false)
                callRequested.set(false)
                turnInFlight.set(false)
                notifyModelLoadStates()
                val msg = "No se pudo iniciar la llamada: ${e.message ?: "error desconocido"}"
                if (callSessionEpoch.get() == sessionEpoch) {
                    callbacks?.onErrorRecorded(msg)
                    updateState(ConversationState.ERROR, msg)
                    callbacks?.onCallEnded()
                }
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
        turnInFlight.set(false)
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

    private fun startTurnPipeline(
        samples: FloatArray,
        speechEndedMs: Long,
        sessionEpoch: Long
    ) {
        if (!turnInFlight.compareAndSet(false, true)) return
        val myGeneration = turnGeneration.incrementAndGet()
        activeTurnJob?.cancel()
        updateState(ConversationState.PROCESSING, "Transcribiendo tu voz…")

        activeTurnJob = scope.launch {
            try {
                val asrResult = speechRecognizer.transcribe(samples, sampleRate)
                if (!callActive.get() ||
                    callSessionEpoch.get() != sessionEpoch ||
                    turnGeneration.get() != myGeneration
                ) {
                    return@launch
                }

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
                var firstAudioClockMs = -1L
                var totalTtsMs = 0L
                var totalSynthAudioMs = 0L

                val ttsConsumer = async(ioDispatcher) {
                    var sessionBegun = false
                    for (sentence in sentenceChannel) {
                        if (!callActive.get() ||
                            callSessionEpoch.get() != sessionEpoch ||
                            turnGeneration.get() != myGeneration
                        ) {
                            break
                        }
                        val synthResult = speechSynthesizer.synthesize(sentence) { pcmChunk, sr ->
                            if (!callActive.get() ||
                                callSessionEpoch.get() != sessionEpoch ||
                                turnGeneration.get() != myGeneration
                            ) {
                                false
                            } else {
                                if (!sessionBegun) {
                                    sessionBegun = true
                                    playbackEngine.beginSession(sr)
                                }
                                if (firstAudioStartMs < 0L) {
                                    val now = clockMs()
                                    firstAudioClockMs = now
                                    firstAudioStartMs = now - speechEndedMs
                                    updateState(ConversationState.SPEAKING, "ORION está hablando…")
                                }
                                playbackEngine.writeSamples(pcmChunk, sr)
                            }
                        }
                        totalTtsMs += synthResult.synthMs
                        totalSynthAudioMs += synthResult.audioDurationMs
                    }
                    if (sessionBegun &&
                        callActive.get() &&
                        callSessionEpoch.get() == sessionEpoch &&
                        turnGeneration.get() == myGeneration
                    ) {
                        playbackEngine.finishSessionGracefully()
                    }
                }

                val llmResult = languageModel.generateReply(
                    systemPrompt = session.systemPrompt,
                    history = session.historySnapshot()
                ) { token ->
                    if (!callActive.get() ||
                        callSessionEpoch.get() != sessionEpoch ||
                        turnGeneration.get() != myGeneration
                    ) {
                        return@generateReply
                    }
                    streamedDisplay.append(token)
                    callbacks?.onStreamingText(streamedDisplay.toString())
                    val readySentences = chunker.appendToken(token)
                    for (s in readySentences) {
                        sentenceChannel.trySend(s)
                    }
                }

                if (!llmResult.cancelled &&
                    callActive.get() &&
                    callSessionEpoch.get() == sessionEpoch &&
                    turnGeneration.get() == myGeneration
                ) {
                    val trailing = chunker.flush()
                    if (trailing != null) {
                        sentenceChannel.trySend(trailing)
                    } else if (streamedDisplay.isEmpty() && llmResult.text.isNotBlank()) {
                        sentenceChannel.trySend(llmResult.text)
                    }
                }
                sentenceChannel.close()
                ttsConsumer.await()

                if (!callActive.get() ||
                    callSessionEpoch.get() != sessionEpoch ||
                    turnGeneration.get() != myGeneration ||
                    llmResult.cancelled
                ) {
                    return@launch
                }

                val replyText = llmResult.text.ifBlank { streamedDisplay.toString().trim() }
                if (replyText.isNotBlank()) {
                    val now = clockMs()
                    val endToSpeech = if (firstAudioStartMs >= 0L) {
                        firstAudioStartMs
                    } else {
                        now - speechEndedMs
                    }
                    val responseDurationMs = if (firstAudioClockMs >= 0L) {
                        (now - firstAudioClockMs).coerceAtLeast(0L)
                    } else {
                        totalSynthAudioMs
                    }
                    val turnMetrics = TurnLatencyMetrics(
                        asrMs = asrResult.decodeMs,
                        llmFirstTokenMs = llmResult.firstTokenMs,
                        llmTotalMs = llmResult.totalMs,
                        llmTokensGenerated = llmResult.tokensGenerated,
                        llmTokensPerSec = llmResult.tokensPerSecond,
                        ttsSynthMs = totalTtsMs,
                        synthesizedAudioMs = totalSynthAudioMs,
                        endToSpeechStartMs = endToSpeech,
                        responseDurationMs = responseDurationMs
                    )
                    session.addAssistantMessage(replyText, turnMetrics)
                    telemetry = telemetry.withTurn(turnMetrics)
                    callbacks?.onTurnsUpdated(session.turnsSnapshot())
                    callbacks?.onTelemetryUpdated(telemetry)
                }

                callbacks?.onStreamingText("")
                echoSuppressUntilMs = clockMs() + ECHO_COOLDOWN_MS
                vad?.reset()
                delay(ECHO_COOLDOWN_MS)
                if (callActive.get() &&
                    callSessionEpoch.get() == sessionEpoch &&
                    turnGeneration.get() == myGeneration
                ) {
                    updateState(ConversationState.LISTENING, "En llamada · Escuchando en español…")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (callActive.get() &&
                    callSessionEpoch.get() == sessionEpoch &&
                    turnGeneration.get() == myGeneration
                ) {
                    callbacks?.onStreamingText("")
                    val msg = "Error en turno de conversación: ${e.message ?: "error desconocido"}"
                    callbacks?.onErrorRecorded(msg)
                    updateState(ConversationState.ERROR, msg)
                    delay(1200L)
                    if (callActive.get() && callSessionEpoch.get() == sessionEpoch) {
                        vad?.reset()
                        updateState(ConversationState.LISTENING, "En llamada · Escuchando en español…")
                    }
                }
            } finally {
                if (turnGeneration.get() == myGeneration) {
                    turnInFlight.set(false)
                }
            }
        }
    }

    fun endCall(unloadModels: Boolean = false) {
        callSessionEpoch.incrementAndGet()
        callRequested.set(false)
        callActive.set(false)
        turnGeneration.incrementAndGet()
        turnInFlight.set(false)
        startJob?.cancel()
        startJob = null
        activeTurnJob?.cancel()
        activeTurnJob = null

        inputEngine.stop()
        playbackEngine.stopImmediately()
        speechSynthesizer.cancel()
        languageModel.cancelGeneration()
        audioRoutingController?.stopCallRouting()
        vad?.reset()

        if (unloadModels) {
            releaseLoadedModels()
        }

        updateAudioHardware {
            it.copy(
                isCapturing = false,
                isPlaying = false,
                audioOutputRoute = audioRoutingController?.currentRouteLabel() ?: "Altavoz"
            )
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
        notifyModelLoadStates()
        callbacks?.onModelsInRamChanged(false)
    }

    fun shutdown() {
        endCall(unloadModels = true)
        scope.cancel()
    }

    private fun isSessionValid(sessionEpoch: Long): Boolean =
        callRequested.get() && callSessionEpoch.get() == sessionEpoch

    private fun notifyModelLoadStates() {
        val asr = speechRecognizer.isLoaded()
        val llm = languageModel.isLoaded()
        val tts = speechSynthesizer.isLoaded()
        callbacks?.onModelLoadStatesChanged(asrLoaded = asr, llmLoaded = llm, ttsLoaded = tts)
        callbacks?.onModelsInRamChanged(asr || llm || tts)
    }

    private fun updateAudioHardware(transform: (AudioHardwareStatus) -> AudioHardwareStatus) {
        val updated = transform(audioHardware)
        audioHardware = updated
        callbacks?.onAudioHardwareChanged(updated)
    }

    private fun updateState(newState: ConversationState, message: String) {
        _state.value = newState
        callbacks?.onStateChanged(newState, message)
    }
}
