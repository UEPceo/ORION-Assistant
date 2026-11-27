package com.orion.voicelab.knowledge

import java.util.Locale

/** Deterministic first-pass routing. A later semantic router can refine, never erase, safety flags. */
object BrainQueryRouter {
    fun route(text: String, jurisdiction: String? = null): BrainQuery {
        val normalized = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        val domain = when {
            containsAny(normalized, "surfcasting", "surf casting", "playa de pesca", "aparejo", "cebo", "captura", "marea", "oleaje", "lubina", "sargo", "pesca") ->
                KnowledgeDomain.SURFCASTING
            containsAny(normalized, "normativa", "talla mínima", "veda", "licencia", "ley", "reglamento", "boe") ->
                KnowledgeDomain.LAW_AND_REGULATION
            containsAny(normalized, "meteorología", "tiempo", "viento", "oleaje", "marejada", "corriente", "temporal", "mar de fondo") ->
                KnowledgeDomain.MARINE_WEATHER
            containsAny(normalized, "salud", "médico", "medicina", "síntoma", "seguridad", "emergencia") ->
                KnowledgeDomain.HEALTH_AND_SAFETY
            containsAny(normalized, "ciberseguridad", "malware", "phishing", "vulnerabilidad", "ransomware") ->
                KnowledgeDomain.CYBERSECURITY
            containsAny(normalized, "programar", "programación", "kotlin", "python", "java", "código", "compilar", "api", "github") ->
                KnowledgeDomain.PROGRAMMING
            containsAny(normalized, "internet", "web", "navegador", "http", "dns", "red") ->
                KnowledgeDomain.INTERNET
            containsAny(normalized, "empresa", "negocio", "ventas", "marketing", "cliente", "mercado") ->
                KnowledgeDomain.BUSINESS
            containsAny(normalized, "finanzas", "inversión", "impuestos", "presupuesto", "dinero") ->
                KnowledgeDomain.FINANCE
            containsAny(normalized, "agenda", "calendario", "tarea", "recordatorio", "productividad") ->
                KnowledgeDomain.PERSONAL_PRODUCTIVITY
            containsAny(normalized, "informática", "ordenador", "computadora", "hardware", "software", "sistema operativo") ->
                KnowledgeDomain.COMPUTER_SCIENCE
            else -> KnowledgeDomain.GENERAL
        }
        val primary = domain in setOf(
            KnowledgeDomain.LAW_AND_REGULATION,
            KnowledgeDomain.HEALTH_AND_SAFETY,
            KnowledgeDomain.MARINE_WEATHER
        )
        val freshness = primary || containsAny(
            normalized, "hoy", "ahora", "actual", "último", "vigente", "mañana", "esta semana", "precio"
        )
        return BrainQuery(
            rawText = text,
            normalizedText = normalized,
            domain = domain,
            requiresFreshEvidence = freshness,
            requiresPrimaryEvidence = primary,
            jurisdiction = jurisdiction
        )
    }

    private fun containsAny(text: String, vararg terms: String): Boolean =
        terms.any(text::contains)
}
