package com.orion.voicelab.engineering

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
        val lines = rawLogs.lineSequence()
            .map { it.take(500) }
            .filter { line ->
                line.containsAny(
                    "error", "failed", "failure", "exception", "unresolved",
                    "could not", "cannot", "timed out", "timeout", "not found",
                    "compilation", "test failed", "assertion"
                )
            }
            .toList()
        val selected = if (lines.isNotEmpty()) lines.takeLast(18) else rawLogs.lineSequence().toList().takeLast(8)
        return selected.joinToString("\n").takeLast(MAX_DIAGNOSTIC_CHARS)
    }

    private fun String.containsAny(vararg values: String): Boolean =
        values.any { contains(it, ignoreCase = true) }
}
