package com.orion.voicelab.model

import org.junit.Assert.*
import org.junit.Test

class ModelManagerProfileArtifactTest {
    @Test fun allUpperProfilesHavePinnedHttpsGgufArtifacts() {
        val artifacts = ModelManager.PROFILE_LLM_ARTIFACTS
        assertEquals(setOf("balanced", "powerful", "flagship"), artifacts.keys)
        artifacts.values.forEach { artifact ->
            assertTrue(artifact.downloadUrl.startsWith("https://huggingface.co/"))
            assertTrue(artifact.fileName.endsWith(".gguf"))
            assertEquals(64, artifact.sha256.length)
            assertTrue(artifact.sha256.all { it in "0123456789abcdef" })
            assertTrue(artifact.approximateDownloadBytes > 1_000_000_000L)
            assertTrue(artifact.minimumFreeBytes > artifact.approximateDownloadBytes / 2)
            assertTrue(artifact.contextTokens >= 2048)
            assertTrue(artifact.license.contains("Apache"))
        }
    }

    @Test fun flagshipUsesSmallerContextToReduceNativeMemoryPressure() {
        val balanced = ModelManager.profileArtifact("balanced")!!
        val powerful = ModelManager.profileArtifact("powerful")!!
        val flagship = ModelManager.profileArtifact("flagship")!!
        assertEquals(4096, balanced.contextTokens)
        assertEquals(4096, powerful.contextTokens)
        assertEquals(2048, flagship.contextTokens)
        assertTrue(flagship.minimumFreeBytes > powerful.minimumFreeBytes)
    }

    @Test fun unknownProfileCannotResolveAnArtifact() {
        assertNull(ModelManager.profileArtifact("unknown"))
    }
}
