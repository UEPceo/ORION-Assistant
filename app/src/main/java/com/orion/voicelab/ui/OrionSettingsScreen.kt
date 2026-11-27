package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.FormalityLevel
import com.orion.voicelab.data.VoiceLabUiState

@Composable
fun OrionSettingsScreen(
    state: VoiceLabUiState,
    onSelectVoice: (String) -> Unit,
    onDownloadVoice: (String) -> Unit,
    onSaveIdentity: (AssistantIdentity) -> Unit,
    onSetMemoryEnabled: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit
) {
    val identity = state.assistantIdentity
    var assistantName by remember(identity.assistantName) { mutableStateOf(identity.assistantName) }
    var personality by remember(identity.personality) { mutableStateOf(identity.personality) }
    var tone by remember(identity.tone) { mutableStateOf(identity.tone) }
    var formality by remember(identity.formality) { mutableStateOf(identity.formality) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
            Text("Ajustes de ORION", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Personaliza la voz, la forma de responder y la privacidad del asistente.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = "Voz de ORION", subtitle = "Selecciona la voz que utilizará durante las conversaciones.") {
            state.availableVoices.forEach { voice ->
                val selected = state.selectedVoiceId == voice.id
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(voice.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(voice.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when {
                            selected -> Text("En uso", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                            voice.isInstalled -> TextButton(onClick = { onSelectVoice(voice.id) }) { Text("Elegir") }
                            voice.downloadUrl != null -> TextButton(
                                enabled = state.voiceDownloadingId == null,
                                onClick = { onDownloadVoice(voice.id) }
                            ) { Text(if (state.voiceDownloadingId == voice.id) "Descargando…" else "Descargar") }
                        }
                    }
                }
            }
            if (state.voiceDownloadingId != null) {
                LinearProgressIndicator(
                    progress = { state.voiceDownloadProgressPercent / 100f },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(state.voiceDownloadStatus ?: "Descargando voz…", style = MaterialTheme.typography.bodySmall)
            } else if (!state.voiceDownloadStatus.isNullOrBlank()) {
                Text(state.voiceDownloadStatus.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            state.voiceDownloadError?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Text(
                "Las voces adicionales se descargan únicamente cuando pulsas «Descargar». Se instalan en el almacenamiento privado de ORION y no sustituyen la voz actual hasta que la elijas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsSection(title = "Personalidad y trato", subtitle = "Define cómo se presenta ORION y cómo se dirige a ti.") {
            OutlinedTextField(
                value = assistantName,
                onValueChange = { assistantName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Nombre del asistente") }
            )
            OutlinedTextField(
                value = personality,
                onValueChange = { personality = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Personalidad") }
            )
            OutlinedTextField(
                value = tone,
                onValueChange = { tone = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = { Text("Tono de voz y respuestas") }
            )
            Text("Nivel de formalidad", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormalityLevel.entries.forEach { option ->
                    FilterChip(
                        selected = formality == option,
                        onClick = { formality = option },
                        label = { Text(option.displayName) }
                    )
                }
            }
            Button(
                onClick = {
                    onSaveIdentity(identity.copy(
                        assistantName = assistantName.trim().ifBlank { AssistantIdentity.DEFAULT_NAME },
                        personality = personality.trim().ifBlank { AssistantIdentity.DEFAULT_PERSONALITY },
                        tone = tone.trim().ifBlank { AssistantIdentity.DEFAULT_TONE },
                        formality = formality
                    ))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                androidx.compose.material3.Icon(Icons.Outlined.Check, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Guardar cambios")
            }
        }

        SettingsSection(title = "Memoria y privacidad", subtitle = "Controla qué información personal puede conservar ORION.") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Memoria personal", style = MaterialTheme.typography.titleSmall)
                    Text("Permite proponer recuerdos y utilizar los que hayas guardado.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = identity.memoryEnabled, onCheckedChange = onSetMemoryEnabled)
            }
            Text("Los datos locales permanecen en el dispositivo. Los conectores externos requieren autorización y sus propias confirmaciones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = "Mantenimiento", subtitle = "Herramientas para revisar el estado de ORION.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Modelos conversacionales", style = MaterialTheme.typography.titleSmall)
                    Text(if (state.areAllConversationalModelsReady) "Todos los modelos principales están instalados." else "Hay modelos pendientes de instalación.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = onOpenDiagnostics) { Text("Abrir laboratorio y diagnóstico") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsSection(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}
