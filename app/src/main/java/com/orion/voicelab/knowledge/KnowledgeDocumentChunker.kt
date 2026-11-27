package com.orion.voicelab.knowledge

/** Splits long source documents into bounded, overlapping retrieval passages. */
object KnowledgeDocumentChunker {
    data class Chunk(val index: Int, val text: String)

    fun split(content: String, maxChars: Int = 1_200, overlapChars: Int = 160): List<Chunk> {
        require(maxChars >= 300)
        require(overlapChars in 0 until maxChars)
        val normalized = content.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChars) return listOf(Chunk(0, normalized))

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            var end = (start + maxChars).coerceAtMost(normalized.length)
            if (end < normalized.length) {
                val lowerBound = start + (maxChars * 0.65).toInt()
                val paragraphBreak = normalized.lastIndexOf("\n\n", end).takeIf { it >= lowerBound }
                val sentenceBreak = normalized.lastIndexOfAny(charArrayOf('.', '!', '?'), end - 1)
                    .takeIf { it >= lowerBound }
                val wordBreak = normalized.lastIndexOf(' ', end).takeIf { it >= lowerBound }
                end = paragraphBreak?.plus(2) ?: sentenceBreak?.plus(1) ?: wordBreak ?: end
            }
            if (end <= start) end = (start + maxChars).coerceAtMost(normalized.length)
            chunks += normalized.substring(start, end).trim()
            if (end == normalized.length) break
            start = (end - overlapChars).coerceAtLeast(start + 1)
        }
        return chunks.mapIndexed { index, text -> Chunk(index, text) }
    }
}
