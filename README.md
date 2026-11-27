# ORION Voice Lab 0.4 (Android / Jetpack Compose)

Primer núcleo conversacional de voz **100 % offline y de coste cero (0 €)** en español para Android (`arm64-v8a`), construido sobre Voice Lab 0.3 con **Kotlin**, **Jetpack Compose (Material 3)**, **sherpa-onnx** (`1.13.8`) y **llama.cpp** (`io.github.ljcamargo:llamacpp-kotlin:0.4.0`).

## Flujo conversacional offline (0.4)

1. **Llamada virtual (`Llamada`)**: Pulsa **Llamar a ORION** para abrir la sesión bidireccional continua.
2. **Captura + cancelación de eco (`VoiceInputEngine`)**: `AudioRecord` a 16 kHz mono PCM-16 (`VOICE_COMMUNICATION` + `AcousticEchoCanceler` / `NoiseSuppressor` cuando están disponibles en hardware).
3. **VAD (`SileroVoiceActivityDetector`)**: `silero_vad.onnx` (MIT) sobre ventanas de 32 ms (512 muestras), cierre de frase tras ~500 ms de silencio y detección de interrupciones (*barge-in*).
4. **ASR (`MoonshineSpeechRecognitionEngine`)**: `sherpa-onnx-moonshine-base-es-quantized-2026-02-27` en hilo único dedicado.
5. **LLM local (`LocalLanguageModel` / `LlamaCppLanguageModel`)**: **Qwen2.5-1.5B-Instruct Q4_K_M** (~1,04 GiB, **Apache 2.0**), ejecutado localmente mediante `llamacpp-kotlin` con `use_mmap = true`, contexto 2048 tokens y streaming token a token.
6. **TTS natural en español (`LocalSpeechSynthesizer` / `SherpaOnnxSpeechSynthesizer`)**: **Piper VITS `es_ES-sharvard-medium-int8`** (~23,5 MB, **MIT / CC-BY 3.0**), con **2 voces españolas** (`Voz Femenina ES` / `Voz Masculina ES`), síntesis por frases mientras el LLM sigue generando y reproducción por `AudioTrack` (`AudioPlaybackEngine`).
7. **Interrupción (*barge-in*) y control de RAM**: Botón **Interrumpir** o voz sostenida del usuario detienen al instante `AudioTrack`, `OfflineTts` y `llama.cpp`, volviendo a escuchar; botón **Liberar RAM** descarga los modelos de memoria manteniendo los archivos en disco.
8. **Compatibilidad 0.3 (`Laboratorio`)**: Mantiene íntegros los controles, métricas Fase 0 (`DeviceMetrics`), transcripciones sin respuesta y exportación de informe.

## Licencias y descarga offline (0 €)

- **Silero VAD** (`silero_vad.onnx`, incluido en `assets/`): MIT License.
- **Moonshine Base ES v2** (`~125 MB`, descarga única): Moonshine AI Community License.
- **Qwen2.5-1.5B-Instruct Q4_K_M GGUF** (`~1,04 GiB`, descarga única con reanudación HTTP `Range` y verificación de cabecera `GGUF`): Apache 2.0.
- **Piper VITS `es_ES-sharvard-medium-int8`** (`~23,5 MB`, descarga única): MIT (sherpa-onnx / Piper) y CC-BY 3.0 (dataset Sharvard).

Sin claves API, sin suscripciones ni llamadas a servicios en la nube.

## Compilación y estado de verificación

- **APK de depuración**: `app/build/outputs/apk/debug/app-debug.apk` (`gradle assembleDebug`, ABIs `arm64-v8a` y `x86_64`, ~68 MB).
- **Pruebas unitarias JVM verificadas (`gradle :app:testDebugUnitTest`)**: 10/10 superadas (formato ChatML, segmentación de frases para TTS, sesión multi-turno, validación GGUF, extracción segura `.tar.bz2` con `espeak-ng-data/`, configuraciones `sherpa-onnx` ASR/VAD/TTS y enlace de `LlamaAndroid`).
- **Validación física pendiente**: La ejecución real sobre audio, inferencia ASR/LLM/TTS en dispositivo, interrupción por voz en altavoz y telemetría real (RAM PSS, temperatura, batería y latencia de 5 minutos) debe ejecutarse físicamente en el **Samsung Galaxy A17 5G** (no hay emulador físico ni dispositivo conectado en el entorno de compilación).

