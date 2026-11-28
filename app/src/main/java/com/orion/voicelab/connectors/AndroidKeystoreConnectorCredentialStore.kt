package com.orion.voicelab.connectors

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts provider credentials with an AES-256-GCM key held by Android Keystore.
 * Only ciphertext is persisted in app-private SharedPreferences.
 */
class AndroidKeystoreConnectorCredentialStore internal constructor(
    context: Context,
    private val testKeyProvider: (() -> SecretKey)?
) : ConnectorCredentialStore {
    constructor(context: Context) : this(context, null)
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override suspend fun save(connectorId: ConnectorId, credential: ByteArray) {
        require(credential.isNotEmpty()) { "Credential cannot be empty" }
        withContext(Dispatchers.IO) {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            val encrypted = cipher.doFinal(credential)
            val iv = cipher.iv
            require(iv.size <= 255) { "Unexpected AES-GCM IV size" }

            // Format: one-byte IV length | IV | authenticated ciphertext + GCM tag.
            val payload = ByteArray(1 + iv.size + encrypted.size)
            payload[0] = iv.size.toByte()
            System.arraycopy(iv, 0, payload, 1, iv.size)
            System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)

            val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
            check(preferences.edit().putString(connectorId.name, encoded).commit()) {
                "Unable to persist encrypted connector credential"
            }
        }
    }

    override suspend fun read(connectorId: ConnectorId): ByteArray? = withContext(Dispatchers.IO) {
        val encoded = preferences.getString(connectorId.name, null) ?: return@withContext null
        val payload = try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            preferences.edit().remove(connectorId.name).commit()
            return@withContext null
        }

        if (payload.size < MIN_PAYLOAD_SIZE) {
            preferences.edit().remove(connectorId.name).commit()
            return@withContext null
        }

        val ivLength = payload[0].toInt() and 0xff
        if (ivLength != GCM_IV_LENGTH || payload.size <= 1 + ivLength) {
            preferences.edit().remove(connectorId.name).commit()
            return@withContext null
        }

        val iv = payload.copyOfRange(1, 1 + ivLength)
        val ciphertext = payload.copyOfRange(1 + ivLength, payload.size)
        try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(ciphertext)
        } catch (_: Exception) {
            // Do not return corrupt or unauthenticated credential material.
            preferences.edit().remove(connectorId.name).commit()
            null
        }
    }

    override suspend fun delete(connectorId: ConnectorId) {
        withContext(Dispatchers.IO) {
            check(preferences.edit().remove(connectorId.name).commit()) {
                "Unable to remove connector credential"
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        testKeyProvider?.let { return it() }
        return synchronized(KEY_LOCK) {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return@synchronized it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setKeySize(256)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generator.generateKey()
        }
    }

    private companion object {
        val KEY_LOCK = Any()
        const val PREFERENCES_NAME = "orion_connector_credentials"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "orion_connector_credentials_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
        const val MIN_PAYLOAD_SIZE = 1 + GCM_IV_LENGTH + 1
    }
}
