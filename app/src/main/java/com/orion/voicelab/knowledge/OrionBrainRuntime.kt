package com.orion.voicelab.knowledge

import android.content.Context
import kotlinx.coroutines.withContext

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

    suspend fun learnFromExplicitSource(request: ExplicitKnowledgeLearningRequest): ExplicitKnowledgeLearningResult {
        val page = withContext(kotlinx.coroutines.Dispatchers.IO) {
            SafeWebPageReader.read(request.url)
        } ?: return ExplicitKnowledgeLearningResult(
            decision = LearningDecision.REJECT_AS_EVIDENCE,
            title = "",
            authority = EvidenceAuthority.UNKNOWN,
            message = "No se pudo leer la página de forma segura. No se ha incorporado."
        )
        val query = BrainQueryRouter.route(request.topic.ifBlank { page.title })
        val host = java.net.URI(page.canonicalUrl).host?.lowercase().orEmpty()
        val seed = KnowledgeSourceCatalog.seeds.firstOrNull { candidate ->
            val seedHost = runCatching { java.net.URI(candidate.url).host?.lowercase().orEmpty() }.getOrDefault("")
            seedHost.isNotBlank() && (host == seedHost || host.endsWith(".$seedHost")) &&
                query.domain in candidate.domains
        }
        val authority = seed?.authority ?: EvidenceAuthority.UNKNOWN
        val origin = when (authority) {
            EvidenceAuthority.OFFICIAL_PRIMARY -> LearningOrigin.OFFICIAL_SOURCE
            EvidenceAuthority.PRIMARY_TECHNICAL -> LearningOrigin.TECHNICAL_SOURCE
            EvidenceAuthority.PEER_REVIEWED -> LearningOrigin.PEER_REVIEWED_SOURCE
            EvidenceAuthority.REPUTABLE_SECONDARY -> LearningOrigin.REPUTABLE_SECONDARY
            EvidenceAuthority.COMMUNITY -> LearningOrigin.COMMUNITY_REPORT
            EvidenceAuthority.USER_SUPPLIED, EvidenceAuthority.UNKNOWN -> LearningOrigin.REPUTABLE_SECONDARY
        }
        val evidence = KnowledgeEvidence(
            id = page.canonicalUrl,
            title = page.title,
            canonicalUrl = page.canonicalUrl,
            publisherId = seed?.id ?: host,
            domain = query.domain,
            authority = authority,
            retrievedAtMs = System.currentTimeMillis(),
            publishedAtMs = page.publishedAtMs,
            jurisdiction = seed?.jurisdiction,
            content = page.text
        )
        val decision = ingestion.ingest(
            LearningCandidate(KnowledgeDocument(evidence = evidence), origin)
        )
        return ExplicitKnowledgeLearningResult(
            decision = decision,
            title = page.title,
            authority = authority,
            message = when (decision) {
                LearningDecision.ACCEPT_FOR_REVIEW -> "Fuente incorporada a cuarentena. Requiere revisión y aprobación antes de entrar en el conocimiento activo."
                LearningDecision.STORE_AS_UNVERIFIED -> "Fuente guardada como candidata no verificada. No se utilizará como conocimiento confirmado."
                LearningDecision.REJECT_AS_EVIDENCE -> "La fuente no ha superado las comprobaciones y no se ha incorporado."
            }
        )
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

data class ExplicitKnowledgeLearningResult(
    val decision: LearningDecision,
    val title: String,
    val authority: EvidenceAuthority,
    val message: String
)
