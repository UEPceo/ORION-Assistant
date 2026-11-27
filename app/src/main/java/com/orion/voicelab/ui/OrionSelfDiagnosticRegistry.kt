package com.orion.voicelab.ui

import com.orion.voicelab.data.VoiceLabUiState

/**
 * Versioned, evidence-oriented map of ORION's runtime modules and common failure signatures.
 * This registry describes source ownership; it does not claim that a diagnosis is confirmed
 * unless the diagnostic log contains matching evidence.
 */
object OrionSelfDiagnosticRegistry {
    const val REGISTRY_VERSION = "1.0"

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
            "corpus local y recuperación léxica",
            "fuentes registradas, documentos disponibles y procedencia",
            "comprobar que el corpus contiene fuentes; no presentar conocimiento del modelo como evidencia"
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
        maxChars: Int = 3_200
    ): String {
        if (maxChars <= 0) return ""
        val log = diagnosticLog.takeLast(6_000)
        val latestEvent = Regex(
            """eventId=(OR-[^ ]+) component=([^ ]+) cause=(.*)""",
            RegexOption.IGNORE_CASE
        ).findAll(log).lastOrNull()
        val latestFatal = Regex(
            """\[(FATAL|PROCESS_EXIT)\].*""",
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
            appendLine("Mapa de módulos (rutas de propiedad conocidas; la línea exacta solo se afirma si aparece en la traza):")
            modules.forEach { module ->
                appendLine("- ${module.id}: ${module.owner}. Código: ${module.entryPoints}. Dependencias: ${module.dependencies}.")
                appendLine("  Comprobaciones: ${module.checks}. Recuperación segura: ${module.recovery}.")
            }
            appendLine("Estado vivo: ASR=${state.asrLoaded}, LLM=${state.llmLoaded}, TTS=${state.ttsLoaded}, conversación=${state.conversationState}, escucha=${state.listeningState}.")
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

    private fun inferFailure(evidence: String): Triple<String, String, String>? {
        val rules = listOf(
            Triple(listOf("outofmemoryerror", "low_memory", "reason=10", "reason=3"), "Presión de memoria o cierre por memoria", "OOM/LOW_MEMORY", "reducir modelos simultáneos, cerrar otras apps y volver a cargar un solo motor"),
            Triple(listOf("unsatisfiedlinkerror", "dlopen failed", "jni"), "Carga de biblioteca nativa", "UnsatisfiedLinkError/JNI", "comprobar ABI del dispositivo y empaquetado de bibliotecas nativas; no reinstalar modelos sin evidencia"),
            Triple(listOf("filenotfoundexception", "no such file", "model file missing", "modelo no encontrado"), "Archivo o modelo ausente", "FileNotFound/NoSuchFile", "verificar instalación y usar la descarga/reparación integrada"),
            Triple(listOf("checksum", "sha-256", "integrity", "archivo corrupt"), "Integridad de descarga", "fallo de hash/integridad", "repetir descarga y validación; conservar el archivo original hasta confirmar reparación"),
            Triple(listOf("securityexception", "permission denied", "not permitted"), "Permiso Android o acceso denegado", "SecurityException/Permission", "revisar el permiso concreto en Ajustes y concederlo solo si la función lo necesita"),
            Triple(listOf("timeout", "sockettimeoutexception", "unknownhostexception", "connection refused"), "Conectividad o servicio remoto", "timeout/red", "comprobar conexión, endpoint y disponibilidad del proveedor; reintentar sin duplicar operaciones"),
            Triple(listOf("anr", "application not responding", "input dispatching timed out"), "Bloqueo prolongado del proceso", "ANR", "revisar la traza y operaciones bloqueantes del hilo principal antes de cambiar código"),
            Triple(listOf("cancellationexception", "cancelled", "canceled"), "Cancelación de una operación", "CancellationException", "determinar si fue una cancelación solicitada; no tratarla automáticamente como fallo")
        )
        return rules.firstOrNull { rule -> rule.first.any(evidence::contains) }
            ?.let { Triple(it.second, it.third, it.third) }
            ?.let { triple ->
                val recovery = when (triple.first) {
                    "Presión de memoria o cierre por memoria" -> "reducir modelos simultáneos, cerrar otras apps y volver a cargar un solo motor"
                    "Carga de biblioteca nativa" -> "comprobar ABI del dispositivo y empaquetado de bibliotecas nativas"
                    "Archivo o modelo ausente" -> "verificar instalación y usar la descarga/reparación integrada"
                    "Integridad de descarga" -> "repetir descarga y validación"
                    "Permiso Android o acceso denegado" -> "revisar el permiso concreto en Ajustes"
                    "Conectividad o servicio remoto" -> "comprobar conexión, endpoint y disponibilidad del proveedor"
                    "Bloqueo prolongado del proceso" -> "revisar traza y operaciones bloqueantes del hilo principal"
                    else -> "determinar si fue una cancelación solicitada"
                }
                Triple(triple.first, triple.second, recovery)
            }
    }
}
