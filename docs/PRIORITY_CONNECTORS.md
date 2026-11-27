# ORION — Conectores prioritarios

Estado: cuatro integraciones prioritarias fijadas. No ampliar el catálogo a otros proveedores hasta completar estas cuatro.

## 1. Notas del móvil

Objetivo: consultar, capturar, crear y organizar notas del usuario.

Integración propuesta:
- Mantener en ORION una biblioteca local de notas propia, indexable por el cerebro y sincronizada solo si el usuario lo activa.
- Importar notas de otras aplicaciones mediante Android Sharesheet, ACTION_SEND y selector de documentos (Storage Access Framework).
- Crear una nota en una aplicación compatible mediante ACTION_CREATE_NOTE cuando esté disponible; usar ACTION_SEND como alternativa.
- Permitir que el usuario comparta una nota concreta desde Samsung Notes, Google Keep u otra app para incorporarla a ORION.
- Mantener referencia a la aplicación de origen, fecha de importación y permiso otorgado.

Límite importante: Android no ofrece un proveedor universal que permita leer todas las notas privadas de Samsung Notes, Keep y cualquier aplicación. No se debe prometer lectura global de notas ajenas sin una API oficial del proveedor. ORION solo importa lo que el usuario comparte o selecciona, o lo que un proveedor autorice explícitamente.

## 2. Calendario

Primera vía: Android Calendar Provider (CalendarContract), con permisos de lectura/escritura solicitados por separado y explicados al usuario.

Capacidades:
- Consultar próximos eventos y disponibilidad básica de calendarios seleccionados.
- Crear, modificar y eliminar eventos después de confirmación contextual.
- Crear recordatorios/tareas locales de ORION sin depender del calendario.
- Respetar zona horaria, recurrencia, calendario de destino, invitados y eventos de día completo.
- Si no se concede permiso, abrir la pantalla de creación/consulta de la aplicación de calendario mediante intents del sistema.

No duplicar el calendario completo dentro de la base de ORION. Consultar bajo demanda y guardar únicamente el contexto mínimo autorizado.

## 3. Google Maps

Primera entrega sin API key: intents y URI oficiales de Android/Google Maps para:
- Buscar lugares y direcciones.
- Mostrar un lugar en el mapa.
- Abrir navegación a pie, en coche o en transporte cuando Google Maps lo soporte.
- Compartir coordenadas o una dirección desde ORION a Maps.
- Abrir desde un lugar de PescaIA la ficha o navegación correspondiente.

La ubicación del usuario solo se solicita cuando una función concreta la necesita, con permiso en tiempo de ejecución. No mantener seguimiento de ubicación en segundo plano.

Una futura búsqueda avanzada de Places, reseñas o detalles estructurados puede requerir Google Places API, facturación y una clave restringida por aplicación. No incluir secretos de servidor en el APK.

## 4. PescaIA Surfcasting

Objetivo: que ORION sea capaz de consultar la información pública y, cuando se autorice, la información privada de la cuenta del usuario.

Primera entrega pública:
- Catálogo de playas y fichas de pesqueros.
- Previsión marina y condiciones disponibles.
- Mareas y calendario solunar.
- Metodología, fuentes y fecha de revisión.
- Guías de especies, cebos, técnicas y material.
- Normativa y tallas con enlaces a las fuentes oficiales originales.

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

## Orden de implementación

1. Notas: biblioteca local + importar/compartir + crear mediante intent compatible.
2. Calendario: lectura y creación controladas con CalendarContract y fallback a intents.
3. Maps: búsqueda, mapa y navegación mediante intents; permisos de ubicación solo cuando sean necesarios.
4. PescaIA: primero consulta pública de fuentes y catálogo; después cuenta y datos privados mediante contrato API autenticado.

## Bloqueos de integración que deben resolverse antes de declarar los conectores completos

- Confirmar qué aplicación de notas se usará como destino preferido (Samsung Notes, Google Keep u otra). La importación por compartir puede funcionar sin esa elección.
- Para PescaIA privada, definir el contrato estable de API y autenticación de la cuenta; no incrustar service-role keys ni secretos Supabase en la aplicación.
- Para Places avanzado de Google Maps, decidir si se necesita realmente la API de Places o si bastan intents y navegación.
