package com.orion.voicelab.knowledge

import java.net.URL
import org.junit.Assert.*
import org.junit.Test

class NeuralEmbeddingModelManagerTest {
    @Test fun modelSourceIsPinnedAndIntegrityDigestIsDeclared() {
        val url = URL(NeuralEmbeddingModelManager.MODEL_URL)
        assertEquals("https", url.protocol)
        assertEquals("huggingface.co", url.host)
        assertTrue(url.path.contains("/resolve/469261b/"))
        assertTrue(NeuralEmbeddingModelManager.MODEL_SHA256.matches(Regex("[a-f0-9]{64}")))
        assertEquals("MIT", NeuralEmbeddingModelManager.MODEL_LICENSE)
    }

    @Test fun optionalModelIsNotBundledAndHasBoundedDownloadSize() {
        assertTrue(NeuralEmbeddingModelManager.MODEL_SIZE_LABEL.isNotBlank())
        assertTrue(NeuralEmbeddingModelManager.MAX_MODEL_BYTES < 200L * 1024L * 1024L)
        assertTrue(NeuralEmbeddingModelManager.MIN_MODEL_BYTES < NeuralEmbeddingModelManager.MAX_MODEL_BYTES)
    }

    @Test fun neuralIndexFingerprintChangesWhenAnyPassageChanges() {
        val first = NeuralEmbeddingIndex.contentFingerprint(listOf("texto uno", "texto dos"))
        val same = NeuralEmbeddingIndex.contentFingerprint(listOf("texto uno", "texto dos"))
        val changed = NeuralEmbeddingIndex.contentFingerprint(listOf("texto uno", "texto distinto"))
        assertEquals(first, same)
        assertNotEquals(first, changed)
        assertEquals(64, first.length)
    }
}
