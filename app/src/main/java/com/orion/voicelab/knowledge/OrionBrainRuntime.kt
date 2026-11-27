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
        sources = listOf(localCorpus) + externalSources.filterNot { it.sourceId == localCorpus.sourceId }
    )
    private val ingestion = KnowledgeIngestionService(localCorpus)

    suspend fun retrieve(queryText: String, jurisdiction: String? = null): BrainContext {
        val query = BrainQueryRouter.route(queryText, jurisdiction)
        return retrieval.retrieve(query)
    }

    suspend fun remember(candidate: LearningCandidate): LearningDecision {
        return ingestion.ingest(candidate)
    }
}
