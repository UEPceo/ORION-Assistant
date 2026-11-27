# ORION 0.8.1 — Recuperación automática de modelos y diagnóstico exportable (Android / Jetpack Compose)

## Novedades de la Fase 0.8.1 — Recuperación automática de modelos

- ORION comprueba en cada arranque si ASR, LLM y TTS están realmente instalados y completos.
- Si falta un modelo o su instalación está dañada/incompleta, vuelve a iniciar la descarga automática; ya no depende de una marca persistente que podía impedir la reparación después de una instalación anterior.
- Se conserva el orden TTS → ASR → LLM, el progreso, la cancelación, la reanudación de descargas parciales y la verificación de integridad existente.
- Una descarga fallida no bloquea los demás módulos de la aplicación: muestra el error y permite reintentar.

## Estabilidad y diagnóstico de memoria

- La aplicación registra eventos Android `onTrimMemory` y `onLowMemory` en el registro local de diagnóstico para correlacionar cierres y degradación con presión de memoria.
- La inicialización de la clave de Android Keystore queda serializada para evitar carreras al guardar credenciales desde varios conectores.

## Autenticación inicial de conectores

- GitHub incorpora OAuth App Device Flow para Android, sin client secret dentro del APK.
- El Client ID público se configura mediante la propiedad Gradle `ORION_GITHUB_CLIENT_ID` o la variable de entorno homónima.
- GitHub usa Device Flow de una GitHub App; los repositorios y permisos disponibles dependen de la instalación que autorice el usuario. Los tokens de acceso y renovación se cifran localmente y admiten rotación.
- Los tokens se guardan mediante AES-256-GCM con clave de Android Keystore y pueden eliminarse desde la pantalla Conectores.
- El token de Gmail es de duración limitada; no se guarda refresh token. Desconectar Gmail revoca el acceso en Google y elimina la credencial local.
- Gmail incorpora autorización nativa Google Identity con permiso inicial de solo lectura y token cifrado de duración limitada; requiere proyecto Google Cloud, Gmail API, pantalla de consentimiento y cliente OAuth Android con la huella SHA-1 correcta. La lectura de mensajes aún no está conectada.
- Guía: `docs/CONNECTORS_AUTHENTICATION.md`.

## Nueva base de conectores — estructura inicial

- Nueva pestaña **Conectores** con catálogo inicial de Gmail, GitHub, Google Calendar y Google Drive. Los proveedores aparecen como próximos; todavía no se anuncian como integraciones operativas.
- Contratos comunes para proveedores, capacidades, estado de conexión, acciones y resultados; registro explícito de adaptadores.
- Política central para impedir acciones sobre proveedores desconectados y exigir confirmación en operaciones con efectos externos o destructivos.
- Almacenamiento inicial de credenciales cifradas con AES-256-GCM y clave no exportable protegida por Android Keystore.
- Arquitectura y requisitos de seguridad documentados en `docs/CONNECTORS_ARCHITECTURE.md`.
- Esta entrega no añade OAuth ni llamadas a APIs externas. El núcleo de voz, memoria, tareas y telefonía continúa independiente y utilizable offline.

## Novedades de la Fase 0.8.0 — Diagnóstico técnico exportable

- Exportación del informe mediante el selector de archivos de Android (Storage Access Framework), en texto UTF-8.
- El informe reúne versión de la app, dispositivo/Android, memoria y almacenamiento, estado y tamaño de ASR/LLM/TTS en disco y RAM, descarga automática, estado de audio/DSP, latencias ASR/LLM/TTS, métricas de sesión, permisos, telefonía, tareas y recordatorios, errores recientes y registro local de cierres/ANR/salidas del proceso.
- Se incluyen los registros de diagnóstico disponibles hasta el límite del archivo local.
- Privacidad: el informe no incluye intencionadamente mensajes de conversaciones, transcripciones, nombres ni números de contacto. Los registros de errores y cierres pueden contener detalles técnicos de excepciones; revisa el archivo antes de compartirlo.
- La exportación es manual y permanece en el destino que el usuario elija; ORION no envía el informe a ningún servidor.


Asistente personal y conversacional de voz **100 % offline y de coste cero (0 €)** en español para Android (`arm64-v8a`), construido con **Kotlin**, **Jetpack Compose (Material 3)**, **Room + KSP**, **sherpa-onnx** (`1.13.8`) y **llama.cpp** (`io.github.ljcamargo:llamacpp-kotlin:0.4.0`).

## Novedades de la Fase 0.7.0 (Asistente personal, tareas y recordatorios locales)

1. **Sistema persistente de tareas en Room (`TaskEntity` + `TaskDao` + `TaskRepository`, migración `v3 → v4`)**:
   - Cada tarea almacena ID único, título, descripción opcional, fechas de creación y actualización, fecha límite y hora opcionales, marca de recordatorio activo, prioridad (`Baja`, `Normal`, `Alta`, `Urgente`), estado (`Pendiente`, `Completada`, `Cancelada`), categoría opcional, notas, contacto asociado y vínculo opcional con un registro de llamada (`linkedCallRecordId`).
   - Operaciones completas: crear, consultar, editar, completar, reabrir, cancelar, posponer, aplazar recordatorio y eliminar (individual, finalizadas o borrado total).
2. **Intérprete determinista en español (`TaskIntentRouter` + `SpanishDateTimeParser`)**:
   - Extrae y valida intenciones sin depender de generación libre del LLM: crear recordatorios (*«Recuérdame comprar comida mañana»*, *«Apunta que tengo que llamar a mi madre el domingo»*, *«Añade una tarea urgente para esta tarde»*), consultar vistas (*«¿Qué tengo pendiente?»*, *«¿Qué tareas tengo para hoy?»*, *«¿Qué tengo atrasado?»*), completar (*«Marca como terminado lo del banco»*), posponer (*«Pospón esa tarea hasta el lunes»*) y cancelar (*«Cancela el recordatorio de comprar comida»*).
   - Detecta ambigüedad temporal (*«a las 8»* sin mañana/tarde, expresiones vagas como *«en un rato»*), títulos faltantes y múltiples coincidencias solicitando aclaración por voz o botón en pantalla.
   - Protege `MemoryCandidateExtractor` para que las órdenes de tareas no contaminen la memoria personal, e inyecta tareas activas en `ContextPromptBuilder` incluso si la memoria personal está desactivada.
3. **Recordatorios locales reales (`AndroidTaskReminderScheduler` + `TaskReminderReceiver`)**:
   - Notificaciones locales de Android (canal `orion_task_reminders`) con permiso en tiempo de ejecución `POST_NOTIFICATIONS` en Android 13+.
   - Programación compatible con ahorro de batería/Doze mediante `AlarmManager.setAndAllowWhileIdle` (sin solicitar permisos restringidos `USE_EXACT_ALARM`/`SCHEDULE_EXACT_ALARM`) y prevención de alarmas duplicadas por tarea.
   - Acciones directas desde la notificación (**Completar**, **Posponer 1 h**, **Descartar**) y reprogramación automática tras cierre, reinicio del dispositivo (`BOOT_COMPLETED`), actualización de paquete (`MY_PACKAGE_REPLACED`) y cambios de hora/zona horaria.
4. **Seguimiento integrado con Telefonía (`Teléfono` ↔ `Tareas`)**:
   - Crear tareas vinculadas a llamadas por voz (*«Recuérdame llamar a Juan mañana»*, *«Tengo que devolver una llamada a María»*, *«Cuando termine la llamada, recuérdame enviarle el presupuesto»*) o mediante el botón **Recordar** en cada registro del historial telefónico, y lanzar llamadas confirmadas directamente desde una tarea asociada.
5. **Nueva pestaña `Tareas`**:
   - Vistas filtrables (*Hoy*, *Próximas*, *Pendientes*, *Atrasadas*, *Completadas*, *Recordatorios*), buscador local, tarjetas con acciones rápidas (+1 h, Mañana, Llamar, Editar, Cancelar, Eliminar) y editor completo.

## Estabilización e Integración QA previa — Fase 0.6.1

1. **Síntesis TTS íntegra en `ConversationEngine`**:
   - Corregida la guarda invertida (`turnGeneration.get() == myGeneration`) que impedía ejecutar `SpeechSentenceChunker.flush()` en el tramo final sin puntuación de las respuestas locales.
2. **Seguridad y concurrencia en confirmaciones telefónicas (`VoiceLabViewModel` + `OrionVoiceLabScreen`)**:
   - Exclusión mutua (`pendingCallLock`) y consumo atómico de `PendingCallRequest` para impedir llamadas duplicadas o involuntarias si coinciden confirmación por voz y pulsación en pantalla.
   - Cancelación inmediata de confirmaciones diferidas ante colgado (`endCall`), interrupción (*barge-in* o botón), cambio a segundo plano (`onAppBackgrounded`) o botón **Cancelar**, sincronizando el disparo real tras completar el TTS de confirmación.
   - `PendingCallConfirmationCard` fijada fuera del `LazyColumn` en la pantalla de llamada para que los controles de autorización nunca queden ocultos por el auto-scroll.
   - Limpieza de observadores `TelephonyCallback` y refresco al revocar permisos en Ajustes (`ON_RESUME`).
3. **Resolución de contactos e intenciones (`PhoneRepository` + `CallIntentRouter`)**:
   - Búsqueda en `ContactsContract` normalizada sin tildes ni formato numérico para localizar correctamente nombres acentuados (*«María»* ↔ *«Maria»*) y teléfonos con prefijo o espacios.
   - Limpieza de fórmulas de cortesía (*«por favor»*, *«orion»*) y preservación de tildes/eñes en notas programadas (*«Para mañana»*).
4. **Cobertura de pruebas automatizadas**:
   - Pruebas Robolectric + Room SQLite reales para migraciones `v1 → v2 → v3` y `v2 → v3`, `PhoneCallManager` (`ACTION_CALL` / `ACTION_DIAL` / permisos), `PhoneRepository` con `ContentResolver` + `ACTION_PICK`, más pruebas de regresión de `ConversationEngine`, `CallIntentRouter` y seguridad frente a llamadas involuntarias.

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


## Conectores Gmail y GitHub — primera integración funcional

- Gmail: búsqueda de mensajes y lectura limitada de remitente, asunto, fecha y fragmento; no se descarga el cuerpo completo ni se envían mensajes.
- GitHub: listado de repositorios y consulta de incidencias abiertas; no se crean, editan ni cierran elementos.
- Las órdenes de voz se reconocen mediante un enrutador determinista y se ejecutan a través de `ConnectorDispatcher`; el modelo de lenguaje no puede inventar llamadas arbitrarias a APIs.
- Las credenciales se comparten entre la pantalla de autenticación y los adaptadores de voz mediante Android Keystore.
- La primera versión de los comandos reconoce búsquedas explícitas de Gmail, listado de repositorios y consultas de incidencias por `propietario/repositorio`.
- Gmail requiere completar la verificación OAuth de Google para distribución pública. GitHub requiere configurar el Client ID de la GitHub App y habilitar Device Flow.
- Estas integraciones dependen de conexión a Internet y de que los permisos OAuth estén concedidos; el núcleo local de voz sigue funcionando sin conexión.
