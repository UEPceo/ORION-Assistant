package com.orion.voicelab.knowledge
import org.junit.Assert.*
import org.junit.Test
class CrossDomainConceptGraphTest {
 @Test fun recognizesMultilingualAliases() {
  assertTrue(CrossDomainConceptGraph.directConcepts("mareas").contains("fishing"))
  assertTrue(CrossDomainConceptGraph.directConcepts("wind").contains("weather"))
  assertTrue(CrossDomainConceptGraph.directConcepts("kotlin").contains("programming"))
 }
 @Test fun associationsAreWeakAndOneHop() {
  val a=CrossDomainConceptGraph.associatedConcepts(setOf("fishing"))
  assertEquals(0.24,a["weather"]!!,0.0001)
  assertTrue(a.containsKey("geography"))
  assertFalse(a.containsKey("finance"))
 }
 @Test fun textConceptExtractionWorks() {
  assertTrue(CrossDomainConceptGraph.conceptsForText("viento y oleaje").contains("weather"))
  assertTrue(CrossDomainConceptGraph.conceptsForText("diseño y composición").contains("creativity"))
 }
}
