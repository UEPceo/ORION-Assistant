package com.orion.voicelab.creative

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores ComfyUI bearer credentials encrypted with an AES-GCM key held by Android Keystore.
 * Existing plaintext preference values are migrated on first read and removed atomically.
 */
object ComfyUiCredentialStore {
    private const val PREFS = "orion_media_settings"
    private const val ENCRYPTED_KEY = "bearer_token_encrypted_v1"
    private const val LEGACY_KEY = "bearer_token"
    private const val KEY_ALIAS = "orion.comfyui.bearer.wrap.v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_BITS = 128
    private const val MAX_TOKEN_CHARS = 4_096

    fun read(context: Context): Result<String> = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val encrypted = prefs.getString(ENCRYPTED_KEY, null)
        if (encrypted != null) {
            decrypt(encrypted)
        } else {
            val legacy = prefs.getString(LEGACY_KEY, "").orEmpty()
            validateToken(legacy)
            if (legacy.isNotBlank()) save(context, legacy).getOrThrow()
            else check(prefs.edit().remove(LEGACY_KEY).commit()) { "No se pudo limpiar la preferencia antigua." }
            legacy
        }
    }

    fun save(context: Context, token: String): Result<Unit> = runCatching {
        validateToken(token)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (token.isBlank()) {
            check(prefs.edit().remove(ENCRYPTED_KEY).remove(LEGACY_KEY).commit()) {
                "No se pudo eliminar el token multimedia."
            }
        } else {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
            val payload = cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8))
            val encoded = Base64.encodeToString(payload, Base64.NO_WRAP)
            check(prefs.edit().putString(ENCRYPTED_KEY, encoded).remove(LEGACY_KEY).commit()) {
                "No se pudo guardar el token multimedia cifrado."
            }
        }
    }

    private fun decrypt(encoded: String): String {
        val key = loadKey() ?: error(
            "No existe la clave Android Keystore para descifrar el token ComfyUI. No se generará otra clave."
        )
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        require(payload.size > 12 + 16) { "El token ComfyUI cifrado está dañado." }
        val iv = payload.copyOfRange(0, 12)
        val ciphertext = payload.copyOfRange(12, payload.size)
        val token = Cipher.getInstance(TRANSFORMATION).run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(doFinal(ciphertext), Charsets.UTF_8)
        }
        validateToken(token)
        return token
    }

    private fun validateToken(token: String) {
        require(token.length <= MAX_TOKEN_CHARS && token.none(Char::isISOControl)) {
            "El token ComfyUI contiene caracteres no permitidos o supera el límite."
        }
    }

    private fun loadKey(): SecretKey? {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        return (store.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    private fun loadOrCreateKey(): SecretKey {
        loadKey()?.let { return it }
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
}
