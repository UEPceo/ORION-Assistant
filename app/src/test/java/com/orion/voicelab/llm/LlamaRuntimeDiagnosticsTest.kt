package com.orion.voicelab.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LlamaRuntimeDiagnosticsTest {
    @Test fun reportsArm64AndConservativeThreadCount() {
        val snapshot = LlamaRuntimeDiagnostics.snapshot(arrayOf("arm64-v8a", "x86_64"), 8)
        assertEquals("arm64-v8a", snapshot.abi)
        assertEquals(8, snapshot.availableProcessors)
        assertEquals(4, snapshot.configuredThreads)
        assertEquals("CPU", snapshot.backend)
    }

    @Test fun neverReportsZeroThreadsOrUnknownAbiAsNull() {
        val snapshot = LlamaRuntimeDiagnostics.snapshot(emptyArray(), 0)
        assertEquals("desconocida", snapshot.abi)
        assertEquals(1, snapshot.availableProcessors)
        assertEquals(1, snapshot.configuredThreads)
    }

    @Test fun doesNotClaimSpecificDotprodOrI8mmKernelWasSelected() {
        assertTrue(LlamaRuntimeDiagnostics.snapshot(arrayOf("arm64-v8a"), 4)
            .dispatchDescription.contains("automática"))
    }
}
