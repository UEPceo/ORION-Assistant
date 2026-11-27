package com.orion.voicelab.ui

import android.app.Application
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.orion.voicelab.OrionCrashDiagnostics
import com.orion.voicelab.asr.MoonshineSpeechRecognitionEngine
import com.orion.voicelab.audio.AudioRoutingController
import com.orion.voicelab.conversation.ConversationEngine
import com.orion.voicelab.connectors.ConnectorResult
import com.orion.voicelab.connectors.ConnectorVoiceIntentRouter
import com.orion.voicelab.connectors.OrionConnectorRuntime
import com.orion.voicelab.creative.CreativeSkillRouter
import com.orion.voicelab.creative.CreativeBrainPromptComposer
import com.orion.voicelab.creative.CreativeSkill
import com.orion.voicelab.creative.OrionMediaRuntime
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
import com.orion.voicelab.data.PendingTaskOperation
import com.orion.voicelab.data.PendingTaskPrompt
import com.orion.voicelab.data.PhoneCallRecord
import com.orion.voicelab.data.PhoneCallStatus
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.PhoneContactSource
import com.orion.voicelab.data.SystemTelephonyState
import com.orion.voicelab.data.TaskFilter
import com.orion.voicelab.data.TaskIntentAction
import com.orion.voicelab.data.TaskItem
import com.orion.voicelab.data.TaskPriority
import com.orion.voicelab.data.TaskQueryScope
import com.orion.voicelab.data.TaskStatus
import com.orion.voicelab.data.TranscriptEntry
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import com.orion.voicelab.llm.LlamaCppLanguageModel
import com.orion.voicelab.llm.ModelProfilePromptComposer
import com.orion.voicelab.knowledge.OrionBrainRuntime
import com.orion.voicelab.brain.PlanningPromptComposer
import com.orion.voicelab.brain.OrionPlan
import com.orion.voicelab.brain.PlanExecutionEntity
import com.orion.voicelab.brain.PlanExecutionRepository
import com.orion.voicelab.brain.PersistentPlanExecutionService
import com.orion.voicelab.brain.ConnectorPlanStepRunner
import com.orion.voicelab.knowledge.BrainPromptComposer
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
import com.orion.voicelab.tasks.AndroidTaskReminderScheduler
import com.orion.voicelab.tasks.SpanishDateTimeParser
import com.orion.voicelab.tasks.TaskIntentRouter
import com.orion.voicelab.tasks.TaskRepository
import com.orion.voicelab.tts.SherpaOnnxSpeechSynthesizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceLabViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val METRICS_TICK_MS = 15_000L
        private const val CALL_TIMER_TICK_MS = 1_000L
        private const val MAX_RECENT_ERRORS = 8
    }

    private val appContext = application.applicationContext
    private val settingsPreferences = appContext.getSharedPreferences("orion_user_settings", 0)
    private val connectorRuntime = OrionConnectorRuntime(appContext)
    private val brainRuntime = OrionBrainRuntime(appContext)
    private val mediaRuntime = OrionMediaRuntime()
    private val connectorPermissionManager = com.orion.voicelab.connectors.ConnectorPermissionManager()
    @Volatile private var lastConnectorRepository: String? = null
    @Volatile private var pendingConnectorInputAction: com.orion.voicelab.connectors.ConnectorAction? = null
    private val database = OrionDatabase.getInstance(appContext)
    private val conversationRepository = ConversationRepository(database.conversationDao())
    private val memoryRepository = MemoryRepository(database.memoryDao())
    private val identityRepository = IdentityRepository(database.identityDao())
    private val contactRepository = ContactRepository(database.contactDao())
    private val phoneRepository = PhoneRepository(database.phoneDao(), appContext.contentResolver)
    private val phoneCallManager = PhoneCallManager(appContext)
    private val taskReminderScheduler = AndroidTaskReminderScheduler(appContext)
    private val taskRepository = TaskRepository(database.taskDao(), taskReminderScheduler)
    private val planExecutionRepository = PlanExecutionRepository(database.planExecutionDao())
    private val planExecutionService = PersistentPlanExecutionService(planExecutionRepository)
    private val planExecutionRepository = PlanExecutionRepository(database.planExecutionDao())
    private val planExecutionService = PersistentPlanExecutionService(planExecutionRepository)
    private val persistenceMutex = Mutex()
    private val asrDownloadMutex = Mutex()
    private val ttsDownloadMutex = Mutex()
    private val voiceDownloadMutex = Mutex()
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
    private var latestTasks: List<TaskItem> = emptyList()

    @Volatile
    private var pendingTaskPromptSnapshot: PendingTaskPrompt? = null

    @Volatile
    private var lastReferencedTaskId: Long? = null

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
            val baseContext = ContextPromptBuilder.buildTurnContext(
                identity = latestIdentity,
                allMemories = latestMemories,
                conversationSummary = latestConversationSummary,
                recentHistory = recentHistory,
                currentQuery = userText,
                activeCoworker = latestSelectedCoworker,
                recentPhoneCalls = latestPhoneCallRecords,
                activeTasks = latestTasks
            )
            val brainContext = withContext(Dispatchers.IO) {
                brainRuntime.retrieve(userText)
            }
            val creativeSkill = CreativeSkillRouter.route(userText)
            val mediaStatus = when (creativeSkill) {
                CreativeSkill.IMAGE_GENERATION -> mediaRuntime.imageGenerationStatus()
                CreativeSkill.VIDEO_GENERATION -> mediaRuntime.videoGenerationStatus()
                CreativeSkill.IMAGE_UNDERSTANDING -> mediaRuntime.imageUnderstandingStatus("image/jpeg")
                CreativeSkill.VIDEO_UNDERSTANDING -> mediaRuntime.videoUnderstandingStatus()
                else -> null
            }
            val creativeContext = CreativeBrainPromptComposer.compose(
                userText = userText,
                skill = creativeSkill,
                activeRole = latestSelectedCoworker?.roleTitle,
                mediaStatus = mediaStatus
            )
            val selfKnowledgeContext = OrionSelfKnowledgeAdvisor.compose(
                userQuery = userText,
                state = _uiState.value,
                diagnosticLog = if (OrionSelfKnowledgeAdvisor.shouldReadDiagnostics(userText)) {
                    OrionCrashDiagnostics.readRecentDiagnostics(appContext)
                } else {
                    ""
                },
                context = appContext
            )
            baseContext.copy(
                systemPrompt = buildString {
                    append(baseContext.systemPrompt)
                    append("\n\n")
                    append(BrainPromptComposer.compose(brainContext))
                    append("\\n\\n")
                    append(ModelProfilePromptComposer.compose())
                    val planningGuidance = PlanningPromptComposer.compose(userText)
                    if (planningGuidance.isNotBlank()) {
                        append("\\n\\n")
                        append(planningGuidance)
                    }
                    if (creativeContext.isNotBlank()) {
                        append("\n\n")
                        append(creativeContext)
                    }
                    if (selfKnowledgeContext.isNotBlank()) {
                        append("\n\n")
                        append(selfKnowledgeContext)
                    }
                }
            )
        },
        directTurnResponder = { userText ->
            handleVoiceTurnCommand(userText)
        }
    )

    private val pendingCallLock = Any()
    private val pendingTaskLock = Any()

    @Volatile
    private var voiceConfirmTtsSignal: CompletableDeferred<Unit>? = null

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
            hasPhoneStatePermission = phoneCallManager.hasPhoneStatePermission(),
            hasNotificationsPermission = AndroidTaskReminderScheduler.hasNotificationPermission(appContext)
        )
    )
    val uiState: StateFlow<VoiceLabUiState> = _uiState.asStateFlow()

    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _planExecutionRecords = MutableStateFlow<List<PlanExecutionEntity>>(emptyList())
    val planExecutionRecords: StateFlow<List<PlanExecutionEntity>> = _planExecutionRecords.asStateFlow()

    private val _planExecutionRecords = MutableStateFlow<List<PlanExecutionEntity>>(emptyList())
    val planExecutionRecords: StateFlow<List<PlanExecutionEntity>> = _planExecutionRecords.asStateFlow()

    init {
        speechSynthesizer.selectVoice(settingsPreferences.getString("selected_voice_id", "sharvard_f").orEmpty())
        val speakerEnabled = settingsPreferences.getBoolean("speaker_enabled", false)
        audioRoutingController.setSpeakerEnabled(speakerEnabled)
        _uiState.update { it.copy(
            selectedVoiceId = speechSynthesizer.selectedVoice().id,
            isSpeakerEnabled = speakerEnabled
        ) }
        attachConversationCallbacks()
        observePersistenceFlows()
        observePlanExecutions()
        observePlanExecutions()
        initializePersistence()
        refreshPhonePermissions()
        startObservingTelephonyState()
        reconcileTaskReminders()
        refreshModelState()
        refreshMetrics()
        ensureInitialModelsDownloaded()
    }

    private fun observePlanExecutions() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { planExecutionService.recoverInterruptedExecutions() }
            planExecutionRepository.observeAll().collect { records ->
                _planExecutionRecords.value = records
            }
        }
    }

    /** Stores a validated plan locally before any step is allowed to execute. */
    fun savePlanExecution(plan: OrionPlan): Job = viewModelScope.launch(Dispatchers.IO) {
        planExecutionService.create(plan)
    }

    /** Each invocation requires fresh approval for every unfinished protected step. */
    fun executePlanExecution(
        planId: String,
        approvedStepIds: Set<String>,
        retryStepIds: Set<String> = emptySet()
    ): Job = viewModelScope.launch(Dispatchers.IO) {
        planExecutionService.execute(
            planId = planId,
            approvedStepIds = approvedStepIds,
            runner = ConnectorPlanStepRunner(connectorRuntime.dispatcher),
            retryStepIds = retryStepIds
        )
    }

    fun deletePlanExecution(planId: String): Job = viewModelScope.launch(Dispatchers.IO) {
        planExecutionRepository.delete(planId)
    }

    private fun observePlanExecutions() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { planExecutionService.recoverInterruptedExecutions() }
            planExecutionRepository.observeAll().collect { records ->
                _planExecutionRecords.value = records
            }
        }
    }

    /** Stores a validated plan locally before any step is allowed to execute. */
    fun savePlanExecution(plan: OrionPlan): Job = viewModelScope.launch(Dispatchers.IO) {
        planExecutionService.create(plan)
    }

    /** Each invocation requires fresh approval for every unfinished protected step. */
    fun executePlanExecution(
        planId: String,
        approvedStepIds: Set<String>,
        retryStepIds: Set<String> = emptySet()
    ): Job = viewModelScope.launch(Dispatchers.IO) {
        planExecutionService.execute(
            planId = planId,
            approvedStepIds = approvedStepIds,
            runner = ConnectorPlanStepRunner(connectorRuntime.dispatcher),
            retryStepIds = retryStepIds
        )
    }

    fun deletePlanExecution(planId: String): Job = viewModelScope.launch(Dispatchers.IO) {
        planExecutionRepository.delete(planId)
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
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.observeAllTasks().collect { taskList ->
                latestTasks = taskList
                _uiState.update { it.copy(tasks = taskList) }
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
                        conversationSummary = latestConversationSummary,
                        activeCoworker = latestSelectedCoworker
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
                if (state == ConversationState.INTERRUPTED) {
                    cancelDelayedVoiceConfirmJob(clearPending = false)
                } else if (state == ConversationState.LISTENING) {
                    voiceConfirmTtsSignal?.complete(Unit)
                }
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
                val handledByDeterministicRouter = synchronized(pendingCallLock) {
                    CallIntentRouter.route(turn.text, pendingRequestSnapshot) != CallIntentAction.None
                } || synchronized(pendingTaskLock) {
                    TaskIntentRouter.route(turn.text, pendingTaskPromptSnapshot) != TaskIntentAction.None
                }

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

                    if (latestIdentity.memoryEnabled && !handledByDeterministicRouter) {
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
                voiceConfirmTtsSignal?.complete(Unit)
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
                availableVoices = speechSynthesizer.availableVoices(),
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
                recordError("ASR descarga: $msg", e)
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
                recordError("LLM descarga: $msg", e)
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
                recordError("TTS descarga: $msg", e)
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
        cancelDelayedVoiceConfirmJob(clearPending = true)
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

    fun toggleSpeaker() {
        val nextEnabled = !_uiState.value.isSpeakerEnabled
        audioRoutingController.setSpeakerEnabled(nextEnabled)
        settingsPreferences.edit().putBoolean("speaker_enabled", nextEnabled).apply()
        _uiState.update { it.copy(isSpeakerEnabled = nextEnabled) }
    }

    fun interruptCall() {
        cancelDelayedVoiceConfirmJob(clearPending = false)
        conversationEngine.interruptSpeaking()
    }

    fun downloadVoicePack(voiceId: String) {
        val voice = speechSynthesizer.availableVoices().firstOrNull { it.id == voiceId } ?: return
        val url = voice.downloadUrl ?: return
        if (voice.isInstalled || _uiState.value.voiceDownloadingId != null) return
        viewModelScope.launch(Dispatchers.IO) {
            voiceDownloadMutex.withLock {
                _uiState.update { it.copy(
                    voiceDownloadingId = voiceId,
                    voiceDownloadProgressPercent = 0,
                    voiceDownloadStatus = "Preparando descarga…",
                    voiceDownloadError = null
                ) }
                try {
                    ModelManager.downloadAndInstallTtsVoice(
                        filesDir = appContext.filesDir,
                        directoryName = voice.modelDirectoryName,
                        archiveName = "${voice.modelDirectoryName}.tar.bz2",
                        modelFileName = voice.modelFileName,
                        url = url,
                        shouldCancel = { !viewModelScope.isActive },
                        onProgress = { percent, downloadedMb, totalMb ->
                            _uiState.update { it.copy(
                                voiceDownloadProgressPercent = percent,
                                voiceDownloadStatus = if (totalMb > 0L) "Descargando $downloadedMb / $totalMb MB" else "Descargando paquete de voz…"
                            ) }
                        },
                        onExtracting = {
                            _uiState.update { it.copy(
                                voiceDownloadProgressPercent = 100,
                                voiceDownloadStatus = "Verificando e instalando la voz…"
                            ) }
                        }
                    )
                    _uiState.update { it.copy(
                        availableVoices = speechSynthesizer.availableVoices(),
                        voiceDownloadingId = null,
                        voiceDownloadStatus = "Voz instalada. Ya puedes seleccionarla.",
                        voiceDownloadProgressPercent = 100,
                        voiceDownloadError = null
                    ) }
                } catch (cancelled: CancellationException) {
                    _uiState.update { it.copy(
                        voiceDownloadingId = null,
                        voiceDownloadStatus = "Descarga interrumpida. Puedes volver a intentarlo.",
                        voiceDownloadProgressPercent = 0
                    ) }
                    throw cancelled
                } catch (error: Exception) {
                    recordError("Voz TTS: ${error.message ?: error.javaClass.simpleName}", error)
                    _uiState.update { it.copy(
                        voiceDownloadingId = null,
                        voiceDownloadError = error.message ?: "No se pudo instalar la voz.",
                        voiceDownloadStatus = null,
                        availableVoices = speechSynthesizer.availableVoices()
                    ) }
                }
            }
        }
    }

    fun selectVoice(voiceId: String) {
        conversationEngine.selectVoice(voiceId)
        val selected = speechSynthesizer.selectedVoice().id
        settingsPreferences.edit().putString("selected_voice_id", selected).apply()
        _uiState.update { it.copy(selectedVoiceId = selected) }
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
        val notificationsOk = AndroidTaskReminderScheduler.hasNotificationPermission(appContext)
        val supported = phoneCallManager.isTelephonySupported()

        _uiState.update { current ->
            current.copy(
                hasContactsPermission = contactsOk,
                hasCallPhonePermission = callOk,
                hasPhoneStatePermission = stateOk,
                hasNotificationsPermission = notificationsOk,
                isTelephonySupported = supported
            )
        }
        refreshNativeContactSearch()
        if (stateOk) {
            startObservingTelephonyState()
        } else {
            phoneCallManager.stopObservingCallState()
            activeTelephonyCallRecordId = null
            activeTelephonyStartedAtMs = null
            _uiState.update { it.copy(systemTelephonyState = SystemTelephonyState.IDLE) }
        }
    }

    fun refreshNotificationPermission() {
        val granted = AndroidTaskReminderScheduler.hasNotificationPermission(appContext)
        _uiState.update { it.copy(hasNotificationsPermission = granted) }
        if (granted) {
            reconcileTaskReminders()
        }
    }

    private fun reconcileTaskReminders() {
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.reconcileReminders()
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
                        val linkedTasks = taskRepository.getAllTasks().filter {
                            it.status == TaskStatus.PENDING && it.linkedCallRecordId == trackedId
                        }
                        if (linkedTasks.isNotEmpty()) {
                            linkedTasks.filter { it.reminderActive }.forEach { task ->
                                AndroidTaskReminderScheduler.showReminderNotification(
                                    context = appContext,
                                    task = task,
                                    isOverdue = false
                                )
                            }
                            _uiState.update {
                                it.copy(
                                    taskStatusMessage = "Llamada finalizada (${elapsedSec} s). Tienes ${linkedTasks.size} seguimiento(s) vinculado(s)."
                                )
                            }
                        }
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

    private fun setPendingCallRequest(req: PendingCallRequest?) {
        synchronized(pendingCallLock) {
            pendingRequestSnapshot = req
            _uiState.update { it.copy(pendingCallRequest = req) }
        }
    }

    private fun takePendingCallIfMatches(expectedRequestId: String? = null): PendingCallRequest? =
        synchronized(pendingCallLock) {
            val current = pendingRequestSnapshot ?: return null
            if (expectedRequestId != null && current.requestId != expectedRequestId) return null
            pendingRequestSnapshot = null
            _uiState.update { it.copy(pendingCallRequest = null) }
            current
        }

    private fun cancelDelayedVoiceConfirmJob(clearPending: Boolean = false) {
        delayedVoiceConfirmJob?.cancel()
        delayedVoiceConfirmJob = null
        voiceConfirmTtsSignal?.cancel()
        voiceConfirmTtsSignal = null
        if (clearPending) {
            setPendingCallRequest(null)
        }
    }

    fun preparePendingCallFromEntry(
        entry: PhoneContactEntry,
        reason: String = "",
        scheduledNote: String = "",
        source: String = "UI"
    ) {
        cancelDelayedVoiceConfirmJob(clearPending = false)
        val req = synchronized(pendingCallLock) {
            PendingCallRequest(
                requestId = "req_${nextPendingRequestSeq++}",
                contactName = entry.displayName,
                phoneNumber = entry.phoneNumber,
                reason = reason.trim(),
                scheduledNote = scheduledNote.trim(),
                candidates = listOf(entry),
                selectedCandidate = entry,
                awaitingConfirmation = true,
                source = source
            ).also { pendingRequestSnapshot = it }
        }
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
        val current = synchronized(pendingCallLock) { pendingRequestSnapshot } ?: return
        preparePendingCallFromEntry(
            entry = candidate,
            reason = current.reason,
            scheduledNote = current.scheduledNote,
            source = current.source
        )
    }

    fun confirmAndExecutePendingCall(preferDirectCall: Boolean) {
        cancelDelayedVoiceConfirmJob(clearPending = false)
        val pending = takePendingCallIfMatches() ?: return
        viewModelScope.launch(Dispatchers.IO) {
            executeCallInternal(pending, preferDirectCall)
        }
    }

    fun cancelPendingCall() {
        cancelDelayedVoiceConfirmJob(clearPending = false)
        val pending = takePendingCallIfMatches()
        _uiState.update {
            it.copy(
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

    private suspend fun handleVoiceTurnCommand(userText: String): String? {
        val pendingConfirmation = connectorPermissionManager.pendingAction()
        if (pendingConfirmation != null) {
            val normalized = userText.trim().lowercase()
            if (normalized in setOf("sí", "si", "sí, confirma", "si confirma", "confirmo", "adelante", "hazlo")) {
                val confirmed = connectorPermissionManager.confirm(userText) ?: return "La confirmación ha caducado. Solicita de nuevo la operación."
                val result = connectorRuntime.dispatcher.dispatch(
                    action = confirmed,
                    userRequestedExactAction = true,
                    userConfirmed = true
                )
                return connectorResultText(result)
            }
            if (normalized in setOf("no", "cancelar", "cancela", "no, cancelar", "olvídalo", "olvidalo")) {
                connectorPermissionManager.cancel()
                return "Acción externa cancelada. No se ha realizado ningún cambio."
            }
            connectorPermissionManager.cancel()
        }

        pendingConnectorInputAction?.let { pending ->
            val normalized = userText.trim().lowercase()
            if (normalized in setOf("cancelar", "cancela", "olvídalo", "olvidalo", "no")) {
                pendingConnectorInputAction = null
                return "De acuerdo, consulta del conector cancelada."
            }
            val repository = Regex("""[A-Za-z0-9_.-]{1,100}/[A-Za-z0-9_.-]{1,100}""").find(userText)?.value
            if (repository != null) {
                pendingConnectorInputAction = null
                lastConnectorRepository = repository
                val completed = pending.copy(arguments = pending.arguments + ("repository" to repository))
                return connectorResultText(connectorRuntime.dispatcher.dispatch(completed, true))
            }
            pendingConnectorInputAction = null
        }

        val hasPendingCall = synchronized(pendingCallLock) { pendingRequestSnapshot != null }
        if (hasPendingCall) {
            handleVoiceCallCommand(userText)?.let { return it }
        }
        val hasPendingTask = synchronized(pendingTaskLock) { pendingTaskPromptSnapshot != null }
        if (hasPendingTask) {
            handleVoiceTaskCommand(userText)?.let { return it }
        }
        val callPendingWith = CallIntentRouter.route(userText, null)
        if (callPendingWith is CallIntentAction.QueryCallHistory &&
            callPendingWith.queryType == com.orion.voicelab.data.HistoryQueryType.PENDING_WITH
        ) {
            handleVoiceCallCommand(userText)?.let { return it }
        }

        ConnectorVoiceIntentRouter.route(userText, lastConnectorRepository)?.let { routed ->
            val repository = routed.action.arguments["repository"]
            if (!repository.isNullOrBlank()) lastConnectorRepository = repository
            val result = connectorRuntime.dispatcher.dispatch(
                action = routed.action,
                userRequestedExactAction = routed.userRequestedExactAction
            )
            when (result) {
                is ConnectorResult.Success -> return result.summary
                is ConnectorResult.NeedsUserInput -> {
                    pendingConnectorInputAction = routed.action
                    return result.prompt
                }
                is ConnectorResult.ConfirmationRequired -> {
                    connectorPermissionManager.request(result.action)
                    return result.prompt
                }
                is ConnectorResult.Failure -> return result.safeMessage
            }
        }
        handleVoiceTaskCommand(userText)?.let { return it }
        return handleVoiceCallCommand(userText)
    }

    private fun connectorResultText(result: ConnectorResult): String = when (result) {
        is ConnectorResult.Success -> result.summary
        is ConnectorResult.NeedsUserInput -> result.prompt
        is ConnectorResult.ConfirmationRequired -> {
            connectorPermissionManager.request(result.action)
            result.prompt
        }
        is ConnectorResult.Failure -> result.safeMessage
    }

    private suspend fun handleVoiceCallCommand(userText: String): String? {
        val currentSnapshot = synchronized(pendingCallLock) { pendingRequestSnapshot }
        val action = CallIntentRouter.route(userText, currentSnapshot)
        return when (action) {
            CallIntentAction.None -> null

            CallIntentAction.CancelPendingCall -> {
                val hadPending = synchronized(pendingCallLock) { pendingRequestSnapshot != null }
                if (hadPending) {
                    cancelPendingCall()
                    "De acuerdo, llamada cancelada. No marcaré ningún número."
                } else {
                    cancelDelayedVoiceConfirmJob(clearPending = false)
                    "De acuerdo, no iniciaré ninguna llamada."
                }
            }

            CallIntentAction.ConfirmPendingCall -> {
                val target = synchronized(pendingCallLock) {
                    pendingRequestSnapshot?.takeIf { it.awaitingConfirmation }
                } ?: return null
                cancelDelayedVoiceConfirmJob(clearPending = false)
                val ttsDoneSignal = CompletableDeferred<Unit>()
                voiceConfirmTtsSignal = ttsDoneSignal
                delayedVoiceConfirmJob = viewModelScope.launch(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(4_500L) {
                            ttsDoneSignal.await()
                        }
                        delay(200L)
                        val consumed = takePendingCallIfMatches(target.requestId) ?: return@launch
                        executeCallInternal(consumed, preferDirectCall = true)
                    } finally {
                        if (voiceConfirmTtsSignal === ttsDoneSignal) {
                            voiceConfirmTtsSignal = null
                        }
                    }
                }
                if (phoneCallManager.hasCallPhonePermission()) {
                    "Confirmado. Iniciando llamada con ${target.contactName}."
                } else {
                    "Confirmado. Abriendo el marcador para llamar a ${target.contactName}."
                }
            }

            is CallIntentAction.SelectCandidateByIndex -> {
                val pending = synchronized(pendingCallLock) { pendingRequestSnapshot } ?: return null
                val candidate = pending.candidates.getOrNull(action.index) ?: return null
                val updated = pending.copy(
                    contactName = candidate.displayName,
                    phoneNumber = candidate.phoneNumber,
                    selectedCandidate = candidate,
                    awaitingConfirmation = pending.scheduledNote.isBlank()
                )
                if (pending.scheduledNote.isNotBlank()) {
                    setPendingCallRequest(null)
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
                    setPendingCallRequest(updated)
                    "Has elegido a ${candidate.displayName}, número ${formatSpokenNumber(candidate.phoneNumber)}. ¿Confirmas que llame?"
                }
            }

            is CallIntentAction.SelectCandidateByName -> {
                val pending = synchronized(pendingCallLock) { pendingRequestSnapshot } ?: return null
                val narrowed = PhoneContactMatcher.findMatches(action.query, pending.candidates, maxCandidates = 2)
                if (narrowed.size == 1) {
                    val chosen = narrowed.first()
                    if (pending.scheduledNote.isNotBlank()) {
                        setPendingCallRequest(null)
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
                        setPendingCallRequest(updated)
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
                    val req = synchronized(pendingCallLock) {
                        PendingCallRequest(
                            requestId = "req_${nextPendingRequestSeq++}",
                            contactName = latest.contactName,
                            phoneNumber = latest.phoneNumber,
                            reason = latest.reason,
                            awaitingConfirmation = true,
                            source = "VOICE_LAST"
                        ).also { pendingRequestSnapshot = it }
                    }
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
                        val req = synchronized(pendingCallLock) {
                            PendingCallRequest(
                                requestId = "req_${nextPendingRequestSeq++}",
                                contactName = action.rawTargetQuery,
                                phoneNumber = "",
                                reason = action.reason,
                                scheduledNote = action.scheduledNote.ifBlank { "Preparada" },
                                candidates = resolved.take(3),
                                awaitingConfirmation = false,
                                source = "VOICE"
                            ).also { pendingRequestSnapshot = it }
                        }
                        _uiState.update { it.copy(pendingCallRequest = req) }
                        val opts = req.candidates.mapIndexed { idx, c -> "${idx + 1}, ${c.displayName}" }.joinToString("; ")
                        "He encontrado varios contactos para preparar la llamada: $opts. ¿Cuál eliges?"
                    }
                } else if (resolved.size == 1) {
                    val chosen = resolved.first()
                    val req = synchronized(pendingCallLock) {
                        PendingCallRequest(
                            requestId = "req_${nextPendingRequestSeq++}",
                            contactName = chosen.displayName,
                            phoneNumber = chosen.phoneNumber,
                            reason = action.reason,
                            candidates = listOf(chosen),
                            selectedCandidate = chosen,
                            awaitingConfirmation = true,
                            source = "VOICE"
                        ).also { pendingRequestSnapshot = it }
                    }
                    _uiState.update { it.copy(pendingCallRequest = req) }
                    val reasonCue = if (action.reason.isNotBlank()) " por ${action.reason}" else ""
                    "Voy a llamar a ${chosen.displayName}, número ${formatSpokenNumber(chosen.phoneNumber)}$reasonCue. ¿Confirmas que inicie la llamada?"
                } else {
                    val req = synchronized(pendingCallLock) {
                        PendingCallRequest(
                            requestId = "req_${nextPendingRequestSeq++}",
                            contactName = action.rawTargetQuery,
                            phoneNumber = "",
                            reason = action.reason,
                            candidates = resolved.take(3),
                            awaitingConfirmation = false,
                            source = "VOICE"
                        ).also { pendingRequestSnapshot = it }
                    }
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

    // =========================================================================
    // ORION 0.7 — Asistente personal, tareas, recordatorios locales y seguimientos
    // =========================================================================

    fun setTaskFilter(filter: TaskFilter) {
        _uiState.update { it.copy(selectedTaskFilter = filter) }
    }

    fun onTaskSearchQueryChanged(query: String) {
        _uiState.update { it.copy(taskSearchQuery = query) }
    }

    fun createManualTask(
        title: String,
        description: String = "",
        dueDateEpochDay: Long? = null,
        dueTimeMinutes: Int? = null,
        reminderAtMs: Long? = null,
        reminderActive: Boolean = reminderAtMs != null,
        priority: TaskPriority = TaskPriority.NORMAL,
        category: String = "",
        notes: String = "",
        contactName: String? = null,
        contactKey: String? = null,
        contactPhone: String? = null,
        linkedCallRecordId: Long? = null
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val created = taskRepository.createTask(
                title = cleanTitle,
                description = description,
                dueDateEpochDay = dueDateEpochDay,
                dueTimeMinutes = dueTimeMinutes,
                reminderAtMs = reminderAtMs,
                reminderActive = reminderActive,
                priority = priority,
                category = category,
                notes = notes,
                contactName = contactName,
                contactKey = contactKey,
                contactPhone = contactPhone,
                linkedCallRecordId = linkedCallRecordId
            )
            lastReferencedTaskId = created.id
            _uiState.update {
                it.copy(taskStatusMessage = "Tarea «${created.title}» guardada en local.")
            }
        }
    }

    fun updateManualTask(
        id: Long,
        title: String,
        description: String = "",
        dueDateEpochDay: Long? = null,
        dueTimeMinutes: Int? = null,
        reminderAtMs: Long? = null,
        reminderActive: Boolean = reminderAtMs != null,
        priority: TaskPriority = TaskPriority.NORMAL,
        category: String = "",
        notes: String = "",
        contactName: String? = null,
        contactKey: String? = null,
        contactPhone: String? = null,
        linkedCallRecordId: Long? = null
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = taskRepository.updateTask(
                id = id,
                title = cleanTitle,
                description = description,
                dueDateEpochDay = dueDateEpochDay,
                dueTimeMinutes = dueTimeMinutes,
                reminderAtMs = reminderAtMs,
                reminderActive = reminderActive,
                priority = priority,
                category = category,
                notes = notes,
                contactName = contactName,
                contactKey = contactKey,
                contactPhone = contactPhone,
                linkedCallRecordId = linkedCallRecordId
            )
            if (updated != null) {
                lastReferencedTaskId = updated.id
                _uiState.update { it.copy(taskStatusMessage = "Tarea «${updated.title}» actualizada.") }
            }
        }
    }

    fun toggleTaskCompleted(task: TaskItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (task.status == TaskStatus.PENDING) {
                taskRepository.completeTask(task.id)
                _uiState.update { it.copy(taskStatusMessage = "«${task.title}» marcada como completada.") }
            } else {
                taskRepository.reopenTask(task.id)
                _uiState.update { it.copy(taskStatusMessage = "«${task.title}» reabierta como pendiente.") }
            }
        }
    }

    fun cancelTaskById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = taskRepository.cancelTask(id)
            if (updated != null) {
                _uiState.update { it.copy(taskStatusMessage = "«${updated.title}» cancelada.") }
            }
        }
    }

    fun postponeTaskTo(
        id: Long,
        newDueDateEpochDay: Long?,
        newDueTimeMinutes: Int?,
        newReminderAtMs: Long?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = taskRepository.postponeTask(
                id = id,
                newDueDateEpochDay = newDueDateEpochDay,
                newDueTimeMinutes = newDueTimeMinutes,
                newReminderAtMs = newReminderAtMs
            )
            if (updated != null) {
                lastReferencedTaskId = updated.id
                val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
                    updated.dueDateEpochDay,
                    updated.dueTimeMinutes,
                    updated.reminderAtMs
                )
                _uiState.update { it.copy(taskStatusMessage = "«${updated.title}» pospuesta ($whenStr).") }
            }
        }
    }

    fun snoozeTaskByMinutes(id: Long, minutes: Long = 60L) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = taskRepository.snoozeReminder(id, minutes)
            if (updated != null) {
                lastReferencedTaskId = updated.id
                _uiState.update { it.copy(taskStatusMessage = "Recordatorio aplazado $minutes min.") }
            }
        }
    }

    fun dismissTaskReminder(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.dismissReminder(id)
            _uiState.update { it.copy(taskStatusMessage = "Recordatorio desactivado; la tarea sigue pendiente.") }
        }
    }

    fun deleteTaskById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.deleteTask(id)
            if (lastReferencedTaskId == id) lastReferencedTaskId = null
            _uiState.update { it.copy(taskStatusMessage = "Tarea eliminada de forma permanente.") }
        }
    }

    fun deleteFinishedTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.deleteFinishedTasks()
            _uiState.update { it.copy(taskStatusMessage = "Tareas completadas y canceladas eliminadas.") }
        }
    }

    fun deleteAllTasks() {
        setPendingTaskPrompt(null)
        lastReferencedTaskId = null
        viewModelScope.launch(Dispatchers.IO) {
            taskRepository.deleteAllTasks()
            _uiState.update { it.copy(taskStatusMessage = "Todas las tareas y recordatorios eliminados.") }
        }
    }

    fun createFollowUpTaskFromCall(
        callRecord: PhoneCallRecord,
        title: String,
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        priority: TaskPriority,
        notes: String
    ) {
        val cleanTitle = title.trim().ifBlank { "Seguimiento con ${callRecord.contactName}" }
        viewModelScope.launch(Dispatchers.IO) {
            val created = taskRepository.createFollowUpForCall(
                callRecord = callRecord,
                title = cleanTitle,
                dueDateEpochDay = dueDateEpochDay,
                dueTimeMinutes = dueTimeMinutes,
                reminderAtMs = reminderAtMs,
                priority = priority,
                notes = notes
            )
            lastReferencedTaskId = created.id
            _uiState.update {
                it.copy(taskStatusMessage = "Seguimiento guardado para ${callRecord.contactName}.")
            }
        }
    }

    fun prepareCallFromTask(task: TaskItem, onReadyForPhoneTab: () -> Unit = {}) {
        val phone = task.contactPhone?.trim().orEmpty()
        val name = task.contactName?.trim().orEmpty()
        if (phone.isNotEmpty()) {
            val normNum = PhoneContactMatcher.normalizePhoneNumber(phone)
            val entry = PhoneContactEntry(
                contactKey = task.contactKey ?: "task:${task.id}:$normNum",
                displayName = name.ifBlank { phone },
                phoneNumber = phone,
                normalizedNumber = normNum,
                label = "Tarea",
                source = PhoneContactSource.MANUAL
            )
            preparePendingCallFromEntry(entry, reason = task.title, source = "TASK")
            onReadyForPhoneTab()
            return
        }
        if (name.isEmpty()) {
            _uiState.update { it.copy(taskStatusMessage = "Esta tarea no tiene un contacto o número asociado.") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val matches = phoneRepository.resolveVoiceContactMatches(name)
            when {
                matches.size == 1 -> {
                    preparePendingCallFromEntry(matches.first(), reason = task.title, source = "TASK")
                    onReadyForPhoneTab()
                }
                matches.size > 1 -> {
                    val req = synchronized(pendingCallLock) {
                        PendingCallRequest(
                            requestId = "req_${nextPendingRequestSeq++}",
                            contactName = name,
                            phoneNumber = "",
                            reason = task.title,
                            candidates = matches.take(3),
                            awaitingConfirmation = false,
                            source = "TASK"
                        ).also { pendingRequestSnapshot = it }
                    }
                    _uiState.update { it.copy(pendingCallRequest = req) }
                    onReadyForPhoneTab()
                }
                else -> {
                    _uiState.update {
                        it.copy(
                            phoneSearchQuery = name,
                            taskStatusMessage = "No hay teléfono guardado para $name. Búscalo en Teléfono."
                        )
                    }
                }
            }
        }
    }

    fun selectTaskDisambiguationCandidate(task: TaskItem) {
        val prompt = synchronized(pendingTaskLock) {
            (pendingTaskPromptSnapshot as? PendingTaskPrompt.DisambiguateTasks)?.also {
                pendingTaskPromptSnapshot = null
                _uiState.update { state -> state.copy(pendingTaskPrompt = null) }
            }
        } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            executeTaskPromptOperation(prompt, task)
        }
    }

    fun resolveTemporalChoice(chosenTimeMinutes: Int) {
        val prompt = synchronized(pendingTaskLock) {
            (pendingTaskPromptSnapshot as? PendingTaskPrompt.ClarifyTemporalAmbiguity)?.also {
                pendingTaskPromptSnapshot = null
                _uiState.update { state -> state.copy(pendingTaskPrompt = null) }
            }
        } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            createTaskFromTemporalChoice(prompt, chosenTimeMinutes)
        }
    }

    fun dismissPendingTaskPrompt() {
        setPendingTaskPrompt(null)
    }

    private fun setPendingTaskPrompt(prompt: PendingTaskPrompt?) {
        synchronized(pendingTaskLock) {
            pendingTaskPromptSnapshot = prompt
            _uiState.update { it.copy(pendingTaskPrompt = prompt) }
        }
    }

    private suspend fun handleVoiceTaskCommand(userText: String): String? {
        val currentPrompt = synchronized(pendingTaskLock) { pendingTaskPromptSnapshot }
        val action = TaskIntentRouter.route(userText, currentPrompt)
        return when (action) {
            TaskIntentAction.None -> null

            TaskIntentAction.CancelPendingPrompt -> {
                setPendingTaskPrompt(null)
                "De acuerdo, cancelo esa operación de tareas."
            }

            is TaskIntentAction.SelectTaskCandidateByIndex -> {
                val prompt = synchronized(pendingTaskLock) {
                    pendingTaskPromptSnapshot as? PendingTaskPrompt.DisambiguateTasks
                } ?: return null
                val candidate = prompt.candidates.getOrNull(action.index) ?: return null
                setPendingTaskPrompt(null)
                executeTaskPromptOperation(prompt, candidate)
            }

            is TaskIntentAction.SelectTaskCandidateByTitle -> {
                val prompt = synchronized(pendingTaskLock) {
                    pendingTaskPromptSnapshot as? PendingTaskPrompt.DisambiguateTasks
                } ?: return null
                val narrowed = taskRepository.findMatchingTasks(action.query, prompt.candidates, pendingOnly = false)
                if (narrowed.size == 1) {
                    setPendingTaskPrompt(null)
                    executeTaskPromptOperation(prompt, narrowed.first())
                } else {
                    "He encontrado varias coincidencias. Dime la primera o la segunda, o pulsa una en pantalla."
                }
            }

            is TaskIntentAction.ResolveTemporalChoice -> {
                val prompt = synchronized(pendingTaskLock) {
                    pendingTaskPromptSnapshot as? PendingTaskPrompt.ClarifyTemporalAmbiguity
                } ?: return null
                setPendingTaskPrompt(null)
                createTaskFromTemporalChoice(prompt, action.chosenTimeMinutes)
            }

            is TaskIntentAction.ProvideMissingTitle -> {
                val prompt = synchronized(pendingTaskLock) {
                    pendingTaskPromptSnapshot as? PendingTaskPrompt.ClarifyMissingTitle
                } ?: return null
                setPendingTaskPrompt(null)
                val created = taskRepository.createTask(
                    title = action.title,
                    dueDateEpochDay = prompt.dueDateEpochDay,
                    dueTimeMinutes = prompt.dueTimeMinutes,
                    reminderAtMs = prompt.reminderAtMs,
                    reminderActive = prompt.reminderAtMs != null,
                    priority = prompt.priority,
                    category = prompt.category
                )
                lastReferencedTaskId = created.id
                val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
                    created.dueDateEpochDay,
                    created.dueTimeMinutes,
                    created.reminderAtMs
                )
                "He apuntado «${created.title}» (${created.priority.label}, $whenStr)."
            }

            is TaskIntentAction.CreateTask -> {
                if (action.missingTitlePrompt != null) {
                    setPendingTaskPrompt(action.missingTitlePrompt)
                    return action.missingTitlePrompt.promptMessage
                }
                if (action.ambiguityPrompt != null) {
                    setPendingTaskPrompt(action.ambiguityPrompt)
                    return action.ambiguityPrompt.promptMessage
                }
                createTaskFromIntentAction(action)
            }

            is TaskIntentAction.QueryTasks -> answerTaskQuery(action.scope, action.specificDateEpochDay)

            is TaskIntentAction.CompleteTask -> {
                val all = taskRepository.getAllTasks()
                val matches = taskRepository.findMatchingTasks(action.query, all, pendingOnly = true)
                when {
                    matches.isEmpty() -> "No encuentro ninguna tarea pendiente que coincida con «${action.query}»."
                    matches.size == 1 -> {
                        val completed = taskRepository.completeTask(matches.first().id) ?: matches.first()
                        lastReferencedTaskId = completed.id
                        "Listo, he marcado «${completed.title}» como completada."
                    }
                    else -> {
                        val prompt = PendingTaskPrompt.DisambiguateTasks(
                            actionLabel = "Completar tarea",
                            operation = PendingTaskOperation.COMPLETE,
                            candidates = matches
                        )
                        setPendingTaskPrompt(prompt)
                        val opts = matches.mapIndexed { idx, t -> "${idx + 1}, ${t.title}" }.joinToString("; ")
                        "He encontrado varias tareas pendientes: $opts. ¿Cuál marco como completada?"
                    }
                }
            }

            is TaskIntentAction.PostponeTask -> {
                val all = taskRepository.getAllTasks()
                val targetPool = all.filter { it.status == TaskStatus.PENDING }
                val matches = if (action.refersToLastTask) {
                    val last = lastReferencedTaskId?.let { id -> targetPool.firstOrNull { it.id == id } }
                        ?: targetPool.firstOrNull()
                    listOfNotNull(last)
                } else {
                    taskRepository.findMatchingTasks(action.query, targetPool, pendingOnly = true)
                }
                when {
                    matches.isEmpty() -> "No tengo ninguna tarea pendiente para posponer."
                    matches.size == 1 -> {
                        val target = matches.first()
                        val updated = taskRepository.postponeTask(
                            id = target.id,
                            newDueDateEpochDay = action.newDueDateEpochDay,
                            newDueTimeMinutes = action.newDueTimeMinutes,
                            newReminderAtMs = action.newReminderAtMs
                        ) ?: target
                        lastReferencedTaskId = updated.id
                        val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
                            updated.dueDateEpochDay,
                            updated.dueTimeMinutes,
                            updated.reminderAtMs
                        )
                        "He pospuesto «${updated.title}» para $whenStr."
                    }
                    else -> {
                        val prompt = PendingTaskPrompt.DisambiguateTasks(
                            actionLabel = "Posponer tarea",
                            operation = PendingTaskOperation.POSTPONE,
                            candidates = matches,
                            targetDueDateEpochDay = action.newDueDateEpochDay,
                            targetDueTimeMinutes = action.newDueTimeMinutes,
                            targetReminderAtMs = action.newReminderAtMs
                        )
                        setPendingTaskPrompt(prompt)
                        val opts = matches.mapIndexed { idx, t -> "${idx + 1}, ${t.title}" }.joinToString("; ")
                        "He encontrado varias tareas: $opts. ¿Cuál quieres posponer?"
                    }
                }
            }

            is TaskIntentAction.CancelTask -> {
                val all = taskRepository.getAllTasks()
                val matches = taskRepository.findMatchingTasks(action.query, all, pendingOnly = true)
                when {
                    matches.isEmpty() -> "No encuentro ningún recordatorio o tarea pendiente de «${action.query}»."
                    matches.size == 1 -> {
                        val cancelled = taskRepository.cancelTask(matches.first().id) ?: matches.first()
                        "He cancelado «${cancelled.title}» y su recordatorio."
                    }
                    else -> {
                        val prompt = PendingTaskPrompt.DisambiguateTasks(
                            actionLabel = "Cancelar tarea",
                            operation = PendingTaskOperation.CANCEL,
                            candidates = matches
                        )
                        setPendingTaskPrompt(prompt)
                        val opts = matches.mapIndexed { idx, t -> "${idx + 1}, ${t.title}" }.joinToString("; ")
                        "Hay varias tareas parecidas: $opts. ¿Cuál cancelo?"
                    }
                }
            }
        }
    }

    private suspend fun createTaskFromIntentAction(action: TaskIntentAction.CreateTask): String {
        val resolvedContact = action.contactQuery?.let { query ->
            val matches = phoneRepository.resolveVoiceContactMatches(query)
            if (matches.size == 1) matches.first() else null
        }
        val activeOrLatestCall = if (action.linkLatestOrActiveCall) {
            val activeId = activeTelephonyCallRecordId
            if (activeId != null) {
                phoneRepository.getAllCallRecords().firstOrNull { it.id == activeId }
            } else {
                phoneRepository.getLatestCallRecord()
            }
        } else {
            null
        }
        val fallbackContactName = resolvedContact?.displayName
            ?: activeOrLatestCall?.contactName
            ?: action.contactQuery?.replaceFirstChar { it.uppercase() }

        val created = taskRepository.createTask(
            title = action.title,
            dueDateEpochDay = action.dueDateEpochDay,
            dueTimeMinutes = action.dueTimeMinutes,
            reminderAtMs = action.reminderAtMs,
            reminderActive = action.reminderAtMs != null,
            priority = action.priority,
            category = action.category,
            contactName = fallbackContactName,
            contactKey = resolvedContact?.contactKey,
            contactPhone = resolvedContact?.phoneNumber ?: activeOrLatestCall?.phoneNumber,
            linkedCallRecordId = activeOrLatestCall?.id
        )
        lastReferencedTaskId = created.id
        val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
            created.dueDateEpochDay,
            created.dueTimeMinutes,
            created.reminderAtMs
        )
        val reminderCue = if (created.reminderActive) " con recordatorio" else ""
        val callCue = if (activeOrLatestCall != null) {
            " vinculada a la llamada con ${activeOrLatestCall.contactName}"
        } else if (resolvedContact != null) {
            " asociada a ${resolvedContact.displayName}"
        } else {
            ""
        }
        return "Apuntado$reminderCue$callCue: «${created.title}» ($whenStr)."
    }

    private suspend fun createTaskFromTemporalChoice(
        prompt: PendingTaskPrompt.ClarifyTemporalAmbiguity,
        chosenTimeMinutes: Int
    ): String {
        val zone = java.time.ZoneId.systemDefault()
        val date = java.time.LocalDate.ofEpochDay(prompt.dateEpochDay)
        val time = java.time.LocalTime.of(
            (chosenTimeMinutes / 60).coerceIn(0, 23),
            (chosenTimeMinutes % 60).coerceIn(0, 59)
        )
        val reminderAtMs = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return createTaskFromIntentAction(
            TaskIntentAction.CreateTask(
                title = prompt.draftTitle,
                priority = prompt.priority,
                dueDateEpochDay = prompt.dateEpochDay,
                dueTimeMinutes = chosenTimeMinutes,
                reminderAtMs = reminderAtMs,
                category = prompt.category,
                contactQuery = prompt.contactQuery,
                isCallFollowUp = prompt.isCallFollowUp,
                linkLatestOrActiveCall = prompt.linkLatestOrActiveCall
            )
        )
    }

    private suspend fun executeTaskPromptOperation(
        prompt: PendingTaskPrompt.DisambiguateTasks,
        task: TaskItem
    ): String {
        return when (prompt.operation) {
            PendingTaskOperation.COMPLETE -> {
                val updated = taskRepository.completeTask(task.id) ?: task
                lastReferencedTaskId = updated.id
                "Listo, he marcado «${updated.title}» como completada."
            }
            PendingTaskOperation.POSTPONE -> {
                val updated = taskRepository.postponeTask(
                    id = task.id,
                    newDueDateEpochDay = prompt.targetDueDateEpochDay,
                    newDueTimeMinutes = prompt.targetDueTimeMinutes,
                    newReminderAtMs = prompt.targetReminderAtMs
                ) ?: task
                lastReferencedTaskId = updated.id
                val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
                    updated.dueDateEpochDay,
                    updated.dueTimeMinutes,
                    updated.reminderAtMs
                )
                "He pospuesto «${updated.title}» para $whenStr."
            }
            PendingTaskOperation.CANCEL -> {
                val updated = taskRepository.cancelTask(task.id) ?: task
                "He cancelado «${updated.title}»."
            }
        }
    }

    private suspend fun answerTaskQuery(
        scope: TaskQueryScope,
        specificDateEpochDay: Long?
    ): String {
        val all = taskRepository.getAllTasks()
        val matching = when (scope) {
            TaskQueryScope.TODAY -> taskRepository.filterTasks(all, TaskFilter.TODAY)
            TaskQueryScope.UPCOMING -> taskRepository.filterTasks(all, TaskFilter.UPCOMING)
            TaskQueryScope.OVERDUE -> taskRepository.filterTasks(all, TaskFilter.OVERDUE)
            TaskQueryScope.REMINDERS -> taskRepository.filterTasks(all, TaskFilter.REMINDERS)
            TaskQueryScope.ALL_PENDING -> taskRepository.filterTasks(all, TaskFilter.PENDING)
            TaskQueryScope.SPECIFIC_DATE -> {
                val targetDay = specificDateEpochDay
                if (targetDay == null) {
                    taskRepository.filterTasks(all, TaskFilter.UPCOMING)
                } else {
                    all.filter { it.status == TaskStatus.PENDING && it.isDueOnDate(targetDay) }
                }
            }
        }
        if (matching.isEmpty()) {
            return when (scope) {
                TaskQueryScope.TODAY -> "No tienes tareas pendientes para hoy."
                TaskQueryScope.OVERDUE -> "No tienes tareas atrasadas ahora mismo."
                TaskQueryScope.REMINDERS -> "No tienes recordatorios activos en este momento."
                TaskQueryScope.UPCOMING -> "No tienes tareas próximas programadas."
                TaskQueryScope.SPECIFIC_DATE -> "No tienes tareas apuntadas para ${SpanishDateTimeParser.formatDateLabel(specificDateEpochDay)}."
                TaskQueryScope.ALL_PENDING -> "No tienes ninguna tarea pendiente."
            }
        }
        if (matching.size == 1) {
            lastReferencedTaskId = matching.first().id
        }
        val header = when (scope) {
            TaskQueryScope.TODAY -> "Para hoy tienes ${matching.size} tarea(s): "
            TaskQueryScope.OVERDUE -> "Tienes ${matching.size} tarea(s) atrasada(s): "
            TaskQueryScope.REMINDERS -> "Tienes ${matching.size} recordatorio(s) activo(s): "
            TaskQueryScope.UPCOMING -> "Tienes ${matching.size} tarea(s) próxima(s): "
            TaskQueryScope.SPECIFIC_DATE -> "Para ${SpanishDateTimeParser.formatDateLabel(specificDateEpochDay)} tienes ${matching.size}: "
            TaskQueryScope.ALL_PENDING -> "Tienes ${matching.size} tarea(s) pendiente(s): "
        }
        val summary = matching.take(4).joinToString("; ") { t ->
            val whenStr = SpanishDateTimeParser.formatDateTimeSummary(t.dueDateEpochDay, t.dueTimeMinutes, t.reminderAtMs)
            "${t.title} ($whenStr)"
        }
        return "$header$summary."
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
        cancelDelayedVoiceConfirmJob(clearPending = false)
        if (conversationEngine.isCallActive()) {
            endCall()
        }
        if (engine.isRunning) {
            stopListening()
        }
    }

    private fun recordError(message: String, error: Throwable? = null) {
        val clean = message.trim()
        if (clean.isEmpty()) return

        // Existing call sites identify their subsystem as "Component: detail".
        // Keep that origin visible to users and persist a correlated technical record.
        val separator = clean.indexOf(':')
        val explicitComponent = separator in 1..64
        val component = if (explicitComponent) {
            clean.substring(0, separator).trim()
        } else {
            val normalized = clean.lowercase()
            when {
                "record_audio" in normalized || "micrófono" in normalized || "microfono" in normalized -> "ASR permisos"
                "notificación" in normalized || "notificacion" in normalized || "alarma" in normalized -> "Tareas"
                "contact" in normalized || "telefon" in normalized || "llamada" in normalized -> "Telefonía"
                "calendar" in normalized || "gmail" in normalized || "github" in normalized || "maps" in normalized || "keep" in normalized -> "Conectores"
                "modelo" in normalized || "llm" in normalized || "qwen" in normalized -> "LLM"
                "tts" in normalized || "piper" in normalized || "voz" in normalized -> "Voz TTS"
                "asr" in normalized || "moonshine" in normalized -> "ASR"
                else -> "Aplicación"
            }
        }
        val cause = if (explicitComponent) clean.substring(separator + 1).trim() else clean
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val eventId = "OR-${System.currentTimeMillis()}"
        val formatted = "[$timestamp] [$eventId] [$component] $cause"

        OrionCrashDiagnostics.recordOperationalError(
            context = appContext,
            eventId = eventId,
            component = component,
            message = cause,
            error = error
        )
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

    /**
     * Writes the diagnostic snapshot to a user-selected document using Android's Storage Access Framework.
     * The export deliberately excludes conversation contents, transcripts, contact names and phone numbers.
     */
    fun writeReport(uri: Uri): Boolean = runCatching {
        appContext.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(buildReport())
        } ?: error("No se pudo abrir el destino de exportación")
    }.onFailure { error ->
        _uiState.update { current ->
            current.copy(recentErrors = (current.recentErrors + "Exportación de diagnóstico: ${error.message ?: error.javaClass.simpleName}").takeLast(MAX_RECENT_ERRORS))
        }
    }.isSuccess

    fun buildReport(): String {
        val state = _uiState.value
        val now = DeviceMetrics.snapshot(appContext, appContext.filesDir)
        val llmMb = ModelManager.llmSizeMb(appContext.filesDir)
        val ttsMb = ModelManager.ttsSizeMb(appContext.filesDir)
        val callTel = state.callTelemetry
        val hw = state.audioHardwareStatus
        return buildString {
            val versionName = runCatching {
                appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
            }.getOrNull() ?: "desconocida"
            appendLine("ORION Voice Lab $versionName — informe técnico de diagnóstico")
            appendLine("Generado: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())}")
            appendLine("Formato: texto UTF-8 · exportación local solicitada por el usuario")
            appendLine("Privacidad: no se incluyen intencionadamente transcripciones, nombres ni números de contacto; los registros de errores pueden contener detalles técnicos de excepciones.")
            appendLine(DeviceMetrics.deviceSummary())
            appendLine(
                "Telefonía local: soporte=${state.isTelephonySupported} · permisoContactos=${state.hasContactsPermission} · " +
                    "permisoLlamar=${state.hasCallPhonePermission} · permisoEstado=${state.hasPhoneStatePermission} · " +
                    "favoritos=${state.favoritePhoneContacts.size} · llamadas=${state.phoneCallRecords.size} · estado=${state.systemTelephonyState.displayLabel}"
            )
            val overdueCount = state.tasks.count { it.isOverdue(System.currentTimeMillis()) }
            val pendingCount = state.tasks.count { it.status == TaskStatus.PENDING }
            val activeRemCount = state.tasks.count { it.status == TaskStatus.PENDING && it.reminderActive }
            appendLine(
                "Tareas locales: total=${state.tasks.size} · pendientes=$pendingCount · atrasadas=$overdueCount · " +
                    "recordatorios=$activeRemCount · permisoNotificaciones=${state.hasNotificationsPermission}"
            )
            appendLine(
                "Memoria local: conversaciones=${state.conversations.size} · recuerdos=${state.memories.size} · " +
                    "memoria=${if (state.assistantIdentity.memoryEnabled) "activa" else "desactivada"}"
            )
            val crashLog = java.io.File(appContext.filesDir, "orion_diagnostics.log")
            if (crashLog.exists()) {
                appendLine("Diagnóstico de cierres y presión de memoria (registro local):")
                appendLine(crashLog.readText().takeLast(256 * 1024))
            } else {
                appendLine("Diagnóstico de cierres: sin eventos registrados todavía")
            }
            appendLine(
                "Descarga automática inicial: activa=${state.isAutoDownloadingInitialModels} · " +
                    "error=${state.autoDownloadError ?: "ninguno"}"
            )
            appendLine(
                "Estado módulos: ASR=${state.modelState} · LLM=${state.llmModelState} · TTS=${state.ttsModelState}"
            )
            appendLine(
                "Estado aplicación: escucha=${state.listeningState} · conversación=${state.conversationState} · " +
                    "llamadaORION=${state.isCallActive} · ocupado=${state.isBusy}"
            )
            appendLine("Errores recientes en memoria: ${state.recentErrors.size}")
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
        cancelDelayedVoiceConfirmJob(clearPending = false)
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

