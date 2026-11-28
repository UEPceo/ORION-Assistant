package com.orion.voicelab.llm

import org.junit.Assert.*
import org.junit.Test

class ModelProfilePromptComposerTest {
    @Test fun promptDisclosesActualLocalModelAndExecutionLimits() {
        val prompt = ModelProfilePromptComposer.compose()
        assertTrue(prompt.contains("Modelo: Ligero"))
        assertTrue(prompt.contains("2048 tokens"))
        assertTrue(prompt.contains("No afirmes que has ejecutado código"))
    }

    @Test fun promptReflectsSelectedUpperProfileAndItsContext() {
        val profile = LanguageModelProfileCatalog.byId("powerful")!!
        val prompt = ModelProfilePromptComposer.compose(profile)
        assertTrue(prompt.contains("Modelo: Potente"))
        assertTrue(prompt.contains("4096 tokens"))
        assertTrue(prompt.contains("MULTI_STEP_REASONING"))
    }

    @Test fun promptDoesNotDescribeEveryProfileAsLightweight() {
        val prompt = ModelProfilePromptComposer.compose(LanguageModelProfileCatalog.byId("flagship")!!)
        assertFalse(prompt.contains("Este perfil es ligero"))
        assertTrue(prompt.contains("Modelo: Flagship"))
    }
}
