# ORION Voice Lab 0.4.1 (Android / Jetpack Compose)

Primer núcleo conversacional de voz **100 % offline y de coste cero (0 €)** en español para Android (`arm64-v8a`), construido sobre Voice Lab 0.3 con **Kotlin**, **Jetpack Compose (Material 3)**, **sherpa-onnx** (`1.13.8`) y **llama.cpp** (`io.github.ljcamargo:llamacpp-kotlin:0.4.0`).

## Mejoras de estabilización (0.4.1)

1. **Compilación y firma reproducibles**:
   - **Gradle Wrapper oficial (`9.3.1`)** incluido (`gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`).
   - La variante `debug` usa `debug.keystore` en la raíz solo si existe localmente y recurre al almacén de depuración estándar (`~/.android/debug.keystore`) en clones limpios.
   - La variante `release` nunca utiliza la firma `debug`; solo firma mediante propiedades Gradle o variables de entorno (`ORION_RELEASE_STORE_FILE`, `ORION_RELEASE_STORE_PASSWORD`, `ORION_RELEASE_KEY_ALIAS`, `ORION_RELEASE_KEY_PASSWORD`).
   - `.gitignore` actualizado para excluir cachés, compilaciones, `local.properties`, keystores privados, credenciales, modelos descargados y `.part`, conservando `app/src/main/assets/silero_vad.onnx` y `gradle/wrapper/gradle-wrapper.jar`.
2. **Integridad y descarga reanudable de modelos (`ResumableHttpDownloader`)**:
   - Comprobación previa y en curso del espacio libre (`usableSpace`).
   - Reanudación HTTP `Range` con soporte verificado para `200`, `206` (validación de `Content-Range`) y `416` (completado exacto o limpieza de `.part` y reintento único desde byte 0).
   - Rechazo de respuestas HTML inesperadas, verificación de tamaño total recibido y reemplazo atómico con copia de respaldo recuperable.
   - Protección frente a *path traversal*, bytes nulos y enlaces simbólicos/duros en archivos `.tar.bz2`.
   - **Verificación SHA-256 oficial**: `Qwen2.5-1.5B-Instruct Q4_K_M` valida tamaño exacto (`1 117 320 736` bytes) y SHA-256 oficial de HuggingFace Git LFS (`6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`).
   - **Limitación documentada**: las etiquetas `asr-models` y `tts-models` de `k2-fsa/sherpa-onnx` no publican `checksum.txt` oficial verificable para `sherpa-onnx-moonshine-base-es-quantized-2026-02-27.tar.bz2` ni `vits-piper-es_ES-sharvard-medium-int8.tar.bz2`; no se han inventado hashes, validándose HTTP/tamaño, estructura BZip2/TAR, componentes requeridos y rutas seguras.
3. **Ciclo de vida nativo de `llama.cpp`, ASR, TTS y audio en llamada**:
   - Contexto LLM protegido con `AtomicReference<Int?>` (sin doble liberación ni contextos duplicados), época de generación y cancelación inmediata al liberar memoria o interrumpir.
   - Enrutamiento dinámico de llamada (`AudioRoutingController`) entre **altavoz**, **auriculares con cable/USB** y **Bluetooth** en `MODE_IN_COMMUNICATION`, restaurando el estado previo del sistema al colgar o pasar a segundo plano.
   - Verificación real post-habilitación de `AcousticEchoCanceler` y `NoiseSuppressor` (`AudioCaptureCapabilities`).
   - Guardas de concurrencia en `ConversationEngine` (`callRequested`, `callSessionEpoch`, `turnInFlight`) para evitar sesiones fantasma y turnos solapados.
4. **Panel de diagnóstico local (`Laboratorio -> DIAGNÓSTICO LOCAL · 0.4.1`)**:
   - Visibilidad en tiempo real del estado en disco y en RAM de ASR/LLM/TTS, uso de memoria PSS/libre, latencias de ASR, primer token LLM, velocidad (`tok/s`), primer audio TTS, duración de respuesta, estado DSP/micrófono, ruta de salida y últimos errores, también incluidos en **Copiar informe**.

## Flujo conversacional offline

1. **Llamada virtual (`Llamada`)**: Pulsa **Llamar a ORION** para abrir la sesión bidireccional continua.
2. **Captura + cancelación de eco (`VoiceInputEngine`)**: `AudioRecord` a 16 kHz mono PCM-16 (`VOICE_COMMUNICATION`).
3. **VAD (`SileroVoiceActivityDetector`)**: `silero_vad.onnx` (MIT) en `assets/` sobre ventanas de 32 ms (512 muestras), umbral `0.5`, pausa `0.5 s`.
4. **ASR (`MoonshineSpeechRecognitionEngine`)**: `sherpa-onnx-moonshine-base-es-quantized-2026-02-27` en hilo único dedicado.
5. **LLM local (`LocalLanguageModel` / `LlamaCppLanguageModel`)**: **Qwen2.5-1.5B-Instruct Q4_K_M** (`Apache 2.0`), `use_mmap = true`, contexto 2048 tokens y streaming token a token.
6. **TTS en español (`LocalSpeechSynthesizer` / `SherpaOnnxSpeechSynthesizer`)**: **Piper VITS `es_ES-sharvard-medium-int8`** (`MIT / CC-BY 3.0`), 2 voces españolas, reproducción por `AudioTrack`.
7. **Interrupción (*barge-in*) y control de RAM**: Botón **Interrumpir** o voz sostenida del usuario; **Liberar RAM** descarga modelos sin borrar archivos.

Sin claves API, sin suscripciones ni llamadas a servicios en la nube (coste **0 €**).
