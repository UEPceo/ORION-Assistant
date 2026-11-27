package com.orion.voicelab.knowledge

/** Discovery seeds only. A URL is not evidence until fetched, dated and checked. */
data class KnowledgeSourceSeed(val id: String, val name: String, val url: String, val domains: Set<KnowledgeDomain>, val authority: EvidenceAuthority, val jurisdiction: String? = null, val reviewIntervalDays: Int = 90, val licenseNote: String = "Verificar licencia y reutilización antes de empaquetar contenido.")

object KnowledgeSourceCatalog {
    val seeds = listOf(
        KnowledgeSourceSeed("boe", "Boletín Oficial del Estado", "https://www.boe.es/", setOf(KnowledgeDomain.LAW_AND_REGULATION), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 7),
        KnowledgeSourceSeed("eur-lex", "EUR-Lex", "https://eur-lex.europa.eu/", setOf(KnowledgeDomain.LAW_AND_REGULATION), EvidenceAuthority.OFFICIAL_PRIMARY, "EU", 7),
        KnowledgeSourceSeed("aemet", "AEMET", "https://www.aemet.es/", setOf(KnowledgeDomain.MARINE_WEATHER, KnowledgeDomain.HEALTH_AND_SAFETY), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 1),
        KnowledgeSourceSeed("miteco", "MITECO", "https://www.miteco.gob.es/", setOf(KnowledgeDomain.HEALTH_AND_SAFETY, KnowledgeDomain.GENERAL), EvidenceAuthority.OFFICIAL_PRIMARY, "ES"),
        KnowledgeSourceSeed("sanidad", "Ministerio de Sanidad", "https://www.sanidad.gob.es/", setOf(KnowledgeDomain.HEALTH_AND_SAFETY), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 30),
        KnowledgeSourceSeed("incibe", "INCIBE", "https://www.incibe.es/", setOf(KnowledgeDomain.CYBERSECURITY), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 30),
        KnowledgeSourceSeed("aepd", "AEPD", "https://www.aepd.es/", setOf(KnowledgeDomain.LAW_AND_REGULATION, KnowledgeDomain.CYBERSECURITY), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 30),
        KnowledgeSourceSeed("dgt", "DGT", "https://www.dgt.es/", setOf(KnowledgeDomain.HEALTH_AND_SAFETY, KnowledgeDomain.LAW_AND_REGULATION), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 7),
        KnowledgeSourceSeed("junta-andalucia", "Junta de Andalucía", "https://www.juntadeandalucia.es/", setOf(KnowledgeDomain.LAW_AND_REGULATION, KnowledgeDomain.SURFCASTING, KnowledgeDomain.FISHING), EvidenceAuthority.OFFICIAL_PRIMARY, "ES-AN", 14),
        KnowledgeSourceSeed("mapa", "Ministerio de Agricultura, Pesca y Alimentación", "https://www.mapa.gob.es/", setOf(KnowledgeDomain.FISHING, KnowledgeDomain.LAW_AND_REGULATION), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 30),
        KnowledgeSourceSeed("android", "Android Developers", "https://developer.android.com/", setOf(KnowledgeDomain.PROGRAMMING, KnowledgeDomain.COMPUTER_SCIENCE), EvidenceAuthority.PRIMARY_TECHNICAL, null, 30),
        KnowledgeSourceSeed("kotlin", "Kotlin Documentation", "https://kotlinlang.org/docs/", setOf(KnowledgeDomain.PROGRAMMING), EvidenceAuthority.PRIMARY_TECHNICAL, null, 30),
        KnowledgeSourceSeed("python", "Python Documentation", "https://docs.python.org/3/", setOf(KnowledgeDomain.PROGRAMMING, KnowledgeDomain.COMPUTER_SCIENCE), EvidenceAuthority.PRIMARY_TECHNICAL, null, 30),
        KnowledgeSourceSeed("owasp", "OWASP", "https://owasp.org/", setOf(KnowledgeDomain.CYBERSECURITY), EvidenceAuthority.PRIMARY_TECHNICAL, null, 30),
        KnowledgeSourceSeed("mdn", "MDN Web Docs", "https://developer.mozilla.org/", setOf(KnowledgeDomain.INTERNET, KnowledgeDomain.PROGRAMMING), EvidenceAuthority.PRIMARY_TECHNICAL, null, 60),
        KnowledgeSourceSeed("github-docs", "GitHub Docs", "https://docs.github.com/", setOf(KnowledgeDomain.PROGRAMMING, KnowledgeDomain.COMPUTER_SCIENCE), EvidenceAuthority.PRIMARY_TECHNICAL, null, 30)
    )
    fun forDomain(domain: KnowledgeDomain): List<KnowledgeSourceSeed> = seeds.filter { domain in it.domains }
}
