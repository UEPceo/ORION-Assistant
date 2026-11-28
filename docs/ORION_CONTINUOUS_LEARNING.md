# ORION — Aprendizaje continuo y mantenimiento del conocimiento

Estado: ingesta en cuarentena, revisión explícita, publicación verificada/no verificada, versionado previo y rollback implementados. Se añadió evaluador determinista de regresión; la ejecución sobre consultas reales aún debe conectarse al pipeline de recuperación/respuesta.

## Qué significa aprender
No reentrena automáticamente pesos del LLM. Mejora el corpus recuperable, mantiene correcciones, fuentes, versiones y pruebas. Memoria personal, conocimiento general y datos de proyectos permanecen separados.

## Implementado
- Packs locales por ámbitos, actualización semanal bajo red/batería adecuados, manifiestos allowlist y SHA-256.
- Corpus local, fragmentación, búsqueda léxica y semántica, embeddings ONNX opcionales.
- Evidencia con URL, publisher, autoridad, dominio, fecha, hash y jurisdicción.
- Cuarentena persistente; candidatos generados por modelo rechazados como evidencia.
- Revisión explícita: aprobar verificado, publicar no verificado, rechazar; historial limitado y rollback.
- Evaluador `KnowledgeRegressionEvaluator`: expected evidence, citas no recuperadas, frescura, dominio, evidencia primaria requerida y respuesta vacía. Resultado reproducible y score por conjunto.
- No se incorpora automáticamente correo, conversaciones ni información privada.

## Límites
- El manifiesto de packs no es crawler universal.
- No hay entrenamiento de pesos.
- La suite de regresión tiene API y tests, pero falta crear corpus de casos de referencia por dominio e invocarla tras actualizaciones/antes de publicar desde UI.
- La evaluación mide condiciones objetivas, no la verdad semántica de la respuesta.

## Ciclo objetivo
Capturar → normalizar/deduplicar → clasificar → cuarentena → verificar autoridad/frescura/conflictos → revisión humana → publicar versionado → evaluar regresión → mantener/retirar con rollback.

## Programación
Versiones de Kotlin/AGP/Gradle/dependencias; changelogs oficiales; seguridad y compatibilidad. Guardar vínculo commit→workflow→tests→resultado. El test CI solo valida el commit y el entorno que ejecutó.

## Privacidad
- No usar datos privados para conocimiento general sin consentimiento específico.
- El texto de modelo/repositorio es dato no confiable, nunca instrucciones del sistema.
- Separar corpus, memoria personal y proyectos.
- Mostrar procedencia y permitir pausar; minimizar logs.
