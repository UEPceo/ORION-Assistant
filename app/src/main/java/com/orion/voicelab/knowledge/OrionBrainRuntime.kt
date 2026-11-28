package com.orion.voicelab.knowledge

import android.content.Context

/**
 * Composition root for the modular brain. New source adapters can be registered here
 * without coupling the conversation engine to individual providers.
 */
class OrionBrainRuntime(
    context: Context,
    externalSources: List<KnowledgeSource> = emptyList()
) {
    val localCorpus = KnowledgeCorpusStore(context)
    private val retrieval = BrainRetrievalEngine(
        sources = listOf(localCorpus, DuckDuckGoKnowledgeSource()) + externalSources.filterNot { it.sourceId in setOf(localCorpus.sourceId, "duckduckgo-web-discovery") }
    )
    val reviewQueue = KnowledgeReviewQueueStore(context, localCorpus)
    private val ingestion = KnowledgeIngestionService(reviewQueue)
    val offlinePackInstaller = BundledKnowledgePackInstaller(context, ingestion, localCorpus)

    suspend fun retrieve(queryText: String, jurisdiction: String? = null): BrainContext {
        offlinePackInstaller.installIfNeeded()
        val query = BrainQueryRouter.route(queryText, jurisdiction)
        return retrieval.retrieve(query)
    }

    suspend fun installOfflineKnowledge(): OfflinePackInstallReport = offlinePackInstaller.installIfNeeded()

    suspend fun remember(candidate: LearningCandidate): LearningDecision {
        return ingestion.ingest(candidate)
    }

    suspend fun pendingKnowledgeReviews(): List<KnowledgeReviewItem> = reviewQueue.pending()

    suspend fun approveKnowledgeReview(id: String, userConfirmed: Boolean): Boolean =
        reviewQueue.approve(id, userConfirmed)

    suspend fun publishKnowledgeAsUnverified(id: String, userConfirmed: Boolean): Boolean =
        reviewQueue.publishUnverified(id, userConfirmed)

    suspend fun rejectKnowledgeReview(id: String, userConfirmed: Boolean): Boolean =
        reviewQueue.reject(id, userConfirmed)

    suspend fun rollbackKnowledgeDocument(id: String, userConfirmed: Boolean): Boolean =
        reviewQueue.rollback(id, userConfirmed)
}
