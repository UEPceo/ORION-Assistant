package com.orion.voicelab.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class KnowledgePackUpdateManifestTest {
    @Test fun updateManifestMatchesEveryBundledUpdatePayloadAndDigest() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manifest = context.assets.open("knowledge_packs/updates/manifest.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        assertEquals(1, manifest.getInt("schema"))
        val packages = manifest.getJSONArray("packages")
        assertEquals(KnowledgePackCatalog.packs.size, packages.length())

        for (index in 0 until packages.length()) {
            val item = packages.getJSONObject(index)
            val id = item.getString("id")
            assertTrue(KnowledgePackCatalog.packs.any { it.id == id })
            val url = java.net.URL(item.getString("url"))
            assertEquals("https", url.protocol)
            assertEquals("raw.githubusercontent.com", url.host)
            assertTrue(url.path.endsWith("/knowledge_packs/updates/$id.json"))

            val bytes = context.assets.open("knowledge_packs/updates/$id.json").use { it.readBytes() }
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
            assertEquals(item.getString("sha256"), digest)
            val payload = JSONObject(bytes.toString(Charsets.UTF_8))
            assertEquals(id, payload.getString("id"))
            assertEquals(item.getInt("version"), payload.getInt("version"))
        }
    }
}
