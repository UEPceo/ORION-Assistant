package com.orion.voicelab.knowledge

/**
 * Conservative heuristic for instruction-like text embedded in retrieved documents.
 * Suspect passages may still be inspected for contradictions, but cannot independently
 * justify a factual claim. This is a risk filter, not a complete prompt-injection detector.
 */
object EvidenceInstructionRiskDetector {
    private val patterns = listOf(
        Regex("(?i)ignore\\s+(all\\s+)?(previous|prior|above)\\s+instructions"),
        Regex("(?i)reveal\\s+(the\\s+)?(system|developer)\\s+prompt"),
        Regex("(?i)override\\s+(the\\s+)?(system|developer|previous)\\s+instructions"),
        Regex("(?i)act\\s+as\\s+(an?\\s+)?(assistant|system)"),
        Regex("(?i)send\\s+.*(password|secret|credential)")
    )

    fun isPotentiallyMalicious(content: String): Boolean =
        patterns.any { it.containsMatchIn(content.take(20_000)) }
}
