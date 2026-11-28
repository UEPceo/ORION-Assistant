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
    fun naturalSenderSearchBecomesGmailFromQuery() {
        val routed = ConnectorVoiceIntentRouter.route("Busca un correo de Amazon en Gmail")
        assertEquals("from:Amazon", routed?.action?.arguments?.get("query"))
    }

    @Test
    fun latestEmailRequestRoutesToReadLatestMessage() {
        val routed = ConnectorVoiceIntentRouter.route("Lee mi último correo de Amazon")
        assertEquals("read_latest_message", routed?.action?.operation)
        assertEquals("newer_than:30d from:Amazon", routed?.action?.arguments?.get("query"))
    }

    @Test
    fun senderSearchDoesNotRequireMentioningGmail() {
        val routed = ConnectorVoiceIntentRouter.route("Busca un correo de Amazon")
        assertEquals(ConnectorId.GMAIL, routed?.action?.connectorId)
        assertEquals("from:Amazon", routed?.action?.arguments?.get("query"))
    }

    @Test
    fun recentMailRequestUsesGmailDateOperator() {
        val routed = ConnectorVoiceIntentRouter.route("Enséñame los últimos correos de Gmail")
        assertEquals("newer_than:7d", routed?.action?.arguments?.get("query"))
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
    fun explicitMapsSearchRoutesPlaceQuery() {
        val routed = ConnectorVoiceIntentRouter.route("Busca en Google Maps la playa de Zahara")
        assertEquals(ConnectorId.GOOGLE_MAPS, routed?.action?.connectorId)
        assertEquals("search_place", routed?.action?.operation)
        assertEquals("la playa de Zahara", routed?.action?.arguments?.get("destination"))
    }

    @Test
    fun mapsNavigationRoutesDestinationAndMode() {
        val routed = ConnectorVoiceIntentRouter.route("Navega a Google Maps hacia Zahara de los Atunes a pie")
        assertEquals(ConnectorId.GOOGLE_MAPS, routed?.action?.connectorId)
        assertEquals("navigate", routed?.action?.operation)
        assertEquals("walking", routed?.action?.arguments?.get("mode"))
        assertEquals("Zahara de los Atunes", routed?.action?.arguments?.get("destination"))
    }

    @Test
    fun pescaiaPublicQueryIsRouted() {
        val routed = ConnectorVoiceIntentRouter.route("Consulta en PescaIA: mareas de Cádiz")
        assertEquals(ConnectorId.PESCAIA, routed?.action?.connectorId)
        assertEquals("search_public", routed?.action?.operation)
        assertEquals("mareas de Cádiz", routed?.action?.arguments?.get("query"))
    }


    @Test
    fun mapsDestinationBeforeMapsPhraseIsPreserved() {
        val routed = ConnectorVoiceIntentRouter.route("Llévame a Tarifa en Google Maps")
        assertEquals("navigate", routed?.action?.operation)
        assertEquals("Tarifa", routed?.action?.arguments?.get("destination"))
    }

    @Test
    fun mapsRouteAfterMapsPhraseDoesNotLeakRouteWordIntoDestination() {
        val routed = ConnectorVoiceIntentRouter.route("Abre Maps ruta a Cádiz")
        assertEquals("navigate", routed?.action?.operation)
        assertEquals("Cádiz", routed?.action?.arguments?.get("destination"))
    }

    @Test
    fun pescaiaQueryBeforeBrandIsPreserved() {
        val routed = ConnectorVoiceIntentRouter.route("Busca mareas de Tarifa en PescaIA")
        assertEquals("mareas de Tarifa", routed?.action?.arguments?.get("query"))
    }

    @Test
    fun unrelatedTextDoesNotTriggerExternalConnector() {
        assertNull(ConnectorVoiceIntentRouter.route("¿Qué tiempo hace hoy?"))
    }
}
