package com.orion.voicelab.llm

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

data class RemoteInferenceSettings(
    val enabled: Boolean = false,
    val mode: String = MODE_AUTO,
    val endpoint: String = "",
    val model: String = "",
    val encryptedApiKey: String = "",
    val shareContext: Boolean = false
) {
    val isConfigured: Boolean
        get() = enabled && shareContext && endpoint.isNotBlank() && model.isNotBlank() &&
            mode in setOf(MODE_AUTO, MODE_REMOTE_ONLY)

    companion object {
        const val MODE_AUTO = "auto"
        const val MODE_LOCAL = "local"
        const val MODE_REMOTE_ONLY = "remote_only"
        private const val PREFS = "orion_remote_inference"
        fun read(context: Context): RemoteInferenceSettings {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return RemoteInferenceSettings(
                enabled = p.getBoolean("enabled", false),
                mode = p.getString("mode", MODE_AUTO) ?: MODE_AUTO,
                endpoint = p.getString("endpoint", "").orEmpty(),
                model = p.getString("model", "").orEmpty(),
                encryptedApiKey = p.getString("api_key_enc", "").orEmpty(),
                shareContext = p.getBoolean("share_context", false)
            )
        }
        fun save(context: Context, settings: RemoteInferenceSettings, apiKey: String? = null) {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val editor = p.edit()
                .putBoolean("enabled", settings.enabled)
                .putString("mode", settings.mode)
                .putString("endpoint", settings.endpoint.trim())
                .putString("model", settings.model.trim())
                .putBoolean("share_context", settings.shareContext)
            if (apiKey != null) editor.putString("api_key_enc", if (apiKey.isBlank()) "" else ApiKeyVault.encrypt(apiKey))
            editor.apply()
        }
        fun apiKey(context: Context): String = ApiKeyVault.decrypt(read(context).encryptedApiKey)
    }
}

private object ApiKeyVault {
    private const val STORE = "AndroidKeyStore"
    private const val ALIAS = "orion.remote.api-key.v1"
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(STORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", STORE)
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }
    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    fun decrypt(value: String): String {
        if (value.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            require(payload.size > 12)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
            String(cipher.doFinal(payload.copyOfRange(12, payload.size)), Charsets.UTF_8)
        }.getOrDefault("")
    }
}


internal object RemoteInferencePayload {
    const val MAX_HISTORY_MESSAGES = 12
    const val MAX_MESSAGE_CHARS = 8_000
    const val MAX_SYSTEM_PROMPT_CHARS = 24_000

    fun build(
        model: String,
        systemPrompt: String,
        history: List<com.orion.voicelab.data.ChatMessage>
    ): JSONObject {
        val messages = JSONArray().put(
            JSONObject().put("role", "system")
                .put("content", systemPrompt.take(MAX_SYSTEM_PROMPT_CHARS))
        )
        history.takeLast(MAX_HISTORY_MESSAGES).forEach { msg ->
            val role = when (msg.role) {
                com.orion.voicelab.data.ChatRole.USER -> "user"
                com.orion.voicelab.data.ChatRole.ASSISTANT -> "assistant"
                com.orion.voicelab.data.ChatRole.SYSTEM -> "system"
            }
            messages.put(
                JSONObject().put("role", role)
                    .put("content", msg.content.take(MAX_MESSAGE_CHARS))
            )
        }
        return JSONObject().put("model", model).put("messages", messages)
            .put("temperature", 0.65).put("max_tokens", 1024)
    }
}

class RemoteInferenceClient(
    private val endpoint: String,
    private val model: String,
    private val apiKey: String,
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 90_000
) {
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)
    private val cancelRequested = AtomicBoolean(false)
    fun cancel() { cancelRequested.set(true); activeConnection.getAndSet(null)?.disconnect() }

    suspend fun generate(systemPrompt: String, history: List<com.orion.voicelab.data.ChatMessage>): LlmGenerationResult =
        withContext(Dispatchers.IO) {
            cancelRequested.set(false)
            val normalized = endpoint.trim().trimEnd('/')
            require(isSafeEndpoint(normalized)) { "El endpoint debe usar HTTPS o una dirección HTTP local/de red privada." }
            require(model.isNotBlank() && model.length <= 160) { "Identificador de modelo no válido." }
            val completionUrl = when { normalized.endsWith("/chat/completions") -> normalized; normalized.endsWith("/v1") -> "$normalized/chat/completions"; else -> "$normalized/v1/chat/completions" }
            val url = URL(completionUrl)
            val body = RemoteInferencePayload.build(model, systemPrompt, history).toString()
            require(body.toByteArray(Charsets.UTF_8).size <= 96 * 1024) { "El contexto remoto supera el límite de seguridad (96 KiB)." }
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val started = System.nanoTime()
            activeConnection.set(connection)
            try {
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val response = stream?.bufferedReader(Charsets.UTF_8)?.use { reader ->
                    val output = StringBuilder()
                    val buffer = CharArray(8_192)
                    while (output.length < 256 * 1024) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 256 * 1024 - output.length))
                        if (count <= 0) break
                        output.append(buffer, 0, count)
                    }
                    output.toString()
                }.orEmpty()
                if (status !in 200..299) error("El servidor de inferencia respondió HTTP $status: " + response.take(500))
                val content = JSONObject(response).getJSONArray("choices")
                    .getJSONObject(0).getJSONObject("message").optString("content").trim()
                check(content.isNotBlank()) { "El servidor remoto devolvió una respuesta vacía." }
                val elapsed = (System.nanoTime() - started) / 1_000_000L
                LlmGenerationResult(content, elapsed, elapsed, content.split(Regex("\\s+")).size, false)
            } catch (error: Exception) {
                if (cancelRequested.get()) throw kotlinx.coroutines.CancellationException("Solicitud remota cancelada")
                throw error
            } finally {
                activeConnection.compareAndSet(connection, null)
                connection.disconnect()
            }
        }

    private fun isSafeEndpoint(value: String): Boolean {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (uri.scheme == "https") return true
        if (uri.scheme != "http") return false
        return host == "localhost" || host == "127.0.0.1" || host == "::1" ||
            host.startsWith("10.") || host.startsWith("192.168.") ||
            host.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))
    }
}
