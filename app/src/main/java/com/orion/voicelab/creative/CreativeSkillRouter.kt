package com.orion.voicelab.creative

import java.util.Locale

enum class CreativeSkill { DESIGN, BRANDING, SOCIAL_STRATEGY, COPYWRITING, STORYBOARD, IMAGE_GENERATION, VIDEO_GENERATION, IMAGE_UNDERSTANDING, VIDEO_UNDERSTANDING, CONTENT_ANALYTICS, NONE }

object CreativeSkillRouter {
    fun route(text: String): CreativeSkill {
        val value = text.lowercase(Locale.ROOT)
        return when {
            any(value, "interpreta esta imagen", "analiza esta foto", "qué aparece en la imagen", "describe la imagen", "lee el texto de la foto") -> CreativeSkill.IMAGE_UNDERSTANDING
            any(value, "analiza este vídeo", "analiza este video", "resume el vídeo", "resume el video", "qué ocurre en el vídeo", "que ocurre en el video") -> CreativeSkill.VIDEO_UNDERSTANDING
            any(value, "genera un vídeo", "genera un video", "crea un vídeo", "crea un video", "anima esta imagen") -> CreativeSkill.VIDEO_GENERATION
            any(value, "genera una imagen", "crea una imagen", "dibuja", "ilustra", "renderiza") -> CreativeSkill.IMAGE_GENERATION
            any(value, "analiza las métricas", "rendimiento de publicaciones", "estadísticas de redes", "estadisticas de redes") -> CreativeSkill.CONTENT_ANALYTICS
            any(value, "storyboard", "guion visual", "secuencia de planos") -> CreativeSkill.STORYBOARD
            any(value, "instagram", "tiktok", "youtube", "redes sociales", "reel", "shorts", "publicación", "publicacion", "algoritmo") -> CreativeSkill.SOCIAL_STRATEGY
            any(value, "branding", "identidad visual", "marca", "logotipo", "logo") -> CreativeSkill.BRANDING
            any(value, "diseña", "diseño", "composición", "composicion", "cartel", "banner", "miniatura", "thumbnail", "creatividad") -> CreativeSkill.DESIGN
            any(value, "copy", "texto publicitario", "titular", "eslogan", "caption", "descripción") -> CreativeSkill.COPYWRITING
            else -> CreativeSkill.NONE
        }
    }

    fun coWorkerGuidance(role: String): String {
        val normalized = role.lowercase(Locale.ROOT)
        return when {
            any(normalized, "redes", "social", "community", "marketing", "contenido") ->
                "Actúa como estratega editorial y responsable de redes. Define objetivo, audiencia, formato, gancho, valor, CTA y métricas. Prioriza retención satisfactoria, compartidos, guardados, conversación útil y conversiones reales; adapta cada pieza a la plataforma y revisa sus guías actuales. No prometas viralidad ni inventes datos."
            any(normalized, "diseño", "diseñador", "creativo", "arte") ->
                "Actúa como director de arte. Aplica una identidad visual consistente, jerarquía, contraste, accesibilidad y formatos móviles. Presenta primero concepto y especificación; usa un proveedor multimedia solo si está disponible y autorizado."
            else -> "Usa las habilidades creativas de ORION cuando la tarea lo requiera y declara qué herramientas multimedia están realmente disponibles."
        }
    }

    private fun any(value: String, vararg terms: String): Boolean = terms.any(value::contains)
}
