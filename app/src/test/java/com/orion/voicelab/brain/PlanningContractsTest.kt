package com.orion.voicelab.brain

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import com.orion.voicelab.connectors.ConnectorId
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
        val plan = OrionPlan("p", "send email", listOf(PlannedStep("send", PlanStepKind.CONNECTOR_WRITE, "Crear borrador", "", PlanRisk.HIGH, connectorId = ConnectorId.GMAIL, connectorOperation = "create_draft")))
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

    @Test fun resumesOnlyUnfinishedStepsAndNeverRepeatsSuccessfulWork() {
        val plan = OrionPlan("resume", "read then calculate", listOf(
            PlannedStep("read", PlanStepKind.READ, "Read", "", PlanRisk.LOW),
            PlannedStep("calc", PlanStepKind.CALCULATE, "Calculate", "", PlanRisk.LOW, setOf("read"))
        ))
        val attempts = AtomicInteger(0)
        val prior = mapOf("read" to StepExecution(StepStatus.SUCCEEDED, "already read", "local:doc-1"))
        val result = runBlocking {
            PlanExecutionCoordinator().executeResumable(
                plan = plan,
                approvedStepIds = emptySet(),
                runner = PlanStepRunner { step, _ ->
                    attempts.incrementAndGet()
                    assertEquals("calc", step.id)
                    StepExecution(StepStatus.SUCCEEDED, "calculated")
                },
                initialResults = prior
            )
        }
        assertEquals(1, attempts.get())
        assertEquals(PlanStatus.COMPLETED, result.status)
        assertEquals("local:doc-1", result.steps["read"]?.outputReference)
    }

    @Test fun failedStepRequiresExplicitRetrySelection() {
        val plan = OrionPlan("retry", "retry read", listOf(
            PlannedStep("read", PlanStepKind.READ, "Read", "", PlanRisk.LOW)
        ))
        val prior = mapOf("read" to StepExecution(StepStatus.FAILED, "interrupted; verify first"))
        var attempts = 0
        val noRetry = runBlocking {
            PlanExecutionCoordinator().executeResumable(
                plan, emptySet(), PlanStepRunner { _, _ ->
                    attempts++
                    StepExecution(StepStatus.SUCCEEDED, "unexpected")
                }, initialResults = prior
            )
        }
        assertEquals(0, attempts)
        assertEquals(StepStatus.FAILED, noRetry.steps["read"]?.status)

        val retry = runBlocking {
            PlanExecutionCoordinator().executeResumable(
                plan, emptySet(), PlanStepRunner { _, _ ->
                    attempts++
                    StepExecution(StepStatus.SUCCEEDED, "verified")
                }, initialResults = prior, retryStepIds = setOf("read")
            )
        }
        assertEquals(1, attempts)
        assertEquals(PlanStatus.COMPLETED, retry.status)
    }

    @Test fun cancellationPersistsPausedStateAndMarksActiveStepUncertain() {
        val plan = OrionPlan("cancel", "long task", listOf(
            PlannedStep("write", PlanStepKind.CREATE_DRAFT, "Create draft", "", PlanRisk.HIGH)
        ))
        val observed = mutableListOf<Pair<PlanStatus, Map<String, StepExecution>>>()
        try {
            runBlocking {
                PlanExecutionCoordinator().executeResumable(
                    plan, setOf("write"),
                    PlanStepRunner { _, _ -> throw kotlinx.coroutines.CancellationException("stop") },
                    observer = PlanExecutionObserver { status, steps, _ -> observed += status to steps }
                )
            }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) {
            // Expected: callers retain cancellation semantics while the observer stores a pause.
        }
        assertTrue(observed.any { it.first == PlanStatus.PAUSED })
        val paused = observed.last { it.first == PlanStatus.PAUSED }.second["write"]
        assertEquals(StepStatus.FAILED, paused?.status)
        assertTrue(paused?.summary?.contains("incierto") == true)
    }

    @Test fun rejectsCredentialMaterialFromPersistedConnectorArguments() {
        val plan = OrionPlan("secret", "unsafe", listOf(
            PlannedStep(
                "read", PlanStepKind.CONNECTOR_READ, "Read", "", PlanRisk.LOW,
                connectorId = ConnectorId.GITHUB,
                connectorOperation = "get_issue",
                connectorArguments = mapOf("access_token" to "must-not-be-saved")
            )
        ))
        val validation = PlanValidator.validate(plan)
        assertFalse(validation.valid)
        assertTrue(validation.errors.any { it.contains("credencial") })
    }

}
