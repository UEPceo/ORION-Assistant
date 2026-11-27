package com.orion.voicelab.knowledge

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Explicit, read-only web discovery. It returns search snippets as UNKNOWN evidence;
 * It reads only bounded public HTTPS result pages; unreadable snippets remain UNKNOWN.
 */
class DuckDuckGoKnowledgeSource : KnowledgeSource {
    override val sourceId: String = "duckduckgo-web-discovery"

    override suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument> {
        val searchTerms = WebSearchIntentPolicy.searchTerms(query.rawText) ?: return emptyList()
        if (limit <= 0) return emptyList()
        return withContext(Dispatchers.IO) {
            val html = fetchResults(searchTerms) ?: throw java.io.IOException("Search provider returned no response")
            parseResults(html, query, limit.coerceAtMost(MAX_RESULTS))
        }
    }

    private fun fetchResults(searchText: String): String? {
        val encoded = URLEncoder.encode(searchText.take(240), StandardCharsets.UTF_8.name())
        val connection = (URL("$SEARCH_ENDPOINT?q=$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "text/html")
        }
        return try {
            if (connection.responseCode !in 200..299) throw java.io.IOException("Search provider HTTP failure")
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                var total = 0
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > MAX_RESPONSE_BYTES) return null
                    output.write(buffer, 0, count)
                }
                output.toByteArray()
            }
            String(bytes, StandardCharsets.UTF_8)
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun parseResults(
        html: String,
        query: BrainQuery,
        limit: Int
    ): List<KnowledgeDocument> {
        val anchors = RESULT_LINK.findAll(html).toList()
        val snippets = RESULT_SNIPPET.findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
        val now = System.currentTimeMillis()
        val documents = mutableListOf<KnowledgeDocument>()
        var fullPageReads = 0
        for ((index, match) in anchors.withIndex()) {
            if (documents.size >= limit) break
            val target = resolveResultUrl(match.groupValues[1]) ?: continue
            val host = runCatching { URL(target).host.lowercase(Locale.ROOT) }
                .getOrNull()?.removePrefix("www.") ?: continue
            val snippet = snippets.getOrNull(index).orEmpty().take(700)
            val page = if (fullPageReads < MAX_FULL_PAGE_READS) {
                fullPageReads++
                SafeWebPageReader.read(target)
            } else null
            val title = (page?.title ?: cleanHtml(match.groupValues[2])).take(180)
            if (title.isBlank()) continue
            val content = page?.text ?: listOf(title, snippet).filter(String::isNotBlank).joinToString("\n")
            if (content.isBlank()) continue
            val canonical = page?.canonicalUrl ?: target
            val id = "web-" + canonical.hashCode().toUInt().toString(16)
            val finalHost = runCatching { URL(canonical).host.lowercase(Locale.ROOT).removePrefix("www.") }
                .getOrDefault(host)
            val authority = if (page == null) EvidenceAuthority.UNKNOWN else WebAuthorityClassifier.classify(finalHost)
            val evidence = KnowledgeEvidence(
                id = id,
                title = title,
                canonicalUrl = canonical,
                publisherId = finalHost,
                domain = query.domain,
                authority = if (page == null) EvidenceAuthority.UNKNOWN else authority,
                retrievedAtMs = now,
                publishedAtMs = page?.publishedAtMs,
                content = content.take(MAX_PAGE_TEXT_CHARS)
            )
            documents += KnowledgeDocument(
                evidence = evidence,
                excerpt = (page?.text ?: snippet.ifBlank { title }).take(1_800),
                relevance = if (page == null) 0.25 else 0.55
            )
        }
        return documents.distinctBy { it.evidence.canonicalUrl }
    }

    private fun resolveResultUrl(rawHref: String): String? {
        val decoded = cleanHtml(rawHref)
        val candidate = when {
            decoded.startsWith("//duckduckgo.com/l/") -> {
                val queryPart = decoded.substringAfter('?', "")
                val encodedTarget = queryPart.split('&')
                    .firstOrNull { it.startsWith("uddg=") }
                    ?.substringAfter("uddg=") ?: return null
                runCatching {
                    URLDecoder.decode(encodedTarget, StandardCharsets.UTF_8.name())
                }.getOrNull() ?: return null
            }
            decoded.startsWith("https://") -> decoded
            else -> return null
        }
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.host.isNullOrBlank() ||
            uri.userInfo != null ||
            (uri.port != -1 && uri.port != 443)
        ) return null
        val host = uri.host.lowercase(Locale.ROOT)
        if (host == "duckduckgo.com" || host.endsWith(".duckduckgo.com")) return null
        return uri.toASCIIString().takeIf { it.length <= MAX_URL_CHARS }
    }

    private fun cleanHtml(value: String): String = value
        .replace(Regex("(?is)<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&nbsp;", " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private companion object {
        const val SEARCH_ENDPOINT = "https://html.duckduckgo.com/html/"
        const val CONNECT_TIMEOUT_MS = 6_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_RESPONSE_BYTES = 1_000_000
        const val MAX_RESULTS = 5
        const val MAX_FULL_PAGE_READS = 3
        const val MAX_URL_CHARS = 1_500
        const val MAX_PAGE_TEXT_CHARS = 12_000
        const val USER_AGENT = "ORION-Android/1.0 (web discovery)"
        val RESULT_LINK = Regex(
            """<a[^>]*class=["'][^"']*result__a[^"']*["'][^>]*href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        val RESULT_SNIPPET = Regex(
            """class=["'][^"']*result__snippet[^"']*["'][^>]*>(.*?)</(?:a|td|div)>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}

object WebSearchIntentPolicy {
    private val explicitPrefixes = listOf(
        "busca en internet", "busca en la web", "búscalo en internet",
        "búscalo en la web", "investiga en internet", "investiga en la web",
        "consulta internet", "consulta la web", "busca online",
        "search the web", "search online", "look this up", "look it up online"
    )

    fun requiresWebSearch(text: String): Boolean = searchTerms(text) != null

    /** Removes the command prefix and common direct identifiers before sending a query. */
    fun searchTerms(text: String): String? {
        var value = text.trim()
        val prefix = explicitPrefixes.firstOrNull { value.startsWith(it, ignoreCase = true) } ?: return null
        value = value.drop(prefix.length).trimStart(' ', ':', ',', ';')
        value = value.replace(Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("(?<!\\w)(?:\\+?\\d[\\d\\s().-]{7,}\\d)(?!\\w)"), " ")
            .replace(Regex("\\s+"), " ").trim()
        return value.takeIf { it.length >= 3 }?.take(240)
    }
}


/** Conservative publisher registry. Unknown domains are never upgraded by suffix guessing. */
internal object WebAuthorityClassifier {
    private val officialHosts = setOf(
        "boe.es", "aemet.es", "miteco.gob.es", "mapa.gob.es", "sanidad.gob.es",
        "administracion.gob.es", "juntadeandalucia.es", "gva.es", "xunta.gal",
        "euskadi.eus", "gencat.cat", "navarra.es", "aragon.es", "cantabria.es",
        "asturias.es", "juntadeextremadura.es", "comunidad.madrid", "regiondemurcia.es",
        "canarias.es", "larioja.org", "europa.eu", "who.int", "un.org"
    )
    private val technicalHosts = setOf(
        "developer.android.com", "kotlinlang.org", "docs.python.org",
        "developer.mozilla.org", "docs.github.com",
        "developer.apple.com", "learn.microsoft.com"
    )

    fun classify(host: String): EvidenceAuthority {
        val normalized = host.lowercase(Locale.ROOT).removePrefix("www.")
        return when {
            normalized in officialHosts || officialHosts.any { normalized.endsWith(".$it") } ->
                EvidenceAuthority.OFFICIAL_PRIMARY
            normalized in technicalHosts || technicalHosts.any { normalized.endsWith(".$it") } ->
                EvidenceAuthority.PRIMARY_TECHNICAL
            else -> EvidenceAuthority.UNKNOWN
        }
    }
}
