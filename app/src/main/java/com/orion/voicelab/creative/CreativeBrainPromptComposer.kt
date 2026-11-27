package com.orion.voicelab.creative

/** Adds domain-specific operating rules to the existing LLM context without replacing it. */
object CreativeBrainPromptComposer {
 fun compose(userText:String,skill:CreativeSkill,activeRole:String?=null,mediaStatus:MediaCapabilityStatus?=null):String {
  if(skill==CreativeSkill.NONE)return ""
  val roleGuidance=activeRole?.takeIf{it.isNotBlank()}?.let{CreativeSkillRouter.coWorkerGuidance(it)}.orEmpty()
  val rules=when(skill) {
   CreativeSkill.DESIGN,CreativeSkill.BRANDING -> """
    HABILIDAD: DIRECCIÓN DE ARTE Y DISEÑO.
    Trabaja con concepto, audiencia, objetivo, jerarquía, composición, contraste, tipografía, accesibilidad y formato final.
    Mantén una identidad reconocible; no copies identidades de terceros. Si no se especifica otra guía, usa ORION Editorial: navy #080D1A, cian #16C7E8 y blanco.
    Entrega un brief ejecutable y especificaciones. No afirmes que has renderizado un archivo si no hay proveedor conectado.
   """.trimIndent()
   CreativeSkill.SOCIAL_STRATEGY,CreativeSkill.CONTENT_ANALYTICS -> """
    HABILIDAD: ESTRATEGIA DE REDES SOCIALES.
    Empieza por plataforma, público, objetivo y formato. Diseña gancho fiel, promesa concreta, estructura de retención, CTA natural y medición.
    Optimiza satisfacción y valor: retención, finalización, compartidos, guardados, comentarios relevantes, visitas cualificadas y conversiones.
    Los algoritmos son dinámicos y parcialmente opacos. No inventes pesos ni garantías de viralidad. Comprueba guías oficiales vigentes. Evita spam y desinformación.
   """.trimIndent()
   CreativeSkill.COPYWRITING -> """
    HABILIDAD: REDACCIÓN CREATIVA.
    Ajusta voz y longitud a plataforma y audiencia. Escribe con claridad, gancho honesto, una idea principal y CTA proporcionada. No inventes testimonios ni cifras.
   """.trimIndent()
   CreativeSkill.STORYBOARD -> """
    HABILIDAD: NARRATIVA AUDIOVISUAL.
    Entrega secuencia temporal de planos, acción, encuadre, movimiento de cámara, sonido, transición y propósito narrativo. Comprueba continuidad y viabilidad.
   """.trimIndent()
   CreativeSkill.IMAGE_GENERATION,CreativeSkill.VIDEO_GENERATION -> """
    HABILIDAD: PRODUCCIÓN MULTIMEDIA.
    Convierte la petición en prompt preciso: sujeto, entorno, estilo, encuadre, luz, movimiento, duración, relación de aspecto y restricciones.
    Si no existe proveedor disponible, entrega prompt/storyboard e informa que el archivo no se ha generado. Nunca simules un archivo terminado.
   """.trimIndent()
   CreativeSkill.IMAGE_UNDERSTANDING,CreativeSkill.VIDEO_UNDERSTANDING -> """
    HABILIDAD: PERCEPCIÓN VISUAL.
    Describe solo elementos visibles o audibles en el material realmente recibido. Distingue observación de inferencia, expresa incertidumbre y no inventes audio.
    Para vídeo, indica si el análisis se basó en fotogramas muestreados y no en todos los instantes.
   """.trimIndent()
   else -> ""
  }
  return buildString {
   append("MÓDULO CREATIVO ORION\n").append(rules)
   if(roleGuidance.isNotBlank())append("\nPERFIL DEL CO-WORKER:\n").append(roleGuidance)
   if(mediaStatus!=null) {
    append("\nESTADO REAL DEL PROVEEDOR MULTIMEDIA: ").append(mediaStatus.name).append(".")
    when(mediaStatus) {
     MediaCapabilityStatus.REQUIRES_USER_CONSENT -> append(" Hay proveedor remoto, pero no envíes contenido hasta obtener autorización expresa para esta operación.")
     MediaCapabilityStatus.REQUIRES_NETWORK -> append(" El proveedor remoto necesita conexión; no envíes nada si no está disponible.")
     MediaCapabilityStatus.REQUIRES_MODEL -> append(" Falta preparar el modelo local; no afirmes haber procesado el archivo.")
     MediaCapabilityStatus.REQUIRES_CONFIGURATION -> append(" No hay motor configurado; ofrece brief o prompt, no simules resultados.")
     MediaCapabilityStatus.UNSUPPORTED -> append(" El formato no está admitido; explica la limitación.")
     MediaCapabilityStatus.AVAILABLE -> Unit
    }
   }
  }
 }
}
