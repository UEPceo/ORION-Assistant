package com.orion.voicelab.llm

enum class InferenceRoute { LOCAL, REMOTE }
enum class InferenceTaskClass { QUICK, STANDARD, CODE_OR_REASONING, DEEP_RESEARCH, LONG_MULTI_STEP, EXPERT_ANALYSIS }

data class InferenceRouteDecision(
    val route: InferenceRoute,
    val taskClass: InferenceTaskClass,
    val reason: String
)

/** Deterministic routing: ordinary turns stay on-device; remote is reserved for work beyond the local tier. */
object InferenceRoutingPolicy {
    fun decide(localProfileId: String, userText: String, remoteConfiguredAndConsented: Boolean): InferenceRouteDecision {
        val text = userText.trim().lowercase()
        val words = text.split(Regex("\\s+")).size
        val deepResearch = containsAny(text, "investigación profunda", "investiga a fondo", "estado del arte",
            "revisión bibliográfica", "fuentes primarias", "deep research", "research report")
        val longPlan = containsAny(text, "plan de varias semanas", "plan de varios meses", "plan de 30 días",
            "plan de 90 días", "planifica todas las fases", "plan multipaso largo", "plan de múltiples fases",
            "orquesta un proyecto", "desglosa el proyecto completo")
        val expert = containsAny(text, "análisis experto", "auditoría exhaustiva", "informe pericial",
            "análisis de nivel experto", "evaluación técnica integral", "due diligence")
        val heavyCode = containsAny(text, "refactorización completa", "auditoría completa del código",
            "diseña la arquitectura completa", "migración integral", "revisión de seguridad del código")
        val codeOrReasoning = containsAny(text, "programa", "programar", "código", "codigo", "depura",
            "debug", "arquitectura", "razonamiento", "demuestra", "resuelve paso a paso")
        val taskClass = when {
            deepResearch -> InferenceTaskClass.DEEP_RESEARCH
            longPlan || words > 450 -> InferenceTaskClass.LONG_MULTI_STEP
            expert || heavyCode -> InferenceTaskClass.EXPERT_ANALYSIS
            codeOrReasoning -> InferenceTaskClass.CODE_OR_REASONING
            words <= 45 -> InferenceTaskClass.QUICK
            else -> InferenceTaskClass.STANDARD
        }
        val localCanHandle = when (localProfileId) {
            "flagship", "powerful" -> taskClass in setOf(
                InferenceTaskClass.QUICK, InferenceTaskClass.STANDARD,
                InferenceTaskClass.CODE_OR_REASONING
            )
            "balanced" -> taskClass in setOf(
                InferenceTaskClass.QUICK, InferenceTaskClass.STANDARD,
                InferenceTaskClass.CODE_OR_REASONING
            )
            else -> taskClass in setOf(InferenceTaskClass.QUICK, InferenceTaskClass.STANDARD)
        }
        if (taskClass in setOf(InferenceTaskClass.DEEP_RESEARCH, InferenceTaskClass.LONG_MULTI_STEP, InferenceTaskClass.EXPERT_ANALYSIS)) {
            return if (remoteConfiguredAndConsented) InferenceRouteDecision(
                InferenceRoute.REMOTE, taskClass, "La tarea requiere investigación, planificación extensa o análisis experto."
            ) else InferenceRouteDecision(
                InferenceRoute.LOCAL, taskClass, "No hay motor remoto autorizado; se mantiene ejecución local con sus límites."
            )
        }
        if (!localCanHandle && remoteConfiguredAndConsented) return InferenceRouteDecision(
            InferenceRoute.REMOTE, taskClass, "El perfil local disponible no cubre bien esta clase de tarea."
        )
        return InferenceRouteDecision(
            InferenceRoute.LOCAL, taskClass, "La tarea se puede resolver con el perfil local; no se transmite contexto."
        )
    }

    private fun containsAny(text: String, vararg phrases: String): Boolean = phrases.any(text::contains)
}
