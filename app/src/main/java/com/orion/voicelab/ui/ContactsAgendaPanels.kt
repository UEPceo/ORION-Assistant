package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.voicelab.R
import com.orion.voicelab.data.ComponentModelState
import com.orion.voicelab.data.ContactProfile
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.data.VoiceProfile
import com.orion.voicelab.persistence.ContactRepository
import java.util.Locale

@Composable
fun ContactsAgendaScreen(
    state: VoiceLabUiState,
    onOpenContact: (Long) -> Unit,
    onCallContact: (Long) -> Unit,
    onRenamePrimary: (String) -> Unit,
    onAddCoworker: (name: String, roleTitle: String, specialtyPrompt: String, voiceId: String) -> Unit,
    onUpdateCoworker: (id: Long, name: String, roleTitle: String, specialtyPrompt: String, voiceId: String) -> Unit,
    onDeleteCoworker: (Long) -> Unit,
    onResumeLastChat: () -> Unit,
    onPauseAutoDownload: () -> Unit,
    onRetryAutoDownload: () -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showRenamePrimaryDialog by remember { mutableStateOf(false) }
    var showAddCoworkerDialog by remember { mutableStateOf(false) }
    var editingCoworker by remember { mutableStateOf<ContactProfile?>(null) }
    var deletingCoworker by remember { mutableStateOf<ContactProfile?>(null) }

    val primary = state.primaryContact
    val normalizedQuery = searchQuery.trim().lowercase(Locale.getDefault())
    val showPrimary = normalizedQuery.isEmpty() ||
        primary.name.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
        primary.roleTitle.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
        "principal".contains(normalizedQuery)

    val filteredCoworkers = remember(state.contacts, normalizedQuery) {
        if (normalizedQuery.isEmpty()) {
            state.contacts
        } else {
            state.contacts.filter { contact ->
                contact.name.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    contact.roleTitle.lowercase(Locale.getDefault()).contains(normalizedQuery) ||
                    contact.specialtyPrompt.lowercase(Locale.getDefault()).contains(normalizedQuery)
            }
        }
    }

    if (showRenamePrimaryDialog) {
        RenamePrimaryContactDialog(
            currentName = primary.name,
            onDismiss = { showRenamePrimaryDialog = false },
            onConfirm = { updatedName ->
                onRenamePrimary(updatedName)
                showRenamePrimaryDialog = false
            }
        )
    }

    if (showAddCoworkerDialog) {
        CoworkerEditorDialog(
            title = stringResource(R.string.agenda_coworker_new_title),
            initialName = "",
            initialRole = "",
            initialPrompt = "",
            initialVoiceId = state.selectedVoiceId,
            availableVoices = state.availableVoices,
            onDismiss = { showAddCoworkerDialog = false },
            onConfirm = { name, role, prompt, voiceId ->
                onAddCoworker(name, role, prompt, voiceId)
                showAddCoworkerDialog = false
            }
        )
    }

    editingCoworker?.let { target ->
        CoworkerEditorDialog(
            title = stringResource(R.string.agenda_coworker_edit_title),
            initialName = target.name,
            initialRole = target.roleTitle,
            initialPrompt = target.specialtyPrompt,
            initialVoiceId = target.voiceId,
            availableVoices = state.availableVoices,
            onDismiss = { editingCoworker = null },
            onConfirm = { name, role, prompt, voiceId ->
                onUpdateCoworker(target.id, name, role, prompt, voiceId)
                editingCoworker = null
            }
        )
    }

    deletingCoworker?.let { target ->
        AlertDialog(
            onDismissRequest = { deletingCoworker = null },
            title = { Text(text = stringResource(R.string.agenda_delete_coworker_title)) },
            text = {
                Text(
                    text = stringResource(R.string.agenda_delete_coworker_confirm, target.name)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteCoworker(target.id)
                        deletingCoworker = null
                    },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(text = stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { deletingCoworker = null },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "agenda_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.agenda_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = stringResource(R.string.agenda_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = { showAddCoworkerDialog = true },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("add_coworker_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PersonAdd,
                            contentDescription = stringResource(R.string.cd_add_coworker),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.agenda_add_coworker_short))
                    }
                }
            }

            if (!state.areAllConversationalModelsReady ||
                state.isAutoDownloadingInitialModels ||
                state.autoDownloadError != null
            ) {
                item(key = "auto_download_banner") {
                    AutoModelDownloadBanner(
                        state = state,
                        onPause = onPauseAutoDownload,
                        onRetry = onRetryAutoDownload
                    )
                }
            }

            item(key = "agenda_search") {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.agenda_search_hint),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.cd_search_contacts)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.action_cancel)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agenda_search_input")
                )
            }

            if (state.conversationTurns.isNotEmpty() && normalizedQuery.isEmpty()) {
                item(key = "resume_last_chat") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable(onClick = onResumeLastChat)
                            .testTag("agenda_resume_last_chat"),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.agenda_last_chat_prefix),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = state.activeConversationTitle,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = stringResource(R.string.agenda_open_last_chat),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }

            if (showPrimary) {
                item(key = "primary_contact") {
                    PrimaryContactCard(
                        contact = primary,
                        isSelected = state.selectedContactId == primary.id,
                        isCallActive = state.isCallActive && state.selectedContactId == primary.id,
                        onOpen = { onOpenContact(primary.id) },
                        onRename = { showRenamePrimaryDialog = true },
                        onCall = { onCallContact(primary.id) }
                    )
                }
            }

            item(key = "coworkers_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Contacts,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.agenda_coworkers_header, state.contacts.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (state.contacts.isEmpty() && normalizedQuery.isEmpty()) {
                item(key = "empty_coworkers") {
                    EmptyCoworkersCard(onAddClick = { showAddCoworkerDialog = true })
                }
            } else if (filteredCoworkers.isEmpty() && !showPrimary) {
                item(key = "no_search_results") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = stringResource(R.string.agenda_no_search_results, searchQuery.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        )
                    }
                }
            } else if (filteredCoworkers.isEmpty()) {
                item(key = "no_coworker_search_results") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = stringResource(R.string.agenda_no_search_results, searchQuery.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp)
                        )
                    }
                }
            } else {
                items(items = filteredCoworkers, key = { it.id }) { coworker ->
                    val voiceName = state.availableVoices
                        .firstOrNull { it.id == coworker.voiceId }
                        ?.displayName
                        ?: coworker.voiceId
                    CoworkerContactCard(
                        contact = coworker,
                        voiceDisplayName = voiceName,
                        isSelected = state.selectedContactId == coworker.id,
                        isCallActive = state.isCallActive && state.selectedContactId == coworker.id,
                        onOpen = { onOpenContact(coworker.id) },
                        onEdit = { editingCoworker = coworker },
                        onDelete = { deletingCoworker = coworker },
                        onCall = { onCallContact(coworker.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoModelDownloadBanner(
    state: VoiceLabUiState,
    onPause: () -> Unit,
    onRetry: () -> Unit
) {
    val ttsState = state.ttsModelState
    val asrState = state.modelState
    val llmState = state.llmModelState

    val activeFraction: Float? = when {
        ttsState is ComponentModelState.Downloading && ttsState.progressPercent > 0 ->
            ttsState.progressPercent / 100f
        asrState is ModelInstallState.Downloading && asrState.progressPercent > 0 ->
            asrState.progressPercent / 100f
        llmState is ComponentModelState.Downloading && llmState.progressPercent > 0 ->
            llmState.progressPercent / 100f
        else -> null
    }

    val activeDetail = when {
        ttsState is ComponentModelState.Downloading ->
            "Descargando voz española (${ttsState.downloadedMb}/${ttsState.totalMb} MB)…"
        ttsState is ComponentModelState.Extracting ->
            "Instalando voz española…"
        asrState is ModelInstallState.Downloading ->
            "Descargando oído Moonshine ES (${asrState.progressPercent} %)…"
        asrState is ModelInstallState.Extracting ->
            "Extrayendo oído Moonshine ES…"
        llmState is ComponentModelState.Downloading ->
            "Descargando cerebro Qwen2.5-1.5B (${llmState.downloadedMb}/${llmState.totalMb} MB)…"
        state.autoDownloadError != null ->
            "Error de descarga: ${state.autoDownloadError}"
        state.isAutoDownloadingInitialModels ->
            "Preparando descarga automática offline…"
        else ->
            stringResource(R.string.call_models_required_hint)
    }

    val ttsStatusLabel = when (ttsState) {
        is ComponentModelState.Installed -> "Voz ✓"
        is ComponentModelState.Downloading -> "Voz ${ttsState.progressPercent}%"
        is ComponentModelState.Extracting -> "Voz …"
        is ComponentModelState.Error -> "Voz !"
        ComponentModelState.NotInstalled -> "Voz ·"
    }
    val asrStatusLabel = when (asrState) {
        is ModelInstallState.Installed -> "Oído ✓"
        is ModelInstallState.Downloading -> "Oído ${asrState.progressPercent}%"
        is ModelInstallState.Extracting -> "Oído …"
        is ModelInstallState.Error -> "Oído !"
        ModelInstallState.NotInstalled -> "Oído ·"
    }
    val llmStatusLabel = when (llmState) {
        is ComponentModelState.Installed -> "LLM ✓"
        is ComponentModelState.Downloading -> "LLM ${llmState.progressPercent}%"
        is ComponentModelState.Extracting -> "LLM …"
        is ComponentModelState.Error -> "LLM !"
        ComponentModelState.NotInstalled -> "LLM ·"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("auto_download_banner"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(
            width = 1.dp,
            color = if (state.autoDownloadError != null) {
                MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudDownload,
                        contentDescription = stringResource(R.string.cd_download_icon),
                        tint = if (state.autoDownloadError != null) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (state.isAutoDownloadingInitialModels) {
                            stringResource(R.string.auto_dl_title_active)
                        } else {
                            stringResource(R.string.auto_dl_title_paused)
                        },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (state.isAutoDownloadingInitialModels) {
                    TextButton(
                        onClick = onPause,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("auto_download_action_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.auto_dl_pause))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("auto_download_action_button"),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(
                            imageVector = if (state.autoDownloadError != null) {
                                Icons.Outlined.Refresh
                            } else {
                                Icons.Outlined.PlayArrow
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (state.autoDownloadError != null) {
                                stringResource(R.string.auto_dl_retry)
                            } else {
                                stringResource(R.string.auto_dl_resume)
                            }
                        )
                    }
                }
            }

            Text(
                text = activeDetail,
                style = MaterialTheme.typography.bodySmall,
                color = if (state.autoDownloadError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            if (state.isAutoDownloadingInitialModels) {
                if (activeFraction != null) {
                    LinearProgressIndicator(
                        progress = { activeFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ModelMiniPill(text = ttsStatusLabel, done = ttsState is ComponentModelState.Installed)
                ModelMiniPill(text = asrStatusLabel, done = asrState is ModelInstallState.Installed)
                ModelMiniPill(text = llmStatusLabel, done = llmState is ComponentModelState.Installed)
            }
        }
    }
}

@Composable
private fun ModelMiniPill(text: String, done: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (done) {
            MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        border = BorderStroke(
            width = 1.dp,
            color = if (done) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun PrimaryContactCard(
    contact: ContactProfile,
    isSelected: Boolean,
    isCallActive: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onOpen)
            .testTag("primary_contact_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContactAvatar(
                initials = contactInitials(contact.name, fallback = "OR"),
                isPrimary = true
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = if (isCallActive) "EN LLAMADA" else stringResource(R.string.agenda_primary_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.roleTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRename,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_primary_contact_button")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_contact),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FilledIconButton(
                onClick = onCall,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("call_primary_contact_button"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = stringResource(R.string.cd_call_contact)
                )
            }
        }
    }
}

@Composable
private fun CoworkerContactCard(
    contact: ContactProfile,
    voiceDisplayName: String,
    isSelected: Boolean,
    isCallActive: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCall: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .testTag("coworker_card_${contact.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f)
            } else {
                MaterialTheme.colorScheme.outline
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ContactAvatar(
                initials = contactInitials(contact.name, fallback = "CW"),
                isPrimary = false
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isCallActive) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = contact.roleTitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Voz: $voiceDisplayName",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("edit_coworker_${contact.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Edit,
                    contentDescription = stringResource(R.string.cd_edit_contact),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_coworker_${contact.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_contact),
                    tint = MaterialTheme.colorScheme.error
                )
            }

            FilledIconButton(
                onClick = onCall,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("call_coworker_${contact.id}"),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = stringResource(R.string.cd_call_contact)
                )
            }
        }
    }
}

@Composable
private fun ContactAvatar(
    initials: String,
    isPrimary: Boolean
) {
    val bgColor = if (isPrimary) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fgColor = if (isPrimary) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initials,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = fgColor
        )
    }
}

@Composable
private fun EmptyCoworkersCard(onAddClick: () -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.SupportAgent,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(30.dp)
            )
            Text(
                text = stringResource(R.string.agenda_empty_coworkers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            FilledTonalButton(
                onClick = onAddClick,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("empty_add_coworker_button"),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = stringResource(R.string.agenda_add_coworker))
            }
        }
    }
}

@Composable
private fun RenamePrimaryContactDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var nameInput by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.agenda_rename_primary_title)) },
        text = {
            OutlinedTextField(
                value = nameInput,
                onValueChange = { nameInput = it },
                singleLine = true,
                label = { Text(text = stringResource(R.string.agenda_rename_primary_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rename_primary_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(nameInput.trim()) },
                enabled = nameInput.trim().isNotEmpty(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_rename_primary_button")
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun CoworkerEditorDialog(
    title: String,
    initialName: String,
    initialRole: String,
    initialPrompt: String,
    initialVoiceId: String,
    availableVoices: List<VoiceProfile>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, roleTitle: String, specialtyPrompt: String, voiceId: String) -> Unit
) {
    var nameInput by remember { mutableStateOf(initialName) }
    var roleInput by remember { mutableStateOf(initialRole) }
    var promptInput by remember { mutableStateOf(initialPrompt) }
    var selectedVoice by remember { mutableStateOf(initialVoiceId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.agenda_coworker_name_label)) },
                    placeholder = { Text(text = "Ej. Vega") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coworker_name_input")
                )

                OutlinedTextField(
                    value = roleInput,
                    onValueChange = { roleInput = it },
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.agenda_coworker_role_label)) },
                    placeholder = { Text(text = ContactRepository.DEFAULT_ROLE) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coworker_role_input")
                )

                OutlinedTextField(
                    value = promptInput,
                    onValueChange = { promptInput = it },
                    minLines = 3,
                    maxLines = 5,
                    label = { Text(text = stringResource(R.string.agenda_coworker_prompt_label)) },
                    placeholder = { Text(text = ContactRepository.DEFAULT_SPECIALTY_PROMPT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("coworker_prompt_input")
                )

                if (availableVoices.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.agenda_coworker_voice_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        availableVoices.forEach { voice ->
                            FilterChip(
                                selected = selectedVoice == voice.id,
                                onClick = { selectedVoice = voice.id },
                                label = {
                                    Text(
                                        text = voice.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        nameInput.trim(),
                        roleInput.trim(),
                        promptInput.trim(),
                        selectedVoice
                    )
                },
                enabled = nameInput.trim().isNotEmpty(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_save_coworker_button")
            ) {
                Text(text = stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

private fun contactInitials(name: String, fallback: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> fallback
        parts.size == 1 -> parts[0].take(2).uppercase(Locale.getDefault())
        else -> "${parts[0].first()}${parts[1].first()}".uppercase(Locale.getDefault())
    }
}
