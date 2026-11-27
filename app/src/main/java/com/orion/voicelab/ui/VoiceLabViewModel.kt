package com.orion.voicelab.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orion.voicelab.asr.MoonshineSpeechRecognitionEngine
import com.orion.voicelab.audio.AudioRoutingController
import com.orion.voicelab.conversation.ConversationEngine
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.AudioHardwareStatus
import com.orion.voicelab.data.CallTelemetry
import com.orion.voicelab.data.ComponentModelState
import com.orion.voicelab.data.ContactProfile
import com.orion.voicelab.data.ConversationState
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.ListeningState
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.MemoryItem
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.TranscriptEntry
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import com.orion.voicelab.llm.LlamaCppLanguageModel
import com.orion.voicelab.memory.ContextPromptBuilder
import com.orion.voicelab.memory.MemoryCandidateExtractor
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.persistence.ContactRepository
import com.orion.voicelab.persistence.ConversationRepository
import com.orion.voicelab.persistence.IdentityRepository
import com.orion.voicelab.persistence.MemoryRepository
import com.orion.voicelab.persistence.OrionDatabase
import com.orion.voicelab.tts.SherpaOnnxSpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceLabViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val METRICS_TICK_MS = 15_000L
        private const val CALL_TIMER_TICK_MS = 1_000L
        private const val MAX_RECENT_ERRORS = 8
        private const val PREFS_NAME = "orion_voice_prefs"
        private const val KEY_INITIAL_DOWNLOADS_DONE = "initial_models_auto_download_done"
    }

    private val appContext = application.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val database = OrionDatabase.getInstance(appContext)
    private val conversationRepository = ConversationRepository(database.conversationDao())
    private val memoryRepository = MemoryRepository(database.memoryDao())
    private val identityRepository = IdentityRepository(database.identityDao())
    private val contactRepository = ContactRepository(database.contactDao())
    private val persistenceMutex = Mutex()
    private val asrDownloadMutex = Mutex()
    private val ttsDownloadMutex = Mutex()
    private val llmDownloadMutex = Mutex()

    @Volatile
    private var latestIdentity: AssistantIdentity = AssistantIdentity()

    @Volatile
    private var latestMemories: List<MemoryItem> = emptyList()

    @Volatile
    private var latestConversationSummary: String = ""

    @Volatile
    private var activeConversationIdSnapshot: Long = 0L

    @Volatile
    private var latestSelectedCoworker: ContactProfile? = null

    @Volatile
    private var userInteractedWithConversations = false

    private val engine = OrionVoiceEngine()
    private val audioRoutingController = AudioRoutingController(appContext)

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
        speechSynthesizer = speechSynthesizer,
        audioRoutingController = audioRoutingController,
        turnContextResolver = { userText, recentHistory ->
            ContextPromptBuilder.buildTurnContext(
                identity = latestIdentity,
                allMemories = latestMemories,
                conversationSummary = latestConversationSummary,
                recentHistory = recentHistory,
                currentQuery = userText,
                activeCoworker = latestSelectedCoworker
            )
        }
    )

    private var tickerJob: Job? = null
    private var callTimerJob: Job? = null
    private var autoDownloadJob: Job? = null
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
        observePersistenceFlows()
        initializePersistence()
        refreshModelState()
        refreshMetrics()
        ensureInitialModelsDownloaded()
    }

    private fun observePersistenceFlows() {
        viewModelScope.launch(Dispatchers.IO) {
            conversationRepository.observeConversations().collect { list ->
                val active = list.firstOrNull { it.id == activeConversationIdSnapshot }
                if (active != null) {
                    latestConversationSummary = active.summaryText
                }
                _uiState.update { current ->
                    current.copy(
                        conversations = list,
                        activeConversationTitle = active?.title ?: current.activeConversationTitle
                    )
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.observeMemories().collect { items ->
                latestMemories = items
                _uiState.update { it.copy(memories = items) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            identityRepository.observeIdentity().collect { identity ->
                latestIdentity = identity
                conversationEngine.updateSystemPrompt(
                    ContextPromptBuilder.buildSystemPrompt(
                        identity = identity,
                        conversationSummary = latestConversationSummary,
                        activeCoworker = latestSelectedCoworker
                    )
                )
                _uiState.update { it.copy(assistantIdentity = identity) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            contactRepository.observeCoworkers().collect { coworkers ->
                val currentSelected = _uiState.value.selectedContactId
                val updatedActiveCoworker = if (currentSelected == ContactProfile.PRIMARY_CONTACT_ID) {
                    null
                } else {
                    coworkers.firstOrNull { it.id == currentSelected }
                }
                val fallbackId = if (currentSelected != ContactProfile.PRIMARY_CONTACT_ID && updatedActiveCoworker == null) {
                    ContactProfile.PRIMARY_CONTACT_ID
                } else {
                    currentSelected
                }
                latestSelectedCoworker = updatedActiveCoworker
                _uiState.update { current ->
                    current.copy(
                        contacts = coworkers,
                        selectedContactId = fallbackId
                    )
                }
            }
        }
    }

    private fun initializePersistence() {
        viewModelScope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                val identity = identityRepository.ensureInitialized()
                latestIdentity = identity
                if (userInteractedWithConversations) return@withLock

                val activeId = conversationRepository.ensureLatestOrCreateConversation()
                val summary = conversationRepository.getConversationSummary(activeId)
                val savedTurns = conversationRepository.loadConversationTurns(activeId)

                activeConversationIdSnapshot = activeId
                latestConversationSummary = summary?.summaryText.orEmpty()
                conversationEngine.restoreConversation(
                    turns = savedTurns,
                    systemPrompt = ContextPromptBuilder.buildSystemPrompt(
                        identity = identity,
                        conversationSummary = latestConversationSummary
                    )
                )
                _uiState.update {
                    it.copy(
                        activeConversationId = activeId,
                        activeConversationTitle = summary?.title ?: ConversationRepository.DEFAULT_CONVERSATION_TITLE,
                        assistantIdentity = identity
                    )
                }
            }
        }
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

            override fun onUserTurnCommitted(turn: ConversationTurn) {
                viewModelScope.launch(Dispatchers.IO) {
                    var targetConvId = 0L
                    persistenceMutex.withLock {
                        val convId = if (activeConversationIdSnapshot > 0L) {
                            activeConversationIdSnapshot
                        } else {
                            conversationRepository.ensureLatestOrCreateConversation().also {
                                activeConversationIdSnapshot = it
                            }
                        }
                        targetConvId = convId
                        conversationRepository.appendUserMessage(
                            conversationId = convId,
                            text = turn.text,
                            audioMs = turn.audioMs,
                            decodeMs = turn.decodeMs
                        )
                        val summary = conversationRepository.getConversationSummary(convId)
                        latestConversationSummary = summary?.summaryText.orEmpty()
                        _uiState.update {
                            it.copy(
                                activeConversationId = convId,
                                activeConversationTitle = summary?.title ?: it.activeConversationTitle
                            )
                        }
                    }

                    if (latestIdentity.memoryEnabled) {
                        val currentSuggestions = _uiState.value.pendingMemorySuggestions
                        val detected = MemoryCandidateExtractor.extractCandidates(
                            userUtterance = turn.text,
                            existingMemories = latestMemories,
                            pendingSuggestions = currentSuggestions,
                            sourceConversationId = targetConvId
                        )
                        if (detected.isNotEmpty()) {
                            _uiState.update { current ->
                                current.copy(
                                    pendingMemorySuggestions = (current.pendingMemorySuggestions + detected)
                                        .distinctBy { it.id }
                                        .take(6)
                                )
                            }
                        }
                    }
                }
            }

            override fun onAssistantTurnCommitted(turn: ConversationTurn) {
                viewModelScope.launch(Dispatchers.IO) {
                    persistenceMutex.withLock {
                        val convId = if (activeConversationIdSnapshot > 0L) {
                            activeConversationIdSnapshot
                        } else {
                            conversationRepository.ensureLatestOrCreateConversation().also {
                                activeConversationIdSnapshot = it
                            }
                        }
                        val latency = turn.latency ?: TurnLatencyMetrics(
                            asrMs = 0L,
                            llmFirstTokenMs = 0L,
                            llmTotalMs = 0L,
                            llmTokensGenerated = 0,
                            llmTokensPerSec = 0.0,
                            ttsSynthMs = 0L,
                            synthesizedAudioMs = 0L,
                            endToSpeechStartMs = 0L,
                            responseDurationMs = 0L
                        )
                        conversationRepository.appendAssistantMessage(
                            conversationId = convId,
                            text = turn.text,
                            latency = latency
                        )
                        val summary = conversationRepository.getConversationSummary(convId)
                        latestConversationSummary = summary?.summaryText.orEmpty()
                    }
                }
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

            override fun onModelLoadStatesChanged(
                asrLoaded: Boolean,
                llmLoaded: Boolean,
                ttsLoaded: Boolean
            ) {
                _uiState.update {
                    it.copy(
                        asrLoaded = asrLoaded,
                        llmLoaded = llmLoaded,
                        ttsLoaded = ttsLoaded,
                        areLoadedModelsInRam = asrLoaded || llmLoaded || ttsLoaded
                    )
                }
            }

            override fun onAudioHardwareChanged(status: AudioHardwareStatus) {
                _uiState.update { it.copy(audioHardwareStatus = status) }
            }

            override fun onErrorRecorded(message: String) {
                recordError(message)
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

    private fun ensureInitialModelsDownloaded() {
        val asrReady = ModelManager.isAsrInstalled(appContext.filesDir)
        val ttsReady = ModelManager.isTtsInstalled(appContext.filesDir)
        val llmReady = ModelManager.isLlmInstalled(appContext.filesDir)
        if (asrReady && ttsReady && llmReady) {
            prefs.edit().putBoolean(KEY_INITIAL_DOWNLOADS_DONE, true).apply()
            return
        }
        if (prefs.getBoolean(KEY_INITIAL_DOWNLOADS_DONE, false)) {
            return
        }
        startInitialAutoDownloads()
    }

    fun retryInitialDownloads() {
        startInitialAutoDownloads()
    }

    fun pauseInitialDownloads() {
        autoDownloadJob?.cancel()
        autoDownloadJob = null
        _uiState.update {
            it.copy(
                isAutoDownloadingInitialModels = false,
                callStatusMessage = "Descarga automática pausada. Pulsa «Reanudar» cuando quieras continuar."
            )
        }
    }

    private fun startInitialAutoDownloads() {
        if (autoDownloadJob?.isActive == true) return
        autoDownloadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isAutoDownloadingInitialModels = true,
                    autoDownloadError = null
                )
            }
            try {
                // Orden: voz TTS (~23.5 MB) -> oído ASR (~125 MB) -> cerebro LLM (~1.04 GB)
                installTtsSuspend(throwOnError = true)
                installAsrSuspend(throwOnError = true)
                installLlmSuspend(throwOnError = true)

                val allReady = ModelManager.isAsrInstalled(appContext.filesDir) &&
                    ModelManager.isTtsInstalled(appContext.filesDir) &&
                    ModelManager.isLlmInstalled(appContext.filesDir)
                if (allReady) {
                    prefs.edit().putBoolean(KEY_INITIAL_DOWNLOADS_DONE, true).apply()
                }
                _uiState.update {
                    it.copy(
                        isAutoDownloadingInitialModels = false,
                        autoDownloadError = null
                    )
                }
            } catch (e: CancellationException) {
                _uiState.update { it.copy(isAutoDownloadingInitialModels = false) }
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "error de red al descargar los modelos iniciales"
                _uiState.update {
                    it.copy(
                        isAutoDownloadingInitialModels = false,
                        autoDownloadError = msg
                    )
                }
            }
        }
    }

    fun downloadModel() {
        if (_uiState.value.isDownloading || _uiState.value.isModelReady) return
        viewModelScope.launch(Dispatchers.IO) {
            installAsrSuspend(throwOnError = false)
        }
    }

    private suspend fun installAsrSuspend(throwOnError: Boolean) {
        asrDownloadMutex.withLock {
            if (ModelManager.isAsrInstalled(appContext.filesDir)) {
                refreshModelState()
                return
            }
            _uiState.update {
                it.copy(
                    modelState = ModelInstallState.Downloading(0),
                    statusMessage = "Descargando modelo oficial Moonshine Base ES…"
                )
            }
            try {
                MoonshineModelManager.downloadAndInstall(
                    filesDir = appContext.filesDir,
                    cacheDir = appContext.cacheDir,
                    shouldCancel = { !viewModelScope.isActive },
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
            } catch (e: CancellationException) {
                refreshModelState()
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "error desconocido"
                recordError("ASR descarga: $msg")
                _uiState.update {
                    it.copy(
                        modelState = ModelInstallState.Error(msg),
                        statusMessage = "No se pudo instalar el modelo: $msg"
                    )
                }
                if (throwOnError) throw e
            }
        }
    }

    fun downloadLlmModel() {
        val cur = _uiState.value.llmModelState
        if (cur is ComponentModelState.Downloading || cur is ComponentModelState.Installed) return
        viewModelScope.launch(Dispatchers.IO) {
            installLlmSuspend(throwOnError = false)
        }
    }

    private suspend fun installLlmSuspend(throwOnError: Boolean) {
        llmDownloadMutex.withLock {
            if (ModelManager.isLlmInstalled(appContext.filesDir)) {
                refreshModelState()
                return
            }
            _uiState.update {
                it.copy(
                    llmModelState = ComponentModelState.Downloading(0, 0L, 1065L),
                    callStatusMessage = "Descargando Qwen2.5-1.5B-Instruct (Q4_K_M, ~1.04 GB)…"
                )
            }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = appContext.filesDir,
                    shouldCancel = { !viewModelScope.isActive }
                ) { percent, downMb, totalMb ->
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
            } catch (e: CancellationException) {
                refreshModelState()
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "error desconocido"
                recordError("LLM descarga: $msg")
                _uiState.update {
                    it.copy(
                        llmModelState = ComponentModelState.Error(msg),
                        callStatusMessage = "No se pudo descargar el LLM: $msg"
                    )
                }
                if (throwOnError) throw e
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
        viewModelScope.launch(Dispatchers.IO) {
            installTtsSuspend(throwOnError = false)
        }
    }

    private suspend fun installTtsSuspend(throwOnError: Boolean) {
        ttsDownloadMutex.withLock {
            if (ModelManager.isTtsInstalled(appContext.filesDir)) {
                refreshModelState()
                return
            }
            _uiState.update {
                it.copy(
                    ttsModelState = ComponentModelState.Downloading(0, 0L, 24L),
                    callStatusMessage = "Descargando voz Piper Sharvard ES (~23.5 MB)…"
                )
            }
            try {
                ModelManager.downloadAndInstallTts(
                    filesDir = appContext.filesDir,
                    cacheDir = appContext.cacheDir,
                    shouldCancel = { !viewModelScope.isActive },
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
            } catch (e: CancellationException) {
                refreshModelState()
                throw e
            } catch (e: Exception) {
                val msg = e.message ?: "error desconocido"
                recordError("TTS descarga: $msg")
                _uiState.update {
                    it.copy(
                        ttsModelState = ComponentModelState.Error(msg),
                        callStatusMessage = "No se pudo instalar la voz TTS: $msg"
                    )
                }
                if (throwOnError) throw e
            }
        }
    }

    fun openContactCallScreen(contactId: Long) {
        applySelectedContact(contactId)
        _uiState.update { it.copy(isInCallScreen = true) }
    }

    fun callContact(contactId: Long) {
        val switchingWhileInCall = conversationEngine.isCallActive() &&
            _uiState.value.selectedContactId != contactId
        if (switchingWhileInCall) {
            conversationEngine.endCall(unloadModels = false)
        }
        applySelectedContact(contactId)
        _uiState.update { it.copy(isInCallScreen = true) }
        if (!switchingWhileInCall && _uiState.value.areAllConversationalModelsReady) {
            startCall()
        }
    }

    fun returnToAgenda() {
        _uiState.update { it.copy(isInCallScreen = false) }
    }

    fun openActiveCallScreen() {
        _uiState.update { it.copy(isInCallScreen = true) }
    }

    fun renamePrimaryAssistant(newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = identityRepository.getIdentity()
            identityRepository.saveIdentity(current.copy(assistantName = clean))
        }
    }

    fun addCoworker(
        name: String,
        roleTitle: String,
        specialtyPrompt: String,
        voiceId: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            contactRepository.addCoworker(
                name = cleanName,
                roleTitle = roleTitle,
                specialtyPrompt = specialtyPrompt,
                voiceId = voiceId
            )
        }
    }

    fun updateCoworker(
        id: Long,
        name: String,
        roleTitle: String,
        specialtyPrompt: String,
        voiceId: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            contactRepository.updateCoworker(
                id = id,
                name = cleanName,
                roleTitle = roleTitle,
                specialtyPrompt = specialtyPrompt,
                voiceId = voiceId
            )
            if (_uiState.value.selectedContactId == id) {
                val updated = ContactProfile(
                    id = id,
                    name = cleanName,
                    roleTitle = roleTitle.trim().ifBlank { ContactRepository.DEFAULT_ROLE },
                    specialtyPrompt = specialtyPrompt.trim().ifBlank { ContactRepository.DEFAULT_SPECIALTY_PROMPT },
                    voiceId = voiceId
                )
                latestSelectedCoworker = updated
                conversationEngine.selectVoice(voiceId)
                conversationEngine.updateSystemPrompt(
                    ContextPromptBuilder.buildSystemPrompt(
                        identity = latestIdentity,
                        conversationSummary = latestConversationSummary,
                        activeCoworker = updated
                    )
                )
            }
        }
    }

    fun deleteCoworker(id: Long) {
        if (id == _uiState.value.selectedContactId && conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            contactRepository.deleteCoworker(id)
            if (_uiState.value.selectedContactId == id) {
                applySelectedContact(ContactProfile.PRIMARY_CONTACT_ID)
            }
        }
    }

    private fun applySelectedContact(contactId: Long) {
        val targetCoworker = if (contactId == ContactProfile.PRIMARY_CONTACT_ID) {
            null
        } else {
            _uiState.value.contacts.firstOrNull { it.id == contactId }
        }
        val effectiveId = targetCoworker?.id ?: ContactProfile.PRIMARY_CONTACT_ID
        latestSelectedCoworker = targetCoworker
        if (targetCoworker != null) {
            conversationEngine.selectVoice(targetCoworker.voiceId)
        }
        conversationEngine.updateSystemPrompt(
            ContextPromptBuilder.buildSystemPrompt(
                identity = latestIdentity,
                conversationSummary = latestConversationSummary,
                activeCoworker = targetCoworker
            )
        )
        _uiState.update { current ->
            current.copy(
                selectedContactId = effectiveId,
                selectedVoiceId = targetCoworker?.voiceId ?: current.selectedVoiceId
            )
        }
    }

    fun startCall() {
        if (!_uiState.value.areAllConversationalModelsReady) {
            _uiState.update {
                it.copy(
                    isInCallScreen = true,
                    callStatusMessage = "Los modelos offline se están preparando. Espera a que finalice la instalación."
                )
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
                isInCallScreen = true,
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
        createNewConversation()
    }

    fun createNewConversation() {
        userInteractedWithConversations = true
        if (conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                val newId = conversationRepository.createConversation()
                activeConversationIdSnapshot = newId
                latestConversationSummary = ""
                conversationEngine.restoreConversation(
                    turns = emptyList(),
                    systemPrompt = ContextPromptBuilder.buildSystemPrompt(
                        identity = latestIdentity,
                        conversationSummary = "",
                        activeCoworker = latestSelectedCoworker
                    )
                )
                _uiState.update {
                    it.copy(
                        activeConversationId = newId,
                        activeConversationTitle = ConversationRepository.DEFAULT_CONVERSATION_TITLE,
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
        }
    }

    fun selectConversation(conversationId: Long) {
        if (conversationId <= 0L) return
        userInteractedWithConversations = true
        if (conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                val summary = conversationRepository.getConversationSummary(conversationId) ?: return@withLock
                val turns = conversationRepository.loadConversationTurns(conversationId)
                activeConversationIdSnapshot = conversationId
                latestConversationSummary = summary.summaryText
                conversationEngine.restoreConversation(
                    turns = turns,
                    systemPrompt = ContextPromptBuilder.buildSystemPrompt(
                        identity = latestIdentity,
                        conversationSummary = summary.summaryText,
                        activeCoworker = latestSelectedCoworker
                    )
                )
                _uiState.update {
                    it.copy(
                        activeConversationId = conversationId,
                        activeConversationTitle = summary.title,
                        streamingAssistantText = ""
                    )
                }
            }
        }
    }

    fun deleteConversation(conversationId: Long) {
        if (conversationId <= 0L) return
        userInteractedWithConversations = true
        val deletingActive = conversationId == activeConversationIdSnapshot
        if (deletingActive && conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = false)
        }
        viewModelScope.launch(Dispatchers.IO) {
            persistenceMutex.withLock {
                conversationRepository.deleteConversation(conversationId)
                if (deletingActive) {
                    val nextId = conversationRepository.ensureLatestOrCreateConversation()
                    val summary = conversationRepository.getConversationSummary(nextId)
                    val turns = conversationRepository.loadConversationTurns(nextId)
                    activeConversationIdSnapshot = nextId
                    latestConversationSummary = summary?.summaryText.orEmpty()
                    conversationEngine.restoreConversation(
                        turns = turns,
                        systemPrompt = ContextPromptBuilder.buildSystemPrompt(
                            identity = latestIdentity,
                            conversationSummary = latestConversationSummary,
                            activeCoworker = latestSelectedCoworker
                        )
                    )
                    _uiState.update {
                        it.copy(
                            activeConversationId = nextId,
                            activeConversationTitle = summary?.title ?: ConversationRepository.DEFAULT_CONVERSATION_TITLE,
                            streamingAssistantText = ""
                        )
                    }
                }
            }
        }
    }

    fun acceptMemorySuggestion(suggestionId: String) {
        val suggestion = _uiState.value.pendingMemorySuggestions.firstOrNull { it.id == suggestionId } ?: return
        _uiState.update { current ->
            current.copy(
                pendingMemorySuggestions = current.pendingMemorySuggestions.filterNot { it.id == suggestionId }
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.addMemory(
                category = suggestion.category,
                content = suggestion.content,
                importance = suggestion.importance,
                sourceConversationId = suggestion.sourceConversationId
            )
        }
    }

    fun dismissMemorySuggestion(suggestionId: String) {
        _uiState.update { current ->
            current.copy(
                pendingMemorySuggestions = current.pendingMemorySuggestions.filterNot { it.id == suggestionId }
            )
        }
    }

    fun addManualMemory(category: MemoryCategory, content: String, importance: Int = 3) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.addMemory(
                category = category,
                content = clean,
                importance = importance,
                sourceConversationId = activeConversationIdSnapshot.takeIf { it > 0L }
            )
        }
    }

    fun updateMemory(id: Long, category: MemoryCategory, content: String, importance: Int) {
        val clean = content.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.updateMemory(
                id = id,
                category = category,
                content = clean,
                importance = importance
            )
        }
    }

    fun deleteMemory(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.deleteMemory(id)
        }
    }

    fun clearAllMemories() {
        _uiState.update { it.copy(pendingMemorySuggestions = emptyList()) }
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.clearAllMemories()
        }
    }

    fun setMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            identityRepository.setMemoryEnabled(enabled)
        }
        if (!enabled) {
            _uiState.update { it.copy(pendingMemorySuggestions = emptyList()) }
        }
    }

    fun saveAssistantIdentity(identity: AssistantIdentity) {
        viewModelScope.launch(Dispatchers.IO) {
            identityRepository.saveIdentity(identity)
        }
    }

    fun resetAssistantIdentity() {
        viewModelScope.launch(Dispatchers.IO) {
            identityRepository.resetToDefaults()
        }
    }

    fun onPermissionDenied() {
        recordError("Permiso RECORD_AUDIO denegado")
        _uiState.update {
            it.copy(
                statusMessage = "Permiso de micrófono denegado.",
                callStatusMessage = "Permiso de micrófono requerido para hablar con ORION."
            )
        }
    }

    fun onAppBackgrounded() {
        if (conversationEngine.isCallActive()) {
            endCall()
        }
        if (engine.isRunning) {
            stopListening()
        }
    }

    private fun recordError(message: String) {
        val clean = message.trim()
        if (clean.isEmpty()) return
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val formatted = "[$timestamp] $clean"
        _uiState.update { current ->
            val updated = (listOf(formatted) + current.recentErrors).take(MAX_RECENT_ERRORS)
            current.copy(recentErrors = updated)
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
                    recordError("Lab ASR: $message")
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
        val hw = state.audioHardwareStatus
        return buildString {
            appendLine("ORION Voice Lab 0.5.0 — informe de diagnóstico")
            appendLine(DeviceMetrics.deviceSummary())
            appendLine(
                "Memoria local: conversaciones=${state.conversations.size} (activa: ${state.activeConversationTitle}) · " +
                    "recuerdos=${state.memories.size} · memoria=${if (state.assistantIdentity.memoryEnabled) "activa" else "desactivada"} · " +
                    "identidad=${state.assistantIdentity.assistantName} (${state.assistantIdentity.formality.displayName})"
            )
            appendLine("Ahora:")
            appendLine(DeviceMetrics.describe(now))
            appendLine(
                "Modelos en disco: ASR ${state.modelSizeMb} MB (${if (state.isModelReady) "instalado" else "pendiente"}) · " +
                    "LLM $llmMb MB (${if (state.llmModelState is ComponentModelState.Installed) "instalado" else "pendiente"}) · " +
                    "TTS $ttsMb MB (${if (state.ttsModelState is ComponentModelState.Installed) "instalado" else "pendiente"})"
            )
            appendLine(
                "Modelos en RAM: ASR=${state.asrLoaded} · LLM=${state.llmLoaded} · TTS=${state.ttsLoaded}"
            )
            appendLine(
                "Audio HW: captura=${hw.isCapturing} (silencio=${hw.isMuted}) · fuente=${hw.audioSourceName} @ ${hw.sampleRateHz} Hz · " +
                    "AEC=${hw.aecActive}/${hw.aecAvailable} · NS=${hw.nsActive}/${hw.nsAvailable} · " +
                    "reproducción=${hw.isPlaying} · ruta=${hw.audioOutputRoute}"
            )
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
                    String.format(
                        Locale.getDefault(),
                        "Turnos llamada: %d · último ASR %d ms · LLM 1er token %d ms · %.1f tok/s · LLM total %d ms (media %d ms) · TTS %d ms (audio %d ms) · 1er audio %d ms · respuesta %d ms",
                        callTel.llmTurnsCount,
                        callTel.lastAsrMs,
                        callTel.lastLlmFirstTokenMs,
                        callTel.lastLlmTokensPerSec,
                        callTel.lastLlmTotalMs,
                        callTel.avgLlmMs,
                        callTel.lastTtsSynthMs,
                        callTel.lastSynthesizedAudioMs,
                        callTel.lastEndToSpeechMs,
                        callTel.lastResponseDurationMs
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
            if (state.recentErrors.isNotEmpty()) {
                appendLine("Últimos errores:")
                state.recentErrors.forEach { appendLine("  $it") }
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
        autoDownloadJob?.cancel()
        stopCallTimer()
        stopMetricsTicker()
        engine.destroy()
        conversationEngine.shutdown()
        speechRecognizer.shutdown()
        languageModel.shutdown()
        speechSynthesizer.shutdown()
        super.onCleared()
    }
}

