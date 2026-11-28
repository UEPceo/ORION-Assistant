# Auditoría del ciclo de arranque

Última revisión: 2026-11-28.

## Secuencia actual verificada en código

1. `OrionApplication.onCreate` instala diagnósticos de cierres, registra salidas históricas y programa actualizaciones de packs de conocimiento e instalación de embeddings.
2. `MainActivity.onCreate` crea el `VoiceLabViewModel` y muestra la interfaz Compose.
3. El inicializador del ViewModel configura motores, repositorios Room, preferencias, reconocimiento, LLM híbrido y TTS.
4. En `init`, se conectan observadores, se inicializa persistencia de conversaciones, se recuperan ejecuciones de planes, se reconcilian recordatorios, se refrescan estados/métricas y se inicia la descarga automática de modelos que falten.
5. La instalación inicial es secuencial: Piper TTS, Moonshine ASR y Qwen2.5 1.5B Q4_K_M. Cada instalador comprueba primero si el componente ya está instalado; las descargas parciales se conservan para reanudación.
6. La descarga usa archivos temporales, validación de tamaño/rangos HTTP, espacio libre, cabecera GGUF y SHA-256 fijado para los tres modelos principales. Tras extraer ASR/TTS se crea un manifiesto SHA-256 por archivo; el LLM conserva manifiesto y hash anclado al artefacto.
7. Los modelos no se cargan todos en RAM al abrir la aplicación: se cargan bajo demanda al iniciar escucha o conversación; existe liberación explícita de RAM.

## Coordinación de tareas de primer arranque

Se ha añadido `ModelManager.areCoreModelsInstalled` como condición única para declarar lista la pila offline obligatoria. El instalador automático de embeddings semánticos (opcional, ~136 MB) ahora se cancela/difiere si faltan TTS, ASR o LLM, y se programa de nuevo al terminar la instalación de los tres componentes principales. El propio Worker vuelve a comprobar la condición antes de descargar, para cubrir el caso en que ya estuviera encolado. Las actualizaciones de packs de conocimiento también se difieren mientras falte algún componente principal. Se añadió una prueba de que una instalación vacía nunca se declara lista.

## Corrección aplicada en esta auditoría

Se detectó que las descargas ASR/LLM/TTS recibían como condición de cancelación el estado del `viewModelScope`, no el Job de la instalación concreta. Al pulsar Pausar, el Job de descarga podía cancelarse mientras el bucle de E/S seguía sin conocer esa cancelación. Ahora cada instalador captura su Job actual y usa `DownloadCancellationGuard`, que comprueba el estado del Job y la interrupción del hilo. La descarga conserva el archivo parcial para reanudación. Se añadieron pruebas para Job activo, Job cancelado y ausencia de Job (fallo seguro).

## Verificación criptográfica completada en código

- Moonshine Base ES cuantizado: el archivo de release está fijado a 50.846.902 bytes y SHA-256 `850c3dcc5dfccc8b1feb10bb221b11d6039b6f5c626241729f46863771016383`. Tras extraer, se genera un manifiesto con SHA-256 de cada archivo ASR y se vuelve a verificar en cada comprobación de disponibilidad.
- Piper Sharvard ES INT8: el archivo de release está fijado a 23.477.120 bytes y SHA-256 `bf5507cbdedea650365bd6175b3164a8ecca5847533ddf97afcba9d76ea599d1`. Tras extraer, se genera y verifica el manifiesto por archivo TTS.
- Qwen2.5 1.5B Q4_K_M: descarga fijada a 1.117.320.736 bytes y SHA-256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`. Si ya existe un modelo legado sin manifiesto, se calcula el hash completo una vez y solo se crea el manifiesto si coincide. En arranques siguientes se comprueban tamaño, ruta, conjunto de archivos y metadatos; si el tamaño o mtime cambian, se vuelve a calcular SHA-256. Los perfiles adicionales ya verifican su hash fijado.
- El manifiesto detecta cambios de contenido, archivos añadidos/eliminados y rutas inseguras. No sustituye una firma digital del editor: el anclaje de confianza reside en los SHA-256 fijados en el código de ORION.
- El conjunto inicial descarga aproximadamente 1,1 GiB (LLM ~1,04 GiB + ASR ~48,5 MiB + TTS ~22,4 MiB), no cantidades de varios GB. Las descargas usan la conexión disponible; embeddings y actualizaciones de conocimiento siguen diferidas y en red no medida.
- La verificación del LLM puede leer aproximadamente 1,04 GiB del almacenamiento una vez en cada arranque de proceso; es un coste deliberado para detectar corrupción que preserve tamaño y fecha. ASR/TTS se vuelven a hashear por archivo (unos 70 MiB combinados). La instalación inicial prepara el perfil Lite. Los perfiles Balanced/Powerful/Flagship se descargan desde Ajustes y se activan al reiniciar; la selección automática por RAM no significa que el modelo superior esté instalado.
- Las pruebas automatizadas no sustituyen medir en el Samsung: tiempo de primer arranque, interrupción real de red, espacio insuficiente, cierre forzado durante descarga/extracción, recuperación tras reinicio y presión térmica/RAM.

## Criterio de aceptación para la primera prueba general

- Primer arranque: progreso por componente, secuencia sin bloqueos y mensajes de error accionables.
- Pausar: el tráfico se detiene cooperativamente y el archivo parcial queda disponible para reanudar.
- Reanudar: no vuelve a descargar bytes ya validados cuando el servidor soporta Range.
- Fallo de red/servidor: no activa archivos incompletos y permite reintentar.
- Reinicio de proceso: recupera backups y estados de instalación sin declarar instalado un artefacto incompleto.
- Arranques siguientes: no descarga modelos ya instalados y no carga innecesariamente todos los modelos en RAM.
