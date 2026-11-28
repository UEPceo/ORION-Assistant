package com.orion.voicelab.llm

import org.junit.Assert.*
import org.junit.Test

class ModelMemoryPolicyTest {
    private val mib = 1024L * 1024L

    @Test fun balancedModelPassesOnlyWhenFreeRamHasRuntimeHeadroom() {
        val modelBytes = 2_100L * mib
        val required = ModelMemoryPolicy.estimateRequiredBytes(modelBytes, 4096)
        assertTrue(ModelMemoryPolicy.evaluate(
            ModelMemorySnapshot(8_000L * mib, required + 600L * mib),
            modelBytes,
            4096
        ).allowed)
        assertFalse(ModelMemoryPolicy.evaluate(
            ModelMemorySnapshot(8_000L * mib, required + 100L * mib),
            modelBytes,
            4096
        ).allowed)
    }

    @Test fun rejectsUnknownAndImpossibleMemorySnapshots() {
        val modelBytes = 1_000L * mib
        assertFalse(ModelMemoryPolicy.evaluate(
            ModelMemorySnapshot(0L, 0L), modelBytes, 2048
        ).allowed)
        assertFalse(ModelMemoryPolicy.evaluate(
            ModelMemorySnapshot(4_000L * mib, 5_000L * mib), modelBytes, 2048
        ).allowed)
        assertFalse(ModelMemoryPolicy.evaluate(
            ModelMemorySnapshot(4_000L * mib, -1L), modelBytes, 2048
        ).allowed)
    }

    @Test fun largerModelAndContextRequireMoreMemory() {
        val small = ModelMemoryPolicy.estimateRequiredBytes(1_000L * mib, 2048)
        val large = ModelMemoryPolicy.estimateRequiredBytes(4_500L * mib, 8192)
        assertTrue(large > small)
    }

    @Test fun contextIsReducedInSafeStepsAndNeverExpanded() {
        assertEquals(2048, ModelMemoryPolicy.safeContextTokens(8192, 900L * mib))
        assertEquals(512, ModelMemoryPolicy.safeContextTokens(8192, 800L * mib))
        assertEquals(0, ModelMemoryPolicy.safeContextTokens(8192, 100L * mib))
        assertEquals(2048, ModelMemoryPolicy.safeContextTokens(2048, 2_000L * mib))
    }

    @Test fun choosesLargestContextThatFitsAndRejectsWhenNoContextFits() {
        val modelBytes = 1_000L * mib
        val snapshot = ModelMemorySnapshot(4_000L * mib, 3_400L * mib)
        val chosen = ModelMemoryPolicy.chooseSafeContextTokens(snapshot, modelBytes, 4096)
        assertTrue(chosen in setOf(512, 1024, 2048, 4096))
        assertTrue(ModelMemoryPolicy.evaluate(snapshot, modelBytes, chosen).allowed)
        assertEquals(0, ModelMemoryPolicy.chooseSafeContextTokens(
            ModelMemorySnapshot(2_000L * mib, 200L * mib), 1_000L * mib, 4096
        ))
    }

    @Test fun invalidContextAndModelSizesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            ModelMemoryPolicy.estimateRequiredBytes(0L, 2048)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ModelMemoryPolicy.estimateRequiredBytes(1L, 100)
        }
    }
}
