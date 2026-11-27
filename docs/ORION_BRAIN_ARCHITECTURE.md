# ORION — Arquitectura del cerebro autónomo y conocimiento verificable

Estado: núcleo modular local implementado e integrado en el contexto de cada turno; fuentes externas y proveedores multimedia requieren configuración e integración.

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

Se han incorporado contratos de evidencia, enrutador determinista por dominio, repositorio local privado persistente, motor de recuperación, composición de contexto con procedencia y política de aprendizaje. Los veredictos incluyen SUPPORTED, NEEDS_CORROBORATION, PRIMARY_SOURCE_REQUIRED, STALE, CONFLICTING, NO_EVIDENCE e INVALID_TIMESTAMP. El núcleo ya se invoca desde el contexto de cada turno de voz. El corpus local comienza vacío: no se simula que existan fuentes descargadas ni se presenta el conocimiento del modelo como evidencia. El corpus se guarda en almacenamiento privado de la app mediante escritura atómica y la recuperación actual es léxica y local; todavía no es búsqueda semántica/vectorial ni recuperación web. El contexto RAG se limita a 1.800 caracteres para proteger la ventana de 2.048 tokens del modelo local.

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

## Fuentes externas y aprendizaje

OrionBrainRuntime acepta KnowledgeSource externos inyectables además del corpus local. En la configuración actual del runtime de la app solo se registra el corpus local; no hay todavía un motor general de búsqueda web activo. El aprendizaje persiste documentos con procedencia y hash, pero no modifica automáticamente los pesos del modelo. Los proveedores de búsqueda, indexación y actualización deben registrarse y someterse a los mismos controles de autoridad, frescura, contradicción y seguridad.

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
