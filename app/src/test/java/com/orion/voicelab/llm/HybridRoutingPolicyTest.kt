package com.orion.voicelab.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class HybridRoutingPolicyTest {
    @Test fun quickRequestsAlwaysStayLocal() {
        assertEquals(InferenceRoute.LOCAL, InferenceRoutingPolicy.decide("lite", "¿Qué hora es?", true).route)
        assertEquals(InferenceRoute.LOCAL, InferenceRoutingPolicy.decide("lite", "Recuérdame comprar leche", true).route)
    }

    @Test fun balancedAndPowerfulProfilesHandleCodeLocally() {
        assertEquals(InferenceRoute.LOCAL, InferenceRoutingPolicy.decide("balanced", "Depura esta función Kotlin", true).route)
        assertEquals(InferenceRoute.LOCAL, InferenceRoutingPolicy.decide("powerful", "Diseña una arquitectura Android", true).route)
    }

    @Test fun liteCanDelegateCodeWhenConsented() {
        assertEquals(InferenceRoute.REMOTE, InferenceRoutingPolicy.decide("lite", "Depura esta función Kotlin", true).route)
        assertEquals(InferenceRoute.LOCAL, InferenceRoutingPolicy.decide("lite", "Depura esta función Kotlin", false).route)
    }

    @Test fun deepResearchAndLongPlansUseRemoteOnlyWithConsent() {
        assertEquals(InferenceRoute.REMOTE, InferenceRoutingPolicy.decide("powerful", "Haz una investigación profunda sobre el tema y contrasta fuentes", true).route)
        assertEquals(InferenceRoute.LOCAL, InferenceRoutingPolicy.decide("powerful", "Haz una investigación profunda sobre el tema y contrasta fuentes", false).route)
        assertEquals(InferenceRoute.REMOTE, InferenceRoutingPolicy.decide("flagship", "Diseña un plan de 90 días con todas las fases", true).route)
    }

    @Test fun expertAnalysisUsesRemoteWhenConfigured() {
        assertEquals(InferenceRoute.REMOTE, InferenceRoutingPolicy.decide("powerful", "Realiza una auditoría exhaustiva de seguridad", true).route)
    }
}
