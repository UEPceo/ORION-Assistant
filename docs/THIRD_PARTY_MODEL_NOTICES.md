# Avisos de terceros: encoder neuronal opcional

## Modelo de embeddings
- Artefacto: `combined_tokenizer_embedded_model.onnx`
- Repositorio: https://huggingface.co/onnx-community/distiluse-base-multilingual-v2-merged-onnx
- Revisión fijada: `469261b`
- SHA-256 esperado: `827d1a23e47f8c68a5788e58a06e137edf52aa568a6e4c852f4ce79f21b8a205`
- Licencia declarada en la ficha del artefacto: MIT.
- Modelo base: `sentence-transformers/distiluse-base-multilingual-cased-v2`, licencia Apache-2.0; la ficha describe embeddings multilingües y una arquitectura DistilBERT + mean pooling + capa densa.
- El archivo ONNX se descarga a petición del usuario y no se distribuye dentro del APK. Antes de activarlo se verifica el SHA-256 fijado en el código.

## Runtime
- ONNX Runtime Android: https://github.com/microsoft/onnxruntime — MIT.
- ONNX Runtime Extensions Android: https://github.com/microsoft/onnxruntime-extensions — MIT.
- Versiones integradas: ONNX Runtime Android 1.22.0 y Extensions Android 0.13.0.

Las licencias y procedencia se documentan para trazabilidad. Antes de actualizar el modelo debe revisarse de nuevo la licencia del artefacto, la licencia del modelo base, la revisión, el tamaño, el formato de entrada/salida y el SHA-256. No se debe cambiar la revisión remota sin actualizar el pin y el digest tras una verificación independiente.
