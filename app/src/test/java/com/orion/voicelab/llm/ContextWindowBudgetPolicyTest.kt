package com.orion.voicelab.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextWindowBudgetPolicyTest {
    @Test fun evidenceBudgetScalesWithEffectiveContextWithoutExceedingPromptBudget() {
        val lite = ContextWindowBudgetPolicy.evidenceChars(2048)
        val balanced = ContextWindowBudgetPolicy.evidenceChars(4096)
        val powerful = ContextWindowBudgetPolicy.evidenceChars(8192)
        assertTrue(lite in 1_400..1_600)
        assertTrue(balanced > lite)
        assertTrue(powerful > balanced)
        assertEquals(12_000, ContextWindowBudgetPolicy.evidenceChars(32_768))
        assertTrue(ContextWindowBudgetPolicy.evidenceChars(512) < ContextWindowBudgetPolicy.promptChars(512))
    }

    @Test fun remainingEvidenceBudgetNeverInventsSpaceWhenPromptIsFull() {
        assertEquals(0, ContextWindowBudgetPolicy.evidenceChars(512, 10_000, 10_000))
        assertEquals(0, ContextWindowBudgetPolicy.evidenceChars(512, 600, 0))
        assertTrue(ContextWindowBudgetPolicy.evidenceChars(2048, 1_000, 500) > 0)
    }

    @Test fun invalidReservationsAreRejectedInsteadOfClampedToFakeCapacity() {
        assertTrue(runCatching { ContextWindowBudgetPolicy.promptChars(512, 500, 20) }.isFailure)
        assertTrue(runCatching { ContextWindowBudgetPolicy.evidenceChars(256) }.isFailure)
        assertTrue(runCatching { ContextWindowBudgetPolicy.evidenceChars(65_536) }.isFailure)
    }
}
