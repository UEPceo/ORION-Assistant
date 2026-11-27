package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgeVerifierTest {
    private fun evidence(
        publisher: String,
        authority: EvidenceAuthority,
        domain: KnowledgeDomain = KnowledgeDomain.PROGRAMMING,
        retrievedAt: Long = 9_000L
    ) = KnowledgeEvidence(
        id = publisher,
        title = "Reference",
        canonicalUrl = "https://example.org/$publisher",
        publisherId = publisher,
        domain = domain,
        authority = authority,
        retrievedAtMs = retrievedAt,
        content = "Evidence"
    )

    @Test
    fun twoIndependentSecondarySourcesMeetGeneralCorroborationThreshold() {
        val result = KnowledgeVerifier.assess(
            listOf(
                evidence("publisher-a", EvidenceAuthority.REPUTABLE_SECONDARY),
                evidence("publisher-b", EvidenceAuthority.PEER_REVIEWED)
            ),
            KnowledgeDomain.PROGRAMMING,
            nowMs = 10_000L,
            maxAgeMs = 60_000L
        )
        assertEquals(KnowledgeVerdict.SUPPORTED, result.verdict)
        assertEquals(2, result.independentPublisherCount)
    }

    @Test
    fun regulationRequiresPrimaryEvidence() {
        val result = KnowledgeVerifier.assess(
            listOf(evidence("blog", EvidenceAuthority.REPUTABLE_SECONDARY, KnowledgeDomain.LAW_AND_REGULATION)),
            KnowledgeDomain.LAW_AND_REGULATION,
            nowMs = 10_000L,
            maxAgeMs = 60_000L
        )
        assertEquals(KnowledgeVerdict.PRIMARY_SOURCE_REQUIRED, result.verdict)
    }

    @Test
    fun staleEvidenceIsNotPresentedAsVerified() {
        val result = KnowledgeVerifier.assess(
            listOf(evidence("official", EvidenceAuthority.OFFICIAL_PRIMARY, retrievedAt = 1_000L)),
            KnowledgeDomain.MARINE_WEATHER,
            nowMs = 100_000L,
            maxAgeMs = 10_000L
        )
        assertEquals(KnowledgeVerdict.STALE, result.verdict)
    }

    @Test
    fun conflictsOverrideSourceCount() {
        val result = KnowledgeVerifier.assess(
            listOf(
                evidence("official-a", EvidenceAuthority.OFFICIAL_PRIMARY),
                evidence("official-b", EvidenceAuthority.OFFICIAL_PRIMARY)
            ),
            KnowledgeDomain.PROGRAMMING,
            nowMs = 10_000L,
            maxAgeMs = 60_000L,
            conflictDetected = true
        )
        assertEquals(KnowledgeVerdict.CONFLICTING, result.verdict)
    }

    @Test
    fun noEvidenceIsExplicitlyUnverified() {
        val result = KnowledgeVerifier.assess(
            emptyList(),
            KnowledgeDomain.SURFCASTING,
            nowMs = 10_000L,
            maxAgeMs = 60_000L
        )
        assertEquals(KnowledgeVerdict.NO_EVIDENCE, result.verdict)
    }
    @Test
    fun retrievalTimeAloneDoesNotProveFreshness() {
        val result = KnowledgeVerifier.assess(
            listOf(evidence("official", EvidenceAuthority.OFFICIAL_PRIMARY, KnowledgeDomain.LAW_AND_REGULATION)),
            KnowledgeDomain.LAW_AND_REGULATION,
            nowMs = 10_000L,
            maxAgeMs = 60_000L,
            requiresPublishedDate = true
        )
        assertEquals(KnowledgeVerdict.AGE_UNCONFIRMED, result.verdict)
    }

    @Test
    fun oldPublicationDateIsNotMadeCurrentByFreshRetrieval() {
        val old = evidence("official", EvidenceAuthority.OFFICIAL_PRIMARY, KnowledgeDomain.LAW_AND_REGULATION)
            .copy(retrievedAtMs = 9_000L, publishedAtMs = 1_000L)
        val result = KnowledgeVerifier.assess(
            listOf(old),
            KnowledgeDomain.LAW_AND_REGULATION,
            nowMs = 100_000L,
            maxAgeMs = 10_000L,
            requiresPublishedDate = true
        )
        assertEquals(KnowledgeVerdict.STALE, result.verdict)
    }

}
