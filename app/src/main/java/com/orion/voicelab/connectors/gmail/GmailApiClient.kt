package com.orion.voicelab.connectors.gmail

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

data class GmailMessageSummary(
    val id: String,
    val threadId: String,
    val from: String,
    val subject: String,
    val date: String,
    val snippet: String,
    val bodyText: String = ""
)

/**
 * Read-only Gmail REST adapter. It never logs tokens or message bodies and bounds
 * both list size and response size. Sending and modifying mail are intentionally absent.
 */
class GmailApiClient(
    private val credentials: GmailCredentialManager
) {
    suspend fun searchMessages(query: String, maxResults: Int = 10): Result<List<GmailMessageSummary>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(query.length <= MAX_QUERY_LENGTH) { "La búsqueda de Gmail es demasiado larga." }
                val token = credentials.getValidAccessToken()
                    ?: throw ReauthorizationRequiredException()
                val limit = maxResults.coerceIn(1, MAX_RESULTS)
                val list = requestJson(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages?q=${encode(query)}&maxResults=$limit",
                    token
                )
                val ids = list.optJSONArray("messages") ?: JSONArray()
                buildList {
                    for (i in 0 until ids.length()) {
                        val item = ids.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isBlank()) continue
                        val message = requestJson(
                            "https://gmail.googleapis.com/gmail/v1/users/me/messages/${encode(id)}?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date",
                            token
                        )
                        add(message.toSummary())
                    }
                }
            }
        }

    suspend fun getMessage(messageId: String): Result<GmailMessageSummary> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(messageId.matches(Regex("[A-Za-z0-9_-]{1,200}"))) { "Identificador de correo no válido." }
                val token = credentials.getValidAccessToken() ?: throw ReauthorizationRequiredException()
                requestJson(
                    "https://gmail.googleapis.com/gmail/v1/users/me/messages/${encode(messageId)}?format=metadata&metadataHeaders=From&metadataHeaders=Subject&metadataHeaders=Date",
                    token
                ).toSummary(includeBody = false)
            }
        }

    private fun JSONObject.toSummary(includeBody: Boolean = false): GmailMessageSummary {
        val payload = optJSONObject("payload")
        val headers = payload?.optJSONArray("headers") ?: JSONArray()
        var from = ""
        var subject = ""
        var date = ""
        for (i in 0 until headers.length()) {
            val header = headers.optJSONObject(i) ?: continue
            when (header.optString("name").lowercase()) {
                "from" -> from = header.optString("value").take(MAX_HEADER_LENGTH)
                "subject" -> subject = header.optString("value").take(MAX_HEADER_LENGTH)
                "date" -> date = header.optString("value").take(MAX_HEADER_LENGTH)
            }
        }
        return GmailMessageSummary(
            id = optString("id"),
            threadId = optString("threadId"),
            from = from,
            subject = subject,
            date = date,
            snippet = optString("snippet").take(MAX_SNIPPET_LENGTH),
            bodyText = if (includeBody && payload != null) payload.extractPlainText().take(MAX_BODY_LENGTH) else ""
        )
    }

    private fun requestJson(url: String, token: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "ORION-Android")
        }
        try {
            val status = connection.responseCode
            if (status == 401) throw ReauthorizationRequiredException()
            if (status !in 200..299) throw GmailApiException(status)
            val body = connection.inputStream.use { it.readBounded(MAX_RESPONSE_BYTES) }
            return JSONObject(String(body, Charsets.UTF_8))
        } finally {
            connection.disconnect()
        }
    }

    private fun JSONObject.extractPlainText(): String {
        val mimeType = optString("mimeType").lowercase()
        if (mimeType == "text/plain") {
            val encoded = optJSONObject("body")?.optString("data").orEmpty()
            if (encoded.isNotBlank()) {
                return runCatching {
                    String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrDefault("")
            }
        }
        val parts = optJSONArray("parts") ?: return ""
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            val text = part.extractPlainText()
            if (text.isNotBlank()) return text
        }
        return ""
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    class ReauthorizationRequiredException : IllegalStateException("La autorización de Gmail ha caducado. Vuelve a conectar la cuenta.")
    class GmailApiException(status: Int) : IllegalStateException("Gmail no pudo completar la consulta (HTTP $status).")

    private companion object {
        const val MAX_RESULTS = 20
        const val MAX_QUERY_LENGTH = 256
        const val MAX_HEADER_LENGTH = 300
        const val MAX_SNIPPET_LENGTH = 500
        const val MAX_BODY_LENGTH = 3_000
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val TIMEOUT_MS = 15_000
    }
}


private fun InputStream.readBounded(maxBytes: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(maxBytes, 8_192))
    val buffer = ByteArray(8_192)
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > maxBytes) {
            throw IllegalStateException("Respuesta del servicio externo demasiado grande.")
        }
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}
