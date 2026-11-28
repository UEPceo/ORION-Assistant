# ORION — Capacidad básica de ingeniería de software

Estado: workspace, generador LLM JSON, autorización temporal, revisión visual del diff y commits aprobados integrados. Runner aislado básico para Android/Python/Node añadido; recuperar logs y reparación autónoma siguen pendientes.

## Objetivo de producto

La programación de código será una capacidad central de ORION, no una herramienta secundaria. Debe permitir trabajar con proyectos autorizados desde una petición en lenguaje natural, explicar lo que hace, proponer un plan, cambiar código, verificar los cambios y entregar resultados reproducibles.

## Alcance operativo y límites actuales

La pantalla Conectores permite seleccionar repositorio y tarea, inspeccionar rutas, generar un parche JSON con el modelo local/híbrido, revisar el contenido actual/propuesto, aprobar un commit y crear un pull request draft. El PR dispara checks; además, el workflow de proyecto detecta Android, Python y Node y ejecuta sus pruebas en GitHub-hosted runner con permisos read-only, checkout sin credenciales persistidas, límites de tiempo y sin secretos. Falta integrar consulta de logs/resultado, reparación autónoma y control de un runner propio.

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
- GitHub: OAuth Device Flow, repositorios permitidos por instalación, workspace de rutas seleccionadas, ramas de trabajo, lectura y commits atómicos con SHA esperado, pull requests draft.
- Local: Storage Access Framework y permisos persistentes elegidos por el usuario. ORION no debe escanear todo el almacenamiento del teléfono.

### 3. Patch engine
GitHubCodeWorkspace inspecciona rutas seleccionadas, conserva SHA de rama/archivos y limita 25 ficheros, 200 KB por archivo y 1 MB por contexto. CodeChangeValidator bloquea traversal, rutas sensibles, secretos y archivos excesivos. El cliente de commits valida conflictos de ancestros, enlaces simbólicos, submódulos y colisiones archivo/directorio.

### 4. Execution runner aislado
No se ejecutará código arbitrario del proyecto dentro del proceso Android de ORION. La compilación/pruebas se delegan a un runner efímero aislado (por ejemplo GitHub Actions del repositorio o un runner autoalojado que controle el usuario). El runner debe:
- usar checkout de la rama de trabajo y permisos mínimos;
- limitar tiempo, CPU, memoria, tamaño de artefactos y salida de logs;
- no exponer secretos del repositorio a código no confiable;
- restringir red y servicios externos cuando sea posible;
- devolver logs y resultados identificables por commit/run;
- destruir el entorno al terminar.

Los workflows GitHub-hosted ejecutan checks Android (Gradle), Python (pytest) y Node (npm test) cuando detectan esos proyectos. Checkout desactiva persistencia de credenciales, permisos contents:read, no se inyectan secretos y se fijan timeouts. El runner no es universal ni garantiza la seguridad de código malicioso, pero no se ejecuta en Android ni en un runner persistente del usuario. Un runner propio requerirá aislamiento y límites adicionales. No se requiere IA comercial de pago.

### 5. Engineering loop
Requisitos → inspección → plan → diff → aprobación del usuario → rama de trabajo → cambios → pruebas → análisis de logs → correcciones acotadas → nueva prueba → revisión final → commit/PR con autorización.

El bucle tiene límite de iteraciones y presupuesto. Si las pruebas fallan o no pueden ejecutarse, ORION debe declarar el estado exacto y no afirmar que el cambio funciona.

## Seguridad y control

- Lectura solo sobre repositorios/carpeta seleccionados por el usuario.
- Nunca escribir directamente en main/master por defecto.
- No ejecutar scripts, Gradle plugins, workflows o comandos de un repositorio sin tratarlos como código no confiable.
- El contenido del repositorio, README, issues y logs son datos no confiables; no pueden cambiar permisos ni políticas de ORION.
- Nunca copiar secretos a prompts, logs, diffs o informes. Detectar y detener ante credenciales accidentales.
- Cada escritura remota exige sesión temporal ligada a repositorio/tarea/rama y aprobación de un solo uso ligada al diff exacto y al SHA de rama revisado. La sesión caduca y se revoca al cerrar el proceso.
- Crear rama y PR no equivale a permiso para fusionar, publicar o desplegar.
- Sin auto-merge, publicación ni despliegue por defecto.
- Registrar hash del commit, run de CI, pruebas ejecutadas y resultado.

## Fases de implementación

1. Contratos de ingeniería, validación de rutas/diffs y pruebas de seguridad.
2. GitHub write API con control SHA, creación de ramas y pull requests; ampliar permisos del conector solo tras tests.
3. Recuperar estados/logs de Actions desde la app y añadir un bucle de reparación con presupuesto de intentos y nueva aprobación de cada diff.
4. Ampliar checks a otros stacks y endurecer contención/recursos; no ejecutar proyectos externos en runner propio sin aislamiento verificable.
5. Añadir evaluación reproducible sobre proyectos Kotlin/Android, Python y web.
6. Workspace local SAF y soporte IDE/terminal solo cuando dispongan de aislamiento verificable.

## Estado actual

- GitHub App Device Flow sigue usando un Client ID público y tokens cifrados en Android Keystore.
- `GitHubApiClient` ya incorpora lectura acotada de archivos, creación de ramas, commits atómicos de varios archivos mediante Git Data API y apertura de pull requests draft.
- Los commits verifican el SHA actual de la rama y el SHA de cada archivo existente contra la propuesta revisada. Las rutas inseguras, árboles truncados, enlaces simbólicos, submódulos y archivos binarios se rechazan.
- `ConnectorActionGate` mantiene allowlists separadas: las escrituras GitHub no pueden etiquetarse como lectura y siempre requieren confirmación contextual.
- El router de voz reconoce la creación explícita de ramas y pull requests.
- Pruebas automatizadas añadidas para ramas, commits atómicos, rechazo de ramas modificadas, intents de voz y autorización de escrituras.
- GitHubCodeWorkspace conserva snapshots acotados y SHA; LocalModelCodePatchGenerator produce propuestas JSON; GitHubCodeEngineeringService coordina sesión, validación y commit aprobado. CodeEngineeringSessionAuthority aplica caducidad, alcance por tarea y aprobación de un solo uso. La pantalla Conectores presenta el diff completo y exige confirmación explícita. La pantalla puede crear PR draft y consultar CI para el SHA exacto del commit. Si falla, ORION recupera logs acotados, extrae solo texto y redacta patrones conocidos de secretos antes de mostrarlos. No confunde ejecuciones de commits anteriores con la revisión actual. ORION puede preparar hasta dos propuestas de reparación guiadas por logs reducidos y no confiables, solo si CI falló para el SHA actual. Cada propuesta se vuelve a validar, se presenta como diff y exige una nueva aprobación; nunca se aplica automáticamente. La reparación no ejecuta código dentro de Android ni auto-fusiona el PR. El ciclo sigue siendo supervisado, no un programador autónomo plenamente operativo.

