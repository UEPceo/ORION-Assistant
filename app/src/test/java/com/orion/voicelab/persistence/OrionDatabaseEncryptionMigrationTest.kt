package com.orion.voicelab.persistence

import android.database.sqlite.SQLiteDatabase as PlainDatabase
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OrionDatabaseEncryptionMigrationTest {
    @Test fun legacyRoomDatabaseIsConvertedWithoutLosingRowsOrSchemaVersion() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = context.getDatabasePath("orion_offline_memory.db")
        file.parentFile?.mkdirs()
        file.delete()
        val plain = PlainDatabase.openOrCreateDatabase(file, null)
        plain.execSQL("CREATE TABLE migration_probe (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
        plain.execSQL("INSERT INTO migration_probe (id, value) VALUES (1, 'preserved')")
        plain.version = 5
        plain.close()

        System.loadLibrary("sqlcipher")
        val passphrase = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef".toByteArray()
        OrionDatabaseEncryption.encryptLegacyDatabaseIfNeeded(context, passphrase)

        val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        assertFalse(String(header, Charsets.US_ASCII) == "SQLite format 3\u0000")
        assertFalse(File(file.parentFile, file.name + ".plaintext-backup").exists())

        val encrypted = CipherDatabase.openDatabase(
            file.absolutePath, passphrase, null, CipherDatabase.OPEN_READWRITE, null
        )
        try {
            encrypted.rawQuery("SELECT value FROM migration_probe WHERE id = 1", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("preserved", cursor.getString(0))
            }
            encrypted.rawQuery("PRAGMA user_version", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(5, cursor.getInt(0))
            }
        } finally {
            encrypted.close()
        }

        // Re-running the migration against an encrypted file must be a no-op.
        OrionDatabaseEncryption.encryptLegacyDatabaseIfNeeded(context, passphrase)
        val secondOpen = CipherDatabase.openDatabase(
            file.absolutePath, passphrase, null, CipherDatabase.OPEN_READWRITE, null
        )
        secondOpen.close()
    }
}
