# ORION — Estado de viabilidad del despertador offline

Fecha de revisión: 2026-11-28.

## Decisión técnica

El motor sherpa-onnx ya incluido en ORION dispone de API Kotlin para keyword spotting en streaming (KeywordSpotter, OnlineStream, acceptWaveform, isReady, decode, getResult y reset). La integración Android puede reutilizar la dependencia existente; no hace falta incorporar un SDK comercial ni otro runtime.

Sin embargo, la documentación oficial de modelos KWS de sherpa-onnx consultada en esta fecha enumera modelos preentrenados para chino, inglés y chino+inglés. No aparece un modelo KWS español. ORION usa Moonshine Base ES v2 para ASR, pero eso no convierte automáticamente ese modelo ASR en un detector KWS: son arquitecturas y contratos de inferencia diferentes.

Por tanto, no se debe conectar un modelo inglés/chino a la palabra «ORION» y declarar soporte español. Eso generaría falsos negativos/falsos positivos y una falsa garantía de fiabilidad. Tampoco se activa el micrófono en segundo plano como sustituto provisional.

## Contrato de integración

La integración final debe cumplir estos requisitos:

1. Modelo KWS entrenado o validado para español, con licencia redistribuible y procedencia verificable.
2. Descarga opcional y explícita, con tamaño, checksum SHA-256, reanudación, espacio libre y borrado controlado.
3. Detección completamente local; ninguna muestra de audio sale del dispositivo.
4. Activación por el usuario desde Ajustes, permiso RECORD_AUDIO, notificación persistente y acción visible para detener el servicio.
5. Servicio foreground de tipo microphone compatible con las restricciones de Android recientes; no iniciar silenciosamente desde BOOT_COMPLETED.
6. Pausa automática durante llamadas, cuando el permiso se revoca, al perder el estado autorizado o ante errores de captura.
7. Perfil de consumo configurable: solo mientras la pantalla está activa como valor inicial; modo de pantalla apagada solo tras mediciones de batería/temperatura y confirmación explícita.
8. Umbral calibrable, cooldown tras activación, supresión de duplicados y opción de desactivar rápidamente el detector.
9. Al detectar la palabra, cerrar o pausar el KWS y transferir el control al flujo de voz existente; no mantener dos capturas de micrófono simultáneas.
10. Pruebas de audio real en español: diferentes acentos, ruido, televisión, música, distancia, voz baja y falsos disparos por conversaciones similares.

## Estado

- Runtime KWS sherpa-onnx disponible en dependencia: sí.
- API Kotlin de keyword spotting: verificada en upstream.
- Modelo preentrenado español oficial: no localizado en el catálogo oficial consultado.
- Servicio foreground de micrófono y control de consentimiento: no implementado para wake word.
- Activación permanente o en segundo plano: deshabilitada.
- Pruebas acústicas con hablantes reales: pendientes.

## Fuentes técnicas

- sherpa-onnx Kotlin API: https://github.com/k2-fsa/sherpa-onnx/blob/master/sherpa-onnx/kotlin-api/KeywordSpotter.kt
- Catálogo oficial KWS: https://k2-fsa.github.io/sherpa/onnx/kws/index.html
- Ejemplo oficial Android KWS: https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws

## Próximo paso técnico

Localizar o entrenar un modelo KWS pequeño para español (preferentemente Zipformer transducer int8) y validar licencia, tamaño y calidad antes de añadirlo al instalador de modelos. En paralelo, continuar con la auditoría de aceleración del LLM local, sin bloquear el resto del desarrollo por la ausencia del modelo español.
