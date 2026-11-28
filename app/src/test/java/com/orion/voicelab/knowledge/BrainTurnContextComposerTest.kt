package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainTurnContextComposerTest {
    @Test
    fun joinsEveryContextChannelWithRealLineBreaks() {
        val result = BrainTurnContextComposer.compose(
            basePrompt = "IDENTIDAD",
            knowledgePrompt = "EVIDENCIA",
            modelGuidance = "MODELO",
            planningGuidance = "PLAN",
            creativeContext = "CREATIVO",
            selfKnowledgeContext = "AUTODIAGNÓSTICO"
        )

        assertEquals(
            listOf("IDENTIDAD", "EVIDENCIA", "MODELO", "PLAN", "CREATIVO", "AUTODIAGNÓSTICO").joinToString("\n\n"),
            result
        )
        assertFalse(result.contains("\\n"))
    }

    @Test
    fun omitsEmptyChannelsWithoutLosingTheRemainingConnections() {
        val result = BrainTurnContextComposer.compose(
            basePrompt = "  IDENTIDAD  ",
            knowledgePrompt = "",
            modelGuidance = " MODELO ",
            planningGuidance = " ",
            creativeContext = "CREATIVO"
        )

        assertEquals("IDENTIDAD\n\nMODELO\n\nCREATIVO", result)
        assertTrue(result.startsWith("IDENTIDAD"))
        assertTrue(result.endsWith("CREATIVO"))
    }
}
