package com.orion.voicelab.brain

import org.junit.Assert.*
import org.junit.Test

class CapabilityEvaluationTest {
    @Test fun everyAreaMustReachEight() {
        val all = CapabilityArea.entries.associateWith { listOf(EvaluationCriterion("core", 8.0, true, "passed"), EvaluationCriterion("gap", 2.0, false, "missing")) }
        val result = CapabilityEvaluator.evaluate(all)
        assertEquals(8.0, result.minimumScore, 0.001)
        assertTrue(result.meetsTarget)
        val below = all.toMutableMap().apply { put(CapabilityArea.MULTIMODAL, listOf(EvaluationCriterion("provider", 1.0, false, "No provider"))) }
        assertFalse(CapabilityEvaluator.evaluate(below).meetsTarget)
    }
    @Test fun missingEvidenceScoresZero() {
        val result = CapabilityEvaluator.evaluate(emptyMap())
        assertEquals(0.0, result.minimumScore, 0.001)
        assertFalse(result.meetsTarget)
    }
}
