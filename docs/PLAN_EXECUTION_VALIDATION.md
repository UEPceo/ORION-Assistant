# Validación del planificador multipaso de ORION

Este documento separa las garantías cubiertas por código y pruebas automatizadas de las pruebas de integración que requieren un dispositivo y conectores configurados.

## Cobertura implementada y automatizada

- Validación del grafo: IDs duplicados, dependencias inexistentes, ciclos y límite de pasos.
- Seguridad de propuestas: solo operaciones registradas, argumentos obligatorios, rechazo de credenciales y rechazo de operaciones no implementadas.
- Serialización: ida y vuelta de planes, conectores, argumentos, dependencias y presupuestos.
- Persistencia Room: creación, checkpoints, historial y bloqueo del borrado mientras una ejecución está activa.
- Reanudación: los pasos completados no se repiten; un paso fallido solo se reintenta si se selecciona expresamente.
- Interrupciones: el cierre durante una operación produce PAUSED y marca el paso activo como resultado incierto; no se repite automáticamente.
- Aprobaciones: se exigen de nuevo para pasos protegidos pendientes y no se guardan entre invocaciones.
- Dependencias: un paso dependiente queda bloqueado si su requisito no termina correctamente.
- Encadenamiento: `${stepId.output}` y `${stepId.summary}` se resuelven únicamente desde pasos SUCCEEDED; la referencia exige declarar la dependencia y los valores no resolubles bloquean el paso.
- Revisión previa: el plan se pausa antes de una escritura no autorizada, después de resolver sus entradas, para que el usuario revise el contenido y autorice ese paso.
- Acciones locales disponibles: creación de tareas, notas y recordatorios mediante el repositorio local, sin necesidad de red y con confirmación contextual. Las tareas conservan fecha y hora explícitas; los valores temporales mal formados se rechazan en vez de ignorarse.
- Presupuesto: límite temporal global; los reintentos automáticos se limitan a razonamiento, búsquedas, lecturas y cálculos. Las escrituras nunca se repiten automáticamente, aunque incluyan una clave de idempotencia.

Los tests de `PlanningContractsTest` cubren encadenamiento de resultados, referencias sin dependencia, pausa para aprobación con entradas resueltas, bloqueo de dependientes y reanudación sin repetir pasos completados. `LocalTaskPlanStepHandlerTest` cubre persistencia de fecha/hora de tareas, rechazo de valores temporales inválidos y confirmación obligatoria antes de escribir.

## Pruebas de integración pendientes en dispositivo

No se consideran superadas hasta ejecutarlas en un Android real con los conectores configurados.

1. Solicitar por voz una consulta multipaso compatible y comprobar que ORION guarda un borrador sin ejecutarlo.
2. Cerrar y volver a abrir ORION; verificar que el plan y sus resultados permanecen en Ajustes.
3. Ejecutar un plan de lectura con Gmail/GitHub conectado y verificar que cada consulta se limita a la operación y argumentos aprobados.
4. Encadenar una lectura real con una acción posterior y comprobar que la salida mostrada para revisión coincide con la entrada efectiva.
5. Desconectar un proveedor y comprobar que el paso falla de forma controlada, sin afirmar que se completó.
6. Cancelar durante una consulta y comprobar que el plan queda pausado.
7. Simular cierre del proceso durante una operación; verificar que el paso queda marcado como incierto y no se repite al reanudar.
8. Marcar expresamente un paso fallido para reintento y comprobar que los pasos ya completados no vuelven a ejecutarse.
9. Crear una tarea, nota y recordatorio locales con el dispositivo sin conexión; comprobar persistencia y programación real de la notificación.
10. Confirmar que no hay envíos, llamadas, compras, borrados ni escrituras externas sin autorización contextual específica.

## Límites conocidos

- El generador de planes propone operaciones de lectura de conectores registradas; la disponibilidad real depende de cada conector y de su autenticación.
- Los handlers locales implementados cubren tareas, notas y recordatorios. No se deben anunciar como disponibles creación de documentos arbitrarios, edición de código ni otras acciones locales sin un handler específico.
- La persistencia está en almacenamiento privado de la app, pero la base Room no se declara cifrada. No se deben incluir credenciales ni secretos en planes ni resultados.
- La autorización es por paso y por invocación; no debe interpretarse como permiso permanente para futuras ejecuciones.
- El estado CI y la prueba real en dispositivo deben verificarse por separado; los tests unitarios no demuestran compatibilidad de cada proveedor en un teléfono.
