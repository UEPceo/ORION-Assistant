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
    @Test
    fun webSearchRequiresAnExplicitUserRequest() {
        assertEquals(true, WebSearchIntentPolicy.requiresWebSearch("Busca en internet las últimas novedades de Kotlin"))
        assertEquals(false, WebSearchIntentPolicy.requiresWebSearch("Explícame cómo funciona Kotlin"))
        val safeQuery = WebSearchIntentPolicy.searchTerms("Busca en internet documentación sobre Kotlin, contacto test@example.com teléfono +34 600 123 456")
        assertEquals(false, safeQuery.orEmpty().contains("test@example.com"))
        assertEquals(false, safeQuery.orEmpty().contains("+34 600 123 456"))
    }

    @Test
    fun unknownSearchSnippetsCannotVerifyGeneralClaims() {
        val snippets = listOf("a", "b").map { host ->
            KnowledgeEvidence(
                id = host,
                title = "Search snippet",
                canonicalUrl = "https://$host.example.org/result",
                publisherId = "$host.example.org",
                domain = KnowledgeDomain.GENERAL,
                authority = EvidenceAuthority.UNKNOWN,
                retrievedAtMs = 1_000L,
                content = "Search result excerpt"
            )
        }
        val result = KnowledgeVerifier.assess(
            evidence = snippets,
            domain = KnowledgeDomain.GENERAL,
            nowMs = 2_000L,
            maxAgeMs = 10_000L
        )
        assertEquals(KnowledgeVerdict.NEEDS_CORROBORATION, result.verdict)
    }

    @Test
    fun offlinePackTopicsRouteToTheirStoredDomains() {
        assertEquals(KnowledgeDomain.PROGRAMMING, BrainQueryRouter.route("Kotlin coroutines y Android Activity").domain)
        assertEquals(KnowledgeDomain.INTERNET, BrainQueryRouter.route("Qué es HTTP y cómo funciona un servidor web").domain)
        assertEquals(KnowledgeDomain.CYBERSECURITY, BrainQueryRouter.route("Cómo proteger contraseñas con doble factor").domain)
        assertEquals(KnowledgeDomain.FISHING, BrainQueryRouter.route("Cómo funciona PescaREC para pesca recreativa").domain)
        assertEquals(KnowledgeDomain.LAW_AND_REGULATION, BrainQueryRouter.route("Qué establece el Decreto 205/2023").domain)
        assertEquals(KnowledgeDomain.HEALTH_AND_SAFETY, BrainQueryRouter.route("Qué hacer ante una emergencia 112").domain)
    }


    @Test
    fun unclassifiedOriginCannotBePromotedByForgedAuthorityMetadata() {
        val evidence = KnowledgeEvidence(
            id = "unclassified-1",
            title = "Unclassified submission",
            canonicalUrl = "https://example.org/claim",
            publisherId = "unknown-publisher",
            domain = KnowledgeDomain.GENERAL,
            authority = EvidenceAuthority.OFFICIAL_PRIMARY,
            retrievedAtMs = 1L,
            content = "Unverified claim"
        )
        val candidate = LearningCandidate(
            document = KnowledgeDocument(evidence),
            origin = LearningOrigin.UNCLASSIFIED_SOURCE
        )
        assertEquals(LearningDecision.STORE_AS_UNVERIFIED, BrainLearningPolicy.decide(candidate))
    }

    @Test
    fun unknownSourcesKeepUnclassifiedProvenance() {
        assertEquals(LearningOrigin.UNCLASSIFIED_SOURCE, learningOriginForAuthority(EvidenceAuthority.UNKNOWN))
        assertEquals(LearningOrigin.UNCLASSIFIED_SOURCE, learningOriginForAuthority(EvidenceAuthority.USER_SUPPLIED))
        assertEquals(LearningOrigin.OFFICIAL_SOURCE, learningOriginForAuthority(EvidenceAuthority.OFFICIAL_PRIMARY))
        assertEquals(LearningOrigin.TECHNICAL_SOURCE, learningOriginForAuthority(EvidenceAuthority.PRIMARY_TECHNICAL))
    }
}
