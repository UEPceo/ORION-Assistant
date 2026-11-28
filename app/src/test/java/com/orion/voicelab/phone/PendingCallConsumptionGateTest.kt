package com.orion.voicelab.phone

import com.orion.voicelab.data.PendingCallRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingCallConsumptionGateTest {
    @Test fun confirmationRequestCanBeConsumedOnlyOnce() {
        var pending: PendingCallRequest? = PendingCallRequest(
            requestId = "req-1",
            contactName = "Laura",
            phoneNumber = "+34600123456",
            awaitingConfirmation = true
        )
        var clearCount = 0

        val first = PendingCallConsumptionGate.consume(pending) {
            pending = null
            clearCount++
        }
        assertSame(first, first)
        assertEquals("req-1", first?.requestId)
        assertNull(pending)

        val second = PendingCallConsumptionGate.consume(pending) {
            clearCount++
        }
        assertNull(second)
        assertEquals(1, clearCount)
    }

    @Test fun mismatchedRequestIdDoesNotConsumePendingAuthorization() {
        var pending: PendingCallRequest? = PendingCallRequest(
            requestId = "req-1",
            contactName = "Laura",
            phoneNumber = "+34600123456",
            awaitingConfirmation = true
        )
        val mismatched = PendingCallConsumptionGate.consume(pending, "req-2") {
            pending = null
        }
        assertNull(mismatched)
        assertEquals("req-1", pending?.requestId)

        val matching = PendingCallConsumptionGate.consume(pending, "req-1") {
            pending = null
        }
        assertEquals("req-1", matching?.requestId)
        assertNull(pending)
    }
}
