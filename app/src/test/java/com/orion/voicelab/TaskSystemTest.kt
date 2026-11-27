package com.orion.voicelab

import android.Manifest
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.MemoryItem
import com.orion.voicelab.data.PendingTaskOperation
import com.orion.voicelab.data.PendingTaskPrompt
import com.orion.voicelab.data.PhoneCallStatus
import com.orion.voicelab.data.TaskFilter
import com.orion.voicelab.data.TaskIntentAction
import com.orion.voicelab.data.TaskPriority
import com.orion.voicelab.data.TaskQueryScope
import com.orion.voicelab.data.TaskStatus
import com.orion.voicelab.memory.ContextPromptBuilder
import com.orion.voicelab.persistence.OrionDatabase
import com.orion.voicelab.phone.PhoneRepository
import com.orion.voicelab.tasks.AndroidTaskReminderScheduler
import com.orion.voicelab.tasks.SpanishDateTimeParser
import com.orion.voicelab.tasks.TaskIntentRouter
import com.orion.voicelab.tasks.TaskReminderReceiver
import com.orion.voicelab.tasks.TaskRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TaskSystemTest {

    private lateinit var app: Application
    private val zoneId = ZoneId.of("Europe/Madrid")
    // Fixed reference: Wednesday, 2026-11-25 10:00 Madrid
    private val fixedNowMs = LocalDateTime.of(2026, 11, 25, 10, 0)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
    private val fixedToday = LocalDate.of(2026, 11, 25)

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `spanish temporal parser handles relative days, weekdays, explicit dates, offsets, and ambiguity`() {
        val hoy = SpanishDateTimeParser.parse("comprar pan hoy", fixedNowMs, zoneId)
        assertEquals(fixedToday.toEpochDay(), hoy.dueDateEpochDay)
        assertEquals("comprar pan", hoy.cleanedRemainderText)

        val manana = SpanishDateTimeParser.parse("comprar comida mañana", fixedNowMs, zoneId)
        assertEquals(fixedToday.plusDays(1).toEpochDay(), manana.dueDateEpochDay)
        assertEquals("comprar comida", manana.cleanedRemainderText)

        val pasado = SpanishDateTimeParser.parse("revisar factura pasado mañana", fixedNowMs, zoneId)
        assertEquals(fixedToday.plusDays(2).toEpochDay(), pasado.dueDateEpochDay)

        // Domingo from Wednesday 2026-11-25 -> 2026-11-29 (+4 days)
        val domingo = SpanishDateTimeParser.parse("llamar a mi madre el domingo", fixedNowMs, zoneId)
        assertEquals(LocalDate.of(2026, 11, 29).toEpochDay(), domingo.dueDateEpochDay)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.ofEpochDay(domingo.dueDateEpochDay!!).dayOfWeek)

        // Lunes from Wednesday 2026-11-25 -> 2026-11-30 (+5 days)
        val lunes = SpanishDateTimeParser.parse("hasta el lunes", fixedNowMs, zoneId)
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), lunes.dueDateEpochDay)

        // Explicit named date
        val explicit = SpanishDateTimeParser.parse("pagar seguro el 15 de diciembre a las 10:30", fixedNowMs, zoneId)
        assertEquals(LocalDate.of(2026, 12, 15).toEpochDay(), explicit.dueDateEpochDay)
        assertEquals(10 * 60 + 30, explicit.dueTimeMinutes)
        assertFalse(explicit.isAmbiguous)

        // Relative offset
        val offset = SpanishDateTimeParser.parse("revisar horno en 30 minutos", fixedNowMs, zoneId)
        assertEquals(fixedNowMs + 30 * 60_000L, offset.reminderAtMs)

        // Temporal ambiguity: "a las 8" without mañana/tarde
        val ambiguousClock = SpanishDateTimeParser.parse("llamar al banco mañana a las 8", fixedNowMs, zoneId)
        assertTrue(ambiguousClock.isAmbiguous)
        assertEquals(8 * 60, ambiguousClock.ambiguityOptionAMinutes)
        assertEquals(20 * 60, ambiguousClock.ambiguityOptionBMinutes)
        assertNotNull(ambiguousClock.ambiguityPrompt)

        // Explicit period removes ambiguity
        val explicitEvening = SpanishDateTimeParser.parse("llamar al banco mañana a las 8 de la tarde", fixedNowMs, zoneId)
        assertFalse(explicitEvening.isAmbiguous)
        assertEquals(20 * 60, explicitEvening.dueTimeMinutes)

        // Vague cue flags ambiguity cleanly
        val vague = SpanishDateTimeParser.parse("sacar la basura en un rato", fixedNowMs, zoneId)
        assertTrue(vague.isAmbiguous)
        assertNotNull(vague.ambiguityPrompt)
    }

    @Test
    fun `deterministic Spanish NLU parses all required assistant commands and disambiguation flows`() {
        val c1 = TaskIntentRouter.route("Recuérdame comprar comida mañana", nowMs = fixedNowMs, zoneId = zoneId)
        assertTrue(c1 is TaskIntentAction.CreateTask)
        c1 as TaskIntentAction.CreateTask
        assertEquals("Comprar comida", c1.title)
        assertEquals(fixedToday.plusDays(1).toEpochDay(), c1.dueDateEpochDay)
        assertNotNull(c1.reminderAtMs)

        val c2 = TaskIntentRouter.route("Apunta que tengo que llamar a mi madre el domingo", nowMs = fixedNowMs, zoneId = zoneId)
        assertTrue(c2 is TaskIntentAction.CreateTask)
        c2 as TaskIntentAction.CreateTask
        assertTrue(c2.isCallFollowUp)
        assertEquals("mi madre", c2.contactQuery)
        assertEquals(LocalDate.of(2026, 11, 29).toEpochDay(), c2.dueDateEpochDay)

        assertEquals(TaskIntentAction.QueryTasks(TaskQueryScope.ALL_PENDING), TaskIntentRouter.route("¿Qué tengo pendiente?"))
        assertEquals(TaskIntentAction.QueryTasks(TaskQueryScope.TODAY), TaskIntentRouter.route("¿Qué tareas tengo para hoy?"))
        assertEquals(TaskIntentAction.QueryTasks(TaskQueryScope.OVERDUE), TaskIntentRouter.route("¿Qué tengo atrasado?"))

        val comp = TaskIntentRouter.route("Marca como terminado lo del banco")
        assertTrue(comp is TaskIntentAction.CompleteTask)
        assertEquals("banco", (comp as TaskIntentAction.CompleteTask).query)

        val post = TaskIntentRouter.route("Pospón esa tarea hasta el lunes", nowMs = fixedNowMs, zoneId = zoneId)
        assertTrue(post is TaskIntentAction.PostponeTask)
        post as TaskIntentAction.PostponeTask
        assertTrue(post.refersToLastTask)
        assertEquals(LocalDate.of(2026, 11, 30).toEpochDay(), post.newDueDateEpochDay)

        val canc = TaskIntentRouter.route("Cancela el recordatorio de comprar comida")
        assertTrue(canc is TaskIntentAction.CancelTask)
        assertEquals("comprar comida", (canc as TaskIntentAction.CancelTask).query)

        // Urgent task without title asks for missing title first
        val missingTitle = TaskIntentRouter.route("Añade una tarea urgente para esta tarde", nowMs = fixedNowMs, zoneId = zoneId)
        assertTrue(missingTitle is TaskIntentAction.CreateTask)
        val missingPrompt = (missingTitle as TaskIntentAction.CreateTask).missingTitlePrompt
        assertNotNull(missingPrompt)
        assertEquals(TaskPriority.URGENT, missingPrompt!!.priority)
        assertEquals(17 * 60, missingPrompt.dueTimeMinutes)

        val suppliedTitle = TaskIntentRouter.route("Revisar contrato", pendingPrompt = missingPrompt)
        assertEquals(TaskIntentAction.ProvideMissingTitle("Revisar contrato"), suppliedTitle)

        // Post-call follow-up
        val postCall = TaskIntentRouter.route(
            "Cuando termine la llamada, recuérdame enviarle el presupuesto",
            nowMs = fixedNowMs,
            zoneId = zoneId
        )
        assertTrue(postCall is TaskIntentAction.CreateTask)
        assertTrue((postCall as TaskIntentAction.CreateTask).linkLatestOrActiveCall)

        // Callback follow-up
        val callback = TaskIntentRouter.route("Tengo que devolver una llamada a María")
        assertTrue(callback is TaskIntentAction.CreateTask)
        callback as TaskIntentAction.CreateTask
        assertTrue(callback.isCallFollowUp)
        assertEquals("María", callback.contactQuery)
        assertEquals(TaskPriority.HIGH, callback.priority)

        // Multi-task disambiguation
        val disambPrompt = PendingTaskPrompt.DisambiguateTasks(
            actionLabel = "Completar tarea",
            operation = PendingTaskOperation.COMPLETE,
            candidates = emptyList()
        )
        assertEquals(
            TaskIntentAction.SelectTaskCandidateByTitle("hipoteca"),
            TaskIntentRouter.route("la de la hipoteca", pendingPrompt = disambPrompt)
        )
    }

    @Test
    fun `task repository supports full CRUD, overdue filtering, status transitions, and call linking`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, OrionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            var now = fixedNowMs
            val repo = TaskRepository(db.taskDao(), clockMs = { now }, zoneId = zoneId)
            val phoneRepo = PhoneRepository(db.phoneDao())

            val callRec = phoneRepo.createCallRecord(
                contactName = "Juan López",
                phoneNumber = "+34600112233",
                reason = "Presupuesto Q4",
                status = PhoneCallStatus.COMPLETED,
                durationSeconds = 95L
            )

            val followUp = repo.createFollowUpForCall(
                callRecord = callRec,
                title = "Enviar presupuesto a Juan",
                dueDateEpochDay = fixedToday.toEpochDay(),
                dueTimeMinutes = 18 * 60,
                reminderAtMs = fixedNowMs + 8 * 3_600_000L
            )
            assertEquals(callRec.id, followUp.linkedCallRecordId)
            assertEquals("Juan López", followUp.contactName)
            assertEquals("+34600112233", followUp.contactPhone)

            // Overdue task from yesterday
            val overdue = repo.createTask(
                title = "Llamar al banco",
                dueDateEpochDay = fixedToday.minusDays(1).toEpochDay(),
                dueTimeMinutes = 11 * 60,
                reminderAtMs = fixedNowMs - 20 * 3_600_000L,
                priority = TaskPriority.URGENT
            )
            assertTrue(overdue.isOverdue(now, zoneId))

            val all = repo.getAllTasks()
            assertEquals(1, repo.filterTasks(all, TaskFilter.OVERDUE).size)
            assertEquals(1, repo.filterTasks(all, TaskFilter.TODAY).size)
            assertEquals(2, repo.filterTasks(all, TaskFilter.PENDING).size)

            // Update task
            val edited = repo.updateTask(
                id = overdue.id,
                title = "Llamar al banco central",
                description = "Revisar transferencia",
                dueDateEpochDay = fixedToday.plusDays(2).toEpochDay(),
                dueTimeMinutes = 10 * 60,
                reminderAtMs = fixedNowMs + 48 * 3_600_000L,
                priority = TaskPriority.HIGH,
                category = "Finanzas"
            )
            assertNotNull(edited)
            assertFalse(edited!!.isOverdue(now, zoneId))
            assertEquals("Llamar al banco central", edited.title)

            // Postpone -> complete -> reopen -> cancel -> delete
            val postponed = repo.postponeTask(edited.id, fixedToday.plusDays(5).toEpochDay(), 9 * 60, null)!!
            assertEquals(fixedToday.plusDays(5).toEpochDay(), postponed.dueDateEpochDay)

            val completed = repo.completeTask(postponed.id)!!
            assertEquals(TaskStatus.COMPLETED, completed.status)
            assertFalse(completed.reminderActive)
            assertFalse(completed.isOverdue(now + 100 * 3_600_000L, zoneId))

            val reopened = repo.reopenTask(completed.id)!!
            assertEquals(TaskStatus.PENDING, reopened.status)

            val cancelled = repo.cancelTask(reopened.id)!!
            assertEquals(TaskStatus.CANCELLED, cancelled.status)
            assertFalse(cancelled.reminderActive)

            repo.deleteFinishedTasks()
            assertEquals(1, repo.getAllTasks().size)

            repo.deleteAllTasks()
            assertTrue(repo.getAllTasks().isEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `reminder scheduler prevents duplicate alarms, cancels on completion, and reconciles on boot`() = runBlocking {
        val shadowApp = shadowOf(app)
        shadowApp.grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val alarmManager = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarm = shadowOf(alarmManager)
        val nm = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = shadowOf(nm)

        val db = Room.databaseBuilder(app, OrionDatabase::class.java, "orion_test_alarms.db")
            .allowMainThreadQueries()
            .build()
        try {
            val now = System.currentTimeMillis()
            val scheduler = AndroidTaskReminderScheduler(app, clockMs = { now })
            val repo = TaskRepository(db.taskDao(), scheduler, clockMs = { now })

            // 1. Schedule future reminder -> single alarm
            val t1 = repo.createTask(
                title = "Tomar medicación",
                reminderAtMs = now + 3_600_000L,
                reminderActive = true
            )
            assertEquals(1, shadowAlarm.scheduledAlarms.size)

            // 2. Reschedule same task -> still only 1 alarm (no duplicates)
            repo.postponeTask(t1.id, null, null, now + 7_200_000L)
            assertEquals(1, shadowAlarm.scheduledAlarms.size)
            assertEquals(now + 7_200_000L, shadowAlarm.nextScheduledAlarm.triggerAtTime)

            // 3. Complete task -> alarm removed & notification cleared
            AndroidTaskReminderScheduler.showReminderNotification(app, t1)
            assertEquals(1, shadowNm.allNotifications.size)

            repo.completeTask(t1.id)
            assertEquals(0, shadowAlarm.scheduledAlarms.size)
            assertEquals(0, shadowNm.allNotifications.size)

            // 4. Create pending future reminder and pending overdue reminder, simulate reboot
            val futureTask = repo.createTask(
                title = "Reunión mañana",
                reminderAtMs = now + 10_000_000L,
                reminderActive = true
            )
            val overdueTask = repo.createTask(
                title = "Factura vencida",
                dueDateEpochDay = LocalDate.now().minusDays(1).toEpochDay(),
                reminderAtMs = now - 60_000L,
                reminderActive = true
            )

            // Clear Robolectric alarm & notification shadows to simulate fresh boot state
            shadowAlarm.scheduledAlarms.clear()
            shadowNm.cancelAll()

            TaskReminderReceiver.handleIntentSuspend(
                context = app,
                intent = Intent(Intent.ACTION_BOOT_COMPLETED),
                database = db,
                scheduler = AndroidTaskReminderScheduler(app, clockMs = { now })
            )

            // Only future pending task gets AlarmManager schedule; overdue surfaces notification; completed stays off
            assertEquals(1, shadowAlarm.scheduledAlarms.size)
            assertEquals(futureTask.reminderAtMs, shadowAlarm.nextScheduledAlarm.triggerAtTime)
            assertEquals(1, shadowNm.allNotifications.size)

            // 5. Snooze via receiver action reschedules future alarm and clears notification
            TaskReminderReceiver.handleIntentSuspend(
                context = app,
                intent = Intent(TaskReminderReceiver.ACTION_SNOOZE_TASK).apply {
                    putExtra(TaskReminderReceiver.EXTRA_TASK_ID, overdueTask.id)
                },
                database = db,
                scheduler = AndroidTaskReminderScheduler(app, clockMs = { now })
            )
            val snoozed = repo.getTaskById(overdueTask.id)!!
            assertTrue(snoozed.reminderActive)
            assertTrue(snoozed.reminderAtMs!! > now)
            assertEquals(0, shadowNm.allNotifications.size)
        } finally {
            db.close()
            app.deleteDatabase("orion_test_alarms.db")
        }
    }

    @Test
    fun `room v3 to v4 migration and reopen preserve tasks, links, and work with memory disabled offline`() = runBlocking {
        val dbName = "orion_test_v3_to_v4_reopen.db"
        app.deleteDatabase(dbName)
        val dbFile = app.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sq ->
            sq.execSQL("CREATE TABLE IF NOT EXISTS conversations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL, summaryText TEXT NOT NULL)")
            sq.execSQL("CREATE TABLE IF NOT EXISTS conversation_messages (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, conversationId INTEGER NOT NULL, role TEXT NOT NULL, text TEXT NOT NULL, createdAtMs INTEGER NOT NULL, audioMs INTEGER NOT NULL, decodeMs INTEGER NOT NULL, llmFirstTokenMs INTEGER NOT NULL, llmTotalMs INTEGER NOT NULL, llmTokensGenerated INTEGER NOT NULL, llmTokensPerSec REAL NOT NULL, ttsSynthMs INTEGER NOT NULL, synthesizedAudioMs INTEGER NOT NULL, endToSpeechStartMs INTEGER NOT NULL, responseDurationMs INTEGER NOT NULL, FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            sq.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_messages_conversationId ON conversation_messages(conversationId)")
            sq.execSQL("CREATE TABLE IF NOT EXISTS memory_items (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, content TEXT NOT NULL, importance INTEGER NOT NULL, sourceConversationId INTEGER, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
            sq.execSQL("CREATE TABLE IF NOT EXISTS assistant_identity (id INTEGER PRIMARY KEY NOT NULL, assistantName TEXT NOT NULL, personality TEXT NOT NULL, tone TEXT NOT NULL, formality TEXT NOT NULL, basePrompt TEXT NOT NULL, memoryEnabled INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
            sq.execSQL("CREATE TABLE IF NOT EXISTS coworker_contacts (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, roleTitle TEXT NOT NULL, specialtyPrompt TEXT NOT NULL, voiceId TEXT NOT NULL, createdAtMs INTEGER NOT NULL, updatedAtMs INTEGER NOT NULL)")
            sq.execSQL("CREATE TABLE IF NOT EXISTS favorite_phone_contacts (contactKey TEXT PRIMARY KEY NOT NULL, displayName TEXT NOT NULL, phoneNumber TEXT NOT NULL, label TEXT NOT NULL, createdAtMs INTEGER NOT NULL)")
            sq.execSQL("CREATE TABLE IF NOT EXISTS phone_call_records (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, contactName TEXT NOT NULL, phoneNumber TEXT NOT NULL, reason TEXT NOT NULL, status TEXT NOT NULL, createdAtMs INTEGER NOT NULL, startedAtMs INTEGER, endedAtMs INTEGER, durationSeconds INTEGER NOT NULL, scheduledNote TEXT NOT NULL, userNotes TEXT NOT NULL, outcome TEXT NOT NULL, followUpPending INTEGER NOT NULL)")
            sq.execSQL("CREATE INDEX IF NOT EXISTS index_phone_call_records_createdAtMs ON phone_call_records(createdAtMs)")
            sq.execSQL("INSERT INTO phone_call_records (id, contactName, phoneNumber, reason, status, createdAtMs, durationSeconds, scheduledNote, userNotes, outcome, followUpPending) VALUES (7, 'Sofía', '+34699887766', 'Contrato', 'COMPLETED', 5000, 42, '', '', '', 1)")
            sq.version = 3
        }

        // First open: migrate v3 -> v4 and save a linked task
        val db1 = Room.databaseBuilder(app, OrionDatabase::class.java, dbName)
            .addMigrations(OrionDatabase.MIGRATION_1_2, OrionDatabase.MIGRATION_2_3, OrionDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val savedId: Long
        try {
            val repo1 = TaskRepository(db1.taskDao(), clockMs = { fixedNowMs }, zoneId = zoneId)
            val created = repo1.createTask(
                title = "Enviar contrato a Sofía",
                dueDateEpochDay = fixedToday.plusDays(1).toEpochDay(),
                dueTimeMinutes = 11 * 60,
                reminderAtMs = fixedNowMs + 25 * 3_600_000L,
                priority = TaskPriority.HIGH,
                contactName = "Sofía",
                contactPhone = "+34699887766",
                linkedCallRecordId = 7L
            )
            savedId = created.id
        } finally {
            db1.close()
        }

        // Second open (simulating app restart): tasks and call link survive cleanly
        val db2 = Room.databaseBuilder(app, OrionDatabase::class.java, dbName)
            .addMigrations(OrionDatabase.MIGRATION_1_2, OrionDatabase.MIGRATION_2_3, OrionDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val repo2 = TaskRepository(db2.taskDao(), clockMs = { fixedNowMs }, zoneId = zoneId)
            val reloaded = repo2.getTaskById(savedId)
            assertNotNull(reloaded)
            assertEquals("Enviar contrato a Sofía", reloaded!!.title)
            assertEquals(7L, reloaded.linkedCallRecordId)

            // Disabling personal memory does NOT hide tasks from ContextPromptBuilder
            val ctx = ContextPromptBuilder.buildTurnContext(
                identity = AssistantIdentity(memoryEnabled = false),
                allMemories = listOf(MemoryItem(1L, MemoryCategory.IDENTITY, "Secreto personal", 5, null, 1L, 1L)),
                conversationSummary = "",
                recentHistory = emptyList(),
                currentQuery = "¿Qué tengo con Sofía?",
                activeTasks = repo2.getAllTasks()
            )
            assertTrue(ctx.usedMemories.isEmpty())
            assertFalse(ctx.systemPrompt.contains("Secreto personal"))
            assertTrue(ctx.systemPrompt.contains("Enviar contrato a Sofía"))
        } finally {
            db2.close()
            app.deleteDatabase(dbName)
        }
    }
}
