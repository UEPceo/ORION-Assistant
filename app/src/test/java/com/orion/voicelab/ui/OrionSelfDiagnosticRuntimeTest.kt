package com.orion.voicelab.ui

import com.orion.voicelab.data.VoiceLabUiState
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OrionSelfDiagnosticRuntimeTest {

    @Test
    fun liveReportIncludesInstalledAppChecksWithoutRequiringModels() {
        val context = RuntimeEnvironment.getApplication()
        val report = OrionSelfDiagnosticRegistry.composeReport(
            state = VoiceLabUiState(),
            diagnosticLog = "",
            context = context
        )

        assertTrue(report.contains("COMPROBACIONES EN VIVO"))
        assertTrue(report.contains("Modelo ASR:"))
        assertTrue(report.contains("Modelo LLM:"))
        assertTrue(report.contains("Modelo TTS:"))
        assertTrue(report.contains("Permiso micrófono:"))
        assertTrue(report.contains("Almacenamiento libre"))
        assertTrue(report.contains("RAM del dispositivo"))
    }
}
