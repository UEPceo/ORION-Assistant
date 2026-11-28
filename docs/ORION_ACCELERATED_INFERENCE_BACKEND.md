# ORION — Backend de inferencia acelerado (GPU/NPU)

Estado: decisión de arquitectura y auditoría de compatibilidad; backend acelerado todavía no integrado en producción.

## Objetivo

Añadir aceleración real por GPU y, cuando el SoC y el runtime lo permitan, NPU, sin retirar el backend llama.cpp CPU que ya funciona como ruta de compatibilidad. ORION no debe anunciar un acelerador por detectar únicamente una GPU/NPU: el backend debe inicializarse, ejecutar una inferencia de prueba y superar validaciones de estabilidad.

## Situación actual

- El backend de producción actual es llama.cpp mediante `llamacpp-kotlin 0.4.0`, con `n_gpu_layers = 0`: inferencia CPU.
- El wrapper selecciona bibliotecas CPU ARM según las capacidades que detecta, pero no ofrece GPU/NPU en el binario usado por ORION.
- El modelo principal actual está en formato GGUF. No puede abrirse directamente como un modelo LiteRT-LM `.litertlm`.
- El teléfono de referencia reportado por el proyecto es Samsung SM-A176B. Las fichas públicas identifican la familia Galaxy A17 5G con Exynos 1330 y GPU Mali-G68 MP2. La RAM y revisión exactas del dispositivo de prueba deben leerse de los diagnósticos de ORION.
- No se ha demostrado todavía que el Exynos 1330 concreto exponga un backend NPU compatible con LiteRT-LM. La compatibilidad NPU no se presume a partir de que el SoC sea Exynos.

## Backend candidato: LiteRT-LM

La documentación oficial de Google ofrece API Kotlin para Android, modelo `.litertlm` y selección de backend CPU, GPU o NPU. La integración GPU Android requiere las bibliotecas nativas correspondientes y declarar como opcionales `libvndksupport.so` y `libOpenCL.so` en el manifiesto. El backend NPU puede requerir bibliotecas específicas del fabricante.

Documentación:
- https://developers.google.com/edge/litert-lm/android
- https://developers.google.com/edge/litert-lm/overview
- https://developers.google.com/edge/litert/next/litert_lm_npu

No se debe incorporar una versión flotante `latest.release` en producción. Antes de añadir la dependencia se fijará una versión estable concreta, se verificará su licencia, sus ABI, tamaño de APK, reglas R8 y compatibilidad con Android 8+.

## Compatibilidad del proyecto y modelos candidatos (28/11/2026)

- La documentación oficial ofrece la API Kotlin Android de LiteRT-LM. La versión publicada más reciente localizada es 0.19.1.
- El proyecto ORION usa Kotlin 2.2.10. Los AAR LiteRT-LM 0.17+ usan metadata Kotlin 2.4.0, que no es compatible con el compilador 2.2.10. No añadir el AAR sin resolver primero esta incompatibilidad.
- La matriz oficial de Kotlin indica que KGP 2.4.21 admite Gradle hasta 9.7 y AGP hasta 9.3.1. El proyecto usa Gradle 9.3.1 y AGP 9.1.1, por lo que 2.4.21 es un candidato compatible; aun así, hay que actualizar plugin Compose y ejecutar toda la suite antes de incorporar LiteRT-LM.
- El repositorio `litert-community/Qwen2.5-1.5B-Instruct` publica variantes `.litertlm` GPU/CPU. La tabla de referencia en Samsung S25 Ultra informa aproximadamente 1.598 MB de modelo dinámico int8 y picos de memoria cercanos a 1.85 GB con GPU. Es una referencia de otro teléfono, no una predicción para el SM-A176B; puede ser demasiado exigente en la variante de 4 GB.
- No se debe seleccionar el artefacto solo por ser el mismo nombre de modelo: cada archivo `.litertlm` puede estar cuantizado y compilado para backend, contexto o SoC concretos. Hay que fijar URL/revisión, tamaño, SHA-256, licencia y requisitos exactos.
- Antes de probar GPU hay que consultar el dispositivo real (propiedades SoC, memoria, disponibilidad de `libOpenCL.so` y capacidad de inicializar LiteRT-LM GPU). Que el dispositivo tenga Mali-G68 no garantiza que el driver exponga un OpenCL utilizable.
- No se ha encontrado evidencia suficiente para declarar que el Exynos 1330 tenga una ruta NPU utilizable por LiteRT-LM. La ruta inicial para el dispositivo de referencia es CPU → GPU; NPU queda bloqueada hasta verificación del runtime y del SoC exacto.

Fuentes de referencia:
- API Kotlin LiteRT-LM: https://developers.google.com/edge/litert-lm/android
- Versiones y compatibilidad Kotlin: https://kotlinlang.org/docs/gradle-configure-project.html
- LiteRT-LM Qwen2.5-1.5B: https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct
- LiteRT-LM Android GPU/NPU: https://developers.google.com/edge/litert/next/litert_lm_npu

## Diseño de integración

1. Introducir una abstracción de backend que mantenga el contrato `LocalLanguageModel` y permita seleccionar implementaciones sin cambiar conversación, memoria, RAG ni herramientas.
2. Mantener llama.cpp GGUF CPU como fallback universal y ruta offline conocida.
3. Crear un adaptador LiteRT-LM independiente para modelos `.litertlm`; no convertir ni sustituir automáticamente el GGUF existente.
4. Añadir catálogo de artefactos con formato, versión, licencia, tamaño, SHA-256, ABI, contexto y backends admitidos.
5. Detectar propiedades reales del dispositivo y capacidades de bibliotecas/runtime. GPU y NPU solo se ofrecen si el runtime las inicializa y la inferencia de prueba termina correctamente.
6. Orden inicial de evaluación para el dispositivo Exynos/Mali: CPU de referencia → GPU experimental → NPU únicamente si el fabricante/runtime confirma compatibilidad para el SoC exacto.
7. Si la inicialización, compilación del modelo o inferencia acelerada falla, liberar recursos y volver al backend CPU sin perder la conversación.
8. Evitar cargar simultáneamente LLM, ASR y TTS en RAM/GPU cuando la memoria disponible no lo permita. El gestor de memoria debe controlar carga, descarga y presión térmica.

## Validación obligatoria

Medir con el mismo prompt, modelo, contexto y salida:
- latencia de primer token, prefill y tokens/s;
- RAM/PSS pico y memoria GPU cuando esté disponible;
- tiempo de carga y caché de compilación;
- consumo energético, temperatura y throttling a 5, 10 y 15 minutos;
- estabilidad con ASR/TTS, audio Bluetooth, interrupciones y recuperación;
- calidad de respuesta y compatibilidad de herramientas frente al backend CPU.

La GPU solo será predeterminada si mejora el rendimiento útil sin degradar estabilidad, memoria o batería. La NPU solo se habilitará si el modelo y el SoC están oficialmente soportados y los resultados se reproducen.

## Estado de entrega

- Auditoría del wrapper CPU actual: completada.
- Candidato LiteRT-LM Kotlin: identificado.
- Backend GPU LiteRT-LM en ORION: pendiente.
- Modelo `.litertlm` de código/instrucción compatible y fijado: pendiente.
- NPU en el Samsung SM-A176B/Exynos 1330: no verificada.
- Benchmark físico y política de fallback: pendientes.
