package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class KnowledgeRegressionEvaluatorTest {
    private val now=1_800_000_000_000L
    private fun evidence(id:String,ageMs:Long=0,domain:KnowledgeDomain=KnowledgeDomain.PROGRAMMING,authority:EvidenceAuthority=EvidenceAuthority.PRIMARY_TECHNICAL)=KnowledgeEvidence(
        id=id,title=id,canonicalUrl="https://docs.example/$id",publisherId="docs",domain=domain,authority=authority,retrievedAtMs=now-ageMs,content="evidence",contentHash="hash"
    )
    @Test fun passesWhenExpectedEvidenceIsRetrievedAndCitedWithFreshPrimaryEvidence() {
        val c=KnowledgeRegressionCase("kotlin","Kotlin API",setOf("official"),KnowledgeDomain.PROGRAMMING,true,true,1000)
        val e=evidence("official")
        val o=KnowledgeRegressionObservation("kotlin",setOf("official"),setOf("official"),listOf(e),"Answer grounded in evidence.")
        assertTrue(KnowledgeRegressionEvaluator.evaluate(c,o,now).passed)
    }
    @Test fun detectsMissingExpectedAndUnsupportedCitation() {
        val c=KnowledgeRegressionCase("case","query",setOf("expected"),KnowledgeDomain.PROGRAMMING)
        val o=KnowledgeRegressionObservation("case",setOf("retrieved"),setOf("invented"),listOf(evidence("retrieved")),"Answer")
        val r=KnowledgeRegressionEvaluator.evaluate(c,o,now)
        assertFalse(r.passed); assertEquals(setOf("expected"),r.missingExpectedEvidence); assertEquals(setOf("expected"),r.missingExpectedCitations); assertEquals(setOf("invented"),r.unsupportedCitations)
    }
    @Test fun detectsStaleAndWrongDomainEvidence() {
        val c=KnowledgeRegressionCase("fresh","query",setOf("old"),KnowledgeDomain.LAW_AND_REGULATION,true,true,100)
        val o=KnowledgeRegressionObservation("fresh",setOf("old"),setOf("old"),listOf(evidence("old",1000,KnowledgeDomain.PROGRAMMING,EvidenceAuthority.COMMUNITY)),"Answer")
        val r=KnowledgeRegressionEvaluator.evaluate(c,o,now)
        assertFalse(r.passed); assertEquals(setOf("old"),r.staleEvidenceIds); assertEquals(setOf("old"),r.domainMismatchIds)
    }
    @Test fun emptyAnswersFailAndScoreIsDeterministic() {
        val c=KnowledgeRegressionCase("empty","q",emptySet(),KnowledgeDomain.PROGRAMMING)
        val o=KnowledgeRegressionObservation("empty",emptySet(),emptySet(),emptyList()," ")
        val r=KnowledgeRegressionEvaluator.evaluate(c,o,now)
        assertTrue(r.answerEmpty); assertEquals(0.0,KnowledgeRegressionEvaluator.score(listOf(r)),0.0)
    }
}
