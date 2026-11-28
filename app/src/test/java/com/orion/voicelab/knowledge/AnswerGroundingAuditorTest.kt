package com.orion.voicelab.knowledge

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AnswerGroundingAuditorTest {
    private fun evidence(id: String) = KnowledgeEvidence(
        id=id, title="Source $id", canonicalUrl="https://example.org/$id",
        publisherId="publisher", domain=KnowledgeDomain.PROGRAMMING,
        authority=EvidenceAuthority.PRIMARY_TECHNICAL, retrievedAtMs=1000L,
        content="Kotlin is a statically typed programming language."
    )
    private fun claim(vararg ids: String) = AtomicAnswerClaim(
        id="claim-1", text="Kotlin is statically typed.", citedEvidenceIds=ids.toSet()
    )

    @Test fun supportedClaimRequiresConfidenceAndValidEvidenceReference() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.95,setOf(ev.first().id))
        })
        val report=auditor.audit(listOf(claim("source-1")),listOf(evidence("source-1")))
        assertEquals(AnswerGroundingStatus.VERIFIED,report.status)
        assertFalse(report.requiresAbstention)
    }

    @Test fun lowConfidenceMustNotBePromotedToVerified() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.55,setOf(ev.first().id))
        })
        val report=auditor.audit(listOf(claim("source-1")),listOf(evidence("source-1")))
        assertEquals(ClaimSupportStatus.INSUFFICIENT,report.claims.single().status)
        assertEquals(AnswerGroundingStatus.ABSTAIN,report.status)
    }

    @Test fun unsupportedCitationsAreRejectedAndCauseAbstention() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, _ ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.99,setOf("invented"))
        })
        val report=auditor.audit(listOf(claim("source-1")),listOf(evidence("source-1")))
        assertEquals(setOf("invented"),report.unsupportedCitationIds)
        assertEquals(AnswerGroundingStatus.PARTIALLY_SUPPORTED,report.status)
        assertTrue(report.requiresAbstention)
    }

    @Test fun contradictoryEvidenceForcesContradictedStatus() = runBlocking {
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.99,setOf(ev.first().id),setOf(ev.first().id))
        })
        val report=auditor.audit(listOf(claim("source-1")),listOf(evidence("source-1")))
        assertEquals(ClaimSupportStatus.CONTRADICTED,report.claims.single().status)
        assertEquals(AnswerGroundingStatus.ABSTAIN,report.status)
    }

    @Test fun missingEvidenceAndJudgeFailureRemainUnverifiableOrInsufficient() = runBlocking {
        val noSource=AnswerGroundingAuditor(SemanticEntailmentJudge { _, _ -> error("unused") })
            .audit(listOf(claim("missing")),emptyList())
        assertEquals(ClaimSupportStatus.INSUFFICIENT,noSource.claims.single().status)
        val failed=AnswerGroundingAuditor(SemanticEntailmentJudge { _, _ -> error("offline") })
            .audit(listOf(claim("source-1")),listOf(evidence("source-1")))
        assertEquals(ClaimSupportStatus.UNVERIFIABLE,failed.claims.single().status)
        assertEquals(AnswerGroundingStatus.ABSTAIN,failed.status)
    }

    @Test fun nonFactualClaimsDoNotBlockFactualAudit() = runBlocking {
        val factual=claim("source-1")
        val opinion=factual.copy(id="opinion",isFactual=false,citedEvidenceIds=emptySet())
        val auditor=AnswerGroundingAuditor(SemanticEntailmentJudge { _, ev ->
            SemanticJudgeAssessment(ClaimSupportStatus.SUPPORTED,0.95,setOf(ev.first().id))
        })
        val report=auditor.audit(listOf(factual,opinion),listOf(evidence("source-1")))
        assertEquals(1,report.claims.size)
        assertEquals(AnswerGroundingStatus.VERIFIED,report.status)
    }
}
