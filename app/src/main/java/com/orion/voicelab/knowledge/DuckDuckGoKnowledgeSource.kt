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
 * it never fetches arbitrary result pages or treats snippets as verified facts.
 */
class DuckDuckGoKnowledgeSource : KnowledgeSource {
    override val sourceId: String = "duckduckgo-web-discovery"

    override suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument> {
        if (!WebSearchIntentPolicy.requiresWebSearch(query.rawText) || limit <= 0) {
            return emptyList()
        }
        return withContext(Dispatchers.IO) {
            val html = fetchResults(query.rawText) ?: return@withContext emptyList()
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
            if (connection.responseCode !in 200..299) return null
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

    private fun parseResults(
        html: String,
        query: BrainQuery,
        limit: Int
    ): List<KnowledgeDocument> {
        val anchors = RESULT_LINK.findAll(html).toList()
        val snippets = RESULT_SNIPPET.findAll(html).map { cleanHtml(it.groupValues[1]) }.toList()
        val now = System.currentTimeMillis()
        return anchors.mapNotNullIndexed { index, match ->
            val title = cleanHtml(match.groupValues[2]).take(180)
            val target = resolveResultUrl(match.groupValues[1]) ?: return@mapNotNullIndexed null
            val host = runCatching { URL(target).host.lowercase(Locale.ROOT) }.getOrNull()
                ?.removePrefix("www.") ?: return@mapNotNullIndexed null
            if (title.isBlank()) return@mapNotNullIndexed null
            val snippet = snippets.getOrNull(index).orEmpty().take(700)
            val content = listOf(title, snippet).filter(String::isNotBlank).joinToString("\n")
            if (content.isBlank()) return@mapNotNullIndexed null
            val id = "web-" + target.hashCode().toUInt().toString(16)
            KnowledgeDocument(
                evidence = KnowledgeEvidence(
                    id = id,
                    title = title,
                    canonicalUrl = target,
                    publisherId = host,
                    domain = query.domain,
                    authority = EvidenceAuthority.UNKNOWN,
                    retrievedAtMs = now,
                    content = content
                ),
                excerpt = if (snippet.isBlank()) title else snippet,
                relevance = 0.25
            )
        }.distinctBy { it.evidence.canonicalUrl }.take(limit)
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
        const val MAX_URL_CHARS = 1_500
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
    fun requiresWebSearch(text: String): Boolean {
        val value = text.lowercase(Locale.ROOT)
        return listOf(
            "busca en internet", "busca en la web", "búscalo en internet",
            "búscalo en la web", "investiga en internet", "investiga en la web",
            "consulta internet", "consulta la web", "busca online",
            "search the web", "search online", "look this up", "look it up online"
        ).any(value::contains)
    }
}

private inline fun <T, R : Any> List<T>.mapNotNullIndexed(
    transform: (index: Int, T) -> R?
): List<R> = buildList {
    forEachIndexed { index, item -> transform(index, item)?.let(::add) }
}
