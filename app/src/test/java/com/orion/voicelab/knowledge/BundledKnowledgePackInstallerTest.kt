package com.orion.voicelab.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BundledKnowledgePackInstallerTest {
    @Test fun installsAllBundledPacksIntoPrivateCorpusAndIsIdempotent() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KnowledgeCorpusStore(context)
        val ingestion = KnowledgeIngestionService(store, now = { 1795800000000L })
        val installer = BundledKnowledgePackInstaller(context, ingestion, store, now = { 1795800000000L })

        val first = installer.installIfNeeded()
        assertTrue("Fallos de instalación: ${first.failedPackIds}", first.failedPackIds.isEmpty())
        assertEquals(6, first.installedPackIds.size)
        assertTrue(first.installedDocuments >= 10)
        assertEquals(6, installer.installedPackIds().size)

        val second = installer.installIfNeeded()
        assertTrue(second.failedPackIds.isEmpty())
        assertEquals(0, second.installedDocuments)
        assertEquals(6, second.skippedPackIds.size)

        val query = BrainQueryRouter.route("Kotlin coroutine dispatcher", null)
        val results = store.search(query, 10)
        assertTrue(results.any { it.evidence.title.contains("Coroutines", ignoreCase = true) })

        val savedId = results.first().evidence.id
        store.remove(savedId)
        val repaired = installer.installIfNeeded()
        assertTrue(repaired.failedPackIds.isEmpty())
        assertTrue(repaired.installedDocuments > 0)
        assertTrue(store.contains(savedId))
    }

    @Test fun packContentHasSourceAttributionAndNonTrivialText() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KnowledgePackCatalog.packs.forEach { pack ->
            val raw = context.assets.open("knowledge_packs/${pack.id}.json")
                .bufferedReader().use { it.readText() }
            val json = org.json.JSONObject(raw)
            val documents = json.getJSONArray("documents")
            assertTrue(documents.length() > 0)
            for (index in 0 until documents.length()) {
                val item = documents.getJSONObject(index)
                assertTrue(item.getString("url").startsWith("https://"))
                assertTrue(item.getString("publisher").isNotBlank())
                assertTrue(item.getString("content").length >= 180)
            }
        }
    }
}
