package com.orion.voicelab.knowledge

import android.content.Context
import android.util.AtomicFile
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
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
    private val semanticIndex = KnowledgeSemanticIndex(context)
    private val neuralIndex = NeuralEmbeddingIndex(context)

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
            val chunksByDocument = documents.associate { document ->
                document.evidence.id to KnowledgeDocumentChunker.split(document.evidence.content)
                    .map { it.text }.ifEmpty { listOf(document.evidence.content) }
            }
            val fields = documents.map { document ->
                Triple(
                    tokens(document.evidence.title),
                    tokens(document.excerpt),
                    chunksByDocument[document.evidence.id].orEmpty().map(::tokens).flatten()
                )
            }
            if (documents.any { !semanticIndex.hasDocument(it.evidence.id) }) {
                semanticIndex.rebuild(documents.map { it.evidence.id to chunksByDocument[it.evidence.id].orEmpty() })
            }
            // Dense vectors are generated only when the optional, verified ONNX model is installed.
            val neuralQuery = runCatching {
                if (!neuralIndex.isAvailable()) return@runCatching null
                documents.forEach { document ->
                    val chunks = chunksByDocument[document.evidence.id].orEmpty()
                    neuralIndex.ensureDocument(document.evidence.id, chunks, NeuralEmbeddingIndex.contentFingerprint(chunks))
                }
                neuralIndex.embedQuery(query.normalizedText)
            }.getOrNull()
            val documentFrequency = queryTerms.associateWith { term ->
                fields.count { (title, excerpt, body) -> term in title || term in excerpt || term in body }
            }
            documents.mapIndexedNotNull { index, document ->
                val (title, excerpt, body) = fields[index]
                val chunks = chunksByDocument[document.evidence.id].orEmpty()
                val lexicalBestChunk = chunks.maxByOrNull { chunk ->
                    val chunkTerms = tokens(chunk)
                    queryTerms.count { it in chunkTerms }.toDouble() / queryTerms.size
                } ?: document.evidence.content
                val bestChunk = if (neuralQuery != null) {
                    val neuralBestIndex = runCatching { neuralIndex.bestChunkIndex(document.evidence.id, neuralQuery) }.getOrDefault(-1)
                    chunks.getOrNull(neuralBestIndex) ?: lexicalBestChunk
                } else lexicalBestChunk
                val matched = queryTerms.count { term -> term in title || term in excerpt || term in body }
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
                val lexical = ((score + phraseBoost) / (score + phraseBoost + 8.0)).coerceIn(0.0, 1.0)
                val semantic = semanticIndex.score(document.evidence.id, query.normalizedText)
                val neural = neuralQuery?.let { vector -> runCatching { neuralIndex.score(document.evidence.id, vector) }.getOrDefault(0.0) } ?: 0.0
                if (matched == 0 && semantic <= 0.01 && neural <= 0.01) return@mapIndexedNotNull null
                val combined = if (neuralQuery != null) {
                    0.25 * lexical + 0.25 * semantic + 0.50 * neural
                } else {
                    0.55 * lexical + 0.45 * semantic
                }
                document.copy(
                    excerpt = bestChunk.take(1_600),
                    relevance = combined * (0.65 + 0.35 * coverage)
                )
            }.sortedWith(
                compareByDescending<KnowledgeDocument> { it.relevance }
                    .thenByDescending { authorityWeight(it.evidence.authority) }
                    .thenByDescending { it.evidence.publishedAtMs ?: it.evidence.retrievedAtMs }
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
                semanticIndex.upsert(
                    document.evidence.id,
                    KnowledgeDocumentChunker.split(document.evidence.content).map { it.text }
                        .ifEmpty { listOf(document.evidence.content) }
                )
            }
        }
    }

    override suspend fun contains(documentId: String): Boolean = withContext(Dispatchers.IO) {
        readAll().any { it.evidence.id == documentId }
    }

    override suspend fun remove(documentId: String) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                writeAll(readAll().filterNot { it.evidence.id == documentId })
                semanticIndex.remove(documentId)
                neuralIndex.remove(documentId)
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
                    if (evidence.id.isNotBlank() && evidence.canonicalUrl.startsWith("https://") &&
                        (evidence.contentHash == null || evidence.contentHash == sha256(evidence.content))) {
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

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun authorityWeight(authority: EvidenceAuthority): Int = when (authority) {
        EvidenceAuthority.OFFICIAL_PRIMARY -> 7
        EvidenceAuthority.PRIMARY_TECHNICAL -> 6
        EvidenceAuthority.PEER_REVIEWED -> 5
        EvidenceAuthority.REPUTABLE_SECONDARY -> 4
        EvidenceAuthority.COMMUNITY -> 2
        EvidenceAuthority.USER_SUPPLIED -> 1
        EvidenceAuthority.UNKNOWN -> 0
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
