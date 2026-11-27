# ORION — Cerebro creativo, multimedia y estrategia de redes

## Arquitectura híbrida elegida

ORION utiliza una política local-first:
1. Si existe un proveedor local compatible y disponible, se selecciona antes que cualquier proveedor remoto.
2. Un proveedor remoto solo puede seleccionarse cuando la operación recibe autorización expresa (allowRemoteProcessing=true) y la conectividad está confirmada.
3. Sin autorización, el estado es REQUIRES_USER_CONSENT; sin red, REQUIRES_NETWORK. No se envía contenido automáticamente.
4. Los proveedores de aplicaciones externas nunca se invocan como motores internos; requieren una acción mediada por el usuario.
5. La autorización es por operación, no un permiso permanente.

El modelo conversacional Qwen2.5 1.5B sigue siendo texto-only. No se presenta como modelo de visión ni generador multimedia. La interfaz admite modelos locales especializados y servicios remotos multimodales.

## Diseño e identidad visual
Perfil ORION Editorial: navy #080D1A, cian #16C7E8 y blanco; estilo tecnológico-editorial limpio, alto contraste, jerarquía clara, legibilidad móvil y tono directo.

## Comprensión de imágenes y vídeos
MediaUnderstandingProvider admite análisis de imagen y vídeo. MediaFrame y VideoFrameSamplingPolicy establecen una cronología con muestreo limitado. MediaUnderstandingResult incluye resumen, sujetos/objetos, texto visible, acciones, cronología e incertidumbres. El análisis debe indicar si usa fotogramas muestreados y nunca inventar audio no procesado.

## Generación de imágenes y vídeos
ImageGenerationProvider y VideoGenerationProvider son contratos intercambiables. El runtime selecciona local primero; nube solo con consentimiento y red. Estados: AVAILABLE, REQUIRES_MODEL, REQUIRES_CONFIGURATION, REQUIRES_USER_CONSENT, REQUIRES_NETWORK y UNSUPPORTED. Sin motor ejecutable, ORION entrega brief/prompt/storyboard sin afirmar que creó el archivo.

## Estrategia social
El plan considera plataforma, público, objetivo y formato; propone gancho honesto, estructura, CTA y métricas. No inventa pesos de algoritmos ni promete viralidad. Las recomendaciones actuales requieren fuentes oficiales actualizadas y datos autorizados de la cuenta.

## Privacidad
- El usuario selecciona el contenido que se analizará.
- No se envía contenido a servicios remotos sin autorización expresa para esa operación.
- La selección local tiene prioridad aunque exista consentimiento remoto.
- Los permisos remotos no se heredan de operaciones anteriores.
- Publicar, borrar, enviar mensajes o modificar cuentas requiere conector y confirmación contextual.
- Se identifica proveedor/modelo y disclosure cuando corresponda.

## Estado
Implementado: contratos tipados local/remoto, router local-first, estados explícitos de consentimiento/red, validación de cronología y pruebas unitarias del enrutamiento.
Pendiente: proveedor local de visión, importación de archivos y extracción de fotogramas Android, proveedor remoto real de visión/generación, controles de tamaño/compresión/cancelación/timeout/costes, fuentes sociales vivas.
Referencias técnicas: https://developer.android.com/ai/overview y https://developer.android.com/ai/gemini/developer-api.
