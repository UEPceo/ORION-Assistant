package com.orion.voicelab.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orion.voicelab.asr.MoonshineSpeechRecognitionEngine
import com.orion.voicelab.audio.AudioRoutingController
import com.orion.voicelab.conversation.ConversationEngine
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.AudioHardwareStatus
import com.orion.voicelab.data.CallIntentAction
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
import com.orion.voicelab.data.PendingCallRequest
import com.orion.voicelab.data.PhoneCallRecord
import com.orion.voicelab.data.PhoneCallStatus
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.PhoneContactSource
import com.orion.voicelab.data.SystemTelephonyState
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
import com.orion.voicelab.phone.CallDispatchResult
import com.orion.voicelab.phone.CallIntentRouter
import com.orion.voicelab.phone.PhoneCallManager
import com.orion.voicelab.phone.PhoneContactMatcher
import com.orion.voicelab.phone.PhoneRepository
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
    private val phoneRepository = PhoneRepository(database.phoneDao(), appContext.contentResolver)
    private val phoneCallManager = PhoneCallManager(appContext)
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
    private var latestPhoneCallRecords: List<PhoneCallRecord> = emptyList()

    @Volatile
    private var latestFavoriteContacts: List<PhoneContactEntry> = emptyList()

    @Volatile
    private var pendingRequestSnapshot: PendingCallRequest? = null

    @Volatile
    private var activeTelephonyCallRecordId: Long? = null

    @Volatile
    private var activeTelephonyStartedAtMs: Long? = null

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
                activeCoworker = latestSelectedCoworker,
                recentPhoneCalls = latestPhoneCallRecords
            )
        },
        directTurnResponder = { userText ->
            handleVoiceCallCommand(userText)
        }
    )

    private var tickerJob: Job? = null
    private var callTimerJob: Job? = null
    private var autoDownloadJob: Job? = null
    private var contactSearchJob: Job? = null
    private var delayedVoiceConfirmJob: Job? = null
    private var nextTranscriptId = 1L
    private var nextPendingRequestSeq = 1L

    private val _uiState = MutableStateFlow(
        VoiceLabUiState(
            availableVoices = speechSynthesizer.availableVoices(),
            selectedVoiceId = speechSynthesizer.selectedVoice().id,
            isTelephonySupported = phoneCallManager.isTelephonySupported(),
            hasContactsPermission = phoneCallManager.hasContactsPermission(),
            hasCallPhonePermission = phoneCallManager.hasCallPhonePermission(),
            hasPhoneStatePermission = phoneCallManager.hasPhoneStatePermission()
        )
    )
    val uiState: StateFlow<VoiceLabUiState> = _uiState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    init {
        attachConversationCallbacks()
        observePersistenceFlows()
        initializePersistence()
        refreshPhonePermissions()
        startObservingTelephonyState()
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
        viewModelScope.launch(Dispatchers.IO) {
            phoneRepository.observeFavorites().collect { favorites ->
                latestFavoriteContacts = favorites
                _uiState.update { it.copy(favoritePhoneContacts = favorites) }
                refreshNativeContactSearch()
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            phoneRepository.observeCallRecords().collect { records ->
                latestPhoneCallRecords = records
                _uiState.update { it.copy(phoneCallRecords = records) }
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

    // =========================================================================
    // ORION 0.6 — Integración telefónica real (agenda, confirmación y llamadas)
    // =========================================================================

    fun refreshPhonePermissions() {
        val contactsOk = phoneCallManager.hasContactsPermission()
        val callOk = phoneCallManager.hasCallPhonePermission()
        val stateOk = phoneCallManager.hasPhoneStatePermission()
        val supported = phoneCallManager.isTelephonySupported()

        _uiState.update { current ->
            current.copy(
                hasContactsPermission = contactsOk,
                hasCallPhonePermission = callOk,
                hasPhoneStatePermission = stateOk,
                isTelephonySupported = supported
            )
        }
        if (contactsOk) {
            refreshNativeContactSearch()
        }
        if (stateOk) {
            startObservingTelephonyState()
        }
    }

    private fun startObservingTelephonyState() {
        phoneCallManager.startObservingCallState { newState ->
            _uiState.update { it.copy(systemTelephonyState = newState) }
            handleTelephonyTransition(newState)
        }
    }

    private fun handleTelephonyTransition(newState: SystemTelephonyState) {
        val trackedId = activeTelephonyCallRecordId ?: return
        val now = System.currentTimeMillis()
        when (newState) {
            SystemTelephonyState.OFFHOOK -> {
                if (activeTelephonyStartedAtMs == null) {
                    activeTelephonyStartedAtMs = now
                    viewModelScope.launch(Dispatchers.IO) {
                        phoneRepository.updateCallStatusAndTiming(
                            id = trackedId,
                            status = PhoneCallStatus.IN_PROGRESS,
                            startedAtMs = now,
                            endedAtMs = null,
                            durationSeconds = 0L
                        )
                    }
                }
            }

            SystemTelephonyState.IDLE -> {
                val started = activeTelephonyStartedAtMs
                if (started != null) {
                    val elapsedSec = ((now - started) / 1_000L).coerceAtLeast(1L)
                    activeTelephonyCallRecordId = null
                    activeTelephonyStartedAtMs = null
                    viewModelScope.launch(Dispatchers.IO) {
                        phoneRepository.updateCallStatusAndTiming(
                            id = trackedId,
                            status = PhoneCallStatus.COMPLETED,
                            startedAtMs = started,
                            endedAtMs = now,
                            durationSeconds = elapsedSec
                        )
                    }
                    _uiState.update {
                        it.copy(phoneStatusMessage = "Llamada finalizada (${elapsedSec} s registrados).")
                    }
                }
            }

            else -> Unit
        }
    }

    fun onPhoneSearchQueryChanged(query: String) {
        _uiState.update { it.copy(phoneSearchQuery = query) }
        refreshNativeContactSearch(query)
    }

    private fun refreshNativeContactSearch(query: String = _uiState.value.phoneSearchQuery) {
        contactSearchJob?.cancel()
        contactSearchJob = viewModelScope.launch(Dispatchers.IO) {
            val favKeys = latestFavoriteContacts.map { it.contactKey }.toSet()
            val matches = if (phoneCallManager.hasContactsPermission()) {
                phoneRepository.queryNativeContacts(
                    query = query,
                    favoriteKeys = favKeys,
                    limit = 40
                )
            } else {
                if (query.isBlank()) {
                    latestFavoriteContacts
                } else {
                    PhoneContactMatcher.findMatches(query, latestFavoriteContacts, maxCandidates = 20)
                }
            }
            _uiState.update { it.copy(phoneContactMatches = matches) }
        }
    }

    fun onNativeContactPicked(uri: Uri?) {
        if (uri == null) return
        viewModelScope.launch(Dispatchers.IO) {
            val favKeys = latestFavoriteContacts.map { it.contactKey }.toSet()
            val picked = phoneRepository.resolvePickedContactUri(uri, favKeys)
            if (picked == null) {
                _uiState.update {
                    it.copy(phoneStatusMessage = "El contacto seleccionado no tiene un número de teléfono válido.")
                }
                return@launch
            }
            preparePendingCallFromEntry(picked, reason = "", source = "PICKER")
        }
    }

    fun toggleFavoriteContact(entry: PhoneContactEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentlyFav = latestFavoriteContacts.any { it.contactKey == entry.contactKey }
            phoneRepository.setFavorite(entry, favorite = !currentlyFav)
        }
    }

    fun preparePendingCallFromEntry(
        entry: PhoneContactEntry,
        reason: String = "",
        scheduledNote: String = "",
        source: String = "UI"
    ) {
        delayedVoiceConfirmJob?.cancel()
        val req = PendingCallRequest(
            requestId = "req_${nextPendingRequestSeq++}",
            contactName = entry.displayName,
            phoneNumber = entry.phoneNumber,
            reason = reason.trim(),
            scheduledNote = scheduledNote.trim(),
            candidates = listOf(entry),
            selectedCandidate = entry,
            awaitingConfirmation = true,
            source = source
        )
        pendingRequestSnapshot = req
        _uiState.update {
            it.copy(
                pendingCallRequest = req,
                phoneStatusMessage = "Confirmación requerida antes de llamar a ${req.contactName} (${req.phoneNumber})."
            )
        }
    }

    fun preparePendingCallManual(
        contactName: String,
        phoneNumber: String,
        reason: String = "",
        scheduledNote: String = ""
    ) {
        val normalizedNum = PhoneContactMatcher.normalizePhoneNumber(phoneNumber)
        if (normalizedNum.isEmpty()) {
            _uiState.update { it.copy(phoneStatusMessage = "Introduce un número de teléfono válido.") }
            return
        }
        val cleanName = contactName.trim().ifBlank { phoneNumber.trim() }
        val entry = PhoneContactEntry(
            contactKey = "manual:$normalizedNum",
            displayName = cleanName,
            phoneNumber = phoneNumber.trim(),
            normalizedNumber = normalizedNum,
            label = "Manual",
            source = PhoneContactSource.MANUAL
        )
        preparePendingCallFromEntry(entry, reason = reason, scheduledNote = scheduledNote, source = "MANUAL")
    }

    fun savePreparedCallOnly(
        contactName: String,
        phoneNumber: String,
        reason: String,
        scheduledNote: String
    ) {
        val cleanNumber = phoneNumber.trim()
        val cleanName = contactName.trim().ifBlank { cleanNumber }
        if (cleanNumber.none { it.isDigit() }) {
            _uiState.update { it.copy(phoneStatusMessage = "Introduce un número válido para preparar la llamada.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            phoneRepository.createCallRecord(
                contactName = cleanName,
                phoneNumber = cleanNumber,
                reason = reason,
                status = PhoneCallStatus.PREPARED,
                scheduledNote = scheduledNote.ifBlank { "Preparada" },
                followUpPending = true
            )
            _uiState.update {
                it.copy(phoneStatusMessage = "Llamada con $cleanName guardada como preparada.")
            }
        }
    }

    fun selectDisambiguationCandidate(candidate: PhoneContactEntry) {
        val current = pendingRequestSnapshot ?: return
        preparePendingCallFromEntry(
            entry = candidate,
            reason = current.reason,
            scheduledNote = current.scheduledNote,
            source = current.source
        )
    }

    fun confirmAndExecutePendingCall(preferDirectCall: Boolean) {
        val pending = pendingRequestSnapshot ?: return
        delayedVoiceConfirmJob?.cancel()
        pendingRequestSnapshot = null
        _uiState.update { it.copy(pendingCallRequest = null) }
        viewModelScope.launch(Dispatchers.IO) {
            executeCallInternal(pending, preferDirectCall)
        }
    }

    fun cancelPendingCall() {
        delayedVoiceConfirmJob?.cancel()
        val pending = pendingRequestSnapshot
        pendingRequestSnapshot = null
        _uiState.update {
            it.copy(
                pendingCallRequest = null,
                phoneStatusMessage = if (pending != null) {
                    "Llamada con ${pending.contactName} cancelada."
                } else {
                    "Operación cancelada."
                }
            )
        }
        if (pending != null && pending.contactName.isNotBlank() && pending.phoneNumber.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                phoneRepository.createCallRecord(
                    contactName = pending.contactName,
                    phoneNumber = pending.phoneNumber,
                    reason = pending.reason,
                    status = PhoneCallStatus.CANCELLED,
                    scheduledNote = pending.scheduledNote,
                    followUpPending = pending.reason.isNotBlank() || pending.scheduledNote.isNotBlank()
                )
            }
        }
    }

    fun prepareRedialFromRecord(record: PhoneCallRecord) {
        val entry = PhoneContactEntry(
            contactKey = "rec:${record.id}:${PhoneContactMatcher.normalizePhoneNumber(record.phoneNumber)}",
            displayName = record.contactName,
            phoneNumber = record.phoneNumber,
            normalizedNumber = PhoneContactMatcher.normalizePhoneNumber(record.phoneNumber),
            label = "Registro",
            source = PhoneContactSource.MANUAL
        )
        preparePendingCallFromEntry(entry, reason = record.reason, source = "HISTORY")
    }

    fun updateCallRecordNotes(
        id: Long,
        reason: String,
        userNotes: String,
        outcome: String,
        followUpPending: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            phoneRepository.updateCallNotesAndOutcome(
                id = id,
                reason = reason,
                userNotes = userNotes,
                outcome = outcome,
                followUpPending = followUpPending
            )
        }
    }

    fun deleteCallRecord(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            phoneRepository.deleteCallRecord(id)
        }
    }

    private suspend fun executeCallInternal(
        pending: PendingCallRequest,
        preferDirectCall: Boolean
    ) {
        // Libera el micrófono de ORION antes de transferir foco de audio al marcador telefónico
        if (conversationEngine.isCallActive()) {
            conversationEngine.endCall(unloadModels = false)
            delay(180L)
        }

        val now = System.currentTimeMillis()
        when (val result = phoneCallManager.dispatchCall(pending.phoneNumber, preferDirectCall)) {
            is CallDispatchResult.DirectCallStarted -> {
                val rec = phoneRepository.createCallRecord(
                    contactName = pending.contactName,
                    phoneNumber = result.sanitizedNumber,
                    reason = pending.reason,
                    status = PhoneCallStatus.CALL_INITIATED,
                    scheduledNote = pending.scheduledNote,
                    startedAtMs = now
                )
                if (phoneCallManager.hasPhoneStatePermission()) {
                    activeTelephonyCallRecordId = rec.id
                    activeTelephonyStartedAtMs = null
                }
                _uiState.update {
                    it.copy(phoneStatusMessage = "Llamando a ${pending.contactName} (${result.sanitizedNumber})…")
                }
            }

            is CallDispatchResult.DialerOpened -> {
                phoneRepository.createCallRecord(
                    contactName = pending.contactName,
                    phoneNumber = result.sanitizedNumber,
                    reason = pending.reason,
                    status = PhoneCallStatus.DIALING_UI_OPENED,
                    scheduledNote = pending.scheduledNote
                )
                _uiState.update {
                    it.copy(phoneStatusMessage = "Marcador abierto con ${pending.contactName} (${result.sanitizedNumber}).")
                }
            }

            is CallDispatchResult.Failed -> {
                recordError("Telefonía: ${result.message}")
                phoneRepository.createCallRecord(
                    contactName = pending.contactName,
                    phoneNumber = pending.phoneNumber,
                    reason = pending.reason,
                    status = PhoneCallStatus.FAILED,
                    scheduledNote = pending.scheduledNote
                )
                _uiState.update { it.copy(phoneStatusMessage = result.message) }
            }
        }
    }

    private suspend fun handleVoiceCallCommand(userText: String): String? {
        val action = CallIntentRouter.route(userText, pendingRequestSnapshot)
        return when (action) {
            CallIntentAction.None -> null

            CallIntentAction.CancelPendingCall -> {
                delayedVoiceConfirmJob?.cancel()
                val hadPending = pendingRequestSnapshot != null
                if (hadPending) {
                    cancelPendingCall()
                    "De acuerdo, llamada cancelada. No marcaré ningún número."
                } else {
                    "De acuerdo, no iniciaré ninguna llamada."
                }
            }

            CallIntentAction.ConfirmPendingCall -> {
                val target = pendingRequestSnapshot
                if (target == null || !target.awaitingConfirmation) {
                    return null
                }
                delayedVoiceConfirmJob?.cancel()
                delayedVoiceConfirmJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(1_850L)
                    if (pendingRequestSnapshot?.requestId == target.requestId) {
                        pendingRequestSnapshot = null
                        _uiState.update { it.copy(pendingCallRequest = null) }
                        executeCallInternal(target, preferDirectCall = true)
                    }
                }
                if (phoneCallManager.hasCallPhonePermission()) {
                    "Confirmado. Iniciando llamada con ${target.contactName}."
                } else {
                    "Confirmado. Abriendo el marcador para llamar a ${target.contactName}."
                }
            }

            is CallIntentAction.SelectCandidateByIndex -> {
                val pending = pendingRequestSnapshot ?: return null
                val candidate = pending.candidates.getOrNull(action.index) ?: return null
                val updated = pending.copy(
                    contactName = candidate.displayName,
                    phoneNumber = candidate.phoneNumber,
                    selectedCandidate = candidate,
                    awaitingConfirmation = pending.scheduledNote.isBlank()
                )
                if (pending.scheduledNote.isNotBlank()) {
                    pendingRequestSnapshot = null
                    _uiState.update { it.copy(pendingCallRequest = null) }
                    phoneRepository.createCallRecord(
                        contactName = candidate.displayName,
                        phoneNumber = candidate.phoneNumber,
                        reason = pending.reason,
                        status = PhoneCallStatus.PREPARED,
                        scheduledNote = pending.scheduledNote,
                        followUpPending = true
                    )
                    "Listo, he dejado preparada la llamada con ${candidate.displayName} (${pending.scheduledNote})."
                } else {
                    pendingRequestSnapshot = updated
                    _uiState.update { it.copy(pendingCallRequest = updated) }
                    "Has elegido a ${candidate.displayName}, número ${formatSpokenNumber(candidate.phoneNumber)}. ¿Confirmas que llame?"
                }
            }

            is CallIntentAction.SelectCandidateByName -> {
                val pending = pendingRequestSnapshot ?: return null
                val narrowed = PhoneContactMatcher.findMatches(action.query, pending.candidates, maxCandidates = 2)
                if (narrowed.size == 1) {
                    val chosen = narrowed.first()
                    if (pending.scheduledNote.isNotBlank()) {
                        pendingRequestSnapshot = null
                        _uiState.update { it.copy(pendingCallRequest = null) }
                        phoneRepository.createCallRecord(
                            contactName = chosen.displayName,
                            phoneNumber = chosen.phoneNumber,
                            reason = pending.reason,
                            status = PhoneCallStatus.PREPARED,
                            scheduledNote = pending.scheduledNote,
                            followUpPending = true
                        )
                        "Preparada la llamada con ${chosen.displayName} (${pending.scheduledNote})."
                    } else {
                        val updated = pending.copy(
                            contactName = chosen.displayName,
                            phoneNumber = chosen.phoneNumber,
                            selectedCandidate = chosen,
                            awaitingConfirmation = true
                        )
                        pendingRequestSnapshot = updated
                        _uiState.update { it.copy(pendingCallRequest = updated) }
                        "Entendido, ${chosen.displayName}, número ${formatSpokenNumber(chosen.phoneNumber)}. ¿Confirmas que inicie la llamada?"
                    }
                } else {
                    "No he podido distinguir cuál de los contactos prefieres. Dime el primero o el segundo, o di cancelar."
                }
            }

            is CallIntentAction.QueryCallHistory -> {
                val records = phoneRepository.getAllCallRecords()
                phoneRepository.answerCallHistoryQuery(
                    queryType = action.queryType,
                    contactQuery = action.contactQuery,
                    records = records
                )
            }

            CallIntentAction.CallLastContact -> {
                val latest = phoneRepository.getLatestCallRecord()
                if (latest == null) {
                    "Todavía no tengo ninguna llamada registrada desde ORION."
                } else {
                    val req = PendingCallRequest(
                        requestId = "req_${nextPendingRequestSeq++}",
                        contactName = latest.contactName,
                        phoneNumber = latest.phoneNumber,
                        reason = latest.reason,
                        awaitingConfirmation = true,
                        source = "VOICE_LAST"
                    )
                    pendingRequestSnapshot = req
                    _uiState.update { it.copy(pendingCallRequest = req) }
                    "El último contacto registrado fue ${latest.contactName}, número ${formatSpokenNumber(latest.phoneNumber)}. ¿Confirmas que vuelva a llamar?"
                }
            }

            is CallIntentAction.LookupContactNumber -> {
                val matches = phoneRepository.resolveVoiceContactMatches(action.rawTargetQuery)
                if (matches.isEmpty()) {
                    missingContactReply(action.rawTargetQuery)
                } else if (matches.size == 1) {
                    val c = matches.first()
                    "El teléfono de ${c.displayName} es ${formatSpokenNumber(c.phoneNumber)}. Si quieres que llame, dímelo."
                } else {
                    val listText = matches.take(3).joinToString("; ") {
                        "${it.displayName}, ${formatSpokenNumber(it.phoneNumber)}"
                    }
                    "He encontrado varios contactos: $listText."
                }
            }

            is CallIntentAction.InitiateCall -> {
                if (action.rawTargetQuery.isBlank() && action.scheduledOnly) {
                    return "Dime el nombre del contacto para preparar la llamada en tu registro."
                }
                val resolved = resolveCandidatesOrRawNumber(action.rawTargetQuery)
                if (resolved.isEmpty()) {
                    return missingContactReply(action.rawTargetQuery)
                }

                if (action.scheduledOnly) {
                    if (resolved.size == 1) {
                        val chosen = resolved.first()
                        phoneRepository.createCallRecord(
                            contactName = chosen.displayName,
                            phoneNumber = chosen.phoneNumber,
                            reason = action.reason,
                            status = PhoneCallStatus.PREPARED,
                            scheduledNote = action.scheduledNote.ifBlank { "Preparada" },
                            followUpPending = true
                        )
                        "He preparado la llamada con ${chosen.displayName} (${action.scheduledNote}). La tienes en la pestaña Teléfono."
                    } else {
                        val req = PendingCallRequest(
                            requestId = "req_${nextPendingRequestSeq++}",
                            contactName = action.rawTargetQuery,
                            phoneNumber = "",
                            reason = action.reason,
                            scheduledNote = action.scheduledNote.ifBlank { "Preparada" },
                            candidates = resolved.take(3),
                            awaitingConfirmation = false,
                            source = "VOICE"
                        )
                        pendingRequestSnapshot = req
                        _uiState.update { it.copy(pendingCallRequest = req) }
                        val opts = req.candidates.mapIndexed { idx, c -> "${idx + 1}, ${c.displayName}" }.joinToString("; ")
                        "He encontrado varios contactos para preparar la llamada: $opts. ¿Cuál eliges?"
                    }
                } else if (resolved.size == 1) {
                    val chosen = resolved.first()
                    val req = PendingCallRequest(
                        requestId = "req_${nextPendingRequestSeq++}",
                        contactName = chosen.displayName,
                        phoneNumber = chosen.phoneNumber,
                        reason = action.reason,
                        candidates = listOf(chosen),
                        selectedCandidate = chosen,
                        awaitingConfirmation = true,
                        source = "VOICE"
                    )
                    pendingRequestSnapshot = req
                    _uiState.update { it.copy(pendingCallRequest = req) }
                    val reasonCue = if (action.reason.isNotBlank()) " por ${action.reason}" else ""
                    "Voy a llamar a ${chosen.displayName}, número ${formatSpokenNumber(chosen.phoneNumber)}$reasonCue. ¿Confirmas que inicie la llamada?"
                } else {
                    val req = PendingCallRequest(
                        requestId = "req_${nextPendingRequestSeq++}",
                        contactName = action.rawTargetQuery,
                        phoneNumber = "",
                        reason = action.reason,
                        candidates = resolved.take(3),
                        awaitingConfirmation = false,
                        source = "VOICE"
                    )
                    pendingRequestSnapshot = req
                    _uiState.update { it.copy(pendingCallRequest = req) }
                    val opts = req.candidates.mapIndexed { idx, c -> "${idx + 1}, ${c.displayName} (${c.label})" }.joinToString("; ")
                    "He encontrado varios contactos: $opts. Dime el primero o el segundo para confirmar antes de llamar."
                }
            }
        }
    }

    private suspend fun resolveCandidatesOrRawNumber(targetQuery: String): List<PhoneContactEntry> {
        val matches = phoneRepository.resolveVoiceContactMatches(targetQuery)
        if (matches.isNotEmpty()) return matches
        val digits = targetQuery.filter { it.isDigit() }
        if (digits.length >= 6) {
            val normalized = PhoneContactMatcher.normalizePhoneNumber(targetQuery)
            return listOf(
                PhoneContactEntry(
                    contactKey = "voice_num:$normalized",
                    displayName = normalized,
                    phoneNumber = normalized,
                    normalizedNumber = normalized,
                    label = "Dictado",
                    source = PhoneContactSource.MANUAL
                )
            )
        }
        return emptyList()
    }

    private fun missingContactReply(target: String): String =
        if (!phoneCallManager.hasContactsPermission()) {
            "No tengo permiso para consultar tu agenda todavía. Concede el permiso en la pestaña Teléfono o elige el contacto manualmente."
        } else {
            "No encuentro a $target en tu agenda ni en tus favoritos. Revisa el nombre o el número en la pestaña Teléfono."
        }

    private fun formatSpokenNumber(raw: String): String =
        raw.trim().ifBlank { "desconocido" }

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
            appendLine("ORION Voice Lab 0.6.0 — informe de diagnóstico")
            appendLine(DeviceMetrics.deviceSummary())
            appendLine(
                "Telefonía local: soporte=${state.isTelephonySupported} · permisoContactos=${state.hasContactsPermission} · " +
                    "permisoLlamar=${state.hasCallPhonePermission} · permisoEstado=${state.hasPhoneStatePermission} · " +
                    "favoritos=${state.favoritePhoneContacts.size} · llamadas=${state.phoneCallRecords.size} · estado=${state.systemTelephonyState.displayLabel}"
            )
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
        contactSearchJob?.cancel()
        delayedVoiceConfirmJob?.cancel()
        phoneCallManager.stopObservingCallState()
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

