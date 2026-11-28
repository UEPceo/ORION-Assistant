package com.orion.voicelab.knowledge

import java.text.Normalizer

data class AnswerClaimCoverageCheck(
    val passed: Boolean,
    val uncoveredSegments: List<String>,
    val invalidClaimIds: Set<String>
)

/**
 * Deterministic completeness guard for the LLM claim decomposition.
 * Every factual prose segment must be represented by exact source spans from the draft.
 * This does not prove entailment; it prevents the critic from silently omitting draft text.
 */
object AnswerClaimCoverageGuard {
    private val tokenPattern = Regex("[\\p{L}\\p{N}]+")
    private val sentenceBoundary = Regex("(?<=[.!?])\\s+|\\n+")
    private val stopWords = setOf(
        "a", "al", "algo", "algunas", "algunos", "ante", "como", "con", "cual", "cuando",
        "de", "del", "desde", "donde", "durante", "el", "ella", "ellas", "ello", "ellos",
        "en", "entre", "era", "es", "esa", "esas", "ese", "eso", "esos", "esta", "estas",
        "este", "esto", "estos", "fue", "ha", "hacia", "hasta", "hay", "la", "las", "le",
        "les", "lo", "los", "mas", "me", "mi", "mis", "muy", "no", "o", "para", "pero",
        "por", "porque", "que", "se", "segun", "si", "sin", "sobre", "su", "sus", "tambien",
        "te", "tiene", "un", "una", "unas", "uno", "unos", "y", "ya",
        "the", "an", "and", "are", "as", "at", "be", "been", "being", "but", "by",
        "for", "from", "had", "has", "have", "in", "into", "is", "it", "its", "of", "on",
        "or", "that", "this", "to", "was", "were", "will", "with"
    )

    fun check(draft: String, claims: List<AtomicAnswerClaim>): AnswerClaimCoverageCheck {
        val factualClaims = claims.filter { it.isFactual }
        val invalid = factualClaims.filter { claim ->
            claim.sourceSpan.isNullOrBlank() ||
                claim.sourceSpan.orEmpty().length > MAX_SPAN_CHARS ||
                !draft.contains(claim.sourceSpan)
        }.map { it.id }.toSet()
        if (invalid.isNotEmpty()) return AnswerClaimCoverageCheck(false, emptyList(), invalid)

        val coveredTokens = factualClaims.flatMap { tokens(it.sourceSpan.orEmpty()) }.toSet()
        val codeFence = "\\u0060\\u0060\\u0060"
        val bibliographyStart = Regex("(?im)^\\s*(?:fuentes|sources)\\s*:").find(draft)?.range?.first ?: draft.length
        val prose = draft.substring(0, bibliographyStart)
            .replace(Regex("(?s)" + codeFence + ".*?" + codeFence), " ")
            .replace(Regex("https?://\\S+"), " ")
            .replace(Regex("\\[\\d{1,2}\\]"), " ")
        val uncovered = prose.split(sentenceBoundary)
            .map { it.trim().replace(Regex("^[#>*\\-\\d.)\\s]+"), "") }
            .filter { tokens(it).size >= MIN_CONTENT_TOKENS }
            .filter { segment -> !coveredTokens.containsAll(tokens(segment)) }
            .map { it.take(MAX_SEGMENT_CHARS) }
        return AnswerClaimCoverageCheck(uncovered.isEmpty(), uncovered, emptySet())
    }

    private fun tokens(value: String): Set<String> {
        val normalized = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return tokenPattern.findAll(normalized)
            .map { it.value }
            .filter { it.length > 1 && it !in stopWords }
            .toSet()
    }

    private const val MAX_SPAN_CHARS = 700
    private const val MAX_SEGMENT_CHARS = 240
    private const val MIN_CONTENT_TOKENS = 3
}
