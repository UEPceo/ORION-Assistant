package com.orion.voicelab

import android.Manifest
import android.app.Application
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.TelephonyManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.orion.voicelab.data.PhoneCallStatus
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.PhoneContactSource
import com.orion.voicelab.data.SystemTelephonyState
import com.orion.voicelab.persistence.OrionDatabase
import com.orion.voicelab.phone.CallDispatchResult
import com.orion.voicelab.phone.PhoneCallManager
import com.orion.voicelab.phone.PhoneRepository
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
import org.robolectric.fakes.RoboCursor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TelephonyAndRoomRobolectricTest {

    private lateinit var app: Application

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `room migrates v1 schema to v3 preserving conversations, messages, memories, and identity`() = runBlocking {
        val dbName = "orion_test_v1_to_v3.db"
        app.deleteDatabase(dbName)
        val dbFile = app.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { sq ->
            sq.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL,
                    updatedAtMs INTEGER NOT NULL,
                    summaryText TEXT NOT NULL
                )
                """.trimIndent()
            )
            sq.execSQL(
                """
                CREATE TABLE IF NOT EXISTS conversation_messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    conversationId INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    text TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL,
                    audioMs INTEGER NOT NULL,
                    decodeMs INTEGER NOT NULL,
                    llmFirstTokenMs INTEGER NOT NULL,
                    llmTotalMs INTEGER NOT NULL,
                    llmTokensGenerated INTEGER NOT NULL,
                    llmTokensPerSec REAL NOT NULL,
                    ttsSynthMs INTEGER NOT NULL,
                    synthesizedAudioMs INTEGER NOT NULL,
                    endToSpeechStartMs INTEGER NOT NULL,
                    responseDurationMs INTEGER NOT NULL,
                    FOREIGN KEY(conversationId) REFERENCES conversations(id) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            sq.execSQL("CREATE INDEX IF NOT EXISTS index_conversation_messages_conversationId ON conversation_messages(conversationId)")
            sq.execSQL(
                """
                CREATE TABLE IF NOT EXISTS memory_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    category TEXT NOT NULL,
                    content TEXT NOT NULL,
                    importance INTEGER NOT NULL,
                    sourceConversationId INTEGER,
                    createdAtMs INTEGER NOT NULL,
                    updatedAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sq.execSQL(
                """
                CREATE TABLE IF NOT EXISTS assistant_identity (
                    id INTEGER PRIMARY KEY NOT NULL,
                    assistantName TEXT NOT NULL,
                    personality TEXT NOT NULL,
                    tone TEXT NOT NULL,
                    formality TEXT NOT NULL,
                    basePrompt TEXT NOT NULL,
                    memoryEnabled INTEGER NOT NULL,
                    updatedAtMs INTEGER NOT NULL
                )
                """.trimIndent()
            )
            sq.execSQL("INSERT INTO conversations (id, title, createdAtMs, updatedAtMs, summaryText) VALUES (1, 'Charla guardada', 1000, 2000, 'Resumen v1')")
            sq.execSQL(
                """
                INSERT INTO conversation_messages
                (id, conversationId, role, text, createdAtMs, audioMs, decodeMs, llmFirstTokenMs, llmTotalMs, llmTokensGenerated, llmTokensPerSec, ttsSynthMs, synthesizedAudioMs, endToSpeechStartMs, responseDurationMs)
                VALUES (1, 1, 'USER', 'Hola desde v1', 1500, 300, 20, -1, -1, 0, 0.0, -1, 0, -1, 0)
                """.trimIndent()
            )
            sq.execSQL("INSERT INTO memory_items (id, category, content, importance, sourceConversationId, createdAtMs, updatedAtMs) VALUES (1, 'IDENTITY', 'Se llama Carlos', 5, 1, 1000, 1000)")
            sq.execSQL("INSERT INTO assistant_identity (id, assistantName, personality, tone, formality, basePrompt, memoryEnabled, updatedAtMs) VALUES (1, 'ORION Prime', 'Claro', 'Directo', 'CERCANO', 'Prompt v1', 1, 1000)")
            sq.version = 1
        }

        val migratedDb = Room.databaseBuilder(app, OrionDatabase::class.java, dbName)
            .addMigrations(OrionDatabase.MIGRATION_1_2, OrionDatabase.MIGRATION_2_3, OrionDatabase.MIGRATION_3_4, OrionDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val conv = migratedDb.conversationDao().getConversationById(1L)
            assertNotNull(conv)
            assertEquals("Charla guardada", conv?.title)
            val msgs = migratedDb.conversationDao().getMessagesForConversation(1L)
            assertEquals(1, msgs.size)
            assertEquals("Hola desde v1", msgs.single().text)
            assertEquals("Se llama Carlos", migratedDb.memoryDao().getAllMemories().single().content)
            assertEquals("ORION Prime", migratedDb.identityDao().getIdentity()?.assistantName)

            val phoneRepo = PhoneRepository(migratedDb.phoneDao())
            val rec = phoneRepo.createCallRecord(
                contactName = "María",
                phoneNumber = "+34600112233",
                reason = "Confirmar entrega",
                status = PhoneCallStatus.PREPARED,
                followUpPending = true
            )
            assertEquals(1, phoneRepo.getAllCallRecords().size)
            assertEquals(rec.id, phoneRepo.getLatestCallRecord()?.id)
        } finally {
            migratedDb.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun `room migrates v2 schema to v3 preserving coworker contacts`() = runBlocking {
        val dbName = "orion_test_v2_to_v3.db"
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
            sq.execSQL("INSERT INTO coworker_contacts (id, name, roleTitle, specialtyPrompt, voiceId, createdAtMs, updatedAtMs) VALUES (1, 'Vega', 'Finanzas', 'Analiza presupuestos', 'sharvard_f', 1000, 2000)")
            sq.version = 2
        }

        val migratedDb = Room.databaseBuilder(app, OrionDatabase::class.java, dbName)
            .addMigrations(OrionDatabase.MIGRATION_1_2, OrionDatabase.MIGRATION_2_3, OrionDatabase.MIGRATION_3_4, OrionDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()
        try {
            val coworkers = migratedDb.contactDao().getAllCoworkers()
            assertEquals(1, coworkers.size)
            assertEquals("Vega", coworkers.single().name)
            assertTrue(migratedDb.phoneDao().getFavorites().isEmpty())
            assertTrue(migratedDb.phoneDao().getAllCallRecords().isEmpty())
        } finally {
            migratedDb.close()
            app.deleteDatabase(dbName)
        }
    }

    @Test
    fun `phone call manager dispatches ACTION_CALL when permitted and falls back to ACTION_DIAL safely`() {
        val manager = PhoneCallManager(app)
        val shadowApp = shadowOf(app)

        // Call-state reconciliation must respect the permission boundary.
        assertEquals(SystemTelephonyState.UNAVAILABLE, manager.currentCallState())
        shadowApp.grantPermissions(Manifest.permission.READ_PHONE_STATE)
        assertEquals(SystemTelephonyState.IDLE, manager.currentCallState())

        // Invalid number rejected safely without starting any Activity
        val invalid = manager.dispatchCall("sin digitos", preferDirectCall = true)
        assertTrue(invalid is CallDispatchResult.Failed)
        assertNull(shadowApp.nextStartedActivity)

        // Without CALL_PHONE permission -> ACTION_DIAL fallback even if preferDirectCall = true
        shadowApp.denyPermissions(Manifest.permission.CALL_PHONE)
        val dialFallback = manager.dispatchCall("+34 600 12 34 56", preferDirectCall = true)
        assertTrue(dialFallback is CallDispatchResult.DialerOpened)
        val dialIntent = shadowApp.nextStartedActivity
        assertNotNull(dialIntent)
        assertEquals(Intent.ACTION_DIAL, dialIntent.action)
        assertEquals("tel:+34600123456", dialIntent.dataString)

        // Grant CALL_PHONE -> ACTION_CALL used when preferDirectCall = true
        shadowApp.grantPermissions(Manifest.permission.CALL_PHONE)
        val directResult = manager.dispatchCall("600-99-88-77", preferDirectCall = true)
        assertTrue(directResult is CallDispatchResult.DirectCallStarted)
        val callIntent = shadowApp.nextStartedActivity
        assertNotNull(callIntent)
        assertEquals(Intent.ACTION_CALL, callIntent.action)
        assertEquals("tel:600998877", callIntent.dataString)

        // Explicit dialer choice keeps ACTION_DIAL even when CALL_PHONE is granted
        val explicitDial = manager.dispatchCall("600998877", preferDirectCall = false)
        assertTrue(explicitDial is CallDispatchResult.DialerOpened)
        assertEquals(Intent.ACTION_DIAL, shadowApp.nextStartedActivity.action)
    }

    @Test
    fun `phone call manager manages READ_PHONE_STATE observation and cleanup cleanly`() {
        val manager = PhoneCallManager(app)
        val shadowApp = shadowOf(app)
        val statesSeen = mutableListOf<SystemTelephonyState>()

        shadowApp.denyPermissions(Manifest.permission.READ_PHONE_STATE)
        manager.startObservingCallState { statesSeen.add(it) }
        assertFalse(manager.isObservingCallState())
        assertEquals(SystemTelephonyState.IDLE, statesSeen.last())

        shadowApp.grantPermissions(Manifest.permission.READ_PHONE_STATE)
        val tm = app.getSystemService(TelephonyManager::class.java)
        val shadowTm = shadowOf(tm)
        manager.startObservingCallState { statesSeen.add(it) }
        assertTrue(manager.isObservingCallState())

        shadowTm.setCallState(TelephonyManager.CALL_STATE_OFFHOOK, "600112233")
        assertTrue(statesSeen.contains(SystemTelephonyState.OFFHOOK))

        manager.stopObservingCallState()
        assertFalse(manager.isObservingCallState())
    }

    @Test
    fun `phone repository queries accented native contacts, formatted numbers, and picked contact URI`() {
        val shadowResolver = shadowOf(app.contentResolver)
        val cols = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        fun registerFreshContactsCursor() {
            // Robolectric's shadow returns the registered cursor instance repeatedly; real
            // ContentResolver queries return a fresh cursor for each call.
            val contactsCursor = RoboCursor().apply {
                setColumnNames(cols.toList())
                setResults(
                    arrayOf(
                        arrayOf(101L, "María López", "+34 600 11 22 33", ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, null, 1),
                        arrayOf(102L, "Ángel Pérez", "611-222-333", ContactsContract.CommonDataKinds.Phone.TYPE_WORK, null, 0)
                    )
                )
            }
            shadowResolver.setCursor(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, contactsCursor)
        }
        registerFreshContactsCursor()

        val pickedUri = Uri.parse("content://com.android.contacts/data/555")
        val pickedCursor = RoboCursor().apply {
            setColumnNames(
                listOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.LABEL
                )
            )
            setResults(
                arrayOf(
                    arrayOf(202L, "Lucía Gómez", "+34 655 44 33 22", ContactsContract.CommonDataKinds.Phone.TYPE_HOME, null)
                )
            )
        }
        shadowResolver.setCursor(pickedUri, pickedCursor)

        val db = Room.inMemoryDatabaseBuilder(app, OrionDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val repo = PhoneRepository(db.phoneDao(), app.contentResolver)

            // Unaccented query matches "María López"
            val mariaHits = repo.queryNativeContacts("Maria")
            assertEquals(1, mariaHits.size)
            assertEquals("María López", mariaHits.single().displayName)
            assertTrue(mariaHits.single().isFavorite)

            // Unformatted digit query matches "+34 600 11 22 33" and "611-222-333"
            registerFreshContactsCursor()
            val digitHits = repo.queryNativeContacts("600112233")
            assertEquals(1, digitHits.size)
            assertEquals("María López", digitHits.single().displayName)

            registerFreshContactsCursor()
            val angelHits = repo.queryNativeContacts("Angel")
            assertEquals("Ángel Pérez", angelHits.single().displayName)

            val picked = repo.resolvePickedContactUri(pickedUri)
            assertNotNull(picked)
            assertEquals("Lucía Gómez", picked?.displayName)
            assertEquals("+34655443322", picked?.normalizedNumber)
            assertEquals(PhoneContactSource.NATIVE_PICKER, picked?.source)
        } finally {
            db.close()
        }
    }


}
