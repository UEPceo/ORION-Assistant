package com.orion.voicelab.knowledge

import java.net.URI
import java.security.MessageDigest

/** Normalizes evidence; normal candidates enter quarantine, never the active corpus. */
class KnowledgeIngestionService(
    private val reviewQueue: KnowledgeReviewQueue,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxContentChars: Int = 50_000
) {
    suspend fun ingest(candidate: LearningCandidate): LearningDecision {
        val (decision, document) = normalize(candidate)
        if (document != null && decision != LearningDecision.REJECT_AS_EVIDENCE) {
            reviewQueue.enqueue(document, candidate.origin, decision)
        }
        return decision
    }

    /** Only the APK-bundled, manifest-validated installer may publish pre-reviewed packs directly. */
    internal suspend fun ingestReviewedBundle(
        candidate: LearningCandidate,
        repository: KnowledgeRepository
    ): LearningDecision {
        val (decision, document) = normalize(candidate)
        if (decision != LearningDecision.ACCEPT_FOR_REVIEW || document == null) return LearningDecision.REJECT_AS_EVIDENCE
        repository.upsert(document)
        return decision
    }

    private fun normalize(candidate: LearningCandidate): Pair<LearningDecision, KnowledgeDocument?> {
        val decision = BrainLearningPolicy.decide(candidate)
        if (decision == LearningDecision.REJECT_AS_EVIDENCE) return decision to null
        val original = candidate.document.evidence
        if (original.content.length !in 1..maxContentChars ||
            original.title.isBlank() ||
            original.publisherId.isBlank() ||
            original.retrievedAtMs <= 0L ||
            original.retrievedAtMs > now() + CLOCK_SKEW_MS
        ) return LearningDecision.REJECT_AS_EVIDENCE to null
        val canonicalUrl = canonicalHttpsUrl(original.canonicalUrl)
            ?: return LearningDecision.REJECT_AS_EVIDENCE to null
        val normalizedEvidence = original.copy(
            id = sha256(canonicalUrl).take(32),
            canonicalUrl = canonicalUrl,
            contentHash = sha256(original.content)
        )
        return decision to candidate.document.copy(
            evidence = normalizedEvidence,
            excerpt = candidate.document.excerpt.take(maxContentChars)
        )
    }

    private fun canonicalHttpsUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim()).normalize()
        val host = uri.host?.lowercase() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null || host == "localhost" || host.endsWith(".local") ||
            host == "127.0.0.1" || host == "::1"
        ) return null
        URI("https", null, host, uri.port, uri.path.ifBlank { "/" }, uri.query, null).toASCIIString()
    }.getOrNull()

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private companion object { const val CLOCK_SKEW_MS = 5L * 60L * 1000L }
}
