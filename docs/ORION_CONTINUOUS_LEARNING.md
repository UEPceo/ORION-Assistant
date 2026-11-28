# ORION — Aprendizaje continuo y mantenimiento del conocimiento

Estado: infraestructura de conocimiento y actualización de packs implementada; ciclo general de revisión, versionado y evaluación todavía incompleto.

## Qué significa aprender

El aprendizaje continuo de ORION no significa modificar automáticamente los pesos del LLM después de cada conversación. Significa mantener un corpus consultable que mejora de forma controlada, incorporar correcciones, actualizar documentación, detectar contradicciones y comprobar que las actualizaciones no degradan respuestas anteriores.

La memoria personal, el conocimiento general y el contenido de proyectos deben seguir siendo almacenes separados.

## Capacidades existentes verificadas en el repositorio

- Dieciséis packs locales por ámbitos, incluidos programación, informática, ingeniería, ciencia, normativa, seguridad, negocio, productividad y pesca.
- Instalación local de packs y actualización periódica semanal bajo red no medida y batería suficiente.
- Manifiesto de actualización con allowlist de paquetes, versiones y SHA-256; los paquetes se descargan a almacenamiento temporal y se comprueba su integridad antes de instalarlos.
- Corpus local privado con escritura atómica, fragmentación documental, ranking léxico y búsqueda semántica dispersa.
- Embeddings ONNX multilingües opcionales, con instalación condicionada a red, batería y espacio.
- Contratos de evidencia con editor, URL canónica, autoridad, dominio, fecha de recuperación/publicación, hash y jurisdicción.
- Verificador de evidencia, detección conservadora de conflictos numéricos y estados de información caducada o no corroborada.
- Ingesta controlada que rechaza texto generado por el propio modelo como evidencia y exige HTTPS y límites de tamaño.
- Actualizaciones de los packs principales se aplazan hasta que ASR, LLM y TTS estén instalados, para no competir con el primer arranque.

## Hallazgos y límites actuales

1. La actualización semanal está centrada en los packs declarados en un manifiesto; no es un rastreador universal de Internet ni aprende automáticamente de cada conversación.
2. El modelo no reentrena sus pesos. El conocimiento nuevo se incorpora al corpus recuperable.
3. `KnowledgeIngestionService` persiste documentos que reciben `ACCEPT_FOR_REVIEW`; el nombre sugiere una cola de revisión, pero no existe todavía un flujo completo de revisión humana antes de incorporarlos al corpus. Hay que corregir esa semántica.
4. Las correcciones del usuario sin aprobación se guardan como no verificadas; falta una bandeja de revisión y una UI clara para aprobar, corregir, rechazar o comparar versiones.
5. El corpus usa hash de contenido y escritura atómica, pero falta un historial completo de versiones por documento, comparación de cambios y rollback explícito.
6. La detección automática de conflictos es deliberadamente limitada; no demuestra que dos afirmaciones sean equivalentes ni que una fuente sea correcta.
7. No existe aún una suite de regresión de respuestas que mida precisión, citas, frescura y calidad antes/después de cada actualización.

## Ciclo objetivo de aprendizaje

1. **Captura controlada:** recoger documentos de fuentes permitidas, documentación oficial, repositorios autorizados y correcciones expresamente aportadas por el usuario.
2. **Normalización:** canonicalizar URL, editor, fecha, idioma, dominio, jurisdicción y versión; calcular SHA-256; deduplicar.
3. **Clasificación:** separar fuente primaria, técnica, revisada, secundaria, comunidad, material aportado por el usuario y salida generada por el modelo.
4. **Cuarentena:** almacenar cada candidato en una bandeja separada del corpus activo. El contenido de repositorios, páginas, issues y documentos se considera dato no confiable, nunca instrucción de sistema.
5. **Verificación:** comprobar integridad, autoridad, fechas, corroboración, conflictos y límites del dominio. En normativa y seguridad se exige fuente primaria oficial.
6. **Revisión:** permitir aprobar, editar metadatos, marcar como no verificado o rechazar. Las correcciones personales no se convierten en hechos generales por el mero hecho de ser aprobadas por el usuario.
7. **Publicación:** solo los candidatos que superen la política correspondiente entran en el corpus activo. Mantener versión anterior y permitir rollback.
8. **Evaluación:** ejecutar pruebas de recuperación y respuestas sobre un conjunto de preguntas de referencia; comparar citas, vigencia, conflictos y regresiones.
9. **Mantenimiento:** detectar documentos caducados, revisar fuentes periódicamente y retirar lógicamente documentos obsoletos sin perder trazabilidad.

## Aprendizaje de programación

La base de conocimiento de programación debe actualizarse de forma específica por stack:
- versión de Kotlin, Android Gradle Plugin, Gradle, Compose, Room, APIs y dependencias;
- documentación oficial y changelogs de la versión realmente usada por cada proyecto;
- patrones de arquitectura, vulnerabilidades, notas de seguridad y compatibilidad;
- decisiones técnicas confirmadas, tests y resultados CI del propio proyecto.

Los resultados de compilación y tests son evidencia del estado de un commit concreto, no prueba universal de que todo el proyecto esté libre de errores. ORION debe guardar la relación commit → workflow → tests → resultado.

## Privacidad y control

- No incorporar conversaciones, correos, notas, contactos ni datos privados a conocimiento general sin consentimiento específico.
- No usar texto del modelo como fuente de verdad ni auto-confirmar sus propias respuestas.
- No mezclar memoria personal con corpus público.
- Permitir pausar las actualizaciones y mostrar origen, fecha, tamaño y destino de cada incorporación.
- Mantener logs sin secretos ni contenido privado innecesario.

## Criterios de aceptación

El aprendizaje continuo se considerará operativo cuando una fuente nueva pueda pasar por captura, cuarentena, verificación, revisión, publicación y rollback; cuando una corrección quede trazable; y cuando una actualización ejecute una suite de regresión sin ocultar fallos ni convertir evidencia débil en información verificada.
