package com.orion.voicelab.knowledge

/** Splits long source documents into overlapping, citation-preserving retrieval passages. */
object KnowledgeDocumentChunker {
    data class Chunk(val index: Int, val text: String)

    fun split(content: String, maxChars: Int = 1_200, overlapChars: Int = 160): List<Chunk> {
        require(maxChars >= 300)
        require(overlapChars in 0 until maxChars)
        val normalized = content.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChars) return listOf(Chunk(0, normalized))

        val paragraphs = normalized.split(Regex("\\n\\s*\\n"))
            .flatMap { paragraph ->
                if (paragraph.length <= maxChars) listOf(paragraph.trim())
                else paragraph.split(Regex("(?<=[.!?])\\s+"))
            }.filter(String::isNotBlank)

        val result = mutableListOf<Chunk>()
        var current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty()) {
                result += current.toString().trim()
                current = StringBuilder()
            }
        }
        paragraphs.forEach { paragraph ->
            if (paragraph.length > maxChars) {
                flush()
                var start = 0
                while (start < paragraph.length) {
                    val end = (start + maxChars).coerceAtMost(paragraph.length)
                    result += paragraph.substring(start, end).trim()
                    if (end == paragraph.length) break
                    start = (end - overlapChars).coerceAtLeast(start + 1)
                }
            } else if (current.length + paragraph.length + 2 > maxChars) {
                flush()
                current.append(paragraph)
            } else {
                if (current.isNotEmpty()) current.append("\n\n")
                current.append(paragraph)
            }
        }
        flush()
        return result.mapIndexed { index, text -> Chunk(index, text) }
    }
}
