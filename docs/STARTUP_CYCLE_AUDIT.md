# Auditoría del ciclo de arranque

Última revisión: 2026-11-28.

## Secuencia actual verificada en código

1. `OrionApplication.onCreate` instala diagnósticos de cierres, registra salidas históricas y programa actualizaciones de packs de conocimiento e instalación de embeddings.
2. `MainActivity.onCreate` crea el `VoiceLabViewModel` y muestra la interfaz Compose.
3. El inicializador del ViewModel configura motores, repositorios Room, preferencias, reconocimiento, LLM híbrido y TTS.
4. En `init`, se conectan observadores, se inicializa persistencia de conversaciones, se recuperan ejecuciones de planes, se reconcilian recordatorios, se refrescan estados/métricas y se inicia la descarga automática de modelos que falten.
5. La instalación inicial es secuencial: Piper TTS, Moonshine ASR y Qwen2.5 1.5B Q4_K_M. Cada instalador comprueba primero si el componente ya está instalado; las descargas parciales se conservan para reanudación.
6. La descarga usa archivos temporales, validación de tamaño/rangos HTTP, espacio libre, cabecera GGUF para el LLM y SHA-256 cuando el artefacto tiene hash configurado. La extracción usa directorios temporales y respaldo para poder recuperar una instalación anterior.
7. Los modelos no se cargan todos en RAM al abrir la aplicación: se cargan bajo demanda al iniciar escucha o conversación; existe liberación explícita de RAM.

## Coordinación de tareas de primer arranque

Se ha añadido `ModelManager.areCoreModelsInstalled` como condición única para declarar lista la pila offline obligatoria. El instalador automático de embeddings semánticos (opcional, ~136 MB) ahora se cancela/difiere si faltan TTS, ASR o LLM, y se programa de nuevo al terminar la instalación de los tres componentes principales. El propio Worker vuelve a comprobar la condición antes de descargar, para cubrir el caso en que ya estuviera encolado. Las actualizaciones de packs de conocimiento también se difieren mientras falte algún componente principal. Se añadió una prueba de que una instalación vacía nunca se declara lista.

## Corrección aplicada en esta auditoría

Se detectó que las descargas ASR/LLM/TTS recibían como condición de cancelación el estado del `viewModelScope`, no el Job de la instalación concreta. Al pulsar Pausar, el Job de descarga podía cancelarse mientras el bucle de E/S seguía sin conocer esa cancelación. Ahora cada instalador captura su Job actual y usa `DownloadCancellationGuard`, que comprueba el estado del Job y la interrupción del hilo. La descarga conserva el archivo parcial para reanudación. Se añadieron pruebas para Job activo, Job cancelado y ausencia de Job (fallo seguro).

## Límites pendientes antes de declarar el arranque cerrado

- ASR Moonshine y TTS Piper no tienen en este flujo un SHA-256 fijado: la validación se basa en estructura, nombres, tamaños mínimos y extracción segura. Debe mantenerse explícito; no se debe llamar verificación criptográfica.
- El LLM base valida SHA-256 durante la descarga, pero la comprobación rápida de instalación en arranques posteriores usa tamaño exacto y cabecera GGUF, no vuelve a hashear 1 GB en cada inicio. Esto evita una lectura completa del modelo en cada arranque, pero no detecta corrupción interna posterior si conserva tamaño y cabecera.
- La descarga automática de los modelos principales puede usar la conexión de datos disponible; no hay una política Wi-Fi-only ni una confirmación específica del consumo de datos. El paquete LLM ronda 1,04 GiB. Las tareas opcionales de embeddings y actualización de conocimiento sí exigen red no medida.
- La instalación inicial prepara el perfil Lite. Los perfiles Balanced/Powerful/Flagship se descargan desde Ajustes y se activan al reiniciar; la selección automática por RAM no significa que el modelo superior esté instalado.
- Las pruebas automatizadas no sustituyen medir en el Samsung: tiempo de primer arranque, interrupción real de red, espacio insuficiente, cierre forzado durante descarga/extracción, recuperación tras reinicio y presión térmica/RAM.

## Criterio de aceptación para la primera prueba general

- Primer arranque: progreso por componente, secuencia sin bloqueos y mensajes de error accionables.
- Pausar: el tráfico se detiene cooperativamente y el archivo parcial queda disponible para reanudar.
- Reanudar: no vuelve a descargar bytes ya validados cuando el servidor soporta Range.
- Fallo de red/servidor: no activa archivos incompletos y permite reintentar.
- Reinicio de proceso: recupera backups y estados de instalación sin declarar instalado un artefacto incompleto.
- Arranques siguientes: no descarga modelos ya instalados y no carga innecesariamente todos los modelos en RAM.
