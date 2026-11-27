package com.orion.voicelab.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeIngestionServiceTest {
    @Test
    fun canonicalizesUrlAndHashesContentBeforeStorage() = runBlocking {
        val repository = FakeRepository()
        val service = KnowledgeIngestionService(repository, now = { 10_000L })
        val candidate = candidate(
            url = "https://EXAMPLE.org/docs/page#section",
            retrievedAt = 9_000L
        )
        val decision = service.ingest(candidate)
        assertEquals(LearningDecision.ACCEPT_FOR_REVIEW, decision)
        val saved = repository.saved ?: error("No se guardó el documento")
        assertEquals("https://example.org/docs/page", saved.evidence.canonicalUrl)
        assertNotNull(saved.evidence.contentHash)
        assertTrue(saved.evidence.id.isNotBlank())
    }

    @Test
    fun insecureOrFutureSourcesAreRejected() = runBlocking {
        val repository = FakeRepository()
        val service = KnowledgeIngestionService(repository, now = { 10_000L })
        assertEquals(
            LearningDecision.REJECT_AS_EVIDENCE,
            service.ingest(candidate(url = "http://example.org", retrievedAt = 9_000L))
        )
        assertEquals(
            LearningDecision.REJECT_AS_EVIDENCE,
            service.ingest(candidate(url = "https://example.org", retrievedAt = 50_000L))
        )
        assertEquals(null, repository.saved)
    }

    private fun candidate(url: String, retrievedAt: Long) = LearningCandidate(
        document = KnowledgeDocument(
            evidence = KnowledgeEvidence(
                id = "untrusted-id",
                title = "Official document",
                canonicalUrl = url,
                publisherId = "official-publisher",
                domain = KnowledgeDomain.PROGRAMMING,
                authority = EvidenceAuthority.OFFICIAL_PRIMARY,
                retrievedAtMs = retrievedAt,
                content = "Official technical documentation"
            )
        ),
        origin = LearningOrigin.OFFICIAL_SOURCE
    )

    private class FakeRepository : KnowledgeRepository {
        var saved: KnowledgeDocument? = null
        override suspend fun search(query: BrainQuery, limit: Int) = emptyList<KnowledgeDocument>()
        override suspend fun upsert(document: KnowledgeDocument) { saved = document }
        override suspend fun remove(documentId: String) { saved = null }
        override suspend fun contains(documentId: String) = saved?.evidence?.id == documentId
    }
}
