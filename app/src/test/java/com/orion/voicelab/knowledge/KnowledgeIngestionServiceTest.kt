package com.orion.voicelab.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class KnowledgeIngestionServiceTest {
    @Test
    fun canonicalizesUrlAndQueuesCandidateInsteadOfPublishingToCorpus() = runBlocking {
        val queue = FakeQueue()
        val service = KnowledgeIngestionService(queue, now = { 10_000L })
        val decision = service.ingest(candidate("https://EXAMPLE.org/docs/page#section", 9_000L))
        assertEquals(LearningDecision.ACCEPT_FOR_REVIEW, decision)
        val saved = queue.items.single().document
        assertEquals("https://example.org/docs/page", saved.evidence.canonicalUrl)
        assertNotNull(saved.evidence.contentHash)
        assertTrue(saved.evidence.id.isNotBlank())
        assertEquals(KnowledgeReviewStatus.PENDING, queue.items.single().status)
    }

    @Test
    fun insecureOrFutureSourcesAreRejectedWithoutQueueing() = runBlocking {
        val queue = FakeQueue()
        val service = KnowledgeIngestionService(queue, now = { 10_000L })
        assertEquals(LearningDecision.REJECT_AS_EVIDENCE, service.ingest(candidate("http://example.org", 9_000L)))
        assertEquals(LearningDecision.REJECT_AS_EVIDENCE, service.ingest(candidate("https://example.org", 50_000L)))
        assertTrue(queue.items.isEmpty())
    }

    @Test
    fun modelOutputNeverEntersReviewQueue() = runBlocking {
        val queue = FakeQueue()
        val service = KnowledgeIngestionService(queue, now = { 10_000L })
        val modelCandidate = candidate("https://example.org/model", 9_000L).copy(origin = LearningOrigin.MODEL_OUTPUT)
        assertEquals(LearningDecision.REJECT_AS_EVIDENCE, service.ingest(modelCandidate))
        assertTrue(queue.items.isEmpty())
    }

    private fun candidate(url:String,retrievedAt:Long)=LearningCandidate(
        document=KnowledgeDocument(
            evidence=KnowledgeEvidence(
                id="untrusted-id",title="Official document",canonicalUrl=url,publisherId="official-publisher",
                domain=KnowledgeDomain.PROGRAMMING,authority=EvidenceAuthority.OFFICIAL_PRIMARY,
                retrievedAtMs=retrievedAt,content="Official technical documentation"
            )
        ),
        origin=LearningOrigin.OFFICIAL_SOURCE
    )

    private class FakeQueue:KnowledgeReviewQueue {
        val items=mutableListOf<KnowledgeReviewItem>()
        override suspend fun enqueue(document:KnowledgeDocument,origin:LearningOrigin,decision:LearningDecision) {
            items.removeAll { it.id==document.evidence.id && it.status==KnowledgeReviewStatus.PENDING }
            items+=KnowledgeReviewItem(document.evidence.id,document,origin,decision,1L,KnowledgeReviewStatus.PENDING)
        }
        override suspend fun pending()=items.filter { it.status==KnowledgeReviewStatus.PENDING }
    }
}
