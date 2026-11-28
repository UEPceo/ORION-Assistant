package com.orion.voicelab.ui

import android.os.Build
import android.os.SystemClock
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
import androidx.compose.material3.Checkbox
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
import com.orion.voicelab.brain.PlanExecutionEntity
import com.orion.voicelab.brain.PlanExecutionJsonCodec
import com.orion.voicelab.brain.PlanRisk
import com.orion.voicelab.brain.StepStatus
import com.orion.voicelab.brain.CapabilityArea
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.FormalityLevel
import com.orion.voicelab.data.VoiceLabUiState
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.orion.voicelab.knowledge.NeuralEmbeddingModelManager
import com.orion.voicelab.knowledge.OnnxMultilingualEmbeddingEncoder

@Composable
fun OrionSettingsScreen(
    state: VoiceLabUiState,
    onSelectVoice: (String) -> Unit,
    onDownloadVoice: (String) -> Unit,
    onSaveIdentity: (AssistantIdentity) -> Unit,
    onSetMemoryEnabled: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    planExecutions: List<PlanExecutionEntity> = emptyList(),
    onExecutePlan: (String, Set<String>, Set<String>) -> Unit = { _, _, _ -> },
    onDeletePlan: (String) -> Unit = {}
) {
    val identity = state.assistantIdentity
    val capabilityEvaluation = remember { CapabilityBaseline.current() }
    val context = LocalContext.current
    val embeddingManager = remember(context) { NeuralEmbeddingModelManager(context) }
    val scope = rememberCoroutineScope()
    var embeddingInstalled by remember { mutableStateOf(false) }
    var embeddingDownloading by remember { mutableStateOf(false) }
    var embeddingTesting by remember { mutableStateOf(false) }
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

        ComfyUiConfigurationSection(context)

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


        SettingsSection(title = "Planes y ejecuciones guardadas", subtitle = "Historial local, checkpoints y reanudación controlada. Las autorizaciones externas se solicitan de nuevo en cada ejecución.") {
            if (planExecutions.isEmpty()) {
                Text("Todavía no hay planes guardados.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                planExecutions.forEach { record ->
                    val plan = remember(record.id, record.planJson) {
                        runCatching { PlanExecutionJsonCodec.decodePlan(record.planJson) }.getOrNull()
                    }
                    val results = remember(record.id, record.resultsJson) {
                        runCatching { PlanExecutionJsonCodec.decodeResults(record.resultsJson) }.getOrDefault(emptyMap())
                    }
                    var approvedIds by remember(record.id) { mutableStateOf(emptySet<String>()) }
                    var retryIds by remember(record.id) { mutableStateOf(emptySet<String>()) }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(record.goal, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("Estado: ${record.status} · pasos: ${plan?.steps?.size ?: "datos no legibles"}", style = MaterialTheme.typography.bodySmall)
                            if (plan == null) {
                                Text("No se puede interpretar el plan guardado. No se ejecutará.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            } else {
                                plan.steps.forEach { step ->
                                    val result = results[step.id]
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${step.title} · ${result?.status ?: StepStatus.PENDING}", style = MaterialTheme.typography.bodyMedium)
                                        if (!result?.summary.isNullOrBlank()) {
                                            Text(result!!.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if ((step.requiresApproval || step.risk >= PlanRisk.MEDIUM) && result?.status != StepStatus.SUCCEEDED) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = step.id in approvedIds,
                                                    onCheckedChange = { checked ->
                                                        approvedIds = if (checked) approvedIds + step.id else approvedIds - step.id
                                                    }
                                                )
                                                Text("Autorizar este paso para esta ejecución", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        if (result?.status == StepStatus.FAILED) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = step.id in retryIds,
                                                    onCheckedChange = { checked ->
                                                        retryIds = if (checked) retryIds + step.id else retryIds - step.id
                                                    }
                                                )
                                                Text("He verificado el resultado y quiero reintentar", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                    }
                                }
                                val connectorOnly = plan.steps.all { it.kind in setOf(
                                    com.orion.voicelab.brain.PlanStepKind.CONNECTOR_READ,
                                    com.orion.voicelab.brain.PlanStepKind.CONNECTOR_WRITE,
                                    com.orion.voicelab.brain.PlanStepKind.SEND_MESSAGE,
                                    com.orion.voicelab.brain.PlanStepKind.PLACE_CALL,
                                    com.orion.voicelab.brain.PlanStepKind.DELETE_DATA,
                                    com.orion.voicelab.brain.PlanStepKind.PURCHASE
                                ) }
                                Button(
                                    enabled = connectorOnly && record.status !in setOf("COMPLETED", "RUNNING"),
                                    onClick = {
                                        onExecutePlan(record.id, approvedIds, retryIds)
                                        approvedIds = emptySet()
                                        retryIds = emptySet()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Ejecutar / reanudar") }
                                if (!connectorOnly) {
                                    Text("Este plan contiene pasos locales cuyo ejecutor aún no está integrado; no se puede ejecutar desde esta pantalla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(
                                    enabled = record.status != "RUNNING",
                                    onClick = { onDeletePlan(record.id) }
                                ) { Text("Eliminar historial") }
                            }
                        }
                    }
                }
            }
            Text("La generación automática de planes desde la conversación y los ejecutores locales siguen pendientes; esta pantalla no presenta un plan como ejecutado por el mero hecho de guardarlo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            } catch (error: CancellationException) {
                                throw error
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
                Button(
                    enabled = !embeddingTesting && !embeddingDownloading,
                    onClick = {
                        scope.launch {
                            embeddingTesting = true
                            embeddingError = null
                            embeddingStatus = "Probando carga e inferencia local…"
                            val encoder = OnnxMultilingualEmbeddingEncoder(context)
                            val startedAt = SystemClock.elapsedRealtime()
                            try {
                                val samples = listOf(
                                    "ORION recupera documentos relevantes según el significado de las frases.",
                                    "ORION retrieves relevant documents based on the meaning of sentences.",
                                    "O ORION recupera documentos relevantes com base no significado das frases.",
                                    "El mercado bursátil cerró con una caída generalizada de las acciones."
                                )
                                val vectors = mutableListOf<FloatArray>()
                                for (sample in samples) {
                                    val vector = encoder.encode(sample)
                                        ?: error("El encoder no ha devuelto un vector para todos los idiomas.")
                                    val norm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
                                    require(vector.isNotEmpty() && vector.all { it.isFinite() } && kotlin.math.abs(norm - 1.0) < 0.02) {
                                        "Un embedding no supera la validación numérica."
                                    }
                                    vectors += vector
                                }
                                require(vectors.map { it.size }.distinct().size == 1) {
                                    "La dimensión de salida cambia entre idiomas."
                                }
                                fun cosine(left: FloatArray, right: FloatArray): Double {
                                    val dot = left.indices.sumOf { left[it].toDouble() * right[it].toDouble() }
                                    val leftNorm = kotlin.math.sqrt(left.sumOf { it.toDouble() * it.toDouble() })
                                    val rightNorm = kotlin.math.sqrt(right.sumOf { it.toDouble() * it.toDouble() })
                                    return if (leftNorm == 0.0 || rightNorm == 0.0) 0.0 else dot / (leftNorm * rightNorm)
                                }
                                val spanishEnglish = cosine(vectors[0], vectors[1])
                                val spanishPortuguese = cosine(vectors[0], vectors[2])
                                val unrelatedControl = cosine(vectors[0], vectors[3])
                                val elapsed = SystemClock.elapsedRealtime() - startedAt
                                embeddingStatus = "Prueba superada: 3 idiomas; ${vectors.first().size} dimensiones; vectores normalizados; similitud ES-EN ${"%.2f".format(Locale.ROOT, spanishEnglish)}, ES-PT ${"%.2f".format(Locale.ROOT, spanishPortuguese)}; control no relacionado ${"%.2f".format(Locale.ROOT, unrelatedControl)}; ${elapsed} ms."
                            } catch (error: CancellationException) {
                                throw error
                            } catch (error: Exception) {
                                embeddingError = error.message ?: "Falló la prueba de inferencia ONNX."
                                embeddingStatus = "El encoder está instalado, pero no ha superado la prueba."
                            } finally {
                                runCatching { encoder.close() }
                                embeddingTesting = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (embeddingTesting) "Probando…" else "Probar inferencia en este dispositivo") }
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

@Composable
private fun ComfyUiConfigurationSection(context: android.content.Context) {
    val prefs=context.getSharedPreferences("orion_media_settings",0)
    var endpoint by remember { mutableStateOf(prefs.getString("endpoint","").orEmpty()) }
    var imageWorkflow by remember { mutableStateOf(prefs.getString("image_workflow","").orEmpty()) }
    var imageNode by remember { mutableStateOf(prefs.getString("image_prompt_node","").orEmpty()) }
    var imageNegativeNode by remember { mutableStateOf(prefs.getString("image_negative_node","").orEmpty()) }
    var videoWorkflow by remember { mutableStateOf(prefs.getString("video_workflow","").orEmpty()) }
    var videoNode by remember { mutableStateOf(prefs.getString("video_prompt_node","").orEmpty()) }
    var videoNegativeNode by remember { mutableStateOf(prefs.getString("video_negative_node","").orEmpty()) }
    var message by remember { mutableStateOf("Configura el servidor y carga los workflows exportados en formato API.") }
    val scope=rememberCoroutineScope()
    val imagePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            val text=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(2*1024*1024) } }
            imageWorkflow=text.orEmpty()
            message=if(imageWorkflow.isNotBlank()) "Workflow de imagen cargado." else "No se pudo leer el workflow."
        }
    }
    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) scope.launch {
            val text=withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(2*1024*1024) } }
            videoWorkflow=text.orEmpty()
            message=if(videoWorkflow.isNotBlank()) "Workflow de vídeo cargado." else "No se pudo leer el workflow."
        }
    }
    SettingsSection(title="Motores multimedia autoalojados",subtitle="Conecta ComfyUI en un PC/servidor GPU. No se descargan modelos al teléfono.") {
        OutlinedTextField(endpoint,onValueChange={endpoint=it},label={Text("URL de ComfyUI")},placeholder={Text("https://… o http://192.168.1.x:8188")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("Solo HTTPS para hosts públicos. HTTP se admite en localhost/LAN privada. No incluyas tokens ni credenciales en la URL.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Imagen · FLUX / SD",fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(imageNode,onValueChange={imageNode=it},label={Text("Nodo prompt positivo")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(imageNegativeNode,onValueChange={imageNegativeNode=it},label={Text("Nodo negativo (opcional)")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Button(onClick={imagePicker.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth()) { Text(if(imageWorkflow.isBlank()) "Importar workflow API de imagen" else "Cambiar workflow de imagen") }
        Text(if(imageWorkflow.isBlank()) "No se ha importado workflow de imagen." else "Workflow de imagen importado (${imageWorkflow.length} caracteres).",style=MaterialTheme.typography.bodySmall)
        Text("Vídeo · Wan",fontWeight=FontWeight.SemiBold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(videoNode,onValueChange={videoNode=it},label={Text("Nodo prompt positivo")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(videoNegativeNode,onValueChange={videoNegativeNode=it},label={Text("Nodo negativo (opcional)")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Button(onClick={videoPicker.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth()) { Text(if(videoWorkflow.isBlank()) "Importar workflow API de vídeo" else "Cambiar workflow de vídeo") }
        Text(if(videoWorkflow.isBlank()) "No se ha importado workflow de vídeo." else "Workflow de vídeo importado (${videoWorkflow.length} caracteres).",style=MaterialTheme.typography.bodySmall)
        Button(onClick={
            runCatching {
                if(endpoint.isNotBlank()) ComfyUiEndpointConfig(endpoint).normalizedBaseUrl()
                if(imageWorkflow.isNotBlank()) ComfyUiWorkflowTemplate(imageWorkflow,imageNode,negativePromptNodeId=imageNegativeNode.ifBlank { null }).build(MediaGenerationRequest("ORION validation prompt"))
                if(videoWorkflow.isNotBlank()) ComfyUiWorkflowTemplate(videoWorkflow,videoNode,negativePromptNodeId=videoNegativeNode.ifBlank { null }).build(MediaGenerationRequest("ORION validation prompt"))
                prefs.edit().putString("endpoint",endpoint.trim()).putString("image_workflow",imageWorkflow)
                    .putString("image_prompt_node",imageNode.trim()).putString("image_negative_node",imageNegativeNode.trim())
                    .putString("video_workflow",videoWorkflow).putString("video_prompt_node",videoNode.trim())
                    .putString("video_negative_node",videoNegativeNode.trim()).apply()
                message="Configuración guardada. El endpoint no ejecuta nada hasta que ORION autorice la operación."
            }.onFailure { message="No se guardó: ${it.message ?: "configuración inválida"}" }
        },modifier=Modifier.fillMaxWidth()) { Text("Validar y guardar") }
        Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
