package com.orion.voicelab.knowledge

import java.net.URI
import java.security.MessageDigest

/**
 * Normalizes evidence and places every accepted candidate in quarantine. This service
 * never writes directly to the active retrieval corpus.
 */
class KnowledgeIngestionService(
    private val reviewQueue: KnowledgeReviewQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxContentChars: Int = 50_000
) {
    suspend fun ingest(candidate: LearningCandidate): LearningDecision {
        val decision = BrainLearningPolicy.decide(candidate)
        if (decision == LearningDecision.REJECT_AS_EVIDENCE) return decision

        val original = candidate.document.evidence
        if (original.content.length !in 1..maxContentChars ||
            original.title.isBlank() ||
            original.publisherId.isBlank() ||
            original.retrievedAtMs <= 0L ||
            original.retrievedAtMs > now() + CLOCK_SKEW_MS
        ) return LearningDecision.REJECT_AS_EVIDENCE

        val canonicalUrl = canonicalHttpsUrl(original.canonicalUrl)
            ?: return LearningDecision.REJECT_AS_EVIDENCE
        val contentHash = sha256(original.content)
        val stableId = sha256(canonicalUrl).take(32)
        val normalizedEvidence = original.copy(
            id = stableId,
            canonicalUrl = canonicalUrl,
            contentHash = contentHash
        )
        val normalizedDocument = candidate.document.copy(
            evidence = normalizedEvidence,
            excerpt = candidate.document.excerpt.take(maxContentChars)
        )
        reviewQueue.enqueue(normalizedDocument, candidate.origin, decision)
        return decision
    }

    private fun canonicalHttpsUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim()).normalize()
        val host = uri.host?.lowercase() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null ||
            host == "localhost" ||
            host.endsWith(".local") ||
            host == "127.0.0.1" ||
            host == "::1"
        ) return null
        URI("https", null, host, uri.port, uri.path.ifBlank { "/" }, uri.query, null)
            .toASCIIString()
    }.getOrNull()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object {
        const val CLOCK_SKEW_MS = 5L * 60L * 1000L
    }
}
