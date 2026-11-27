package com.orion.voicelab.brain

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.orion.voicelab.connectors.ConnectorId
import com.orion.voicelab.persistence.OrionDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlanExecutionPersistenceTest {

    @Test
    fun planCodecRoundTripsDependenciesConnectorArgumentsAndBudget() {
        val plan = OrionPlan(
            id = "codec-plan",
            goal = "Prepare a GitHub issue summary",
            steps = listOf(
                PlannedStep("read", PlanStepKind.CONNECTOR_READ, "Read issue", "Fetch issue details", PlanRisk.LOW,
                    connectorId = ConnectorId.GITHUB,
                    connectorOperation = "get_issue",
                    connectorArguments = mapOf("repository" to "owner/repo", "issue_number" to "42")),
                PlannedStep("draft", PlanStepKind.CREATE_DRAFT, "Prepare summary", "Summarize issue", PlanRisk.MEDIUM,
                    dependsOn = setOf("read"), inputSummary = "Issue details")
            ),
            budget = ExecutionBudget(maxSteps = 8, maxRetriesPerStep = 2, maxDurationMs = 90_000L)
        )
        val decoded = PlanExecutionJsonCodec.decodePlan(PlanExecutionJsonCodec.encodePlan(plan))
        assertEquals(plan, decoded)
    }

    @Test
    fun interruptedExecutionIsPausedAndNeverAutomaticallyReplayed() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, OrionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repository = PlanExecutionRepository(database.planExecutionDao())
            val service = PersistentPlanExecutionService(repository)
            val plan = OrionPlan(
                id = "durable-plan",
                goal = "Perform one local read",
                steps = listOf(PlannedStep("read", PlanStepKind.READ, "Read", "", PlanRisk.LOW))
            )
            repository.create(plan, nowMs = 100L)
            repository.saveProgress(
                id = plan.id,
                status = PlanStatus.RUNNING,
                results = mapOf("read" to StepExecution(StepStatus.RUNNING, "running")),
                activeStepId = "read",
                nowMs = 200L
            )

            assertEquals(1, service.recoverInterruptedExecutions())
            val recovered = repository.load(plan.id)!!
            assertEquals(PlanStatus.PAUSED, recovered.status)
            assertEquals(StepStatus.FAILED, recovered.results["read"]?.status)
            assertTrue(recovered.results["read"]!!.summary.contains("incierto"))

            var attempts = 0
            val held = service.execute(
                planId = plan.id,
                approvedStepIds = emptySet(),
                runner = PlanStepRunner { _, _ ->
                    attempts++
                    StepExecution(StepStatus.SUCCEEDED, "must not run automatically")
                }
            )
            assertEquals(0, attempts)
            assertEquals(StepStatus.FAILED, held.steps["read"]?.status)

            val retried = service.execute(
                planId = plan.id,
                approvedStepIds = emptySet(),
                runner = PlanStepRunner { _, _ ->
                    attempts++
                    StepExecution(StepStatus.SUCCEEDED, "user-approved retry")
                },
                retryStepIds = setOf("read")
            )
            assertEquals(1, attempts)
            assertEquals(PlanStatus.COMPLETED, retried.status)
        } finally {
            database.close()
        }
    }
    @Test
    fun structuredProposalParserAcceptsValidReadOnlyPlanButDoesNotExecuteIt() {
        val raw = """
            {
              "goal": "Summarize one GitHub issue",
              "budget": {"maxSteps": 4, "maxRetriesPerStep": 1, "maxDurationMs": 60000},
              "steps": [
                {
                  "id": "read_issue",
                  "kind": "CONNECTOR_READ",
                  "title": "Read issue",
                  "rationale": "Collect source data",
                  "risk": "LOW",
                  "dependsOn": [],
                  "requiresApproval": false,
                  "connectorId": "GITHUB",
                  "connectorOperation": "get_issue",
                  "connectorArguments": {"repository": "owner/repo", "number": "12"}
                }
              ]
            }
        """.trimIndent()
        val parsed = PlanProposalParser.parse(raw, "generated-plan")
        assertTrue(parsed is PlanProposalParseResult.Valid)
        val plan = (parsed as PlanProposalParseResult.Valid).plan
        assertEquals(PlanStatus.READY, plan.status)
        assertEquals(1, plan.steps.size)
        assertEquals("get_issue", plan.steps.single().connectorOperation)
    }

    @Test
    fun structuredProposalParserRejectsMissingConnectorAndCredentialArguments() {
        val missingConnector = """
            {"goal":"Unsafe action","steps":[{"id":"send","kind":"SEND_MESSAGE","title":"Send","risk":"HIGH","dependsOn":[]}]}
        """.trimIndent()
        assertTrue(PlanProposalParser.parse(missingConnector, "bad-1") is PlanProposalParseResult.Invalid)

        val credential = """
            {
              "goal":"Credential leak",
              "steps":[{
                "id":"read","kind":"CONNECTOR_READ","title":"Read","risk":"LOW","dependsOn":[],
                "connectorId":"GITHUB","connectorOperation":"get_issue",
                "connectorArguments":{"access_token":"do-not-persist"}
              }]
            }
        """.trimIndent()
        val parsed = PlanProposalParser.parse(credential, "bad-2")
        assertTrue(parsed is PlanProposalParseResult.Invalid)
    }

    @Test
    fun structuredProposalParserRejectsConnectorOperationsNotImplementedByProvider() {
        val raw = """
            {
              "goal":"Read repository metadata",
              "steps":[{
                "id":"repo","kind":"CONNECTOR_READ","title":"Read repository","risk":"LOW",
                "connectorId":"GITHUB","connectorOperation":"get_repository",
                "connectorArguments":{"repository":"owner/repo"}
              }]
            }
        """.trimIndent()
        val parsed = PlanProposalParser.parse(raw, "unsupported-op")
        assertTrue(parsed is PlanProposalParseResult.Invalid)
    }

}
