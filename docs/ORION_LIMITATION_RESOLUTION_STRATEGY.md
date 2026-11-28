# ORION — Estrategia para superar limitaciones técnicas

## Principio rector

Una limitación detectada no se acepta como techo definitivo. Debe convertirse en un problema de ingeniería con alternativas, criterios de selección, plan de validación y una ruta de fallback. No se debe declarar una capacidad operativa hasta demostrarla.

## Método obligatorio para cada limitación

1. **Describir el límite real:** separar restricción del hardware, Android, proveedor, modelo, arquitectura, coste o implementación incompleta. No atribuir al sistema límites que no se hayan medido.
2. **Determinar el impacto:** calidad, latencia, memoria, batería, privacidad, seguridad, coste, disponibilidad y experiencia del usuario.
3. **Investigar alternativas:** al menos una vía principal y una alternativa de respaldo cuando sea razonable. Priorizar software libre, estándares abiertos, ejecución local y compatibilidad con dispositivos modestos.
4. **Diseñar una arquitectura intercambiable:** interfaces estables, adaptadores, detección de capacidades y fallback. Evitar acoplar ORION a un proveedor o modelo único.
5. **Implementar por fases:** prototipo aislado, integración, pruebas automatizadas, validación en dispositivo/servicio real y activación gradual.
6. **Medir antes de decidir:** benchmarks reproducibles, consumo de RAM/CPU, latencia, tasa de error, calidad, coste y comportamiento bajo fallos.
7. **Conservar controles:** permisos mínimos, consentimiento explícito para datos remotos, revisión humana de escrituras sensibles, procedencia de evidencia, registro y reversión.
8. **Comunicar el estado con precisión:** implementado, experimental, requiere configuración, pendiente de validación o no implementado. Una alternativa investigada no equivale a una función disponible.

## Líneas de evolución prioritarias

### Modelos y contexto
- Mantener un modelo ligero local como base offline.
- Incorporar perfiles de mayor capacidad solo cuando la memoria real del dispositivo lo permita; descargar bajo demanda, comprobar hash, liberar recursos y permitir volver al perfil ligero.
- Evaluar modelos 7B–14B cuantizados, LiteRT-LM/GPU/NPU y llama.cpp optimizado como backends intercambiables. No asumir que una NPU o un acelerador es compatible sin comprobarlo en el SoC real.
- Ampliar contexto progresivamente (4096, 8192 o más) solo tras medir memoria y latencia. Combinar resumen jerárquico, memoria episódica y RAG; no resolver todo aumentando tokens.
- Permitir inferencia remota opcional con consentimiento contextual, minimización de datos, límites de payload, endpoint configurable y fallback explícito.

### Ingeniería de software
- Completar el ciclo requisito → inspección → plan → diff → aprobación → runner aislado → logs → reparación acotada → nuevas pruebas → revisión final.
- Ejecutar código arbitrario solo en runners efímeros y aislados, nunca dentro del proceso Android.
- Presupuestar iteraciones, tiempo y recursos. Cada nuevo parche requiere revisión y autorización conforme al riesgo.
- Si CI o pruebas fallan, recuperar logs, explicar el fallo y proponer un parche; no afirmar éxito ni auto-merge sin evidencia.

### Veracidad y aprendizaje
- Separar memoria personal, conocimiento general y datos de proyectos.
- Mantener procedencia, jurisdicción, fecha, autoridad, hash y caducidad de evidencias.
- Ampliar corpus editorial con afirmaciones verificadas, contradicciones, fuentes primarias, información histórica/caducada y preguntas sin respuesta.
- Priorizar verificaciones deterministas: cálculos, APIs oficiales, ejecución reproducible, hashes, validación de esquemas y comparaciones exactas.
- El aprendizaje de corpus no se debe describir como entrenamiento de pesos. La publicación de conocimiento sigue sometida a revisión y regresión.

### Planificador y autonomía
- Añadir handlers tipados por dominio y contratos de entrada/salida; no crear una operación universal sin límites.
- Resolver referencias y validar todas las entradas antes de mostrar la revisión final de escrituras.
- Exigir aprobación contextual para efectos externos o irreversibles.
- Reanudar desde checkpoints, no repetir escrituras inciertas y verificar el resultado posterior a cada acción.
- Si el resultado no puede comprobarse, detenerse y declararlo no verificado.

### Web, multimedia y conectividad
- Reforzar recuperación web con extracción robusta, fuentes oficiales, fechas, autoridad, protección DNS/SSRF, límites de descarga y formatos alternativos. Un lector HTML no debe anunciarse como navegador completo.
- Multimedia: conectar proveedores locales o remotos reales mediante adaptadores. Validar endpoint, workflow, modelo, almacenamiento, límites y resultado antes de anunciar disponibilidad.
- Si un proveedor no funciona, ofrecer una ruta alternativa (otro backend, generación local compatible o proveedor remoto consentido), sin simular que la operación se completó.

### Datos y seguridad
- Cifrar datos privados en reposo mediante claves protegidas por Android Keystore y diseñar migraciones recuperables.
- No persistir tokens, claves, cookies ni credenciales en planes, logs, memoria o resultados. Redactar patrones y rechazar entradas sensibles; el detector no es garantía universal.
- Definir recuperación segura, backup compatible con las claves, exportación cifrada y migración entre dispositivos antes de depender de una sola clave local.
- Minimizar datos enviados a servicios externos y registrar qué se envía, a quién y bajo qué consentimiento.

## Registro de decisiones

Toda limitación relevante debe tener una entrada con:
- límite observado y evidencia;
- causa raíz y alcance;
- alternativas consideradas y motivo de elección;
- riesgos y controles;
- cambio de código/documentación;
- pruebas automatizadas y pruebas reales pendientes;
- métricas de aceptación;
- estado actual y siguiente acción.

## Criterio de éxito

ORION debe poder ampliar capacidades mediante módulos sustituibles y medibles sin depender de un único modelo, proveedor, acelerador o dispositivo. La ambición es un agente más útil y complejo, pero cada capacidad debe ser verificable, reversible cuando corresponda y honesta sobre lo que realmente puede hacer.
