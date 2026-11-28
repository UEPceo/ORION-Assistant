package com.orion.voicelab.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KnowledgeReviewQueueStoreTest {
    private class FakeCorpus:KnowledgeRepository {
        val documents=linkedMapOf<String,KnowledgeDocument>()
        override suspend fun search(query:BrainQuery,limit:Int)=emptyList<KnowledgeDocument>()
        override suspend fun upsert(document:KnowledgeDocument) { documents[document.evidence.id]=document }
        override suspend fun remove(documentId:String) { documents.remove(documentId) }
        override suspend fun contains(documentId:String)=documents.containsKey(documentId)
        override suspend fun get(documentId:String)=documents[documentId]
    }

    private fun document(content:String,authority:EvidenceAuthority=EvidenceAuthority.OFFICIAL_PRIMARY)=KnowledgeDocument(
        KnowledgeEvidence("doc-1","Test document","https://example.org/doc","example",
            KnowledgeDomain.PROGRAMMING,authority,1_000L,content=content,contentHash=null)
    )

    @Test fun candidateStaysQuarantinedUntilExplicitApprovalAndCanRollback()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val corpus=FakeCorpus()
        val queue=KnowledgeReviewQueueStore(context,corpus,nowMs={2_000L})
        queue.enqueue(document("version one"),LearningOrigin.OFFICIAL_SOURCE,LearningDecision.ACCEPT_FOR_REVIEW)
        assertTrue(corpus.documents.isEmpty())
        assertEquals(1,KnowledgeReviewQueueStore(context,corpus).pending().size)
        assertTrue(queue.approve("doc-1",true))
        assertEquals("version one",corpus.documents["doc-1"]?.evidence?.content)

        queue.enqueue(document("version two"),LearningOrigin.OFFICIAL_SOURCE,LearningDecision.ACCEPT_FOR_REVIEW)
        assertTrue(queue.approve("doc-1",true))
        assertEquals("version two",corpus.documents["doc-1"]?.evidence?.content)
        assertTrue(queue.rollback("doc-1",true))
        assertEquals("version one",corpus.documents["doc-1"]?.evidence?.content)
    }

    @Test fun unverifiedCandidateCannotBeApprovedAsVerified()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val corpus=FakeCorpus()
        val queue=KnowledgeReviewQueueStore(context,corpus)
        queue.enqueue(document("user correction",EvidenceAuthority.USER_SUPPLIED),
            LearningOrigin.USER_CORRECTION,LearningDecision.STORE_AS_UNVERIFIED)
        assertFalse(queue.approve("doc-1",true))
        assertTrue(queue.publishUnverified("doc-1",true))
        assertEquals("user correction",corpus.documents["doc-1"]?.evidence?.content)
    }

    @Test fun rejectionKeepsCandidateOutOfActiveCorpus()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val corpus=FakeCorpus()
        val queue=KnowledgeReviewQueueStore(context,corpus)
        queue.enqueue(document("candidate"),LearningOrigin.OFFICIAL_SOURCE,LearningDecision.ACCEPT_FOR_REVIEW)
        assertTrue(queue.reject("doc-1",true))
        assertTrue(corpus.documents.isEmpty())
        assertTrue(queue.pending().isEmpty())
    }
}
