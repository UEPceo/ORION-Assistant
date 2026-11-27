package com.orion.voicelab.connectors

enum class ConnectorAuthorizationDecision {
    ALLOW,
    REQUIRE_CONFIRMATION,
    DENY
}

/**
 * Central safety gate. Provider adapters must not bypass this policy.
 * External side effects and destructive operations always require explicit confirmation.
 */
object ConnectorActionGate {
    fun evaluate(
        action: ConnectorAction,
        userRequestedExactAction: Boolean,
        connectorConnected: Boolean
    ): ConnectorAuthorizationDecision {
        if (!connectorConnected) return ConnectorAuthorizationDecision.DENY

        return when (action.risk) {
            ConnectorRisk.READ_ONLY ->
                if (userRequestedExactAction) ConnectorAuthorizationDecision.ALLOW
                else ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION

            ConnectorRisk.REVERSIBLE_WRITE,
            ConnectorRisk.EXTERNAL_SIDE_EFFECT,
            ConnectorRisk.DESTRUCTIVE ->
                ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION
        }
    }
}
