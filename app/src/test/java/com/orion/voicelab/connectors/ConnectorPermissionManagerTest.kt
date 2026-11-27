package com.orion.voicelab.connectors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectorPermissionManagerTest {
    @Test
    fun confirmationIsBoundToOneActionAndConsumedOnce() {
        var now = 1_000L
        val manager = ConnectorPermissionManager(nowMs = { now })
        val action = ConnectorAction(
            ConnectorId.GMAIL,
            "send_email",
            mapOf("to" to "person@example.com", "subject" to "Informe"),
            ConnectorRisk.EXTERNAL_SIDE_EFFECT
        )
        manager.request(action)

        assertNull(manager.confirm("no"))
        assertEquals(action, manager.confirm("sí"))
        assertNull(manager.confirm("sí"))
    }

    @Test
    fun confirmationExpiresAfterSixtySeconds() {
        var now = 1_000L
        val manager = ConnectorPermissionManager(nowMs = { now })
        val action = ConnectorAction(ConnectorId.GITHUB, "create_issue", risk = ConnectorRisk.EXTERNAL_SIDE_EFFECT)
        manager.request(action)
        now += 60_001L
        assertNull(manager.pendingAction())
        assertNull(manager.confirm("sí"))
    }

    @Test
    fun cancellationClearsPendingAction() {
        val manager = ConnectorPermissionManager()
        manager.request(ConnectorAction(ConnectorId.GITHUB, "delete_branch", risk = ConnectorRisk.DESTRUCTIVE))
        assertTrue(manager.cancel())
        assertNull(manager.pendingAction())
    }
}
