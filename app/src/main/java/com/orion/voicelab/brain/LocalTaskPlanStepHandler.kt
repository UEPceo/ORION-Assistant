package com.orion.voicelab.brain

import com.orion.voicelab.tasks.TaskRepository
import com.orion.voicelab.data.TaskPriority
import java.time.Instant
import kotlinx.coroutines.CancellationException

/** Local-only plan actions. The coordinator requires contextual approval before these writes. */
class LocalTaskPlanStepHandler(private val tasks: TaskRepository) : LocalPlanStepHandler {
    override suspend fun run(step: PlannedStep, budget: ExecutionBudget): StepExecution {
        val args = step.connectorArguments
        val dueDateEpochDay = args["dueDateEpochDay"]?.toLongOrNull()
        val dueTimeMinutes = args["dueTimeMinutes"]?.toIntOrNull()
        if ("dueDateEpochDay" in args && dueDateEpochDay == null) {
            return StepExecution(StepStatus.FAILED, "La fecha de vencimiento no es válida.")
        }
        if ("dueTimeMinutes" in args && (dueTimeMinutes == null || dueTimeMinutes !in 0..1439)) {
            return StepExecution(StepStatus.FAILED, "La hora debe estar entre 0 y 1439 minutos.")
        }
        return try {
            when (step.kind) {
                PlanStepKind.LOCAL_CREATE_TASK -> {
                    val created = tasks.createTask(
                        title = args["title"].orEmpty(),
                        description = args["description"].orEmpty(),
                        notes = args["notes"].orEmpty(),
                        dueDateEpochDay = dueDateEpochDay,
                        dueTimeMinutes = dueTimeMinutes,
                        category = args["category"].orEmpty().ifBlank { "Plan ORION" },
                        priority = parsePriority(args["priority"])
                    )
                    StepExecution(StepStatus.SUCCEEDED, "Tarea local guardada: ${created.title}.", "task:${created.id}")
                }
                PlanStepKind.LOCAL_CREATE_NOTE -> {
                    val title = args["title"].orEmpty().ifBlank { "Nota de ORION" }
                    val body = args["content"].orEmpty().ifBlank { args["notes"].orEmpty() }
                    val created = tasks.createTask(
                        title = title,
                        description = body,
                        notes = body,
                        category = args["category"].orEmpty().ifBlank { "Nota" },
                        priority = TaskPriority.NORMAL
                    )
                    StepExecution(StepStatus.SUCCEEDED, "Nota guardada localmente: ${created.title}.", "note-task:${created.id}")
                }
                PlanStepKind.LOCAL_CREATE_REMINDER -> {
                    val reminderAt = args["reminderAtMs"]?.toLongOrNull()
                        ?: return StepExecution(StepStatus.FAILED, "Fecha del recordatorio inválida.")
                    if (reminderAt <= System.currentTimeMillis()) return StepExecution(StepStatus.FAILED, "El recordatorio debe programarse en el futuro.")
                    val instant = Instant.ofEpochMilli(reminderAt)
                    val created = tasks.createTask(
                        title = args["title"].orEmpty(),
                        description = args["description"].orEmpty(),
                        notes = args["notes"].orEmpty(),
                        category = args["category"].orEmpty().ifBlank { "Recordatorio" },
                        priority = parsePriority(args["priority"]),
                        dueDateEpochDay = dueDateEpochDay ?: instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(),
                        dueTimeMinutes = dueTimeMinutes ?: instant.atZone(java.time.ZoneId.systemDefault()).toLocalTime().let { it.hour * 60 + it.minute },
                        reminderAtMs = reminderAt,
                        reminderActive = true
                    )
                    StepExecution(StepStatus.SUCCEEDED, "Recordatorio local programado: ${created.title}.", "reminder-task:${created.id}")
                }
                else -> StepExecution(StepStatus.FAILED, "Tipo de acción local no soportado.")
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            StepExecution(StepStatus.FAILED, "No se pudo guardar localmente: ${error.message?.take(180) ?: error.javaClass.simpleName}.")
        }
    }

    private fun parsePriority(raw: String?): TaskPriority =
        raw?.uppercase()?.let { runCatching { TaskPriority.valueOf(it) }.getOrNull() } ?: TaskPriority.NORMAL
}
