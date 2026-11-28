# Auditoría de aceleración de inferencia Android

Fecha: 2026-11-28.

## Hallazgo confirmado en el código de ORION

LlamaCppLanguageModel configura explícitamente n_gpu_layers = 0 al inicializar el motor nativo. Por tanto, ORION solicita ejecución CPU-only incluso si el teléfono dispone de GPU. La aplicación usa llamacpp-kotlin 0.4.0 (io.github.ljcamargo), pero el wrapper actual no expone en el código de ORION una detección de backend ni una selección Vulkan/OpenCL. Cambiar solo n_gpu_layers a un valor positivo no sería una solución válida: si el binario nativo no fue compilado con el backend correspondiente, la app podría fallar o ignorar la opción.

## Nueva evidencia: distribución Android con OpenCL y Hexagon

Se ha localizado el proyecto público `midnightkoderr/llama.cpp-android`, que publica builds Android arm64 diferenciadas: una variante OpenCL para Adreno y otra que declara CPU + OpenCL + Hexagon HTP, con bibliotecas para varias generaciones Hexagon. La publicación muestra checksums de los archivos comprimidos y ejemplos de uso de HTP.

Esto demuestra que existe una vía técnica que merece un prototipo, pero **no es una dependencia aprobada para ORION**: el repositorio declara licencia `NOASSERTION`, tiene muy poca adopción pública y no se ha verificado su cadena de suministro, compatibilidad binaria con el wrapper Kotlin actual, API de inicialización, cobertura de dispositivos ni licencia de todos los artefactos. No incorporar ni descargar sus binarios en la app hasta completar esa auditoría. La documentación oficial de llama.cpp sí confirma OpenCL para Adreno en determinados dispositivos Snapdragon.

## Ruta segura de mejora

1. Mantener CPU como fallback universal.
2. Auditar el binario AAR/JNI exacto de llamacpp-kotlin 0.4.0: versión upstream llama.cpp, flags de CMake, backends incluidos, ABIs, bibliotecas compartidas y licencia.
3. Activar primero optimización CPU arm64 con KleidiAI, que la documentación actual de llama.cpp contempla para Android NDK arm64-v8a. Es una ruta más portable que asumir soporte GPU.
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
- Snapdragon Hexagon/HTP: existe un build comunitario de referencia, pero no está integrado ni aprobado; licencia del repositorio NOASSERTION y compatibilidad ORION pendientes.
- LiteRT-LM Kotlin: candidato para prototipo comparativo.
- Cambio de backend por defecto: bloqueado hasta disponer de benchmark y fallback verificados.

## Fuentes

- llama.cpp Android: https://github.com/ggml-org/llama.cpp/blob/master/docs/android.md
- llama.cpp OpenCL / Adreno: https://github.com/ggml-org/llama.cpp/blob/master/docs/backend/OPENCL.md
- Build comunitario Android OpenCL + Hexagon (solo referencia, no dependencia aprobada): https://github.com/midnightkoderr/llama.cpp-android
- llama.cpp build / KleidiAI / OpenCL: https://github.com/ggml-org/llama.cpp/blob/master/docs/build.md
- LiteRT-LM overview y backends Android: https://developers.google.com/edge/litert-lm/overview
