package com.orion.voicelab.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import com.orion.voicelab.tasks.AndroidTaskReminderScheduler
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.orion.voicelab.brain.CapabilityBaseline
import com.orion.voicelab.brain.CapabilityArea
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.FormalityLevel
import com.orion.voicelab.data.VoiceLabUiState
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.orion.voicelab.knowledge.NeuralEmbeddingModelManager

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
    val capabilityEvaluation = remember { CapabilityBaseline.current() }
    val context = LocalContext.current
    val embeddingManager = remember(context) { NeuralEmbeddingModelManager(context) }
    val scope = rememberCoroutineScope()
    var embeddingInstalled by remember { mutableStateOf(false) }
    var embeddingDownloading by remember { mutableStateOf(false) }
    var embeddingProgress by remember { mutableStateOf(0f) }
    var embeddingStatus by remember { mutableStateOf("El encoder es opcional y no se descarga automáticamente.") }
    var embeddingError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(embeddingManager) {
        embeddingInstalled = embeddingManager.isInstalled()
    }
    var exactAlarmAllowed by remember { mutableStateOf(AndroidTaskReminderScheduler.canScheduleExactAlarms(context)) }
    val exactAlarmSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        exactAlarmAllowed = AndroidTaskReminderScheduler.canScheduleExactAlarms(context)
    }
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

        SettingsSection(title = "Capacidades del cerebro", subtitle = "Evaluación técnica preliminar basada en funciones realmente implementadas. Objetivo: mínimo 8/10 en cada ámbito.") {
            Text(
                "Media: ${"%.1f".format(Locale.ROOT, capabilityEvaluation.averageScore)}/10 · Mínimo: ${"%.1f".format(Locale.ROOT, capabilityEvaluation.minimumScore)}/10",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            capabilityEvaluation.areas.forEach { area ->
                val label = when (area.area) {
                    CapabilityArea.KNOWLEDGE_QUALITY -> "Calidad del conocimiento"
                    CapabilityArea.MULTI_STEP_PLANNING -> "Planificación multipaso"
                    CapabilityArea.OFFLINE_KNOWLEDGE -> "Conocimiento offline"
                    CapabilityArea.REASONING_AND_MODEL -> "Razonamiento y modelos"
                    CapabilityArea.TOOL_EXECUTION -> "Herramientas y ejecución"
                    CapabilityArea.MULTIMODAL -> "Imagen y vídeo"
                    CapabilityArea.RELIABILITY_AND_SAFETY -> "Fiabilidad y seguridad"
                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${"%.1f".format(Locale.ROOT, area.score)}/10", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
                LinearProgressIndicator(
                    progress = { (area.score / 10.0).toFloat() },
                    modifier = Modifier.fillMaxWidth()
                )
                area.gaps.take(2).forEach { gap ->
                    Text("Pendiente: $gap", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Una capacidad solo sube de puntuación cuando su implementación y sus pruebas lo demuestran. Los catálogos y funciones pendientes no cuentan como operativos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }


        SettingsSection(title = "Búsqueda semántica neuronal", subtitle = "Añade embeddings multilingües ONNX ejecutados localmente para mejorar la recuperación documental.") {
            Text(
                if (embeddingInstalled) "Encoder instalado y verificado. La búsqueda podrá combinar embeddings neuronales con el índice léxico y semántico ligero." else "Encoder opcional no instalado. ORION seguirá usando el recuperador local actual sin perder funciones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Modelo DistilUSE multilingüe · ONNX INT8 · ${NeuralEmbeddingModelManager.MODEL_SIZE_LABEL} · licencia ${NeuralEmbeddingModelManager.MODEL_LICENSE}. La descarga requiere conexión; después la inferencia es local.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!embeddingInstalled) {
                Button(
                    enabled = !embeddingDownloading,
                    onClick = {
                        scope.launch {
                            embeddingDownloading = true
                            embeddingError = null
                            embeddingStatus = "Preparando descarga…"
                            try {
                                embeddingManager.download { downloaded, total ->
                                    withContext(Dispatchers.Main) {
                                        embeddingProgress = if (total > 0L) (downloaded.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
                                        embeddingStatus = if (total > 0L) "Descargando: ${downloaded / (1024 * 1024)} / ${total / (1024 * 1024)} MB" else "Descargando encoder…"
                                    }
                                }
                                embeddingInstalled = true
                                embeddingProgress = 1f
                                embeddingStatus = "Descarga completada. Integridad SHA-256 verificada."
                            } catch (error: Exception) {
                                embeddingError = error.message ?: "No se pudo instalar el encoder."
                                embeddingStatus = "La instalación no se ha completado."
                            } finally {
                                embeddingDownloading = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (embeddingDownloading) "Descargando…" else "Descargar encoder neuronal") }
            } else {
                Text("El modelo está guardado en el almacenamiento privado de ORION. No se incluye dentro del APK.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (embeddingDownloading) LinearProgressIndicator(progress = { embeddingProgress }, modifier = Modifier.fillMaxWidth())
            Text(embeddingStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            embeddingError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            Text("El archivo se descarga desde una revisión fijada del repositorio del modelo y solo se activa si coincide su SHA-256. Si el encoder no puede cargarse, ORION conserva el buscador anterior.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                if (state.voiceDownloadProgressPercent > 0) {
                    LinearProgressIndicator(
                        progress = { state.voiceDownloadProgressPercent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
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

        SettingsSection(title = "Recordatorios y alarmas", subtitle = "Controla la precisión de los avisos de tus tareas.") {
            Text(
                text = when {
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.S -> "Este dispositivo permite programar alarmas exactas sin un acceso especial adicional."
                    exactAlarmAllowed -> "Acceso a alarmas exactas concedido. ORION puede programar recordatorios con mayor precisión."
                    else -> "Acceso especial no concedido. Los recordatorios seguirán funcionando, pero Android puede retrasar su entrega."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !exactAlarmAllowed) {
                Button(
                    onClick = {
                        AndroidTaskReminderScheduler.exactAlarmSettingsIntent(context)?.let {
                            exactAlarmSettingsLauncher.launch(it)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Configurar alarmas exactas")
                }
                Text(
                    "Se abrirán los ajustes de Android para ORION. Activa el permiso solo si quieres que los recordatorios puedan sonar a la hora prevista. Puedes seguir usando ORION sin concederlo.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
