package com.orion.voicelab.connectors

/**
 * One-shot, short-lived confirmation state for external side effects.
 * A confirmation is bound to the exact immutable action and cannot authorize a later action.
 */
class ConnectorPermissionManager(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val ttlMs: Long = 60_000L
) {
    data class Pending(val action: ConnectorAction, val expiresAtMs: Long)

    @Volatile
    private var pending: Pending? = null

    @Synchronized
    fun request(action: ConnectorAction) {
        pending = Pending(action = action, expiresAtMs = nowMs() + ttlMs)
    }

    @Synchronized
    fun pendingAction(): ConnectorAction? {
        val current = pending ?: return null
        if (nowMs() > current.expiresAtMs) {
            pending = null
            return null
        }
        return current.action
    }

    @Synchronized
    fun confirm(utterance: String): ConnectorAction? {
        val current = pending ?: return null
        if (nowMs() > current.expiresAtMs) {
            pending = null
            return null
        }
        val normalized = utterance.trim().lowercase()
        val accepted = normalized in setOf("sí", "si", "sí, confirma", "si confirma", "confirmo", "adelante", "hazlo")
        if (!accepted) return null
        pending = null
        return current.action
    }

    @Synchronized
    fun cancel(): Boolean {
        val existed = pending != null
        pending = null
        return existed
    }
}
