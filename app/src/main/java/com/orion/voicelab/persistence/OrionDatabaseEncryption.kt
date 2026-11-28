package com.orion.voicelab.persistence

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Room database key management and one-time conversion of the legacy plaintext Room file.
 * The SQLCipher passphrase is random, never hard-coded, and only stored wrapped by Android Keystore.
 */
internal object OrionDatabaseEncryption {
    private const val KEY_ALIAS = "orion.room.database.wrap.v1"
    private const val PREFS = "orion_database_key"
    private const val KEY_CIPHERTEXT = "wrapped_passphrase_v1"
    private const val DB_NAME = "orion_offline_memory.db"
    private const val BACKUP_SUFFIX = ".plaintext-backup"
    private const val TEMP_SUFFIX = ".encrypted-tmp"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128

    fun getOrCreatePassphrase(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wrapped = prefs.getString(KEY_CIPHERTEXT, null)
        if (wrapped != null) return unwrap(wrapped)

        val database = context.getDatabasePath(DB_NAME)
        if (database.exists() && !hasPlainSqliteHeader(database)) {
            error("La base de datos parece cifrada, pero falta su clave de Android Keystore. Se bloquea el acceso para evitar pérdida de datos.")
        }
        val key = loadOrCreateWrappingKey()
        val random = ByteArray(32)
        SecureRandom().nextBytes(random)
        val passphrase = random.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
        random.fill(0)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(passphrase)
        val payload = cipher.iv + encrypted
        val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
        check(prefs.edit().putString(KEY_CIPHERTEXT, encoded).commit()) {
            "No se pudo guardar de forma segura la clave de la base de datos."
        }
        return passphrase
    }

    private fun unwrap(encoded: String): ByteArray {
        val key = loadWrappingKey()
            ?: error("No existe la clave de Android Keystore para descifrar la base de datos. No se generará otra clave.")
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12 + 16) { "El contenedor de la clave de base de datos está dañado." }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        return Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            doFinal(ciphertext)
        }
    }

    private fun loadOrCreateWrappingKey(): SecretKey {
        loadWrappingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    private fun loadWrappingKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    /**
     * Converts the old plaintext SQLite database in place using SQLCipher's sqlcipher_export.
     * The old file is retained until the encrypted replacement has been fully written and renamed.
     */
    fun encryptLegacyDatabaseIfNeeded(context: Context, passphrase: ByteArray) {
        val database = context.getDatabasePath(DB_NAME)
        val backup = File(database.parentFile, database.name + BACKUP_SUFFIX)
        val temporary = File(database.parentFile, database.name + TEMP_SUFFIX)
        database.parentFile?.let { if (!it.exists()) check(it.mkdirs()) }

        if (!database.exists() && backup.exists()) {
            if (temporary.exists() && temporary.length() > 0L) {
                check(temporary.renameTo(database)) { "No se pudo finalizar la conversión cifrada." }
                backup.delete()
            } else {
                check(backup.renameTo(database)) { "No se pudo restaurar la base de datos anterior." }
            }
        }
        if (!database.exists() || !hasPlainSqliteHeader(database)) return

        if (temporary.exists()) check(temporary.delete()) { "No se pudo limpiar una conversión anterior." }
        val source = CipherDatabase.openDatabase(
            database.absolutePath,
            byteArrayOf(),
            null,
            CipherDatabase.OPEN_READWRITE,
            null
        )
        try {
            val userVersion = source.rawQuery("PRAGMA user_version", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            val key = String(passphrase, Charsets.UTF_8)
            require(key.matches(Regex("[0-9a-f]{64}"))) { "La clave SQLCipher no tiene el formato esperado." }
            val escapedPath = temporary.absolutePath.replace("'", "''")
            source.rawExecSQL("ATTACH DATABASE '$escapedPath' AS encrypted KEY '$key'")
            try {
                source.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                source.rawExecSQL("PRAGMA encrypted.user_version = $userVersion")
            } finally {
                source.rawExecSQL("DETACH DATABASE encrypted")
            }
            source.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        } finally {
            source.close()
        }
        check(temporary.isFile && temporary.length() > 0L) {
            "SQLCipher no produjo una base de datos cifrada válida."
        }
        File(database.absolutePath + "-wal").delete()
        File(database.absolutePath + "-shm").delete()
        if (backup.exists()) check(backup.delete()) { "No se pudo limpiar el respaldo anterior." }
        check(database.renameTo(backup)) { "No se pudo conservar la base de datos anterior durante la conversión." }
        if (!temporary.renameTo(database)) {
            backup.renameTo(database)
            error("No se pudo activar la base de datos cifrada; se restauró la anterior.")
        }
        backup.delete()
    }

    private fun hasPlainSqliteHeader(file: File): Boolean = runCatching {
        if (file.length() < 16L) return@runCatching false
        file.inputStream().use { input ->
            val header = ByteArray(16)
            if (input.read(header) != header.size) return@use false
            String(header, Charsets.US_ASCII) == "SQLite format 3\u0000"
        }
    }.getOrDefault(false)
}
