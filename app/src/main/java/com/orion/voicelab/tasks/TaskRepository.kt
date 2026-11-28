package com.orion.voicelab.tasks

import com.orion.voicelab.data.PhoneCallRecord
import com.orion.voicelab.data.TaskFilter
import com.orion.voicelab.data.TaskItem
import com.orion.voicelab.data.TaskPriority
import com.orion.voicelab.data.TaskStatus
import com.orion.voicelab.memory.MemoryTextSimilarity
import com.orion.voicelab.persistence.TaskDao
import com.orion.voicelab.persistence.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

interface TaskReminderScheduler {
    fun schedule(task: TaskItem)
    fun cancel(taskId: Long)
    fun reconcileAll(tasks: List<TaskItem>, nowMs: Long = System.currentTimeMillis())
}

object NoOpTaskReminderScheduler : TaskReminderScheduler {
    override fun schedule(task: TaskItem) = Unit
    override fun cancel(taskId: Long) = Unit
    override fun reconcileAll(tasks: List<TaskItem>, nowMs: Long) = Unit
}

class TaskRepository(
    private val taskDao: TaskDao,
    private val reminderScheduler: TaskReminderScheduler = NoOpTaskReminderScheduler,
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val zoneId: ZoneId = ZoneId.systemDefault()
) {
    fun observeAllTasks(): Flow<List<TaskItem>> =
        taskDao.observeAllTasks().map { rows ->
            sortTasks(rows.map { it.toDomain() })
        }

    suspend fun getAllTasks(): List<TaskItem> =
        sortTasks(taskDao.getAllTasks().map { it.toDomain() })

    suspend fun getTaskById(id: Long): TaskItem? =
        taskDao.getTaskById(id)?.toDomain()

    suspend fun getActiveReminderTasks(): List<TaskItem> =
        taskDao.getActiveReminderTasks().map { it.toDomain() }

    suspend fun createTask(
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
    ): TaskItem {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "El título de la tarea no puede estar vacío." }
        val now = clockMs()
        val effectiveReminder = if (reminderActive) reminderAtMs else null
        val entity = TaskEntity(
            title = cleanTitle,
            description = description.trim(),
            createdAtMs = now,
            updatedAtMs = now,
            dueDateEpochDay = dueDateEpochDay,
            dueTimeMinutes = dueTimeMinutes,
            reminderAtMs = effectiveReminder,
            reminderActive = reminderActive && effectiveReminder != null,
            priority = priority.name,
            status = TaskStatus.PENDING.name,
            category = category.trim(),
            notes = notes.trim(),
            contactName = contactName?.trim()?.ifBlank { null },
            contactKey = contactKey?.trim()?.ifBlank { null },
            contactPhone = contactPhone?.trim()?.ifBlank { null },
            linkedCallRecordId = linkedCallRecordId,
            completedAtMs = null
        )
        val id = taskDao.insertTask(entity)
        val created = entity.copy(id = id).toDomain()
        if (created.reminderActive && created.reminderAtMs != null) {
            reminderScheduler.schedule(created)
        }
        return created
    }

    suspend fun updateTask(
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
    ): TaskItem? {
        val cleanTitle = title.trim()
        require(cleanTitle.isNotEmpty()) { "El título de la tarea no puede estar vacío." }
        val existing = taskDao.getTaskById(id) ?: return null
        val now = clockMs()
        val effectiveReminder = if (reminderActive && existing.status == TaskStatus.PENDING.name) reminderAtMs else null
        val activeFlag = reminderActive && effectiveReminder != null && existing.status == TaskStatus.PENDING.name

        taskDao.updateTaskDetails(
            id = id,
            title = cleanTitle,
            description = description.trim(),
            updatedAtMs = now,
            dueDateEpochDay = dueDateEpochDay,
            dueTimeMinutes = dueTimeMinutes,
            reminderAtMs = effectiveReminder,
            reminderActive = activeFlag,
            priority = priority.name,
            category = category.trim(),
            notes = notes.trim(),
            contactName = contactName?.trim()?.ifBlank { null },
            contactKey = contactKey?.trim()?.ifBlank { null },
            contactPhone = contactPhone?.trim()?.ifBlank { null },
            linkedCallRecordId = linkedCallRecordId
        )
        val updated = taskDao.getTaskById(id)?.toDomain() ?: return null
        if (updated.reminderActive && updated.reminderAtMs != null) {
            reminderScheduler.schedule(updated)
        } else {
            reminderScheduler.cancel(id)
        }
        return updated
    }

    suspend fun completeTask(id: Long): TaskItem? {
        val now = clockMs()
        taskDao.updateTaskStatus(
            id = id,
            status = TaskStatus.COMPLETED.name,
            updatedAtMs = now,
            completedAtMs = now,
            reminderActive = false
        )
        reminderScheduler.cancel(id)
        return taskDao.getTaskById(id)?.toDomain()
    }

    suspend fun cancelTask(id: Long): TaskItem? {
        val now = clockMs()
        taskDao.updateTaskStatus(
            id = id,
            status = TaskStatus.CANCELLED.name,
            updatedAtMs = now,
            completedAtMs = null,
            reminderActive = false
        )
        reminderScheduler.cancel(id)
        return taskDao.getTaskById(id)?.toDomain()
    }

    suspend fun reopenTask(id: Long): TaskItem? {
        val now = clockMs()
        val existing = taskDao.getTaskById(id) ?: return null
        val hasFutureReminder = existing.reminderAtMs != null && existing.reminderAtMs > now
        taskDao.updateTaskStatus(
            id = id,
            status = TaskStatus.PENDING.name,
            updatedAtMs = now,
            completedAtMs = null,
            reminderActive = hasFutureReminder
        )
        val reopened = taskDao.getTaskById(id)?.toDomain() ?: return null
        if (reopened.reminderActive && reopened.reminderAtMs != null) {
            reminderScheduler.schedule(reopened)
        }
        return reopened
    }

    suspend fun postponeTask(
        id: Long,
        newDueDateEpochDay: Long?,
        newDueTimeMinutes: Int?,
        newReminderAtMs: Long?
    ): TaskItem? {
        val existing = taskDao.getTaskById(id)?.toDomain() ?: return null
        // A completed or cancelled task must never regain an active reminder.
        if (existing.status != TaskStatus.PENDING) return existing
        val now = clockMs()
        val effectiveDate = newDueDateEpochDay ?: existing.dueDateEpochDay
        val effectiveTime = newDueTimeMinutes ?: existing.dueTimeMinutes
        val effectiveReminder = newReminderAtMs ?: computeReminderFromDateAndTime(effectiveDate, effectiveTime, now)
        val reminderActive = effectiveReminder != null
        taskDao.rescheduleTask(
            id = id,
            dueDateEpochDay = effectiveDate,
            dueTimeMinutes = effectiveTime,
            reminderAtMs = effectiveReminder,
            reminderActive = reminderActive,
            updatedAtMs = now
        )
        val updated = taskDao.getTaskById(id)?.toDomain() ?: return null
        if (updated.reminderActive && updated.reminderAtMs != null) {
            reminderScheduler.schedule(updated)
        } else {
            reminderScheduler.cancel(id)
        }
        return updated
    }

    suspend fun snoozeReminder(id: Long, delayMinutes: Long): TaskItem? {
        val existing = taskDao.getTaskById(id)?.toDomain() ?: return null
        if (existing.status != TaskStatus.PENDING) return existing
        val now = clockMs()
        val targetMs = now + (delayMinutes.coerceAtLeast(1L) * 60_000L)
        val zdt = Instant.ofEpochMilli(targetMs).atZone(zoneId)
        val targetDay = zdt.toLocalDate().toEpochDay()
        val targetMins = zdt.hour * 60 + zdt.minute
        taskDao.rescheduleTask(
            id = id,
            dueDateEpochDay = targetDay,
            dueTimeMinutes = targetMins,
            reminderAtMs = targetMs,
            reminderActive = true,
            updatedAtMs = now
        )
        val updated = taskDao.getTaskById(id)?.toDomain() ?: return null
        reminderScheduler.schedule(updated)
        return updated
    }

    suspend fun dismissReminder(id: Long): TaskItem? {
        val existing = taskDao.getTaskById(id) ?: return null
        taskDao.updateReminderOnly(
            id = id,
            reminderAtMs = existing.reminderAtMs,
            reminderActive = false,
            updatedAtMs = clockMs()
        )
        reminderScheduler.cancel(id)
        return taskDao.getTaskById(id)?.toDomain()
    }

    suspend fun deleteTask(id: Long) {
        reminderScheduler.cancel(id)
        taskDao.deleteTaskById(id)
    }

    suspend fun deleteFinishedTasks() {
        val finished = taskDao.getAllTasks().filter { it.status != TaskStatus.PENDING.name }
        finished.forEach { reminderScheduler.cancel(it.id) }
        taskDao.deleteFinishedTasks()
    }

    suspend fun deleteAllTasks() {
        taskDao.getAllTasks().forEach { reminderScheduler.cancel(it.id) }
        taskDao.deleteAllTasks()
    }

    suspend fun reconcileReminders() {
        val all = getAllTasks()
        reminderScheduler.reconcileAll(all, clockMs())
    }

    suspend fun createFollowUpForCall(
        callRecord: PhoneCallRecord,
        title: String,
        dueDateEpochDay: Long? = null,
        dueTimeMinutes: Int? = null,
        reminderAtMs: Long? = null,
        priority: TaskPriority = TaskPriority.HIGH,
        notes: String = ""
    ): TaskItem = createTask(
        title = title,
        description = callRecord.reason,
        dueDateEpochDay = dueDateEpochDay,
        dueTimeMinutes = dueTimeMinutes,
        reminderAtMs = reminderAtMs,
        reminderActive = reminderAtMs != null,
        priority = priority,
        category = "Llamadas",
        notes = notes,
        contactName = callRecord.contactName,
        contactPhone = callRecord.phoneNumber,
        linkedCallRecordId = callRecord.id
    )

    fun filterTasks(
        tasks: List<TaskItem>,
        filter: TaskFilter,
        searchQuery: String = ""
    ): List<TaskItem> = filter(
        tasks = tasks,
        filter = filter,
        searchQuery = searchQuery,
        nowMs = clockMs(),
        zoneId = zoneId
    )

    fun findMatchingTasks(
        query: String,
        pool: List<TaskItem>,
        pendingOnly: Boolean = true
    ): List<TaskItem> {
        val candidatesPool = if (pendingOnly) pool.filter { it.status == TaskStatus.PENDING } else pool
        val cleanQ = MemoryTextSimilarity.normalize(query)
        if (cleanQ.isEmpty() || candidatesPool.isEmpty()) return emptyList()
        val qTokens = MemoryTextSimilarity.tokenizeSignificant(query)

        val scored = candidatesPool.mapNotNull { task ->
            val searchable = listOf(task.title, task.description, task.category, task.contactName.orEmpty())
                .joinToString(" ")
            val normTarget = MemoryTextSimilarity.normalize(searchable)
            val normTitle = MemoryTextSimilarity.normalize(task.title)
            val score = when {
                normTitle == cleanQ -> 1.0
                normTitle.contains(cleanQ) -> 0.92
                normTarget.contains(cleanQ) -> 0.84
                else -> {
                    val tTokens = MemoryTextSimilarity.tokenizeSignificant(searchable)
                    val overlap = qTokens.intersect(tTokens).size
                    if (overlap == 0 || qTokens.isEmpty()) 0.0
                    else (overlap.toDouble() / qTokens.size.toDouble()) * 0.8
                }
            }
            if (score >= 0.45) task to score else null
        }.sortedWith(
            compareByDescending<Pair<TaskItem, Double>> { it.second }
                .thenByDescending { it.first.priority.sortRank }
                .thenByDescending { it.first.updatedAtMs }
        )

        if (scored.isEmpty()) return emptyList()
        val top = scored.first()
        val second = scored.getOrNull(1)
        if (second == null || (top.second >= 0.96 && second.second < 0.8)) {
            return listOf(top.first)
        }
        return scored.take(4).map { it.first }
    }

    private fun computeReminderFromDateAndTime(
        dateEpochDay: Long?,
        timeMinutes: Int?,
        nowMs: Long
    ): Long? {
        val day = dateEpochDay ?: return null
        val mins = timeMinutes ?: (9 * 60)
        val date = java.time.LocalDate.ofEpochDay(day)
        val time = LocalTime.of((mins / 60).coerceIn(0, 23), (mins % 60).coerceIn(0, 59))
        val target = date.atTime(time).atZone(zoneId).toInstant().toEpochMilli()
        return if (target > nowMs || timeMinutes != null) target else nowMs + 3_600_000L
    }

    private fun TaskEntity.toDomain(): TaskItem = TaskItem(
        id = id,
        title = title,
        description = description,
        createdAtMs = createdAtMs,
        updatedAtMs = updatedAtMs,
        dueDateEpochDay = dueDateEpochDay,
        dueTimeMinutes = dueTimeMinutes,
        reminderAtMs = reminderAtMs,
        reminderActive = reminderActive,
        priority = TaskPriority.fromStored(priority),
        status = TaskStatus.fromStored(status),
        category = category,
        notes = notes,
        contactName = contactName,
        contactKey = contactKey,
        contactPhone = contactPhone,
        linkedCallRecordId = linkedCallRecordId,
        completedAtMs = completedAtMs
    )

    companion object {
        fun filter(
            tasks: List<TaskItem>,
            filter: TaskFilter,
            searchQuery: String = "",
            nowMs: Long = System.currentTimeMillis(),
            zoneId: ZoneId = ZoneId.systemDefault()
        ): List<TaskItem> {
            val todayDay = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay()
            val byBucket = when (filter) {
                TaskFilter.TODAY -> tasks.filter {
                    it.status == TaskStatus.PENDING && it.isDueOnDate(todayDay, zoneId)
                }
                TaskFilter.UPCOMING -> tasks.filter {
                    it.status == TaskStatus.PENDING &&
                        (
                            (it.dueDateEpochDay != null && it.dueDateEpochDay > todayDay) ||
                                (it.dueDateEpochDay == null && it.reminderAtMs != null &&
                                    Instant.ofEpochMilli(it.reminderAtMs).atZone(zoneId).toLocalDate().toEpochDay() > todayDay)
                            )
                }
                TaskFilter.PENDING -> tasks.filter { it.status == TaskStatus.PENDING }
                TaskFilter.OVERDUE -> tasks.filter { it.isOverdue(nowMs, zoneId) }
                TaskFilter.COMPLETED -> tasks.filter {
                    it.status == TaskStatus.COMPLETED || it.status == TaskStatus.CANCELLED
                }
                TaskFilter.REMINDERS -> tasks.filter {
                    it.status == TaskStatus.PENDING && (it.reminderActive || it.reminderAtMs != null || it.dueTimeMinutes != null)
                }
            }
            val cleanQuery = MemoryTextSimilarity.normalize(searchQuery)
            val filtered = if (cleanQuery.isEmpty()) {
                byBucket
            } else {
                byBucket.filter { task ->
                    val haystack = MemoryTextSimilarity.normalize(
                        listOf(task.title, task.description, task.category, task.notes, task.contactName.orEmpty())
                            .joinToString(" ")
                    )
                    haystack.contains(cleanQuery)
                }
            }
            return sortTasks(filtered)
        }

        fun sortTasks(list: List<TaskItem>): List<TaskItem> =
            list.sortedWith(
                compareBy<TaskItem> { if (it.status == TaskStatus.PENDING) 0 else 1 }
                    .thenByDescending { if (it.status == TaskStatus.PENDING) it.priority.sortRank else 0 }
                    .thenBy { it.dueDateEpochDay ?: Long.MAX_VALUE }
                    .thenBy { it.dueTimeMinutes ?: Int.MAX_VALUE }
                    .thenByDescending { it.updatedAtMs }
            )
    }
}
