package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

class BrainCoreTest {
    @Test
    fun legalQueriesRequirePrimaryEvidence() {
        val query = BrainQueryRouter.route("¿Cuál es la talla mínima según la normativa?")
        assertEquals(KnowledgeDomain.LAW_AND_REGULATION, query.domain)
        assertEquals(true, query.requiresPrimaryEvidence)
        assertEquals(true, query.requiresFreshEvidence)
    }

    @Test
    fun surfcastingQueriesAreClassified() {
        val query = BrainQueryRouter.route("Qué cebo usar para lubina al surfcasting")
        assertEquals(KnowledgeDomain.SURFCASTING, query.domain)
    }

    @Test
    fun currentSocialAlgorithmClaimsRequireOfficialPrimaryEvidence() {
        val sources = listOf("a", "b").map { publisher ->
            KnowledgeEvidence(
                id = publisher,
                title = "Social ranking overview",
                canonicalUrl = "https://$publisher.example.org/ranking",
                publisherId = publisher,
                domain = KnowledgeDomain.SOCIAL_MEDIA,
                authority = EvidenceAuthority.REPUTABLE_SECONDARY,
                retrievedAtMs = 1_000L,
                content = "Ranking overview"
            )
        }
        val result = KnowledgeVerifier.assess(
            evidence = sources,
            domain = KnowledgeDomain.SOCIAL_MEDIA,
            nowMs = 2_000L,
            maxAgeMs = 10_000L
        )
        assertEquals(KnowledgeVerdict.PRIMARY_SOURCE_REQUIRED, result.verdict)
    }

    @Test
    fun modelOutputCannotBecomeTrainingEvidence() {
        val evidence = KnowledgeEvidence(
            id = "model-1",
            title = "Generated answer",
            canonicalUrl = "https://example.org/generated",
            publisherId = "orion",
            domain = KnowledgeDomain.GENERAL,
            authority = EvidenceAuthority.UNKNOWN,
            retrievedAtMs = 1L,
            content = "Generated content"
        )
        val candidate = LearningCandidate(
            document = KnowledgeDocument(evidence),
            origin = LearningOrigin.MODEL_OUTPUT
        )
        assertEquals(LearningDecision.REJECT_AS_EVIDENCE, BrainLearningPolicy.decide(candidate))
    }

    @Test
    fun userCorrectionNeedsApprovalBeforeReview() {
        val evidence = KnowledgeEvidence(
            id = "user-1",
            title = "User correction",
            canonicalUrl = "https://example.org/user-note",
            publisherId = "user",
            domain = KnowledgeDomain.GENERAL,
            authority = EvidenceAuthority.USER_SUPPLIED,
            retrievedAtMs = 1L,
            content = "Correction"
        )
        val candidate = LearningCandidate(
            document = KnowledgeDocument(evidence),
            origin = LearningOrigin.USER_CORRECTION
        )
        assertEquals(LearningDecision.STORE_AS_UNVERIFIED, BrainLearningPolicy.decide(candidate))
    }
}
