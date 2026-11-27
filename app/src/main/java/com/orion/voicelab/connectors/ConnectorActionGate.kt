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
    private val readOnlyOperations = setOf(
        "search",
        "read",
        "list",
        "get",
        "fetch",
        "inspect",
        "profile",
        "status",
        "search_messages",
        "list_messages",
        "get_message",
        "list_repositories",
        "get_repository",
        "get_file",
        "list_issues",
        "list_commits",
        "list_workflow_runs",
        "list_files",
        "get_issue",
        "list_pull_requests",
        "get_pull_request"
    )

    fun evaluate(
        action: ConnectorAction,
        userRequestedExactAction: Boolean,
        connectorConnected: Boolean
    ): ConnectorAuthorizationDecision {
        if (!connectorConnected) return ConnectorAuthorizationDecision.DENY

        return when (action.risk) {
            ConnectorRisk.READ_ONLY -> {
                if (!isReadOnlyOperation(action.operation)) {
                    ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION
                } else if (userRequestedExactAction) {
                    ConnectorAuthorizationDecision.ALLOW
                } else {
                    ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION
                }
            }

            ConnectorRisk.REVERSIBLE_WRITE,
            ConnectorRisk.EXTERNAL_SIDE_EFFECT,
            ConnectorRisk.DESTRUCTIVE ->
                ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION
        }
    }

    private fun isReadOnlyOperation(operation: String): Boolean {
        val normalized = operation.trim().lowercase()
        return normalized in readOnlyOperations
    }
}
