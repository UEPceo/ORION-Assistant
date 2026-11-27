package com.orion.voicelab.knowledge
import org.junit.Assert.*
import org.junit.Test
class KnowledgePackCatalogTest {
 @Test fun noMetadataOnlyPackIsClaimedAsInstalled() {
  assertTrue(KnowledgePackCatalog.withRuntimeState(emptySet()).none { it.state == KnowledgePackState.INSTALLED })
  assertTrue(KnowledgePackCatalog.packs.all { it.state == KnowledgePackState.READY_TO_INSTALL })
 }
 @Test fun allFifteenOfflinePacksAreRegistered() {
  assertEquals(15, KnowledgePackCatalog.packs.size)
  assertEquals(15, KnowledgePackCatalog.packs.map { it.id }.toSet().size)
  assertTrue(KnowledgePackCatalog.packs.all { it.state == KnowledgePackState.READY_TO_INSTALL })
 }
 @Test fun runtimeStateReflectsActuallyInstalledPackIds() {
  val states = KnowledgePackCatalog.withRuntimeState(setOf("android-kotlin", "es-cyber"))
  assertEquals(KnowledgePackState.INSTALLED, states.first { it.id == "android-kotlin" }.state)
  assertEquals(KnowledgePackState.READY_TO_INSTALL, states.first { it.id == "python-web" }.state)
 }
 @Test fun spanishAndAndalusiaPacksAreDefined() {
  assertNotNull(KnowledgePackCatalog.packs.firstOrNull { it.id == "es-eu-law" })
  assertTrue(KnowledgePackCatalog.packs.any { it.id == "andalucia-fishing" && it.jurisdiction == "ES-AN" })
 }
 @Test fun allRequestedDisciplinesHaveDedicatedPacks() {
  val ids=KnowledgePackCatalog.packs.map { it.id }.toSet()
  assertTrue(ids.containsAll(setOf("computer-science","mathematics","science","engineering","finance","business","communication","creativity","general-knowledge")))
  assertTrue(KnowledgePackCatalog.packs.any { KnowledgeDomain.MATHEMATICS in it.domains })
  assertTrue(KnowledgePackCatalog.packs.any { KnowledgeDomain.SCIENCE in it.domains })
  assertTrue(KnowledgePackCatalog.packs.any { KnowledgeDomain.ENGINEERING in it.domains })
  assertTrue(KnowledgePackCatalog.packs.any { KnowledgeDomain.COMMUNICATION in it.domains })
 }
}
