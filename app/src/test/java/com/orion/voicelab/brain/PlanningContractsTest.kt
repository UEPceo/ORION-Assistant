package com.orion.voicelab.brain

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test

class PlanningContractsTest {
    @Test fun detectsDependencyCyclesAndMissingDependencies() {
        val cycle = OrionPlan("p", "goal", listOf(
            PlannedStep("a", PlanStepKind.READ, "A", "", PlanRisk.LOW, setOf("b")),
            PlannedStep("b", PlanStepKind.READ, "B", "", PlanRisk.LOW, setOf("a"))))
        assertFalse(PlanValidator.validate(cycle).valid)
        val missing = cycle.copy(steps = listOf(PlannedStep("a", PlanStepKind.READ, "A", "", PlanRisk.LOW, setOf("x"))))
        assertTrue(PlanValidator.validate(missing).errors.any { it.contains("inexistente") })
    }
    @Test fun requiresFreshApprovalForExternalWrites() {
        val plan = OrionPlan("p", "send email", listOf(PlannedStep("send", PlanStepKind.SEND_MESSAGE, "Enviar", "", PlanRisk.HIGH)))
        val result = runBlocking { PlanExecutionCoordinator().execute(plan, emptySet()) { _, _ -> StepExecution(StepStatus.SUCCEEDED, "sent") } }
        assertEquals(PlanStatus.AWAITING_APPROVAL, result.status)
    }
    @Test fun blocksDownstreamStepsAfterFailure() {
        val plan = OrionPlan("p", "read then calculate", listOf(
            PlannedStep("read", PlanStepKind.READ, "Read", "", PlanRisk.LOW),
            PlannedStep("calc", PlanStepKind.CALCULATE, "Calc", "", PlanRisk.LOW, setOf("read"))))
        val result = runBlocking { PlanExecutionCoordinator().execute(plan, emptySet()) { item, _ ->
            if (item.id == "read") StepExecution(StepStatus.FAILED, "error") else StepExecution(StepStatus.SUCCEEDED, "unexpected")
        } }
        assertEquals(StepStatus.BLOCKED, result.steps["calc"]?.status)
    }
    @Test fun retriesReadOnlyStepsButNotNonIdempotentWrites() {
        val attempts = AtomicInteger(0)
        val readPlan = OrionPlan("read", "read", listOf(PlannedStep("r", PlanStepKind.READ, "Read", "", PlanRisk.LOW)),
            ExecutionBudget(maxRetriesPerStep = 1))
        val readResult = runBlocking {
            PlanExecutionCoordinator().execute(readPlan, emptySet()) { _, _ ->
                if (attempts.incrementAndGet() == 1) StepExecution(StepStatus.FAILED, "temporary")
                else StepExecution(StepStatus.SUCCEEDED, "ok")
            }
        }
        assertEquals(2, attempts.get())
        assertEquals(PlanStatus.COMPLETED, readResult.status)

        attempts.set(0)
        val writePlan = OrionPlan("write", "draft", listOf(PlannedStep("w", PlanStepKind.CREATE_DRAFT, "Draft", "", PlanRisk.HIGH)),
            ExecutionBudget(maxRetriesPerStep = 2))
        runBlocking {
            PlanExecutionCoordinator().execute(writePlan, setOf("w")) { _, _ ->
                attempts.incrementAndGet()
                StepExecution(StepStatus.FAILED, "failed")
            }
        }
        assertEquals(1, attempts.get())
    }

    @Test fun enforcesOverallExecutionTimeBudget() {
        val plan = OrionPlan("slow", "slow", listOf(PlannedStep("slow", PlanStepKind.READ, "Slow", "", PlanRisk.LOW)),
            ExecutionBudget(maxDurationMs = 30L))
        val result = runBlocking {
            PlanExecutionCoordinator().execute(plan, emptySet()) { _, _ ->
                delay(500L)
                StepExecution(StepStatus.SUCCEEDED, "too late")
            }
        }
        assertEquals(StepStatus.FAILED, result.steps["slow"]?.status)
    }

}
