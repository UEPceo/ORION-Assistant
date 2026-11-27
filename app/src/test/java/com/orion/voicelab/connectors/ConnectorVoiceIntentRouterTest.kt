package com.orion.voicelab.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConnectorVoiceIntentRouterTest {
    @Test
    fun explicitGmailSearchProducesReadOnlyAction() {
        val routed = ConnectorVoiceIntentRouter.route("Busca en mi correo: factura de luz")
        assertEquals(ConnectorId.GMAIL, routed?.action?.connectorId)
        assertEquals("search_messages", routed?.action?.operation)
        assertEquals("factura de luz", routed?.action?.arguments?.get("query"))
        assertEquals(ConnectorRisk.READ_ONLY, routed?.action?.risk)
    }

    @Test
    fun missingGmailQueryAsksForClarification() {
        val routed = ConnectorVoiceIntentRouter.route("Busca en Gmail")
        assertEquals("", routed?.action?.arguments?.get("query"))
    }

    @Test
    fun explicitRepositoryListIsRouted() {
        val routed = ConnectorVoiceIntentRouter.route("Lista mis repositorios de GitHub")
        assertEquals(ConnectorId.GITHUB, routed?.action?.connectorId)
        assertEquals("list_repositories", routed?.action?.operation)
    }

    @Test
    fun issueQueryRequiresExplicitRepository() {
        val routed = ConnectorVoiceIntentRouter.route("Lista las incidencias de UEPceo/ORION-Assistant")
        assertEquals("UEPceo/ORION-Assistant", routed?.action?.arguments?.get("repository"))
        assertEquals("list_issues", routed?.action?.operation)
    }

    @Test
    fun unrelatedTextDoesNotTriggerExternalConnector() {
        assertNull(ConnectorVoiceIntentRouter.route("¿Qué tiempo hace hoy?"))
    }
}
