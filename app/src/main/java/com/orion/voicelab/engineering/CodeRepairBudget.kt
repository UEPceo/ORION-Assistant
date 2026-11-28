package com.orion.voicelab.engineering

import com.orion.voicelab.brain.PlanSecretGuard

/**
 * Bounds repair iterations and reduces CI output to diagnostic signals. Logs remain untrusted
 * data and never authorize execution, commits or merges.
 */
object CodeRepairBudget {
    const val MAX_ATTEMPTS = 2
    const val MAX_DIAGNOSTIC_CHARS = 1_800

    fun canPrepareRepair(attemptsAlreadyUsed: Int, inspection: CodeEngineeringCiInspection): Boolean =
        attemptsAlreadyUsed in 0 until MAX_ATTEMPTS &&
            inspection.failed &&
            !inspection.failureLogs.isNullOrBlank()

    fun summarizeLogs(rawLogs: String): String {
        val selected = java.util.ArrayDeque<String>()
        val sanitizedLogs = PlanSecretGuard.redact(rawLogs, 1_500_000)
        sanitizedLogs.lineSequence()
            .map { it.take(500) }
            .filter { line ->
                line.containsAny(
                    "error", "failed", "failure", "exception", "unresolved",
                    "could not", "cannot", "timed out", "timeout", "not found",
                    "compilation", "test failed", "assertion"
                )
            }
            .forEach { line ->
                selected.addLast(line)
                if (selected.size > 18) selected.removeFirst()
            }
        if (selected.isEmpty()) return ""
        return selected.joinToString("\n").takeLast(MAX_DIAGNOSTIC_CHARS)
    }

    private fun String.containsAny(vararg values: String): Boolean =
        values.any { contains(it, ignoreCase = true) }
}
