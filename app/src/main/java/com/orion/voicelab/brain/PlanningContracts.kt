package com.orion.voicelab.brain

import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

enum class PlanStepKind { THINK, SEARCH, READ, CALCULATE, CREATE_DRAFT, CONNECTOR_READ, CONNECTOR_WRITE, SEND_MESSAGE, PLACE_CALL, DELETE_DATA, PURCHASE }
enum class PlanRisk { LOW, MEDIUM, HIGH, CRITICAL }
enum class PlanStatus { DRAFT, READY, AWAITING_APPROVAL, RUNNING, COMPLETED, PARTIAL, FAILED, CANCELLED }
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
        if (plan.steps.isEmpty()) errors += "El plan no contiene pasos."
        if (plan.budget.maxSteps !in 1..50) errors += "El límite de pasos debe estar entre 1 y 50."
        if (plan.steps.size > plan.budget.maxSteps) errors += "El plan supera el máximo de pasos."
        if (plan.budget.maxDurationMs !in 1..30L * 60_000L) errors += "El presupuesto temporal no es válido."
        if (plan.budget.maxRetriesPerStep !in 0..3) errors += "El número de reintentos no es válido."
        val duplicateIds = plan.steps.groupBy { it.id }.filterValues { it.size > 1 }.keys
        if (duplicateIds.isNotEmpty()) errors += "Hay identificadores de paso duplicados."
        val ids = plan.steps.map { it.id }.toSet()
        if (plan.steps.any { it.id.isBlank() || it.title.isBlank() }) errors += "Cada paso necesita ID y título."
        plan.steps.forEach { item ->
            if (item.id in item.dependsOn) errors += "Un paso no puede depender de sí mismo: ${item.id}."
            if (item.kind in setOf(PlanStepKind.CONNECTOR_READ, PlanStepKind.CONNECTOR_WRITE, PlanStepKind.SEND_MESSAGE, PlanStepKind.PLACE_CALL, PlanStepKind.DELETE_DATA, PlanStepKind.PURCHASE) && (item.connectorId == null || item.connectorOperation.isNullOrBlank())) errors += "El paso externo ${item.id} necesita conector y operación explícitos."
            (item.dependsOn - ids).forEach { errors += "Dependencia inexistente: $it." }
            if (item.kind in setOf(PlanStepKind.CONNECTOR_WRITE, PlanStepKind.SEND_MESSAGE, PlanStepKind.PLACE_CALL, PlanStepKind.DELETE_DATA, PlanStepKind.PURCHASE) && !item.requiresApproval) {
                errors += "La acción externa ${item.id} debe requerir confirmación."
            }
        }
        val ordered = topologicalOrder(plan.steps)
        if (ordered.size != plan.steps.size) errors += "El plan contiene un ciclo de dependencias."
        return PlanValidation(errors.isEmpty(), errors.distinct(),
            plan.steps.filter { it.requiresApproval || it.risk >= PlanRisk.MEDIUM }.map { it.id }.toSet(), ordered)
    }

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
fun interface PlanStepRunner { suspend fun run(step: PlannedStep, budget: ExecutionBudget): StepExecution }
data class PlanExecutionReport(val planId: String, val status: PlanStatus, val steps: Map<String, StepExecution>, val blockedReason: String? = null)

/** Approval is bound to exact step IDs for this invocation; no approval persists. */
class PlanExecutionCoordinator {
    suspend fun execute(plan: OrionPlan, approvedStepIds: Set<String>, runner: PlanStepRunner): PlanExecutionReport {
        val validation = PlanValidator.validate(plan)
        if (!validation.valid) return PlanExecutionReport(plan.id, PlanStatus.FAILED, emptyMap(), validation.errors.joinToString(" "))
        val missing = validation.approvalRequiredStepIds - approvedStepIds
        if (missing.isNotEmpty()) return PlanExecutionReport(plan.id, PlanStatus.AWAITING_APPROVAL, emptyMap(), "Falta autorización expresa para: ${missing.sorted().joinToString()}.")
        val results = linkedMapOf<String, StepExecution>()
        val startedAt = System.nanoTime()
        for (id in validation.orderedStepIds) {
            val item = plan.steps.first { it.id == id }
            val failedDependency = item.dependsOn.firstOrNull { results[it]?.status != StepStatus.SUCCEEDED }
            if (failedDependency != null) {
                results[id] = StepExecution(StepStatus.BLOCKED, "Bloqueado por el paso $failedDependency.")
                continue
            }
            val elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L
            val remainingMs = plan.budget.maxDurationMs - elapsedMs
            if (remainingMs <= 0L) {
                results[id] = StepExecution(StepStatus.FAILED, "Se agotó el presupuesto temporal del plan.")
                continue
            }
            val retryAllowed = item.kind in setOf(PlanStepKind.THINK, PlanStepKind.SEARCH, PlanStepKind.READ, PlanStepKind.CALCULATE, PlanStepKind.CONNECTOR_READ) || !item.idempotencyKey.isNullOrBlank()
            var attempt = 0
            var execution: StepExecution
            do {
                execution = try {
                    withTimeout(remainingMs) { runner.run(item, plan.budget) }
                } catch (cancelled: CancellationException) {
                    if (cancelled is TimeoutCancellationException) {
                        StepExecution(StepStatus.FAILED, "El paso superó el tiempo disponible.")
                    } else throw cancelled
                } catch (error: Exception) {
                    StepExecution(StepStatus.FAILED, "Fallo controlado: ${error::class.java.simpleName}.")
                }
                if (execution.status != StepStatus.FAILED || !retryAllowed || attempt >= plan.budget.maxRetriesPerStep) break
                attempt++
            } while (true)
            results[id] = execution
        }
        val status = when {
            results.values.all { it.status == StepStatus.SUCCEEDED } -> PlanStatus.COMPLETED
            results.values.any { it.status == StepStatus.SUCCEEDED } -> PlanStatus.PARTIAL
            else -> PlanStatus.FAILED
        }
        return PlanExecutionReport(plan.id, status, results)
    }
}
