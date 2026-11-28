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

 @Test fun githubCatalogListsOnlyImplementedOperations() {
  val github=ToolCapabilityCatalog.find("github")!!
  assertTrue(github.operations.containsAll(setOf(
   "list_repositories","list_issues","get_issue","list_commits","list_workflow_runs",
   "list_files","get_file","create_branch","commit_changes","create_pull_request"
  )))
  assertFalse(github.operations.contains("get_repository"))
  assertEquals(ToolAvailability.PARTIAL,github.availability)
 }
}
