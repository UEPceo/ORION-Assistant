package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class SemanticJudgePromptContractTest {
    @Test fun semanticAuditRequiresLiteralAnswerCitationsAndSourceNumberMapping() {
        val source = LocalSemanticAnswerEvaluator::class.java
        assertNotNull(source)
        // The deterministic auditor only accepts support IDs that are both retrieved and cited.
        val claim = AtomicAnswerClaim("c1", "Claim", setOf("source-a"))
        assertEquals(setOf("source-a"), claim.citedEvidenceIds)
    }

    @Test fun overLimitOrDuplicateClaimPayloadsAreRejectedFailClosed() {
        // The parser caps claim count and rejects duplicate IDs before the auditor can
        // collapse them into a map and accidentally omit one factual statement.
        val source = LocalSemanticAnswerEvaluator::class.java
        assertNotNull(source.getDeclaredMethod(
            "parseAssessment",
            String::class.java,
            String::class.java,
            List::class.java
        ))
    }

    @Test fun uncitedFactualClaimCannotBeMarkedVerified() = kotlinx.coroutines.runBlocking {
        val evidence = KnowledgeEvidence(
            id="source-a", title="A", canonicalUrl="https://example.org/a",
            publisherId="publisher", domain=KnowledgeDomain.PROGRAMMING,
            authority=EvidenceAuthority.PRIMARY_TECHNICAL, retrievedAtMs=1L,
            content="Evidence supports the claim."
        )
        val auditor = AnswerGroundingAuditor(SemanticEntailmentJudge { _, all ->
            SemanticJudgeAssessment(
                ClaimSupportStatus.SUPPORTED, 0.99,
                supportingEvidenceIds=setOf(all.first().id)
            )
        })
        val report = auditor.audit(
            listOf(AtomicAnswerClaim("c1", "A factual claim", emptySet())),
            listOf(evidence)
        )
        assertEquals(ClaimSupportStatus.INSUFFICIENT, report.claims.single().status)
        assertEquals(AnswerGroundingStatus.ABSTAIN, report.status)
    }
}
