package com.orion.voicelab.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KnowledgeCorpusRankingTest {
    @Test fun officialEvidenceWinsAnEqualRelevanceTie() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KnowledgeCorpusStore(context)
        val now = System.currentTimeMillis()
        val unknown = KnowledgeEvidence("unknown", "Lubina y pesca", "https://blog.example.org/lubina", "blog.example.org",
            KnowledgeDomain.SURFCASTING, EvidenceAuthority.UNKNOWN, now, content = "Consejos para lubina.")
        val official = KnowledgeEvidence("official", "Lubina y pesca", "https://www.juntadeandalucia.es/lubina", "juntadeandalucia.es",
            KnowledgeDomain.SURFCASTING, EvidenceAuthority.OFFICIAL_PRIMARY, now, content = "Información sobre lubina.")
        store.upsert(KnowledgeDocument(unknown, "Lubina pesca"))
        store.upsert(KnowledgeDocument(official, "Lubina pesca"))
        val results = store.search(BrainQueryRouter.route("lubina", "ES-AN"), 5)
        assertEquals("official", results.first().evidence.id)
    }
}
