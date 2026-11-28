# Auditoría de aceleración de inferencia Android

Fecha: 2026-11-28.

## Hallazgo confirmado en el código de ORION

LlamaCppLanguageModel configura explícitamente n_gpu_layers = 0 al inicializar el motor nativo. Por tanto, ORION solicita ejecución CPU-only incluso si el teléfono dispone de GPU. La aplicación usa llamacpp-kotlin 0.4.0 (io.github.ljcamargo), pero el wrapper actual no expone en el código de ORION una detección de backend ni una selección Vulkan/OpenCL. Cambiar solo n_gpu_layers a un valor positivo no sería una solución válida: si el binario nativo no fue compilado con el backend correspondiente, la app podría fallar o ignorar la opción.

## Evidencia del wrapper nativo instalado: CPU-only confirmado

Se ha inspeccionado el código fuente del proyecto exacto publicado como `io.github.ljcamargo:llamacpp-kotlin:0.4.0`:

- `LlamaAndroid.kt` selecciona bibliotecas nativas específicas según `/proc/cpuinfo`: variantes ARM64 con DotProd e I8MM cuando están disponibles, y fallback ARM64/x86_64.
- El método `startEngine` devuelve explícitamente `gpu = false` y `reasonNoGPU = "Currently not supported"`.
- Su `CMakeLists.txt` compila con `LM_GGML_USE_CPU` y no configura `GGML_OPENCL`, Vulkan ni Hexagon.
- ORION pasa `n_gpu_layers = 0` en la inicialización.

Conclusión: el backend integrado actual es CPU con selección de kernels ARM optimizados; no es GPU ni NPU. No hay que cambiar el número de capas GPU esperando que aparezca un backend que el binario no contiene. La selección de kernels CPU se basa en características del procesador y es una optimización real ya presente.

## Nueva evidencia: distribución Android con OpenCL y Hexagon

Se ha localizado el proyecto público `midnightkoderr/llama.cpp-android`, que publica builds Android arm64 diferenciadas: una variante OpenCL para Adreno y otra que declara CPU + OpenCL + Hexagon HTP, con bibliotecas para varias generaciones Hexagon. La publicación muestra checksums de los archivos comprimidos y ejemplos de uso de HTP.

El repositorio incluye un fichero LICENSE que declara MIT, aunque contiene texto adicional de atribuciones de terceros que debe revisarse por componente antes de redistribuir artefactos. La release v1.1.0 publica un archivo arm64 Hexagon de 15.583.502 bytes con SHA-256 `f8d5130d27586276ed13b1af5a9ea13f1cc76fb2cafaa76dc69f5043f871b855`; el release declara llama.cpp v1.1.0 y el repositorio estaba en el commit `c8c5e463a0c9b52c20903bd8cd27978de9b01fd3` al auditarlo. Aun así, el archivo es un paquete de ejecutables CLI/servidor y bibliotecas compartidas, no un AAR JNI compatible con ORION. No se debe incorporar directamente: hay que portar/compilar los backends en el wrapper de ORION o diseñar un proceso aislado con pruebas de Android, empaquetado, linker, permisos y seguridad. La adopción pública es muy baja y la cadena de suministro de los binarios no está verificada.

## Ruta segura de mejora

1. Mantener CPU como fallback universal.
2. Mantener como referencia confirmada el wrapper 0.4.0: CPU-only con dispatch ARM DotProd/I8MM; no activar n_gpu_layers mientras no se compile un backend real.
3. Evaluar KleidiAI como mejora CPU independiente; el wrapper actual no demuestra que lo incluya.
4. Prototipar Vulkan u OpenCL en un flavor experimental separado. llama.cpp documenta OpenCL para Adreno, pero requiere compilar y empaquetar el backend y sus dependencias; no basta con una preferencia Kotlin.
5. Detectar GPU/backend real en runtime y ejecutar una prueba de carga pequeña. Si falla, no cargar capas GPU y mantener CPU.
6. Comparar CPU/GPU por dispositivo: tokens/s, primer token, RAM pico, latencia, batería, temperatura y estabilidad tras 10-15 minutos.
7. No cargar todas las capas en GPU por defecto. Calcular un presupuesto conservador con memoria disponible, reservar margen para Android, audio y UI, y permitir rollback.
8. No prometer uso de Hexagon/NPU en Snapdragon hasta verificar un runtime, delegado y modelo compatibles en el dispositivo objetivo.

## LiteRT-LM como backend alternativo

Google publica LiteRT-LM con API Kotlin estable, ejecución local y backends CPU/GPU/NPU en Android. La documentación lista soporte para Gemma, Llama, Phi-4 y Qwen. Esto justifica un prototipo independiente, pero no un reemplazo inmediato: hay que validar conversión/disponibilidad del modelo, licencia, tamaño, calidad en español, uso de herramientas, streaming, consumo y compatibilidad real con el dispositivo objetivo.

## Decisión actual

- Llama.cpp CPU-only solicitado por ORION: confirmado (n_gpu_layers = 0).
- Vulkan/OpenCL en el binario actual: no verificado; no debe asumirse.
- KleidiAI en el binario actual: no verificado.
- Snapdragon Hexagon/HTP: existe paquete comunitario CLI/servidor con SHA-256 publicado, pero no es compatible directamente con el JNI actual; portado/integración pendiente.
- CPU ARM64 DotProd/I8MM: dispatch por características detectado en el wrapper 0.4.0.
- LiteRT-LM Kotlin: candidato para prototipo comparativo.
- Cambio de backend por defecto: bloqueado hasta disponer de benchmark y fallback verificados.

## Fuentes

- llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- llama.cpp OpenCL / Adreno: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- Wrapper Kotlin nativo usado por ORION: https://github.com/ljcamargo/kotlinllamacpp
- Build comunitario Android OpenCL + Hexagon (referencia de portado; no integrar el tarball directamente): https://github.com/midnightkoderr/llama.cpp-android
- Release v1.1.0 y digest: https://github.com/midnightkoderr/llama.cpp-android/releases/tag/v1.1.0
- llama.cpp build / KleidiAI / OpenCL: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- LiteRT-LM overview y backends Android: https://developers.google.com/edge/litert-lm/overview
