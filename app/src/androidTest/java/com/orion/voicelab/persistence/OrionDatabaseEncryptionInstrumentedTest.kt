package com.orion.voicelab.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.KeyStore

class OrionDatabaseEncryptionInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseFile: File
    private lateinit var backupFile: File
    private lateinit var temporaryFile: File

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath(DB_NAME)
        databaseFile.parentFile?.mkdirs()
        backupFile = File(databaseFile.parentFile, DB_NAME + BACKUP_SUFFIX)
        temporaryFile = File(databaseFile.parentFile, DB_NAME + TEMP_SUFFIX)
        cleanState()
    }

    @After fun tearDown() {
        cleanState()
    }

    @Test fun legacyPlaintextDatabaseIsConvertedAndRowsRemainReadable() {
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacy.execSQL("CREATE TABLE sample (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
        legacy.execSQL("INSERT INTO sample(value) VALUES ('preserved-row')")
        legacy.version = 5
        legacy.close()

        val passphrase = OrionDatabaseEncryption.getOrCreatePassphrase(context)
        assertEquals(64, passphrase.size)
        assertTrue(String(passphrase, Charsets.UTF_8).matches(Regex("[0-9a-f]{64}")))
        OrionDatabaseEncryption.encryptLegacyDatabaseIfNeeded(context, passphrase)

        assertTrue(databaseFile.isFile)
        assertFalse(hasPlainSqliteHeader(databaseFile))
        val encrypted = CipherDatabase.openDatabase(
            databaseFile.absolutePath, passphrase, null, CipherDatabase.OPEN_READWRITE, null
        )
        encrypted.use {
            val cipherVersion = it.rawQuery("PRAGMA cipher_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
            val schemaVersion = it.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }
            assertEquals(5, schemaVersion)
            assertNotNull(cipherVersion)
            assertTrue(cipherVersion.isNotBlank())
            val preserved = it.rawQuery("SELECT value FROM sample", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals("preserved-row", preserved)
        }
        assertFalse(backupFile.exists())
        assertFalse(temporaryFile.exists())
    }

    @Test fun interruptedSwapRecoversVerifiedEncryptedTemporaryDatabase() {
        val passphrase = OrionDatabaseEncryption.getOrCreatePassphrase(context)
        val temporary = CipherDatabase.openDatabase(
            temporaryFile.absolutePath,
            passphrase,
            null,
            CipherDatabase.OPEN_READWRITE or CipherDatabase.CREATE_IF_NECESSARY,
            null
        )
        temporary.execSQL("CREATE TABLE recovery_sample (value TEXT NOT NULL)")
        temporary.execSQL("INSERT INTO recovery_sample(value) VALUES ('recovered')")
        temporary.close()

        val backup = SQLiteDatabase.openOrCreateDatabase(backupFile, null)
        backup.execSQL("CREATE TABLE old_plaintext (value TEXT)")
        backup.close()

        OrionDatabaseEncryption.encryptLegacyDatabaseIfNeeded(context, passphrase)

        assertTrue(databaseFile.isFile)
        assertFalse(backupFile.exists())
        assertFalse(temporaryFile.exists())
        val recovered = CipherDatabase.openDatabase(
            databaseFile.absolutePath, passphrase, null, CipherDatabase.OPEN_READWRITE, null
        )
        recovered.use {
            val value = it.rawQuery("SELECT value FROM recovery_sample", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getString(0)
            }
            assertEquals("recovered", value)
        }
    }

    @Test fun encryptedDatabaseWithoutKeystoreKeyFailsClosed() {
        val legacy = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
        legacy.execSQL("CREATE TABLE sample (value TEXT)")
        legacy.close()
        val passphrase = OrionDatabaseEncryption.getOrCreatePassphrase(context)
        OrionDatabaseEncryption.encryptLegacyDatabaseIfNeeded(context, passphrase)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)

        val failure = runCatching { OrionDatabaseEncryption.getOrCreatePassphrase(context) }.exceptionOrNull()
        assertNotNull(failure)
        assertTrue(failure?.message.orEmpty().contains("falta su clave", ignoreCase = true))
    }

    private fun cleanState() {
        runCatching { context.deleteDatabase(DB_NAME) }
        listOf(databaseFile, backupFile, temporaryFile).forEach { runCatching { it.delete() } }
        listOf(
            File(databaseFile.absolutePath + "-wal"),
            File(databaseFile.absolutePath + "-shm")
        ).forEach { runCatching { it.delete() } }
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit() }
        runCatching {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    private fun hasPlainSqliteHeader(file: File): Boolean =
        file.inputStream().use { input ->
            val header = ByteArray(16)
            input.read(header) == 16 && String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
        }

    private companion object {
        const val DB_NAME = "orion_offline_memory.db"
        const val BACKUP_SUFFIX = ".plaintext-backup"
        const val TEMP_SUFFIX = ".encrypted-tmp"
        const val PREFS = "orion_database_key"
        const val KEY_ALIAS = "orion.room.database.wrap.v1"
    }
}
