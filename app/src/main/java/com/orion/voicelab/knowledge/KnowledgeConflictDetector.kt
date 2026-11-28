package com.orion.voicelab.knowledge

import java.text.Normalizer
import java.util.Locale

/** Conservative detector: only warns about numeric discrepancies tied to the same named fact. */
object KnowledgeConflictDetector {
    private val anchors = listOf("talla", "minimo", "minima", "maximo", "maxima", "cupo", "plazo", "tasa", "precio", "temperatura", "velocidad", "altura", "distancia", "cantidad")
    private val stop = setOf("para", "con", "por", "una", "uno", "las", "los", "del", "desde", "sobre", "entre", "que", "como", "segun", "segun", "actual", "consulta", "guia", "informacion")

    fun detect(documents: List<KnowledgeDocument>): Set<Pair<String, String>> {
        val pairs = linkedSetOf<Pair<String, String>>()
        documents.forEach { doc ->
            doc.conflictsWithIds.filter { target -> documents.any { it.evidence.id == target } }
                .forEach { target -> pairs += ordered(doc.evidence.id, target) }
        }
        for (i in documents.indices) for (j in i + 1 until documents.size) {
            val left = documents[i].evidence
            val right = documents[j].evidence
            if (left.publisherId.equals(right.publisherId, ignoreCase = true)) continue
            if (left.domain != right.domain || titleSimilarity(left.title, right.title) < 0.38) continue
            val leftFacts = factValues(left.content)
            val rightFacts = factValues(right.content)
            val sharedAnchors = leftFacts.keys.intersect(rightFacts.keys)
            val mismatch = sharedAnchors.any { anchor ->
                val lv = leftFacts[anchor].orEmpty()
                val rv = rightFacts[anchor].orEmpty()
                lv.isNotEmpty() && rv.isNotEmpty() && lv != rv
            }
            if (mismatch) pairs += ordered(left.id, right.id)
        }
        return pairs
    }

    private fun factValues(content: String): Map<String, Set<String>> {
        val normalized = normalize(content)
        return anchors.associateWith { anchor ->
            Regex("""\b$anchor\b[^.\n]{0,80}?(\d+(?:[.,]\d+)?\s*(?:cm|mm|m|kg|g|%|dias?|mes(?:es)?|euros?|€|km/h)?)""")
                .findAll(normalized).map { it.groupValues[1].replace(" ", "") }.toSet()
        }.filterValues { it.isNotEmpty() }
    }

    private fun titleSimilarity(a: String, b: String): Double {
        val left = normalize(a).split(Regex("\\s+")).filter { it.length >= 3 && it !in stop }.toSet()
        val right = normalize(b).split(Regex("\\s+")).filter { it.length >= 3 && it !in stop }.toSet()
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size
    }

    private fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .replace(Regex("[^a-z0-9%€.,\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun ordered(a: String, b: String): Pair<String, String> = if (a <= b) a to b else b to a
}
