package com.orion.voicelab.connectors

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorDispatcherTest {
    @Test
    fun exactReadOnlyActionRunsWhenConnected() = runBlocking {
        val connector = FakeConnector(ConnectorConnectionState.CONNECTED)
        val dispatcher = ConnectorDispatcher(ConnectorRegistry(listOf(connector)))

        val result = dispatcher.dispatch(
            ConnectorAction(ConnectorId.GITHUB, "list_repositories"),
            userRequestedExactAction = true
        )

        assertTrue(result is ConnectorResult.Success)
        assertEquals(1, connector.executions)
    }

    @Test
    fun writeRequiresConfirmationAndDoesNotExecuteBeforeIt() = runBlocking {
        val connector = FakeConnector(ConnectorConnectionState.CONNECTED)
        val dispatcher = ConnectorDispatcher(ConnectorRegistry(listOf(connector)))
        val action = ConnectorAction(
            connectorId = ConnectorId.GITHUB,
            operation = "create_issue",
            arguments = mapOf("repository" to "UEPceo/ORION-Assistant", "title" to "Bug", "access_token" to "never-show"),
            risk = ConnectorRisk.EXTERNAL_SIDE_EFFECT
        )

        val pending = dispatcher.dispatch(action, userRequestedExactAction = true)
        assertTrue(pending is ConnectorResult.ConfirmationRequired)
        val prompt = (pending as ConnectorResult.ConfirmationRequired).prompt
        assertTrue(prompt.contains("UEPceo/ORION-Assistant"))
        assertTrue(prompt.contains("Bug"))
        assertTrue(!prompt.contains("never-show"))
        assertEquals(0, connector.executions)

        val confirmed = dispatcher.dispatch(
            action,
            userRequestedExactAction = true,
            userConfirmed = true
        )
        assertTrue(confirmed is ConnectorResult.Success)
        assertEquals(1, connector.executions)
    }

    @Test
    fun disconnectedConnectorNeverExecutesEvenWhenConfirmed() = runBlocking {
        val connector = FakeConnector(ConnectorConnectionState.NOT_CONFIGURED)
        val dispatcher = ConnectorDispatcher(ConnectorRegistry(listOf(connector)))

        val result = dispatcher.dispatch(
            ConnectorAction(
                connectorId = ConnectorId.GITHUB,
                operation = "create_issue",
                risk = ConnectorRisk.EXTERNAL_SIDE_EFFECT
            ),
            userRequestedExactAction = true,
            userConfirmed = true
        )

        assertTrue(result is ConnectorResult.Failure)
        assertEquals(0, connector.executions)
    }

    private class FakeConnector(
        private val state: ConnectorConnectionState
    ) : OrionConnector {
        var executions = 0
        override val descriptor = ConnectorDescriptor(
            id = ConnectorId.GITHUB,
            displayName = "Fake",
            description = "Test",
            capabilities = setOf(ConnectorCapability.READ, ConnectorCapability.CREATE),
            isImplemented = true
        )

        override suspend fun connectionState(): ConnectorConnectionState = state

        override suspend fun execute(action: ConnectorAction): ConnectorResult {
            executions++
            return ConnectorResult.Success("ok")
        }
    }
}
