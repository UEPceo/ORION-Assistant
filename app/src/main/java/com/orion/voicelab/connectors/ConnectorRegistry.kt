package com.orion.voicelab.connectors

/**
 * In-memory registry for concrete provider adapters.
 * Registration is explicit: catalog entries alone do not imply a live integration.
 */
class ConnectorRegistry(connectors: List<OrionConnector> = emptyList()) {
    private val byId: Map<ConnectorId, OrionConnector> =
        connectors.associateBy { it.descriptor.id }

    fun get(id: ConnectorId): OrionConnector? = byId[id]

    fun all(): List<OrionConnector> = byId.values.toList()

    fun isRegistered(id: ConnectorId): Boolean = byId.containsKey(id)
}
