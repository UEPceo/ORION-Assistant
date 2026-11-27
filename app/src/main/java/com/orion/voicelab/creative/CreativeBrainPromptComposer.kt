package com.orion.voicelab.creative

/** Adds domain-specific operating rules to the existing LLM context without replacing it. */
object CreativeBrainPromptComposer {
    fun compose(userText: String, skill: CreativeSkill, activeRole: String? = null, mediaStatus: MediaCapabilityStatus? = null): String {
        if (skill == CreativeSkill.NONE) return ""
        val roleGuidance = activeRole?.takeIf { it.isNotBlank() }?.let { CreativeSkillRouter.coWorkerGuidance(it) }.orEmpty()
        val rules = when (skill) {
            CreativeSkill.DESIGN, CreativeSkill.BRANDING -> """
                HABILIDAD: DIRECCIÓN DE ARTE Y DISEÑO.
                Trabaja con concepto, audiencia, objetivo, jerarquía, composición, contraste, tipografía, accesibilidad y formato final.
                Mantén una identidad reconocible entre piezas; no copies identidades de terceros. Si no se especifica otra guía, usa ORION Editorial: navy #080D1A, cian #16C7E8 y blanco; estilo tecnológico-editorial limpio.
                Entrega un brief ejecutable, especificaciones y variantes razonadas. No afirmes que has renderizado un archivo si no hay proveedor multimedia conectado.
            """.trimIndent()
            CreativeSkill.SOCIAL_STRATEGY, CreativeSkill.CONTENT_ANALYTICS -> """
                HABILIDAD: ESTRATEGIA DE REDES SOCIALES.
                Empieza por plataforma, público, objetivo y formato. Diseña un gancho fiel al contenido, promesa concreta, estructura de retención, CTA natural y plan de medición.
                Optimiza para satisfacción y valor: retención, finalización, compartidos, guardados, comentarios relevantes, visitas cualificadas y conversiones. No confundas métricas de vanidad con resultados.
                Los algoritmos son dinámicos y parcialmente opacos. No inventes pesos, trucos secretos ni garantías de viralidad. Comprueba las guías oficiales vigentes antes de recomendar tácticas específicas. Evita spam, engagement bait, desinformación y contenido repetitivo.
            """.trimIndent()
            CreativeSkill.COPYWRITING -> """
                HABILIDAD: REDACCIÓN CREATIVA.
                Ajusta voz, longitud y vocabulario a la plataforma y audiencia. Escribe con claridad, gancho honesto, una idea principal y CTA proporcionada. No inventes testimonios, cifras ni resultados.
            """.trimIndent()
            CreativeSkill.STORYBOARD -> """
                HABILIDAD: NARRATIVA AUDIOVISUAL.
                Entrega secuencia temporal de planos, acción, encuadre, movimiento de cámara, sonido, transición y propósito narrativo. Comprueba continuidad y viabilidad de producción.
            """.trimIndent()
            CreativeSkill.IMAGE_GENERATION, CreativeSkill.VIDEO_GENERATION -> """
                HABILIDAD: PRODUCCIÓN MULTIMEDIA.
                Convierte la petición en un prompt de producción preciso: sujeto, entorno, estilo, encuadre, luz, movimiento, duración, relación de aspecto y restricciones. Respeta la identidad visual y las referencias autorizadas.
                Si no existe un proveedor de generación disponible, entrega el prompt y storyboard, e informa que el archivo no se ha generado. Nunca simules una imagen o vídeo terminado.
            """.trimIndent()
            CreativeSkill.IMAGE_UNDERSTANDING, CreativeSkill.VIDEO_UNDERSTANDING -> """
                HABILIDAD: PERCEPCIÓN VISUAL.
                Describe solo elementos visibles o audibles en el material realmente recibido. Distingue observación de inferencia, señala texto legible, objetos, acciones y secuencia temporal; expresa incertidumbre ante desenfoque, oclusión o falta de fotogramas. No inventes audio ni eventos fuera del material.
                Para vídeo, resume la cronología y los cambios entre escenas; indica si el análisis se basó en fotogramas muestreados y no en todos los instantes.
            """.trimIndent()
            else -> ""
        }
        return buildString {
            append("MÓDULO CREATIVO ORION\n")
            append(rules)
            if (roleGuidance.isNotBlank()) append("\nPERFIL DEL CO-WORKER:\n").append(roleGuidance)
            if (mediaStatus != null) {
                append("\nESTADO REAL DEL PROVEEDOR MULTIMEDIA: ").append(mediaStatus.name).append(".")
                if (mediaStatus != MediaCapabilityStatus.AVAILABLE) {
                    append(" No afirmes que has analizado o generado el archivo. Ofrece un brief o indica que falta configurar el motor.")
                }
            }
        }
    }
}
