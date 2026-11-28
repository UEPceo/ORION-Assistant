package com.orion.voicelab.knowledge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KnowledgeSemanticIndexTest {
    @Test fun spanishSynonymsShareSemanticConceptVector() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val index = KnowledgeSemanticIndex(context)
        index.upsert("auth-doc", listOf("Gestionar password y credenciales para iniciar sesión"))
        assertTrue(index.score("auth-doc", "clave de acceso") > 0.05)
    }

    @Test fun vectorsPersistAcrossIndexInstancesAndCanBeRemoved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        KnowledgeSemanticIndex(context).upsert("security-doc", listOf("proteger contraseña y autenticación"))
        val restored = KnowledgeSemanticIndex(context)
        assertTrue(restored.hasDocument("security-doc"))
        assertTrue(restored.score("security-doc", "seguridad de credenciales") > 0.0)
        restored.remove("security-doc")
        assertFalse(restored.hasDocument("security-doc"))
    }
    @Test fun associativeGraphImprovesCrossDomainRecall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val index = KnowledgeSemanticIndex(context)
        index.upsert("weather-fishing-bridge", listOf("meteorología, viento y oleaje en la costa"))
        assertTrue(index.score("weather-fishing-bridge", "pesca surfcasting") > 0.0)
        index.remove("weather-fishing-bridge")
    }

}
