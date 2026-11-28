# ORION — Validación real en dispositivo

Estado: la suite instrumentada está añadida, pero todavía no se ha ejecutado en el teléfono objetivo. Un build CI o Robolectric no sustituye una ejecución física.

## Pruebas automatizadas en Android

Ejecutar con el teléfono conectado y depuración USB autorizada:

```bash
./gradlew :app:connectedDebugAndroidTest
```

La suite `DeviceRuntimeValidationTest` comprueba:

1. Room usa SQLCipher: `PRAGMA cipher_version` devuelve versión y la cabecera de la base no es SQLite en claro.
2. El encoder ONNX se inicializa y ejecuta inferencia real. Si el modelo opcional no está instalado, la prueba se marca como omitida, no como superada.
3. Los embeddings tienen dimensión 512, norma unitaria aproximada y el pasaje relevante obtiene mayor similitud que un distractor.
4. El sistema informa la clase de memoria Android y el heap disponible sin asumir una gama mínima.

La migración SQLCipher desde la base antigua en claro tiene además una prueba instrumentada independiente que verifica preservación de filas y `user_version`.

## Matriz manual pendiente

- Galaxy A17 5G / SM-A176B: instalar desde cero, completar descargas y ejecutar la suite instrumentada.
- Actualización desde versión con Room sin cifrar: comprobar conversión y conservación de conversaciones, tareas, memoria y planes.
- Cierre forzado durante la conversión: confirmar recuperación desde el respaldo o el temporal sin borrar el original.
- Gama baja: medir pico PSS, memoria libre, latencia ASR/LLM/TTS y comportamiento térmico en conversación larga.
- Recuperación: medir Recall@k/MRR con consultas de referencia por dominio; comparar búsqueda léxica, semántica dispersa y ONNX.
- Encoder ONNX: medir latencia de inicialización, primer embedding, embeddings sucesivos, consumo de RAM y estabilidad tras presión de memoria.
- ComfyUI: verificar endpoint real, workflow de imagen y vídeo, modelo disponible, subida de referencia, cancelación, timeout y descarga de salida.
- Desconexión de red/proveedor: la capacidad debe pasar a no disponible y no anunciar una generación completada.

## Interpretación

- Una prueba omitida por falta del modelo no es un éxito.
- Un smoke test de inferencia demuestra que el encoder carga y produce un vector válido; no demuestra que la recuperación sea semánticamente buena.
- La prueba de ranking con dos frases es una comprobación básica, no un benchmark editorial.
- El estado del dispositivo debe registrarse con modelo, versión de Android, ABI, RAM, versión de ORION, commit, resultado y duración.
