package com.orion.voicelab.llm

import org.junit.Assert.*
import org.junit.Test

class LanguageModelProfilesTest {
    @Test fun lightweightOfflineModelRemainsOnlyOperationalProfile() {
        val current = LanguageModelProfileCatalog.available(ModelExecutionMode.ON_DEVICE_OFFLINE)
        assertEquals(listOf("lite"), current.map { it.id })
        assertTrue(current.first().capabilities.contains(ModelCapability.GENERAL_CHAT))
    }

    @Test fun selectsRecommendationFromPhysicalRamBands() {
        assertEquals("lite", LanguageModelProfileCatalog.recommendForDevice(5_500L).id)
        assertEquals("balanced", LanguageModelProfileCatalog.recommendForDevice(8_000L).id)
        assertEquals("powerful", LanguageModelProfileCatalog.recommendForDevice(12_000L).id)
        assertEquals("flagship", LanguageModelProfileCatalog.recommendForDevice(16_000L).id)
    }

    @Test fun unavailableRequestedTierSafelyFallsBackToLite() {
        assertEquals("lite", LanguageModelProfileCatalog.effectiveProfile("flagship", 16_000L).id)
        assertEquals("lite", LanguageModelProfileCatalog.effectiveProfile("balanced", 6_000L).id)
        assertEquals("lite", LanguageModelProfileCatalog.effectiveProfile("unknown", 8_000L).id)
    }

    @Test fun selectionDoesNotPromiseUnavailableReasoningCapability() {
        assertNull(LanguageModelProfileCatalog.select(ModelExecutionMode.ON_DEVICE_OFFLINE, setOf(ModelCapability.MULTI_STEP_REASONING)))
        assertNotNull(LanguageModelProfileCatalog.select(ModelExecutionMode.ON_DEVICE_OFFLINE, setOf(ModelCapability.GENERAL_CHAT), 6000))
        assertNull(LanguageModelProfileCatalog.select(ModelExecutionMode.ON_DEVICE_OFFLINE, setOf(ModelCapability.GENERAL_CHAT), 512))
    }

    @Test fun candidateTiersAreNotMarkedOperationalBeforeTheirArtifactsAreValidated() {
        assertFalse(LanguageModelProfileCatalog.byId("balanced")!!.operational)
        assertFalse(LanguageModelProfileCatalog.byId("powerful")!!.operational)
        assertFalse(LanguageModelProfileCatalog.byId("flagship")!!.operational)
    }
}
