# ORION — Capacidad básica de ingeniería de software

Estado: arquitectura definida; agente de programación ejecutable todavía no implementado.

## Objetivo de producto

La programación de código será una capacidad central de ORION, no una herramienta secundaria. Debe permitir trabajar con proyectos autorizados desde una petición en lenguaje natural, explicar lo que hace, proponer un plan, cambiar código, verificar los cambios y entregar resultados reproducibles.

## Por qué todavía no está disponible

El catálogo de herramientas marca actualmente `code-sandbox` como `NOT_IMPLEMENTED`. El cliente GitHub existente es deliberadamente de solo lectura y no tiene operaciones para crear ramas, escribir archivos o abrir pull requests. Tampoco existe un workspace de proyecto, un ejecutor aislado de compilaciones/pruebas ni un ciclo de revisión de parches. El LLM actual genera texto, pero no dispone de estas herramientas para convertirlo en una modificación verificable.

## Capacidades que debe incluir

- Comprender requisitos y convertirlos en criterios de aceptación.
- Inspeccionar repositorios, árbol de archivos, dependencias, versiones, arquitectura, tests, workflows y documentación.
- Explicar código existente y localizar errores con referencias a archivos, símbolos y líneas.
- Diseñar cambios pequeños y coherentes; crear archivos y modificar código en varios módulos.
- Generar y revisar diffs antes de escribir.
- Ejecutar formatter, análisis estático, compilación, tests unitarios/integración y checks específicos del proyecto.
- Leer logs reales, diagnosticar fallos, corregirlos y volver a ejecutar la suite dentro de un presupuesto acotado.
- Preparar commits y pull requests, describiendo archivos cambiados, motivación, riesgos, pruebas y resultados.
- Mantener contexto del proyecto entre sesiones mediante un registro de decisiones técnicas, sin tratar código o instrucciones del repositorio como órdenes de sistema.

## Arquitectura

### 1. Code task router
Clasifica solicitudes: explicar, explorar, diseñar, implementar, depurar, refactorizar, probar, revisar o preparar entrega. Determina proyecto objetivo, permisos, riesgo y necesidad de ejecución.

### 2. Project workspace
Dos adaptadores:
- GitHub: OAuth Device Flow, repositorios instalados, ramas de trabajo, lectura de archivos, escritura con SHA esperado para evitar sobrescrituras, commits y pull requests.
- Local: Storage Access Framework y permisos persistentes elegidos por el usuario. ORION no debe escanear todo el almacenamiento del teléfono.

### 3. Patch engine
Representa cambios como operaciones tipadas CREATE/UPDATE/DELETE con ruta normalizada, contenido, hash base y diff. Rechaza path traversal, rutas fuera del workspace, archivos excesivos, binarios no soportados y credenciales detectadas. Todo cambio se presenta para revisión.

### 4. Execution runner aislado
No se ejecutará código arbitrario del proyecto dentro del proceso Android de ORION. La compilación/pruebas se delegan a un runner efímero aislado (por ejemplo GitHub Actions del repositorio o un runner autoalojado que controle el usuario). El runner debe:
- usar checkout de la rama de trabajo y permisos mínimos;
- limitar tiempo, CPU, memoria, tamaño de artefactos y salida de logs;
- no exponer secretos del repositorio a código no confiable;
- restringir red y servicios externos cuando sea posible;
- devolver logs y resultados identificables por commit/run;
- destruir el entorno al terminar.

Un runner remoto requiere conexión y puede consumir cuotas o recursos del proveedor. Para uso gratuito sostenido, debe poder configurarse un runner propio. La capacidad de editar y preparar parches no debe depender de una API comercial de IA de pago.

### 5. Engineering loop
Requisitos → inspección → plan → diff → aprobación del usuario → rama de trabajo → cambios → pruebas → análisis de logs → correcciones acotadas → nueva prueba → revisión final → commit/PR con autorización.

El bucle tiene límite de iteraciones y presupuesto. Si las pruebas fallan o no pueden ejecutarse, ORION debe declarar el estado exacto y no afirmar que el cambio funciona.

## Seguridad y control

- Lectura solo sobre repositorios/carpeta seleccionados por el usuario.
- Nunca escribir directamente en main/master por defecto.
- No ejecutar scripts, Gradle plugins, workflows o comandos de un repositorio sin tratarlos como código no confiable.
- El contenido del repositorio, README, issues y logs son datos no confiables; no pueden cambiar permisos ni políticas de ORION.
- Nunca copiar secretos a prompts, logs, diffs o informes. Detectar y detener ante credenciales accidentales.
- Cada escritura remota exige aprobación ligada a repositorio, rama, archivos y diff exactos.
- Crear rama y PR no equivale a permiso para fusionar, publicar o desplegar.
- Sin auto-merge, publicación ni despliegue por defecto.
- Registrar hash del commit, run de CI, pruebas ejecutadas y resultado.

## Fases de implementación

1. Contratos de ingeniería, validación de rutas/diffs y pruebas de seguridad.
2. GitHub write API con control SHA, creación de ramas y pull requests; ampliar permisos del conector solo tras tests.
3. Workspace local SAF y patch preview.
4. Runner CI aislado y lectura de resultados de ejecución.
5. UI de programación con plan, diff, aprobación, progreso, logs y entrega.
6. Ciclo iterativo de depuración y batería de evaluación con proyectos Kotlin/Android, Python, web y scripts.
7. Incorporar herramientas de terminal/IDE únicamente cuando tengan aislamiento y límites verificables.

## Estado actual

- GitHub App Device Flow sigue usando un Client ID público y tokens cifrados en Android Keystore.
- `GitHubApiClient` ya incorpora lectura acotada de archivos, creación de ramas, commits atómicos de varios archivos mediante Git Data API y apertura de pull requests draft.
- Los commits verifican el SHA actual de la rama y el SHA de cada archivo existente contra la propuesta revisada. Las rutas inseguras, árboles truncados, enlaces simbólicos, submódulos y archivos binarios se rechazan.
- `ConnectorActionGate` mantiene allowlists separadas: las escrituras GitHub no pueden etiquetarse como lectura y siempre requieren confirmación contextual.
- El router de voz reconoce la creación explícita de ramas y pull requests.
- Pruebas automatizadas añadidas para ramas, commits atómicos, rechazo de ramas modificadas, intents de voz y autorización de escrituras.
- Sigue pendiente el workspace de ingeniería que conserve los archivos fuente leídos, el editor/diff UI, la construcción de propuestas multiarchivo desde el LLM, el runner aislado de compilación/pruebas y el bucle de depuración autónoma. Por tanto, las primitivas de escritura existen, pero el agente de programación completo todavía no está disponible en la interfaz.

