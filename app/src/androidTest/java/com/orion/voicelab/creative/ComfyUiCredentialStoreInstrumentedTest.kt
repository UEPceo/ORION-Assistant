package com.orion.voicelab.creative

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.security.KeyStore

class ComfyUiCredentialStoreInstrumentedTest {
    private lateinit var context: Context
    private val alias = "orion.comfyui.bearer.wrap.v1"

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clean()
    }

    @After fun tearDown() = clean()

    @Test fun tokenIsEncryptedAndNeverLeftInPlaintextPreferences() {
        val token = "comfy-secret-token-123"
        ComfyUiCredentialStore.save(context, token).getOrThrow()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        assertFalse(prefs.contains(LEGACY_KEY))
        assertTrue(prefs.getString(ENCRYPTED_KEY, null).orEmpty().isNotBlank())
        assertFalse(prefs.getString(ENCRYPTED_KEY, "").orEmpty().contains(token))
        assertEquals(token, ComfyUiCredentialStore.read(context).getOrThrow())
    }

    @Test fun legacyPlaintextTokenMigratesOnFirstRead() {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(LEGACY_KEY, "legacy-comfy-token").commit()
        assertEquals("legacy-comfy-token", ComfyUiCredentialStore.read(context).getOrThrow())
        assertFalse(prefs.contains(LEGACY_KEY))
        assertTrue(prefs.getString(ENCRYPTED_KEY, null).orEmpty().isNotBlank())
    }

    @Test fun missingKeystoreKeyFailsClosedInsteadOfGeneratingReplacement() {
        ComfyUiCredentialStore.save(context, "stored-token").getOrThrow()
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (store.containsAlias(alias)) store.deleteEntry(alias)
        assertTrue(ComfyUiCredentialStore.read(context).isFailure)
        assertTrue(ComfyUiCredentialStore.save(context, "").isFailure)
        assertTrue(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(ENCRYPTED_KEY))
    }

    @Test fun rejectsHeaderInjectionInBearerToken() {
        assertTrue(ComfyUiCredentialStore.save(context, "token\r\nAuthorization: evil").isFailure)
    }

    private fun clean() {
        runCatching { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit() }
        runCatching {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (store.containsAlias(alias)) store.deleteEntry(alias)
        }
    }

    private companion object {
        const val PREFS = "orion_media_settings"
        const val LEGACY_KEY = "bearer_token"
        const val ENCRYPTED_KEY = "bearer_token_encrypted_v1"
    }
}
