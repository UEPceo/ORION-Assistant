package com.orion.voicelab.brain

import kotlinx.coroutines.runBlocking
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
}
