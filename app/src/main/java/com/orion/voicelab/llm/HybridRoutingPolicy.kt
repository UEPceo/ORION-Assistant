package com.orion.voicelab.llm

/** Deterministic complexity routing; privacy/consent is checked separately by the caller. */
object HybridRoutingPolicy {
    fun shouldUseRemote(mode: String, request: String): Boolean {
        if (mode == RemoteInferenceSettings.MODE_REMOTE_ONLY) return true
        if (mode != RemoteInferenceSettings.MODE_AUTO) return false
        val normalized = request.lowercase()
        val signals = listOf(
            "programa", "programar", "código", "codigo", "arquitectura", "auditoría", "auditoria",
            "analiza", "compara", "investiga", "planifica", "estrategia", "razona", "depura",
            "implementa", "diseña", "diseño", "matemática", "matematicas", "finanzas",
            "paso a paso", "varios pasos", "informe", "documentación", "documentacion"
        )
        return request.length >= 900 || signals.any(normalized::contains)
    }
}
