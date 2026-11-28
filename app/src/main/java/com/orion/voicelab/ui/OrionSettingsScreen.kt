package com.orion.voicelab.ui

import android.os.Build
import android.app.ActivityManager
import com.orion.voicelab.llm.LanguageModelProfileCatalog
import com.orion.voicelab.llm.ModelTier
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.creative.ComfyUiVideoGenerationProvider
import com.orion.voicelab.creative.ComfyUiImageGenerationProvider
import com.orion.voicelab.creative.ComfyUiClient
import com.orion.voicelab.creative.OrionMediaRuntime
import com.orion.voicelab.creative.MediaAsset
import com.orion.voicelab.creative.GeneratedMedia
import com.orion.voicelab.creative.MediaGenerationKind
import com.orion.voicelab.creative.MediaGenerationConsentExecutor
import com.orion.voicelab.creative.MediaGenerationConsentGate
import androidx.compose.material3.AlertDialog
import android.provider.MediaStore
import android.os.Environment
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.content.Intent
import android.content.ContentValues
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.orion.voicelab.brain.CapabilityBaseline
import com.orion.voicelab.knowledge.KnowledgeReviewItem
import com.orion.voicelab.knowledge.LearningDecision
import com.orion.voicelab.brain.PlanExecutionEntity
import com.orion.voicelab.brain.PlanExecutionJsonCodec
import com.orion.voicelab.brain.PlanRisk
import com.orion.voicelab.brain.PlanOutputReferences
import com.orion.voicelab.brain.PlanStepInputPreviewBuilder
import com.orion.voicelab.brain.PlanStepKind
import com.orion.voicelab.brain.PlanValidator
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
import com.orion.voicelab.creative.ComfyUiEndpointConfig
import com.orion.voicelab.creative.ComfyUiWorkflowTemplate
import com.orion.voicelab.creative.MediaGenerationRequest
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
    onDeletePlan: (String) -> Unit = {},
    knowledgeReviews: List<KnowledgeReviewItem> = emptyList(),
    knowledgeReviewMessage: String? = null,
    onApproveKnowledgeReview: (String) -> Unit = {},
    onPublishKnowledgeUnverified: (String) -> Unit = {},
    onRejectKnowledgeReview: (String) -> Unit = {}
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
    var embeddingStatus by remember { mutableStateOf("Instalación automática por Wi-Fi: activada por defecto.") }
    var embeddingAutoInstall by remember { mutableStateOf(true) }
    var embeddingError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(embeddingManager) {
        embeddingInstalled = embeddingManager.isInstalled()
        embeddingAutoInstall = com.orion.voicelab.knowledge.NeuralEmbeddingAutoInstallScheduler.enabled(context)
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


        val remoteSettings = remember(context) { com.orion.voicelab.llm.RemoteInferenceSettings.read(context) }
        var remoteEnabled by remember { mutableStateOf(remoteSettings.enabled) }
        var remoteMode by remember { mutableStateOf(remoteSettings.mode) }
        var remoteEndpoint by remember { mutableStateOf(remoteSettings.endpoint) }
        var remoteModel by remember { mutableStateOf(remoteSettings.model) }
        var remoteApiKey by remember { mutableStateOf("") }
        var remoteShareContext by remember { mutableStateOf(remoteSettings.shareContext) }
        var remoteSaved by remember { mutableStateOf(false) }
        SettingsSection(title = "Motor neuronal híbrido", subtitle = "Inferencia avanzada en un servidor autoalojado con software libre, sin cargar modelos grandes en la RAM del móvil") {
            Text("ORION mantiene el modelo local como opción privada y de respaldo. Puedes conectar un servidor propio compatible con OpenAI (por ejemplo, llama.cpp server o vLLM). El software es gratuito; el hardware/hosting y la electricidad del servidor pueden tener coste.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Permitir motor remoto", modifier = Modifier.weight(1f))
                Switch(checked = remoteEnabled, onCheckedChange = { remoteEnabled = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = remoteMode == com.orion.voicelab.llm.RemoteInferenceSettings.MODE_AUTO, onClick = { remoteMode = com.orion.voicelab.llm.RemoteInferenceSettings.MODE_AUTO }, label = { Text("Automático") })
                FilterChip(selected = remoteMode == com.orion.voicelab.llm.RemoteInferenceSettings.MODE_LOCAL, onClick = { remoteMode = com.orion.voicelab.llm.RemoteInferenceSettings.MODE_LOCAL }, label = { Text("Solo local") })
                FilterChip(selected = remoteMode == com.orion.voicelab.llm.RemoteInferenceSettings.MODE_REMOTE_ONLY, onClick = { remoteMode = com.orion.voicelab.llm.RemoteInferenceSettings.MODE_REMOTE_ONLY }, label = { Text("Solo remoto") })
            }
            OutlinedTextField(value = remoteEndpoint, onValueChange = { remoteEndpoint = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Endpoint del servidor") }, placeholder = { Text("https://servidor:puerto/v1") }, singleLine = true)
            OutlinedTextField(value = remoteModel, onValueChange = { remoteModel = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Identificador del modelo") }, placeholder = { Text("qwen2.5-14b-instruct") }, singleLine = true)
            OutlinedTextField(value = remoteApiKey, onValueChange = { remoteApiKey = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Clave API (opcional)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = remoteShareContext, onCheckedChange = { remoteShareContext = it })
                Text("Autorizo enviar al endpoint remoto el prompt de sistema y hasta los últimos 12 mensajes de esta conversación (cada mensaje se limita a 8.000 caracteres). El prompt de sistema puede contener instrucciones y contexto que ORION haya preparado para esta consulta. No se transmite el historial completo ni archivos adjuntos.")
            }
            Button(onClick = {
                com.orion.voicelab.llm.RemoteInferenceSettings.save(
                    context,
                    com.orion.voicelab.llm.RemoteInferenceSettings(
                        enabled = remoteEnabled,
                        mode = remoteMode,
                        endpoint = remoteEndpoint,
                        model = remoteModel,
                        encryptedApiKey = remoteSettings.encryptedApiKey,
                        shareContext = remoteShareContext
                    ),
                    apiKey = remoteApiKey.takeIf { it.isNotBlank() }
                )
                remoteSaved = true
                remoteApiKey = ""
            }, modifier = Modifier.fillMaxWidth()) { Text("Guardar configuración remota") }
            if (remoteSaved) Text("Configuración guardada. El modo remoto solo se activa con endpoint, modelo y autorización válidos.", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Text("Automático: delega consultas complejas al servidor y mantiene las sencillas en local. Solo local: nunca transmite conversaciones. Solo remoto: evita cargar el LLM local; si el servidor falla, ORION no envía la petición a otro motor.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Seguridad: la clave API se cifra con Android Keystore. Usa HTTPS en servidores externos; HTTP solo se admite para localhost o redes privadas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val modelPreferences = remember(context) { context.getSharedPreferences("orion_model_settings", 0) }
        val totalDeviceRamMb = remember(context) {
            val info = ActivityManager.MemoryInfo()
            (context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
            info.totalMem / (1024L * 1024L)
        }
        var automaticModelProfile by remember { mutableStateOf(modelPreferences.getBoolean("auto_profile", true)) }
        var selectedModelProfileId by remember { mutableStateOf(modelPreferences.getString("selected_profile", "lite") ?: "lite") }
        val recommendedModelProfile = remember(totalDeviceRamMb) { LanguageModelProfileCatalog.recommendForDevice(totalDeviceRamMb) }
        val selectedModelProfile = remember(selectedModelProfileId) { LanguageModelProfileCatalog.byId(selectedModelProfileId) ?: LanguageModelProfileCatalog.profiles.first() }
        var installedModelProfiles by remember { mutableStateOf(setOf("lite")) }
        var profileDownloadId by remember { mutableStateOf<String?>(null) }
        var profileDownloadProgress by remember { mutableStateOf(0) }
        var profileDownloadStatus by remember { mutableStateOf("Los modelos superiores se descargan solo cuando los eliges.") }
        LaunchedEffect(context) {
            installedModelProfiles = withContext(Dispatchers.IO) {
                setOf("lite") + ModelManager.PROFILE_LLM_ARTIFACTS.keys.filter {
                    ModelManager.isProfileLlmInstalled(context.filesDir, it)
                }
            }
        }
        val requestedModelProfileId = if (automaticModelProfile) recommendedModelProfile.id else selectedModelProfileId
        val effectiveModelProfile = LanguageModelProfileCatalog.byId(requestedModelProfileId)
            ?.takeIf { it.id == "lite" || it.id in installedModelProfiles }
            ?: LanguageModelProfileCatalog.profiles.first()
        SettingsSection(title = "Perfiles del modelo neuronal", subtitle = "Selección manual o recomendación automática según la RAM física del dispositivo") {
            Text("RAM total detectada: ${totalDeviceRamMb / 1024} GB", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Selección automática", modifier = Modifier.weight(1f))
                Switch(checked = automaticModelProfile, onCheckedChange = {
                    automaticModelProfile = it
                    modelPreferences.edit().putBoolean("auto_profile", it).apply()
                    if (it) {
                        selectedModelProfileId = recommendedModelProfile.id
                        modelPreferences.edit().putString("selected_profile", recommendedModelProfile.id).apply()
                    }
                })
            }
            if (automaticModelProfile) {
                Text("Perfil recomendado para este dispositivo: ${recommendedModelProfile.displayName}.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("Perfil solicitado: ${selectedModelProfile.displayName}.", style = MaterialTheme.typography.bodyMedium)
            }
            LanguageModelProfileCatalog.profiles.forEach { profile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if ((if (automaticModelProfile) recommendedModelProfile.id else selectedModelProfileId) == profile.id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(profile.displayName, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            if (profile.id == recommendedModelProfile.id) Text("Recomendado", style = MaterialTheme.typography.labelSmall)
                        }
                        Text("Modelos: ${profile.recommendedModels.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                        Text("Descarga aproximada: ${profile.approximateDownloadMb} MB · RAM: ${profile.minimumDeviceRamGb}–${profile.recommendedDeviceRamGb} GB · Contexto objetivo: ${profile.contextTokens} tokens", style = MaterialTheme.typography.bodySmall)
                        Text(profile.configurationNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (!automaticModelProfile) {
                            TextButton(onClick = {
                                selectedModelProfileId = profile.id
                                modelPreferences.edit().putString("selected_profile", profile.id).apply()
                            }) { Text(if (selectedModelProfileId == profile.id) "Seleccionado" else "Elegir perfil") }
                        }
                        val artifact = ModelManager.profileArtifact(profile.id)
                        if (artifact != null) {
                            val installed = profile.id in installedModelProfiles
                            Button(
                                enabled = profileDownloadId == null && !installed,
                                onClick = {
                                    profileDownloadId = profile.id
                                    profileDownloadProgress = 0
                                    profileDownloadStatus = "Preparando ${profile.displayName}…"
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) {
                                                ModelManager.downloadAndInstallProfileLlm(context.filesDir, profile.id) { percent, _, _ ->
                                                    scope.launch { profileDownloadProgress = percent }
                                                }
                                            }
                                            installedModelProfiles = installedModelProfiles + profile.id
                                            profileDownloadStatus = "${profile.displayName} descargado y verificado. Selecciónalo y reinicia ORION para activarlo."
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (error: Exception) {
                                            profileDownloadStatus = "No se pudo instalar ${profile.displayName}: ${error.message ?: "error desconocido"}"
                                        } finally {
                                            profileDownloadId = null
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    when {
                                        profileDownloadId == profile.id -> "Descargando $profileDownloadProgress%"
                                        installed -> "Modelo verificado"
                                        else -> "Descargar e instalar"
                                    }
                                )
                            }
                        }
                    }
                }
            }
            Text(profileDownloadStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    effectiveModelProfile.id == "lite" && requestedModelProfileId != "lite" ->
                        "Motor activo: Ligero. El perfil solicitado no está instalado; ORION no cambia de motor hasta verificar su descarga."
                    effectiveModelProfile.id == "lite" ->
                        "Motor activo: Qwen2.5-1.5B Q4_K_M."
                    else ->
                        "Perfil ${effectiveModelProfile.displayName} verificado. Se aplicará al iniciar de nuevo ORION. La carga comprobará además la RAM libre real antes de abrir llama.cpp."
                },
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
                    var approvedIds by remember(record.id, record.resultsJson) { mutableStateOf(emptySet<String>()) }
                    var retryIds by remember(record.id, record.resultsJson) { mutableStateOf(emptySet<String>()) }
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
                                    val inputPreview = PlanStepInputPreviewBuilder.build(step, results)
                                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text("${step.title} · ${result?.status ?: StepStatus.PENDING}", style = MaterialTheme.typography.bodyMedium)
                                        if (!result?.summary.isNullOrBlank()) {
                                            Text(result!!.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (!result?.outputReference.isNullOrBlank()) {
                                            Text("Salida disponible: ${result!!.outputReference}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                        if ((step.requiresApproval || step.risk >= PlanRisk.MEDIUM) && result?.status != StepStatus.SUCCEEDED) {
                                            if (step.inputSummary.isNotBlank()) {
                                                Text("Contexto resuelto: ${inputPreview.summary ?: "Pendiente de salida de pasos anteriores"}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            inputPreview.arguments.forEach { (key, value) ->
                                                Text("$key: ${value ?: "Pendiente de salida de pasos anteriores"}", style = MaterialTheme.typography.bodySmall)
                                            }
                                            if (!inputPreview.readyForApproval) {
                                                Text("No puedes autorizar este paso hasta que todas las entradas dependientes estén resueltas.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    enabled = inputPreview.readyForApproval,
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
                                val executablePlan = plan.steps.all { it.kind in setOf(
                                    PlanStepKind.CONNECTOR_READ,
                                    PlanStepKind.CONNECTOR_WRITE,
                                    PlanStepKind.SEND_MESSAGE,
                                    PlanStepKind.PLACE_CALL,
                                    PlanStepKind.DELETE_DATA,
                                    PlanStepKind.PURCHASE,
                                    PlanStepKind.LOCAL_CREATE_TASK,
                                    PlanStepKind.LOCAL_CREATE_NOTE,
                                    PlanStepKind.LOCAL_CREATE_REMINDER
                                ) } && PlanValidator.validate(plan).valid
                                Button(
                                    enabled = executablePlan && record.status !in setOf("COMPLETED", "RUNNING"),

                                    onClick = {
                                        onExecutePlan(record.id, approvedIds, retryIds)
                                        approvedIds = emptySet()
                                        retryIds = emptySet()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Ejecutar / reanudar") }
                                if (!executablePlan) {
                                    Text("Este plan contiene pasos sin ejecutor o validación. No se puede ejecutar desde esta pantalla.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Los pasos de escritura se detienen hasta que se revisen sus entradas resueltas y se autoricen para esa ejecución. Los pasos no compatibles no se ejecutan.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(
            title = "Revisión del conocimiento",
            subtitle = "Los documentos nuevos permanecen fuera del corpus activo hasta que se revisen. La publicación verificada exige superar la política de evidencia."
        ) {
            knowledgeReviewMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            if (knowledgeReviews.isEmpty()) {
                Text("No hay candidatos pendientes de revisión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                knowledgeReviews.forEach { review ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(review.document.evidence.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text("${review.document.evidence.publisherId} · ${review.document.evidence.domain} · ${review.document.evidence.authority}", style = MaterialTheme.typography.labelSmall)
                            Text(review.document.evidence.canonicalUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            Text(review.document.excerpt.take(1_200), style = MaterialTheme.typography.bodySmall)
                            Text("Origen: ${review.origin} · decisión: ${review.decision}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (review.decision == LearningDecision.ACCEPT_FOR_REVIEW) {
                                    Button(onClick = { onApproveKnowledgeReview(review.id) }, modifier = Modifier.fillMaxWidth()) {
                                        Text("Publicar verificado")
                                    }
                                }
                                OutlinedButton(onClick = { onPublishKnowledgeUnverified(review.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Publicar como no verificado")
                                }
                                TextButton(onClick = { onRejectKnowledgeReview(review.id) }, modifier = Modifier.fillMaxWidth()) {
                                    Text("Rechazar candidato")
                                }
                            }
                        }
                    }
                }
            }
            Text("El texto generado por ORION nunca se admite como evidencia. Las correcciones personales no se convierten automáticamente en hechos generales.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SettingsSection(title = "Búsqueda semántica neuronal", subtitle = "Embeddings multilingües ONNX para mejorar la recuperación de conocimiento local.") {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Instalación automática", fontWeight = FontWeight.SemiBold)
                    Text("Por defecto descarga en Wi-Fi sin batería baja y con espacio suficiente. Después se ejecuta sin conexión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = embeddingAutoInstall, onCheckedChange = { enabled ->
                    embeddingAutoInstall = enabled
                    com.orion.voicelab.knowledge.NeuralEmbeddingAutoInstallScheduler.setEnabled(context, enabled)
                    embeddingStatus = if (enabled) "Descarga automática programada: solo Wi-Fi, batería y almacenamiento adecuados." else "Descarga automática desactivada; puedes instalar manualmente."
                })
            }
            Text(
                if (embeddingInstalled) "Encoder instalado y verificado. La búsqueda podrá combinar embeddings neuronales con el índice léxico y semántico ligero." else "Encoder pendiente. La recuperación léxica y semántica ligera continúa funcionando mientras se instala.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Modelo DistilUSE multilingüe · ONNX INT8 · ${NeuralEmbeddingModelManager.MODEL_SIZE_LABEL} · licencia ${NeuralEmbeddingModelManager.MODEL_LICENSE}. Se instala automáticamente por defecto en Wi-Fi; la inferencia y los embeddings se ejecutan localmente.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    var bearerToken by remember { mutableStateOf(prefs.getString("bearer_token","").orEmpty()) }
    var imageWorkflow by remember { mutableStateOf(prefs.getString("image_workflow","").orEmpty()) }
    var imageModelLabel by remember { mutableStateOf(prefs.getString("image_model_label","FLUX.1-schnell").orEmpty()) }
    var imageNode by remember { mutableStateOf(prefs.getString("image_prompt_node","").orEmpty()) }
    var imageNegativeNode by remember { mutableStateOf(prefs.getString("image_negative_node","").orEmpty()) }
    var imageDimensionNode by remember { mutableStateOf(prefs.getString("image_dimension_node","").orEmpty()) }
    var imageReferenceNodes by remember { mutableStateOf(prefs.getString("image_reference_nodes","").orEmpty()) }
    var videoWorkflow by remember { mutableStateOf(prefs.getString("video_workflow","").orEmpty()) }
    var videoModelLabel by remember { mutableStateOf(prefs.getString("video_model_label","Wan2.1 T2V").orEmpty()) }
    var videoNode by remember { mutableStateOf(prefs.getString("video_prompt_node","").orEmpty()) }
    var videoNegativeNode by remember { mutableStateOf(prefs.getString("video_negative_node","").orEmpty()) }
    var videoDimensionNode by remember { mutableStateOf(prefs.getString("video_dimension_node","").orEmpty()) }
    var videoReferenceNodes by remember { mutableStateOf(prefs.getString("video_reference_nodes","").orEmpty()) }
    var videoDurationNode by remember { mutableStateOf(prefs.getString("video_duration_node","").orEmpty()) }
    var videoDurationInput by remember { mutableStateOf(prefs.getString("video_duration_input","length").orEmpty()) }
    var videoFps by remember { mutableStateOf(prefs.getString("video_fps","16").orEmpty()) }
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
        OutlinedTextField(bearerToken,onValueChange={bearerToken=it},label={Text("Token Bearer (opcional)")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
        Text("Solo HTTPS para hosts públicos. HTTP se admite en localhost/LAN privada. El token se guarda localmente en preferencias privadas.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Imagen · FLUX / SD",fontWeight=FontWeight.SemiBold)
        OutlinedTextField(imageModelLabel,onValueChange={imageModelLabel=it},label={Text("Nombre del modelo (etiqueta)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(imageNode,onValueChange={imageNode=it},label={Text("Nodo prompt positivo")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(imageNegativeNode,onValueChange={imageNegativeNode=it},label={Text("Nodo negativo (opcional)")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Button(onClick={imagePicker.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth()) { Text(if(imageWorkflow.isBlank()) "Importar workflow API de imagen" else "Cambiar workflow de imagen") }
        OutlinedTextField(imageDimensionNode,onValueChange={imageDimensionNode=it},label={Text("Nodo dimensiones (opcional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(imageReferenceNodes,onValueChange={imageReferenceNodes=it},label={Text("Nodos de imagen de referencia (IDs separados por coma)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Text("Configura el nodo que contiene width y height para que ORION ajuste la relación de aspecto.",style=MaterialTheme.typography.bodySmall)
        Text(if(imageWorkflow.isBlank()) "No se ha importado workflow de imagen." else "Workflow de imagen importado (${imageWorkflow.length} caracteres).",style=MaterialTheme.typography.bodySmall)
        Text("Vídeo · Wan",fontWeight=FontWeight.SemiBold)
        OutlinedTextField(videoModelLabel,onValueChange={videoModelLabel=it},label={Text("Nombre del modelo (etiqueta)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(videoNode,onValueChange={videoNode=it},label={Text("Nodo prompt positivo")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(videoNegativeNode,onValueChange={videoNegativeNode=it},label={Text("Nodo negativo (opcional)")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Button(onClick={videoPicker.launch(arrayOf("application/json","text/plain"))},modifier=Modifier.fillMaxWidth()) { Text(if(videoWorkflow.isBlank()) "Importar workflow API de vídeo" else "Cambiar workflow de vídeo") }
        OutlinedTextField(videoDimensionNode,onValueChange={videoDimensionNode=it},label={Text("Nodo dimensiones (opcional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(videoReferenceNodes,onValueChange={videoReferenceNodes=it},label={Text("Nodos de imagen inicial/referencia (IDs separados por coma)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(videoDurationNode,onValueChange={videoDurationNode=it},label={Text("Nodo duración/frames (opcional)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(videoDurationInput,onValueChange={videoDurationInput=it},label={Text("Input frames")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(videoFps,onValueChange={videoFps=it.filter(Char::isDigit).take(2)},label={Text("FPS")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Text("Opcional: asigna el nodo de frames para adaptar la duración solicitada. Sin él, la duración depende del workflow.",style=MaterialTheme.typography.bodySmall)
        Text(if(videoWorkflow.isBlank()) "No se ha importado workflow de vídeo." else "Workflow de vídeo importado (${videoWorkflow.length} caracteres).",style=MaterialTheme.typography.bodySmall)
        Button(onClick={
            runCatching {
                if(endpoint.isNotBlank()) ComfyUiEndpointConfig(endpoint).normalizedBaseUrl()
                if(imageWorkflow.isNotBlank()) ComfyUiWorkflowTemplate(imageWorkflow,imageNode,negativePromptNodeId=imageNegativeNode.ifBlank { null },dimensionNodeId=imageDimensionNode.ifBlank { null },referenceImageNodeIds=imageReferenceNodes.split(",").map { it.trim() }.filter { it.isNotBlank() }).build(MediaGenerationRequest("ORION validation prompt","", "1:1"))
                if(videoWorkflow.isNotBlank()) ComfyUiWorkflowTemplate(videoWorkflow,videoNode,negativePromptNodeId=videoNegativeNode.ifBlank { null },dimensionNodeId=videoDimensionNode.ifBlank { null },referenceImageNodeIds=videoReferenceNodes.split(",").map { it.trim() }.filter { it.isNotBlank() },durationNodeId=videoDurationNode.ifBlank { null },durationInput=videoDurationInput.ifBlank { "length" },framesPerSecond=videoFps.toIntOrNull() ?: 16).build(MediaGenerationRequest("ORION validation prompt","", "16:9",5))
                prefs.edit().putString("endpoint",endpoint.trim()).putString("bearer_token",bearerToken.trim()) .putString("image_workflow",imageWorkflow).putString("image_model_label",imageModelLabel.trim())
                    .putString("image_prompt_node",imageNode.trim()).putString("image_negative_node",imageNegativeNode.trim()).putString("image_dimension_node",imageDimensionNode.trim()).putString("image_reference_nodes",imageReferenceNodes.trim())
                    .putString("video_workflow",videoWorkflow).putString("video_model_label",videoModelLabel.trim()).putString("video_prompt_node",videoNode.trim())
                    .putString("video_negative_node",videoNegativeNode.trim()).putString("video_dimension_node",videoDimensionNode.trim()).putString("video_reference_nodes",videoReferenceNodes.trim()).putString("video_duration_node",videoDurationNode.trim()).putString("video_duration_input",videoDurationInput.trim()).putString("video_fps",videoFps.trim()).apply()
                message="Configuración guardada. El endpoint no ejecuta nada hasta que ORION autorice la operación."
            }.onFailure { message="No se guardó: ${it.message ?: "configuración inválida"}" }
        },modifier=Modifier.fillMaxWidth()) { Text("Validar y guardar") }
        Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)

        // Direct generation UI: explicit consent, actual workflow execution, and export.
        var imagePrompt by remember { mutableStateOf("") }
        var selectedReferenceAssets by remember { mutableStateOf<List<MediaAsset>>(emptyList()) }
        val referencePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            selectedReferenceAssets=uris.take(4).map { uri ->
                val mime=context.contentResolver.getType(uri) ?: "image/*"
                val size=runCatching { context.contentResolver.openAssetFileDescriptor(uri,"r")?.use { it.length } }.getOrNull()
                MediaAsset(uri,mime,uri.lastPathSegment?.substringAfterLast("/") ?: "referencia",sizeBytes=size)
            }
        }
        var imageNegativePrompt by remember { mutableStateOf("") }
        var videoPrompt by remember { mutableStateOf("") }
        var videoNegativePrompt by remember { mutableStateOf("") }
        var videoDuration by remember { mutableStateOf("5") }
        var imageRatio by remember { mutableStateOf("1:1") }
        var videoRatio by remember { mutableStateOf("16:9") }
        var pendingGeneration by remember { mutableStateOf<MediaGenerationConsentRequestUi?>(null) }
        var generating by remember { mutableStateOf(false) }
        var generationStatus by remember { mutableStateOf("") }
        var generated by remember { mutableStateOf<GeneratedMedia?>(null) }
        var savedResultUri by remember { mutableStateOf<Uri?>(null) }
        val consentGate=remember { MediaGenerationConsentGate() }
        val generateScope=rememberCoroutineScope()
        fun executeApproved(kind:MediaGenerationKind,prompt:String,negative:String,ratio:String,duration:Int?) {
            val providerId=if(kind==MediaGenerationKind.IMAGE) "comfyui-image" else "comfyui-video"
            val label=endpoint.trim().ifBlank { "Servidor ComfyUI" }
            val approval=consentGate.prepare(kind,providerId,label,prompt)
            pendingGeneration=MediaGenerationConsentRequestUi(approval.id,kind,prompt,negative,ratio,duration,providerId,label,selectedReferenceAssets.toList())
        }
        Text("Generación directa",fontWeight=FontWeight.SemiBold)
        Button(onClick={referencePicker.launch(arrayOf("image/*"))},modifier=Modifier.fillMaxWidth()) { Text("Añadir imágenes de referencia (máx. 4)") }
        Text(if(selectedReferenceAssets.isEmpty()) "Sin imágenes de referencia seleccionadas." else "Referencias: "+selectedReferenceAssets.joinToString { it.displayName.orEmpty() },style=MaterialTheme.typography.bodySmall)
        Text("Las creaciones se ejecutan en el servidor configurado. ORION no impone cuotas ni créditos; los límites dependen del servidor, GPU, workflow, modelo y almacenamiento.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(imagePrompt,onValueChange={imagePrompt=it},label={Text("Prompt de imagen")},modifier=Modifier.fillMaxWidth(),minLines=2)
        OutlinedTextField(imageNegativePrompt,onValueChange={imageNegativePrompt=it},label={Text("Prompt negativo (opcional)")},modifier=Modifier.fillMaxWidth(),minLines=1)
        OutlinedTextField(imageRatio,onValueChange={imageRatio=it},label={Text("Formato imagen (informativo)")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Button(enabled=!generating&&endpoint.isNotBlank()&&imageWorkflow.isNotBlank()&&imagePrompt.isNotBlank(),onClick={executeApproved(MediaGenerationKind.IMAGE,imagePrompt,imageNegativePrompt,imageRatio,null)},modifier=Modifier.fillMaxWidth()) { Text("Generar imagen") }
        OutlinedTextField(videoPrompt,onValueChange={videoPrompt=it},label={Text("Prompt de vídeo")},modifier=Modifier.fillMaxWidth(),minLines=2)
        OutlinedTextField(videoNegativePrompt,onValueChange={videoNegativePrompt=it},label={Text("Prompt negativo (opcional)")},modifier=Modifier.fillMaxWidth(),minLines=1)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()) {
            OutlinedTextField(videoRatio,onValueChange={videoRatio=it},label={Text("Formato vídeo")},modifier=Modifier.weight(1f),singleLine=true)
            OutlinedTextField(videoDuration,onValueChange={videoDuration=it.filter(Char::isDigit).take(2)},label={Text("Segundos")},modifier=Modifier.weight(1f),singleLine=true)
        }
        Button(enabled=!generating&&endpoint.isNotBlank()&&videoWorkflow.isNotBlank()&&videoPrompt.isNotBlank()&&videoDuration.toIntOrNull() in 1..60,onClick={executeApproved(MediaGenerationKind.VIDEO,videoPrompt,videoNegativePrompt,videoRatio,videoDuration.toInt())},modifier=Modifier.fillMaxWidth()) { Text("Generar vídeo") }
        if(generating) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        if(generationStatus.isNotBlank()) Text(generationStatus,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        generated?.let { result ->
            Text("Resultado listo: ${result.asset.displayName ?: result.asset.uri.lastPathSegment.orEmpty()} · ${result.model}",fontWeight=FontWeight.SemiBold)
            Button(onClick={
                generateScope.launch {
                    runCatching {
                        val isVideo=result.asset.mimeType.startsWith("video/")
                        val collection=if(isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                        val ext=result.asset.displayName?.substringAfterLast('.',if(isVideo)"mp4" else "png") ?: if(isVideo)"mp4" else "png"
                        val values=ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME,"ORION-${System.currentTimeMillis()}.$ext")
                            put(MediaStore.MediaColumns.MIME_TYPE,result.asset.mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH,if(isVideo) Environment.DIRECTORY_MOVIES+"/ORION" else Environment.DIRECTORY_PICTURES+"/ORION")
                            put(MediaStore.MediaColumns.IS_PENDING,1)
                        }
                        val uri=context.contentResolver.insert(collection,values) ?: error("No se pudo crear el archivo en la galería.")
                        context.contentResolver.openOutputStream(uri,"w")?.use { out ->
                            context.contentResolver.openInputStream(result.asset.uri)?.use { input->input.copyTo(out) } ?: error("No se pudo leer el resultado.")
                        } ?: error("No se pudo escribir el archivo.")
                        values.clear();values.put(MediaStore.MediaColumns.IS_PENDING,0)
                        context.contentResolver.update(uri,values,null,null)
                        savedResultUri=uri
                        generationStatus="Guardado en ${if(isVideo)"Movies/ORION" else "Pictures/ORION"}."
                    }.onFailure { generationStatus="Error al guardar: ${it.message}" }
                }
            },modifier=Modifier.fillMaxWidth()) { Text(if(savedResultUri==null)"Guardar en galería" else "Guardar otra copia") }
            savedResultUri?.let { uri ->
                Button(onClick={
                    runCatching {
                        val intent=Intent(Intent.ACTION_VIEW).setDataAndType(uri,result.asset.mimeType).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(intent)
                    }.onFailure { generationStatus="No hay una aplicación compatible para abrir el resultado." }
                },modifier=Modifier.fillMaxWidth()) { Text("Abrir resultado") }
                Button(onClick={
                    val intent=Intent(Intent.ACTION_SEND).setType(result.asset.mimeType).putExtra(Intent.EXTRA_STREAM,uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(Intent.createChooser(intent,"Compartir creación de ORION"))
                },modifier=Modifier.fillMaxWidth()) { Text("Compartir resultado") }
            }
        }
        pendingGeneration?.let { pending ->
            AlertDialog(
                onDismissRequest={consentGate.cancel(pending.id);pendingGeneration=null},
                title={Text("Confirmar generación remota")},
                text={Text("ORION enviará el prompt a ${pending.destination}. Operación: ${if(pending.kind==MediaGenerationKind.IMAGE)"generar imagen" else "generar vídeo"}. Prompt: ${pending.prompt.take(500)}. Se enviarán también ${pending.references.size} imagen(es) de referencia: ${pending.references.joinToString { it.displayName.orEmpty() }}. ¿Confirmas?")},
                confirmButton={TextButton(onClick={
                    pendingGeneration=null
                    generating=true
                    generationStatus="Enviando workflow y esperando al servidor…"
                    generateScope.launch {
                        runCatching {
                            val runtime=run {
                                val client=ComfyUiClient(context,ComfyUiEndpointConfig(endpoint,bearerToken.ifBlank { null }))
                                val images=if(imageWorkflow.isNotBlank()) listOf(ComfyUiImageGenerationProvider(client,ComfyUiWorkflowTemplate(imageWorkflow,imageNode,negativePromptNodeId=imageNegativeNode.ifBlank { null },dimensionNodeId=imageDimensionNode.ifBlank { null },referenceImageNodeIds=imageReferenceNodes.split(",").map { it.trim() }.filter { it.isNotBlank() }),imageModelLabel.ifBlank { "FLUX.1-schnell" })) else emptyList()
                                val videos=if(videoWorkflow.isNotBlank()) listOf(ComfyUiVideoGenerationProvider(client,ComfyUiWorkflowTemplate(videoWorkflow,videoNode,negativePromptNodeId=videoNegativeNode.ifBlank { null },dimensionNodeId=videoDimensionNode.ifBlank { null },referenceImageNodeIds=videoReferenceNodes.split(",").map { it.trim() }.filter { it.isNotBlank() },durationNodeId=videoDurationNode.ifBlank { null },durationInput=videoDurationInput.ifBlank { "length" },framesPerSecond=videoFps.toIntOrNull() ?: 16),videoModelLabel.ifBlank { "Wan2.1 T2V" })) else emptyList()
                                OrionMediaRuntime(imageProviders=images,videoProviders=videos)
                            }
                            val executor=MediaGenerationConsentExecutor(runtime,consentGate,{ val manager=context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager; val active=manager.activeNetwork; manager.getNetworkCapabilities(active)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)==true })
                            val request=MediaGenerationRequest(pending.prompt,pending.negative.ifBlank { null },pending.ratio,pending.duration,pending.references)
                            val result=executor.execute(pending.id,true,request)
                            generated=result;savedResultUri=null
                            generationStatus="Generación completada. El resultado está en almacenamiento temporal; guárdalo para conservarlo."
                        }.onFailure { generationStatus="Generación fallida: ${it.message ?: it.javaClass.simpleName}" }
                        generating=false
                    }
                }) { Text("Confirmar y generar") }},
                dismissButton={TextButton(onClick={consentGate.cancel(pending.id);pendingGeneration=null}) { Text("Cancelar") }}
            )
        }
    }
}
private data class MediaGenerationConsentRequestUi(val id:String,val kind:MediaGenerationKind,val prompt:String,val negative:String,val ratio:String,val duration:Int?,val providerId:String,val destination:String,val references:List<MediaAsset>)
