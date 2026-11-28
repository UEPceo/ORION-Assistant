# ORION — Arquitectura del cerebro autónomo y conocimiento verificable

Estado: núcleo local, planificación por etapas, acciones locales con confirmación, recuperación híbrida y corpus offline de dieciséis packs implementados. El encoder ONNX multilingüe se instala por defecto cuando hay Wi-Fi sin batería baja y espacio suficiente; validación en dispositivo pendiente. Los proveedores multimedia siguen sin configuración.

## Objetivo

ORION será un asistente personal y empresarial de propósito general, con conversación, investigación, programación, análisis informático, navegación de información y ejecución controlada de tareas. Debe abarcar programación, arquitectura de software, sistemas, redes, ciberseguridad, Internet, empresa, productividad, conocimiento general, pesca recreativa, pesca marítima y surfcasting.

No se limita a respuestas memorizadas. Debe investigar, recuperar información, contrastarla, explicar sus límites y actualizar su base documental.

## Arquitectura por capas

1. Orquestador: identifica intención, ámbito, actualidad necesaria, riesgo y herramientas disponibles. Decide si responder, investigar, pedir aclaración o ejecutar una acción.
2. Modelo de lenguaje: redacta, razona sobre la evidencia recuperada, planifica y genera código. No es una fuente de verdad ni tiene permiso directo para ejecutar acciones.
3. Recuperación de conocimiento (RAG): consulta documentos locales, manuales, fuentes oficiales, documentación técnica y fuentes web autorizadas. Devuelve fragmentos con URL, editor, fecha, ámbito y jurisdicción.
4. Verificador de evidencia: evalúa autoridad, independencia, vigencia, contradicciones y cobertura. Puede impedir que una respuesta se presente como confirmada.
5. Herramientas y conectores: realizan operaciones tipadas a través de permisos y confirmaciones. El modelo no construye llamadas arbitrarias.
6. Memoria personal: conserva preferencias y hechos del usuario con controles de consulta, edición y borrado. No se mezcla con el conocimiento general.
7. Aprendizaje y mantenimiento: incorpora nuevas fuentes, versiones de documentación, correcciones confirmadas y feedback; mantiene historial, fecha de revisión y procedencia.

## Política de veracidad

ORION no puede garantizar verdad absoluta ni prometer que nunca cometerá errores. Sí puede aplicar controles verificables:

- Toda afirmación actual o importante debe poder vincularse a una o varias fuentes concretas.
- Para normativa, seguridad, meteorología y restricciones locales se exige fuente primaria u oficial vigente. Una guía secundaria nunca sustituye al BOE, boletín autonómico, autoridad marítima, AEMET o fuente oficial equivalente.
- Para programación se prioriza documentación oficial del lenguaje, framework, fabricante o API y se registra la versión consultada.
- Para afirmaciones técnicas generales se buscan fuentes independientes y se distinguen documentos primarios de resúmenes.
- La experiencia comunitaria aporta contexto práctico, pero se etiqueta como experiencia, no como hecho universal.
- Las fuentes contradictorias se muestran como conflicto; ORION no elige una arbitrariamente.
- La información caducada se marca como histórica o se vuelve a consultar.
- Si no hay evidencia suficiente, ORION dice «no lo he podido verificar» y no completa huecos inventando.
- Cada dato dinámico (tiempo, mareas, tráfico, disponibilidad, calendario, CI) lleva fecha/hora de consulta y zona geográfica cuando corresponda.

Se han incorporado contratos de evidencia, enrutador determinista por dominio, repositorio local privado persistente, motor de recuperación, composición de contexto con procedencia y política de aprendizaje. Los veredictos incluyen SUPPORTED, NEEDS_CORROBORATION, PRIMARY_SOURCE_REQUIRED, STALE, AGE_UNCONFIRMED, CONFLICTING, NO_EVIDENCE e INVALID_TIMESTAMP. El núcleo ya se invoca desde el contexto de cada turno de voz. El corpus local se inicializa automáticamente en el primer uso con dieciséis paquetes, incluidos pesca Andalucía, Android/Kotlin, normativa España/UE y calendarios/recordatorios. Se guarda en almacenamiento privado mediante escritura atómica y la recuperación combina índice semántico disperso, ranking léxico y embeddings ONNX multilingües cuando están instalados. El encoder se descarga de forma automática por defecto solo en red no medida, con batería y almacenamiento suficientes; el usuario puede desactivarlo. La búsqueda web solo se activa ante una petición explícita. El contexto RAG se limita a 2.200 caracteres para proteger la ventana reducida del modelo local. La detección de discrepancias compara afirmaciones numéricas asociadas al mismo tema y contrasta metadatos de conflicto; sus alertas son indicios, no una prueba automática.

## Fuentes por ámbito

### Programación e informática
Documentación oficial y notas de versión de Kotlin, Android, Jetpack, Gradle, Git, GitHub, bases de datos, protocolos y proveedores. Repositorios, issues, changelogs y pruebas ejecutables cuando el usuario autorice el acceso. La versión de la documentación debe coincidir con la versión del proyecto analizado.

### Internet y conocimiento general
Fuentes primarias, documentación de organismos, universidades, publicaciones técnicas y medios reputados según el tipo de afirmación. Fechas de publicación y actualización visibles; evitar presentar resultados antiguos como actuales.

### Empresa
Fuentes oficiales de organismos públicos, documentación de producto y datos financieros publicados por fuentes responsables. Separar hechos, estimaciones, hipótesis y recomendaciones. No convertir una proyección en resultado garantizado.

### Pesca y surfcasting
- Normativa y tallas: boletines y organismos oficiales por país, comunidad autónoma y zona.
- Seguridad marítima y condiciones: AEMET, Salvamento Marítimo, autoridades portuarias y proveedores meteorológicos identificados.
- Mareas: fuente, estación, datum y hora local; no confundir altura sobre cero hidrográfico con otros niveles.
- Especies, temporadas y cebos: distinguir evidencia científica, fichas técnicas y experiencia de pescadores; indicar zona y estación.
- PescaIA Surfcasting puede aportar catálogo de playas, previsión, mareas, solunar, metodología y contenido propio. Deben conservarse sus fuentes originales; PescaIA no será la única autoridad para normativa o seguridad.

## Aprendizaje continuo sin autoengaño

Aprender significa actualizar el corpus recuperable, registrar correcciones y mejorar las herramientas, no reentrenar automáticamente los pesos del modelo con cada conversación.

- Ingesta con lista de fuentes permitidas, límites de tamaño y protección frente a instrucciones maliciosas incluidas en páginas o documentos.
- Deduplificación por URL canónica y hash de contenido.
- Versionado de cada documento, fecha de recuperación, publicación y revisión.
- Feedback del usuario guardado como corrección pendiente hasta que pueda verificarse; la preferencia del usuario no convierte por sí sola una afirmación objetiva en verdadera.
- Memorias personales separadas del corpus público.
- No usar conversaciones privadas, correos, notas ni datos de conectores para entrenar o ampliar conocimiento general sin consentimiento específico.
- Revisión periódica y retirada lógica de documentos obsoletos, conservando trazabilidad.



## Líneas prioritarias de evolución (28/11/2026)

- La aceleración GPU/NPU se desarrollará como backend alternativo LiteRT-LM, conservando llama.cpp GGUF CPU como fallback. No se dará por compatible una NPU hasta verificar el SoC concreto y ejecutar una inferencia real. Diseño: `docs/ORION_ACCELERATED_INFERENCE_BACKEND.md`.
- La ingeniería de software es una capacidad central en construcción: GitHub tiene workspace acotado, generación de parches estructurados, autorización temporal y commits aprobados; GitHub Actions valida ORION en runner efímero. El diff y la aprobación están en la pantalla Conectores; el bucle de reparación y un runner universal siguen pendientes. Diseño: `docs/ORION_CODE_ENGINEERING.md`.
- El aprendizaje continuo se organizará como captura, cuarentena, verificación, revisión, publicación, versionado y evaluación de regresiones. No implica autoentrenar pesos del LLM. Estado y brechas: `docs/ORION_CONTINUOUS_LEARNING.md`.

## Capacidad de programación y autonomía

El flujo de programación debe poder inspeccionar repositorios y documentación autorizados; identificar requisitos, dependencias, versiones y riesgos; diseñar cambios pequeños; modificar código en un entorno controlado; ejecutar compilación, análisis estático y pruebas; revisar diffs, errores y regresiones; y proponer commits, PR o despliegues.

La autonomía tiene límites: no puede autoasignarse permisos, ocultar errores, afirmar que una compilación pasó sin evidencia ni desplegar cambios sensibles sin autorización. Debe detenerse y pedir intervención cuando no pueda verificar un resultado.

## Límite del modelo actual

El modelo local actual Qwen2.5 1.5B, con contexto configurado de 2048 tokens, sirve como base ligera para conversación offline, pero no equivale por sí solo a un cerebro experto en programación, investigación o razonamiento empresarial. La arquitectura debe permitir modelos alternativos:

- Perfil local ligero para privacidad y funcionamiento sin conexión.
- Perfil local avanzado si el dispositivo dispone de RAM y almacenamiento suficientes.
- Perfil remoto opcional para investigación o tareas complejas, siempre con consentimiento, configuración visible y control de los datos enviados.
- Si no existe conexión ni evidencia local suficiente, ORION reconoce la limitación en vez de improvisar.

## Ciclo de respuesta

Intención → clasificación de riesgo y actualidad → recuperación → verificación → respuesta con fuentes y límites → acción autorizada (si procede) → registro del resultado.

Las respuestas distinguen:
- Verificado: cumple la política de fuentes y vigencia.
- Orientativo: útil, pero basado en evidencia parcial o experiencia.
- No verificado: faltan fuentes o hay conflicto.
- Histórico: válido para una fecha anterior, no necesariamente ahora.

## Fases de implementación

1. Contratos de evidencia y política de veracidad (iniciados).
2. Repositorio de conocimiento local con Room, indexación y búsqueda híbrida.
3. Recuperador web con lista de fuentes, extracción segura y citas.
4. Enrutador de herramientas y planificador con límites de pasos, presupuesto y cancelación.
5. Integración de los cuatro conectores prioritarios definidos en PRIORITY_CONNECTORS.md.
6. Packs de conocimiento y actualización periódica por ámbito.
7. Selección de modelos local/avanzado/remoto y evaluación comparativa.
8. Suite de evaluación de factualidad, actualidad, citas, seguridad, programación y regresiones.


## Planificador multipaso persistente — fase de ejecución durable

Se ha añadido la primera capa operativa de persistencia y recuperación:

- La base Room sube de versión 4 a 5 mediante migración incremental; se crea `plan_executions` sin borrar conversaciones, recuerdos, contactos ni tareas existentes.
- Cada plan validado se guarda antes de ejecutar, con pasos tipados, dependencias, argumentos de conectores, presupuesto, estado y resultados.
- El coordinador publica checkpoints antes y después de cada paso. Se conservan el estado, el resumen y la referencia de salida para no repetir pasos ya completados.
- La reanudación exige aprobación nueva para cada paso protegido que siga pendiente. Las autorizaciones no se guardan ni se reutilizan entre ejecuciones.
- Si Android cierra el proceso durante un paso, el registro se recupera como PAUSED y el paso activo pasa a FAILED con advertencia de resultado incierto. ORION no lo repite automáticamente; el usuario debe verificar y seleccionar expresamente los pasos que desea reintentar.
- La cancelación cooperativa conserva el checkpoint PAUSED incluso cuando la corrutina ya está cancelada.
- La ViewModel expone historial observable y operaciones para guardar, ejecutar y borrar planes. Ajustes incluye una pantalla de historial para revisar los pasos, marcar autorizaciones puntuales, seleccionar reintentos tras verificar y eliminar registros. Se ha añadido un parser JSON estricto para propuestas estructuradas; la generación estructurada ya se invoca desde el turno de voz para peticiones multipaso compatibles; las propuestas no compatibles vuelven al flujo conversacional normal.

Límites aún abiertos: la salida del LLM todavía no se convierte automáticamente en un objeto `OrionPlan` estructurado y validado; no existe aún una pantalla de revisión/aprobación/reanudación para el usuario; los pasos locales (pensar, cálculo, lectura genérica y creación de documentos) necesitan handlers reales. El adaptador de conectores sí pasa por el dispatcher y su puerta de seguridad, pero la persistencia por sí sola no convierte una operación no implementada en funcional. La fase no debe presentarse como agente autónomo completo.

La base de datos de ORION es almacenamiento privado de la aplicación, pero no se declara cifrada. Los argumentos de planes deben excluir credenciales, tokens y secretos; el sistema no debe persistir material de autenticación.

## Criterios de aceptación

ORION no se considerará listo por responder con fluidez. Debe demostrar en pruebas que no inventa fuentes, enlaces ni resultados de ejecución; reconoce información caducada, insuficiente o contradictoria; prioriza fuentes oficiales para normativa y seguridad; separa conocimiento público, memoria personal y contenido privado; mantiene capacidades locales cuando fallan Internet o los conectores; y puede investigar, generar código y validar resultados con herramientas autorizadas.


## Módulos creativos y multimedia

El cerebro incluye dominios CREATIVE_DESIGN, SOCIAL_MEDIA e IMAGE_VIDEO. El enrutador creativo activa módulos de diseño, branding, copywriting, storyboard, estrategia social, analítica, generación o comprensión de imagen/vídeo. Sus instrucciones se añaden al contexto conversacional y pueden especializarse según el rol del co-worker.

La dirección visual por defecto es ORION Editorial (navy #080D1A, cian #16C7E8 y blanco), con composición editorial tecnológica, alto contraste y legibilidad móvil. El motor social planifica por plataforma, público, objetivo y formato; mide retención, finalización, guardados, compartidos, comentarios cualificados, acciones de perfil y conversiones. No pretende conocer pesos privados de algoritmos ni garantiza viralidad.

Los contratos MediaUnderstandingProvider, ImageGenerationProvider y VideoGenerationProvider permiten integrar modelos locales, servicios remotos o aplicaciones externas. OrionMediaRuntime declara disponibilidad real y rechaza tareas si no hay proveedor configurado. El modelo Qwen2.5 1.5B actual es textual; no se debe describir como modelo de visión ni generador multimedia.

## Descubrimiento web explícito

Se ha conectado un adaptador de búsqueda web de solo lectura basado en la página HTML de DuckDuckGo. Solo se activa cuando el usuario pide expresamente buscar o investigar en Internet; las conversaciones normales y el modo offline no generan tráfico web. La consulta tiene timeout, límite de respuesta y máximo de cinco resultados.

Alcance actual: busca resultados, recupera título, URL y fragmento, e intenta leer hasta tres páginas completas por consulta. El lector solo admite HTTPS, rechaza credenciales, puertos no estándar, destinos locales/privados/reservados, limita redirecciones, tiempo de conexión, tamaño de respuesta y texto extraído. Las redirecciones se validan de nuevo y la autoridad se asigna al host final, mediante una lista conservadora de dominios oficiales y documentación técnica conocida. Los dominios no reconocidos permanecen UNKNOWN.

La lectura HTML es heurística, no un navegador completo: no ejecuta JavaScript, no resuelve paywalls, contenido dinámico, PDFs ni páginas que requieran sesión. La resolución DNS se valida antes de la conexión, pero la implementación aún no fija la IP validada en el socket; queda pendiente endurecimiento frente a DNS rebinding antes de considerarla una frontera de seguridad de nivel producción. El buscador puede cambiar HTML, bloquear peticiones o devolver resultados incompletos.

Los fragmentos no leídos siguen siendo pistas UNKNOWN. En consultas sensibles a actualidad, ORION exige fecha de publicación/actualización extraída: consultar hoy una página antigua no convierte su contenido en actual. La clasificación de dominios es una lista inicial pequeña, no una evaluación universal de reputación. No se guarda automáticamente nada en el corpus ni en la memoria personal.

## Fuentes externas y aprendizaje

OrionBrainRuntime acepta KnowledgeSource externos inyectables además del corpus local. El runtime registra el corpus local y el adaptador de búsqueda web explícita con lectura HTML acotada descrito arriba. El aprendizaje persiste documentos con procedencia y hash, pero no modifica automáticamente los pesos del modelo. Los proveedores de búsqueda, indexación y actualización deben registrarse y someterse a los mismos controles de autoridad, frescura, contradicción y seguridad.

Detalle creativo/multimedia y pendientes: [ORION_CREATIVE_MULTIMODAL_ARCHITECTURE.md](ORION_CREATIVE_MULTIMODAL_ARCHITECTURE.md).

## Autoconocimiento técnico y asesor de capacidades

ORION mantiene un inventario explícito de las capacidades de la versión instalada y lo incorpora al contexto del modelo cuando la consulta trata sobre su propio funcionamiento o pide recomendaciones de uso. El inventario diferencia funciones operativas, integraciones parciales y capacidades que aún requieren configuración.

Para preguntas sobre errores propios, el asesor recupera un extracto acotado del registro privado local, identifica los identificadores `OR-...`, el componente y la excepción disponible, y orienta el diagnóstico hacia el módulo responsable. Debe distinguir entre causa confirmada e hipótesis, no inventar líneas de código ni resultados de compilación y proponer soluciones seguras y verificables. La lectura del registro es local; no implica envío automático a servidores.

El asesor recomienda las funciones existentes según la tarea y explica requisitos de permisos, autenticación, conexión y confirmación. El inventario declara expresamente límites conocidos: Google Keep solo recibe contenido compartido, Calendar no permite edición/borrado directo, Maps abre la app externa, PescaIA solo consulta contenido público limitado, multimedia real y búsqueda web general siguen pendientes de configuración/integración. Las recomendaciones nunca conceden permisos ni ejecutan acciones por sí mismas; siguen vigentes los routers deterministas y las puertas de confirmación.

Esta capa mejora el autoconocimiento del modelo, pero no sustituye al diagnóstico estático ni a las pruebas: si los registros no contienen evidencia suficiente, ORION debe solicitar el informe técnico y reconocer que no puede confirmar la causa.

### Registro técnico de autodiagnóstico v1.3

Se incorpora `OrionSelfDiagnosticRegistry`, que relaciona los subsistemas principales con sus puntos de entrada, dependencias conocidas, comprobaciones y recuperación segura:

- Voz ASR: `OrionVoiceEngine`, `MoonshineModelManager`, sherpa-onnx 1.13.8, Moonshine Base ES v2 y Silero VAD.
- LLM: `ConversationEngine`, adaptador llama.cpp 0.4.0 y Qwen2.5 1.5B.
- TTS: `SpeechSynthesizer`, Piper y configuración de voz.
- Tareas: `TaskRepository`, Room, AlarmManager y notificaciones.
- Telefonía: `PhoneCallManager`, `CallIntentRouter` y APIs Android.
- Conectores, conocimiento/RAG, multimedia y diagnóstico: runtime y adaptadores respectivos; se declaran expresamente los proveedores que aún requieren configuración.

El registro incorpora la versión instalada de ORION, API de Android, fabricante/modelo y ABI; correlaciona el último evento `OR-...`, su componente y causa, y extrae el último marco Kotlin disponible con clase, archivo y línea. Incluye firmas heurísticas para OOM/LOW_MEMORY, JNI, archivos/modelos ausentes, integridad de descargas, permisos, red, ANR y cancelaciones. Una firma solo genera una pista: nunca se convierte automáticamente en causa confirmada. La localización exacta requiere una traza real; cuando no existe, el informe lo indica.

La recuperación recomendada se limita a acciones seguras y reversibles. No elimina modelos, no cambia permisos, no reintenta operaciones sensibles ni transmite registros por sí misma. El inventario se mantiene manualmente y debe revisarse cuando cambien módulos, versiones o dependencias; todavía no es un escáner automático del código ni un grafo de dependencias generado desde Gradle.



### Autodiagnóstico de estado en vivo v1.1

El informe de autodiagnóstico recibe ahora el contexto de la aplicación y realiza comprobaciones locales de solo lectura antes de formular recomendaciones:

- Estado de los modelos ASR, LLM y TTS en disco, diferenciándolo de su carga efectiva en RAM.
- Permisos Android relevantes para micrófono, contactos, llamadas, calendario y notificaciones (este último solo desde Android 13).
- Espacio disponible en la partición de datos y memoria RAM disponible, incluyendo el indicador de presión de memoria del sistema.
- Estado de conversación y escucha, versión de ORION, API y dispositivo, junto con los eventos técnicos correlacionados.

La comprobación no solicita permisos, no descarga ni repara modelos, no modifica archivos y no transmite datos. Un permiso ausente se presenta como una limitación funcional potencial, no como un fallo automático. Un modelo instalado pero no cargado se distingue de uno ausente o incompleto. Se añade una prueba Robolectric para verificar que el informe incorpora estas comprobaciones con un contexto Android.


### Transparencia de fallos de fuentes v1.1

El motor de recuperación conserva ahora los fallos individuales de cada proveedor en `BrainContext.sourceFailures`. Registra únicamente el identificador acotado del proveedor y el tipo de excepción, nunca el mensaje bruto que podría contener URLs, credenciales u otros datos sensibles. Las cancelaciones de corrutinas siguen propagándose y no se convierten en errores de proveedor.

Si todas las fuentes fallan y no hay documentos, el resultado se distingue de un corpus vacío mediante `BrainPresentation.SOURCE_FAILURE`. Si alguna fuente responde correctamente, su evidencia se conserva y el contexto incorpora además los proveedores que fallaron. El compositor del prompt instruye al modelo para que no confunda una consulta fallida con la inexistencia de información. Se añaden pruebas para ambos escenarios.


### Programación de recordatorios y alarmas exactas v1.0

El programador de tareas utiliza `setExactAndAllowWhileIdle` en Android 12 o superior cuando el acceso especial de alarmas exactas está concedido. En Android anteriores se mantiene el comportamiento compatible. Si el usuario no concede ese acceso, ORION no bloquea la creación de recordatorios: utiliza `setAndAllowWhileIdle` y el sistema puede diferir la entrega. La aplicación declara `SCHEDULE_EXACT_ALARM`, consulta `canScheduleExactAlarms()` antes de elegir la API y expone una intención para abrir el ajuste del sistema; esa intención debe lanzarse únicamente desde una acción explícita del usuario, nunca automáticamente.

Cuando Android notifica un cambio en el acceso a alarmas exactas, el receptor vuelve a conciliar los recordatorios pendientes para aplicar el modo disponible. La precisión exacta depende del permiso especial y de las restricciones del sistema; no se promete puntualidad exacta cuando el permiso está denegado ni se utiliza `USE_EXACT_ALARM`, cuyo uso está sujeto a políticas restrictivas de distribución.

La pantalla Ajustes > Recordatorios y alarmas muestra el estado real del acceso especial y ofrece un botón para abrir los ajustes del sistema únicamente cuando el acceso falta. Al volver de Ajustes, la pantalla vuelve a consultar el permiso y actualiza el estado. En Android anterior a 12 informa que no se requiere ese acceso adicional.


## Auditoría de capacidad del cerebro — 27/11/2026

La arquitectura está bien separada en recuperación, verificación, memoria, diagnóstico, creatividad y conectores, pero no equivale todavía a un asistente general ultra completo. El cuello de botella no es solo la cantidad de conocimiento: es el modelo local pequeño, la ausencia de un corpus curado inicial, la recuperación léxica y la falta de un planificador multi-herramienta integrado.

| Capacidad | Estado real | Brecha principal |
|---|---|---|
| Conversación por voz offline | Implementación local | Calidad de razonamiento limitada por Qwen2.5 1.5B y ventana corta |
| Conocimiento general offline | Conocimiento paramétrico del modelo | Sin enciclopedia/documentación local indexada |
| RAG local | Implementado, seis packs y dieciséis documentos | Búsqueda híbrida local, fragmentos solapados y actualizaciones periódicas verificadas |
| Búsqueda web | Búsqueda explícita + lectura HTML acotada | Sin navegador completo, JS, PDF, múltiples buscadores ni cobertura amplia de fuentes |
| Verificación | Política conservadora implementada | Clasificador de autoridad pequeño; conflictos y fechas aún parciales |
| Aprendizaje | Ingesta curada disponible | Sin flujo UI completo de revisión, versionado y actualización programada |
| Autodiagnóstico | Registro local y heurísticas | No captura todos los errores ni analiza automáticamente todo el código |
| Tareas y recordatorios | Operaciones deterministas | Falta planificación de varios pasos y seguimiento de resultados |
| Conectores | Integraciones acotadas | Faltan acciones completas de escritura y más proveedores |
| Programación | Workspace, generador JSON, diff UI, autorización y commits aprobados | Falta seguimiento CI/repair loop y runner universal de proyectos |
| Diseño y estrategia creativa | Router y prompts | No hay motores multimedia conectados |
| Comprensión/generación de imagen y vídeo | Contratos y consentimiento | Sin proveedores operativos en el runtime |
| Memoria personal | Local y separada del corpus | Falta mejor extracción, consolidación y control de caducidad |

### Qué conocimiento debe incorporar

1. Packs generales offline: ciencia, matemáticas, historia, geografía, lengua, cultura, economía, informática, primeros auxilios básicos y seguridad. Cada pack debe llevar licencia, versión, fecha, fuente y tamaño; no descargar un corpus masivo sin control.
2. Packs técnicos: Kotlin/Android/Gradle, Python, Git/GitHub, Linux, redes, bases de datos, APIs, ciberseguridad defensiva y documentación de herramientas que ORION pueda usar.
3. Fuentes España/UE: BOE, EUR-Lex, AEPD, INCIBE, AEMET, Protección Civil, DGT y organismos autonómicos/municipales cuando la consulta lo requiera. Para PescaIA, normativa pesquera por comunidad, provincia, zona y fecha.
4. Conocimiento práctico: guías de usuario, manuales de fabricantes, documentación de dispositivos, protocolos y procedimientos. Separar instrucciones verificadas de experiencias comunitarias.
5. Conocimiento personal: preferencias, proyectos, decisiones y rutinas del usuario en un almacén independiente y editable; nunca mezclarlo con el corpus público.

### Qué debe poder hacer además de saber

- Investigar una pregunta: descomponerla, buscar, abrir fuentes, comparar fechas, extraer afirmaciones y entregar una respuesta citada.
- Planificar trabajos de varios pasos con dependencias, presupuesto, tiempo máximo, estado, cancelación y recuperación ante fallos.
- Usar herramientas tipadas: archivos, correo, calendario, navegador, repositorios, terminal aislada y aplicaciones Android; cada acción con alcance mínimo y confirmación según riesgo.
- Programar de extremo a extremo en un sandbox: inspeccionar proyecto, proponer diff, editar, compilar, ejecutar tests, analizar logs y preparar commit/PR. Nunca afirmar éxito sin resultados.
- Analizar archivos y datos: PDF, DOCX, hojas de cálculo, imágenes, audio y vídeo, con extracción local cuando sea viable.
- Comparar alternativas, calcular, crear informes, documentos, presentaciones y hojas de cálculo.
- Supervisar tareas autorizadas: recordatorios, seguimiento de CI, alertas y trabajos programados, con límites y registro auditable.
- Aprender de fuentes y correcciones aprobadas, detectar información caducada y avisar cuando una fuente cambie.
- Mantener perfiles de ejecución: offline ligero, local avanzado en equipo compatible y remoto opcional con consentimiento por operación.

### Orden recomendado de cierre

P0 — Calidad del conocimiento: cerrar pruebas del recuperador web, ampliar fuentes oficiales españolas y europeas, añadir fecha de modificación, extracción robusta, citas visibles y pruebas contra prompt injection, redirecciones y DNS rebinding.

P1 — Planificador y uso de herramientas: crear un plan tipado, máximo de pasos, permisos por acción, confirmación por operación, cancelación, idempotencia, registro de ejecución y verificación de resultados.

P2 — Corpus offline: incorporar packs pequeños y útiles con licencia, hash, versión, índice léxico + semántico y actualización incremental. Priorizar documentación Android/Kotlin, informática general, España/UE y dominios del usuario.

P3 — Capacidad cognitiva: permitir modelos alternativos más capaces. Qwen2.5 1.5B debe seguir como perfil ligero offline, no como único motor para razonamiento complejo. Añadir evaluación comparativa y escalado opcional a un modelo local avanzado o remoto consentido.

P4 — Herramientas y multimedia: sandbox de programación, lectura de documentos, navegador real, proveedores de imagen/vídeo y conectores ampliados. Cada proveedor debe exponer disponibilidad real y requisitos antes de ofrecer la función.

P5 — Evaluación continua: banco de pruebas con preguntas conocidas, fuentes fechadas, tareas multi-herramienta, errores simulados, privacidad, seguridad y regresiones. No declarar el cerebro terminado hasta superar criterios medibles.

**Conclusión de auditoría:** ORION ya tiene una base arquitectónica prometedora, pero su conocimiento offline es escaso y su autonomía multi-paso aún no está resuelta. Para alcanzar el objetivo ultra completo necesita más conocimiento curado, un modelo de razonamiento mejorable por perfiles y, sobre todo, un planificador con herramientas reales y validación de resultados.

## Ampliación del cerebro — 27/11/2026

Esta actualización incorpora las primeras implementaciones de los seis frentes de mejora. No debe confundirse el catálogo o el contrato arquitectónico con una capacidad ya operativa.

### 1. Calidad del conocimiento y fuentes
- Catálogo inicial de fuentes oficiales españolas y europeas y documentación técnica: BOE, EUR-Lex, AEMET, MITECO, Sanidad, INCIBE, AEPD, DGT, Junta de Andalucía, MAPA, Android, Kotlin, Python, OWASP, MDN y GitHub Docs.
- El catálogo registra dominio, jurisdicción, autoridad y cadencia de revisión. Las búsquedas sensibles se restringen preferentemente a los dominios oficiales registrados. Una URL de catálogo no se considera evidencia hasta recuperarla, fecharla y verificarla.
- La recuperación local combina BM25-like léxico y vectores dispersos por fragmento (conceptos, alias y trigramas), con desempate por autoridad/fecha. No utiliza todavía embeddings neuronales.
- Sigue pendiente extracción PDF, varios buscadores, corroboración avanzada, fecha de modificación robusta y defensa completa contra DNS rebinding.

### 2. Planificación multipaso
- Contratos tipados para planes, pasos, dependencias, riesgo, presupuestos, estados e idempotencia.
- Validador de dependencias y ciclos, límite de pasos, reintentos y duración.
- Coordinador secuencial con presupuesto temporal global, reintentos limitados y condicionados por idempotencia; una dependencia fallida bloquea los pasos posteriores y las excepciones se convierten en resultados controlados.
- Adaptador `ConnectorPlanStepRunner` conectado al dispatcher existente y su puerta de seguridad; cada paso externo declara conector, operación y argumentos explícitos.
- Las escrituras externas, mensajes, llamadas, borrados y compras requieren aprobación específica para los IDs de paso de esa ejecución. No hay autorización persistente.
- El contexto de conversación activa instrucciones de planificación cuando detecta una petición multipaso. Existe un adaptador que conecta pasos externos con ConnectorDispatcher y su puerta de confirmación.
- Limitación: falta integrar el coordinador en el runtime conversacional, convertir la salida del modelo en planes estructurados y persistir/reanudar ejecuciones. El modelo puede proponer un plan, pero ORION no debe afirmar que lo ha ejecutado.

### 3. Conocimiento offline
- Catálogo de seis packs previstos: normativa España/UE, seguridad España, ciberseguridad, Android/Kotlin, Python/Internet y pesca recreativa Andalucía.
- Cada pack declara versión, dominios, jurisdicción, fuentes, licencia y frecuencia de revisión.
- **Estado actualizado: seis packs con contenido real incluido en APK assets, 16 documentos fuente y un instalador local versionado.** Los documentos se incorporan al corpus privado al primer uso del cerebro, sin red ni descarga. Se calcula SHA-256 del contenido, se conserva URL/editor/autoridad/jurisdicción y se valida cada documento antes de guardarlo.
- Packs incluidos: Android/Kotlin (arquitectura Android, ciclo de vida, concurrencia estructurada), Python (fundamentos, HTTP, excepciones y pruebas), ciberseguridad (OWASP Top 10, credenciales y modelado de amenazas), legislación España/UE (consulta BOE y EUR-Lex), emergencias 112 y pesca recreativa Andalucía (licencias, PescaREC y aviso sobre cambios judiciales).
- La instalación es idempotente y versionada; el paquete andaluz está en versión 2 para reflejar información institucional consultada el 27/11/2026. Si un paquete falla, se registra el error y no se marca como instalado.
- El contenido es una síntesis educativa con enlaces a las fuentes, no una copia íntegra ni una base legal exhaustiva. El conocimiento normativo y de seguridad debe verificarse online antes de actuar; las fechas de revisión y las advertencias de vigencia se conservan.

### 4. Modelos y razonamiento
- Catálogo de perfiles separa modelo ligero local, futuro modelo local avanzado y futuro proveedor remoto consentido.
- Selector por modo, capacidades solicitadas y memoria disponible. Si ningún perfil operativo cumple los requisitos, devuelve ausencia de motor compatible en lugar de prometer capacidad.
- Qwen2.5 1.5B Q4 continúa siendo el único perfil operativo actual, con contexto 2048 y uso ligero. No es un motor de razonamiento avanzado ni multimodal.
- La selección multi-modelo, cuantizaciones alternativas y evaluación de calidad/rendimiento en dispositivo siguen pendientes.

### 5. Herramientas y multimedia
- Catálogo de capacidades y limitaciones para Gmail, GitHub (lectura y escritura de ingeniería), Maps, PescaIA, Calendar, archivos, navegador y sandbox de programación.
- Se explicita que las escrituras GitHub no equivalen a ejecución de código; Actions valida el propio ORION y el sandbox universal sigue sin estar implementado.
- Los contratos multimedia y la puerta de consentimiento de un solo uso siguen preparados, pero no se han conectado proveedores ComfyUI ni una interfaz de progreso/resultado. Generación y análisis de imagen/vídeo siguen sin estar operativos.

### 6. Evaluación continua
- Evaluador ponderado por áreas con puntuación 0–10, brechas y criterio global: todas las áreas deben llegar a 8/10.
- Línea base conservadora calculada desde capacidades realmente implementadas: conocimiento 7,7/10; planificación 6,7/10; conocimiento offline 8,8/10; razonamiento/modelo 4/10; herramientas 4/10; multimedia 4/10; fiabilidad/seguridad 4/10 (CI de esta revisión pendiente). Mínimo global: **4/10**; media aproximada: **5,6/10**.
- Esta puntuación es una línea base de ingeniería basada en criterios explícitos, no una prueba independiente de inteligencia. Se recalculará cuando haya pruebas automatizadas y resultados de ejecución.
- La meta 8/10 no está alcanzada. El mayor déficit está en razonamiento/modelos avanzados, ejecución multipaso conectada, herramientas completas y multimedia operativa.

### Criterio de aceptación para declarar 8/10
Cada área debe disponer de criterios reproducibles, pruebas unitarias e integración, evidencia de ejecución y cobertura suficiente. Ninguna capacidad marcada como catálogo, contrato, configuración pendiente o no implementada puede sumar como operativa. La puntuación mínima entre áreas gobierna el resultado global; una media alta no puede ocultar un área débil.


### Incorporación de conocimiento offline real — 27/11/2026
- Assets: `app/src/main/assets/knowledge_packs/*.json` con seis packs y dieciséis documentos en castellano.
- Instalador: `BundledKnowledgePackInstaller`; integra en `OrionBrainRuntime.retrieve()` antes de la primera recuperación. No realiza peticiones de red.
- Persistencia: corpus privado existente; versión por pack en preferencias privadas. Reintento automático si un pack falla antes de marcar su versión como instalada.
- Integridad: SHA-256 al ingerir y comprobación del hash al leer el corpus. Las fuentes y jurisdicciones se conservan para filtrar y ordenar resultados.
- Tests: contenido y atribución de los seis assets, instalación real con Robolectric, repetición idempotente, búsqueda posterior y estado dinámico de instalación.
- Índice semántico: `KnowledgeSemanticIndex` mantiene vectores dispersos por fragmento en un archivo privado independiente. Combina términos normalizados, trigramas de caracteres y un mapa explícito de conceptos/sinónimos en español; la recuperación combina puntuación léxica BM25-like y similitud coseno. No requiere red ni un modelo adicional.
- Profundidad documental: `KnowledgeDocumentChunker` crea pasajes de hasta 1.200 caracteres con solapamiento de 160 caracteres y preferencia por límites de frase/párrafo. La respuesta recupera el pasaje más relevante y conserva la procedencia del documento.
- Actualización automática: `KnowledgePackUpdateScheduler` registra trabajo periódico semanal con WorkManager, solo redes no medidas (habitualmente Wi-Fi) y batería no baja. Android puede aplazar la ejecución por ahorro energético; también existe una solicitud manual inmediata.
- Canal remoto: manifiesto versionado en GitHub Raw, seis paquetes allowlist por ID y ruta, descargas HTTPS limitadas por tamaño/tiempo, validación de SHA-256, versión monotónica y escritura atómica en caché. El instalador vuelve a verificar el hash antes de ingerir. Los fallos generan reintentos y no sustituyen la versión válida instalada.
- Límite importante: el índice actual es semántico disperso basado en conceptos y n-gramas, no embeddings neuronales. Resuelve sinónimos y variaciones léxicas conocidas, pero no alcanza la comprensión de un encoder multilingüe. Se mantiene la búsqueda léxica como respaldo y se ha integrado un encoder ONNX cuantizado opcional; su validación de inferencia y calidad sigue pendiente.
- Seguridad del canal: TLS y SHA-256 detectan transporte corrupto o archivos que no coinciden con el manifiesto, pero el manifiesto y los paquetes residen en el mismo repositorio; no es una firma criptográfica independiente del editor. Para un canal de alta garantía queda pendiente firmar el manifiesto con una clave privada fuera del repositorio y verificar con clave pública fijada en la app.
- El paquete legal no sustituye la consulta de vigencia. El contenido inicial sigue siendo una síntesis educativa, no una base normativa exhaustiva.


### Índice semántico, profundidad documental y actualizaciones — 27/11/2026
- `KnowledgeSemanticIndex`: persistencia local de vectores dispersos por fragmento; similitud coseno, trigramas, conceptos y alias en español; reconstrucción si faltan vectores; eliminación sincronizada con el corpus.
- `KnowledgeCorpusStore`: ranking híbrido con peso léxico 55 % y semántico 45 %, más autoridad y fecha como desempates. Permite recuperar documentos aunque no compartan palabras exactas cuando existe un concepto compartido.
- `KnowledgeDocumentChunker`: pasajes solapados para no perder contexto en los límites. Los fragmentos conservan la cita del documento de origen.
- `KnowledgePackUpdateWorker`: consulta el manifiesto remoto, comprueba el host/ruta permitidos, tamaño, versión y hash; descarga a caché mediante `AtomicFile`; el instalador valida el sidecar SHA-256 antes de ingerir.
- `KnowledgePackUpdateScheduler`: trabajo semanal en red no medida y batería suficiente, con backoff exponencial. El SO puede retrasarlo; no es una actualización en tiempo real.
- Canal de publicación: `app/src/main/assets/knowledge_packs/updates/manifest.json` y paquetes versionados. Para publicar una actualización hay que subir el JSON de paquete y actualizar versión, URL y SHA-256 del manifiesto.
- Limitación reconocida: el índice no usa aún embeddings neuronales. Los hashes no sustituyen una firma de editor independiente. Los tests de integración y el CI deben confirmar la implementación antes de considerarla validada.


### Ampliación del corpus multidisciplinar — 27/11/2026

El catálogo pasa de seis a quince paquetes locales. Se añaden nueve áreas: informática, matemáticas, ciencia, ingeniería, finanzas, empresa, comunicación, creatividad y conocimiento general. Se mantienen los seis paquetes previos de normativa, seguridad, ciberseguridad, Android/Kotlin, Python/Internet y pesca recreativa de Andalucía.

- Nuevos paquetes: `computer-science`, `mathematics`, `science`, `engineering`, `finance`, `business`, `communication`, `creativity` y `general-knowledge`.
- Primera versión: dos documentos por paquete nuevo (18 documentos adicionales), cada uno con título, URL HTTPS, editor, dominio, autoridad, jurisdicción cuando aplica y fecha de revisión.
- Fuentes iniciales: NIST, MIT OpenCourseWare, OpenStax, NASA, NOAA, CNMV, Banco de España, OCDE, Comisión Europea, W3C, Purdue OWL, Creative Commons, Adobe y UNESCO.
- Los textos son síntesis educativas redactadas para el corpus, no copias íntegras. El enlace a una fuente no implica permiso para reproducir su contenido; las licencias y condiciones de reutilización se revisan por recurso.
- Se incorporan los dominios `MATHEMATICS`, `SCIENCE`, `ENGINEERING` y `COMMUNICATION`; el router determinista y el mapa semántico reconocen vocabulario de estas disciplinas. Finanzas y empresa utilizan dominios ya existentes.
- Cada paquete tiene versión propia y copia de actualización bajo `knowledge_packs/updates/`. El manifiesto se publica después de disponer de todos los payloads y contiene su SHA-256. El worker existente valida host/ruta, tamaño, ID, versión y hash; el instalador vuelve a verificar la caché antes de ingerirla.
- Finanzas: los conceptos de presupuesto, riesgo y diversificación son educativos; cotizaciones, tipos, impuestos y condiciones de productos deben consultarse en fuentes actuales.
- Empresa: ayudas, obligaciones y requisitos deben verificarse por jurisdicción y fecha. Ciencia e ingeniería: las fichas introducen principios; estudios, normas, tolerancias y diseños críticos requieren fuentes técnicas primarias y revisión profesional.
- Creatividad: se separan técnicas de diseño y licencias. Una licencia no implica que cualquier obra pueda reutilizarse; se comprueban titular y condiciones concretas.

La expansión añade 18 documentos y nueve paquetes; no incrementa por sí sola la capacidad de razonamiento del modelo. La evaluación debe medir recuperación por dominio, exactitud de atribución, frescura y comportamiento ante preguntas sin evidencia.


### Encoder neuronal multilingüe ONNX opcional — 27/11/2026

- Se incorpora `OnnxMultilingualEmbeddingEncoder`, ejecutado en el dispositivo mediante ONNX Runtime Android y ONNX Runtime Extensions. El encoder acepta texto y devuelve embeddings densos normalizados; la dimensión se obtiene de la salida real y no se fija en el código.
- Modelo elegido: `onnx-community/distiluse-base-multilingual-v2-merged-onnx`, derivado de `sentence-transformers/distiluse-base-multilingual-cased-v2`. La ficha del modelo base indica 50 idiomas y arquitectura Sentence-Transformers con pooling y capa densa; el artefacto ONNX integrado declara licencia MIT. El archivo seleccionado ocupa aproximadamente 136 MB.
- La descarga es explícita desde Ajustes, nunca silenciosa ni incluida en el APK. Se fija la revisión `469261b`, se limita el tamaño, se permiten únicamente los hosts HTTPS esperados de Hugging Face, se soportan rangos HTTP para reanudar, se conserva el parcial ante interrupciones y se exige SHA-256 `827d1a23e47f8c68a5788e58a06e137edf52aa568a6e4c852f4ce79f21b8a205` antes de activar el modelo.
- `NeuralEmbeddingIndex` persiste vectores densos por documento y fragmento en almacenamiento privado, con huella SHA-256 del contenido para detectar cambios y reconstruir solo lo necesario.
- `KnowledgeCorpusStore` mantiene el ranking léxico + semántico disperso actual cuando el modelo no está instalado. Si está disponible, combina 25 % léxico, 25 % semántico disperso y 50 % neuronal, y selecciona el pasaje más próximo. Los errores de carga o inferencia neuronal no deben interrumpir la recuperación: el buscador anterior queda como fallback.
- Dependencias fijadas: ONNX Runtime Android 1.28.0 y ONNX Runtime Extensions Android 0.13.0. sherpa-onnx 1.13.8 también incluye una biblioteca nativa con el mismo nombre SONAME; el empaquetado conserva una sola copia y la compatibilidad ABI debe confirmarse en ejecución. Se conservan las clases JNI en R8 para evitar fallos en release.
- Limitaciones pendientes: la descarga de 136 MB requiere espacio y conectividad; la primera indexación densa puede consumir CPU, RAM y batería. El encoder usa CPU por defecto; no se ha habilitado NNAPI ni aceleración específica. La calidad y latencia reales deben medirse en el dispositivo Samsung objetivo. La puntuación de capacidad no se incrementa hasta superar una prueba real de inferencia y evaluación de recuperación.
- El archivo ONNX se obtiene de una revisión fijada y su hash se compara con el valor registrado en la aplicación. La ficha de modelo y sus afirmaciones de rendimiento son información del publicador, no una evaluación independiente de ORION.


## Grafo neuronal asociativo — expansión de noviembre de 2026

El recuperador disperso incorpora un grafo curado de conceptos y asociaciones entre dominios. La activación directa reconoce aliases multilingües y una sola expansión de conexiones ponderadas débiles (0,24), por ejemplo pesca↔meteorología/geografía/ciencia; programación↔seguridad/matemáticas/ingeniería/datos; negocio↔finanzas/comunicación/creatividad. La expansión es intencionalmente de un salto para evitar propagación descontrolada y sesgos por asociaciones recursivas. Se usa únicamente para descubrir documentos candidatos: no modifica los veredictos de evidencia, autoridad, actualidad ni verificación. El grafo no se autoentrena; sus relaciones son explícitas, revisables y cubiertas por tests.

## Planificador multipaso y acciones locales

- Las salidas de pasos pueden encadenarse mediante `${stepId.output}` o `${stepId.summary}`; cada consumidor debe declarar la dependencia. Solo se interpolan resultados de pasos completados.
- La ejecución es por etapas: los pasos de lectura previos pueden ejecutarse primero; antes de una escritura no autorizada, ORION se pausa y presenta las entradas resueltas en Ajustes → Planes. La autorización se concede por paso y por ejecución, no se conserva.
- Acciones locales disponibles: crear tarea, guardar nota como registro local y programar recordatorio. Se almacenan en el repositorio de tareas de Room y usan el planificador de recordatorios Android; no requieren red.
- Las acciones locales y externas de escritura requieren riesgo MEDIUM o superior y confirmación contextual. Si una salida no se puede resolver, el paso queda bloqueado; un fallo de dependencia también bloquea descendientes.
- Los pasos no compatibles no se muestran como ejecutables. Los resultados y salidas se guardan para reanudación; tras un cierre, operaciones de resultado incierto requieren revisión explícita.

## Perfiles adaptativos del modelo local

La configuración de ORION define cuatro perfiles offline: Ligero, Equilibrado, Potente y Flagship. El modo automático recomienda un perfil a partir de la RAM física total del dispositivo (no de la RAM libre momentánea); el modo manual permite guardar una preferencia. Umbrales recomendados: 6, 8, 12 y 16 GB respectivamente. Los tamaños mostrados son estimaciones de descarga y no equivalen a RAM de ejecución: KV cache, contexto, buffers nativos y el propio Android requieren memoria adicional.

- Ligero: Qwen2.5-1.5B Q4_K_M (modelo actual), con alternativas candidatas Qwen3.5 0.8B/1.5B; ~1–1.3 GB; 4–6 GB de RAM; voz y dispositivos modestos.
- Equilibrado: Qwen2.5-3B o Qwen3.5 2B/4B Q4_K_M; ~2–2.7 GB; 8 GB de RAM.
- Potente: Qwen3.5-4B, Phi-4-mini o Gemma 4 E4B; ~2.5–3.6 GB; 8–12 GB de RAM.
- Flagship: Qwen3.5-9B o modelos 7–8B Q4; ~5–6 GB; 12–16 GB de RAM.

Estado de integración: el catálogo, selector manual/automático y fallback seguro están implementados. Solo Ligero está marcado como operativo porque es el único artefacto GGUF actualmente descargable, verificado e integrado en llama.cpp. Los otros perfiles son candidatos y no se activan ni se presentan como instalados hasta incorporar archivos GGUF concretos, URLs/versiones inmutables, hashes, licencias, parámetros de contexto/hilos y pruebas de memoria/latencia. Si el perfil recomendado o solicitado aún no está operativo, el motor permanece en Ligero.


### Protección de memoria del motor LLM — 28/11/2026

- Antes de inicializar llama.cpp se consulta la RAM física total y la RAM disponible real mediante `ActivityManager.MemoryInfo`; la RAM total por sí sola ya no autoriza la carga.
- `ModelMemoryPolicy` estima el footprint a partir del tamaño GGUF, contexto, margen de buffers nativos y reserva para Android/voz. La carga se bloquea si no hay margen libre suficiente o si la medición es incoherente.
- Si el motor nativo lanza `OutOfMemoryError` durante la inicialización, ORION convierte el fallo en un error operativo controlado y evita continuar con una sesión marcada como cargada. Esto no puede interceptar un cierre forzado del sistema por OOM nativo; por eso la comprobación previa es la defensa principal.
- Pruebas unitarias: límites de RAM disponible, snapshots imposibles, crecimiento de footprint con modelos/contextos mayores y reducción escalonada de contexto. El contexto no se amplía por encima de lo solicitado.
- La política se aplica al motor LLM que usa actualmente el runtime. Los artefactos superiores aún no se declaran operativos: antes de activarlos se deben fijar sus archivos GGUF concretos, revisión, tamaño exacto, SHA-256, licencia y compatibilidad real con la versión de llama.cpp. No se sustituye una verificación criptográfica por una simple cabecera GGUF.
- La última compilación inspeccionada fallaba por tres errores de Kotlin ajenos a este bloque (interpolación literal de referencias de pasos, tratamiento de pares de conflictos y mutex compartido no declarado); los tres se han corregido y quedan pendientes los nuevos resultados CI.


### Activación de los tres perfiles GGUF superiores — 28/11/2026

Se han integrado tres artefactos descargables, con URL de revisión fijada y SHA-256 comprobado antes de activarlos:

- Equilibrado: Qwen2.5-3B-Instruct Q4_K_M, aproximadamente 2,1 GB, contexto 4.096, RAM física recomendada 8 GB.
- Potente: Qwen2.5-7B-Instruct Q4_K_M, aproximadamente 4,68 GB, contexto 4.096, RAM física recomendada 12 GB.
- Flagship: Qwen2.5-14B-Instruct Q3_K_M, aproximadamente 7,34 GB, contexto 2.048, RAM física recomendada 16 GB. Se reduce la cuantización y el contexto para contener el consumo; sigue siendo un perfil experimental en móviles.

El instalador usa descargas reanudables, HTTPS, espacio libre mínimo, SHA-256, cabecera GGUF y sustitución con backup. Un artefacto con hash incorrecto nunca se activa. Los modelos superiores no se descargan automáticamente ni se incluyen en el APK.

Ajustes permite descargar e instalar cada perfil. La preferencia manual o automática se guarda; el perfil descargado se selecciona al iniciar ORION de nuevo. El runtime usa el archivo correspondiente y compone el prompt con el perfil realmente seleccionado. Si el archivo no existe, la RAM física no alcanza el umbral o la RAM libre no supera la estimación conservadora de carga, se usa Ligero.

La protección de memoria comprueba RAM disponible antes de construir el motor y vuelve a comprobarla inmediatamente antes de cargar llama.cpp. Estima pesos, KV cache/contexto, buffers y margen de Android. El contexto de Flagship se reduce a 2.048 tokens y el batch de los perfiles superiores a 128. Si la memoria cae entre la comprobación inicial y la carga, se rechaza la inicialización de forma controlada. Una excepción OutOfMemoryError de inicialización se transforma en error operativo; no puede interceptarse un cierre forzado del proceso por el sistema ante agotamiento nativo extremo.

La validación de integridad completa se realiza fuera del hilo principal. La verificación positiva se cachea durante la sesión del motor para evitar recalcular el SHA-256 de archivos de varios GB en cada carga. Si se reemplaza el archivo, la instancia debe reconstruirse para volver a verificarlo.

Pruebas añadidas: bandas de RAM física, RAM libre real, snapshots incoherentes, estimación creciente con modelo/contexto, reducción escalonada de contexto, catálogo de artefactos, SHA-256, URLs HTTPS y prompt correspondiente al perfil activo.

La presencia del artefacto y la compatibilidad del código no sustituyen un benchmark real en cada dispositivo. Quedan por medir en hardware: latencia de primer token, tokens/segundo, pico de RAM nativa, temperatura, consumo y estabilidad prolongada. El perfil Flagship puede no ser utilizable en móviles de 16 GB si el sistema no deja suficiente RAM libre; en ese caso el fallback a Ligero es intencionado.


## Inferencia híbrida gratuita y sin carga de modelos grandes en el móvil

Se incorpora una capa de ejecución híbrida con tres modos configurables:

- **Automático**: el perfil local se selecciona según RAM física, RAM disponible, instalación y política de memoria. Ligero atiende voz, intenciones, tareas, confirmaciones y respuestas rápidas; Equilibrado/Potente/Flagship atienden código y razonamiento medio cuando caben con margen. Una política determinista reserva el remoto para investigación profunda, planes largos multi-fase y análisis expertos; el perfil Ligero también puede delegar código/razonamiento medio si el usuario ha consentido el remoto.
- **Solo local**: no transmite mensajes ni contexto a un servidor remoto.
- **Solo remoto**: evita inicializar el LLM local. Si el servidor no responde, la petición falla de forma explícita y no se deriva silenciosamente a otro motor.

El cliente remoto no requiere SDK ni proveedor comercial: utiliza HTTP JSON compatible con `/v1/chat/completions`, por lo que puede conectarse a un servidor autoalojado como llama.cpp server o vLLM. El software de estos motores es libre; ejecutar el servidor requiere hardware y puede implicar coste eléctrico o de alojamiento. Los servicios externos de pago no son obligatorios.

**Privacidad y seguridad**: el uso remoto exige activar el motor y aceptar expresamente el envío del mensaje y del contexto de sistema necesario. No se envía la memoria completa ni el historial íntegro. Las claves API se cifran con Android Keystore. Se exige HTTPS para hosts externos y solo se permite HTTP en localhost o rangos privados. Las solicitudes tienen límites de contexto y tamaño, timeout y máximo de salida. El modo automático vuelve al modelo local si falla el servidor; nunca cambia de proveedor ni comparte datos adicionales.

En modo Automático, ORION conserva el mejor perfil local que el dispositivo pueda ejecutar con margen de seguridad y solo deriva al remoto las clases de trabajo que la política marca como superiores a esa capacidad. En modo Solo remoto, el LLM local no se carga; los perfiles avanzados siguen disponibles para ejecución local cuando el usuario elige Solo local y el dispositivo supera las comprobaciones de memoria. El enrutamiento remoto es por capacidad lógica, no una promesa de rendimiento: depende del modelo y recursos del servidor configurado.

**Estado de esta fase**: cliente HTTP compatible, almacenamiento cifrado de clave, controles de modo/consentimiento, enrutamiento por complejidad e integración con el motor conversacional añadidos. Pendiente: ejecutar CI, probar interoperabilidad real contra llama.cpp/vLLM y verificar la experiencia en dispositivo. La cancelación de solicitudes remotas ya desconecta la conexión activa. No se considera validado en producción hasta superar esas pruebas.


### Siguiente bloque de optimización de ejecución

1. **Wake word offline**: activar detección local de palabra de activación, con umbral configurable, ventana de escucha limitada, protección frente a falsos positivos y control visible de micrófono. No se enviará audio a servidores para detectar la activación.
2. **Aceleración Snapdragon**: auditar la versión de llama.cpp y sus backends Android antes de tocar el runtime. Medir Vulkan/Adreno y evaluar NPU/Hexagon solo mediante un backend soportado y verificable; no prometer aceleración por NPU sin integración real.
3. **Backend alternativo LiteRT-LM + Gemma**: crear una prueba de concepto aislada y comparar calidad, latencia, memoria pico, consumo, temperatura, compatibilidad de dispositivos, licencias y tamaño de APK/modelos frente a llama.cpp. No sustituir el backend actual hasta superar pruebas reproducibles y conservar un fallback estable.
4. **Planificador multipaso**: auditar el chaining de salidas reales, handlers locales de tareas/notas/recordatorios, aprobación contextual de un solo uso, pausa/reanudación y recuperación tras cierre. Los pasos que escriben en servicios externos deben seguir bloqueados hasta aprobación individual explícita.


## Despertador offline — evaluación de viabilidad (2026-11-28)

La dependencia sherpa-onnx existente incluye API Kotlin para keyword spotting en streaming, por lo que no se requiere un SDK adicional. La revisión del catálogo oficial de modelos KWS no encontró un modelo preentrenado español: los publicados cubren chino, inglés y chino+inglés. Moonshine Base ES v2 es un modelo ASR y no puede tratarse como KWS sin un adaptador/modelo específico validado. No se ha activado escucha permanente ni un servicio de micrófono en segundo plano. La integración queda condicionada a localizar o entrenar un modelo español con licencia y calidad verificables. El contrato de seguridad, consentimiento, servicio foreground, batería y validación acústica está documentado en [WAKE_WORD_READINESS.md](WAKE_WORD_READINESS.md).


## Revisión de limitaciones: wake word y aceleración Android (2026-11-28)

Se ha localizado un candidato KWS español compatible con sherpa-onnx: jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08. Su licencia declarada es Apache-2.0 y permite palabras configurables mediante BPE, pero su evaluación publicada es pequeña y no incluye «ORION». Quedan pendientes fijar revisión inmutable, hashes, tokenización BPE, pruebas de falsos disparos y validación acústica peninsular/andaluza. No activar el micrófono en segundo plano hasta cerrar esas comprobaciones y el flujo de consentimiento. Ver WAKE_WORD_READINESS.md.

La implementación actual de LlamaCppLanguageModel solicita n_gpu_layers=0; por tanto, usa CPU. No se debe cambiar ese valor sin confirmar que el AAR/JNI incorpora un backend GPU. La ruta de mejora prioriza auditar y habilitar KleidiAI en arm64, y después prototipar Vulkan/OpenCL para Adreno con detección real, fallback y mediciones. LiteRT-LM dispone de API Kotlin estable y backends CPU/GPU/NPU en Android, así que se evaluará como backend alternativo en un prototipo separado, sin sustituir llama.cpp hasta superar pruebas comparativas. Hexagon/NPU no se declarará compatible sin runtime y pruebas concretas. Ver LLAMA_ANDROID_ACCELERATION_AUDIT.md.


## Privacidad de inferencia remota — revisión 2026-11-28

La interfaz de consentimiento se ha ajustado para describir el payload real del cliente remoto: prompt de sistema y hasta los últimos 12 mensajes, con límite de 8.000 caracteres por mensaje. El prompt de sistema puede contener instrucciones y contexto que ORION haya preparado para la consulta. No se envía el historial completo ni archivos adjuntos. La autorización sigue siendo explícita y persistente hasta que el usuario la desactive; el modo Solo local no transmite contenido.

La ruta remota continúa siendo opcional. No se requiere proveedor comercial: puede apuntar a un servidor autoalojado compatible. Antes de activar la delegación automática en producción, quedan pendientes pruebas de redacción/minimización del prompt y pruebas unitarias que inspeccionen el JSON exacto transmitido.


## Pruebas de minimización del payload remoto

Se ha extraído la construcción del JSON remoto a `RemoteInferencePayload`. Las pruebas unitarias cubren el máximo de 12 mensajes recientes, los límites de 24.000 caracteres para el prompt de sistema y 8.000 por mensaje, y el mapeo de roles OpenAI. La prueba confirma el contrato del payload; no demuestra por sí sola el comportamiento de un servidor externo ni la seguridad de red extremo a extremo.


## Backend LLM Android — verificación de código nativo

El wrapper `llamacpp-kotlin 0.4.0` integrado en ORION selecciona bibliotecas CPU ARM64 según las features del procesador (incluye variantes DotProd/I8MM). Su JNI informa `gpu=false` y `reasonNoGPU=Currently not supported`; el CMake declara `LM_GGML_USE_CPU` y no compila OpenCL/Vulkan/Hexagon. Por tanto, ORION dispone de dispatch CPU optimizado, no de aceleración GPU/NPU en el motor local actual.

El paquete comunitario Android de llama.cpp con Hexagon/OpenCL es CLI/servidor, no un AAR JNI. Su digest de release permite fijar un artefacto para investigación, pero no demuestra seguridad de cadena de suministro ni compatibilidad con el runtime actual. La integración debe realizarse como un backend experimental independiente o un port del CMake/JNI; mantener el motor CPU como fallback y no alterar el modelo predeterminado hasta completar tests y benchmarks.


## Seguridad del planificador — reintentos de escrituras

Se ha endurecido la política de reintentos: solo se reintentan automáticamente pasos de razonamiento, lectura, cálculo y lectura de conectores. Una clave de idempotencia declarada por el plan no basta para repetir automáticamente una escritura externa, porque el proveedor puede no aplicar esa clave o la respuesta puede perderse después de completar la operación. Las escrituras externas fallidas o interrumpidas quedan para verificación y reintento explícito del usuario. Se añadió una prueba que confirma que incluso un paso de escritura con clave de idempotencia solo se ejecuta una vez automáticamente.
