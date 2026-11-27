package com.orion.voicelab.knowledge
import org.junit.Assert.*
import org.junit.Test
class KnowledgePackCatalogTest {
 @Test fun noMetadataOnlyPackIsClaimedAsInstalled() {
  assertTrue(KnowledgePackCatalog.withRuntimeState(emptySet()).none { it.state == KnowledgePackState.INSTALLED })
  assertTrue(KnowledgePackCatalog.packs.all { it.state==KnowledgePackState.READY_TO_INSTALL })
 }
 @Test fun allSixOfflinePacksHaveRealBundledAssets() {
  assertEquals(6, KnowledgePackCatalog.packs.size)
  assertTrue(KnowledgePackCatalog.packs.all { it.state == KnowledgePackState.READY_TO_INSTALL })
 }
 @Test fun runtimeStateReflectsActuallyInstalledPackIds() {
  val states = KnowledgePackCatalog.withRuntimeState(setOf("android-kotlin", "es-cyber"))
  assertEquals(KnowledgePackState.INSTALLED, states.first { it.id == "android-kotlin" }.state)
  assertEquals(KnowledgePackState.READY_TO_INSTALL, states.first { it.id == "python-web" }.state)
 }
 @Test fun spanishAndAndalusiaPacksAreDefined() {
  assertNotNull(KnowledgePackCatalog.packs.firstOrNull { it.id=="es-eu-law" })
  assertTrue(KnowledgePackCatalog.packs.any { it.id=="andalucia-fishing" && it.jurisdiction=="ES-AN" })
 }
}
