# ORION — Conectores prioritarios

Estado: primera integración funcional de las cuatro prioridades incorporada. El alcance se limita a capacidades verificables; no se declara acceso a datos privados sin API/permisos específicos.

## 1. Notas del móvil

Aplicación de destino elegida: Google Keep.

Primera entrega implementada:
- Tarjeta Google Keep en Conectores con título y contenido editables.
- Envío mediante Android Sharesheet (ACTION_SEND, text/plain); Google Keep aparecerá como destino si está instalado y registrado para recibir texto.
- El usuario elige el destino y confirma la acción en el selector del sistema.

Pendiente de ampliación:
- Recepción de notas compartidas desde Keep hacia ORION.
- Biblioteca local de notas con persistencia Room, búsqueda e indexación para el cerebro.
- Importación de documentos seleccionados mediante Storage Access Framework.
- Organización, edición y eliminación de notas propias con controles explícitos.
- No se usa una API privada o no documentada de Google Keep.

Límite importante: Android no ofrece un proveedor universal que permita leer todas las notas privadas de Samsung Notes, Keep y cualquier aplicación. No se debe prometer lectura global de notas ajenas sin una API oficial del proveedor. ORION solo importa lo que el usuario comparte o selecciona, o lo que un proveedor autorice explícitamente.

## 2. Calendario

Primera entrega implementada:
- Consulta de próximos 14 días mediante CalendarContract.Instances, tras solicitud y concesión explícita del permiso READ_CALENDAR.
- Formulario para preparar título, fecha, hora de inicio/fin y descripción.
- Transferencia mediante ACTION_INSERT y CalendarContract.Events.CONTENT_URI a la aplicación de calendario instalada.
- El usuario revisa y guarda el evento en la aplicación de destino. No se solicita WRITE_CALENDAR y ORION no escribe ni modifica silenciosamente.

Pendiente de ampliación:
- Crear directamente en un calendario seleccionado con WRITE_CALENDAR y confirmación explícita, si el usuario habilita esa modalidad.
- Modificar y eliminar eventos con confirmación reforzada.
- Recurrencia, invitados y eventos de día completo.
- Consultar disponibilidad sin duplicar la agenda dentro de la base local de ORION.

No duplicar el calendario completo dentro de la base de ORION. Consultar bajo demanda y guardar únicamente el contexto mínimo autorizado.

## 3. Google Maps

Primera entrega incorporada:
- Tarjeta de Maps con búsqueda de lugar/dirección y apertura de navegación.
- Enrutamiento por voz para búsquedas explícitas y navegación a pie, coche o transporte público.
- Proveedor Android registrado en el runtime; las búsquedas se consideran lectura y la navegación requiere confirmación adicional en el dispatcher.
- Intents geo: y google.navigation: sin API key ni permiso de ubicación en segundo plano.

La ubicación del usuario solo se solicita cuando una función concreta la necesita, con permiso en tiempo de ejecución. No mantener seguimiento de ubicación en segundo plano.

Una futura búsqueda avanzada de Places, reseñas o detalles estructurados puede requerir Google Places API, facturación y una clave restringida por aplicación. No incluir secretos de servidor en el APK.

## 4. PescaIA Surfcasting

Objetivo: que ORION sea capaz de consultar la información pública y, cuando se autorice, la información privada de la cuenta del usuario.

Primera entrega pública incorporada:
- Proveedor de consulta HTTP de páginas públicas con lista fija de dominios y URLs de PescaIA.
- Lectura acotada de la portada, catálogo de playas y metodología; extracción de texto limitada por tamaño y tiempo de espera.
- Búsqueda por términos sobre los fragmentos obtenidos, devolviendo las URLs de origen y advertencia de que la información es orientativa.
- Enrutamiento por voz para consultas explícitas que mencionan PescaIA.
- Tarjeta con accesos directos a la web, catálogo de playas y metodología.

No se afirma que esta primera versión lea la previsión horaria dinámica, mareas en tiempo real ni los datos privados de una cuenta. Esos datos requieren endpoints públicos estables o una API autenticada del backend.

Cuenta personal, fase posterior del mismo conector:
- Bitácora, playas favoritas y preferencias del usuario.
- Sincronización de datos de cuenta solo después de autenticación y consentimiento.
- No intentar leer localStorage/IndexedDB del navegador del usuario desde Android ni reutilizar sesiones privadas del navegador.
- Preferir endpoints oficiales y acotados del backend PescaIA/Supabase, con autenticación segura, scopes mínimos y política de revocación.
- No incorporar capturas privadas, notas o datos de cuenta a la memoria general sin una acción explícita del usuario.

El conector debe conservar la fuente original y su fecha. Para normativa y seguridad, ORION debe consultar o enlazar la autoridad competente, aunque PescaIA muestre una explicación resumida.

## Contrato común de los cuatro conectores

Cada proveedor debe declarar:
- Capacidades exactas y operaciones permitidas.
- Estado de permisos y conexión.
- Datos que lee, escribe y conserva.
- Fecha de última sincronización.
- Tratamiento de errores, desconexión y revocación.
- Confirmaciones requeridas para escrituras o acciones externas.

Toda salida debe indicar origen y hora de consulta cuando la información sea dinámica. Los datos no deben incorporarse automáticamente a la memoria personal o al corpus general.

## Estado y siguientes ampliaciones

1. Keep: añadir recepción de notas compartidas, biblioteca Room e importación mediante Storage Access Framework.
2. Calendario: lectura de agenda con permiso runtime y creación directa solo con confirmación; después edición y borrado.
3. Maps: ampliar a búsqueda avanzada de Places solo si se justifica el coste y la clave restringida.
4. PescaIA: descubrir y documentar endpoints públicos estables para previsión/mareas; para cuenta privada, integrar API autenticada y scopes mínimos.

## Límites que deben resolverse antes de ampliar el alcance

- La aplicación de destino ya está elegida: Google Keep. La lectura global de notas privadas no está disponible mediante una API pública de consumo; el intercambio debe basarse en acciones explícitas del usuario.
- Para PescaIA privada, definir el contrato estable de API y autenticación de la cuenta; no incrustar service-role keys ni secretos Supabase en la aplicación.
- Para Places avanzado de Google Maps, decidir si se necesita realmente la API de Places o si bastan intents y navegación.
