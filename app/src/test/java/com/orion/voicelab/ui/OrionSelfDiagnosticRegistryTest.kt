package com.orion.voicelab.ui

import com.orion.voicelab.data.VoiceLabUiState
import org.junit.Assert.assertTrue
import org.junit.Test

class OrionSelfDiagnosticRegistryTest {

    @Test
    fun reportMapsKnownRuntimeModules() {
        val report = OrionSelfDiagnosticRegistry.composeReport(
            state = VoiceLabUiState(),
            diagnosticLog = ""
        )
        assertTrue(report.contains("VOICE_ASR"))
        assertTrue(report.contains("LLM"))
        assertTrue(report.contains("CONNECTORS"))
        assertTrue(report.contains("DIAGNOSTICS"))
        assertTrue(report.contains("ONNX Runtime Android 1.28.0"))
        assertTrue(report.contains("encoder neuronal ONNX opcional"))
    }

    @Test
    fun reportIncludesRuntimeEnvironmentAndMapsComponentAliases() {
        val log = "[2026-11-27 12:00:00.000] ERROR: eventId=OR-125 component=ASR descarga cause=missing model"
        val report = OrionSelfDiagnosticRegistry.composeReport(VoiceLabUiState(), log)
        assertTrue(report.contains("Android API="))
        assertTrue(report.contains("Detalle del módulo VOICE_ASR"))
    }


    @Test
    fun reportMapsNeuralEncoderFailuresToKnowledgeModule() {
        val log = "[2026-11-27 12:00:00.000] ERROR: eventId=OR-126 component=embedding ONNX cause=UnsatisfiedLinkError"
        val report = OrionSelfDiagnosticRegistry.composeReport(VoiceLabUiState(), log)
        assertTrue(report.contains("Detalle del módulo KNOWLEDGE"))
        assertTrue(report.contains("recuperación léxica y dispersa"))
    }

    @Test
    fun reportCorrelatesOperationalEventAndSourceLine() {
        val log = """
            [2026-11-27 12:00:00.000] ERROR: eventId=OR-123 component=LLM cause=java.lang.OutOfMemoryError
            at com.orion.voicelab.engine.LocalModelRunner.load(LocalModelRunner.kt:87)
        """.trimIndent()
        val report = OrionSelfDiagnosticRegistry.composeReport(VoiceLabUiState(), log)
        assertTrue(report.contains("OR-123"))
        assertTrue(report.contains("LocalModelRunner"))
        assertTrue(report.contains("LocalModelRunner.kt:87"))
        assertTrue(report.contains("Coincidencia heurística"))
        assertTrue(report.contains("Presión de memoria"))
    }

    @Test
    fun reportDoesNotInventLocationWithoutStackTrace() {
        val log = "[2026-11-27 12:00:00.000] ERROR: eventId=OR-124 component=ASR cause=unknown"
        val report = OrionSelfDiagnosticRegistry.composeReport(VoiceLabUiState(), log)
        assertTrue(report.contains("No se ha encontrado un marco Kotlin"))
        assertTrue(report.contains("No hay firma conocida"))
    }

    @Test
    fun advisorLoadsDiagnosticsForNaturalSelfFailurePhrases() {
        assertTrue(OrionSelfKnowledgeAdvisor.shouldReadDiagnostics("¿Por qué te has quedado bloqueado?"))
        assertTrue(OrionSelfKnowledgeAdvisor.shouldReadDiagnostics("¿Qué ha fallado?"))
        assertTrue(OrionSelfKnowledgeAdvisor.shouldReadDiagnostics("No respondes"))
    }
}
