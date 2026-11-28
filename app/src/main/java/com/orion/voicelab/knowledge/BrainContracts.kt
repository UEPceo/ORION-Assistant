package com.orion.voicelab.knowledge

/**
 * Stable contracts for ORION's modular knowledge brain.
 * Provider adapters must return source metadata; generated model text is never a source.
 */
data class BrainQuery(
    val rawText: String,
    val normalizedText: String,
    val domain: KnowledgeDomain,
    val requiresFreshEvidence: Boolean,
    val requiresPrimaryEvidence: Boolean,
    val jurisdiction: String? = null
)

data class KnowledgeDocument(
    val evidence: KnowledgeEvidence,
    val excerpt: String = evidence.content,
    val relevance: Double = 0.0,
    val conflictsWithIds: Set<String> = emptySet()
)

interface KnowledgeSource {
    val sourceId: String
    suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument>
}

interface KnowledgeRepository {
    suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument>
    suspend fun upsert(document: KnowledgeDocument)
    suspend fun remove(documentId: String)
    suspend fun contains(documentId: String): Boolean
    suspend fun get(documentId: String): KnowledgeDocument? = null
    suspend fun allDocuments(): List<KnowledgeDocument> = emptyList()
    suspend fun searchWithCandidate(
        query: BrainQuery,
        limit: Int,
        candidate: KnowledgeDocument
    ): List<KnowledgeDocument> =
        (search(query, limit) + candidate.takeIf { it.evidence.domain == query.domain })
            .filterNotNull()
            .distinctBy { it.evidence.id }
            .sortedByDescending { it.relevance }
            .take(limit.coerceAtLeast(0))
}

enum class LearningOrigin {
    OFFICIAL_SOURCE,
    TECHNICAL_SOURCE,
    PEER_REVIEWED_SOURCE,
    REPUTABLE_SECONDARY,
    COMMUNITY_REPORT,
    UNCLASSIFIED_SOURCE,
    USER_CORRECTION,
    MODEL_OUTPUT
}

/** Keeps source provenance honest: an unknown host is never labelled reputable. */
internal fun learningOriginForAuthority(authority: EvidenceAuthority): LearningOrigin = when (authority) {
    EvidenceAuthority.OFFICIAL_PRIMARY -> LearningOrigin.OFFICIAL_SOURCE
    EvidenceAuthority.PRIMARY_TECHNICAL -> LearningOrigin.TECHNICAL_SOURCE
    EvidenceAuthority.PEER_REVIEWED -> LearningOrigin.PEER_REVIEWED_SOURCE
    EvidenceAuthority.REPUTABLE_SECONDARY -> LearningOrigin.REPUTABLE_SECONDARY
    EvidenceAuthority.COMMUNITY -> LearningOrigin.COMMUNITY_REPORT
    EvidenceAuthority.USER_SUPPLIED, EvidenceAuthority.UNKNOWN -> LearningOrigin.UNCLASSIFIED_SOURCE
}

enum class LearningDecision {
    ACCEPT_FOR_REVIEW,
    STORE_AS_UNVERIFIED,
    REJECT_AS_EVIDENCE
}

data class LearningCandidate(
    val document: KnowledgeDocument,
    val origin: LearningOrigin,
    val userApproved: Boolean = false
)

/** Learning is corpus curation, not autonomous model-weight modification. */
object BrainLearningPolicy {
    fun decide(candidate: LearningCandidate): LearningDecision {
        val evidence = candidate.document.evidence
        if (evidence.canonicalUrl.isBlank() || evidence.content.isBlank()) {
            return LearningDecision.REJECT_AS_EVIDENCE
        }
        if (candidate.origin == LearningOrigin.MODEL_OUTPUT) {
            return LearningDecision.REJECT_AS_EVIDENCE
        }
        // A correction is useful feedback, but it is not an independent source.
        // It remains unverified even when the user explicitly approves storing it.
        if (candidate.origin == LearningOrigin.USER_CORRECTION) {
            return LearningDecision.STORE_AS_UNVERIFIED
        }
        // Provenance and authority must agree. Metadata supplied by a caller cannot
        // promote an unknown/community source into an official or technical source.
        val provenanceMatchesAuthority = when (candidate.origin) {
            LearningOrigin.OFFICIAL_SOURCE -> evidence.authority == EvidenceAuthority.OFFICIAL_PRIMARY
            LearningOrigin.TECHNICAL_SOURCE -> evidence.authority == EvidenceAuthority.PRIMARY_TECHNICAL
            LearningOrigin.PEER_REVIEWED_SOURCE -> evidence.authority == EvidenceAuthority.PEER_REVIEWED
            LearningOrigin.REPUTABLE_SECONDARY -> evidence.authority == EvidenceAuthority.REPUTABLE_SECONDARY
            LearningOrigin.COMMUNITY_REPORT -> evidence.authority == EvidenceAuthority.COMMUNITY
            LearningOrigin.UNCLASSIFIED_SOURCE -> false
            LearningOrigin.USER_CORRECTION, LearningOrigin.MODEL_OUTPUT -> false
        }
        if (!provenanceMatchesAuthority) return LearningDecision.STORE_AS_UNVERIFIED
        return when (evidence.authority) {
            EvidenceAuthority.OFFICIAL_PRIMARY,
            EvidenceAuthority.PRIMARY_TECHNICAL,
            EvidenceAuthority.PEER_REVIEWED -> LearningDecision.ACCEPT_FOR_REVIEW
            EvidenceAuthority.REPUTABLE_SECONDARY,
            EvidenceAuthority.COMMUNITY,
            EvidenceAuthority.USER_SUPPLIED,
            EvidenceAuthority.UNKNOWN -> LearningDecision.STORE_AS_UNVERIFIED
        }
    }
}
