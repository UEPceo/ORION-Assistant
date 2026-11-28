package com.orion.voicelab.brain

import org.junit.Assert.*
import org.junit.Test

class PlanProposalParserLocalActionsTest {
    @Test fun parsesOfflineTaskAndReminderWithContextualApproval() {
        val raw = """{"goal":"crear tarea y recordatorio","steps":[{"id":"task","kind":"LOCAL_CREATE_TASK","title":"Guardar tarea","risk":"MEDIUM","requiresApproval":true,"connectorArguments":{"title":"Preparar informe"}},{"id":"reminder","kind":"LOCAL_CREATE_REMINDER","title":"Avisar","risk":"MEDIUM","requiresApproval":true,"dependsOn":["task"],"connectorArguments":{"title":"Recordar informe","reminderAtMs":"4102444800000","notes":"Creada desde ${'$'}{task.output}"}}]}"""
        val result = PlanProposalParser.parse(raw, "parser-test")
        assertTrue(result.toString(), result is PlanProposalParseResult.Valid)
        val plan = (result as PlanProposalParseResult.Valid).plan
        assertEquals(2, plan.steps.size)
        assertTrue(PlanValidator.validate(plan).approvalRequiredStepIds.containsAll(setOf("task", "reminder")))
    }

    @Test fun rejectsLocalReminderWithoutFutureTimestampAndUnsafeFields() {
        val invalidTime = """{"goal":"recordar","steps":[{"id":"r","kind":"LOCAL_CREATE_REMINDER","title":"Reminder","risk":"MEDIUM","connectorArguments":{"title":"Avisar","reminderAtMs":"1"}}]}"""
        assertTrue(PlanProposalParser.parse(invalidTime, "invalid-time") is PlanProposalParseResult.Invalid)
        val unsafe = """{"goal":"nota","steps":[{"id":"n","kind":"LOCAL_CREATE_NOTE","title":"Nota","risk":"MEDIUM","connectorArguments":{"content":"texto","access_token":"secret"}}]}"""
        assertTrue(PlanProposalParser.parse(unsafe, "unsafe") is PlanProposalParseResult.Invalid)
    }
}
