package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.orion.voicelab.R
import com.orion.voicelab.data.PendingTaskPrompt
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.TaskFilter
import com.orion.voicelab.data.TaskItem
import com.orion.voicelab.data.TaskPriority
import com.orion.voicelab.data.TaskStatus
import com.orion.voicelab.data.VoiceLabUiState
import com.orion.voicelab.tasks.SpanishDateTimeParser
import com.orion.voicelab.tasks.TaskRepository
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

@Composable
fun PendingTaskPromptCard(
    prompt: PendingTaskPrompt,
    onSelectCandidate: (TaskItem) -> Unit,
    onResolveTemporalChoice: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("pending_task_prompt_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f)
        ),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.secondary)
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
                Text(
                    text = when (prompt) {
                        is PendingTaskPrompt.DisambiguateTasks -> prompt.actionLabel
                        is PendingTaskPrompt.ClarifyTemporalAmbiguity -> stringResource(R.string.tasks_clarify_time_title)
                        is PendingTaskPrompt.ClarifyMissingTitle -> stringResource(R.string.tasks_clarify_title_header)
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.secondary
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.action_cancel)
                    )
                }
            }

            when (prompt) {
                is PendingTaskPrompt.DisambiguateTasks -> {
                    prompt.candidates.forEachIndexed { idx, task ->
                        Surface(
                            onClick = { onSelectCandidate(task) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("task_disambiguate_$idx"),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${idx + 1}. ${task.title}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = SpanishDateTimeParser.formatDateTimeSummary(
                                            task.dueDateEpochDay,
                                            task.dueTimeMinutes,
                                            task.reminderAtMs
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                is PendingTaskPrompt.ClarifyTemporalAmbiguity -> {
                    Text(
                        text = prompt.promptMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { onResolveTemporalChoice(prompt.optionA24HourMinutes) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("task_temporal_option_a"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(text = SpanishDateTimeParser.formatTimeLabel(prompt.optionA24HourMinutes))
                        }
                        Button(
                            onClick = { onResolveTemporalChoice(prompt.optionB24HourMinutes) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("task_temporal_option_b"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(text = SpanishDateTimeParser.formatTimeLabel(prompt.optionB24HourMinutes))
                        }
                    }
                }

                is PendingTaskPrompt.ClarifyMissingTitle -> {
                    Text(
                        text = prompt.promptMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TasksSectionScreen(
    state: VoiceLabUiState,
    onSelectFilter: (TaskFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onRequestNotificationsPermission: () -> Unit,
    onCreateTask: (
        title: String,
        description: String,
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        reminderActive: Boolean,
        priority: TaskPriority,
        category: String,
        notes: String,
        contactName: String?,
        contactKey: String?,
        contactPhone: String?,
        linkedCallRecordId: Long?
    ) -> Unit,
    onUpdateTask: (
        id: Long,
        title: String,
        description: String,
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        reminderActive: Boolean,
        priority: TaskPriority,
        category: String,
        notes: String,
        contactName: String?,
        contactKey: String?,
        contactPhone: String?,
        linkedCallRecordId: Long?
    ) -> Unit,
    onToggleCompleted: (TaskItem) -> Unit,
    onCancelTask: (Long) -> Unit,
    onPostponeTask: (id: Long, newDueDateEpochDay: Long?, newDueTimeMinutes: Int?, newReminderAtMs: Long?) -> Unit,
    onSnoozeTask: (Long, Long) -> Unit,
    onDismissReminder: (Long) -> Unit,
    onDeleteTask: (Long) -> Unit,
    onDeleteFinished: () -> Unit,
    onDeleteAll: () -> Unit,
    onCallTaskContact: (TaskItem) -> Unit,
    onSelectDisambiguationCandidate: (TaskItem) -> Unit,
    onResolveTemporalChoice: (Int) -> Unit,
    onDismissPendingPrompt: () -> Unit
) {
    var showCreateDialog by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskItem?>(null) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val nowMs = System.currentTimeMillis()
    val filteredTasks = remember(state.tasks, state.selectedTaskFilter, state.taskSearchQuery, nowMs) {
        TaskRepository.filter(
            tasks = state.tasks,
            filter = state.selectedTaskFilter,
            searchQuery = state.taskSearchQuery,
            nowMs = nowMs
        )
    }
    val filterCounts = remember(state.tasks, nowMs) {
        TaskFilter.entries.associateWith { bucket ->
            TaskRepository.filter(state.tasks, bucket, "", nowMs).size
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "tasks_header") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                                    text = stringResource(R.string.tasks_title),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.tasks_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (!state.hasNotificationsPermission) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.tasks_notification_perm_rationale),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(end = 8.dp)
                                    )
                                    FilledTonalButton(
                                        onClick = onRequestNotificationsPermission,
                                        modifier = Modifier
                                            .heightIn(min = 48.dp)
                                            .testTag("request_notifications_permission_button"),
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Text(text = stringResource(R.string.tasks_allow_notifications))
                                    }
                                }
                            }
                        }

                        if (state.taskStatusMessage.isNotBlank()) {
                            Text(
                                text = state.taskStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        OutlinedTextField(
                            value = state.taskSearchQuery,
                            onValueChange = onSearchQueryChanged,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("tasks_search_input"),
                            singleLine = true,
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = stringResource(R.string.cd_search_tasks)
                                )
                            },
                            trailingIcon = {
                                if (state.taskSearchQuery.isNotBlank()) {
                                    IconButton(
                                        onClick = { onSearchQueryChanged("") },
                                        modifier = Modifier.minimumInteractiveComponentSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = stringResource(R.string.action_cancel)
                                        )
                                    }
                                }
                            },
                            placeholder = {
                                Text(text = stringResource(R.string.tasks_search_hint))
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("add_task_button"),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(text = stringResource(R.string.tasks_new_button))
                            }

                            if (state.tasks.isNotEmpty()) {
                                OutlinedButton(
                                    onClick = { showClearDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("clear_tasks_menu_button"),
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteSweep,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(text = stringResource(R.string.tasks_clean_button))
                                }
                            }
                        }
                    }
                }
            }

            state.pendingTaskPrompt?.let { prompt ->
                item(key = "tasks_pending_prompt") {
                    PendingTaskPromptCard(
                        prompt = prompt,
                        onSelectCandidate = onSelectDisambiguationCandidate,
                        onResolveTemporalChoice = onResolveTemporalChoice,
                        onDismiss = onDismissPendingPrompt
                    )
                }
            }

            item(key = "tasks_filter_strip") {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = TaskFilter.entries,
                        key = { it.name }
                    ) { filter ->
                        val count = filterCounts[filter] ?: 0
                        FilterChip(
                            selected = state.selectedTaskFilter == filter,
                            onClick = { onSelectFilter(filter) },
                            label = { Text("${filter.label} ($count)") },
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("task_filter_${filter.name.lowercase()}")
                        )
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item(key = "tasks_empty") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text(
                            text = stringResource(R.string.tasks_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                }
            } else {
                items(
                    items = filteredTasks,
                    key = { "task_${it.id}" }
                ) { task ->
                    TaskCard(
                        task = task,
                        nowMs = nowMs,
                        onToggleCompleted = { onToggleCompleted(task) },
                        onEdit = { editingTask = task },
                        onSnooze1h = { onSnoozeTask(task.id, 60L) },
                        onPostponeTomorrow = {
                            val zone = ZoneId.systemDefault()
                            val tomorrow = LocalDate.now(zone).plusDays(1)
                            val mins = task.dueTimeMinutes ?: (9 * 60)
                            val remMs = tomorrow.atTime(mins / 60, mins % 60).atZone(zone).toInstant().toEpochMilli()
                            onPostponeTask(task.id, tomorrow.toEpochDay(), mins, remMs)
                        },
                        onDismissReminder = { onDismissReminder(task.id) },
                        onCancelTask = { onCancelTask(task.id) },
                        onDelete = { onDeleteTask(task.id) },
                        onCallContact = { onCallTaskContact(task) }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        TaskEditorDialog(
            existingTask = null,
            favoriteContacts = state.favoritePhoneContacts,
            hasNotificationsPermission = state.hasNotificationsPermission,
            onRequestNotificationsPermission = onRequestNotificationsPermission,
            onDismiss = { showCreateDialog = false },
            onSave = { title, desc, day, mins, remMs, remActive, prio, cat, notes, cName, cKey, cPhone, callId ->
                onCreateTask(title, desc, day, mins, remMs, remActive, prio, cat, notes, cName, cKey, cPhone, callId)
                showCreateDialog = false
            }
        )
    }

    editingTask?.let { target ->
        TaskEditorDialog(
            existingTask = target,
            favoriteContacts = state.favoritePhoneContacts,
            hasNotificationsPermission = state.hasNotificationsPermission,
            onRequestNotificationsPermission = onRequestNotificationsPermission,
            onDismiss = { editingTask = null },
            onSave = { title, desc, day, mins, remMs, remActive, prio, cat, notes, cName, cKey, cPhone, callId ->
                onUpdateTask(target.id, title, desc, day, mins, remMs, remActive, prio, cat, notes, cName, cKey, cPhone, callId)
                editingTask = null
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.tasks_clean_dialog_title)) },
            text = { Text(stringResource(R.string.tasks_clean_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFinished()
                        showClearDialog = false
                    },
                    modifier = Modifier.heightIn(min = 48.dp)
                ) {
                    Text(stringResource(R.string.tasks_clean_finished))
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            onDeleteAll()
                            showClearDialog = false
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.tasks_clean_all),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    TextButton(
                        onClick = { showClearDialog = false },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskCard(
    task: TaskItem,
    nowMs: Long,
    onToggleCompleted: () -> Unit,
    onEdit: () -> Unit,
    onSnooze1h: () -> Unit,
    onPostponeTomorrow: () -> Unit,
    onDismissReminder: () -> Unit,
    onCancelTask: () -> Unit,
    onDelete: () -> Unit,
    onCallContact: () -> Unit
) {
    val isOverdue = remember(task, nowMs) { task.isOverdue(nowMs) }
    val isDone = task.status != TaskStatus.PENDING
    val priorityColor = when {
        isOverdue -> MaterialTheme.colorScheme.error
        task.priority == TaskPriority.URGENT -> MaterialTheme.colorScheme.error
        task.priority == TaskPriority.HIGH -> MaterialTheme.colorScheme.tertiary
        task.priority == TaskPriority.NORMAL -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("task_card_${task.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (isOverdue) 1.5.dp else 1.dp,
            color = if (isOverdue) MaterialTheme.colorScheme.error.copy(alpha = 0.7f) else MaterialTheme.colorScheme.outline
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onToggleCompleted,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("task_toggle_${task.id}")
                ) {
                    Icon(
                        imageVector = when (task.status) {
                            TaskStatus.COMPLETED -> Icons.Filled.CheckCircle
                            TaskStatus.CANCELLED -> Icons.Outlined.Block
                            TaskStatus.PENDING -> Icons.Outlined.RadioButtonUnchecked
                        },
                        contentDescription = if (isDone) {
                            stringResource(R.string.cd_reopen_task)
                        } else {
                            stringResource(R.string.cd_complete_task)
                        },
                        tint = when (task.status) {
                            TaskStatus.COMPLETED -> MaterialTheme.colorScheme.secondary
                            TaskStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
                            TaskStatus.PENDING -> MaterialTheme.colorScheme.primary
                        }
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (isDone) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        color = if (isDone) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                    )
                    if (task.description.isNotBlank()) {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = priorityColor.copy(alpha = 0.14f),
                    border = BorderStroke(1.dp, priorityColor.copy(alpha = 0.5f))
                ) {
                    Text(
                        text = if (isOverdue) "ATRASADA" else task.priority.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = priorityColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val whenStr = SpanishDateTimeParser.formatDateTimeSummary(
                    task.dueDateEpochDay,
                    task.dueTimeMinutes,
                    task.reminderAtMs
                )
                if (whenStr != "Sin fecha" || task.reminderActive) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (task.reminderActive) Icons.Outlined.NotificationsActive else Icons.Outlined.Schedule,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (task.reminderActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = whenStr,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                if (task.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    ) {
                        Text(
                            text = task.category,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (!task.contactName.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Text(
                                text = task.contactName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }

                if (task.linkedCallRecordId != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = "Vinculada a llamada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (task.notes.isNotBlank()) {
                Text(
                    text = "Notas: ${task.notes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (task.status == TaskStatus.PENDING) {
                    OutlinedButton(
                        onClick = onSnooze1h,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(imageVector = Icons.Outlined.Snooze, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "+1 h")
                    }

                    OutlinedButton(
                        onClick = onPostponeTomorrow,
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(imageVector = Icons.Outlined.Alarm, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.tasks_postpone_tomorrow))
                    }

                    if (task.reminderActive) {
                        IconButton(
                            onClick = onDismissReminder,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AlarmOff,
                                contentDescription = stringResource(R.string.cd_dismiss_reminder),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!task.contactName.isNullOrBlank() || !task.contactPhone.isNullOrBlank()) {
                    FilledTonalButton(
                        onClick = onCallContact,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("task_call_contact_${task.id}"),
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Icon(imageVector = Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = stringResource(R.string.agenda_call_action))
                    }
                }

                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cd_edit_task),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (task.status == TaskStatus.PENDING) {
                    IconButton(
                        onClick = onCancelTask,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Block,
                            contentDescription = stringResource(R.string.cd_cancel_task),
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.cd_delete_task),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorDialog(
    existingTask: TaskItem?,
    favoriteContacts: List<PhoneContactEntry>,
    hasNotificationsPermission: Boolean,
    onRequestNotificationsPermission: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String,
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        reminderActive: Boolean,
        priority: TaskPriority,
        category: String,
        notes: String,
        contactName: String?,
        contactKey: String?,
        contactPhone: String?,
        linkedCallRecordId: Long?
    ) -> Unit,
    prefillTitle: String = "",
    prefillDescription: String = "",
    prefillCategory: String = "",
    prefillPriority: TaskPriority = TaskPriority.NORMAL,
    prefillContactName: String? = null,
    prefillContactKey: String? = null,
    prefillContactPhone: String? = null,
    prefillCallRecordId: Long? = null
) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)

    var title by remember { mutableStateOf(existingTask?.title ?: prefillTitle) }
    var description by remember { mutableStateOf(existingTask?.description ?: prefillDescription) }
    var category by remember { mutableStateOf(existingTask?.category ?: prefillCategory) }
    var notes by remember { mutableStateOf(existingTask?.notes ?: "") }
    var priority by remember { mutableStateOf(existingTask?.priority ?: prefillPriority) }
    var selectedDateEpochDay by remember { mutableStateOf(existingTask?.dueDateEpochDay ?: today.toEpochDay()) }
    var customDateText by remember {
        mutableStateOf(
            (existingTask?.dueDateEpochDay ?: today.toEpochDay()).let { day ->
                LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        )
    }
    var selectedTimeMinutes by remember { mutableStateOf(existingTask?.dueTimeMinutes) }
    var customTimeText by remember {
        mutableStateOf(
            existingTask?.dueTimeMinutes?.let { SpanishDateTimeParser.formatTimeLabel(it) } ?: ""
        )
    }
    var reminderEnabled by remember { mutableStateOf(existingTask?.reminderActive ?: false) }
    var contactName by remember { mutableStateOf(existingTask?.contactName ?: prefillContactName.orEmpty()) }
    var contactKey by remember { mutableStateOf(existingTask?.contactKey ?: prefillContactKey) }
    var contactPhone by remember { mutableStateOf(existingTask?.contactPhone ?: prefillContactPhone.orEmpty()) }
    val linkedCallId = existingTask?.linkedCallRecordId ?: prefillCallRecordId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (existingTask == null) {
                    stringResource(R.string.tasks_dialog_new_title)
                } else {
                    stringResource(R.string.tasks_dialog_edit_title)
                }
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("task_dialog_title_input"),
                    label = { Text(stringResource(R.string.tasks_field_title)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.tasks_field_description)) }
                )

                Text(
                    text = stringResource(R.string.tasks_field_priority),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.tasks_field_date),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                    FilterChip(
                        selected = selectedDateEpochDay == null,
                        onClick = {
                            selectedDateEpochDay = null
                            customDateText = ""
                        },
                        label = { Text("Sin fecha") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    listOf(
                        "Hoy" to today,
                        "Mañana" to today.plusDays(1),
                        "Lunes" to nextMon
                    ).forEach { (label, date) ->
                        FilterChip(
                            selected = selectedDateEpochDay == date.toEpochDay(),
                            onClick = {
                                selectedDateEpochDay = date.toEpochDay()
                                customDateText = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            },
                            label = { Text(label) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = customDateText,
                    onValueChange = { raw ->
                        customDateText = raw
                        val parsed = runCatching {
                            LocalDate.parse(raw.trim(), DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                        }.getOrNull()
                        if (parsed != null) selectedDateEpochDay = parsed.toEpochDay()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Fecha (dd/MM/aaaa)") }
                )

                Text(
                    text = stringResource(R.string.tasks_field_time),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedTimeMinutes == null,
                        onClick = {
                            selectedTimeMinutes = null
                            customTimeText = ""
                        },
                        label = { Text("Sin hora") },
                        modifier = Modifier.heightIn(min = 48.dp)
                    )
                    listOf(9 * 60, 13 * 60, 17 * 60, 20 * 60).forEach { mins ->
                        FilterChip(
                            selected = selectedTimeMinutes == mins,
                            onClick = {
                                selectedTimeMinutes = mins
                                customTimeText = SpanishDateTimeParser.formatTimeLabel(mins)
                            },
                            label = { Text(SpanishDateTimeParser.formatTimeLabel(mins)) },
                            modifier = Modifier.heightIn(min = 48.dp)
                        )
                    }
                }
                OutlinedTextField(
                    value = customTimeText,
                    onValueChange = { raw ->
                        customTimeText = raw
                        val parts = raw.trim().split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull()
                        val m = parts.getOrNull(1)?.toIntOrNull()
                        if (h != null && m != null && h in 0..23 && m in 0..59) {
                            selectedTimeMinutes = h * 60 + m
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Hora (HH:mm)") }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.tasks_field_reminder_toggle),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { checked ->
                            reminderEnabled = checked
                            if (checked && !hasNotificationsPermission) {
                                onRequestNotificationsPermission()
                            }
                            if (checked && selectedDateEpochDay == null) {
                                selectedDateEpochDay = today.toEpochDay()
                                customDateText = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                            }
                        }
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.tasks_field_category)) }
                )

                if (favoriteContacts.isNotEmpty()) {
                    Text(
                        text = "Asociar contacto rápido",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        favoriteContacts.take(5).forEach { fav ->
                            FilterChip(
                                selected = contactPhone == fav.phoneNumber && contactName == fav.displayName,
                                onClick = {
                                    contactName = fav.displayName
                                    contactKey = fav.contactKey
                                    contactPhone = fav.phoneNumber
                                },
                                label = { Text(fav.displayName) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = contactName,
                    onValueChange = { contactName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.tasks_field_contact_name)) }
                )
                OutlinedTextField(
                    value = contactPhone,
                    onValueChange = { contactPhone = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.tasks_field_contact_phone)) }
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    label = { Text(stringResource(R.string.tasks_field_notes)) }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetDay = selectedDateEpochDay
                    val reminderMs = if (reminderEnabled) {
                        val day = targetDay ?: today.toEpochDay()
                        val mins = selectedTimeMinutes ?: (9 * 60)
                        val target = LocalDate.ofEpochDay(day)
                            .atTime(LocalTime.of(mins / 60, mins % 60))
                            .atZone(zone)
                            .toInstant()
                            .toEpochMilli()
                        val now = System.currentTimeMillis()
                        if (target <= now && selectedTimeMinutes == null) now + 3_600_000L else target
                    } else {
                        null
                    }
                    onSave(
                        title.trim(),
                        description.trim(),
                        targetDay,
                        selectedTimeMinutes,
                        reminderMs,
                        reminderEnabled && reminderMs != null,
                        priority,
                        category.trim(),
                        notes.trim(),
                        contactName.trim().ifBlank { null },
                        contactKey,
                        contactPhone.trim().ifBlank { null },
                        linkedCallId
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("task_dialog_save_button")
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
