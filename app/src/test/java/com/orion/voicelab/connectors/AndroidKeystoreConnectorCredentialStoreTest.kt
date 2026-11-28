package com.orion.voicelab.connectors

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import java.security.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidKeystoreConnectorCredentialStoreTest {
    private lateinit var context: Context
    private lateinit var key: SecretKey

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("orion_connector_credentials", Context.MODE_PRIVATE)
            .edit().clear().commit()
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    }

    @Test
    fun encryptsCredentialAtRestAndRoundTripsPlaintext() = runBlocking {
        val store = AndroidKeystoreConnectorCredentialStore(context) { key }
        val secret = "oauth-access-token".toByteArray()

        store.save(ConnectorId.GMAIL, secret)

        val stored = context.getSharedPreferences("orion_connector_credentials", Context.MODE_PRIVATE)
            .getString(ConnectorId.GMAIL.name, null)!!
        assertFalse(stored.contains("oauth-access-token"))
        assertArrayEquals(secret, store.read(ConnectorId.GMAIL))
        assertTrue(store.read(ConnectorId.GITHUB) == null)
    }

    @Test
    fun tamperedGcmCiphertextIsRejectedAndRemoved() = runBlocking {
        val store = AndroidKeystoreConnectorCredentialStore(context) { key }
        store.save(ConnectorId.GITHUB, "github-token".toByteArray())
        val preferences = context.getSharedPreferences("orion_connector_credentials", Context.MODE_PRIVATE)
        val encoded = preferences.getString(ConnectorId.GITHUB.name, null)!!
        val payload = Base64.decode(encoded, Base64.NO_WRAP)
        payload[payload.lastIndex] = (payload.last().toInt() xor 0x01).toByte()
        preferences.edit().putString(ConnectorId.GITHUB.name, Base64.encodeToString(payload, Base64.NO_WRAP)).commit()

        assertNull(store.read(ConnectorId.GITHUB))
        assertFalse(preferences.contains(ConnectorId.GITHUB.name))
    }
}
