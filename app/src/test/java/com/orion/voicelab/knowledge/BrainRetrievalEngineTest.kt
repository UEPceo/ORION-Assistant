package com.orion.voicelab.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainRetrievalEngineTest {
    @Test
    fun primarySourceCanSupportRegulatoryAnswer() = runBlocking {
        val evidence = KnowledgeEvidence(
            id = "boe-1",
            title = "Normativa oficial",
            canonicalUrl = "https://www.boe.es/example",
            publisherId = "boe.es",
            domain = KnowledgeDomain.LAW_AND_REGULATION,
            authority = EvidenceAuthority.OFFICIAL_PRIMARY,
            retrievedAtMs = 1_000_000L,
            content = "Texto normativo aplicable"
        )
        val engine = BrainRetrievalEngine(
            sources = listOf(FakeSource(KnowledgeDocument(evidence, relevance = 0.9))),
            now = { 1_000_000L }
        )
        val context = engine.retrieve(
            BrainQueryRouter.route("¿Qué dice la normativa vigente sobre tallas mínimas?")
        )
        assertEquals(BrainPresentation.EVIDENCE_SUPPORTED, context.presentation)
        assertEquals(KnowledgeVerdict.SUPPORTED, context.verification.verdict)
        assertTrue(BrainPromptComposer.compose(context).contains("boe.es"))
    }

    @Test
    fun noSourceNeverProducesVerifiedContext() = runBlocking {
        val context = BrainRetrievalEngine(emptyList(), now = { 10_000L })
            .retrieve(BrainQueryRouter.route("¿Qué tiempo hace en el mar?"))
        assertEquals(BrainPresentation.NO_SOURCES, context.presentation)
        assertEquals(KnowledgeVerdict.NO_EVIDENCE, context.verification.verdict)
    }

    private class FakeSource(
        private val document: KnowledgeDocument
    ) : KnowledgeSource {
        override val sourceId: String = "test-source"
        override suspend fun search(query: BrainQuery, limit: Int) = listOf(document).take(limit)
    }
}
