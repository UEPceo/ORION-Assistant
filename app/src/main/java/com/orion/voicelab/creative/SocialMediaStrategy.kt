package com.orion.voicelab.creative

enum class SocialPlatform { INSTAGRAM, TIKTOK, YOUTUBE, FACEBOOK, LINKEDIN, X, GENERIC }
enum class SocialGoal { REACH, WATCH_TIME, SAVES, SHARES, COMMUNITY, LEADS, CONVERSIONS, TRUST }
enum class SocialFormat { SHORT_VIDEO, LONG_VIDEO, IMAGE, CAROUSEL, STORY, TEXT_POST, LIVE }

data class SocialContentBrief(
    val platform: SocialPlatform,
    val goal: SocialGoal,
    val audience: String,
    val format: SocialFormat,
    val topic: String,
    val desiredAction: String? = null
)

data class SocialPerformanceSignals(
    val audienceRetention: Double? = null,
    val completionRate: Double? = null,
    val meaningfulShares: Double? = null,
    val saves: Double? = null,
    val qualifiedComments: Double? = null,
    val profileActions: Double? = null,
    val conversionRate: Double? = null,
    val negativeFeedbackRate: Double? = null
)

data class SocialContentPlan(
    val openingHook: String,
    val valuePromise: String,
    val structure: List<String>,
    val recommendedLengthSeconds: Int?,
    val captionGuidance: String,
    val callToAction: String,
    val accessibility: List<String>,
    val measurementPlan: List<String>,
    val caveats: List<String>
)

/** Evidence-aware strategy; never claims access to proprietary ranking weights. */
object SocialMediaStrategyEngine {
    fun plan(brief: SocialContentBrief): SocialContentPlan {
        val platformLength = when (brief.platform) {
            SocialPlatform.TIKTOK -> if (brief.format == SocialFormat.SHORT_VIDEO) 20 else 45
            SocialPlatform.INSTAGRAM -> if (brief.format == SocialFormat.SHORT_VIDEO) 20 else 35
            SocialPlatform.YOUTUBE -> if (brief.format == SocialFormat.SHORT_VIDEO) 30 else null
            else -> null
        }
        val structure = when (brief.goal) {
            SocialGoal.REACH -> listOf("Gancho reconocible", "Contexto mínimo", "Idea visual compartible", "Cierre claro")
            SocialGoal.WATCH_TIME -> listOf("Pregunta o resultado inicial", "Información por etapas", "Cambios visuales con sentido", "Resolver la promesa sin alargar")
            SocialGoal.SAVES -> listOf("Problema concreto", "Pasos o datos ordenados", "Resumen reutilizable", "Invitación a guardar")
            SocialGoal.SHARES -> listOf("Situación identificable", "Valor claro y verificable", "Conclusión útil para otra persona")
            SocialGoal.COMMUNITY -> listOf("Experiencia compartida", "Pregunta genuina y específica", "Continuidad en comentarios")
            SocialGoal.LEADS, SocialGoal.CONVERSIONS -> listOf("Necesidad concreta", "Demostración honesta", "Beneficio y limitaciones", "Siguiente paso claro")
            SocialGoal.TRUST -> listOf("Contexto y fuente", "Explicación transparente", "Límites y excepciones", "Invitación a corregir con evidencia")
        }
        val cta = brief.desiredAction?.takeIf { it.isNotBlank() } ?: when (brief.goal) {
            SocialGoal.REACH -> "Compártelo con alguien a quien le resulte útil."
            SocialGoal.WATCH_TIME -> "Quédate para ver la conclusión."
            SocialGoal.SAVES -> "Guárdalo para consultarlo."
            SocialGoal.SHARES -> "Envíalo a quien pueda aprovecharlo."
            SocialGoal.COMMUNITY -> "Cuéntanos tu experiencia concreta."
            SocialGoal.LEADS, SocialGoal.CONVERSIONS -> "Consulta los detalles antes de decidir."
            SocialGoal.TRUST -> "Contrasta la fuente y señala cualquier dato que haya cambiado."
        }
        return SocialContentPlan(
            openingHook = "Empieza con el problema, la pregunta o el resultado que reconoce la audiencia.",
            valuePromise = "Resuelve una sola necesidad del tema solicitado; no prometas resultados no demostrables.",
            structure = structure,
            recommendedLengthSeconds = platformLength,
            captionGuidance = captionGuidance(brief.platform),
            callToAction = cta,
            accessibility = listOf("Subtítulos revisados cuando proceda", "Texto grande y con contraste", "No depender solo del color", "Audio comprensible y sin música que tape la voz"),
            measurementPlan = listOf("Retención por tramos y finalización", "Compartidos y guardados por alcance", "Comentarios relevantes", "Visitas al perfil y acciones cualificadas", "Conversiones con medición consentida"),
            caveats = listOf("Las plataformas cambian sus sistemas; revisar documentación y métricas recientes.", "Los pesos de ranking no son públicos ni existe una fórmula universal de viralidad.", "Evitar compra de interacción, spam, engagement bait y afirmaciones engañosas.")
        )
    }

    /** Normalized comparison of a creator's own posts, not a platform ranking formula. */
    fun outcomeScore(signals: SocialPerformanceSignals): Double? {
        val values = listOf(
            signals.audienceRetention to 0.22,
            signals.completionRate to 0.16,
            signals.meaningfulShares to 0.16,
            signals.saves to 0.14,
            signals.qualifiedComments to 0.10,
            signals.profileActions to 0.08,
            signals.conversionRate to 0.10,
            signals.negativeFeedbackRate?.let { 1.0 - it.coerceIn(0.0, 1.0) } to 0.04
        )
        val available = values.filter { it.first != null }
        if (available.isEmpty()) return null
        val weight = available.sumOf { it.second }
        return available.sumOf { (value, factor) -> value!!.coerceIn(0.0, 1.0) * factor } / weight
    }

    private fun captionGuidance(platform: SocialPlatform): String = when (platform) {
        SocialPlatform.INSTAGRAM -> "Primera línea clara; palabras clave naturales; etiquetas específicas solo si aportan contexto."
        SocialPlatform.TIKTOK -> "Texto breve que complemente el vídeo; tema reconocible y lenguaje natural."
        SocialPlatform.YOUTUBE -> "Título fiel a la promesa; descripción útil y capítulos cuando proceda."
        SocialPlatform.LINKEDIN -> "Contexto profesional, idea concreta y conversación sustantiva."
        SocialPlatform.X -> "Mensaje conciso, una idea principal y contexto cuando haga falta."
        SocialPlatform.FACEBOOK -> "Contexto comprensible y motivo real para compartir o comentar."
        SocialPlatform.GENERIC -> "Adaptar el texto al público y a las guías vigentes de la plataforma."
    }
}
