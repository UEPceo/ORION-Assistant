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
                !draft.contains(claim.sourceSpan.orEmpty())
        }.map { it.id }.toSet()
        if (invalid.isNotEmpty()) return AnswerClaimCoverageCheck(false, emptyList(), invalid)

        val bibliographyStart = Regex("(?im)^\\s*(?:fuentes|sources)\\s*:")
            .find(draft)?.range?.first ?: draft.length
        val prose = draft.substring(0, bibliographyStart).replace(
            Regex("(?s)" + CODE_FENCE + ".*?" + CODE_FENCE)
        ) { match -> " ".repeat(match.value.length) }

        // Map each exact span to a distinct occurrence so a repeated sentence cannot be
        // considered covered twice by one single claim.
        val usedRanges = mutableListOf<IntRange>()
        val spanRanges = factualClaims.mapNotNull { claim ->
            val span = claim.sourceSpan.orEmpty()
            val range = draft.indices.firstOrNull { index ->
                index + span.length <= bibliographyStart &&
                    draft.regionMatches(index, span, 0, span.length) &&
                    usedRanges.none { index <= it.last && index + span.length - 1 >= it.first }
            }?.let { it until (it + span.length) }
            if (range != null) usedRanges += range
            range
        }

        val uncovered = mutableListOf<String>()
        var segmentStart = 0
        val boundaries = sentenceBoundary.findAll(prose).toList()
        val segmentRanges = boundaries.map { boundary ->
            val range = segmentStart until boundary.range.first
            segmentStart = boundary.range.last + 1
            range
        } + listOf(segmentStart until prose.length)

        for (range in segmentRanges) {
            if (range.isEmpty()) continue
            val segment = prose.substring(range.first, range.last + 1)
                .replace(Regex("https?://\\S+"), " ")
                .replace(Regex("\\[\\d{1,2}\\]"), " ")
                .trim()
                .replace(Regex("^[#>*\\-\\d.)\\s]+"), "")
            val requiredTokens = tokens(segment)
            if (requiredTokens.size < MIN_CONTENT_TOKENS) continue
            val coveredTokens = spanRanges
                .filter { it.first >= range.first && it.last <= range.last }
                .flatMap { spanRange -> tokens(draft.substring(spanRange.first, spanRange.last + 1)) }
                .toSet()
            if (!coveredTokens.containsAll(requiredTokens)) uncovered += segment.take(MAX_SEGMENT_CHARS)
        }
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

    private const val CODE_FENCE = "\\u0060\\u0060\\u0060"
    private const val MAX_SPAN_CHARS = 700
    private const val MAX_SEGMENT_CHARS = 240
    private const val MIN_CONTENT_TOKENS = 3
}
