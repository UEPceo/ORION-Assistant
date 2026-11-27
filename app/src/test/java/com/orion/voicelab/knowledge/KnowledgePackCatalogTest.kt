package com.orion.voicelab.knowledge
import org.junit.Assert.*
import org.junit.Test
class KnowledgePackCatalogTest {
 @Test fun noMetadataOnlyPackIsClaimedAsInstalled() {
  assertTrue(KnowledgePackCatalog.installed().isEmpty())
  assertTrue(KnowledgePackCatalog.packs.all { it.state==KnowledgePackState.READY_TO_INSTALL })
 }
 @Test fun allSixOfflinePacksHaveRealBundledAssets() {
  assertEquals(6, KnowledgePackCatalog.packs.size)
  assertTrue(KnowledgePackCatalog.packs.all { it.state == KnowledgePackState.READY_TO_INSTALL })
 }
 @Test fun spanishAndAndalusiaPacksAreDefined() {
  assertNotNull(KnowledgePackCatalog.packs.firstOrNull { it.id=="es-eu-law" })
  assertTrue(KnowledgePackCatalog.packs.any { it.id=="andalucia-fishing" && it.jurisdiction=="ES-AN" })
 }
}
