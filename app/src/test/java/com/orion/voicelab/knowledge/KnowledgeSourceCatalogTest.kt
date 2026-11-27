package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class KnowledgeSourceCatalogTest {
    @Test fun criticalDomainsHaveOfficialSeeds() {
        listOf(KnowledgeDomain.LAW_AND_REGULATION, KnowledgeDomain.MARINE_WEATHER, KnowledgeDomain.HEALTH_AND_SAFETY, KnowledgeDomain.CYBERSECURITY).forEach { domain ->
            assertTrue("$domain", KnowledgeSourceCatalog.forDomain(domain).any { it.authority == EvidenceAuthority.OFFICIAL_PRIMARY })
        }
    }
    @Test fun everySeedHasHttpsAndReviewCadence() {
        assertTrue(KnowledgeSourceCatalog.seeds.isNotEmpty())
        assertTrue(KnowledgeSourceCatalog.seeds.all { it.url.startsWith("https://") && it.reviewIntervalDays > 0 && it.licenseNote.isNotBlank() })
    }
}
