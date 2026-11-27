# ORION Voice Lab 0.6.0 (Android / Jetpack Compose)

Asistente conversacional de voz **100 % offline y de coste cero (0 €)** en español para Android (`arm64-v8a`), construido con **Kotlin**, **Jetpack Compose (Material 3)**, **Room + KSP**, **sherpa-onnx** (`1.13.8`) y **llama.cpp** (`io.github.ljcamargo:llamacpp-kotlin:0.4.0`).

## Novedades de la Fase 0.6 (Integración telefónica real)

1. **Agenda nativa de Android y selector sin permisos (`PhoneRepository`)**:
   - Búsqueda bajo demanda en `ContactsContract.CommonDataKinds.Phone` mediante permiso en tiempo de ejecución (`READ_CONTACTS`) **sin replicar la agenda completa** dentro de la base de datos de ORION.
   - Resolución fonética/difusa en español (`PhoneContactMatcher`) para variaciones de ASR (`b/v`, `c/z/s`, `ll/y`, tildes) y alias familiares (*«mi madre»* → *Mamá*).
   - Selector nativo del sistema (`ACTION_PICK`) sin requerir `READ_CONTACTS`, más gestión local de contactos favoritos.
2. **Enrutador determinista de intenciones (`CallIntentRouter`)**:
   - Separado del LLM (`Qwen2.5-1.5B`) para evitar alucinaciones en acciones telefónicas críticas: solicitar llamadas (*«Quiero llamar a Juan»*, *«Llama a mi madre»*), buscar teléfonos, rellamar al último contacto, preparar llamadas sin marcar (*«Prepara una llamada…»*), desambiguar homónimos y responder consultas sobre historial (*«¿Cuándo llamé a Pedro?»*, *«¿Qué tenía pendiente hablar con María?»*, *«¿A quién tengo que devolver la llamada?»*).
3. **Confirmación explícita obligatoria y control de seguridad**:
   - ORION nunca marca ni abre el marcador sin mostrar primero **nombre y número** y recibir autorización explícita (*«Sí»*, *«Confirmo»*, *«Adelante»* o botón **Confirmar y llamar**), con cancelación inmediata por voz (*«No, cancela»*) o botón **Cancelar**.
4. **Telefonía oficial Android (`PhoneCallManager`) y pestaña `Teléfono`**:
   - Inicio con `Intent.ACTION_CALL` si `CALL_PHONE` está concedido y respaldo automático a `Intent.ACTION_DIAL` (marcador del sistema) si no hay permiso.
   - Seguimiento de estado telefónico (`IDLE` / `OFFHOOK` / `RINGING`) mediante `TelephonyCallback.CallStateListener` (API 31+) y `PhoneStateListener` (API 26–30) con `READ_PHONE_STATE` para medir duración cuando el dispositivo lo permite.
   - Registro persistente local en Room (`PhoneCallRecordEntity`, migración v2→v3) con motivo, estado, duración, notas, resultado y bandera de seguimiento pendiente.
5. **Limitaciones reales de la plataforma Android**:
   - Las aplicaciones estándar no pueden capturar ni inyectar audio en llamadas celulares activas (`VOICE_DOWNLINK`/`VOICE_UPLINK` restringidos al sistema); por ello ORION libera su micrófono al iniciar una llamada real.
   - No se fuerza `RoleManager` ni la sustitución del marcador predeterminado del sistema.

## Novedades heredadas de la Fase 0.5 (Memoria persistente, Agenda de Co-workers e identidad)

1. **Persistencia local de conversaciones (`Chats`)**:
   - Almacenamiento local 100 % offline en SQLite mediante **Room** (`orion_offline_memory.db`, `allowBackup="false"`).
   - Restauración automática de la última conversación al abrir la aplicación, conservación íntegra de turnos, fechas y telemetría de latencia.
   - Creación de nuevas conversaciones sin perder las anteriores, cambio entre sesiones guardadas para continuar hablando y eliminación individual con confirmación.
2. **Memoria personal controlada (`Memoria de ORION`)**:
   - Detector heurístico en español (`MemoryCandidateExtractor`) que propone candidatos útiles (nombre preferido, preferencias de comunicación, rutinas, proyectos activos, decisiones o indicaciones explícitas con *«recuerda que…»*) **sin guardar todo indiscriminadamente**: cada sugerencia muestra botones **Guardar**, **Editar** o **Descartar**.
   - Gestión completa: crear manualmente, editar categoría/contenido/prioridad, eliminar individualmente, vaciar todo con confirmación y activar/pausar el uso de memoria persistente.
3. **Contexto inteligente para LLM local (`ContextPromptBuilder` + `MemorySelector`)**:
   - Construcción dinámica del contexto por turno respetando la ventana de 2048 tokens de **Qwen2.5-1.5B-Instruct**: identidad configurada, hasta 6 recuerdos más relevantes puntuados por consulta y categoría (máx. 650 caracteres), resumen progresivo de turnos antiguos y ventana reciente acotada por número de mensajes y presupuesto de caracteres, con regla explícita de veracidad para evitar alucinaciones.
4. **Identidad configurable (`Identidad`)**:
   - Edición local de nombre del asistente, personalidad, tono comunicativo, nivel de formalidad (`Cercano`, `Equilibrado`, `Formal`) e instrucción base, manteniendo por defecto el estilo natural, breve y directo de ORION, con botón para restablecer valores predeterminados.

## Estabilización previa heredada (0.4.1 / 0.4.2)

1. **Compilación y firma reproducibles**:
   - **Gradle Wrapper oficial (`9.3.1`)** incluido (`gradlew` con permiso `100755`, `gradlew.bat`, `gradle/wrapper/`).
   - La variante `debug` usa `debug.keystore` en la raíz solo si existe localmente y recurre al almacén de depuración estándar en clones limpios.
   - La variante `release` nunca utiliza la firma `debug`; solo firma mediante propiedades Gradle o variables de entorno (`ORION_RELEASE_STORE_FILE`, `ORION_RELEASE_STORE_PASSWORD`, `ORION_RELEASE_KEY_ALIAS`, `ORION_RELEASE_KEY_PASSWORD`).
2. **Integridad y descarga reanudable de modelos (`ResumableHttpDownloader`)**:
   - Comprobación previa y en curso del espacio libre (`usableSpace`).
   - Reanudación HTTP `Range` con validación estricta de `Content-Range` en `206`, `416` y limpieza en `200`, rechazo de HTML inesperado y reemplazo atómico con copia de respaldo recuperable.
   - Verificación **SHA-256** oficial para `Qwen2.5-1.5B-Instruct Q4_K_M` (`1 117 320 736` bytes) y extracción segura frente a *path traversal*, bytes nulos y enlaces en `.tar.bz2`.
3. **Ciclo de vida nativo de `llama.cpp`, ASR, TTS y audio en llamada**:
   - Contexto LLM protegido con `AtomicReference<Int?>`, enrutamiento dinámico (`AudioRoutingController`), cancelación de eco/ruido (`VOICE_COMMUNICATION` + AEC/NS) y guardas de concurrencia en `ConversationEngine`.
4. **Panel de diagnóstico local (`Lab -> DIAGNÓSTICO LOCAL · 0.5.0`)**:
   - Estado en disco/RAM de ASR/LLM/TTS, memoria PSS/libre, latencias de turno, DSP/micrófono, memoria/identidad activa y exportación con **Copiar informe**.

## Flujo conversacional offline

1. **Llamada virtual (`Llamada`)**: Pulsa **Llamar a ORION** para abrir la sesión bidireccional continua sobre la conversación activa.
2. **Captura + cancelación de eco (`VoiceInputEngine`)**: `AudioRecord` a 16 kHz mono PCM-16 (`VOICE_COMMUNICATION`).
3. **VAD (`SileroVoiceActivityDetector`)**: `silero_vad.onnx` (MIT) en `assets/` sobre ventanas de 32 ms (512 muestras), umbral `0.5`, pausa `0.5 s`.
4. **ASR (`MoonshineSpeechRecognitionEngine`)**: `sherpa-onnx-moonshine-base-es-quantized-2026-02-27` en hilo único dedicado.
5. **LLM local (`LocalLanguageModel` / `LlamaCppLanguageModel`)**: **Qwen2.5-1.5B-Instruct Q4_K_M** (`Apache 2.0`), `use_mmap = true`, contexto 2048 tokens y streaming token a token con contexto inyectado de identidad + memoria.
6. **TTS en español (`LocalSpeechSynthesizer` / `SherpaOnnxSpeechSynthesizer`)**: **Piper VITS `es_ES-sharvard-medium-int8`** (`MIT / CC-BY 3.0`), 2 voces españolas, reproducción por `AudioTrack`.
7. **Interrupción (*barge-in*) y control de RAM**: Botón **Interrumpir** o voz sostenida del usuario; **Liberar RAM** descarga modelos sin borrar archivos ni memoria persistente.

Sin claves API, sin suscripciones ni llamadas a servicios en la nube (coste **0 €**).
