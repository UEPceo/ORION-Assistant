package com.orion.voicelab.llm
import org.junit.Assert.*
import org.junit.Test
class ModelProfilePromptComposerTest {
 @Test fun promptDisclosesActualLocalModelAndExecutionLimits() {
  val prompt=ModelProfilePromptComposer.compose()
  assertTrue(prompt.contains("Qwen2.5 1.5B"))
  assertTrue(prompt.contains("2048 tokens"))
  assertTrue(prompt.contains("No afirmes que has ejecutado código"))
 }
}
