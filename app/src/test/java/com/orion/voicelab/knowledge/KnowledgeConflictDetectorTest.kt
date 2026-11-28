package com.orion.voicelab.knowledge

import org.junit.Assert.*
import org.junit.Test

class KnowledgeConflictDetectorTest {
    private fun document(id:String,publisher:String,title:String,content:String)=KnowledgeDocument(
        evidence=KnowledgeEvidence(id,title,"https://example.org/$id",publisher,KnowledgeDomain.FISHING,EvidenceAuthority.REPUTABLE_SECONDARY,1L,content=content)
    )

    @Test fun flagsPossibleDifferentNumericClaimsForSameTopicAcrossPublishers() {
        val docs=listOf(
            document("a","source-a","Talla mínima de lubina recreativa","La talla mínima de lubina es 42 cm."),
            document("b","source-b","Talla mínima lubina Andalucía","La talla mínima de lubina es 36 cm.")
        )
        assertEquals(setOf("a" to "b"),KnowledgeConflictDetector.detect(docs))
    }

    @Test fun doesNotFlagDifferentPublishersWithoutSameAnchoredFactOrSamePublisher() {
        val unrelated=listOf(
            document("a","source-a","Talla mínima de lubina","La talla mínima es 42 cm."),
            document("b","source-b","Cebo recomendado para lubina","El precio es 9 euros.")
        )
        assertTrue(KnowledgeConflictDetector.detect(unrelated).isEmpty())
        val samePublisher=listOf(
            document("a","same","Talla mínima de lubina","La talla mínima es 42 cm."),
            document("b","same","Talla mínima de lubina","La talla mínima es 36 cm.")
        )
        assertTrue(KnowledgeConflictDetector.detect(samePublisher).isEmpty())
    }
}
