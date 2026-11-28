package com.orion.voicelab.brain

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orion.voicelab.data.TaskStatus
import com.orion.voicelab.persistence.OrionDatabase
import com.orion.voicelab.tasks.NoOpTaskReminderScheduler
import com.orion.voicelab.tasks.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalTaskPlanStepHandlerTest {
    private lateinit var database: OrionDatabase
    private lateinit var tasks: TaskRepository
    private lateinit var handler: LocalTaskPlanStepHandler

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, OrionDatabase::class.java)
            .allowMainThreadQueries().build()
        tasks = TaskRepository(database.taskDao(), NoOpTaskReminderScheduler)
        handler = LocalTaskPlanStepHandler(tasks)
    }

    @After fun tearDown() { database.close() }

    @Test fun localTaskPersistsExplicitDueDateAndTime() = runBlocking {
        val day = LocalDate.of(2027, 2, 14).toEpochDay()
        val result = handler.run(
            PlannedStep(
                id = "task", kind = PlanStepKind.LOCAL_CREATE_TASK, title = "Crear tarea",
                rationale = "", risk = PlanRisk.MEDIUM,
                connectorArguments = mapOf(
                    "title" to "Llamar al proveedor",
                    "dueDateEpochDay" to day.toString(),
                    "dueTimeMinutes" to "615"
                )
            ),
            ExecutionBudget()
        )
        assertEquals(StepStatus.SUCCEEDED, result.status)
        val created = tasks.getAllTasks().single()
        assertEquals(day, created.dueDateEpochDay)
        assertEquals(615, created.dueTimeMinutes)
        assertEquals(TaskStatus.PENDING, created.status)
    }

    @Test fun invalidTimeIsRejectedWithoutCreatingTask() = runBlocking {
        val result = handler.run(
            PlannedStep(
                id = "task", kind = PlanStepKind.LOCAL_CREATE_TASK, title = "Crear tarea",
                rationale = "", risk = PlanRisk.MEDIUM,
                connectorArguments = mapOf("title" to "No crear", "dueTimeMinutes" to "1440")
            ),
            ExecutionBudget()
        )
        assertEquals(StepStatus.FAILED, result.status)
        assertTrue(tasks.getAllTasks().isEmpty())
    }

    @Test fun coordinatorRequiresExplicitApprovalBeforeLocalWrite() = runBlocking {
        val plan = OrionPlan(
            id = "approval-local-write", goal = "Guardar una tarea",
            steps = listOf(
                PlannedStep(
                    id = "create", kind = PlanStepKind.LOCAL_CREATE_TASK, title = "Crear tarea",
                    rationale = "Persistir localmente", risk = PlanRisk.MEDIUM,
                    connectorArguments = mapOf("title" to "Tarea autorizada")
                )
            )
        )
        val coordinator = PlanExecutionCoordinator()
        val waiting = coordinator.execute(plan, emptySet(), PlanStepRunner { step, budget ->
            handler.run(step, budget)
        })
        assertEquals(PlanStatus.AWAITING_APPROVAL, waiting.status)
        assertTrue(tasks.getAllTasks().isEmpty())

        val approved = coordinator.execute(plan, setOf("create"), PlanStepRunner { step, budget ->
            handler.run(step, budget)
        })
        assertEquals(PlanStatus.COMPLETED, approved.status)
        assertEquals("Tarea autorizada", tasks.getAllTasks().single().title)
    }

    @Test fun postponingCompletedTaskDoesNotReactivateReminder() = runBlocking {
        val dueDay = LocalDate.of(2027, 3, 10).toEpochDay()
        val created = tasks.createTask(
            title = "Tarea completada",
            reminderAtMs = System.currentTimeMillis() + 3_600_000L,
            reminderActive = true
        )
        tasks.completeTask(created.id)

        val result = tasks.postponeTask(
            id = created.id,
            newDueDateEpochDay = dueDay,
            newDueTimeMinutes = 600,
            newReminderAtMs = System.currentTimeMillis() + 7_200_000L
        )

        assertNotNull(result)
        assertEquals(TaskStatus.COMPLETED, result?.status)
        assertEquals(false, result?.reminderActive)
        assertEquals(null, result?.reminderAtMs)
    }

    @Test fun validatorRejectsMalformedDateAndTimeInsteadOfSilentlyIgnoringThem() {
        val invalidDate = PlannedStep(
            id = "date", kind = PlanStepKind.LOCAL_CREATE_TASK, title = "Tarea",
            rationale = "", risk = PlanRisk.MEDIUM,
            connectorArguments = mapOf("title" to "Tarea", "dueDateEpochDay" to "not-a-day")
        )
        val invalidTime = invalidDate.copy(
            id = "time",
            connectorArguments = mapOf("title" to "Tarea", "dueTimeMinutes" to "1440")
        )
        assertTrue(PlanValidator.validate(OrionPlan("bad-date", "Objetivo", listOf(invalidDate))).errors
            .any { it.contains("fecha de vencimiento") })
        assertTrue(PlanValidator.validate(OrionPlan("bad-time", "Objetivo", listOf(invalidTime))).errors
            .any { it.contains("hora") })
    }
}
