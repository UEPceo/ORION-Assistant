# ORION — Arquitectura inicial de conectores

Estado: estructura base incorporada; proveedores todavía no implementados.

## Objetivo

Permitir que ORION trabaje con servicios externos sin acoplar la lógica de voz, el LLM local o las funciones offline a SDK concretos de cada proveedor.

## Módulos previstos

- `connectors/`: contratos comunes, catálogo, registro, política de autorización y gestión de credenciales.
- `connectors/gmail/`: OAuth y Gmail API.
- `connectors/github/`: autorización GitHub y GitHub REST API.
- `connectors/googlecalendar/`: Google Calendar API.
- `connectors/googledrive/`: Google Drive API.
- Pantalla Compose **Conectores**: estado, permisos concedidos, conexión/desconexión y último error.

## Flujo de ejecución

1. El enrutador determinista identifica proveedor y operación.
2. Se valida que el proveedor esté registrado, conectado y tenga la capacidad solicitada.
3. Se construye una acción tipada; nunca se entrega al proveedor texto libre sin validar.
4. `ConnectorActionGate` decide si puede ejecutarse o necesita confirmación explícita.
5. El adaptador realiza la petición y devuelve un resultado resumido y saneado.
6. Los errores técnicos se registran sin tokens, cabeceras de autorización ni contenido privado innecesario.

## Seguridad obligatoria

- OAuth oficial; no solicitar ni almacenar contraseñas de Google o GitHub.
- Credenciales cifradas en almacenamiento privado con claves protegidas por Android Keystore.
- Tokens y secretos nunca se incorporan al prompt del LLM, logs, informes de diagnóstico ni exportaciones.
- Solicitar scopes mínimos y explicar cada permiso antes de autorizar.
- Lecturas solo tras petición del usuario; enviar correo, publicar comentarios, crear/modificar elementos o borrar datos exige confirmación contextual.
- Desconectar debe revocar o eliminar credenciales locales y limpiar estado sensible.
- Timeouts, cancelación, límites de petición, renovación segura y tratamiento explícito de expiración.

## Catálogo inicial

| Proveedor | Primera entrega prevista |
|---|---|
| Gmail | Buscar y leer mensajes; resumir; crear borradores; envío con confirmación |
| GitHub | Repositorios, archivos, issues y PR; comentarios y cambios con confirmación |
| Google Calendar | Consultar y gestionar eventos |
| Google Drive | Buscar y leer documentos |

## Restricciones de diseño

- El núcleo de conversación, ASR, TTS, memoria y tareas debe seguir funcionando sin conexión.
- Los conectores requieren Internet y no deben bloquear la conversación local si fallan.
- El LLM local no decide por sí solo si una operación sensible está autorizada; el enrutador y la política determinista lo controlan. La política contrasta también el nombre de la operación para que una acción de escritura no pueda etiquetarse como lectura por error.
- El catálogo no equivale a integración funcional: un proveedor solo estará disponible cuando exista un adaptador probado y una conexión válida.

## Estado de esta entrega

Se han añadido los contratos comunes, el registro, la política central de autorización, el catálogo, el almacenamiento cifrado Android Keystore y la pantalla de conectores. GitHub incorpora OAuth App Device Flow con scope inicial `read:user`; Gmail incorpora Google Identity con `gmail.readonly`, token temporal cifrado y revocación al desconectar. Los adaptadores de Gmail API y GitHub REST API aún no están implementados. Google exige configurar proyecto, Gmail API, consentimiento, cliente Android y SHA-1; GitHub requiere una OAuth App con Device Flow habilitado y Client ID público.
