package com.orion.voicelab.phone

import com.orion.voicelab.data.PendingCallRequest

/**
 * Consumes a call-confirmation request at most once. The caller serializes access with its
 * state lock and clears the UI snapshot in the supplied callback.
 */
internal object PendingCallConsumptionGate {
    fun consume(
        current: PendingCallRequest?,
        expectedRequestId: String? = null,
        clear: () -> Unit
    ): PendingCallRequest? {
        if (current == null) return null
        if (expectedRequestId != null && current.requestId != expectedRequestId) return null
        clear()
        return current
    }
}
