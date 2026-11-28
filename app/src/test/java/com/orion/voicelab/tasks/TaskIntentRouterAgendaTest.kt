package com.orion.voicelab.tasks

import com.orion.voicelab.data.TaskIntentAction
import com.orion.voicelab.data.TaskQueryScope
import org.junit.Assert.*
import org.junit.Test

class TaskIntentRouterAgendaTest {
    @Test fun recognizesCalendarEventCreationAsLocalTask() {
        val action=TaskIntentRouter.route("Añade a mi agenda llamar al dentista mañana")
        assertTrue(action is TaskIntentAction.CreateTask)
        val task=action as TaskIntentAction.CreateTask
        assertTrue(task.title.contains("dentista",ignoreCase=true))
        assertNotNull(task.dueDateEpochDay)
        assertNotNull(task.reminderAtMs)
    }

    @Test fun agendaTodayQueryUsesSpecificDateScope() {
        val action=TaskIntentRouter.route("¿Qué tengo en mi agenda hoy?")
        assertTrue(action is TaskIntentAction.QueryTasks)
        assertEquals(TaskQueryScope.SPECIFIC_DATE,(action as TaskIntentAction.QueryTasks).scope)
        assertNotNull(action.specificDateEpochDay)
    }
}
