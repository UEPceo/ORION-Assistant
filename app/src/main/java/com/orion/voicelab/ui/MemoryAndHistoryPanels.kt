package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.ConversationSummary
import com.orion.voicelab.data.FormalityLevel
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.MemoryItem
import com.orion.voicelab.data.MemorySuggestion
import com.orion.voicelab.data.VoiceLabUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class MemorySubTab {
    MEMORIES,
    IDENTITY
}

@Composable
fun PendingMemorySuggestionsCard(
    suggestions: List<MemorySuggestion>,
    onAcceptSuggestion: (String) -> Unit,
    onEditAndSaveSuggestion: (suggestionId: String, category: MemoryCategory, content: String, importance: Int) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (suggestions.isEmpty()) return

    var editingSuggestion by remember { mutableStateOf<MemorySuggestion?>(null) }

    editingSuggestion?.let { target ->
        MemoryEditorDialog(
            title = "Editar antes de guardar",
            initialCategory = target.category,
            initialContent = target.content,
            initialImportance = target.importance,
            onDismiss = { editingSuggestion = null },
            onConfirm = { cat, text, imp ->
                onEditAndSaveSuggestion(target.id, cat, text, imp)
                editingSuggestion = null
            }
        )
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pending_memory_suggestions_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.memory_suggestions_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            suggestions.forEach { item ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_suggestion_${item.id}"),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CategoryBadge(label = item.category.label)
                        Text(
                            text = item.content,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilledTonalButton(
                                onClick = { onAcceptSuggestion(item.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("accept_suggestion_${item.id}"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(text = stringResource(R.string.memory_save), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { editingSuggestion = item },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .minimumInteractiveComponentSize(),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Edit,
                                    contentDescription = stringResource(R.string.cd_edit_memory),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            TextButton(
                                onClick = { onDismissSuggestion(item.id) },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .minimumInteractiveComponentSize()
                                    .testTag("dismiss_suggestion_${item.id}")
                            ) {
                                Text(text = stringResource(R.string.memory_dismiss), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationsHistoryScreen(
    state: VoiceLabUiState,
    onCreateNewConversation: () -> Unit,
    onSelectConversation: (Long) -> Unit,
    onDeleteConversation: (Long) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<ConversationSummary?>(null) }

    pendingDelete?.let { conv ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(text = stringResource(R.string.conv_delete_title)) },
            text = { Text(text = stringResource(R.string.conv_delete_confirm, conv.title)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteConversation(conv.id)
                        pendingDelete = null
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
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
            item(key = "conv_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HISTORIAL LOCAL DE CHATS",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "${state.conversations.size} guardadas en este dispositivo (100 % offline)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Button(
                            onClick = onCreateNewConversation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("new_conversation_button"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.conv_new))
                        }
                    }
                }
            }

            if (state.conversations.isEmpty()) {
                item(key = "conv_empty") {
                    EmptyLocalInfoCard(
                        icon = Icons.Outlined.ChatBubbleOutline,
                        message = stringResource(R.string.conv_empty)
                    )
                }
            } else {
                items(
                    items = state.conversations,
                    key = { it.id }
                ) { conv ->
                    ConversationSummaryCard(
                        summary = conv,
                        isActive = conv.id == state.activeConversationId,
                        onOpen = { onSelectConversation(conv.id) },
                        onDeleteRequest = { pendingDelete = conv }
                    )
                }
            }
        }
    }
}

@Composable
private fun ConversationSummaryCard(
    summary: ConversationSummary,
    isActive: Boolean,
    onOpen: () -> Unit,
    onDeleteRequest: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen)
            .testTag("conversation_card_${summary.id}"),
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = stringResource(R.string.conv_active_badge),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "${formatConversationTimestamp(summary.updatedAtMs)} · ${summary.messageCount} mensajes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )

                if (summary.lastPreview.isNotBlank()) {
                    Text(
                        text = summary.lastPreview,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = onDeleteRequest,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("delete_conversation_${summary.id}")
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.cd_delete_conversation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun MemoryAndIdentityScreen(
    state: VoiceLabUiState,
    onSetMemoryEnabled: (Boolean) -> Unit,
    onAddMemory: (MemoryCategory, String, Int) -> Unit,
    onUpdateMemory: (Long, MemoryCategory, String, Int) -> Unit,
    onDeleteMemory: (Long) -> Unit,
    onClearAllMemories: () -> Unit,
    onAcceptSuggestion: (String) -> Unit,
    onEditAndSaveSuggestion: (String, MemoryCategory, String, Int) -> Unit,
    onDismissSuggestion: (String) -> Unit,
    onSaveIdentity: (AssistantIdentity) -> Unit,
    onResetIdentity: () -> Unit
) {
    var subTab by rememberSaveable { mutableStateOf(MemorySubTab.MEMORIES) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingMemory by remember { mutableStateOf<MemoryItem?>(null) }
    var confirmClearAll by remember { mutableStateOf(false) }

    if (showAddDialog) {
        MemoryEditorDialog(
            title = stringResource(R.string.memory_add),
            initialCategory = MemoryCategory.GENERAL,
            initialContent = "",
            initialImportance = 4,
            onDismiss = { showAddDialog = false },
            onConfirm = { category, content, importance ->
                onAddMemory(category, content, importance)
                showAddDialog = false
            }
        )
    }

    editingMemory?.let { item ->
        MemoryEditorDialog(
            title = "Editar recuerdo",
            initialCategory = item.category,
            initialContent = item.content,
            initialImportance = item.importance,
            onDismiss = { editingMemory = null },
            onConfirm = { category, content, importance ->
                onUpdateMemory(item.id, category, content, importance)
                editingMemory = null
            }
        )
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            title = { Text(text = stringResource(R.string.memory_clear_title)) },
            text = { Text(text = stringResource(R.string.memory_clear_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAllMemories()
                        confirmClearAll = false
                    }
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = subTab == MemorySubTab.MEMORIES,
                    onClick = { subTab = MemorySubTab.MEMORIES },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Psychology,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = "${stringResource(R.string.memory_subtab_memories)} (${state.memories.size})",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = 48.dp)
                        .testTag("subtab_memories"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
                FilterChip(
                    selected = subTab == MemorySubTab.IDENTITY,
                    onClick = { subTab = MemorySubTab.IDENTITY },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Badge,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.memory_subtab_identity),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    modifier = Modifier
                        .weight(0.85f)
                        .heightIn(min = 48.dp)
                        .testTag("subtab_identity"),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.primary
                    )
                )
            }

            when (subTab) {
                MemorySubTab.MEMORIES -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item(key = "memory_toggle_card") {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                            Text(
                                                text = stringResource(R.string.memory_toggle_title),
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = if (state.assistantIdentity.memoryEnabled) {
                                                    stringResource(R.string.memory_toggle_desc_on)
                                                } else {
                                                    stringResource(R.string.memory_toggle_desc_off)
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = state.assistantIdentity.memoryEnabled,
                                            onCheckedChange = onSetMemoryEnabled,
                                            modifier = Modifier.testTag("memory_enabled_switch")
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { showAddDialog = true },
                                            modifier = Modifier
                                                .weight(1.2f)
                                                .heightIn(min = 48.dp)
                                                .minimumInteractiveComponentSize()
                                                .testTag("add_memory_button"),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Add,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(text = stringResource(R.string.memory_add), maxLines = 1)
                                        }

                                        OutlinedButton(
                                            onClick = { confirmClearAll = true },
                                            enabled = state.memories.isNotEmpty(),
                                            modifier = Modifier
                                                .weight(0.9f)
                                                .heightIn(min = 48.dp)
                                                .minimumInteractiveComponentSize()
                                                .testTag("clear_all_memories_button"),
                                            shape = MaterialTheme.shapes.small
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.DeleteSweep,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(text = stringResource(R.string.memory_clear_all), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }

                        if (state.pendingMemorySuggestions.isNotEmpty()) {
                            item(key = "memory_suggestions") {
                                PendingMemorySuggestionsCard(
                                    suggestions = state.pendingMemorySuggestions,
                                    onAcceptSuggestion = onAcceptSuggestion,
                                    onEditAndSaveSuggestion = onEditAndSaveSuggestion,
                                    onDismissSuggestion = onDismissSuggestion
                                )
                            }
                        }

                        if (state.memories.isEmpty()) {
                            item(key = "memory_empty") {
                                EmptyLocalInfoCard(
                                    icon = Icons.Filled.BookmarkAdded,
                                    message = stringResource(R.string.memory_empty)
                                )
                            }
                        } else {
                            items(
                                items = state.memories,
                                key = { it.id }
                            ) { item ->
                                MemoryItemCard(
                                    item = item,
                                    onEdit = { editingMemory = item },
                                    onDelete = { onDeleteMemory(item.id) }
                                )
                            }
                        }
                    }
                }

                MemorySubTab.IDENTITY -> {
                    IdentityConfigPanel(
                        identity = state.assistantIdentity,
                        onSaveIdentity = onSaveIdentity,
                        onResetIdentity = onResetIdentity
                    )
                }
            }
        }
    }
}

@Composable
private fun MemoryItemCard(
    item: MemoryItem,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("memory_item_${item.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CategoryBadge(label = item.category.label)
                    Text(
                        text = "Prioridad ${item.importance}/5",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                Text(
                    text = item.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("edit_memory_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cd_edit_memory),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_memory_${item.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_delete_memory),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityConfigPanel(
    identity: AssistantIdentity,
    onSaveIdentity: (AssistantIdentity) -> Unit,
    onResetIdentity: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(identity.assistantName) }
    var personality by rememberSaveable { mutableStateOf(identity.personality) }
    var tone by rememberSaveable { mutableStateOf(identity.tone) }
    var formality by rememberSaveable { mutableStateOf(identity.formality) }
    var basePrompt by rememberSaveable { mutableStateOf(identity.basePrompt) }

    LaunchedEffect(identity) {
        name = identity.assistantName
        personality = identity.personality
        tone = identity.tone
        formality = identity.formality
        basePrompt = identity.basePrompt
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
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
                text = "IDENTIDAD DEL ASISTENTE",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Ajusta el nombre, personalidad, tono y estilo que ORION usará en cada respuesta offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del asistente") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("identity_name_input")
            )

            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                label = { Text("Personalidad") },
                maxLines = 3,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("identity_personality_input")
            )

            OutlinedTextField(
                value = tone,
                onValueChange = { tone = it },
                label = { Text("Tono comunicativo") },
                maxLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("identity_tone_input")
            )

            Text(
                text = "Nivel de formalidad",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormalityLevel.entries.forEach { level ->
                    FilterChip(
                        selected = formality == level,
                        onClick = { formality = level },
                        label = { Text(level.displayName) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("formality_chip_${level.name}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }
            }

            OutlinedTextField(
                value = basePrompt,
                onValueChange = { basePrompt = it },
                label = { Text("Instrucción base") },
                minLines = 3,
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("identity_prompt_input")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        onSaveIdentity(
                            identity.copy(
                                assistantName = name,
                                personality = personality,
                                tone = tone,
                                formality = formality,
                                basePrompt = basePrompt
                            )
                        )
                    },
                    modifier = Modifier
                        .weight(1.2f)
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("identity_save_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(text = stringResource(R.string.identity_save), maxLines = 1)
                }

                OutlinedButton(
                    onClick = onResetIdentity,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("identity_reset_button"),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.identity_reset),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoryEditorDialog(
    title: String,
    initialCategory: MemoryCategory,
    initialContent: String,
    initialImportance: Int,
    onDismiss: () -> Unit,
    onConfirm: (MemoryCategory, String, Int) -> Unit
) {
    var selectedCategory by remember { mutableStateOf(initialCategory) }
    var content by remember { mutableStateOf(initialContent) }
    var importance by remember { mutableIntStateOf(initialImportance.coerceIn(1, 5)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Dato o preferencia a recordar") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("memory_editor_input")
                )

                Text(
                    text = "Categoría",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MemoryCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Text(
                    text = "Prioridad: $importance / 5",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..5).forEach { level ->
                        FilterChip(
                            selected = importance == level,
                            onClick = { importance = level },
                            label = { Text("$level") },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedCategory, content.trim(), importance) },
                enabled = content.isNotBlank(),
                modifier = Modifier.testTag("memory_editor_save")
            ) {
                Text(text = stringResource(R.string.memory_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun CategoryBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EmptyLocalInfoCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
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
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatConversationTimestamp(epochMs: Long): String {
    if (epochMs <= 0L) return "Ahora"
    return SimpleDateFormat("dd MMM · HH:mm", Locale.getDefault()).format(Date(epochMs))
}
