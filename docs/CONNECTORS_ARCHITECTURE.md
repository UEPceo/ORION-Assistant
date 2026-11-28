# ORION — Arquitectura de conectores

Estado: Gmail dispone de integración de lectura; GitHub dispone de lectura y escrituras de ingeniería controladas. Los cuatro conectores prioritarios siguientes están especificados en docs/PRIORITY_CONNECTORS.md.

## Proveedores

- Gmail: Google Identity y Gmail API, scope gmail.readonly, búsqueda y lectura limitada.
- GitHub: GitHub App Device Flow y REST API; repositorios, incidencias, commits, archivos y Actions en lectura; ramas, commits multiarchivo y pull requests draft en escritura, con SHA optimista, validador de rutas y aprobación contextual. El workspace inspecciona archivos seleccionados; Actions valida el proyecto ORION, no código arbitrario.
- Notas del móvil: biblioteca local ORION, importación por Android Sharesheet/SAF y creación mediante intents compatibles. Android no permite leer de forma universal las notas privadas de otras aplicaciones.
- Calendario: Android Calendar Provider (CalendarContract), permisos de lectura/escritura solicitados por separado y fallback a intents del calendario.
- Google Maps: intents y URI oficiales para búsqueda, ubicación y navegación. Places API avanzada queda opcional y no se incrustan claves privadas en el APK.
- PescaIA Surfcasting: lectura de contenido público con procedencia y fecha; datos privados de cuenta solo mediante API autenticada y contrato explícito.

## Flujo de ejecución

1. El enrutador determinista identifica proveedor y operación.
2. Se valida que el proveedor esté registrado, conectado y tenga la capacidad solicitada.
3. Se construye una acción tipada; nunca se entrega al proveedor texto libre sin validar.
4. ConnectorActionGate decide si puede ejecutarse o necesita confirmación contextual.
5. El adaptador realiza la petición y devuelve un resultado resumido y saneado, con procedencia cuando corresponda.
6. Los errores técnicos se registran sin tokens, cabeceras de autorización ni contenido privado innecesario.

## Seguridad obligatoria

- OAuth oficial o permisos nativos Android; nunca solicitar contraseñas de Google o GitHub.
- Credenciales cifradas con Android Keystore.
- Tokens y secretos nunca se incorporan al prompt del LLM, logs o exportaciones.
- Solicitar permisos mínimos y explicar cada permiso antes de autorizar.
- Escrituras, publicaciones, cambios o borrados requieren confirmación contextual ligada a la acción exacta.
- Desconectar debe revocar o eliminar credenciales locales y limpiar estado sensible.
- Timeouts, cancelación, límites de petición y tratamiento explícito de expiración.
- Los conectores no deben bloquear el núcleo offline si fallan.

## Referencias de implementación

- docs/CONNECTORS_AUTHENTICATION.md: autenticación Gmail/GitHub.
- docs/PRIORITY_CONNECTORS.md: alcance, límites de Android y orden de implementación de los cuatro conectores nuevos.
- docs/ORION_BRAIN_ARCHITECTURE.md: separación entre conocimiento, memoria personal, evidencia y ejecución de herramientas.
