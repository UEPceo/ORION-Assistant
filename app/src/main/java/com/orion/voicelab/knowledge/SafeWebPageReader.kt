package com.orion.voicelab.knowledge

import javax.net.ssl.HttpsURLConnection
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
            val host = URI(current).host ?: return null
            val pinnedAddress = resolvePublicAddress(host) ?: return null
            val connection = (URL(current).openConnection() as HttpsURLConnection).apply {
                sslSocketFactory = PinnedHttpsSocketFactory(host, pinnedAddress, CONNECT_TIMEOUT_MS)
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
                    body.replace(Regex("(?is)<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\1>"), " ")
                ).take(MAX_TEXT_CHARS)
                if (text.length < MIN_TEXT_CHARS) return null
                val published = DATE_META.find(body)?.groupValues?.getOrNull(1)
                    ?.let(::parseDate)
                    ?: connection.getHeaderField("Last-Modified")?.let(::parseDate)
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
        if (resolvePublicAddress(host) == null) return null
        URI("https", null, host, -1, uri.path.ifBlank { "/" }, uri.query, null)
            .toASCIIString().takeIf { it.length <= MAX_URL_CHARS }
    }.getOrNull()

    internal fun resolvePublicAddress(host: String): InetAddress? {
        val addresses = runCatching { InetAddress.getAllByName(host) }.getOrNull() ?: return null
        if (addresses.isEmpty() || addresses.any(::isNonPublicAddress)) return null
        return addresses.first()
    }

    internal fun isNonPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return true
        val bytes = address.address.map { it.toInt() and 0xff }
        if (bytes.size == 4) {
            val a = bytes[0]
            val b = bytes[1]
            val c = bytes[2]
            return a == 0 || a == 10 || a == 127 ||
                (a == 169 && b == 254) ||
                (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) ||
                (a == 100 && b in 64..127) ||
                (a == 192 && b == 0) ||
                (a == 192 && b == 88 && c == 99) ||
                (a == 198 && b in 18..19) ||
                (a == 198 && b == 51) ||
                (a == 203 && b == 0) ||
                a >= 224
        }
        if (bytes.size == 16) {
            val isGlobalUnicast = (bytes[0] and 0xe0) == 0x20 // 2000::/3
            if (!isGlobalUnicast) return true
            val isIetfProtocolAssignment = bytes[0] == 0x20 &&
                bytes[1] == 0x01 && (bytes[2] == 0x00 || bytes[2] == 0x01)
            val isDocumentation = (bytes[0] == 0x3f && bytes[1] == 0xff &&
                (bytes[2] and 0xf0) == 0x00) || // 3fff::/20
                (bytes[0] == 0x20 && bytes[1] == 0x01 && bytes[2] == 0x0d && bytes[3] == 0xb8) // 2001:db8::/32
            val isSixToFour = bytes[0] == 0x20 && bytes[1] == 0x02 // 2002::/16
            val isWellKnownNat64 = bytes[0] == 0x00 && bytes[1] == 0x64 &&
                bytes[2] == 0xff && bytes[3] == 0x9b &&
                bytes.subList(4, 12).all { it == 0 } // 64:ff9b::/96
            val isLocalUseNat64 = bytes[0] == 0x00 && bytes[1] == 0x64 &&
                bytes[2] == 0xff && bytes[3] == 0x9b &&
                bytes[4] == 0x00 && bytes[5] == 0x01 // 64:ff9b:1::/48
            return isIetfProtocolAssignment || isDocumentation || isSixToFour ||
                isWellKnownNat64 || isLocalUseNat64
        }
        return true
    }

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
            else -> java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toInstant().toEpochMilli()
        }
    }.getOrNull()

    private val TITLE = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val DATE_META = Regex(
        """<meta[^>]+(?:property|name)=["'](?:article:modified_time|dateModified|article:published_time|datePublished|date|last-modified)["'][^>]+content=["']([^"']+)["']""",
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
