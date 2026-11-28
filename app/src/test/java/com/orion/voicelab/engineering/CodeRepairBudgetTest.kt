package com.orion.voicelab.engineering

import com.orion.voicelab.connectors.github.GitHubWorkflowRunSummary
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeRepairBudgetTest {
    private fun failed(logs: String?) = CodeEngineeringCiInspection(
        run = GitHubWorkflowRunSummary(
            name = "quality", status = "completed", conclusion = "failure",
            branch = "orion/task", htmlUrl = "", updatedAt = "",
            runId = 7L, headSha = "a".repeat(40)
        ),
        jobs = emptyList(),
        failureLogs = logs
    )

    @Test fun repairRequiresFailedExactRunAndAvailableLogs() {
        assertTrue(CodeRepairBudget.canPrepareRepair(0, failed("Compilation error")))
        assertFalse(CodeRepairBudget.canPrepareRepair(2, failed("Compilation error")))
        assertFalse(CodeRepairBudget.canPrepareRepair(0, failed(null)))
        val success = failed("logs").copy(run = failed("logs").run!!.copy(conclusion = "success"))
        assertFalse(CodeRepairBudget.canPrepareRepair(0, success))
    }

    @Test fun unclassifiedLogsAreNotForwardedAsRawFallback() {
        val summary = CodeRepairBudget.summarizeLogs(
            "ordinary output with no recognized diagnostic\nsecond harmless line"
        )
        assertTrue(summary.isEmpty())
    }

    @Test fun diagnosticSummaryRedactsSecretsBeforeModelContext() {
        val summary = CodeRepairBudget.summarizeLogs(
            "Compilation error: client_secret=super-secret-value-123456\n" +
                "e: file.kt:9 unresolved reference: item"
        )
        assertFalse(summary.contains("super-secret-value-123456"))
        assertTrue(summary.contains("[REDACTED]"))
        assertTrue(summary.contains("unresolved reference"))
    }

    @Test fun diagnosticSummaryIsBoundedAndPrioritizesFailureLines() {
        val raw = (1..80).joinToString("\n") { index ->
            if (index % 10 == 0) "e: file.kt:$index unresolved reference: item$index"
            else "ordinary build output line $index"
        }
        val summary = CodeRepairBudget.summarizeLogs(raw)
        assertTrue(summary.length <= CodeRepairBudget.MAX_DIAGNOSTIC_CHARS)
        assertTrue(summary.contains("unresolved reference"))
        assertFalse(summary.contains("ordinary build output line 1\n"))
    }
}
