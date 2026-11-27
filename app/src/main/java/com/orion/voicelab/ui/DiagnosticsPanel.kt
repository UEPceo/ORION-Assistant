package com.orion.voicelab.ui

import com.orion.voicelab.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orion.voicelab.data.ComponentModelState
import com.orion.voicelab.data.ModelInstallState
import com.orion.voicelab.data.VoiceLabUiState
import java.util.Locale

@Composable
fun OrionDiagnosticsPanel(state: VoiceLabUiState) {
    val hw = state.audioHardwareStatus
    val tel = state.callTelemetry
    val snap = state.metricsSnapshot

    fun diskLabel(installed: Boolean, stateDesc: String): String =
        if (installed) "instalado" else stateDesc

    val llmDiskStatus = when (val s = state.llmModelState) {
        is ComponentModelState.Installed -> "instalado (${s.sizeMb} MB)"
        is ComponentModelState.Downloading -> "descargando ${s.progressPercent}%"
        is ComponentModelState.Extracting -> "extrayendo"
        is ComponentModelState.Error -> "error"
        ComponentModelState.NotInstalled -> "pendiente"
    }
    val ttsDiskStatus = when (val s = state.ttsModelState) {
        is ComponentModelState.Installed -> "instalado (${s.sizeMb} MB)"
        is ComponentModelState.Downloading -> "descargando ${s.progressPercent}%"
        is ComponentModelState.Extracting -> "extrayendo"
        is ComponentModelState.Error -> "error"
        ComponentModelState.NotInstalled -> "pendiente"
    }
    val asrDiskStatus = when (val s = state.modelState) {
        is ModelInstallState.Installed -> "instalado (${s.sizeMb} MB)"
        is ModelInstallState.Downloading -> "descargando ${s.progressPercent}%"
        is ModelInstallState.Extracting -> "extrayendo"
        is ModelInstallState.Error -> "error"
        ModelInstallState.NotInstalled -> diskLabel(false, "pendiente")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_panel"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.section_diagnostics),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            DiagnosticLine(
                label = "Descarga/disco",
                value = "ASR $asrDiskStatus · LLM $llmDiskStatus · TTS $ttsDiskStatus"
            )
            DiagnosticLine(
                label = "Carga en RAM",
                value = "ASR ${if (state.asrLoaded) "sí" else "no"} · " +
                    "LLM ${if (state.llmLoaded) "sí" else "no"} · " +
                    "TTS ${if (state.ttsLoaded) "sí" else "no"}"
            )
            DiagnosticLine(
                label = "Memoria",
                value = if (snap != null) {
                    val minAvail = if (state.minAvailMb == Long.MAX_VALUE) "n/d" else "${state.minAvailMb} MB"
                    "PSS ${snap.appPssMb} MB (pico ${state.peakPssMb} MB) · libre ${snap.availRamMb} MB (mín $minAvail)"
                } else {
                    "sin lectura"
                }
            )
            DiagnosticLine(
                label = "Latencias turno",
                value = if (tel.llmTurnsCount > 0) {
                    String.format(
                        Locale.getDefault(),
                        "ASR %d ms · 1er token LLM %d ms (%.1f tok/s) · 1er audio %d ms · respuesta %d ms",
                        tel.lastAsrMs,
                        tel.lastLlmFirstTokenMs,
                        tel.lastLlmTokensPerSec,
                        tel.lastEndToSpeechMs,
                        tel.lastResponseDurationMs
                    )
                } else {
                    "sin turnos en llamada todavía"
                }
            )
            DiagnosticLine(
                label = "Micrófono y DSP",
                value = "${if (hw.isCapturing) "activo" else "inactivo"}" +
                    "${if (hw.isMuted) " (silenciado)" else ""} · ${hw.audioSourceName} ${hw.sampleRateHz} Hz · " +
                    "AEC ${hw.aecActive}/${hw.aecAvailable} · NS ${hw.nsActive}/${hw.nsAvailable}"
            )
            DiagnosticLine(
                label = "Salida de audio",
                value = "${if (hw.isPlaying) "reproduciendo" else "en espera"} · ruta ${hw.audioOutputRoute}"
            )
            DiagnosticLine(
                label = "Últimos errores",
                value = if (state.recentErrors.isEmpty()) {
                    "ninguno registrado"
                } else {
                    state.recentErrors.take(3).joinToString(" | ")
                },
                isError = state.recentErrors.isNotEmpty()
            )
        }
    }
}

@Composable
private fun DiagnosticLine(
    label: String,
    value: String,
    isError: Boolean = false
) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
    )
}
