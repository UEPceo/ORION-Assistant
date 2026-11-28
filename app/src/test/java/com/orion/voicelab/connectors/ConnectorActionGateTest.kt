package com.orion.voicelab.connectors

import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectorActionGateTest {
    @Test
    fun disconnectedProviderIsAlwaysDenied() {
        val action = ConnectorAction(
            connectorId = ConnectorId.GMAIL,
            operation = "search_messages",
            risk = ConnectorRisk.READ_ONLY
        )

        assertEquals(
            ConnectorAuthorizationDecision.DENY,
            ConnectorActionGate.evaluate(action, userRequestedExactAction = true, connectorConnected = false)
        )
    }

    @Test
    fun explicitReadRequestIsAllowedWhenConnected() {
        val action = ConnectorAction(
            connectorId = ConnectorId.GITHUB,
            operation = "list_repositories",
            risk = ConnectorRisk.READ_ONLY
        )

        assertEquals(
            ConnectorAuthorizationDecision.ALLOW,
            ConnectorActionGate.evaluate(action, userRequestedExactAction = true, connectorConnected = true)
        )
    }

    @Test
    fun unsolicitedReadRequiresConfirmation() {
        val action = ConnectorAction(
            connectorId = ConnectorId.GMAIL,
            operation = "search_messages",
            risk = ConnectorRisk.READ_ONLY
        )

        assertEquals(
            ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION,
            ConnectorActionGate.evaluate(action, userRequestedExactAction = false, connectorConnected = true)
        )
    }

    @Test
    fun newGitHubReadOperationsRemainReadOnly() {
        listOf("list_commits", "list_workflow_runs", "list_files", "get_issue", "get_message").forEach { operation ->
            assertEquals(
                ConnectorAuthorizationDecision.ALLOW,
                ConnectorActionGate.evaluate(
                    ConnectorAction(ConnectorId.GITHUB, operation),
                    userRequestedExactAction = true,
                    connectorConnected = true
                )
            )
        }
    }

    @Test
    fun sendCannotBeAllowedWhenIncorrectlyLabelledReadOnly() {
        val action = ConnectorAction(
            connectorId = ConnectorId.GMAIL,
            operation = "send_email",
            risk = ConnectorRisk.READ_ONLY
        )

        assertEquals(
            ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION,
            ConnectorActionGate.evaluate(action, userRequestedExactAction = true, connectorConnected = true)
        )
    }

    @Test
    fun unknownListOrGetPrefixedOperationsCannotBypassReadOnlyGate() {
        listOf("list_and_delete", "get_then_delete", "search_and_send").forEach { operation ->
            assertEquals(
                ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION,
                ConnectorActionGate.evaluate(
                    ConnectorAction(ConnectorId.GITHUB, operation, risk = ConnectorRisk.READ_ONLY),
                    userRequestedExactAction = true,
                    connectorConnected = true
                )
            )
        }
    }


    @Test
    fun unimplementedGithubOperationsAreNotWhitelisted() {
        listOf("get_repository", "get_file", "list_pull_requests", "get_pull_request").forEach { operation ->
            assertEquals(
                ConnectorAuthorizationDecision.DENY,
                ConnectorActionGate.evaluate(
                    ConnectorAction(ConnectorId.GITHUB, operation),
                    userRequestedExactAction = true,
                    connectorConnected = true
                )
            )
        }
    }

    @Test
    fun externalSideEffectsAlwaysRequireConfirmation() {
        val action = ConnectorAction(
            connectorId = ConnectorId.GMAIL,
            operation = "send_message",
            risk = ConnectorRisk.EXTERNAL_SIDE_EFFECT
        )

        assertEquals(
            ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION,
            ConnectorActionGate.evaluate(action, userRequestedExactAction = true, connectorConnected = true)
        )
    }

    @Test
    fun genericPlaceholderOperationsAreNotWhitelisted() {
        listOf("search", "read", "list", "get", "fetch", "inspect", "profile", "status").forEach { operation ->
            assertEquals(
                ConnectorAuthorizationDecision.DENY,
                ConnectorActionGate.evaluate(
                    ConnectorAction(ConnectorId.GITHUB, operation),
                    userRequestedExactAction = true,
                    connectorConnected = true
                )
            )
        }
    }

}
