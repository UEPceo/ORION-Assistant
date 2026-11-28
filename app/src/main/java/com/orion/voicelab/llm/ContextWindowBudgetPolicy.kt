package com.orion.voicelab.llm

/**
 * Converts the effective native context into a conservative character budget for retrieved
 * evidence. It reserves most of the window for system instructions, conversation history,
 * user input and generated output instead of filling the entire window with RAG passages.
 */
object ContextWindowBudgetPolicy {
    private const val CHARS_PER_TOKEN_ESTIMATE = 4
    private const val EVIDENCE_CONTEXT_SHARE = 0.28
    private const val MIN_EVIDENCE_CHARS = 1_500
    private const val MAX_EVIDENCE_CHARS = 12_000

    fun evidenceChars(contextTokens: Int): Int {
        require(contextTokens in 512..32_768) { "Context window outside supported bounds." }
        return (contextTokens * CHARS_PER_TOKEN_ESTIMATE * EVIDENCE_CONTEXT_SHARE)
            .toInt()
            .coerceIn(MIN_EVIDENCE_CHARS, MAX_EVIDENCE_CHARS)
    }
}
