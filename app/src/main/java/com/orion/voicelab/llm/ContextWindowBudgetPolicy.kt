package com.orion.voicelab.llm

/**
 * Converts the effective native context into a conservative character budget for retrieved
 * evidence. It reserves most of the window for system instructions, conversation history,
 * user input and generated output instead of filling the entire window with RAG passages.
 */
object ContextWindowBudgetPolicy {
    private const val CHARS_PER_TOKEN_ESTIMATE = 3
    private const val EVIDENCE_CONTEXT_SHARE = 0.28
    private const val MIN_EVIDENCE_CHARS = 1_500
    private const val MAX_EVIDENCE_CHARS = 12_000
    private const val PROMPT_OVERHEAD_CHARS = 240
    private const val MIN_COMPOSER_CHARS = 256

    fun promptChars(contextTokens: Int, reserveOutputTokens: Int = 180, safetyTokens: Int = 64): Int {
        require(contextTokens in 512..32_768) { "Context window outside supported bounds." }
        require(reserveOutputTokens >= 1 && safetyTokens >= 0)
        val availableTokens = (contextTokens - reserveOutputTokens - safetyTokens).coerceAtLeast(128)
        return availableTokens * CHARS_PER_TOKEN_ESTIMATE
    }

    fun evidenceChars(contextTokens: Int): Int {
        require(contextTokens in 512..32_768) { "Context window outside supported bounds." }
        return (contextTokens * CHARS_PER_TOKEN_ESTIMATE * EVIDENCE_CONTEXT_SHARE)
            .toInt()
            .coerceIn(MIN_EVIDENCE_CHARS, MAX_EVIDENCE_CHARS)
    }

    fun evidenceChars(contextTokens: Int, fixedPromptChars: Int, historyChars: Int): Int {
        val remaining = promptChars(contextTokens) - fixedPromptChars.coerceAtLeast(0) -
            historyChars.coerceAtLeast(0) - PROMPT_OVERHEAD_CHARS
        return remaining.coerceIn(MIN_COMPOSER_CHARS, MAX_EVIDENCE_CHARS)
    }
}
