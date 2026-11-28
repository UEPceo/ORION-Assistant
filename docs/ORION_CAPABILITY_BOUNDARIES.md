# ORION — Límites reales de aprendizaje, autonomía y multimedia

## Aprendizaje

Implementado:
- Captura de una fuente solo tras una orden explícita.
- Ingesta limitada de URL HTTPS y clasificación conservadora de autoridad.
- Cuarentena, revisión humana, publicación versionada, rollback y gate de regresión de recuperación.
- Evaluación semántica de respuestas integrada en el flujo de conversación, con abstención ante evidencia insuficiente.

No implementado:
- Entrenamiento automático de pesos del LLM.
- Aprendizaje automático de conversaciones, correos o archivos privados.
- Auto-publicación sin revisión humana.
- Auto-modificación del código, políticas o permisos de ORION.
- Auto-reparación autónoma de código sin revisión del diff y autorización.

El término «aprendizaje» se refiere a mantener un corpus recuperable, con procedencia y control editorial; no significa que el modelo neuronal se reentrene.

## Programación y autonomía

La ingeniería GitHub es parcial: inspección, propuesta de parche, validación, revisión exacta y escritura autorizada están implementadas. La recuperación de logs, reparación iterativa, runner universal y cierre autónomo del ciclo siguen siendo límites. ORION no debe describirse como programador completamente autónomo.

## Multimedia

ComfyUI tiene adaptadores de imagen y vídeo, pero no se registra un endpoint ni un workflow por defecto. Un proveedor no es operativo hasta que:
1. el endpoint sea configurado por el usuario;
2. el workflow pase validación estructural;
3. el endpoint supere una comprobación reciente de conexión;
4. la operación remota tenga consentimiento específico cuando transmite prompts o archivos.

El catálogo de motores describe opciones desplegables, no motores instalados ni disponibilidad en el móvil. La capacidad permanece como requiere configuración hasta superar la verificación.

## Datos y secretos

- Room se configura con SQLCipher; la clave aleatoria de la base se envuelve con AES-GCM usando una clave Android Keystore.
- La base antigua en claro se convierte mediante exportación SQLCipher y se conserva hasta que la nueva copia esté lista para activarse.
- Los planes rechazan patrones conocidos de credenciales; los resultados y errores redactan esos patrones antes de persistirlos.
- El detector de secretos es conservador, no una garantía contra cualquier secreto arbitrario. No introducir credenciales en objetivos, argumentos ni resultados.
- Android Auto Backup está desactivado para evitar restaurar una base cifrada sin la clave de Keystore del dispositivo. No existe aún exportación/importación cifrada entre dispositivos.

## Validación pendiente

La compilación CI valida código y tests en el entorno de CI. No demuestra el comportamiento de ONNX, el ranking semántico, SQLCipher en todos los ABI, ni rendimiento en el Galaxy A17. Para eso se ha añadido `docs/ORION_DEVICE_VALIDATION.md` y la suite instrumentada `connectedDebugAndroidTest`. Los resultados solo se pueden declarar después de ejecutarlos en el hardware real.
