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
}

enum class LearningOrigin {
    OFFICIAL_SOURCE,
    TECHNICAL_SOURCE,
    PEER_REVIEWED_SOURCE,
    REPUTABLE_SECONDARY,
    COMMUNITY_REPORT,
    USER_CORRECTION,
    MODEL_OUTPUT
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
        if (candidate.origin == LearningOrigin.USER_CORRECTION && !candidate.userApproved) {
            return LearningDecision.STORE_AS_UNVERIFIED
        }
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
