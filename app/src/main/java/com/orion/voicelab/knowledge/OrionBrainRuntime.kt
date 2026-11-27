package com.orion.voicelab.knowledge

import android.content.Context

/**
 * Composition root for the modular brain. New source adapters can be registered here
 * without coupling the conversation engine to individual providers.
 */
class OrionBrainRuntime(context: Context) {
    val localCorpus = KnowledgeCorpusStore(context)
    private val retrieval = BrainRetrievalEngine(
        sources = listOf(localCorpus)
    )

    suspend fun retrieve(queryText: String, jurisdiction: String? = null): BrainContext {
        val query = BrainQueryRouter.route(queryText, jurisdiction)
        return retrieval.retrieve(query)
    }

    suspend fun remember(candidate: LearningCandidate): LearningDecision {
        val decision = BrainLearningPolicy.decide(candidate)
        if (decision == LearningDecision.ACCEPT_FOR_REVIEW ||
            decision == LearningDecision.STORE_AS_UNVERIFIED
        ) {
            localCorpus.upsert(candidate.document)
        }
        return decision
    }
}
