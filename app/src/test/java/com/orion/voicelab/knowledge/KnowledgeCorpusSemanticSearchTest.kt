package com.orion.voicelab.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KnowledgeCorpusSemanticSearchTest {
    @Test fun retrievesCredentialDocumentForMeaningEquivalentSpanishQuery() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = KnowledgeCorpusStore(context)
        val now = System.currentTimeMillis()
        val evidence = KnowledgeEvidence(
            id = "credential-guide",
            title = "Gestión segura de contraseñas",
            canonicalUrl = "https://incibe.es/guia-credenciales",
            publisherId = "incibe.es",
            domain = KnowledgeDomain.CYBERSECURITY,
            authority = EvidenceAuthority.OFFICIAL_PRIMARY,
            retrievedAtMs = now,
            jurisdiction = "ES",
            content = "Las credenciales y contraseñas protegen el acceso a las cuentas. Utiliza autenticación multifactor y gestores de contraseñas."
        )
        store.upsert(KnowledgeDocument(evidence, excerpt = "Guía de credenciales y autenticación"))
        val results = store.search(BrainQueryRouter.route("clave de acceso", "ES"), 5)
        assertTrue(results.any { it.evidence.id == "credential-guide" })
    }
}
