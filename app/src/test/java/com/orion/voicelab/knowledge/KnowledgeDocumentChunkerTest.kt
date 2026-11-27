package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class KnowledgeDocumentChunkerTest {
    @Test fun longDocumentsSplitIntoBoundedOverlappingPassages() {
        val sentence = "La concurrencia estructurada permite cancelar tareas relacionadas de forma coherente. "
        val content = sentence.repeat(80)
        val chunks = KnowledgeDocumentChunker.split(content, maxChars = 700, overlapChars = 100)
        assertTrue(chunks.size > 2)
        assertTrue(chunks.all { it.text.length <= 700 })
        assertEquals(chunks.indices.toList(), chunks.map { it.index })
        assertTrue(chunks.zipWithNext().all { (left, right) ->
            left.text.takeLast(100).trim() in right.text
        })
    }

    @Test fun shortDocumentRemainsSinglePassage() {
        val chunks = KnowledgeDocumentChunker.split("Texto breve de prueba.")
        assertEquals(1, chunks.size)
        assertEquals("Texto breve de prueba.", chunks.single().text)
    }
}
