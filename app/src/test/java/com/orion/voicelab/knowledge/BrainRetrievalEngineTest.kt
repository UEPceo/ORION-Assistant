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
    fun explicitlyLinkedConflictingSourcesAreNotPresentedAsVerified() = runBlocking {
        val first = KnowledgeEvidence(
            id = "source-a",
            title = "Fuente A",
            canonicalUrl = "https://example.org/a",
            publisherId = "publisher-a",
            domain = KnowledgeDomain.GENERAL,
            authority = EvidenceAuthority.REPUTABLE_SECONDARY,
            retrievedAtMs = 1_000_000L,
            content = "Dato A"
        )
        val second = KnowledgeEvidence(
            id = "source-b",
            title = "Fuente B",
            canonicalUrl = "https://example.net/b",
            publisherId = "publisher-b",
            domain = KnowledgeDomain.GENERAL,
            authority = EvidenceAuthority.REPUTABLE_SECONDARY,
            retrievedAtMs = 1_000_000L,
            content = "Dato B"
        )
        val source = object : KnowledgeSource {
            override val sourceId = "conflict-test"
            override suspend fun search(query: BrainQuery, limit: Int) = listOf(
                KnowledgeDocument(first, conflictsWithIds = setOf("source-b")),
                KnowledgeDocument(second)
            )
        }
        val context = BrainRetrievalEngine(listOf(source), now = { 1_000_000L })
            .retrieve(BrainQueryRouter.route("información general"))
        assertEquals(BrainPresentation.UNVERIFIED, context.presentation)
        assertEquals(KnowledgeVerdict.CONFLICTING, context.verification.verdict)
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
