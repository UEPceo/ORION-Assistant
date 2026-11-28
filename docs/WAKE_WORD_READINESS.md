# ORION — Estado de viabilidad del despertador offline

Fecha de revisión: 2026-11-28.

## Hallazgo: candidato KWS español localizado

Se ha localizado el modelo comunitario jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08 en Hugging Face. Su tarjeta declara licencia Apache-2.0 para el modelo/receta y compatibilidad con sherpa-onnx. Es un Zipformer transducer streaming de aproximadamente 3,3 millones de parámetros, entrada mono 16 kHz, y permite configurar las palabras clave regenerando keywords.txt sin reentrenar.

Archivos requeridos: encoder, decoder, joiner, tokens.txt y keywords.txt. El repositorio también publica bpe.model para regenerar el archivo de palabras clave. Para inferencia int8 se indica encoder y joiner int8 con decoder fp32 o int8.

**Importante:** es un candidato, no una integración aprobada. Su evaluación publicada usa un único clip de 536,7 segundos, cinco frases de seguridad y un conjunto reducido de distractores; reporta 5/5 y cero falsos positivos en ese material. El propio autor advierte que no representa un benchmark amplio, que la cuantización int8 no se había evaluado para español y que la cobertura de acentos/ruido/voz espontánea es limitada. No hay resultados publicados para la palabra «ORION». La licencia declarada del modelo es Apache-2.0, pero la tarjeta también señala que deben verificarse las licencias concretas de las voces Piper usadas en la augmentación antes de redistribuir artefactos asociados.

## Integración propuesta

1. Descargar desde el repositorio de Hugging Face usando una revisión inmutable (commit SHA), no main flotante.
2. Obtener y fijar SHA-256 y tamaño esperado de cada artefacto. No habilitar descarga automática hasta tener hashes comprobados.
3. Generar keywords.txt para «orion» con el tokenizer BPE del modelo. Mantener la grafía en minúsculas salvo que la tabla BPE demuestre que la forma elegida es tokenizable. No asumir que mayúsculas/minúsculas son intercambiables.
4. Validar que el archivo generado contiene una secuencia BPE no vacía y un tag estable @orion.
5. Crear un test de inicialización del KeywordSpotter con el modelo descargado y ejecutar clips positivos y negativos en sherpa-onnx 1.13.8.
6. Antes de activar el detector en producción, evaluar voz peninsular y andaluza, varios hablantes, volumen/distancia, ruido doméstico, música, televisión, conversación parecida y falsos disparos por la palabra en frases normales.
7. Medir sensibilidad, falsos disparos por hora, CPU, memoria, temperatura y consumo en el teléfono objetivo.

## Arquitectura sherpa-onnx

La aplicación ya depende de sherpa-onnx v1.13.8. Su API Kotlin incluye KeywordSpotter, KeywordSpotterConfig, OnlineStream, acceptWaveform, isReady, decode, getResult y reset. No se necesita un SDK comercial ni conexión a Internet durante la detección.

## Contrato de seguridad y producto

- Detección completamente local; ninguna muestra de audio sale del dispositivo.
- Activación voluntaria desde Ajustes, permiso RECORD_AUDIO, notificación persistente y acción visible para detener el servicio.
- Servicio foreground de tipo microphone compatible con restricciones Android actuales; no iniciar silenciosamente desde BOOT_COMPLETED.
- Pausa durante llamadas, permiso revocado, pérdida de estado autorizado o errores de captura.
- Perfil inicial: solo mientras la pantalla está activa. Pantalla apagada solo tras mediciones de batería/temperatura y confirmación explícita.
- Umbral calibrable, cooldown, supresión de duplicados y desactivación rápida.
- Al detectar la palabra, parar KWS antes de iniciar el flujo ASR existente; nunca dos capturas simultáneas.
- No usar como sistema de emergencia ni prometer cero falsos positivos/negativos.

## Estado

- Runtime KWS sherpa-onnx v1.13.8 en la app: sí.
- Modelo KWS español candidato: localizado, pendiente de auditoría de artefactos.
- Licencia declarada del modelo: Apache-2.0; revisión de atribuciones/voz de entrenamiento pendiente.
- Palabra «ORION» tokenizada y validada: pendiente.
- Hashes y revisión inmutable fijados: pendiente.
- Detector integrado en app: no.
- Servicio foreground y controles de consentimiento: no.
- Pruebas acústicas con hablantes reales: no.

## Fuentes

- Modelo español: https://huggingface.co/jguerrisi/sherpa-onnx-kws-zipformer-es-3M-2026-08-08
- API Kotlin sherpa-onnx v1.13.8: https://github.com/k2-fsa/sherpa-onnx/blob/v1.13.8/sherpa-onnx/kotlin-api/KeywordSpotter.kt
- Ejemplo oficial Android KWS: https://github.com/k2-fsa/sherpa-onnx/tree/master/android/SherpaOnnxKws
- Issue de soporte español upstream: https://github.com/k2-fsa/sherpa-onnx/issues/3631
