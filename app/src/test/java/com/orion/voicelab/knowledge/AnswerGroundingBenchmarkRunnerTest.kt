package com.orion.voicelab.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnswerGroundingBenchmarkRunnerTest {
    private val source = KnowledgeEvidence(
        id="official", title="Official source", canonicalUrl="https://example.org",
        publisherId="official", domain=KnowledgeDomain.LAW_AND_REGULATION,
        authority=EvidenceAuthority.OFFICIAL_PRIMARY, retrievedAtMs=1000L,
        content="The regulation states the limit is 25."
    )

    @Test fun benchmarkReportsMetricsAndPassesOnlyWhenAllConfiguredGatesPass() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.99,setOf(ev.first().id))
        })
        val cases=(1..4).map { n ->
            val claim=AtomicAnswerClaim("c$n","The limit is 25.",setOf("official"))
            GroundingBenchmarkCase("case$n",listOf(claim),listOf(source),
                mapOf("c$n" to ClaimSupportStatus.SUPPORTED),expectedAbstention=false)
        }
        val criteria=GroundingAcceptanceCriteria(
            minimumFactualClaims=4,minimumContradictionCases=0,minimumAbstentionCases=0,
            minimumSupportedPrecision=1.0,minimumContradictionRecall=0.0,
            minimumAbstentionAccuracy=0.0,minimumCitationRejectionRate=1.0
        )
        val report=AnswerGroundingBenchmarkRunner(auditor,criteria).run(cases)
        assertTrue(report.sampleSufficient)
        assertTrue(report.passed)
        assertEquals(4,report.metrics.factualClaims)
        assertEquals(1.0,report.metrics.supportedPrecision,0.0)
    }

    @Test fun smallBenchmarkCannotPassDefaultMinimumSampleGate() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.99,setOf(ev.first().id))
        })
        val claim=AtomicAnswerClaim("c","A factual claim",setOf("official"))
        val report=AnswerGroundingBenchmarkRunner(auditor).run(listOf(
            GroundingBenchmarkCase("tiny",listOf(claim),listOf(source),
                mapOf("c" to ClaimSupportStatus.SUPPORTED),expectedAbstention=false)
        ))
        assertFalse(report.sampleSufficient)
        assertFalse(report.passed)
        assertTrue(report.failedCriteria.any { it.contains("sample") })
    }

    @Test fun criticalDomainFalsePositiveFailsAcceptance() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.99,setOf(ev.first().id))
        })
        val claim=AtomicAnswerClaim("c","An unsupported legal claim",setOf("official"))
        val case=GroundingBenchmarkCase("critical",listOf(claim),listOf(source),
            mapOf("c" to ClaimSupportStatus.CONTRADICTED),expectedAbstention=true,criticalDomain=true)
        val criteria=GroundingAcceptanceCriteria(
            minimumFactualClaims=1,minimumContradictionCases=1,minimumAbstentionCases=1,
            minimumSupportedPrecision=0.0,minimumContradictionRecall=0.0,
            minimumAbstentionAccuracy=0.0,maximumCriticalFalsePositives=0
        )
        val report=AnswerGroundingBenchmarkRunner(auditor,criteria).run(listOf(case))
        assertEquals(1,report.metrics.criticalFalsePositives)
        assertFalse(report.passed)
    }
}
