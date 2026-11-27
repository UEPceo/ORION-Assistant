# ORION — Arquitectura del cerebro autónomo y conocimiento verificable

Estado: núcleo local y recuperación web explícita implementados en código; validación CI pendiente. El corpus local no incluye todavía packs de conocimiento y los proveedores multimedia siguen sin configuración.

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

Se han incorporado contratos de evidencia, enrutador determinista por dominio, repositorio local privado persistente, motor de recuperación, composición de contexto con procedencia y política de aprendizaje. Los veredictos incluyen SUPPORTED, NEEDS_CORROBORATION, PRIMARY_SOURCE_REQUIRED, STALE, AGE_UNCONFIRMED, CONFLICTING, NO_EVIDENCE e INVALID_TIMESTAMP. El núcleo ya se invoca desde el contexto de cada turno de voz. El corpus local comienza vacío: no se simula que existan fuentes descargadas ni se presenta el conocimiento del modelo como evidencia. El corpus se guarda en almacenamiento privado mediante escritura atómica y la recuperación local es léxica, no semántica/vectorial. La búsqueda web solo se activa ante una petición explícita. El contexto RAG se limita a 1.800 caracteres para proteger la ventana reducida del modelo local.

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

### Registro técnico de autodiagnóstico v1.0

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
| RAG local | Implementado, corpus inicialmente vacío | Búsqueda léxica; falta índice semántico/híbrido y packs versionados |
| Búsqueda web | Búsqueda explícita + lectura HTML acotada | Sin navegador completo, JS, PDF, múltiples buscadores ni cobertura amplia de fuentes |
| Verificación | Política conservadora implementada | Clasificador de autoridad pequeño; conflictos y fechas aún parciales |
| Aprendizaje | Ingesta curada disponible | Sin flujo UI completo de revisión, versionado y actualización programada |
| Autodiagnóstico | Registro local y heurísticas | No captura todos los errores ni analiza automáticamente todo el código |
| Tareas y recordatorios | Operaciones deterministas | Falta planificación de varios pasos y seguimiento de resultados |
| Conectores | Integraciones acotadas | Faltan acciones completas de escritura y más proveedores |
| Programación | Conector GitHub y capacidades del modelo | Falta entorno de ejecución/sandbox y ciclo integrado de pruebas/revisión |
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
- El catálogo registra dominio, jurisdicción, autoridad y cadencia de revisión. Una URL de catálogo no se considera evidencia hasta recuperarla, fecharla y verificarla.
- La recuperación local usa ponderación por coincidencia en título, extracto y cuerpo, frecuencia inversa de documento, cobertura de términos y desempate por autoridad/fecha. Es una mejora léxica tipo TF-IDF ponderado, no un motor vectorial ni búsqueda semántica.
- Sigue pendiente extracción PDF, varios buscadores, corroboración avanzada, fecha de modificación robusta y defensa completa contra DNS rebinding.

### 2. Planificación multipaso
- Contratos tipados para planes, pasos, dependencias, riesgo, presupuestos, estados e idempotencia.
- Validador de dependencias y ciclos, límite de pasos, reintentos y duración.
- Coordinador secuencial: una dependencia fallida bloquea los pasos posteriores; las excepciones se convierten en resultados controlados.
- Las escrituras externas, mensajes, llamadas, borrados y compras requieren aprobación específica para los IDs de paso de esa ejecución. No hay autorización persistente.
- El contexto de conversación activa instrucciones de planificación cuando detecta una petición multipaso.
- Limitación: el coordinador todavía no está conectado al dispatcher de conectores ni a un almacén persistente. El modelo puede proponer un plan, pero ORION no debe afirmar que lo ha ejecutado.

### 3. Conocimiento offline
- Catálogo de seis packs previstos: normativa España/UE, seguridad España, ciberseguridad, Android/Kotlin, Python/Internet y pesca recreativa Andalucía.
- Cada pack declara versión, dominios, jurisdicción, fuentes, licencia y frecuencia de revisión.
- **Estado real: catálogo únicamente. No hay contenido empaquetado ni packs instalados.** No se contabiliza un pack como conocimiento offline hasta incluir sus documentos, hashes, licencias y pruebas de actualización.

### 4. Modelos y razonamiento
- Catálogo de perfiles separa modelo ligero local, futuro modelo local avanzado y futuro proveedor remoto consentido.
- Selector por modo, capacidades solicitadas y memoria disponible. Si ningún perfil operativo cumple los requisitos, devuelve ausencia de motor compatible en lugar de prometer capacidad.
- Qwen2.5 1.5B Q4 continúa siendo el único perfil operativo actual, con contexto 2048 y uso ligero. No es un motor de razonamiento avanzado ni multimodal.
- La selección multi-modelo, cuantizaciones alternativas y evaluación de calidad/rendimiento en dispositivo siguen pendientes.

### 5. Herramientas y multimedia
- Catálogo de capacidades y limitaciones para Gmail, GitHub, Maps, PescaIA, Calendar, archivos, navegador y sandbox de programación.
- Se explicita que GitHub no equivale a compilar código, que el lector web no es navegador completo y que el sandbox no existe aún.
- Los contratos multimedia y la puerta de consentimiento de un solo uso siguen preparados, pero no se han conectado proveedores ComfyUI ni una interfaz de progreso/resultado. Generación y análisis de imagen/vídeo siguen sin estar operativos.

### 6. Evaluación continua
- Evaluador ponderado por áreas con puntuación 0–10, brechas y criterio global: todas las áreas deben llegar a 8/10.
- Línea base conservadora calculada desde capacidades realmente implementadas: conocimiento 6/10; planificación 4/10; conocimiento offline 4/10; razonamiento/modelo 4/10; herramientas 4/10; multimedia 4/10; fiabilidad/seguridad 6/10. Mínimo global: **4/10**; media aproximada: **4,6/10**.
- Esta puntuación es una línea base de ingeniería basada en criterios explícitos, no una prueba independiente de inteligencia. Se recalculará cuando haya pruebas automatizadas y resultados de ejecución.
- La meta 8/10 no está alcanzada. El mayor déficit está en contenido offline real, ejecución multipaso conectada, motores avanzados y multimedia operativa.

### Criterio de aceptación para declarar 8/10
Cada área debe disponer de criterios reproducibles, pruebas unitarias e integración, evidencia de ejecución y cobertura suficiente. Ninguna capacidad marcada como catálogo, contrato, configuración pendiente o no implementada puede sumar como operativa. La puntuación mínima entre áreas gobierna el resultado global; una media alta no puede ocultar un área débil.
