package com.orion.voicelab.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowBudgetPolicyTest {
    @Test fun evidenceBudgetScalesWithEffectiveContext() {
        val lite = ContextWindowBudgetPolicy.evidenceChars(2048)
        val balanced = ContextWindowBudgetPolicy.evidenceChars(4096)
        val powerful = ContextWindowBudgetPolicy.evidenceChars(8192)
        assertTrue(lite in 1_500..1_800)
        assertTrue(balanced > lite)
        assertTrue(powerful > balanced)
        assertEquals(12_000, ContextWindowBudgetPolicy.evidenceChars(32_768))
    }

    @Test fun tinyWindowsKeepMinimumEvidenceBudgetButInvalidValuesAreRejected() {
        assertEquals(1_500, ContextWindowBudgetPolicy.evidenceChars(512))
        assertTrue(runCatching { ContextWindowBudgetPolicy.evidenceChars(256) }.isFailure)
        assertTrue(runCatching { ContextWindowBudgetPolicy.evidenceChars(65_536) }.isFailure)
    }
}
