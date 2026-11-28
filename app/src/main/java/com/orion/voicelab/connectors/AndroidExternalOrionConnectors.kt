package com.orion.voicelab.connectors

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Android intents and a bounded public PescaIA reader. These providers do not
 * require account credentials; they only act after an explicit user request.
 */
class GoogleMapsOrionConnector(private val context: Context) : OrionConnector {
    override val descriptor = ConnectorDescriptor(
        ConnectorId.GOOGLE_MAPS,
        "Google Maps",
        "Buscar lugares y abrir navegación mediante intents de Android.",
        setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
        true
    )

    override suspend fun connectionState() = ConnectorConnectionState.CONNECTED

    override suspend fun execute(action: ConnectorAction): ConnectorResult {
        if (action.connectorId != ConnectorId.GOOGLE_MAPS) {
            return ConnectorResult.Failure("Operación de Maps no disponible.", false)
        }
        val destination = action.arguments["destination"]?.trim().orEmpty()
        if (destination.isBlank()) return ConnectorResult.NeedsUserInput("¿Qué lugar o dirección quieres buscar?")
        val uri = buildGoogleMapsUri(action.operation, destination, action.arguments["mode"])
            ?: return ConnectorResult.Failure("Operación de Maps no disponible.", false)
        return try {
            val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            ConnectorResult.Success(
                if (action.operation == "navigate") "He abierto la navegación hacia $destination." else "He abierto Maps para buscar $destination."
            )
        } catch (_: Exception) {
            ConnectorResult.Failure("No hay una aplicación compatible para abrir el mapa o la ruta.", true)
        }
    }
}

class PescaIAOrionConnector : OrionConnector {
    override val descriptor = ConnectorDescriptor(
        ConnectorId.PESCAIA,
        "PescaIA Surfcasting",
        "Consulta páginas públicas de playas y metodología de PescaIA.",
        setOf(ConnectorCapability.SEARCH, ConnectorCapability.READ),
        true
    )

    override suspend fun connectionState() = ConnectorConnectionState.CONNECTED

    override suspend fun execute(action: ConnectorAction): ConnectorResult {
        if (action.connectorId != ConnectorId.PESCAIA || action.operation != "search_public") {
            return ConnectorResult.Failure("Operación pública de PescaIA no disponible.", false)
        }
        val query = action.arguments["query"]?.trim().orEmpty()
        if (query.isBlank()) return ConnectorResult.NeedsUserInput("¿Qué información de PescaIA quieres consultar?")
        return withContext(Dispatchers.IO) {
            runCatching {
                val pages = when {
                    query.containsAny("metodología", "metodologia", "fuentes", "ranking", "puntuación", "puntuacion") ->
                        listOf("https://pescaisurfcasting.company/metodologia.html")
                    query.containsAny("playa", "pesquero", "zona", "surfcasting", "especie", "cebo", "técnica", "tecnica", "marea", "mareas", "solunar", "normativa", "talla") ->
                        listOf("https://pescaisurfcasting.company/playas.html", "https://pescaisurfcasting.company/metodologia.html")
                    else -> listOf("https://pescaisurfcasting.company/", "https://pescaisurfcasting.company/playas.html")
                }
                val results = pages.mapNotNull { page -> fetchText(page)?.let { page to it } }
                if (results.isEmpty()) {
                    ConnectorResult.Failure("No se pudo consultar PescaIA. Comprueba la conexión e inténtalo de nuevo.", true)
                } else {
                    val terms = query.lowercase().split(Regex("\\s+")).filter { it.length >= 3 }.distinct()
                    val excerpts = results.flatMap { (url, content) ->
                        val normalized = content.replace(Regex("\\s+"), " ")
                        val matches = terms.mapNotNull { term ->
                            val index = normalized.indexOf(term, ignoreCase = true)
                            if (index < 0) null else normalized.substring((index - 220).coerceAtLeast(0), (index + 520).coerceAtMost(normalized.length))
                        }
                        val selected = (matches.ifEmpty { listOf(normalized.take(1_200)) }).distinct().take(3)
                        selected.map { "Fuente: $url\n$it" }
                    }
                    ConnectorResult.Success(
                        "Consulta de páginas públicas de PescaIA Surfcasting. Contenido web orientativo; comprueba la fecha y las fuentes oficiales cuando se trate de normativa o seguridad.\n" +
                            excerpts.joinToString("\n\n").take(5_500)
                    )
                }
            }.getOrElse {
                ConnectorResult.Failure("No se pudo consultar PescaIA. Comprueba la conexión e inténtalo de nuevo.", true)
            }
        }
    }

    private fun fetchText(address: String): String? {
        var current = URL(address)
        repeat(MAX_REDIRECTS + 1) {
            require(isAllowedPescaIaUrl(current.toString())) {
                "Redirección fuera del dominio autorizado de PescaIA"
            }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 10_000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "ORION-Android/1.0")
                setRequestProperty("Accept", "text/html")
            }
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    val location = connection.getHeaderField("Location") ?: return null
                    current = URL(current, location)
                    return@repeat
                }
                if (status !in 200..299) return null
                val bytes = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4_096)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_HTML_BYTES) throw IllegalStateException("Página demasiado grande")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                val extracted = String(bytes, Charsets.UTF_8)
                    .replace(Regex("""(?is)<(script|style|noscript)[^>]*>.*?</\1>"""), " ")
                    .replace(Regex("(?s)<[^>]+>"), " ")
                    .replace("&nbsp;", " ")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace(Regex("""\s+"""), " ")
                    .take(MAX_TEXT_CHARS)
                return extracted.takeIf { it.length >= MIN_EXTRACTED_TEXT_CHARS }
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    private fun String.containsAny(vararg values: String): Boolean =
        values.any { contains(it, ignoreCase = true) }

    private companion object {
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        const val MIN_EXTRACTED_TEXT_CHARS = 120
        const val MAX_HTML_BYTES = 1_000_000
        const val MAX_TEXT_CHARS = 100_000
    }
}

internal fun isAllowedPescaIaUrl(address: String): Boolean = runCatching {
    val parsed = URL(address)
    parsed.protocol.equals("https", ignoreCase = true) &&
        parsed.host.lowercase() in setOf("pescaisurfcasting.company", "www.pescaisurfcasting.company")
}.getOrDefault(false)

internal fun buildGoogleMapsUri(operation: String, destination: String, requestedMode: String?): Uri? {
    if (destination.isBlank()) return null
    return when (operation) {
        "search_place" -> Uri.parse("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(destination))
        "navigate" -> {
            val mode = when (requestedMode?.lowercase()) {
                "walking", "a pie", "andar" -> "walking"
                "transit", "transporte", "transporte público" -> "transit"
                else -> "driving"
            }
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(destination) + "&travelmode=" + mode)
        }
        else -> null
    }
}
