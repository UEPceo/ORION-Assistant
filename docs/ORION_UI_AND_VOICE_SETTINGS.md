# ORION — navegación, ajustes y catálogo de voces

## Navegación principal

La pantalla inicial es Contactos: presenta ORION Principal y los co-workers creados por el usuario. Desde cada ficha se abre la pantalla de llamada/conversación del asistente seleccionado.

La navegación superior se ha reducido a cinco destinos:
- Contactos
- Teléfono
- Tareas
- Más
- Ajustes

El hub Más agrupa Conversaciones, Memoria, Conectores y Laboratorio/diagnóstico para evitar una barra principal saturada.

## Pantalla de llamada

La llamada es una sesión de voz dentro de ORION, representada con una interfaz de llamada. No es una llamada celular ni permite escuchar el audio de llamadas telefónicas normales.

Controles disponibles:
- Iniciar/finalizar sesión.
- Silenciar y reactivar el micrófono.
- Activar/desactivar altavoz.
- Interrumpir la locución cuando ORION está hablando o procesando.
- Enrutamiento automático a auriculares con cable o Bluetooth cuando están disponibles.
- Indicador de presencia/actividad de voz, duración, conversación y controles de sesión.

La preferencia de altavoz se guarda localmente y se aplica al iniciar sesiones futuras. Android y el dispositivo pueden limitar los dispositivos de audio disponibles.

## Ajustes

La pantalla de Ajustes permite:
- Elegir la voz instalada.
- Cambiar el nombre, personalidad, tono y formalidad del asistente.
- Activar/desactivar la memoria personal.
- Consultar el estado de los modelos y abrir el laboratorio de diagnóstico.

La identidad y la memoria se guardan mediante las estructuras locales existentes; la voz elegida y la preferencia de altavoz se guardan en preferencias privadas de la aplicación.

## Catálogo de voces

Voces incluidas en el paquete inicial:
- Sharvard femenina.
- Sharvard masculina.

Paquetes opcionales en el catálogo, descargados únicamente al pulsar Descargar:
- DaveFX (es_ES, Piper Medium).
- Miro (es_ES, Piper High).
- CarlFM (es_ES, Piper x_low).

Los paquetes opcionales se instalan en directorios privados separados. La voz actual no cambia al finalizar la descarga: el usuario debe pulsar Elegir. El motor libera y vuelve a cargar el modelo cuando cambia entre paquetes de voz.

La descarga utiliza el descargador reanudable de ORION, progreso visible, extracción controlada, validación de presencia/tamaño del ONNX, tokens y datos espeak, y sustitución de directorio con recuperación de respaldo. No se fija un tamaño de descarga porque depende del activo remoto y del servidor. Los paquetes oficiales de sherpa-onnx no publican un SHA-256 verificable en el catálogo usado; la instalación valida estructura y archivos, pero no debe describirse como verificación criptográfica de origen.

Referencias de modelos:
- sherpa-onnx mantiene los modelos Piper es_ES DaveFX, GLaDOS, Miro, Sharvard y CarlFM en su catálogo de modelos y scripts de empaquetado.
- Los archivos de voz se distribuyen en los releases oficiales de sherpa-onnx bajo la etiqueta `tts-models`.

## Pendiente de validación

El código y la navegación están incorporados, pero no se ha ejecutado todavía una compilación completa ni una prueba en el Samsung. Antes de declarar esta fase cerrada hay que compilar y corregir posibles incompatibilidades de API/Compose, validar descargas reales de cada paquete, cambiar de voz con el motor cargado y probar rutas de audio en el dispositivo.
