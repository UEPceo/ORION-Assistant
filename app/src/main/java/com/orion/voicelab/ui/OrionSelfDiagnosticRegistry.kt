package com.orion.voicelab.ui

import android.Manifest
import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.BuildConfig
import com.orion.voicelab.data.VoiceLabUiState

/**
 * Versioned, evidence-oriented map of ORION's runtime modules and common failure signatures.
 * This registry describes source ownership; it does not claim that a diagnosis is confirmed
 * unless the diagnostic log contains matching evidence.
 */
object OrionSelfDiagnosticRegistry {
    const val REGISTRY_VERSION = "1.2"

    private data class ModuleProfile(
        val id: String,
        val owner: String,
        val entryPoints: String,
        val dependencies: String,
        val checks: String,
        val recovery: String
    )

    private val modules = listOf(
        ModuleProfile(
            "VOICE_ASR",
            "Motor de voz / reconocimiento",
            "OrionVoiceEngine, MoonshineModelManager",
            "sherpa-onnx v1.13.8; Moonshine Base ES v2; Silero VAD",
            "modelo presente, carga del recognizer, captura de micrófono, estado de escucha",
            "comprobar permiso de micrófono y archivos del modelo; detener/iniciar escucha; reintentar descarga si faltan archivos"
        ),
        ModuleProfile(
            "LLM",
            "Motor de lenguaje local",
            "ConversationEngine y adaptador llama.cpp",
            "llamacpp-kotlin 0.4.0; Qwen2.5 1.5B",
            "modelo instalado, memoria disponible, inicialización JNI y estado de inferencia",
            "comprobar espacio y RAM; cerrar procesos pesados; reiniciar carga del modelo; reintentar instalación si está incompleta"
        ),
        ModuleProfile(
            "VOICE_TTS",
            "Síntesis de voz",
            "SpeechSynthesizer y gestor de modelos TTS",
            "Piper TTS y modelo/voz configurados",
            "modelo y configuración de voz, inicialización, cancelación y salida de audio",
            "verificar instalación de la voz, volumen y salida de audio; volver a seleccionar voz y reintentar"
        ),
        ModuleProfile(
            "TASKS",
            "Tareas y recordatorios",
            "TaskRepository, parser de tareas, AndroidTaskReminderScheduler",
            "Room; AlarmManager; notificaciones Android",
            "persistencia Room, permiso de notificaciones, alarmas y recepción tras reinicio",
            "comprobar permiso de notificaciones y configuración de alarmas; revisar fecha/hora y zona horaria"
        ),
        ModuleProfile(
            "CONTACTS_CALLS",
            "Contactos y llamadas",
            "PhoneCallManager, CallIntentRouter",
            "ContactsContract; Telecom/Intent Android",
            "permisos de contactos/teléfono, coincidencia de contacto y disponibilidad de telefonía",
            "revisar permisos y desambiguar el contacto; confirmar siempre antes de iniciar una llamada"
        ),
        ModuleProfile(
            "CONNECTORS",
            "Conectores externos",
            "OrionConnectorRuntime, routers y adaptadores por proveedor",
            "OAuth/device flow, intents Android y APIs de proveedor",
            "autenticación vigente, permisos, conectividad y operación permitida por el adaptador",
            "reautenticar el proveedor, revisar permisos y confirmar el alcance real de la operación"
        ),
        ModuleProfile(
            "KNOWLEDGE",
            "Conocimiento y recuperación local",
            "OrionBrainRuntime y repositorio de conocimiento",
            "corpus local; índice léxico y semántico disperso; encoder neuronal ONNX opcional (ONNX Runtime Android 1.28.0 y Extensions 0.13.0)",
            "fuentes registradas, fragmentación documental, estado del encoder opcional e inferencia local",
            "si ONNX falla, conservar la recuperación léxica y dispersa; comprobar archivo, SHA-256, ABI y bibliotecas nativas antes de reinstalar"
        ),
        ModuleProfile(
            "MEDIA",
            "Diseño y multimedia",
            "CreativeSkillRouter, OrionMediaRuntime, MediaUploadCoordinator",
            "proveedor ComfyUI remoto/local aún por configurar",
            "proveedor registrado, endpoint, consentimiento puntual y conectividad",
            "configurar proveedor compatible; obtener consentimiento específico antes de transmitir archivos"
        ),
        ModuleProfile(
            "DIAGNOSTICS",
            "Registro y diagnóstico",
            "OrionCrashDiagnostics",
            "almacenamiento privado app; ApplicationExitInfo desde Android 11",
            "escritura/lectura del log, permisos de almacenamiento privado y registros de salida del proceso",
            "exportar el informe local y revisar datos técnicos antes de compartirlo"
        )
    )

    fun composeReport(
        state: VoiceLabUiState,
        diagnosticLog: String,
        maxChars: Int = 3_200,
        context: Context? = null
    ): String {
        if (maxChars <= 0) return ""
        val log = diagnosticLog.takeLast(6_000)
        val latestEvent = Regex(
            """eventId=(OR-[^ ]+) component=(.*?) cause=(.*)""",
            RegexOption.IGNORE_CASE
        ).findAll(log).lastOrNull()
        val latestFatal = Regex(
            """] (FATAL|PROCESS_EXIT):.*""",
            RegexOption.IGNORE_CASE
        ).findAll(log).lastOrNull()?.value
        val stackFrame = Regex("""at ([A-Za-z0-9_$.]+)\(([^)]*\.kt):(\d+)\)""")
            .findAll(log).lastOrNull()

        val component = latestEvent?.groupValues?.getOrNull(2).orEmpty()
        val cause = latestEvent?.groupValues?.getOrNull(3).orEmpty()
        val evidence = (component + " " + cause + " " + log.takeLast(2_000)).lowercase()
        val inferred = inferFailure(evidence)

        return buildString {
            appendLine("REGISTRO TÉCNICO DE AUTODIAGNÓSTICO v$REGISTRY_VERSION:")
            appendLine("Entorno: ORION=${BuildConfig.VERSION_NAME} (code=${BuildConfig.VERSION_CODE}); Android API=${Build.VERSION.SDK_INT}; dispositivo=${Build.MANUFACTURER} ${Build.MODEL}; ABI=${Build.SUPPORTED_ABIS.take(3).joinToString()}.")
            appendLine("Mapa de módulos (rutas de propiedad conocidas; la línea exacta solo se afirma si aparece en la traza):")
            modules.forEach { module ->
                appendLine("- ${module.id}: ${module.owner}; código=${module.entryPoints}; deps=${module.dependencies}.")
            }
            val normalizedComponent = component.lowercase()
            val matchedModule = modules.firstOrNull { module ->
                normalizedComponent.contains(module.id.lowercase()) ||
                    module.entryPoints.split(",").any { entry ->
                        normalizedComponent.contains(entry.trim().substringBefore("." ).lowercase())
                    } ||
                    componentAliasMatches(module.id, normalizedComponent)
            }
            if (matchedModule != null) {
                appendLine("Detalle del módulo ${matchedModule.id}: comprobar ${matchedModule.checks}. Recuperación segura: ${matchedModule.recovery}.")
            }
            appendLine("Estado vivo: ASR=${state.asrLoaded}, LLM=${state.llmLoaded}, TTS=${state.ttsLoaded}, conversación=${state.conversationState}, escucha=${state.listeningState}.")
            if (context != null) appendRuntimeHealth(context.applicationContext, state)
            if (latestEvent != null) {
                appendLine("Último error operativo correlacionado: id=${latestEvent.groupValues[1]}, componente=${component}, causa registrada=${cause.take(400)}.")
            } else {
                appendLine("No se encontró evento operativo OR-... en el extracto.")
            }
            if (stackFrame != null) {
                appendLine("Marco de traza encontrado: ${stackFrame.groupValues[1]} (${stackFrame.groupValues[2]}:${stackFrame.groupValues[3]}).")
            } else {
                appendLine("No se ha encontrado un marco Kotlin con archivo y línea en el extracto.")
            }
            if (!latestFatal.isNullOrBlank()) appendLine("Registro fatal/salida detectado: ${latestFatal.take(500)}")
            if (inferred != null) {
                appendLine("Coincidencia heurística (NO confirmación): ${inferred.first}. Evidencia que la activó: ${inferred.second}.")
                appendLine("Acción inicial segura: ${inferred.third}.")
            } else {
                appendLine("No hay firma conocida suficientemente clara. No atribuyas el error a un módulo sin evidencia adicional.")
            }
            appendLine("Regla de certeza: el componente del evento es la procedencia registrada; una firma heurística es solo una pista; la clase/método/línea requieren una traza real.")
        }.take(maxChars)
    }


    /**
     * Read-only checks against the installed app: model files, runtime memory, free storage
     * and Android permissions. A missing permission is reported as a limitation, not an error.
     */
    private fun appendRuntimeHealth(context: Context, state: VoiceLabUiState): String = buildString {
        appendLine("COMPROBACIONES EN VIVO (solo lectura):")
        val filesDir = context.filesDir
        val modelChecks = listOf(
            Triple(
                "ASR",
                runCatching {
                    ModelManager.asrDirectory(filesDir).let { it.isDirectory && it.listFiles()?.isNotEmpty() == true }
                }.getOrDefault(false),
                state.asrLoaded
            ),
            Triple(
                "LLM",
                runCatching {
                    ModelManager.llmModelFile(filesDir).let {
                        it.isFile && it.length() == ModelManager.LLM_EXACT_BYTES
                    }
                }.getOrDefault(false),
                state.llmLoaded
            ),
            Triple(
                "TTS",
                runCatching {
                    val model = ModelManager.ttsModelFile(filesDir)
                    val tokens = ModelManager.ttsTokensFile(filesDir)
                    val espeak = ModelManager.ttsEspeakDataDir(filesDir)
                    model.isFile && model.length() >= 5_000_000L &&
                        tokens.isFile && tokens.length() > 100L &&
                        espeak.isDirectory && espeak.listFiles()?.isNotEmpty() == true
                }.getOrDefault(false),
                state.ttsLoaded
            )
        )
        modelChecks.forEach { (name, installed, loaded) ->
            val status = when {
                !installed -> "NO_INSTALADO_O_INCOMPLETO"
                loaded -> "CARGADO_EN_RAM"
                else -> "INSTALADO_EN_DISCO_NO_CARGADO"
            }
            appendLine("- Modelo $name: $status.")
        }

        val permissions = listOf(
            "micrófono" to Manifest.permission.RECORD_AUDIO,
            "contactos" to Manifest.permission.READ_CONTACTS,
            "llamadas" to Manifest.permission.CALL_PHONE,
            "calendario" to Manifest.permission.READ_CALENDAR
        ).toMutableList()
        if (Build.VERSION.SDK_INT >= 33) {
            permissions += "notificaciones" to Manifest.permission.POST_NOTIFICATIONS
        }
        permissions.forEach { (label, permission) ->
            val granted = runCatching {
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            appendLine("- Permiso $label: ${if (granted) "CONCEDIDO" else "NO_CONCEDIDO"} (la función puede requerir autorización).")
        }

        val storage = runCatching { StatFs(filesDir.absolutePath).availableBytes }.getOrNull()
        if (storage != null) appendLine("- Almacenamiento libre en partición de datos: ${storage / (1024L * 1024L)} MB.")
        val memory = runCatching {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        }.getOrNull()
        if (memory != null) {
            appendLine("- RAM del dispositivo: libre=${memory.availMem / (1024L * 1024L)} MB; baja=${memory.lowMemory}; umbralSistema=${memory.threshold / (1024L * 1024L)} MB.")
        }
        appendLine("Los permisos no concedidos no se consideran fallos si la función correspondiente no se está utilizando.")
    }

    private fun componentAliasMatches(moduleId: String, component: String): Boolean = when (moduleId) {
        "VOICE_ASR" -> listOf("asr", "moonshine", "reconocimiento", "microfono", "micrófono").any(component::contains)
        "LLM" -> listOf("llm", "qwen", "lenguaje", "inferencia").any(component::contains)
        "VOICE_TTS" -> listOf("tts", "piper", "sintesis", "síntesis", "voz").any(component::contains)
        "TASKS" -> listOf("tarea", "recordatorio", "alarma").any(component::contains)
        "CONTACTS_CALLS" -> listOf("contact", "llamada", "telefon", "teléfono").any(component::contains)
        "CONNECTORS" -> listOf("gmail", "github", "calendar", "maps", "keep", "conector").any(component::contains)
        "KNOWLEDGE" -> listOf("knowledge", "conocimiento", "rag", "fuente", "onnx", "embedding", "encoder neuronal").any(component::contains)
        "MEDIA" -> listOf("media", "multimedia", "creative", "imagen", "video", "vídeo").any(component::contains)
        "DIAGNOSTICS" -> listOf("diagn", "crash", "log").any(component::contains)
        else -> false
    }

    private data class FailureSignature(
        val needles: List<String>,
        val diagnosis: String,
        val evidenceLabel: String,
        val recovery: String
    )

    private fun inferFailure(evidence: String): Triple<String, String, String>? {
        val rules = listOf(
            FailureSignature(
                listOf("outofmemoryerror", "low_memory", "reason=3 (low_memory)"),
                "Presión de memoria o cierre por memoria",
                "OOM/LOW_MEMORY",
                "reducir modelos simultáneos, cerrar otras apps y volver a cargar un solo motor"
            ),
            FailureSignature(
                listOf("unsatisfiedlinkerror", "dlopen failed", "jni"),
                "Carga de biblioteca nativa",
                "UnsatisfiedLinkError/JNI",
                "comprobar ABI del dispositivo y empaquetado de bibliotecas nativas"
            ),
            FailureSignature(
                listOf("filenotfoundexception", "no such file", "model file missing", "modelo no encontrado"),
                "Archivo o modelo ausente",
                "FileNotFound/NoSuchFile",
                "verificar instalación y usar la descarga/reparación integrada"
            ),
            FailureSignature(
                listOf("checksum", "sha-256", "integrity", "archivo corrupt"),
                "Integridad de descarga",
                "fallo de hash/integridad",
                "repetir descarga y validación; conservar el archivo original hasta confirmar reparación"
            ),
            FailureSignature(
                listOf("securityexception", "permission denied", "not permitted"),
                "Permiso Android o acceso denegado",
                "SecurityException/Permission",
                "revisar el permiso concreto en Ajustes y concederlo solo si la función lo necesita"
            ),
            FailureSignature(
                listOf("timeout", "sockettimeoutexception", "unknownhostexception", "connection refused"),
                "Conectividad o servicio remoto",
                "timeout/red",
                "comprobar conexión, endpoint y disponibilidad del proveedor; reintentar sin duplicar operaciones"
            ),
            FailureSignature(
                listOf("anr", "application not responding", "input dispatching timed out"),
                "Bloqueo prolongado del proceso",
                "ANR",
                "revisar la traza y operaciones bloqueantes del hilo principal antes de cambiar código"
            ),
            FailureSignature(
                listOf("cancellationexception", "cancelled", "canceled"),
                "Cancelación de una operación",
                "CancellationException",
                "determinar si fue una cancelación solicitada; no tratarla automáticamente como fallo"
            )
        )
        val match = rules.firstOrNull { rule -> rule.needles.any(evidence::contains) } ?: return null
        return Triple(match.diagnosis, match.evidenceLabel, match.recovery)
    }
}
