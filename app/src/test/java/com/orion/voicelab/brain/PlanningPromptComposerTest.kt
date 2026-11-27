package com.orion.voicelab.brain
import org.junit.Assert.*
import org.junit.Test
class PlanningPromptComposerTest {
 @Test fun recognizesMultiStepIntentAndAddsExecutionSafeguards() {
  val prompt=PlanningPromptComposer.compose("Investiga y compara tres opciones y prepara un informe")
  assertTrue(prompt.contains("MODO DE PLANIFICACIÓN"))
  assertTrue(prompt.contains("éxito verificable"))
  assertTrue(prompt.contains("confirmación específica"))
  assertTrue(prompt.contains("Gmail"))
  assertFalse(prompt.contains("Entorno de programación aislado"))
 }
 @Test fun ordinaryQuestionDoesNotForcePlanningMode() {
  assertEquals("",PlanningPromptComposer.compose("Qué es Kotlin"))
 }
}
