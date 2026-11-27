package com.orion.voicelab.knowledge

import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Bounded HTTPS reader for pages explicitly discovered by the user-requested search.
 * Rejects private/local destinations, credentials, non-HTTPS URLs and unsafe redirects.
 * Extracted page text remains untrusted evidence until separately classified.
 */
internal object SafeWebPageReader {
    data class Page(
        val title: String,
        val text: String,
        val publishedAtMs: Long?,
        val canonicalUrl: String
    )

    fun read(rawUrl: String): Page? {
        var current = normalizePublicHttpsUrl(rawUrl) ?: return null
        repeat(MAX_REDIRECTS + 1) { hop ->
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain;q=0.8")
            }
            try {
                val status = connection.responseCode
                if (status in REDIRECT_CODES) {
                    if (hop >= MAX_REDIRECTS) return null
                    val location = connection.getHeaderField("Location") ?: return null
                    current = normalizePublicHttpsUrl(URL(URL(current), location).toString()) ?: return null
                    return@repeat
                }
                if (status !in 200..299) return null
                val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                if (!contentType.contains("text/html") &&
                    !contentType.contains("application/xhtml+xml") &&
                    !contentType.startsWith("text/plain")
                ) return null
                val body = connection.inputStream.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4_096)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    String(output.toByteArray(), StandardCharsets.UTF_8)
                }
                val title = TITLE.find(body)?.groupValues?.getOrNull(1)?.let(::plainText)
                    ?.take(180).orEmpty().ifBlank { URL(current).host }
                val text = plainText(
                    body.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\\\1>"), " ")
                ).take(MAX_TEXT_CHARS)
                if (text.length < MIN_TEXT_CHARS) return null
                val published = DATE_META.find(body)?.groupValues?.getOrNull(1)
                    ?.let(::parseDate)
                return Page(title, text, published, current)
            } finally {
                connection.disconnect()
            }
        }
        return null
    }

    fun normalizePublicHttpsUrl(raw: String): String? = runCatching {
        val uri = URI(raw.trim()).normalize()
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.userInfo != null ||
            uri.port !in setOf(-1, 443) ||
            host == "localhost" ||
            host.endsWith(".localhost") ||
            host.endsWith(".local") ||
            host.endsWith(".internal")
        ) return null
        val address = InetAddress.getByName(host)
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return null
        URI("https", null, host, -1, uri.rawPath.ifBlank { "/" }, uri.rawQuery, null)
            .toASCIIString().takeIf { it.length <= MAX_URL_CHARS }
    }.getOrNull()

    private fun plainText(html: String): String = html
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<")
        .replace("&gt;", ">").replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ").trim()

    private fun parseDate(raw: String): Long? = runCatching {
        val value = raw.trim()
        when {
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) ->
                java.time.LocalDate.parse(value).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            value.matches(Regex("\\d{4}-\\d{2}-\\d{2}T.*")) ->
                java.time.Instant.parse(value).toEpochMilli()
            else -> null
        }
    }.getOrNull()

    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val DATE_META = Regex(
        """<meta[^>]+(?:property|name)=["'](?:article:published_time|datePublished|date)["'][^>]+content=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    private const val MAX_REDIRECTS = 3
    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 5_000
    private const val MAX_BYTES = 1_500_000
    private const val MAX_TEXT_CHARS = 12_000
    private const val MIN_TEXT_CHARS = 120
    private const val MAX_URL_CHARS = 1_500
    private const val USER_AGENT = "ORION-Android/1.0 (user-requested research)"
}
