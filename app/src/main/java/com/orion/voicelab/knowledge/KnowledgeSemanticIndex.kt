package com.orion.voicelab.knowledge

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.sqrt

/**
 * Deterministic on-device sparse semantic index.
 * It combines normalized terms, character n-grams and a curated multilingual concept map.
 * It is intentionally transparent and requires no embedding model or network access.
 */
class KnowledgeSemanticIndex(context: Context) {
    private val file = AtomicFile(File(context.applicationContext.filesDir, "orion-semantic-index.json"))
    private val lock = Any()
    private var vectors: MutableMap<String, Map<String, Double>>? = null

    fun upsert(documentId: String, chunks: List<String>) = synchronized(lock) {
        val vector = buildVector(chunks.joinToString(" "))
        allVectors()[documentId] = vector
        persist()
    }

    fun remove(documentId: String) = synchronized(lock) {
        allVectors().remove(documentId)
        persist()
    }

    fun hasDocument(documentId: String): Boolean = synchronized(lock) {
        allVectors().keys.any { it.startsWith(documentId + "::") || it == documentId }
    }

    fun score(documentId: String, query: String): Double = synchronized(lock) {
        cosine(buildVector(query), allVectors()[documentId].orEmpty())
    }

    fun rebuild(documents: List<Pair<String, List<String>>>) = synchronized(lock) {
        val next = linkedMapOf<String, Map<String, Double>>()
        documents.forEach { (id, chunks) -> next[id] = buildVector(chunks.joinToString(" ")) }
        vectors = next
        persist()
    }

    private fun allVectors(): MutableMap<String, Map<String, Double>> {
        vectors?.let { return it }
        val loaded = linkedMapOf<String, Map<String, Double>>()
        runCatching {
            val raw = file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(raw)
            val items = root.optJSONArray("vectors") ?: JSONArray()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val values = item.getJSONObject("values")
                val vector = buildMap {
                    values.keys().forEach { key -> put(key, values.optDouble(key)) }
                }
                loaded[item.getString("id")] = vector
            }
        }
        vectors = loaded
        return loaded
    }

    private fun persist() {
        val array = JSONArray()
        allVectors().forEach { (id, vector) ->
            val values = JSONObject()
            vector.forEach { (key, value) -> values.put(key, value) }
            array.put(JSONObject().put("id", id).put("values", values))
        }
        val output = file.startWrite()
        try {
            output.write(JSONObject().put("schema", 1).put("vectors", array).toString().toByteArray(Charsets.UTF_8))
            file.finishWrite(output)
        } catch (error: Exception) {
            file.failWrite(output)
            throw error
        }
    }

    private fun buildVector(text: String): Map<String, Double> {
        val tokens = normalize(text).split(Regex("\\s+")).filter { it.length > 2 }
        val weights = linkedMapOf<String, Double>()
        tokens.forEach { token ->
            weights["w:$token"] = (weights["w:$token"] ?: 0.0) + 1.0
            token.windowed(3, 1, partialWindows = false).forEach { gram ->
                weights["c:$gram"] = (weights["c:$gram"] ?: 0.0) + 0.12
            }
            CONCEPTS.forEach { (concept, aliases) ->
                if (token in aliases || token == concept) {
                    weights["s:$concept"] = (weights["s:$concept"] ?: 0.0) + 1.5
                }
            }
        }
        return weights
    }

    private fun normalize(value: String): String {
        val decomposed = java.text.Normalizer.normalize(value.lowercase(), java.text.Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ").trim()
    }

    private fun cosine(left: Map<String, Double>, right: Map<String, Double>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val dot = left.entries.sumOf { (key, value) -> value * (right[key] ?: 0.0) }
        val leftNorm = sqrt(left.values.sumOf { it * it })
        val rightNorm = sqrt(right.values.sumOf { it * it })
        return if (leftNorm == 0.0 || rightNorm == 0.0) 0.0 else (dot / (leftNorm * rightNorm)).coerceIn(0.0, 1.0)
    }

    private companion object {
        val CONCEPTS = mapOf(
            "authentication" to setOf("login", "signin", "credential", "password", "contrasena", "autenticacion", "identidad", "clave", "acceso", "credencial", "pin"),
            "security" to setOf("seguridad", "proteccion", "defensa", "vulnerabilidad", "amenaza", "ataque", "ciberseguridad"),
            "emergency" to setOf("emergencia", "urgencia", "auxilio", "112", "socorro", "accidente"),
            "law" to setOf("ley", "normativa", "legislacion", "reglamento", "decreto", "legal", "juridico"),
            "fishing" to setOf("pesca", "pescador", "captura", "anzuelo", "cebo", "surfcasting", "licencia"),
            "programming" to setOf("programacion", "codigo", "desarrollo", "software", "algoritmo", "kotlin", "python"),
            "web" to setOf("internet", "http", "https", "servidor", "navegador", "api", "web"),
            "concurrency" to setOf("coroutine", "coroutines", "concurrencia", "paralelo", "asincrono", "suspend"),
            "finance" to setOf("finanzas", "dinero", "inversion", "presupuesto", "coste", "ingresos", "gastos")
        )
    }
}
