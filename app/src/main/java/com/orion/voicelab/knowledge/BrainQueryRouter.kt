package com.orion.voicelab.knowledge

import java.util.Locale

/** Deterministic first-pass routing. A later semantic router can refine, never erase, safety flags. */
object BrainQueryRouter {
    fun route(text: String, jurisdiction: String? = null): BrainQuery {
        val normalized = text.trim().lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
        val domain = when {
            containsAny(normalized, "normativa", "talla mínima", "veda", "licencia", "ley", "reglamento", "decreto", "real decreto", "boe") ->
                KnowledgeDomain.LAW_AND_REGULATION
            containsAny(normalized, "salud", "médico", "medicina", "síntoma", "seguridad", "emergencia", "112") ->
                KnowledgeDomain.HEALTH_AND_SAFETY
            containsAny(normalized, "surfcasting", "surf casting", "aparejo", "cebo", "lubina", "sargo") ->
                KnowledgeDomain.SURFCASTING
            containsAny(normalized, "pesca", "pesca recreativa", "normativa pesquera", "licencia de pesca", "talla mínima", "pescarec", "pesca-rec") ->
                KnowledgeDomain.FISHING
            containsAny(normalized, "meteorología", "tiempo", "viento", "oleaje", "marejada", "corriente", "temporal", "mar de fondo", "marea") ->
                KnowledgeDomain.MARINE_WEATHER
            containsAny(normalized, "ciberseguridad", "malware", "phishing", "vulnerabilidad", "ransomware", "contraseña", "contraseñas", "autenticación", "doble factor", "mfa", "owasp", "incibe", "clave de acceso", "credenciales") ->
                KnowledgeDomain.CYBERSECURITY
            containsAny(normalized, "matemática", "matemáticas", "álgebra", "calculo", "cálculo", "derivada", "integral", "ecuación", "ecuaciones", "geometría", "estadística", "probabilidad", "teorema") ->
                KnowledgeDomain.MATHEMATICS
            containsAny(normalized, "ciencia", "científico", "científica", "física", "química", "biología", "astronomía", "genética", "ecología", "hipótesis", "experimento", "investigación científica") ->
                KnowledgeDomain.SCIENCE
            containsAny(normalized, "ingeniería", "ingeniero", "ingeniera", "mecánica", "electrónica", "termodinámica", "sistemas de control", "metrología", "materiales", "diseño industrial") ->
                KnowledgeDomain.ENGINEERING
            containsAny(normalized, "programar", "programación", "kotlin", "python", "java", "código", "compilar", "api", "github", "android", "coroutine", "coroutines", "activity", "jetpack compose") ->
                KnowledgeDomain.PROGRAMMING
            containsAny(normalized, "internet", "web", "navegador", "http", "dns", "red") ->
                KnowledgeDomain.INTERNET
            containsAny(normalized, "comunicación", "comunicacion", "redacción", "redaccion", "escritura", "oratoria", "argumentación", "argumentacion", "presentación", "presentacion", "retórica", "retorica", "escucha activa", "storytelling", "narrativa") ->
                KnowledgeDomain.COMMUNICATION
            containsAny(normalized, "empresa", "negocio", "ventas", "marketing", "cliente", "mercado", "emprendimiento", "modelo de negocio", "plan de negocio", "pyme", "productividad empresarial") ->
                KnowledgeDomain.BUSINESS
            containsAny(normalized, "finanzas", "inversión", "impuestos", "presupuesto", "dinero", "ahorro", "interés compuesto", "diversificación", "hipoteca", "acciones", "bonos", "crédito", "tae", "inflación") ->
                KnowledgeDomain.FINANCE
            containsAny(normalized, "instagram", "tiktok", "youtube", "redes sociales", "reel", "shorts", "alcance", "retención", "engagement", "algoritmo", "publicación", "community manager") ->
                KnowledgeDomain.SOCIAL_MEDIA
            containsAny(normalized, "diseño", "diseñar", "identidad visual", "branding", "tipografía", "composición", "paleta", "creatividad", "cartel", "banner", "miniatura", "thumbnail") ->
                KnowledgeDomain.CREATIVE_DESIGN
            containsAny(normalized, "imagen", "fotografía", "foto", "vídeo", "video", "clip", "fotograma", "multimedia", "generar imagen", "generar vídeo") ->
                KnowledgeDomain.IMAGE_VIDEO
            containsAny(normalized, "agenda", "calendario", "tarea", "recordatorio", "productividad") ->
                KnowledgeDomain.PERSONAL_PRODUCTIVITY
            containsAny(normalized, "informática", "ordenador", "computadora", "hardware", "software", "sistema operativo", "algoritmo", "estructura de datos", "computación", "sistemas informáticos") ->
                KnowledgeDomain.COMPUTER_SCIENCE
            else -> KnowledgeDomain.GENERAL
        }
        val primary = domain in setOf(
            KnowledgeDomain.LAW_AND_REGULATION,
            KnowledgeDomain.HEALTH_AND_SAFETY,
            KnowledgeDomain.MARINE_WEATHER,
            KnowledgeDomain.SOCIAL_MEDIA
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
