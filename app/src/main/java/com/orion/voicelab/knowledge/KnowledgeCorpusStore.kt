package com.orion.voicelab.knowledge

import android.content.Context
import android.util.AtomicFile
import java.io.File
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
    private val corpusFile = AtomicFile(File(context.applicationContext.filesDir, "orion-knowledge-corpus.json"))
    private val lock = Any()

    override suspend fun search(query: BrainQuery, limit: Int): List<KnowledgeDocument> =
        withContext(Dispatchers.IO) {
            if (limit <= 0) return@withContext emptyList()
            val queryTerms = tokens(query.normalizedText)
            if (queryTerms.isEmpty()) return@withContext emptyList()
            val documents = readAll().filter {
                it.evidence.domain == query.domain &&
                    (query.jurisdiction.isNullOrBlank() ||
                        it.evidence.jurisdiction.equals(query.jurisdiction, ignoreCase = true))
            }
            if (documents.isEmpty()) return@withContext emptyList()
            val fields = documents.map { document ->
                Triple(
                    tokens(document.evidence.title),
                    tokens(document.excerpt),
                    tokens(document.evidence.content)
                )
            }
            val documentFrequency = queryTerms.associateWith { term ->
                fields.count { (title, excerpt, body) -> term in title || term in excerpt || term in body }
            }
            documents.mapIndexedNotNull { index, document ->
                val (title, excerpt, body) = fields[index]
                val matched = queryTerms.count { term -> term in title || term in excerpt || term in body }
                if (matched == 0) return@mapIndexedNotNull null
                val n = documents.size.toDouble()
                val score = queryTerms.sumOf { term ->
                    val df = documentFrequency[term]!!.toDouble()
                    val idf = kotlin.math.ln(1.0 + (n - df + 0.5) / (df + 0.5))
                    val titleTf = title.count { it == term }
                    val excerptTf = excerpt.count { it == term }
                    val bodyTf = body.count { it == term }
                    idf * (3.0 * titleTf + 2.0 * excerptTf + bodyTf)
                }
                val phraseBoost = if (document.evidence.title.contains(query.normalizedText, ignoreCase = true)) 1.5 else 0.0
                val coverage = matched.toDouble() / queryTerms.size
                document.copy(relevance = (score + phraseBoost) * (0.5 + 0.5 * coverage))
            }.sortedWith(
                compareByDescending<KnowledgeDocument> { it.relevance }
                    .thenByDescending { it.evidence.authority.ordinal }
                    .thenByDescending { it.evidence.publishedAtMs ?: 0L }
            ).take(limit)
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
        val raw = runCatching {
            corpusFile.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrDefault("[]")
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
                    val conflicts = item.optJSONArray("conflictsWithIds")?.let { values ->
                        buildSet {
                            for (conflictIndex in 0 until values.length()) {
                                values.optString(conflictIndex).takeIf { it.isNotBlank() }?.let { add(it) }
                            }
                        }
                    }.orEmpty()
                    if (evidence.id.isNotBlank() && evidence.canonicalUrl.startsWith("https://")) {
                        add(KnowledgeDocument(
                            evidence = evidence,
                            excerpt = item.optString("excerpt", evidence.content),
                            conflictsWithIds = conflicts
                        ))
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
                val conflicts = JSONArray()
                document.conflictsWithIds.forEach { conflicts.put(it) }
                put("conflictsWithIds", conflicts)
            })
        }
        var output: java.io.FileOutputStream? = null
        try {
            val stream = corpusFile.startWrite()
            output = stream
            stream.write(array.toString().toByteArray(Charsets.UTF_8))
            corpusFile.finishWrite(stream)
        } catch (error: Exception) {
            output?.let { corpusFile.failWrite(it) }
            throw IllegalStateException("No se pudo guardar el corpus local de conocimiento.", error)
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
        val STOP_WORDS = setOf(
            "que", "con", "para", "por", "una", "uno", "del", "las", "los",
            "como", "desde", "sobre", "esta", "este", "cuando", "donde"
        )
    }
}
