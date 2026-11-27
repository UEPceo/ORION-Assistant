package com.orion.voicelab.knowledge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Private, device-local knowledge corpus. It stores source-backed documents only.
 * No cloud sync, model-output ingestion, or hidden background network access.
 */
class KnowledgeCorpusStore(context: Context) : KnowledgeSource, KnowledgeRepository {
    override val sourceId: String = "orion-local-corpus"
    private val preferences = context.applicationContext.getSharedPreferences(
        "orion_knowledge_corpus_v1",
        Context.MODE_PRIVATE
    )
    private val lock = Any()

    override suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext emptyList()
            val terms = tokens(query.normalizedText)
            readAll().asSequence()
                .filter { it.evidence.domain == query.domain }
                .filter {
                    query.jurisdiction.isNullOrBlank() ||
                        it.evidence.jurisdiction.equals(query.jurisdiction, ignoreCase = true)
                }
                .map { document ->
                    val sourceTerms = tokens(
                        "${document.evidence.title} ${document.evidence.content} ${document.excerpt}"
                    )
                    val overlap = terms.intersect(sourceTerms).size
                    val relevance = if (terms.isEmpty()) 0.0 else {
                        overlap.toDouble() / terms.size.toDouble()
                    }
                    document.copy(relevance = relevance)
                }
                .filter { it.relevance > 0.0 }
                .sortedByDescending { it.relevance }
                .take(limit)
                .toList()
        }

    override suspend fun upsert(document: KnowledgeDocument) {
        withContext(Dispatchers.IO) {
            require(document.evidence.id.isNotBlank())
            require(document.evidence.canonicalUrl.startsWith("https://"))
            require(document.evidence.content.isNotBlank())
            synchronized(lock) {
                val all = readAll().associateBy { it.evidence.id }.toMutableMap()
                all[document.evidence.id] = document
                writeAll(all.values.toList())
            }
        }
    }

    override suspend fun remove(documentId: String) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                writeAll(readAll().filterNot { it.evidence.id == documentId })
            }
        }
    }

    private fun readAll(): List<KnowledgeDocument> = synchronized(lock) {
        val raw = preferences.getString(KEY_DOCUMENTS, "[]") ?: "[]"
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val authority = runCatching {
                        EvidenceAuthority.valueOf(item.optString("authority"))
                    }.getOrDefault(EvidenceAuthority.UNKNOWN)
                    val domain = runCatching {
                        KnowledgeDomain.valueOf(item.optString("domain"))
                    }.getOrDefault(KnowledgeDomain.GENERAL)
                    val evidence = KnowledgeEvidence(
                        id = item.optString("id"),
                        title = item.optString("title"),
                        canonicalUrl = item.optString("url"),
                        publisherId = item.optString("publisher"),
                        domain = domain,
                        authority = authority,
                        retrievedAtMs = item.optLong("retrievedAt"),
                        publishedAtMs = item.takeIf { it.has("publishedAt") }?.optLong("publishedAt"),
                        jurisdiction = item.optString("jurisdiction").takeIf(String::isNotBlank),
                        content = item.optString("content"),
                        contentHash = item.optString("contentHash").takeIf(String::isNotBlank)
                    )
                    if (evidence.id.isNotBlank() && evidence.canonicalUrl.startsWith("https://")) {
                        add(KnowledgeDocument(evidence, item.optString("excerpt", evidence.content)))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeAll(documents: List<KnowledgeDocument>) {
        val array = JSONArray()
        documents.forEach { document ->
            val evidence = document.evidence
            array.put(JSONObject().apply {
                put("id", evidence.id)
                put("title", evidence.title)
                put("url", evidence.canonicalUrl)
                put("publisher", evidence.publisherId)
                put("domain", evidence.domain.name)
                put("authority", evidence.authority.name)
                put("retrievedAt", evidence.retrievedAtMs)
                evidence.publishedAtMs?.let { put("publishedAt", it) }
                evidence.jurisdiction?.let { put("jurisdiction", it) }
                put("content", evidence.content)
                evidence.contentHash?.let { put("contentHash", it) }
                put("excerpt", document.excerpt)
            })
        }
        check(preferences.edit().putString(KEY_DOCUMENTS, array.toString()).commit()) {
            "No se pudo guardar el corpus local de conocimiento."
        }
    }

    private fun tokens(value: String): Set<String> {
        val normalized = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
        return normalized.split(Regex("\\s+"))
            .filter { it.length >= 3 && it !in STOP_WORDS }
            .toSet()
    }

    private companion object {
        const val KEY_DOCUMENTS = "documents"
        val STOP_WORDS = setOf(
            "que", "con", "para", "por", "una", "uno", "del", "las", "los",
            "como", "desde", "sobre", "esta", "este", "cuando", "donde"
        )
    }
}
