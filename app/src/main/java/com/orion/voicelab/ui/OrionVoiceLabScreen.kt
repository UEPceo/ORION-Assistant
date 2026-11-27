package com.orion.voicelab.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.PhoneInTalk
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.orion.voicelab.R
import com.orion.voicelab.data.ConversationState
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.ListeningState
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.TaskItem
import com.orion.voicelab.data.TranscriptEntry
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.ui.theme.OrionNavyGlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.Locale

private enum class OrionTab {
    CALL,
    PHONE,
    TASKS,
    CONVERSATIONS,
    MEMORY,
    CONNECTORS,
    LAB,
    SETTINGS
}

private enum class MicPermissionTarget {
    CALL,
    LAB
}

@Composable
fun OrionVoiceLabScreen(
    viewModel: VoiceLabViewModel,
    initialTabName: String? = null
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val reportExportedMsg = stringResource(R.string.report_exported)
    val reportExportFailedMsg = stringResource(R.string.report_export_failed)
    val createReportDocument = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val saved = viewModel.writeReport(uri)
            coroutineScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(if (saved) reportExportedMsg else reportExportFailedMsg)
            }
        }
    }
    val memorySavedMsg = stringResource(R.string.memory_saved_feedback)
    val identitySavedMsg = stringResource(R.string.identity_saved_feedback)
    val identityResetMsg = stringResource(R.string.identity_reset_feedback)

    val lifecycleOwner = LocalLifecycleOwner.current
    var selectedTab by rememberSaveable {
        mutableStateOf(
            if (initialTabName == "TASKS") OrionTab.TASKS else OrionTab.CALL
        )
    }
    LaunchedEffect(initialTabName) {
        if (initialTabName == "TASKS") {
            selectedTab = OrionTab.TASKS
        }
    }
    var pendingPermissionTarget by remember { mutableStateOf(MicPermissionTarget.CALL) }
    var executePendingAfterCallPerm by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPhonePermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = selectedTab != OrionTab.CALL || (state.isInCallScreen && !state.isCallActive)) {
        if (selectedTab != OrionTab.CALL) {
            selectedTab = OrionTab.CALL
        } else if (state.isInCallScreen && !state.isCallActive) {
            viewModel.returnToAgenda()
        }
    }

    val showToast: (String) -> Unit = { message ->
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (pendingPermissionTarget) {
                MicPermissionTarget.CALL -> viewModel.startCall()
                MicPermissionTarget.LAB -> viewModel.startListening()
            }
        } else {
            viewModel.onPermissionDenied()
        }
    }

    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPhonePermissions()
        if (!granted) {
            showToast("Permiso de agenda no concedido; puedes usar el selector de contacto.")
        }
    }

    val callPhonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPhonePermissions()
        if (executePendingAfterCallPerm) {
            executePendingAfterCallPerm = false
            // Si no se concede CALL_PHONE, cae con seguridad al marcador nativo (ACTION_DIAL)
            viewModel.confirmAndExecutePendingCall(preferDirectCall = granted)
        } else if (!granted) {
            showToast("Permiso de llamada directa no concedido; se usará el marcador del teléfono.")
        }
    }

    val phoneStatePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshPhonePermissions()
        if (!granted) {
            showToast("Permiso de estado telefónico no concedido; la duración automática no estará disponible.")
        }
    }

    val notificationsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.refreshNotificationPermission()
        if (!granted) {
            showToast("Permiso de notificaciones denegado; los recordatorios se guardan en Tareas pero no mostrarán avisos.")
        }
    }

    val requestNotificationsPermission: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.refreshNotificationPermission()
        }
    }

    val nativeContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onNativeContactPicked(result.data?.data)
        }
    }

    val onPickNativeContact: () -> Unit = {
        val intent = Intent(Intent.ACTION_PICK).apply {
            type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE
        }
        try {
            nativeContactPickerLauncher.launch(intent)
        } catch (_: Exception) {
            showToast("No hay selector de contactos disponible en este dispositivo.")
        }
    }

    val onConfirmDirectPhoneCall: () -> Unit = {
        val hasPerm = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CALL_PHONE
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPerm) {
            viewModel.confirmAndExecutePendingCall(preferDirectCall = true)
        } else {
            executePendingAfterCallPerm = true
            callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val requestMicAndRun: (MicPermissionTarget, () -> Unit) -> Unit = { target, action ->
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            pendingPermissionTarget = target
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val onStartLabClicked: () -> Unit = {
        requestMicAndRun(MicPermissionTarget.LAB) { viewModel.startListening() }
    }

    val onStartCallClicked: () -> Unit = {
        requestMicAndRun(MicPermissionTarget.CALL) { viewModel.startCall() }
    }

    val onCallContactClicked: (Long) -> Unit = { contactId ->
        if (!state.areAllConversationalModelsReady) {
            viewModel.callContact(contactId)
        } else {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.callContact(contactId)
            } else {
                viewModel.openContactCallScreen(contactId)
                pendingPermissionTarget = MicPermissionTarget.CALL
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val onCopyReportClicked: () -> Unit = {
        createReportDocument.launch("ORION-informe-diagnostico.txt")
    }

    val bgColor = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(OrionNavyGlow, Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.14f),
                    radius = size.maxDimension * 0.55f
                )
            )
        }

        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                TopBrandAndTabs(
                    selectedTab = selectedTab,
                    onSelectTab = { tab ->
                        if (tab == OrionTab.CALL && selectedTab == OrionTab.CALL && state.isInCallScreen && !state.isCallActive) {
                            viewModel.returnToAgenda()
                        } else {
                            selectedTab = tab
                        }
                    }
                )

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    val isWide = maxWidth >= 720.dp
                    val onEditAndSaveSuggestion: (String, MemoryCategory, String, Int) -> Unit =
                        { suggestionId, category, content, importance ->
                            viewModel.dismissMemorySuggestion(suggestionId)
                            viewModel.addManualMemory(category, content, importance)
                            showToast(memorySavedMsg)
                        }
                    when (selectedTab) {
                        OrionTab.CALL -> {
                            if (state.isInCallScreen || state.isCallActive) {
                                VirtualCallScreen(
                                    state = state,
                                    audioLevelFlow = viewModel.audioLevel,
                                    isWide = isWide,
                                    onBackToAgenda = viewModel::returnToAgenda,
                                    onStartCall = onStartCallClicked,
                                    onEndCall = viewModel::endCall,
                                    onToggleMute = viewModel::toggleMute,
                                    onToggleSpeaker = viewModel::toggleSpeaker,
                                    onInterrupt = viewModel::interruptCall,
                                    onSelectVoice = viewModel::selectVoice,
                                    onReleaseRam = viewModel::releaseLoadedModels,
                                    onNewConversation = viewModel::createNewConversation,
                                    onOpenConversations = { selectedTab = OrionTab.CONVERSATIONS },
                                    onAcceptSuggestion = { id ->
                                        viewModel.acceptMemorySuggestion(id)
                                        showToast(memorySavedMsg)
                                    },
                                    onEditAndSaveSuggestion = onEditAndSaveSuggestion,
                                    onDismissSuggestion = viewModel::dismissMemorySuggestion,
                                    onSelectPhoneCandidate = viewModel::selectDisambiguationCandidate,
                                    onConfirmDirectPhoneCall = onConfirmDirectPhoneCall,
                                    onOpenPhoneDialer = { viewModel.confirmAndExecutePendingCall(preferDirectCall = false) },
                                    onCancelPhoneCall = viewModel::cancelPendingCall,
                                    onSelectTaskCandidate = viewModel::selectTaskDisambiguationCandidate,
                                    onResolveTaskTemporalChoice = viewModel::resolveTemporalChoice,
                                    onDismissTaskPrompt = viewModel::dismissPendingTaskPrompt,
                                    onCopyReport = onCopyReportClicked
                                )
                            } else {
                                ContactsAgendaScreen(
                                    state = state,
                                    onOpenContact = viewModel::openContactCallScreen,
                                    onCallContact = onCallContactClicked,
                                    onRenamePrimary = viewModel::renamePrimaryAssistant,
                                    onAddCoworker = viewModel::addCoworker,
                                    onUpdateCoworker = viewModel::updateCoworker,
                                    onDeleteCoworker = viewModel::deleteCoworker,
                                    onResumeLastChat = viewModel::openActiveCallScreen,
                                    onPauseAutoDownload = viewModel::pauseInitialDownloads,
                                    onRetryAutoDownload = viewModel::retryInitialDownloads
                                )
                            }
                        }

                        OrionTab.PHONE -> {
                            PhoneSectionScreen(
                                state = state,
                                onSearchQueryChanged = viewModel::onPhoneSearchQueryChanged,
                                onRequestContactsPermission = {
                                    contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                },
                                onRequestCallPhonePermission = {
                                    executePendingAfterCallPerm = false
                                    callPhonePermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                                },
                                onRequestPhoneStatePermission = {
                                    phoneStatePermissionLauncher.launch(Manifest.permission.READ_PHONE_STATE)
                                },
                                onPickNativeContact = onPickNativeContact,
                                onToggleFavorite = viewModel::toggleFavoriteContact,
                                onRequestCallContact = { entry -> viewModel.preparePendingCallFromEntry(entry) },
                                onPrepareManualCall = viewModel::preparePendingCallManual,
                                onSavePreparedCallOnly = viewModel::savePreparedCallOnly,
                                onSelectCandidate = viewModel::selectDisambiguationCandidate,
                                onConfirmDirectCall = onConfirmDirectPhoneCall,
                                onOpenDialer = { viewModel.confirmAndExecutePendingCall(preferDirectCall = false) },
                                onCancelPendingCall = viewModel::cancelPendingCall,
                                onRedialRecord = viewModel::prepareRedialFromRecord,
                                onUpdateRecordNotes = viewModel::updateCallRecordNotes,
                                onDeleteRecord = viewModel::deleteCallRecord,
                                onCreateFollowUpTask = { record, title, day, mins, remMs, prio, notes ->
                                    viewModel.createFollowUpTaskFromCall(record, title, day, mins, remMs, prio, notes)
                                    showToast("Seguimiento guardado en Tareas.")
                                },
                                onRequestNotificationsPermission = requestNotificationsPermission
                            )
                        }

                        OrionTab.TASKS -> {
                            TasksSectionScreen(
                                state = state,
                                onSelectFilter = viewModel::setTaskFilter,
                                onSearchQueryChanged = viewModel::onTaskSearchQueryChanged,
                                onRequestNotificationsPermission = requestNotificationsPermission,
                                onCreateTask = viewModel::createManualTask,
                                onUpdateTask = viewModel::updateManualTask,
                                onToggleCompleted = viewModel::toggleTaskCompleted,
                                onCancelTask = viewModel::cancelTaskById,
                                onPostponeTask = viewModel::postponeTaskTo,
                                onSnoozeTask = viewModel::snoozeTaskByMinutes,
                                onDismissReminder = viewModel::dismissTaskReminder,
                                onDeleteTask = viewModel::deleteTaskById,
                                onDeleteFinished = viewModel::deleteFinishedTasks,
                                onDeleteAll = viewModel::deleteAllTasks,
                                onCallTaskContact = { task ->
                                    viewModel.prepareCallFromTask(task) {
                                        selectedTab = OrionTab.PHONE
                                    }
                                },
                                onSelectDisambiguationCandidate = viewModel::selectTaskDisambiguationCandidate,
                                onResolveTemporalChoice = viewModel::resolveTemporalChoice,
                                onDismissPendingPrompt = viewModel::dismissPendingTaskPrompt
                            )
                        }

                        OrionTab.CONVERSATIONS -> {
                            ConversationsHistoryScreen(
                                state = state,
                                onCreateNewConversation = {
                                    viewModel.createNewConversation()
                                    viewModel.openActiveCallScreen()
                                    selectedTab = OrionTab.CALL
                                },
                                onSelectConversation = { id ->
                                    viewModel.selectConversation(id)
                                    viewModel.openActiveCallScreen()
                                    selectedTab = OrionTab.CALL
                                },
                                onDeleteConversation = viewModel::deleteConversation
                            )
                        }

                        OrionTab.MEMORY -> {
                            MemoryAndIdentityScreen(
                                state = state,
                                onSetMemoryEnabled = viewModel::setMemoryEnabled,
                                onAddMemory = { category, content, importance ->
                                    viewModel.addManualMemory(category, content, importance)
                                    showToast(memorySavedMsg)
                                },
                                onUpdateMemory = viewModel::updateMemory,
                                onDeleteMemory = viewModel::deleteMemory,
                                onClearAllMemories = viewModel::clearAllMemories,
                                onAcceptSuggestion = { id ->
                                    viewModel.acceptMemorySuggestion(id)
                                    showToast(memorySavedMsg)
                                },
                                onEditAndSaveSuggestion = onEditAndSaveSuggestion,
                                onDismissSuggestion = viewModel::dismissMemorySuggestion,
                                onSaveIdentity = { identity ->
                                    viewModel.saveAssistantIdentity(identity)
                                    showToast(identitySavedMsg)
                                },
                                onResetIdentity = {
                                    viewModel.resetAssistantIdentity()
                                    showToast(identityResetMsg)
                                }
                            )
                        }

                        OrionTab.CONNECTORS -> {
                            ConnectorsSectionScreen()
                        }

                        OrionTab.LAB -> {
                            if (isWide) {
                                WideVoiceLabLayout(
                                    state = state,
                                    audioLevelFlow = viewModel.audioLevel,
                                    onDownloadClick = viewModel::downloadModel,
                                    onStartClick = onStartLabClicked,
                                    onStopClick = viewModel::stopListening,
                                    onMeasureClick = viewModel::refreshMetrics,
                                    onCopyReportClick = onCopyReportClicked,
                                    onClearClick = viewModel::resetMeasurements
                                )
                            } else {
                                CompactVoiceLabLayout(
                                    state = state,
                                    audioLevelFlow = viewModel.audioLevel,
                                    onDownloadClick = viewModel::downloadModel,
                                    onStartClick = onStartLabClicked,
                                    onStopClick = viewModel::stopListening,
                                    onMeasureClick = viewModel::refreshMetrics,
                                    onCopyReportClick = onCopyReportClicked,
                                    onClearClick = viewModel::resetMeasurements
                                )
                            }
                        }

                        OrionTab.SETTINGS -> {
                            OrionSettingsScreen(
                                state = state,
                                onSelectVoice = viewModel::selectVoice,
                                onSaveIdentity = viewModel::saveAssistantIdentity,
                                onSetMemoryEnabled = viewModel::setMemoryEnabled,
                                onOpenDiagnostics = { selectedTab = OrionTab.LAB }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopBrandAndTabs(
    selectedTab: OrionTab,
    onSelectTab: (OrionTab) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.brand_title),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(R.string.brand_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Surface(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useScrollableTabs = maxWidth < 460.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (useScrollableTabs) {
                                Modifier.horizontalScroll(rememberScrollState())
                            } else {
                                Modifier
                            }
                        )
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val tabItemMod: (String) -> Modifier = { tag ->
                        if (useScrollableTabs) {
                            Modifier
                                .widthIn(min = 64.dp)
                                .testTag(tag)
                        } else {
                            Modifier
                                .weight(1f)
                                .testTag(tag)
                        }
                    }
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.CALL,
                        icon = Icons.Outlined.PhoneInTalk,
                        label = "Contactos",
                        onClick = { onSelectTab(OrionTab.CALL) },
                        modifier = tabItemMod("tab_call")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.PHONE,
                        icon = Icons.Outlined.Phone,
                        label = stringResource(R.string.tab_phone),
                        onClick = { onSelectTab(OrionTab.PHONE) },
                        modifier = tabItemMod("tab_phone")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.TASKS,
                        icon = Icons.Outlined.CheckCircle,
                        label = stringResource(R.string.tab_tasks),
                        onClick = { onSelectTab(OrionTab.TASKS) },
                        modifier = tabItemMod("tab_tasks")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.CONVERSATIONS,
                        icon = Icons.Outlined.Forum,
                        label = stringResource(R.string.tab_conversations),
                        onClick = { onSelectTab(OrionTab.CONVERSATIONS) },
                        modifier = tabItemMod("tab_conversations")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.MEMORY,
                        icon = Icons.Outlined.Psychology,
                        label = stringResource(R.string.tab_memory),
                        onClick = { onSelectTab(OrionTab.MEMORY) },
                        modifier = tabItemMod("tab_memory")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.CONNECTORS,
                        icon = Icons.Outlined.Lock,
                        label = stringResource(R.string.tab_connectors),
                        onClick = { onSelectTab(OrionTab.CONNECTORS) },
                        modifier = tabItemMod("tab_connectors")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.LAB,
                        icon = Icons.Outlined.Science,
                        label = stringResource(R.string.tab_lab),
                        onClick = { onSelectTab(OrionTab.LAB) },
                        modifier = tabItemMod("tab_lab")
                    )
                    TabSegmentButton(
                        selected = selectedTab == OrionTab.SETTINGS,
                        icon = Icons.Outlined.Settings,
                        label = "Ajustes",
                        onClick = { onSelectTab(OrionTab.SETTINGS) },
                        modifier = tabItemMod("tab_settings")
                    )
                }
            }
        }
    }
}

@Composable
private fun TabSegmentButton(
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fgColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .semantics { this.selected = selected }
            .clickable(role = Role.Tab, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = fgColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VirtualCallScreen(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>,
    isWide: Boolean,
    onBackToAgenda: () -> Unit,
    onStartCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onInterrupt: () -> Unit,
    onSelectVoice: (String) -> Unit,
    onReleaseRam: () -> Unit,
    onNewConversation: () -> Unit,
    onOpenConversations: () -> Unit,
    onAcceptSuggestion: (String) -> Unit,
    onEditAndSaveSuggestion: (String, MemoryCategory, String, Int) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onSelectPhoneCandidate: (PhoneContactEntry) -> Unit,
    onConfirmDirectPhoneCall: () -> Unit,
    onOpenPhoneDialer: () -> Unit,
    onCancelPhoneCall: () -> Unit,
    onSelectTaskCandidate: (TaskItem) -> Unit,
    onResolveTaskTemporalChoice: (Int) -> Unit,
    onDismissTaskPrompt: () -> Unit,
    onCopyReport: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(
        state.conversationTurns.size,
        state.streamingAssistantText,
        state.pendingMemorySuggestions.size,
        state.pendingCallRequest,
        state.pendingTaskPrompt
    ) {
        val hasFeedContent = state.conversationTurns.isNotEmpty() || state.streamingAssistantText.isNotBlank()
        if (hasFeedContent) {
            val lastIdx = (listState.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
            if (lastIdx > 0) {
                listState.animateScrollToItem(lastIdx)
            }
        }
    }

    val activeContact = state.activeContact
    val assistantDisplayName = activeContact.name.ifBlank { "ORION" }

    if (isWide) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActiveContactCallHeaderBar(
                        contactName = assistantDisplayName,
                        contactRole = activeContact.roleTitle,
                        canGoBack = !state.isCallActive,
                        onBackToAgenda = onBackToAgenda
                    )
                    OrionPresenceStage(state = state, audioLevelFlow = audioLevelFlow)
                    CallVoiceAndRamControls(
                        state = state,
                        onSelectVoice = onSelectVoice,
                        onReleaseRam = onReleaseRam
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                VirtualCallBar(
                    state = state,
                    onStartCall = onStartCall,
                    onEndCall = onEndCall,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onInterrupt = onInterrupt
                )
            }

            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CallFeedHeaderRow(
                    conversationTitle = state.activeConversationTitle,
                    memoryEnabled = state.assistantIdentity.memoryEnabled,
                    onNewConversation = onNewConversation,
                    onOpenConversations = onOpenConversations,
                    onCopyReport = onCopyReport
                )
                state.pendingCallRequest?.let { pendingCall ->
                    PendingCallConfirmationCard(
                        pendingCall = pendingCall,
                        hasCallPhonePermission = state.hasCallPhonePermission,
                        onSelectCandidate = onSelectPhoneCandidate,
                        onConfirmDirectCall = onConfirmDirectPhoneCall,
                        onOpenDialer = onOpenPhoneDialer,
                        onCancel = onCancelPhoneCall
                    )
                }
                state.pendingTaskPrompt?.let { taskPrompt ->
                    PendingTaskPromptCard(
                        prompt = taskPrompt,
                        onSelectCandidate = onSelectTaskCandidate,
                        onResolveTemporalChoice = onResolveTaskTemporalChoice,
                        onDismiss = onDismissTaskPrompt
                    )
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    if (state.pendingMemorySuggestions.isNotEmpty()) {
                        item(key = "call_memory_suggestions") {
                            PendingMemorySuggestionsCard(
                                suggestions = state.pendingMemorySuggestions,
                                onAcceptSuggestion = onAcceptSuggestion,
                                onEditAndSaveSuggestion = onEditAndSaveSuggestion,
                                onDismissSuggestion = onDismissSuggestion
                            )
                        }
                    }
                    if (state.conversationTurns.isEmpty() && state.streamingAssistantText.isBlank()) {
                        item(key = "empty_call") {
                            EmptyCallHintCard(modelsReady = state.areAllConversationalModelsReady)
                        }
                    } else {
                        items(items = state.conversationTurns, key = { it.id }) { turn ->
                            OrionConversationTurnCard(
                                turn = turn,
                                assistantName = assistantDisplayName
                            )
                        }
                        if (state.streamingAssistantText.isNotBlank()) {
                            item(key = "streaming_reply") {
                                OrionStreamingReplyCard(
                                    text = state.streamingAssistantText,
                                    assistantName = assistantDisplayName
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(key = "active_contact_bar") {
                    ActiveContactCallHeaderBar(
                        contactName = assistantDisplayName,
                        contactRole = activeContact.roleTitle,
                        canGoBack = !state.isCallActive,
                        onBackToAgenda = onBackToAgenda
                    )
                }
                item(key = "presence") {
                    OrionPresenceStage(state = state, audioLevelFlow = audioLevelFlow)
                }
                if (state.availableVoices.isNotEmpty() || state.areLoadedModelsInRam) {
                    item(key = "voice_ram_controls") {
                        CallVoiceAndRamControls(
                            state = state,
                            onSelectVoice = onSelectVoice,
                            onReleaseRam = onReleaseRam
                        )
                    }
                }
                item(key = "feed_header") {
                    CallFeedHeaderRow(
                        conversationTitle = state.activeConversationTitle,
                        memoryEnabled = state.assistantIdentity.memoryEnabled,
                        onNewConversation = onNewConversation,
                        onOpenConversations = onOpenConversations,
                        onCopyReport = onCopyReport
                    )
                }
                if (state.pendingMemorySuggestions.isNotEmpty()) {
                    item(key = "call_memory_suggestions") {
                        PendingMemorySuggestionsCard(
                            suggestions = state.pendingMemorySuggestions,
                            onAcceptSuggestion = onAcceptSuggestion,
                            onEditAndSaveSuggestion = onEditAndSaveSuggestion,
                            onDismissSuggestion = onDismissSuggestion
                        )
                    }
                }
                if (state.conversationTurns.isEmpty() && state.streamingAssistantText.isBlank()) {
                    item(key = "empty_call") {
                        EmptyCallHintCard(modelsReady = state.areAllConversationalModelsReady)
                    }
                } else {
                    items(items = state.conversationTurns, key = { it.id }) { turn ->
                        OrionConversationTurnCard(
                            turn = turn,
                            assistantName = assistantDisplayName
                        )
                    }
                    if (state.streamingAssistantText.isNotBlank()) {
                        item(key = "streaming_reply") {
                            OrionStreamingReplyCard(
                                text = state.streamingAssistantText,
                                assistantName = assistantDisplayName
                            )
                        }
                    }
                }
            }

            state.pendingCallRequest?.let { pendingCall ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    PendingCallConfirmationCard(
                        pendingCall = pendingCall,
                        hasCallPhonePermission = state.hasCallPhonePermission,
                        onSelectCandidate = onSelectPhoneCandidate,
                        onConfirmDirectCall = onConfirmDirectPhoneCall,
                        onOpenDialer = onOpenPhoneDialer,
                        onCancel = onCancelPhoneCall
                    )
                }
            }

            state.pendingTaskPrompt?.let { taskPrompt ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 620.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    PendingTaskPromptCard(
                        prompt = taskPrompt,
                        onSelectCandidate = onSelectTaskCandidate,
                        onResolveTemporalChoice = onResolveTaskTemporalChoice,
                        onDismiss = onDismissTaskPrompt
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                VirtualCallBar(
                    state = state,
                    onStartCall = onStartCall,
                    onEndCall = onEndCall,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onInterrupt = onInterrupt,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ActiveContactCallHeaderBar(
    contactName: String,
    contactRole: String,
    canGoBack: Boolean,
    onBackToAgenda: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_contact_call_bar"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = onBackToAgenda,
                enabled = canGoBack,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("back_to_agenda_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back_agenda),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = stringResource(R.string.agenda_back_button))
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = contactName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contactRole,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Escenario visual modular de presencia de ORION para llamadas virtuales.
 * Preparado para evolucionar con avatares, imágenes y fondos personalizables.
 */
@Composable
fun OrionPresenceStage(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>,
    modifier: Modifier = Modifier
) {
    val audioLevel by audioLevelFlow.collectAsStateWithLifecycle()
    val animatedLevel by animateFloatAsState(
        targetValue = if (state.isCallActive) audioLevel else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "callAudioLevel"
    )
    val infiniteTransition = rememberInfiniteTransition(label = "orionOrbPulse")
    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseFactor"
    )

    val stageColor by animateColorAsState(
        targetValue = when (state.conversationState) {
            ConversationState.LISTENING -> if (state.isSpeechDetected) {
                MaterialTheme.colorScheme.secondary
            } else {
                MaterialTheme.colorScheme.primary
            }
            ConversationState.PROCESSING -> MaterialTheme.colorScheme.tertiary
            ConversationState.SPEAKING -> MaterialTheme.colorScheme.secondary
            ConversationState.INTERRUPTED -> MaterialTheme.colorScheme.tertiary
            ConversationState.ERROR -> MaterialTheme.colorScheme.error
            ConversationState.IDLE -> MaterialTheme.colorScheme.outline
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "presenceColor"
    )

    val assistantLabel = state.activeContact.name.ifBlank { "ORION" }.uppercase(Locale.getDefault())
    val stateLabel = when (state.conversationState) {
        ConversationState.IDLE -> "LISTO PARA LLAMAR"
        ConversationState.LISTENING -> if (state.isMuted) {
            "MICRÓFONO SILENCIADO"
        } else if (state.isSpeechDetected) {
            "ESCUCHANDO VOZ"
        } else {
            "ESCUCHANDO"
        }
        ConversationState.PROCESSING -> "PENSANDO EN LOCAL"
        ConversationState.SPEAKING -> "$assistantLabel HABLANDO"
        ConversationState.INTERRUPTED -> "INTERRUMPIDO"
        ConversationState.ERROR -> "ATENCIÓN"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("call_presence_stage"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = stageColor.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, stageColor.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(stageColor)
                        )
                        Text(
                            text = stateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = stageColor
                        )
                    }
                }

                Text(
                    text = formatDuration(state.callDurationSeconds),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (state.isCallActive) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Box(
                modifier = Modifier.size(136.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = size.minDimension * 0.28f
                    val dynamicBoost = when (state.conversationState) {
                        ConversationState.SPEAKING -> 14f + 12f * pulse
                        ConversationState.PROCESSING -> 8f + 10f * pulse
                        ConversationState.LISTENING -> 6f + animatedLevel * 28f
                        else -> 4f * pulse
                    }

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(stageColor.copy(alpha = 0.30f), Color.Transparent),
                            center = center,
                            radius = baseRadius + dynamicBoost + 28f
                        ),
                        center = center,
                        radius = baseRadius + dynamicBoost + 28f
                    )
                    drawCircle(
                        color = stageColor.copy(alpha = 0.35f),
                        center = center,
                        radius = baseRadius + dynamicBoost + 14f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = stageColor.copy(alpha = 0.72f),
                        center = center,
                        radius = baseRadius + dynamicBoost * 0.55f,
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                    drawCircle(
                        color = stageColor.copy(alpha = 0.22f),
                        center = center,
                        radius = baseRadius
                    )
                }

                Icon(
                    imageVector = when (state.conversationState) {
                        ConversationState.SPEAKING -> Icons.Filled.RecordVoiceOver
                        ConversationState.PROCESSING -> Icons.Filled.GraphicEq
                        ConversationState.LISTENING -> if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic
                        else -> Icons.Outlined.PhoneInTalk
                    },
                    contentDescription = stringResource(R.string.cd_voice_status),
                    tint = stageColor,
                    modifier = Modifier.size(34.dp)
                )
            }

            Text(
                text = state.callStatusMessage.ifBlank {
                    stringResource(R.string.call_models_required_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("call_status_message")
            )

            val snap = state.metricsSnapshot
            if (snap != null) {
                val tel = state.callTelemetry
                val summary = buildString {
                    append("PSS ${snap.appPssMb} MB · Libre ${snap.availRamMb} MB · ${DeviceMetrics.formatTemp(snap.batteryTempC)} · Bat ${snap.batteryPercent}%")
                    if (tel.lastEndToSpeechMs >= 0) {
                        append("\nÚltimo turno: voz→respuesta ${tel.lastEndToSpeechMs} ms · LLM ${tel.lastLlmTotalMs} ms")
                    }
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun CallVoiceAndRamControls(
    state: VoiceLabUiState,
    onSelectVoice: (String) -> Unit,
    onReleaseRam: () -> Unit
) {
    if (state.availableVoices.isEmpty() && !state.areLoadedModelsInRam) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.availableVoices.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.availableVoices.forEach { voice ->
                    val selected = voice.id == state.selectedVoiceId
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectVoice(voice.id) },
                        label = {
                            Text(
                                text = voice.displayName,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("voice_chip_${voice.id}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }
        }

        if (state.areLoadedModelsInRam) {
            OutlinedButton(
                onClick = onReleaseRam,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("release_ram_button"),
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.call_release_memory))
            }
        }
    }
}

@Composable
private fun CallFeedHeaderRow(
    conversationTitle: String,
    memoryEnabled: Boolean,
    onNewConversation: () -> Unit,
    onOpenConversations: () -> Unit,
    onCopyReport: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                SectionLabel(text = "CONVERSACIÓN ACTIVA")
                Text(
                    text = "$conversationTitle · ${if (memoryEnabled) "Memoria activa" else "Memoria en pausa"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenConversations,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("call_open_chats_button")
                ) {
                    Text(text = stringResource(R.string.tab_conversations))
                }
                FilledTonalButton(
                    onClick = onNewConversation,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("call_new_chat_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.conv_new_short))
                }
                IconButton(
                    onClick = onCopyReport,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("call_copy_report_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_icon),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyCallHintCard(modelsReady: Boolean) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(26.dp)
            )
            Text(
                text = if (modelsReady) {
                    stringResource(R.string.call_empty_hint)
                } else {
                    stringResource(R.string.call_models_required_hint)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun VirtualCallBar(
    state: VoiceLabUiState,
    onStartCall: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onInterrupt: () -> Unit,
    modifier: Modifier = Modifier
) {
    val canInterrupt = state.isCallActive && (
        state.conversationState == ConversationState.SPEAKING ||
            state.conversationState == ConversationState.PROCESSING
        )

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedButton(
            onClick = onToggleMute,
            enabled = state.isCallActive,
            modifier = Modifier
                .weight(0.9f)
                .heightIn(min = 52.dp)
                .minimumInteractiveComponentSize()
                .testTag("call_mute_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = if (state.isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                contentDescription = stringResource(R.string.cd_mic_icon),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = if (state.isMuted) {
                    stringResource(R.string.call_unmute)
                } else {
                    stringResource(R.string.call_mute)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onToggleSpeaker,
            enabled = state.isCallActive,
            modifier = Modifier
                .weight(0.85f)
                .heightIn(min = 52.dp)
                .minimumInteractiveComponentSize()
                .testTag("call_speaker_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 6.dp),
            border = BorderStroke(1.dp, if (state.isSpeakerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = if (state.isSpeakerEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = "Altavoz",
                modifier = Modifier.size(18.dp),
                tint = if (state.isSpeakerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(if (state.isSpeakerEnabled) "Altavoz" else "Audio", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        Button(
            onClick = if (state.isCallActive) onEndCall else onStartCall,
            enabled = state.isCallActive || state.areAllConversationalModelsReady,
            modifier = Modifier
                .weight(1.35f)
                .heightIn(min = 54.dp)
                .minimumInteractiveComponentSize()
                .testTag("call_primary_button"),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.isCallActive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
                contentColor = if (state.isCallActive) {
                    MaterialTheme.colorScheme.onError
                } else {
                    MaterialTheme.colorScheme.onPrimary
                }
            )
        ) {
            Icon(
                imageVector = if (state.isCallActive) Icons.Filled.CallEnd else Icons.Filled.Call,
                contentDescription = stringResource(R.string.cd_call_icon),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (state.isCallActive) {
                    stringResource(R.string.call_end)
                } else {
                    "Llamar a ${state.activeContact.name.ifBlank { "ORION" }}"
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        FilledTonalButton(
            onClick = onInterrupt,
            enabled = canInterrupt,
            modifier = Modifier
                .weight(0.95f)
                .heightIn(min = 52.dp)
                .minimumInteractiveComponentSize()
                .testTag("call_interrupt_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.PanTool,
                contentDescription = stringResource(R.string.cd_interrupt_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.call_interrupt),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatDuration(totalSeconds: Long): String {
    val mins = totalSeconds / 60L
    val secs = totalSeconds % 60L
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}

// =========================================================================
// Voice Lab 0.3 preserved Laboratory tab
// =========================================================================

@Composable
private fun CompactVoiceLabLayout(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>,
    onDownloadClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onMeasureClick: () -> Unit,
    onCopyReportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.transcripts.size) {
        if (state.transcripts.isNotEmpty()) {
            listState.animateScrollToItem(6 + state.transcripts.size - 1)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 620.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "header") {
                OrionHeader(state = state, audioLevelFlow = audioLevelFlow)
            }
            item(key = "controls") {
                ControlsCard(
                    state = state,
                    onDownloadClick = onDownloadClick,
                    onStartClick = onStartClick,
                    onStopClick = onStopClick
                )
            }
            item(key = "tools") {
                ToolsActionRow(
                    onMeasureClick = onMeasureClick,
                    onCopyReportClick = onCopyReportClick,
                    onClearClick = onClearClick
                )
            }
            item(key = "telemetry") {
                OrionTelemetryPanel(state = state)
            }
            item(key = "diagnostics") {
                OrionDiagnosticsPanel(state = state)
            }
            item(key = "transcripts_header") {
                SectionLabel(text = stringResource(R.string.section_transcripts))
            }
            if (state.transcripts.isEmpty()) {
                item(key = "transcripts_empty") {
                    EmptyTranscriptCard()
                }
            } else {
                items(
                    items = state.transcripts,
                    key = { it.id }
                ) { entry ->
                    TranscriptCard(entry = entry)
                }
            }
        }
    }
}

@Composable
private fun WideVoiceLabLayout(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>,
    onDownloadClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onMeasureClick: () -> Unit,
    onCopyReportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val listState = rememberLazyListState()
    LaunchedEffect(state.transcripts.size) {
        if (state.transcripts.isNotEmpty()) {
            listState.animateScrollToItem(state.transcripts.size - 1)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OrionHeader(state = state, audioLevelFlow = audioLevelFlow)
            ControlsCard(
                state = state,
                onDownloadClick = onDownloadClick,
                onStartClick = onStartClick,
                onStopClick = onStopClick
            )
            ToolsActionRow(
                onMeasureClick = onMeasureClick,
                onCopyReportClick = onCopyReportClick,
                onClearClick = onClearClick
            )
            OrionTelemetryPanel(state = state)
            OrionDiagnosticsPanel(state = state)
        }

        Column(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionLabel(text = stringResource(R.string.section_transcripts))
            if (state.transcripts.isEmpty()) {
                EmptyTranscriptCard(modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(
                        items = state.transcripts,
                        key = { it.id }
                    ) { entry ->
                        TranscriptCard(entry = entry)
                    }
                }
            }
        }
    }
}

@Composable
private fun OrionHeader(
    state: VoiceLabUiState,
    audioLevelFlow: StateFlow<Float>
) {
    val audioLevel by audioLevelFlow.collectAsStateWithLifecycle()
    val indicatorColor by animateColorAsState(
        targetValue = when (state.listeningState) {
            ListeningState.VOICE_DETECTED -> MaterialTheme.colorScheme.secondary
            ListeningState.LISTENING -> MaterialTheme.colorScheme.primary
            ListeningState.LOADING_MODELS, ListeningState.STOPPING -> MaterialTheme.colorScheme.tertiary
            ListeningState.IDLE -> MaterialTheme.colorScheme.outline
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "voiceIndicatorColor"
    )
    val animatedLevel by animateFloatAsState(
        targetValue = if (state.isRunning) audioLevel else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "speechLevel"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Text(
                text = "Laboratorio ASR + VAD (0.3)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        AnimatedVisibility(visible = state.isRunning) {
            Row(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val multipliers = listOf(0.45f, 0.75f, 1.0f, 0.75f, 0.45f)
                multipliers.forEach { weight ->
                    val barHeight = (4f + 10f * animatedLevel * weight).dp
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(indicatorColor)
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlsCard(
    state: VoiceLabUiState,
    onDownloadClick: () -> Unit,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = state.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("status_message")
            )

            AnimatedVisibility(visible = state.isDownloading) {
                val modelState = state.modelState
                if (modelState is ModelInstallState.Downloading && modelState.progressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { modelState.progressPercent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("download_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .testTag("download_progress"),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            FilledTonalButton(
                onClick = onDownloadClick,
                enabled = !state.isModelReady && !state.isDownloading,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("download_model_button"),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.CloudDownload,
                    contentDescription = stringResource(R.string.cd_download_icon),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (state.isModelReady) {
                        stringResource(R.string.btn_model_installed)
                    } else {
                        stringResource(R.string.btn_download_model)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onStartClick,
                    enabled = state.isModelReady && !state.isBusy,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("start_recognition_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = stringResource(R.string.cd_mic_icon),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_start_recognition),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onStopClick,
                    enabled = state.isRunning,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 50.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("stop_listening_button"),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = stringResource(R.string.cd_stop_icon),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_stop_listening),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsActionRow(
    onMeasureClick: () -> Unit,
    onCopyReportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(
            onClick = onMeasureClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .testTag("measure_now_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = stringResource(R.string.cd_measure_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_measure_now),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onCopyReportClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .testTag("copy_report_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_copy_report),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onClearClick,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .minimumInteractiveComponentSize()
                .testTag("clear_data_button"),
            shape = MaterialTheme.shapes.small,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Icon(
                imageVector = Icons.Outlined.DeleteSweep,
                contentDescription = stringResource(R.string.cd_clear_icon),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.btn_clear_data),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.GraphicEq,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun EmptyTranscriptCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("empty_transcript_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.empty_transcript),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TranscriptCard(entry: TranscriptEntry) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transcript_item_${entry.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "• ${entry.text}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = String.format(
                    Locale.getDefault(),
                    "audio %.1f s · decodificación %d ms",
                    entry.audioMs / 1000.0,
                    entry.decodeMs
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

