package com.orion.voicelab.connectors

enum class ConnectorAuthorizationDecision {
    ALLOW,
    REQUIRE_CONFIRMATION,
    DENY
}

/**
 * Central safety gate. Provider adapters must not bypass this policy.
 * Operation names are checked as a second line of defense: a caller cannot make a
 * write operation safe merely by labelling its risk READ_ONLY.
 */
object ConnectorActionGate {
    private val writeOperationsByConnector = mapOf(
        ConnectorId.GITHUB to setOf("create_branch", "commit_changes", "create_pull_request")
    )

    private val readOnlyOperationsByConnector = mapOf(
        ConnectorId.GMAIL to setOf("search_messages", "get_message", "read_latest_message"),
        ConnectorId.GITHUB to setOf(
            "list_repositories", "list_issues", "get_issue",
            "list_commits", "list_workflow_runs", "list_files",
            "create_branch", "commit_changes", "create_pull_request"
        ),
        ConnectorId.GOOGLE_MAPS to setOf("search_place", "navigate"),
        ConnectorId.PESCAIA to setOf("search_public")
    )

    fun evaluate(
        action: ConnectorAction,
        userRequestedExactAction: Boolean,
        connectorConnected: Boolean
    ): ConnectorAuthorizationDecision {
        if (!connectorConnected) return ConnectorAuthorizationDecision.DENY

        return when (action.risk) {
            ConnectorRisk.READ_ONLY -> {
                if (!isReadOnlyOperation(action.connectorId, action.operation)) {
                    ConnectorAuthorizationDecision.DENY
                } else if (userRequestedExactAction) {
                    ConnectorAuthorizationDecision.ALLOW
                } else {
                    ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION
                }
            }

            ConnectorRisk.REVERSIBLE_WRITE -> {
                if (action.operation.trim().lowercase() in writeOperationsByConnector[action.connectorId].orEmpty()) {
                    ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION
                } else ConnectorAuthorizationDecision.DENY
            }
            ConnectorRisk.EXTERNAL_SIDE_EFFECT,
            ConnectorRisk.DESTRUCTIVE -> ConnectorAuthorizationDecision.DENY
        }
    }

    private fun isReadOnlyOperation(connectorId: ConnectorId, operation: String): Boolean {
        val normalized = operation.trim().lowercase()
        return normalized in readOnlyOperationsByConnector[connectorId].orEmpty()
    }
}
