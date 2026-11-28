# ORION — Arquitectura multimedia híbrida y motores gratuitos

## Motores elegidos

La estrategia prioriza software y pesos abiertos, autoalojados mediante ComfyUI. No se depende de cuotas gratuitas de terceros que puedan cambiar o desaparecer.

### Imagen — principal
- FLUX.1-schnell, servido por ComfyUI.
- Identificador: black-forest-labs/FLUX.1-schnell.
- Licencia del modelo: Apache 2.0.
- Uso: generación de imagen desde texto y referencias, piezas creativas y recursos sociales.
- Perfil de respaldo: Stable Diffusion 1.5, por madurez y menor exigencia, revisando licencia del checkpoint y restricciones de uso.

### Vídeo — principal
- Wan2.1 T2V-1.3B / I2V-1.3B, servido por ComfyUI.
- Licencia del repositorio/modelos Wan2.1: Apache 2.0.
- Uso: texto-a-vídeo e imagen-a-vídeo. Referencia oficial indica 8.19 GB de VRAM para T2V-1.3B y generación 480p en hardware de referencia.
- Perfil de calidad opcional: Wan2.1 14B, para servidores GPU de gama alta.

## Qué significa gratuito
Los pesos y el software seleccionados se pueden usar sin pagar una API por generación, sujetos a sus licencias. El alojamiento no es gratis por sí mismo: se necesita GPU, electricidad y almacenamiento. No se afirma que estos modelos puedan ejecutarse en el Samsung SM-A176B. ORION en Android será cliente de un endpoint ComfyUI autoalojado y configurable. Sin endpoint configurado, ofrecerá brief/storyboard y mostrará que el motor no está disponible.

No se establece ningún proveedor comercial con un supuesto free tier como motor principal. Los límites, precios y condiciones de terceros cambian y no garantizan uso gratuito sostenido.

## Subida automática con autorización
El flujo es automático después de la confirmación, no antes:
1. ORION prepara el archivo y calcula SHA-256.
2. Presenta al usuario archivo, tamaño, operación, destino/proveedor y finalidad.
3. Solo el botón de confirmación explícita puede emitir un permiso de subida.
4. El permiso queda ligado al hash, proveedor y operación concretos, caduca a los cinco minutos y solo se puede usar una vez.
5. Tras autorizar, ORION inicia automáticamente la subida al endpoint configurado y continúa la tarea.
6. Si cambia el archivo, destino u operación, caduca el permiso o falla la conexión, se detiene y solicita una nueva autorización.
7. No se reutiliza el permiso para otros archivos, generaciones, análisis ni publicaciones.

El permiso es temporal y en memoria. Tras reiniciar la app debe solicitarse de nuevo. La subida a un servidor autoalojado sigue siendo una transferencia de datos fuera del dispositivo: la pantalla debe mostrar claramente el host/destino real. No se envían credenciales ni tokens dentro del APK.

## Implementación en ORION
- Catálogo de motores predeterminados con identificadores y licencias.
- Contrato MediaUploadGateway desacoplado del proveedor.
- MediaUploadConsentGate con confirmación, hash SHA-256, destino, operación, caducidad y uso único.
- MediaUploadCoordinator inicia la transferencia solo tras consumir un permiso válido.
- Pruebas unitarias de rechazo sin consentimiento, vinculación del permiso, uso único y caducidad.

## Pendiente antes de generación real
- Instalar/configurar ComfyUI y descargar pesos en un servidor GPU controlado por el usuario.
- Implementar adaptadores HTTP específicos de ComfyUI (workflow API), estado/progreso, cancelación y descarga de resultados.
- Añadir selector Android de archivos y cálculo real de SHA-256 con ContentResolver.
- UI de confirmación que muestre host, nombre, tamaño, propósito y operación.
- Aplicar límites de tamaño, compresión, eliminación de temporales, timeout y manejo de errores.
- Verificar compatibilidad/licencia de cada checkpoint y workflow antes de habilitarlo.

## Referencias oficiales
- Wan2.1: https://github.com/Wan-Video/Wan2.1
- FLUX.1-schnell: https://github.com/black-forest-labs/flux
- ComfyUI: https://github.com/comfyanonymous/ComfyUI
- Stable Diffusion 1.5: https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5


## ComfyUI API integration — first execution layer

Se incorpora un cliente HTTP que puede enviar workflows JSON a un endpoint ComfyUI configurado, hacer polling de la cola, detectar errores, recuperar la salida y guardarla en caché privada. Permite upload multipart de referencias con límite de 25 MB; el adaptador de generación aún rechaza referencias porque la autorización de subida no está conectada al selector/UI. Las plantillas mapean nodo/input de prompt positivo y opcionalmente negativo; los nodos de referencia son un contrato preparado, no una ruta activa. Se añaden adaptadores de generación de imagen y vídeo que requieren endpoint y workflow suministrados en runtime. No se incluyen modelos ni endpoint preconfigurado. HTTPS obligatorio fuera de localhost/LAN privada; no se aceptan credenciales embebidas en URL.

Pendiente: formulario de endpoint y almacenamiento seguro de credenciales, selector/importador de workflows, mapping visual de nodos, consentimiento UI para referencias, configuración de providers en OrionMediaRuntime, cancelación/progreso persistente y pruebas de integración contra servidor ComfyUI real.


## Consentimiento para generación remota

Se añade un gate de aprobación efímero (5 minutos, una sola ejecución) que liga la aprobación al tipo de operación, identificador del proveedor y prompt concreto. El executor consume la aprobación antes de ejecutar y transmite el consentimiento explícito al router de proveedores; si no hay red, falla sin generar. El provider seleccionado queda ligado al ID aprobado. Esta capa todavía no está conectada a un diálogo o flujo de voz/UI de confirmación, por lo que la generación no está disponible como acción directa de usuario dentro de la app.

La aplicación permite cleartext para endpoints HTTP de LAN; el validador del ajuste multimedia limita HTTP a hosts locales/privados, aunque el permiso de Android es global para el proceso y no constituye una whitelist de red. Para despliegues públicos debe usarse HTTPS.


## Generación desde la interfaz: integración de ejecución (28/11/2026)

Se incorpora al apartado de Ajustes un flujo de generación directo para imagen y vídeo: prompt positivo/negativo, relación de aspecto, duración solicitada para vídeo, selección de hasta cuatro imágenes de referencia, diálogo explícito de consentimiento que identifica endpoint, operación, prompt y referencias, ejecución de ComfyUI, estado de espera/error y exportación del resultado a MediaStore (Pictures/ORION o Movies/ORION) para abrir/compartir. Se admite token Bearer guardado en preferencias privadas del dispositivo.

La plantilla API permite mapear el nodo de prompt, prompt negativo, nodo de dimensiones (width/height), nodo de duración (frames), FPS y nodos de imágenes de referencia. Los IDs se configuran/importan por workflow; no se presuponen nodos universales. La relación de aspecto se aplica a una resolución base de 768 px y múltiplos de 8. Para vídeo, los frames se calculan segundos×FPS+1, FPS predeterminado 16. Sin configurar los nodos opcionales, esos valores quedan como información y no se cambian en el workflow.

ComfyUI genera por workflow, por lo que ORION no fija una cuota de creaciones. El cliente impone límites técnicos por seguridad: workflow API de hasta 2 MiB; referencias de hasta 25 MiB cada una; máximo 4 seleccionadas en UI; salida hasta 150 MiB; tiempo máximo 5 minutos imagen y 15 minutos vídeo; duración de vídeo UI 1–60 s. La cola, VRAM, modelo, resolución real, storage y throughput del servidor pueden imponer límites adicionales. El software ComfyUI no cobra por creación; costes externos (hosting, electricidad, API/cloud si se usa), licencias/condiciones de los modelos y recursos del servidor son independientes.

Pendiente de validación real: workflows exportados específicos de FLUX.1-schnell, SD 1.5, Wan T2V e I2V en un servidor GPU; compatibilidad de nodes custom y sus nombres; análisis/understanding de imagen y vídeo con VLM (no conectado aún); tests end-to-end con resultado real, comprobación de MediaStore y verificación de build/CI. No se afirma que la generación funcione hasta conectar y probar un endpoint y workflows válidos.
