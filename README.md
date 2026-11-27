# ORION Voice Lab 0.3 (Android / Jetpack Compose)

Laboratorio offline de reconocimiento de voz en español para Android, desarrollado con **Kotlin**, **Jetpack Compose (Material 3)** y **sherpa-onnx** bajo el principio de coste cero (0 €).

## Características verificadas

- **Reconocimiento español offline (`Moonshine Base ES v2`)**: Descarga una única vez el archivo oficial `.tar.bz2` desde GitHub Releases y extrae de forma segura `encoder_model.ort`, `decoder_model_merged.ort`, `tokens.txt` y `LICENSE` en el almacenamiento privado de la app (`filesDir/models/`).
- **Detección de actividad vocal (`Silero VAD`)**: Utiliza `app/src/main/assets/silero_vad.onnx` (644 KB, SHA-256 `9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6`, licencia MIT) sobre ventanas de 32 ms (512 muestras a 16 kHz) con cierre de frase tras ~500 ms de silencio.
- **Panel de medición Fase 0 (`DeviceMetrics`)**: Mide RAM libre/total, PSS de ORION, porcentaje y temperatura de batería, estado térmico, almacenamiento libre, tamaño del modelo en disco, tiempo de carga de modelo+VAD, latencia de decodificación por frase y factor de tiempo real (RTF), con refresco cada 15 s durante la escucha.
- **Exportación y control de sesión**: Botones **Medir ahora**, **Copiar informe** y **Borrar datos**, además de liberación segura de `OfflineRecognizer` en un hilo único (`OrionAsr`) para evitar liberaciones concurrentes durante la decodificación.

## Compilación y pruebas

- APK de depuración: `app/build/outputs/apk/debug/app-debug.apk` (`gradle assembleDebug`)
- Pruebas locales JVM / Robolectric: `gradle :app:testDebugUnitTest`
