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
        assertEquals("in:anywhere", routed?.action?.arguments?.get("query"))
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
    fun followUpCommitRequestUsesPreviousRepositoryContext() {
        val routed = ConnectorVoiceIntentRouter.route("¿Y los commits recientes?", "UEPceo/ORION-Assistant")
        assertEquals("list_commits", routed?.action?.operation)
        assertEquals("UEPceo/ORION-Assistant", routed?.action?.arguments?.get("repository"))
    }

    @Test
    fun ciStatusRequestUsesPreviousRepositoryContext() {
        val routed = ConnectorVoiceIntentRouter.route("¿Cómo va el CI?", "UEPceo/ORION-Assistant")
        assertEquals("list_workflow_runs", routed?.action?.operation)
        assertEquals("UEPceo/ORION-Assistant", routed?.action?.arguments?.get("repository"))
    }

    @Test
    fun issueDetailRequestUsesRepositoryAndIssueNumber() {
        val routed = ConnectorVoiceIntentRouter.route(
            "Dame los detalles de la incidencia #42 en UEPceo/ORION-Assistant"
        )
        assertEquals("get_issue", routed?.action?.operation)
        assertEquals("42", routed?.action?.arguments?.get("number"))
        assertEquals("UEPceo/ORION-Assistant", routed?.action?.arguments?.get("repository"))
    }

    @Test
    fun explicitGmailMessageIdRoutesToDetailRead() {
        val routed = ConnectorVoiceIntentRouter.route("Abre el correo con id 18c0abcdef123456")
        assertEquals(ConnectorId.GMAIL, routed?.action?.connectorId)
        assertEquals("get_message", routed?.action?.operation)
        assertEquals("18c0abcdef123456", routed?.action?.arguments?.get("message_id"))
    }

    @Test
    fun unrelatedTextDoesNotTriggerExternalConnector() {
        assertNull(ConnectorVoiceIntentRouter.route("¿Qué tiempo hace hoy?"))
    }
}
