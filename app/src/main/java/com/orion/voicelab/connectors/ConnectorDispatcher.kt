package com.orion.voicelab.connectors

/**
 * Single execution boundary for external actions.
 *
 * The caller must pass whether the user explicitly requested this exact operation and
 * whether a confirmation was obtained for this specific action. Provider adapters must
 * never be called directly from the language model or from free-form generated text.
 */
class ConnectorDispatcher(
    private val registry: ConnectorRegistry
) {
    suspend fun dispatch(
        action: ConnectorAction,
        userRequestedExactAction: Boolean,
        userConfirmed: Boolean = false
    ): ConnectorResult {
        val connector = registry.get(action.connectorId)
            ?: return ConnectorResult.Failure(
                safeMessage = "El conector solicitado no está disponible.",
                retryable = false
            )

        val connected = try {
            connector.connectionState() == ConnectorConnectionState.CONNECTED
        } catch (_: Exception) {
            false
        }

        val decision = ConnectorActionGate.evaluate(
            action = action,
            userRequestedExactAction = userRequestedExactAction,
            connectorConnected = connected
        )

        return when (decision) {
            ConnectorAuthorizationDecision.DENY -> ConnectorResult.Failure(
                safeMessage = "El conector no está conectado o no está autorizado.",
                retryable = true
            )
            ConnectorAuthorizationDecision.REQUIRE_CONFIRMATION -> {
                if (!userConfirmed) {
                    ConnectorResult.ConfirmationRequired(
                        action = action,
                        prompt = confirmationPrompt(action)
                    )
                } else {
                    executeSafely(connector, action)
                }
            }
            ConnectorAuthorizationDecision.ALLOW -> executeSafely(connector, action)
        }
    }

    private fun confirmationPrompt(action: ConnectorAction): String {
        val safeArguments = action.arguments.entries
            .filterNot { (key, _) ->
                key.contains("token", ignoreCase = true) ||
                    key.contains("secret", ignoreCase = true) ||
                    key.contains("password", ignoreCase = true) ||
                    key.contains("authorization", ignoreCase = true)
            }
            .take(5)
            .joinToString("; ") { (key, value) -> "$key=${value.take(160)}" }
            .take(500)
        val details = safeArguments.takeIf { it.isNotBlank() }?.let { " Detalles: $it." }.orEmpty()
        return "Voy a ejecutar ${action.operation} en ${action.connectorId}.$details ¿Confirmas esta acción?"
    }

    private suspend fun executeSafely(
        connector: OrionConnector,
        action: ConnectorAction
    ): ConnectorResult = try {
        connector.execute(action)
    } catch (_: kotlinx.coroutines.CancellationException) {
        throw kotlinx.coroutines.CancellationException("Operación de conector cancelada.")
    } catch (_: Exception) {
        ConnectorResult.Failure(
            safeMessage = "El servicio externo no pudo completar la operación.",
            retryable = true
        )
    }
}
