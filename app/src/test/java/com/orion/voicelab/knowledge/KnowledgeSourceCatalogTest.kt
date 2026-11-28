package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class KnowledgeSourceCatalogTest {
    @Test fun criticalDomainsHaveOfficialSeeds() {
        listOf(KnowledgeDomain.LAW_AND_REGULATION, KnowledgeDomain.MARINE_WEATHER, KnowledgeDomain.HEALTH_AND_SAFETY, KnowledgeDomain.CYBERSECURITY).forEach { domain ->
            assertTrue("$domain", KnowledgeSourceCatalog.forDomain(domain).any { it.authority == EvidenceAuthority.OFFICIAL_PRIMARY })
        }
    }
    @Test fun recognizesExactOfficialAndTechnicalHosts() {
        assertEquals("boe", KnowledgeSourceCatalog.match("https://www.boe.es/buscar", KnowledgeDomain.LAW_AND_REGULATION)?.id)
        assertEquals("android", KnowledgeSourceCatalog.match("https://developer.android.com/guide", KnowledgeDomain.PROGRAMMING)?.id)
    }

    @Test fun refusesLookalikeHostsAndWrongDomains() {
        assertNull(KnowledgeSourceCatalog.match("https://boe.es.attacker.example/document", KnowledgeDomain.LAW_AND_REGULATION))
        assertNull(KnowledgeSourceCatalog.match("https://developer.android.com/guide", KnowledgeDomain.FINANCE))
        assertNull(KnowledgeSourceCatalog.match("http://www.boe.es/document", KnowledgeDomain.LAW_AND_REGULATION))
    }

    @Test fun everySeedHasHttpsAndReviewCadence() {
        assertTrue(KnowledgeSourceCatalog.seeds.isNotEmpty())
        assertTrue(KnowledgeSourceCatalog.seeds.all { it.url.startsWith("https://") && it.reviewIntervalDays > 0 && it.licenseNote.isNotBlank() })
    }
}
