# ORION — Aprendizaje continuo y mantenimiento del conocimiento

Estado: ingesta explícita desde URL, clasificación conservadora de fuentes, cuarentena, revisión, publicación verificada/no verificada, versionado, rollback y evaluador determinista implementados. La suite de regresión aún no bloquea publicaciones.

## Qué significa aprender
No reentrena automáticamente pesos del LLM. Mejora el corpus recuperable, mantiene correcciones, fuentes, versiones y pruebas. Memoria personal, conocimiento general y datos de proyectos permanecen separados.

## Implementado
- Packs locales por ámbitos, actualización semanal bajo red/batería adecuados, manifiestos allowlist y SHA-256.
- Corpus local, fragmentación, búsqueda léxica y semántica, embeddings ONNX opcionales.
- Evidencia con URL, publisher, autoridad, dominio, fecha, hash y jurisdicción.
- Cuarentena persistente; candidatos generados por modelo rechazados como evidencia. Comando explícito «aprende de esta fuente <URL>» para leer páginas HTTPS con límites, rechazar destinos privados/redirecciones inseguras y enviar el contenido a cuarentena. Los dominios allowlisted solo reciben autoridad oficial/técnica si coinciden exactamente o como subdominio real y el ámbito consultado está autorizado; el resto queda UNKNOWN y no verificado.
- Revisión explícita: aprobar verificado, publicar no verificado, rechazar; historial limitado y rollback.
- Evaluador `KnowledgeRegressionEvaluator`: evidencia esperada recuperada, citas no recuperadas, frescura, dominio, evidencia primaria requerida y respuesta vacía. Resultado reproducible y score por conjunto; pruebas cubren éxito y regresiones.
- No se incorpora automáticamente correo, conversaciones ni información privada. Pegar un enlace o hacer una pregunta no activa el aprendizaje: exige una orden explícita.

## Límites
- El manifiesto de packs no es crawler universal.
- No hay entrenamiento de pesos.
- La suite tiene API y tests; falta corpus de casos de referencia por dominio, conexión a consultas reales e invocación tras actualización/antes de publicar desde UI. Actualmente no bloquea publicaciones.
- La lectura de una URL no convierte su contenido en verdad: páginas no incluidas en el catálogo oficial/técnico se guardan como no verificadas y requieren revisión; las páginas con autoridad reconocida también permanecen en cuarentena hasta aprobación.
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
