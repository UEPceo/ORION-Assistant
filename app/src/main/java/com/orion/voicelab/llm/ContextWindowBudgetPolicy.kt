package com.orion.voicelab.llm

/**
 * Conservative character budgets derived from the native context actually loaded by llama.cpp.
 * Character-to-token conversion is an estimate, not tokenizer accounting; native generation
 * remains the final authority and can still reject an oversized prompt.
 */
object ContextWindowBudgetPolicy {
    private const val CHARS_PER_TOKEN_ESTIMATE = 3
    private const val EVIDENCE_CONTEXT_SHARE = 0.28
    private const val MAX_EVIDENCE_CHARS = 12_000
    private const val PROMPT_OVERHEAD_CHARS = 240

    fun promptChars(contextTokens: Int, reserveOutputTokens: Int = 180, safetyTokens: Int = 64): Int {
        require(contextTokens in 512..32_768) { "Context window outside supported bounds." }
        require(reserveOutputTokens >= 1 && safetyTokens >= 0)
        require(reserveOutputTokens.toLong() + safetyTokens < contextTokens) {
            "Output and safety reserves consume the entire context window."
        }
        val availableTokens = contextTokens - reserveOutputTokens - safetyTokens
        return availableTokens * CHARS_PER_TOKEN_ESTIMATE
    }

    fun evidenceChars(contextTokens: Int): Int =
        (promptChars(contextTokens) * EVIDENCE_CONTEXT_SHARE)
            .toInt()
            .coerceIn(0, MAX_EVIDENCE_CHARS)

    fun evidenceChars(contextTokens: Int, fixedPromptChars: Int, historyChars: Int): Int {
        val remaining = promptChars(contextTokens) -
            fixedPromptChars.coerceAtLeast(0) -
            historyChars.coerceAtLeast(0) -
            PROMPT_OVERHEAD_CHARS
        if (remaining <= 0) return 0
        return (remaining * EVIDENCE_CONTEXT_SHARE).toInt().coerceIn(0, MAX_EVIDENCE_CHARS)
    }
}
