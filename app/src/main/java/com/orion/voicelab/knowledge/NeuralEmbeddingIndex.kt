package com.orion.voicelab.knowledge

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted dense passage vectors. Re-indexes only documents whose content fingerprint changed.
 * The sparse semantic index remains available as a fallback when the optional model is absent.
 */
class NeuralEmbeddingIndex(context: Context) {
    private val encoder = OnnxMultilingualEmbeddingEncoder(context)
    private val file = AtomicFile(File(context.applicationContext.filesDir, INDEX_FILE))
    private val lock = Any()
    private var state: MutableMap<String, StoredDocument>? = null

    suspend fun isAvailable(): Boolean = encoder.isAvailable()

    suspend fun embedQuery(query: String): FloatArray? = encoder.encode(query)

    suspend fun ensureDocument(documentId: String, chunks: List<String>, contentHash: String): Boolean {
        if (!encoder.isAvailable()) return false
        synchronized(lock) {
            val existing = allDocuments()[documentId]
            if (existing?.contentHash == contentHash && existing.vectors.size == chunks.size) {
                return true
            }
        }
        val vectors = mutableListOf<FloatArray>()
        for (chunk in chunks) {
            val vector = encoder.encode(chunk) ?: return false
            vectors += vector
        }
        if (vectors.isEmpty() || vectors.any { it.size != vectors.first().size }) return false
        synchronized(lock) {
            allDocuments()[documentId] = StoredDocument(contentHash, vectors)
            persist()
        }
        return true
    }

    fun score(documentId: String, queryVector: FloatArray): Double = synchronized(lock) {
        allDocuments()[documentId]?.vectors?.maxOfOrNull { cosine(queryVector, it) } ?: 0.0
    }

    fun bestChunkIndex(documentId: String, queryVector: FloatArray): Int = synchronized(lock) {
        allDocuments()[documentId]?.vectors?.mapIndexed { index, vector -> index to cosine(queryVector, vector) }
            ?.maxByOrNull { it.second }?.first ?: 0
    }

    fun remove(documentId: String) = synchronized(lock) {
        allDocuments().remove(documentId)
        persist()
    }

    private fun allDocuments(): MutableMap<String, StoredDocument> {
        state?.let { return it }
        val loaded = linkedMapOf<String, StoredDocument>()
        runCatching {
            val root = JSONObject(file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() })
            if (root.optInt("schema") != SCHEMA) return@runCatching
            val documents = root.optJSONArray("documents") ?: JSONArray()
            for (index in 0 until documents.length()) {
                val item = documents.getJSONObject(index)
                val vectors = item.getJSONArray("vectors")
                val parsed = buildList {
                    for (vectorIndex in 0 until vectors.length()) {
                        val values = vectors.getJSONArray(vectorIndex)
                        add(FloatArray(values.length()) { values.getDouble(it).toFloat() })
                    }
                }
                if (parsed.isNotEmpty() && parsed.all { it.size == parsed.first().size }) {
                    loaded[item.getString("id")] = StoredDocument(item.getString("hash"), parsed)
                }
            }
        }
        state = loaded
        return loaded
    }

    private fun persist() {
        val documents = JSONArray()
        allDocuments().forEach { (id, stored) ->
            val vectors = JSONArray()
            stored.vectors.forEach { vector ->
                val values = JSONArray()
                vector.forEach { value -> values.put(value.toDouble()) }
                vectors.put(values)
            }
            documents.put(JSONObject().put("id", id).put("hash", stored.contentHash).put("vectors", vectors))
        }
        val output = file.startWrite()
        try {
            output.write(JSONObject().put("schema", SCHEMA).put("documents", documents)
                .toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun cosine(left: FloatArray, right: FloatArray): Double {
        if (left.size != right.size || left.isEmpty()) return 0.0
        val dot = left.indices.sumOf { left[it].toDouble() * right[it].toDouble() }
        val leftNorm = kotlin.math.sqrt(left.sumOf { it.toDouble() * it.toDouble() })
        val rightNorm = kotlin.math.sqrt(right.sumOf { it.toDouble() * it.toDouble() })
        if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
        return (dot / (leftNorm * rightNorm)).coerceIn(-1.0, 1.0)
    }

    private data class StoredDocument(val contentHash: String, val vectors: List<FloatArray>)

    companion object {
        private const val INDEX_FILE = "orion-neural-embedding-index.json"
        private const val SCHEMA = 1
        fun contentFingerprint(chunks: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            digest.update(NeuralEmbeddingModelManager.MODEL_ID.toByteArray(Charsets.UTF_8))
            chunks.forEach { digest.update(it.toByteArray(Charsets.UTF_8)); digest.update(0.toByte()) }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
