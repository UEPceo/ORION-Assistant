package com.orion.voicelab.knowledge

import kotlinx.coroutines.CancellationException

/**
 * Retrieval pipeline: domain filter -> jurisdiction preference -> relevance ordering ->
 * evidence verification -> bounded context. It never upgrades model output into evidence.
 */
class BrainRetrievalEngine(
    private val sources: List<KnowledgeSource>,
    private val now: () -> Long = System::currentTimeMillis,
    private val maxDocuments: Int = 5,
    private val maxExcerptChars: Int = 1_800
) {
    suspend fun retrieve(query: BrainQuery): BrainContext {
        val retrieved = mutableListOf<KnowledgeDocument>()
        val sourceFailures = mutableListOf<KnowledgeSourceFailure>()
        for (source in sources) {
            try {
                retrieved.addAll(source.search(query, maxDocuments))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // Keep failures visible without exposing provider messages, URLs or credentials.
                sourceFailures += KnowledgeSourceFailure(
                    sourceId = source.sourceId.take(100),
                    errorType = error::class.java.simpleName.take(100)
                )
            }
        }
        val candidates = retrieved.filter { document ->
            document.evidence.domain == query.domain &&
                document.evidence.canonicalUrl.startsWith("https://") &&
                document.evidence.content.isNotBlank()
        }.distinctBy { it.evidence.id }
            .sortedWith(
                compareByDescending<KnowledgeDocument> { jurisdictionMatch(it, query) }
                    .thenByDescending {
                        if (query.requiresPrimaryEvidence &&
                            it.evidence.authority in setOf(
                                EvidenceAuthority.OFFICIAL_PRIMARY,
                                EvidenceAuthority.PRIMARY_TECHNICAL
                            )
                        ) 1 else 0
                    }
                    .thenByDescending { it.relevance.coerceIn(0.0, 1.0) }
                    .thenByDescending { authorityWeight(it.evidence.authority) }
                    .thenByDescending { it.evidence.retrievedAtMs }
            )
            .take(maxDocuments)
            .map { it.copy(excerpt = it.excerpt.take(maxExcerptChars)) }

        val maxAge = freshnessWindowMs(query.domain)
        val candidateIds = candidates.map { it.evidence.id }.toSet()
        val conflictDetected = candidates.any { document ->
            document.conflictsWithIds.any { it in candidateIds }
        }
        val verification = KnowledgeVerifier.assess(
            evidence = candidates.map { it.evidence },
            domain = query.domain,
            nowMs = now(),
            maxAgeMs = if (query.requiresFreshEvidence) minOf(maxAge, 24 * 60 * 60 * 1000L) else maxAge,
            conflictDetected = conflictDetected,
            requiresPublishedDate = query.requiresFreshEvidence
        )
        val safeToPresentAsVerified = verification.verdict == KnowledgeVerdict.SUPPORTED &&
            (!query.requiresPrimaryEvidence || verification.hasPrimaryEvidence)

        return BrainContext(
            query = query,
            documents = candidates,
            verification = verification,
            sourceFailures = sourceFailures,
            presentation = when {
                candidates.isEmpty() && sourceFailures.isNotEmpty() -> BrainPresentation.SOURCE_FAILURE
                candidates.isEmpty() -> BrainPresentation.NO_SOURCES
                safeToPresentAsVerified -> BrainPresentation.EVIDENCE_SUPPORTED
                verification.verdict == KnowledgeVerdict.STALE || verification.verdict == KnowledgeVerdict.AGE_UNCONFIRMED -> BrainPresentation.HISTORICAL
                else -> BrainPresentation.UNVERIFIED
            }
        )
    }

    private fun jurisdictionMatch(document: KnowledgeDocument, query: BrainQuery): Int {
        val requested = query.jurisdiction?.trim()?.lowercase().orEmpty()
        if (requested.isBlank()) return 0
        return if (document.evidence.jurisdiction?.trim()?.lowercase() == requested) 1 else 0
    }

    private fun authorityWeight(authority: EvidenceAuthority): Int = when (authority) {
        EvidenceAuthority.OFFICIAL_PRIMARY -> 7
        EvidenceAuthority.PRIMARY_TECHNICAL -> 6
        EvidenceAuthority.PEER_REVIEWED -> 5
        EvidenceAuthority.REPUTABLE_SECONDARY -> 4
        EvidenceAuthority.COMMUNITY -> 2
        EvidenceAuthority.USER_SUPPLIED -> 1
        EvidenceAuthority.UNKNOWN -> 0
    }

    private fun freshnessWindowMs(domain: KnowledgeDomain): Long = when (domain) {
        KnowledgeDomain.LAW_AND_REGULATION -> 30L * DAY
        KnowledgeDomain.MARINE_WEATHER -> DAY
        KnowledgeDomain.HEALTH_AND_SAFETY -> 90L * DAY
        KnowledgeDomain.PROGRAMMING,
        KnowledgeDomain.COMPUTER_SCIENCE,
        KnowledgeDomain.CYBERSECURITY,
        KnowledgeDomain.INTERNET -> 365L * DAY
        else -> 730L * DAY
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
    }
}

enum class BrainPresentation {
    EVIDENCE_SUPPORTED,
    UNVERIFIED,
    HISTORICAL,
    NO_SOURCES,
    SOURCE_FAILURE
}

data class KnowledgeSourceFailure(
    val sourceId: String,
    val errorType: String
)

data class BrainContext(
    val query: BrainQuery,
    val documents: List<KnowledgeDocument>,
    val verification: KnowledgeVerification,
    val presentation: BrainPresentation,
    val sourceFailures: List<KnowledgeSourceFailure> = emptyList()
)
