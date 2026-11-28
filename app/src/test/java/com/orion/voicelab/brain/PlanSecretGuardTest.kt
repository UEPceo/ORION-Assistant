package com.orion.voicelab.brain

import org.junit.Assert.*
import org.junit.Test

class PlanSecretGuardTest {
    @Test fun rejectsProviderTokensInPlanArguments() {
        val plan = OrionPlan(
            id = "secret-plan",
            goal = "Read repository",
            steps = listOf(PlannedStep(
                "read", PlanStepKind.READ, "Read", "", PlanRisk.LOW,
                connectorArguments = mapOf("header" to "Bearer abcdefghijklmnopqrstuvwxyz123456")
            ))
        )
        val result = PlanValidator.validate(plan)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("credenciales") || it.contains("secretos") })
    }

    @Test fun rejectsSecretLikeValuesEvenWhenArgumentNameLooksHarmless() {
        val plan = OrionPlan(
            id = "secret-value",
            goal = "Read repository",
            steps = listOf(PlannedStep(
                "read", PlanStepKind.READ, "Read", "", PlanRisk.LOW,
                connectorArguments = mapOf("query" to "ghp_abcdefghijklmnopqrstuvwxyz123456")
            ))
        )
        assertFalse(PlanValidator.validate(plan).valid)
    }

    @Test fun codecRefusesToSerializeAPlanContainingSecrets() {
        val plan = OrionPlan(
            id = "codec-secret",
            goal = "Use key ghp_abcdefghijklmnopqrstuvwxyz123456",
            steps = listOf(PlannedStep("read", PlanStepKind.READ, "Read", "", PlanRisk.LOW))
        )
        assertThrows(IllegalArgumentException::class.java) {
            PlanExecutionJsonCodec.encodePlan(plan)
        }
    }

    @Test fun executionResultsRedactSecretsBeforePersistence() {
        val encoded = PlanExecutionJsonCodec.encodeResults(
            mapOf("read" to StepExecution(
                StepStatus.SUCCEEDED,
                "Request failed: Bearer abcdefghijklmnopqrstuvwxyz123456",
                "ghp_abcdefghijklmnopqrstuvwxyz123456"
            ))
        )
        assertFalse(encoded.contains("abcdefghijklmnopqrstuvwxyz123456"))
        assertTrue(encoded.contains("[REDACTED]"))
    }

    @Test fun detectsPrivateKeyHeaders() {
        assertTrue(PlanSecretGuard.containsSecret("-----BEGIN PRIVATE KEY-----"))
        assertFalse(PlanSecretGuard.containsSecret("ordinary plan output"))
    }
}
