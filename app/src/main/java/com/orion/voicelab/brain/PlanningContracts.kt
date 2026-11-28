package com.orion.voicelab.brain

import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

enum class PlanStepKind { THINK, SEARCH, READ, CALCULATE, CREATE_DRAFT, CONNECTOR_READ, CONNECTOR_WRITE, SEND_MESSAGE, PLACE_CALL, DELETE_DATA, PURCHASE, LOCAL_CREATE_TASK, LOCAL_CREATE_NOTE, LOCAL_CREATE_REMINDER }
enum class PlanRisk { LOW, MEDIUM, HIGH, CRITICAL }
enum class PlanStatus { DRAFT, READY, AWAITING_APPROVAL, RUNNING, PAUSED, COMPLETED, PARTIAL, FAILED, CANCELLED }
enum class StepStatus { PENDING, RUNNING, SUCCEEDED, FAILED, BLOCKED, SKIPPED }

data class PlannedStep(
    val id: String, val kind: PlanStepKind, val title: String, val rationale: String,
    val risk: PlanRisk, val dependsOn: Set<String> = emptySet(),
    val requiresApproval: Boolean = risk >= PlanRisk.MEDIUM,
    val idempotencyKey: String? = null, val inputSummary: String = "",
    val connectorId: ConnectorId? = null, val connectorOperation: String? = null,
    val connectorArguments: Map<String, String> = emptyMap()
)
data class ExecutionBudget(val maxSteps: Int = 12, val maxRetriesPerStep: Int = 1, val maxDurationMs: Long = 5 * 60_000L)
data class OrionPlan(val id: String, val goal: String, val steps: List<PlannedStep>, val budget: ExecutionBudget = ExecutionBudget(), val status: PlanStatus = PlanStatus.DRAFT)
data class PlanValidation(val valid: Boolean, val errors: List<String>, val approvalRequiredStepIds: Set<String>, val orderedStepIds: List<String>)

object PlanValidator {
    fun validate(plan: OrionPlan): PlanValidation {
        val errors = mutableListOf<String>()
        if (plan.id.isBlank()) errors += "El plan necesita un identificador."
        if (plan.goal.isBlank()) errors += "El plan necesita un objetivo."
        if (plan.id.length > 100 || plan.goal.length > 1_200) errors += "El identificador u objetivo del plan supera el límite permitido."
        if (plan.steps.isEmpty()) errors += "El plan no contiene pasos."
        if (plan.budget.maxSteps !in 1..50) errors += "El límite de pasos debe estar entre 1 y 50."
        if (plan.steps.size > plan.budget.maxSteps) errors += "El plan supera el máximo de pasos."
        if (plan.budget.maxDurationMs !in 1..30L * 60_000L) errors += "El presupuesto temporal no es válido."
        if (plan.budget.maxRetriesPerStep !in 0..3) errors += "El número de reintentos no es válido."
        val duplicateIds = plan.steps.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) errors += "Hay identificadores de paso duplicados."
        val ids = plan.steps.map { it.id }.toSet()
        if (plan.steps.any { it.id.isBlank() || it.title.isBlank() }) errors += "Cada paso necesita ID y título."
        if (plan.steps.any {
                it.id.length > 80 || it.title.length > 180 || it.rationale.length > 1_200 ||
                    it.inputSummary.length > 1_200 || it.connectorArguments.size > 30 ||
                    it.connectorArguments.any { (key, value) -> key.length > 100 || value.length > 1_200 }
            }) errors += "Un paso supera los límites de tamaño o argumentos."
        plan.steps.forEach { item ->
            if (item.id in item.dependsOn) errors += "Un paso no puede depender de sí mismo: ${item.id}."
            if (item.kind in EXTERNAL_KINDS && (item.connectorId == null || item.connectorOperation.isNullOrBlank())) errors += "El paso externo ${item.id} necesita conector y operación explícitos."
            (item.dependsOn - ids).forEach { errors += "Dependencia inexistente: $it." }
            if (item.kind in WRITE_KINDS && !item.requiresApproval) errors += "La acción ${item.id} debe requerir confirmación contextual."
            if (item.kind in LOCAL_WRITE_KINDS) {
                if (!item.requiresApproval || item.risk < PlanRisk.MEDIUM) errors += "La escritura local ${item.id} requiere confirmación contextual y riesgo MEDIUM o superior."
                if (item.connectorId != null || !item.connectorOperation.isNullOrBlank()) errors += "El paso local ${item.id} no puede declarar un conector."
                if (item.connectorArguments["title"].isNullOrBlank() && item.kind != PlanStepKind.LOCAL_CREATE_NOTE) errors += "El paso local ${item.id} necesita título."
                if (item.kind == PlanStepKind.LOCAL_CREATE_REMINDER && item.connectorArguments["reminderAtMs"].isNullOrBlank()) errors += "El recordatorio ${item.id} necesita fecha y hora (reminderAtMs)."
                if (item.kind == PlanStepKind.LOCAL_CREATE_NOTE && item.connectorArguments["content"].isNullOrBlank() && item.connectorArguments["notes"].isNullOrBlank()) errors += "La nota ${item.id} necesita contenido."
                if (item.kind == PlanStepKind.LOCAL_CREATE_REMINDER) {
                    val rawReminder = item.connectorArguments["reminderAtMs"].orEmpty()
                    val timestamp = rawReminder.toLongOrNull()
                    if (timestamp != null && timestamp <= System.currentTimeMillis()) errors += "El recordatorio ${item.id} debe estar en el futuro."
                    if (timestamp == null && PlanOutputReferences.find(listOf(rawReminder)).isEmpty()) errors += "El recordatorio ${item.id} tiene una fecha inválida."
                }
                val allowed = when (item.kind) {
                    PlanStepKind.LOCAL_CREATE_TASK -> setOf("title", "description", "notes", "category", "priority", "dueDateEpochDay", "dueTimeMinutes")
                    PlanStepKind.LOCAL_CREATE_NOTE -> setOf("title", "content", "notes", "category")
                    PlanStepKind.LOCAL_CREATE_REMINDER -> setOf("title", "description", "notes", "category", "priority", "dueDateEpochDay", "dueTimeMinutes", "reminderAtMs")
                    else -> emptySet()
                }
                val unknown = item.connectorArguments.keys - allowed
                if (unknown.isNotEmpty()) errors += "El paso local ${item.id} contiene campos no permitidos: ${unknown.joinToString()}."
            }
            PlanOutputReferences.find(item.connectorArguments.values + item.inputSummary).forEach { ref ->
                if (ref.stepId !in ids) errors += "Referencia de salida inexistente: ${ref.stepId}."
                if (ref.stepId !in item.dependsOn) errors += "El paso ${item.id} debe declarar dependencia de ${ref.stepId} para usar su salida."
            }
            val secretKeyMarkers = listOf("password", "passwd", "secret", "token", "authorization", "api_key", "apikey", "credential")
            if (item.connectorArguments.keys.any { key ->
                    val normalized = key.lowercase().replace("-", "_")
                    secretKeyMarkers.any(normalized::contains)
                }) errors += "El paso ${item.id} contiene una clave de credencial que no puede persistirse."
        }
        val ordered = topologicalOrder(plan.steps)
        if (ordered.size != plan.steps.size) errors += "El plan contiene un ciclo de dependencias."
        return PlanValidation(errors.isEmpty(), errors.distinct(),
            plan.steps.filter { it.requiresApproval || it.risk >= PlanRisk.MEDIUM }.map { it.id }.toSet(), ordered)
    }

    private val EXTERNAL_KINDS = setOf(PlanStepKind.CONNECTOR_READ, PlanStepKind.CONNECTOR_WRITE, PlanStepKind.SEND_MESSAGE, PlanStepKind.PLACE_CALL, PlanStepKind.DELETE_DATA, PlanStepKind.PURCHASE)
    private val WRITE_KINDS = setOf(PlanStepKind.CONNECTOR_WRITE, PlanStepKind.SEND_MESSAGE, PlanStepKind.PLACE_CALL, PlanStepKind.DELETE_DATA, PlanStepKind.PURCHASE, PlanStepKind.LOCAL_CREATE_TASK, PlanStepKind.LOCAL_CREATE_NOTE, PlanStepKind.LOCAL_CREATE_REMINDER)
    private val LOCAL_WRITE_KINDS = setOf(PlanStepKind.LOCAL_CREATE_TASK, PlanStepKind.LOCAL_CREATE_NOTE, PlanStepKind.LOCAL_CREATE_REMINDER)

    private fun topologicalOrder(steps: List<PlannedStep>): List<String> {
        val remaining = steps.associate { it.id to it.dependsOn.toMutableSet() }.toMutableMap()
        if (remaining.size != steps.size) return emptyList()
        val result = mutableListOf<String>()
        while (remaining.isNotEmpty()) {
            val ready = remaining.filterValues { it.isEmpty() }.keys.sorted()
            if (ready.isEmpty()) return emptyList()
            ready.forEach { id ->
                result += id
                remaining.remove(id)
                remaining.values.forEach { it.remove(id) }
            }
        }
        return result
    }
}

data class StepExecution(val status: StepStatus, val summary: String, val outputReference: String? = null)
data class PlanOutputReference(val stepId: String, val field: String)
object PlanOutputReferences {
    private val token = Regex("\\$\\{([A-Za-z0-9_-]{1,80})\\.(output|summary)}")
    fun find(values: Iterable<String>): List<PlanOutputReference> = values.flatMap { value ->
        token.findAll(value).map { PlanOutputReference(it.groupValues[1], it.groupValues[2]) }.toList()
    }
    fun resolve(value: String, results: Map<String, StepExecution>): String? {
        var unresolved = false
        val resolved = token.replace(value) { match ->
            val execution = results[match.groupValues[1]]
            val replacement = when (match.groupValues[2]) {
                "output" -> execution?.outputReference
                "summary" -> execution?.summary
                else -> null
            }
            if (execution?.status != StepStatus.SUCCEEDED || replacement == null) {
                unresolved = true
                ""
            } else replacement.take(1_200)
        }
        return if (unresolved || Regex("\\$\\{[^}]+}").containsMatchIn(resolved)) null else resolved.take(1_200)
    }
}
fun interface PlanStepRunner { suspend fun run(step: PlannedStep, budget: ExecutionBudget): StepExecution }
data class PlanExecutionReport(val planId: String, val status: PlanStatus, val steps: Map<String, StepExecution>, val blockedReason: String? = null)

fun interface PlanExecutionObserver {
    suspend fun onProgress(status: PlanStatus, steps: Map<String, StepExecution>, activeStepId: String?)
}

/** Approval is bound to exact step IDs for this invocation; no approval persists. */
class PlanExecutionCoordinator {
    suspend fun execute(plan: OrionPlan, approvedStepIds: Set<String>, runner: PlanStepRunner): PlanExecutionReport =
        executeResumable(plan, approvedStepIds, runner)

    suspend fun executeResumable(
        plan: OrionPlan,
        approvedStepIds: Set<String>,
        runner: PlanStepRunner,
        initialResults: Map<String, StepExecution> = emptyMap(),
        retryStepIds: Set<String> = emptySet(),
        observer: PlanExecutionObserver = PlanExecutionObserver { _, _, _ -> }
    ): PlanExecutionReport {
        val validation = PlanValidator.validate(plan)
        if (!validation.valid) return PlanExecutionReport(plan.id, PlanStatus.FAILED, emptyMap(), validation.errors.joinToString(" "))
        val unknownResults = initialResults.keys - plan.steps.map { it.id }.toSet()
        if (unknownResults.isNotEmpty()) return PlanExecutionReport(plan.id, PlanStatus.FAILED, emptyMap(), "El estado guardado contiene pasos que ya no existen.")
        val alreadySucceeded = initialResults.filterValues { it.status == StepStatus.SUCCEEDED }.keys
        val missing = validation.approvalRequiredStepIds - approvedStepIds - alreadySucceeded
        if (missing.isNotEmpty()) {
            val report = PlanExecutionReport(plan.id, PlanStatus.AWAITING_APPROVAL, initialResults, "Falta autorización expresa para: ${missing.sorted().joinToString()}.")
            observer.onProgress(report.status, report.steps, null)
            return report
        }
        val results = linkedMapOf<String, StepExecution>().apply { putAll(initialResults) }
        val startedAt = System.nanoTime()
        var activeStepId: String? = null
        observer.onProgress(PlanStatus.RUNNING, results.toMap(), null)
        try {
            for (id in validation.orderedStepIds) {
                val previous = results[id]
                if (previous?.status == StepStatus.SUCCEEDED) continue
                if (previous?.status == StepStatus.FAILED && id !in retryStepIds) continue
                if (previous != null && previous.status !in setOf(StepStatus.FAILED, StepStatus.BLOCKED, StepStatus.SKIPPED, StepStatus.PENDING)) continue

                val original = plan.steps.first { it.id == id }
                val argsResolved = original.connectorArguments.mapValues { (_, value) -> PlanOutputReferences.resolve(value, results) }
                val summaryResolved = PlanOutputReferences.resolve(original.inputSummary, results)
                if (argsResolved.values.any { it == null } || summaryResolved == null) {
                    results[id] = StepExecution(StepStatus.BLOCKED, "No se pudo resolver una salida de un paso anterior.")
                    observer.onProgress(PlanStatus.RUNNING, results.toMap(), null)
                    continue
                }
                val item = original.copy(
                    connectorArguments = argsResolved.mapValues { it.value.orEmpty() },
                    inputSummary = summaryResolved
                )
                val failedDependency = item.dependsOn.firstOrNull { results[it]?.status != StepStatus.SUCCEEDED }
                if (failedDependency != null) {
                    results[id] = StepExecution(StepStatus.BLOCKED, "Bloqueado por el paso $failedDependency.")
                    observer.onProgress(PlanStatus.RUNNING, results.toMap(), null)
                    continue
                }
                val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
                val remainingMs = plan.budget.maxDurationMs - elapsedMs
                if (remainingMs <= 0L) {
                    results[id] = StepExecution(StepStatus.FAILED, "Se agotó el presupuesto temporal del plan.")
                    observer.onProgress(PlanStatus.RUNNING, results.toMap(), null)
                    continue
                }
                val retryAllowed = item.kind in setOf(PlanStepKind.THINK, PlanStepKind.SEARCH, PlanStepKind.READ, PlanStepKind.CALCULATE, PlanStepKind.CONNECTOR_READ) || !item.idempotencyKey.isNullOrBlank()
                var attempt = 0
                var execution: StepExecution
                activeStepId = id
                results[id] = StepExecution(StepStatus.RUNNING, "En ejecución.")
                observer.onProgress(PlanStatus.RUNNING, results.toMap(), activeStepId)
                do {
                    execution = try {
                        withTimeout(remainingMs) { runner.run(item, plan.budget) }
                    } catch (cancelled: CancellationException) {
                        if (cancelled is TimeoutCancellationException) {
                            StepExecution(StepStatus.FAILED, "El paso superó el tiempo disponible. El resultado externo puede ser incierto; verifica antes de reintentar.")
                        } else throw cancelled
                    } catch (error: Exception) {
                        StepExecution(StepStatus.FAILED, "Fallo controlado: ${error::class.java.simpleName}.")
                    }
                    if (execution.status != StepStatus.FAILED || !retryAllowed || attempt >= plan.budget.maxRetriesPerStep) break
                    attempt++
                } while (true)
                results[id] = execution
                activeStepId = null
                observer.onProgress(PlanStatus.RUNNING, results.toMap(), null)
            }
        } catch (cancelled: CancellationException) {
            activeStepId?.let { id ->
                results[id] = StepExecution(StepStatus.FAILED, "Ejecución interrumpida. El resultado externo puede ser incierto; verifica antes de reintentar.")
            }
            withContext(NonCancellable) {
                observer.onProgress(PlanStatus.PAUSED, results.toMap(), null)
            }
            throw cancelled
        }
        val status = when {
            results.values.count { it.status == StepStatus.SUCCEEDED } == plan.steps.size -> PlanStatus.COMPLETED
            results.values.any { it.status == StepStatus.SUCCEEDED } -> PlanStatus.PARTIAL
            results.values.any { it.status == StepStatus.FAILED || it.status == StepStatus.BLOCKED } -> PlanStatus.FAILED
            else -> PlanStatus.PARTIAL
        }
        observer.onProgress(status, results.toMap(), null)
        return PlanExecutionReport(plan.id, status, results)
    }
}
