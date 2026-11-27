package com.orion.voicelab.knowledge
import org.junit.Assert.*
import org.junit.Test
class OfficialSearchTargetingTest {
 @Test fun legalQueriesPreferOfficialSpanishAndEuropeanDomains() {
  val query=BrainQueryRouter.route("Busca en internet normativa de pesca vigente")
  val result=buildSearchQuery(query,"normativa de pesca vigente")
  assertTrue(result.contains("site:boe.es"))
  assertTrue(result.contains("site:eur-lex.europa.eu"))
 }
 @Test fun weatherQueriesPreferAemet() {
  val query=BrainQueryRouter.route("Busca en internet previsión de viento y oleaje")
  assertTrue(buildSearchQuery(query,"viento oleaje").contains("site:aemet.es"))
 }
 @Test fun generalQueriesAreNotArtificiallyRestricted() {
  val query=BrainQueryRouter.route("Busca en internet historia del surfcasting")
  assertEquals("historia del surfcasting",buildSearchQuery(query,"historia del surfcasting"))
 }
}
