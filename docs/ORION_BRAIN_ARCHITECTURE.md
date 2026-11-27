# ORION — Arquitectura del cerebro autónomo y conocimiento verificable

Estado: núcleo modular implementado e integrado en el contexto de cada turno; conectores de fuentes externas e ingesta avanzada pendientes.

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

Se han incorporado contratos de evidencia, enrutador determinista por dominio, repositorio local privado persistente, motor de recuperación, composición de contexto con procedencia y política de aprendizaje. Los veredictos incluyen SUPPORTED, NEEDS_CORROBORATION, PRIMARY_SOURCE_REQUIRED, STALE, CONFLICTING, NO_EVIDENCE e INVALID_TIMESTAMP. El núcleo ya se invoca desde el contexto de cada turno de voz. El corpus local comienza vacío: no se simula que existan fuentes descargadas ni se presenta el conocimiento del modelo como evidencia. La recuperación actual es léxica y local; todavía no es búsqueda semántica/vectorial ni recuperación web.

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
