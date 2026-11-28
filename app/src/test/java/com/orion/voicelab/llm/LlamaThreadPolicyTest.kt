package com.orion.voicelab.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class LlamaThreadPolicyTest {
    @Test fun neverUsesMoreThreadsThanAvailableProcessors() {
        for (cores in -2..16) {
            assertEquals(
                minOf(4, cores.coerceAtLeast(1)),
                LlamaThreadPolicy.recommend(cores).let { if (cores == 4) 2 else it }
            )
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
