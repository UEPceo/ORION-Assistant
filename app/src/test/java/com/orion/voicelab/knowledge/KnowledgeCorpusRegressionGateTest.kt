package com.orion.voicelab.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeCorpusRegressionGateTest {
    private val now = 1_800_000_000_000L

    private class FakeCorpus(
        private val documents: List<KnowledgeDocument>,
        private val crowdOutBaseline: Boolean = false
    ) : KnowledgeRepository {
        override suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument> =
            documents.take(limit)

        override suspend fun allDocuments(): List<KnowledgeDocument> = documents

        override suspend fun searchWithCandidate(
            query: BrainQuery,
            limit: Int,
            candidate: KnowledgeDocument
        ): List<KnowledgeDocument> {
            if (crowdOutBaseline && query.normalizedText == "alpha guide") {
                return (listOf(candidate) + documents.take(4)).take(limit)
            }
            return (listOf(candidate) + documents).distinctBy { it.evidence.id }.take(limit)
        }

        override suspend fun upsert(document: KnowledgeDocument) = Unit
        override suspend fun remove(documentId: String) = Unit
        override suspend fun contains(documentId: String) = documents.any { it.evidence.id == documentId }
        override suspend fun get(documentId: String) = documents.firstOrNull { it.evidence.id == documentId }
    }

    private fun document(id: String, title: String): KnowledgeDocument {
        val content = "A sufficiently detailed source document for regression testing."
        return KnowledgeDocument(
            KnowledgeEvidence(
                id = id,
                title = title,
                canonicalUrl = "https://docs.example/$id",
                publisherId = "docs.example",
                domain = KnowledgeDomain.PROGRAMMING,
                authority = EvidenceAuthority.PRIMARY_TECHNICAL,
                retrievedAtMs = now,
                content = content
            )
        )
    }

    @Test
    fun candidatePassesWhenItCanBeRetrievedWithoutChangingBaseline() = runBlocking {
        val candidate = document("candidate", "New compiler guide")
        val gate = KnowledgeCorpusRegressionGate(FakeCorpus(emptyList()), nowMs = { now })
        val result = gate.evaluate(candidate)

        assertTrue(result.candidateCasePassed)
        assertTrue(result.passed)
        assertTrue(result.regressions.isEmpty())
    }

    @Test
    fun blocksCandidateThatDisplacesPreviouslyRetrievableEvidence() = runBlocking {
        val baseline = (1..5).map { document("doc-$it", "Alpha Guide") }
        val candidate = document("candidate", "Alpha Guide")
        val gate = KnowledgeCorpusRegressionGate(
            FakeCorpus(baseline, crowdOutBaseline = true),
            nowMs = { now }
        )
        val result = gate.evaluate(candidate)

        assertTrue(result.candidateCasePassed)
        assertFalse(result.passed)
        assertTrue(result.regressions.any { it.contains("doc-5") })
    }
}
