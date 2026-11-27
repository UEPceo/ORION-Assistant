package com.orion.voicelab.brain
import org.junit.Assert.*
import org.junit.Test
class ToolCapabilityCatalogTest {
 @Test fun unavailableSandboxIsNeverPresentedAsOperational() {
  assertEquals(ToolAvailability.NOT_IMPLEMENTED,ToolCapabilityCatalog.find("code-sandbox")?.availability)
 }
 @Test fun browserLimitationsAreExplicit() {
  val browser=ToolCapabilityCatalog.find("browser")!!
  assertEquals(ToolAvailability.PARTIAL,browser.availability)
  assertTrue(browser.limitation.contains("PDF"))
 }
}
