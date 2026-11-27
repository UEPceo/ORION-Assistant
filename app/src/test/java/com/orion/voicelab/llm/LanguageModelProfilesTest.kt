package com.orion.voicelab.llm

import org.junit.Assert.*
import org.junit.Test

class LanguageModelProfilesTest {
    @Test fun lightweightOfflineModelRemainsDefaultOperationalProfile() {
        val current = LanguageModelProfileCatalog.available(ModelExecutionMode.ON_DEVICE_OFFLINE)
        assertEquals(listOf("qwen2.5-1.5b-local"), current.map { it.id })
        assertTrue(current.first().capabilities.contains(ModelCapability.GENERAL_CHAT))
    }
    @Test fun selectionDoesNotPromiseUnavailableReasoningCapability() {
        assertNull(LanguageModelProfileCatalog.select(ModelExecutionMode.ON_DEVICE_OFFLINE, setOf(ModelCapability.MULTI_STEP_REASONING)))
        assertNotNull(LanguageModelProfileCatalog.select(ModelExecutionMode.ON_DEVICE_OFFLINE, setOf(ModelCapability.GENERAL_CHAT), 1600))
        assertNull(LanguageModelProfileCatalog.select(ModelExecutionMode.ON_DEVICE_OFFLINE, setOf(ModelCapability.GENERAL_CHAT), 512))
    }
    @Test fun advancedAndRemoteProfilesAreNotFalselyMarkedOperational() {
        assertFalse(LanguageModelProfileCatalog.profiles.first { it.id == "local-advanced" }.operational)
        assertFalse(LanguageModelProfileCatalog.profiles.first { it.id == "remote-consented" }.operational)
    }
}
