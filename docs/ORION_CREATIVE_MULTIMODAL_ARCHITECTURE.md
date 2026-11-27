# ORION — Cerebro creativo, multimedia y estrategia de redes

## Alcance del módulo

El cerebro creativo está desacoplado del motor conversacional. La conversación clasifica la intención y añade reglas específicas al contexto del LLM; las tareas multimedia se ejecutan únicamente mediante proveedores tipados registrados en el runtime.

### Diseño e identidad visual

Perfil predeterminado ORION Editorial:
- Navy #080D1A, cian #16C7E8 y blanco.
- Dirección tecnológica-editorial, limpia y de alto contraste.
- Tipografía sans serif legible, jerarquía clara y composiciones móviles.
- Una idea principal por pieza, espacio negativo y respeto a zonas seguras.
- Tono directo, útil y sin exageraciones.

Los briefs incluyen objetivo, público, plataforma, texto obligatorio, relación de aspecto, duración y referencias. El resultado estructurado contiene concepto, gancho, composición, paleta, tipografía, escenas, copy, accesibilidad y comprobaciones.

### Co-workers creativos

El rol del co-worker modifica las instrucciones de trabajo:
- Social media / community manager: estrategia editorial, calendario, copy, medición y revisión de publicaciones.
- Diseño / dirección de arte: identidad, composición, piezas, miniaturas y consistencia visual.
- Otros roles pueden invocar estas habilidades sin cambiar de modelo ni duplicar el motor conversacional.

La selección de habilidad es determinista y ampliable. No otorga permisos para publicar, enviar mensajes o ejecutar cambios externos.

### Estrategia de redes sociales

El plan considera plataforma, público, objetivo y formato. Los objetivos incluyen alcance, tiempo de visualización, guardados, compartidos, comunidad, leads, conversiones y confianza.

El motor propone gancho, promesa de valor, estructura, orientación inicial de longitud (heurística, no regla oficial), copy, CTA, accesibilidad y medición. La duración se debe validar con retención y finalización de la audiencia real. La puntuación de resultados permite comparar publicaciones propias con señales disponibles: retención, finalización, compartidos, guardados, comentarios cualificados, acciones de perfil, conversiones y feedback negativo.

No replica ni afirma conocer pesos internos de los algoritmos de Instagram, TikTok o YouTube. Sus sistemas cambian y son parcialmente opacos. Para recomendaciones específicas de actualidad, debe conectarse una fuente de documentación oficial y datos recientes de la cuenta. Se ha añadido un catálogo inicial de referencias oficiales para Meta/Instagram, TikTok, YouTube y LinkedIn en `SocialPlatformReferenceCatalog`; no sustituye la consulta y actualización de esas páginas. Nunca se promete viralidad ni se fomenta spam, interacción artificial o engagement bait.

### Comprensión de imágenes y vídeos

Contratos:
- `MediaUnderstandingProvider`: análisis de imágenes y vídeos.
- `MediaFrame` y `VideoFrameSamplingPolicy`: cronología de fotogramas, con muestreo limitado.
- `MediaUnderstandingResult`: resumen, sujetos/objetos, texto visible, acciones, cronología e incertidumbres.

El análisis de vídeo debe indicar que se basa en fotogramas muestreados, salvo que un proveedor procese realmente el flujo completo. No se debe inventar audio si no se ha extraído ni analizado.

### Generación de imágenes y vídeos

Contratos:
- `ImageGenerationProvider`
- `VideoGenerationProvider`
- `MediaGenerationRequest` y `GeneratedMedia`

Los proveedores son intercambiables y pueden ser locales, remotos o aplicaciones externas. El runtime informa `REQUIRES_CONFIGURATION`, `REQUIRES_MODEL`, `UNSUPPORTED` o `AVAILABLE`. Si no hay proveedor disponible, ORION entrega brief, prompt o storyboard, pero no afirma haber generado el archivo.

### Privacidad y seguridad

- El usuario selecciona el archivo multimedia que quiere analizar.
- Las referencias no se suben a un servicio remoto sin una acción informada y un proveedor configurado.
- No se permite ejecutar código, publicar ni modificar cuentas sociales directamente desde un prompt generado.
- Publicación, borrado, mensajería y cambios externos exigen conector, permiso y confirmación contextual.
- Los resultados multimedia deben identificar proveedor/modelo y, cuando corresponda, disclosure o marca de agua.

## Estado real de implementación

Implementado en código:
- Nuevos dominios de conocimiento: diseño creativo, redes sociales e imagen/vídeo.
- Clasificación de habilidades creativas.
- Inyección de reglas de diseño/social/percepción/generación en el contexto del LLM.
- Perfil visual ORION Editorial.
- Motor de estrategia y comparación de métricas.
- Contratos de proveedores y orquestador multimedia con estados honestos.
- Muestreo temporal de vídeo limitado y validación de cronología.
- Pruebas unitarias de clasificación, estrategia, puntuación y límites de muestreo.
- Runtime del cerebro preparado para fuentes de conocimiento externas inyectables.

Pendiente para disponer de multimedia real:
1. Registrar y configurar un proveedor de visión capaz de interpretar imágenes y fotogramas de vídeo.
2. Implementar extracción segura de fotogramas y selección/importación de archivos desde Android.
3. Elegir proveedor de generación de imágenes y vídeos, local o remoto; los proveedores remotos necesitan credenciales y consentimiento.
4. Integrar métricas oficiales de cada red mediante conectores autorizados.
5. Conectar fuentes web oficiales/actualizadas para que el módulo social y el resto del cerebro investiguen en tiempo real.

El modelo local Qwen2.5 1.5B actual es de texto y no sustituye un modelo multimodal ni un generador de vídeo. La arquitectura ya permite añadir esos motores sin sustituir el cerebro ni romper el modo offline básico.
