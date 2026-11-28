package com.orion.voicelab.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRoutingPolicyTest {
    @Test fun localModeNeverRoutesRemotely() {
        assertFalse(HybridRoutingPolicy.shouldUseRemote(RemoteInferenceSettings.MODE_LOCAL, "auditoría de código"))
    }

    @Test fun autoModeRoutesComplexRequests() {
        assertTrue(HybridRoutingPolicy.shouldUseRemote(RemoteInferenceSettings.MODE_AUTO, "Analiza la arquitectura y depura este proyecto"))
        assertTrue(HybridRoutingPolicy.shouldUseRemote(RemoteInferenceSettings.MODE_AUTO, "x".repeat(900)))
    }

    @Test fun autoModeKeepsSimpleRequestsLocal() {
        assertFalse(HybridRoutingPolicy.shouldUseRemote(RemoteInferenceSettings.MODE_AUTO, "¿Qué hora es?"))
        assertFalse(HybridRoutingPolicy.shouldUseRemote(RemoteInferenceSettings.MODE_AUTO, "Recuérdame comprar leche"))
    }

    @Test fun remoteOnlyRoutesEveryRequest() {
        assertTrue(HybridRoutingPolicy.shouldUseRemote(RemoteInferenceSettings.MODE_REMOTE_ONLY, "Hola"))
    }
}
