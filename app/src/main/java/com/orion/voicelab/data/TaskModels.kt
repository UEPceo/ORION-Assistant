package com.orion.voicelab.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

enum class TaskPriority(val label: String, val sortRank: Int) {
    LOW("Baja", 1),
    NORMAL("Normal", 2),
    HIGH("Alta", 3),
    URGENT("Urgente", 4);

    companion object {
        fun fromStored(raw: String?): TaskPriority =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: NORMAL
    }
}

enum class TaskStatus(val label: String) {
    PENDING("Pendiente"),
    COMPLETED("Completada"),
    CANCELLED("Cancelada");

    companion object {
        fun fromStored(raw: String?): TaskStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PENDING
    }
}

enum class TaskFilter(val label: String) {
    TODAY("Hoy"),
    UPCOMING("Próximas"),
    PENDING("Pendientes"),
    OVERDUE("Atrasadas"),
    COMPLETED("Completadas"),
    REMINDERS("Recordatorios")
}

data class TaskItem(
    val id: Long = 0L,
    val title: String,
    val description: String = "",
    val createdAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val reminderAtMs: Long? = null,
    val reminderActive: Boolean = false,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val status: TaskStatus = TaskStatus.PENDING,
    val category: String = "",
    val notes: String = "",
    val contactName: String? = null,
    val contactKey: String? = null,
    val contactPhone: String? = null,
    val linkedCallRecordId: Long? = null,
    val completedAtMs: Long? = null
) {
    fun dueAtEpochMs(zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        val day = dueDateEpochDay ?: return reminderAtMs
        val date = LocalDate.ofEpochDay(day)
        val mins = dueTimeMinutes ?: (23 * 60 + 59)
        val time = LocalTime.of((mins / 60).coerceIn(0, 23), (mins % 60).coerceIn(0, 59))
        return date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
    }

    fun isDueOnDate(targetDay: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (dueDateEpochDay != null) return dueDateEpochDay == targetDay
        val rem = reminderAtMs ?: return false
        val remDay = Instant.ofEpochMilli(rem).atZone(zoneId).toLocalDate().toEpochDay()
        return remDay == targetDay
    }

    fun isOverdue(nowMs: Long, zoneId: ZoneId = ZoneId.systemDefault()): Boolean {
        if (status != TaskStatus.PENDING) return false
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay()
        val day = dueDateEpochDay
        if (day != null) {
            if (day < today) return true
            if (day == today && dueTimeMinutes != null) {
                val dueMs = dueAtEpochMs(zoneId)
                if (dueMs != null && dueMs < nowMs) return true
            }
        }
        val rem = reminderAtMs
        return rem != null && reminderActive && rem < nowMs
    }
}

sealed interface PendingTaskPrompt {
    data class DisambiguateTasks(
        val actionLabel: String,
        val operation: PendingTaskOperation,
        val candidates: List<TaskItem>,
        val targetDueDateEpochDay: Long? = null,
        val targetDueTimeMinutes: Int? = null,
        val targetReminderAtMs: Long? = null
    ) : PendingTaskPrompt

    data class ClarifyTemporalAmbiguity(
        val draftTitle: String,
        val priority: TaskPriority,
        val category: String,
        val isCallFollowUp: Boolean,
        val contactQuery: String?,
        val linkLatestOrActiveCall: Boolean = false,
        val dateEpochDay: Long,
        val optionA24HourMinutes: Int,
        val optionB24HourMinutes: Int,
        val promptMessage: String
    ) : PendingTaskPrompt

    data class ClarifyMissingTitle(
        val priority: TaskPriority,
        val category: String,
        val dueDateEpochDay: Long?,
        val dueTimeMinutes: Int?,
        val reminderAtMs: Long?,
        val promptMessage: String
    ) : PendingTaskPrompt
}

enum class PendingTaskOperation {
    COMPLETE,
    POSTPONE,
    CANCEL
}

enum class TaskQueryScope {
    ALL_PENDING,
    TODAY,
    UPCOMING,
    OVERDUE,
    REMINDERS,
    SPECIFIC_DATE
}

sealed interface TaskIntentAction {
    data object None : TaskIntentAction

    data class CreateTask(
        val title: String,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val dueDateEpochDay: Long? = null,
        val dueTimeMinutes: Int? = null,
        val reminderAtMs: Long? = null,
        val category: String = "",
        val contactQuery: String? = null,
        val isCallFollowUp: Boolean = false,
        val linkLatestOrActiveCall: Boolean = false,
        val ambiguityPrompt: PendingTaskPrompt.ClarifyTemporalAmbiguity? = null,
        val missingTitlePrompt: PendingTaskPrompt.ClarifyMissingTitle? = null
    ) : TaskIntentAction

    data class QueryTasks(
        val scope: TaskQueryScope,
        val specificDateEpochDay: Long? = null
    ) : TaskIntentAction

    data class CompleteTask(
        val query: String
    ) : TaskIntentAction

    data class PostponeTask(
        val query: String,
        val refersToLastTask: Boolean = false,
        val newDueDateEpochDay: Long? = null,
        val newDueTimeMinutes: Int? = null,
        val newReminderAtMs: Long? = null
    ) : TaskIntentAction

    data class CancelTask(
        val query: String
    ) : TaskIntentAction

    data class SelectTaskCandidateByIndex(
        val index: Int
    ) : TaskIntentAction

    data class SelectTaskCandidateByTitle(
        val query: String
    ) : TaskIntentAction

    data class ResolveTemporalChoice(
        val chosenTimeMinutes: Int
    ) : TaskIntentAction

    data class ProvideMissingTitle(
        val title: String
    ) : TaskIntentAction

    data object CancelPendingPrompt : TaskIntentAction
}
