# Validación del planificador multipaso de ORION

Este documento separa las comprobaciones automatizadas de las pruebas de integración que requieren un dispositivo y conectores configurados.

## Cobertura automatizada

- Validación del grafo: IDs duplicados, dependencias inexistentes, ciclos y límite de pasos.
- Seguridad de propuestas: solo operaciones de lectura registradas, argumentos obligatorios, rechazo de credenciales y rechazo de operaciones no implementadas.
- Serialización: ida y vuelta de planes, conectores, argumentos, dependencias y presupuestos.
- Persistencia Room: creación, checkpoints, historial y bloqueo del borrado mientras una ejecución está activa.
- Reanudación: los pasos completados no se repiten; un paso fallido solo se reintenta si se selecciona expresamente.
- Interrupciones: el cierre durante una operación produce PAUSED/FAILED incierto; no se repite automáticamente.
- Aprobaciones: se exigen de nuevo para pasos protegidos pendientes y no se guardan entre invocaciones.
- Dependencias: un paso dependiente queda bloqueado si su requisito no termina correctamente.
- Presupuesto: límite temporal global y reintentos restringidos por idempotencia.

## Pruebas de integración pendientes en dispositivo

No se consideran superadas hasta ejecutarlas en un Android real con los conectores configurados.

1. Solicitar por voz una consulta multipaso compatible y comprobar que ORION guarda un borrador sin ejecutarlo.
2. Cerrar y volver a abrir ORION; verificar que el plan y sus resultados permanecen en Ajustes.
3. Ejecutar un plan con Gmail/GitHub conectado y verificar que cada consulta se limita a la operación y argumentos aprobados.
4. Desconectar un proveedor y comprobar que el paso falla de forma controlada, sin afirmar que se completó.
5. Cancelar durante una consulta y comprobar que el plan queda pausado.
6. Simular cierre del proceso durante una operación; verificar que el paso queda marcado como incierto y no se repite al reanudar.
7. Marcar expresamente un paso fallido para reintento y comprobar que los pasos ya completados no vuelven a ejecutarse.
8. Confirmar que no hay operaciones de escritura, llamadas, envíos, compras ni borrados generados por el planificador de esta fase.

## Límites conocidos

- El generador produce únicamente planes de consultas de lectura para operaciones registradas de Gmail, GitHub, Google Maps y PescaIA.
- No hay handlers operativos para pasos locales como cálculo, creación de documentos o edición de código.
- No existe interpolación de resultados de un paso como entrada del siguiente; los argumentos deben venir completos desde la solicitud.
- La persistencia está en almacenamiento privado de la app, pero la base Room no se declara cifrada. No se deben incluir credenciales ni secretos en planes.
- El estado CI y la prueba real en dispositivo deben verificarse por separado; los tests unitarios no demuestran compatibilidad de cada proveedor en un teléfono.
