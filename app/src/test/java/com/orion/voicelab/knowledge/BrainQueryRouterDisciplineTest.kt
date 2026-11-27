package com.orion.voicelab.knowledge

import org.junit.Assert.assertEquals
import org.junit.Test

class BrainQueryRouterDisciplineTest {
    @Test fun routesMathematicsQueries() {
        assertEquals(KnowledgeDomain.MATHEMATICS, BrainQueryRouter.route("Explícame las derivadas").domain)
        assertEquals(KnowledgeDomain.MATHEMATICS, BrainQueryRouter.route("Cómo calcular una integral").domain)
    }

    @Test fun routesScienceQueries() {
        assertEquals(KnowledgeDomain.SCIENCE, BrainQueryRouter.route("Qué es una hipótesis científica").domain)
        assertEquals(KnowledgeDomain.SCIENCE, BrainQueryRouter.route("Explícame la biología").domain)
    }

    @Test fun routesEngineeringQueries() {
        assertEquals(KnowledgeDomain.ENGINEERING, BrainQueryRouter.route("Qué es la termodinámica").domain)
        assertEquals(KnowledgeDomain.ENGINEERING, BrainQueryRouter.route("Cómo funciona la metrología").domain)
    }

    @Test fun routesFinanceAndBusinessQueries() {
        assertEquals(KnowledgeDomain.FINANCE, BrainQueryRouter.route("Cómo funciona el interés compuesto").domain)
        assertEquals(KnowledgeDomain.BUSINESS, BrainQueryRouter.route("Cómo validar un modelo de negocio").domain)
    }

    @Test fun routesCommunicationCreativityAndGeneralComputingQueries() {
        assertEquals(KnowledgeDomain.COMMUNICATION, BrainQueryRouter.route("Consejos para mejorar la redacción").domain)
        assertEquals(KnowledgeDomain.CREATIVE_DESIGN, BrainQueryRouter.route("Cómo aplicar creatividad al diseño").domain)
        assertEquals(KnowledgeDomain.COMPUTER_SCIENCE, BrainQueryRouter.route("Qué es un algoritmo informático").domain)
    }
}
