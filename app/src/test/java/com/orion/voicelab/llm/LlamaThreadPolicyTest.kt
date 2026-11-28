package com.orion.voicelab.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlamaThreadPolicyTest {
    @Test fun neverUsesMoreThreadsThanAvailableProcessors() {
        for (cores in -2..16) {
            val normalized = cores.coerceAtLeast(1)
            val expected = if (normalized == 4) 2 else minOf(4, normalized)
            assertEquals(expected, LlamaThreadPolicy.recommend(cores))
        }
    }

    @Test fun matchesNativeConservativeFourCoreDefault() {
        assertEquals(1, LlamaThreadPolicy.recommend(1))
        assertEquals(2, LlamaThreadPolicy.recommend(2))
        assertEquals(3, LlamaThreadPolicy.recommend(3))
        assertEquals(2, LlamaThreadPolicy.recommend(4))
        assertEquals(4, LlamaThreadPolicy.recommend(8))
    }
}
