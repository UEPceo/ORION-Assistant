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
        KnowledgeSourceSeed("github-docs", "GitHub Docs", "https://docs.github.com/", setOf(KnowledgeDomain.PROGRAMMING, KnowledgeDomain.COMPUTER_SCIENCE), EvidenceAuthority.PRIMARY_TECHNICAL, null, 30),
        KnowledgeSourceSeed("nist", "National Institute of Standards and Technology", "https://www.nist.gov/", setOf(KnowledgeDomain.COMPUTER_SCIENCE, KnowledgeDomain.ENGINEERING, KnowledgeDomain.SCIENCE), EvidenceAuthority.OFFICIAL_PRIMARY, "US", 90),
        KnowledgeSourceSeed("mit-ocw", "MIT OpenCourseWare", "https://ocw.mit.edu/", setOf(KnowledgeDomain.COMPUTER_SCIENCE, KnowledgeDomain.MATHEMATICS, KnowledgeDomain.ENGINEERING), EvidenceAuthority.PRIMARY_TECHNICAL, null, 180),
        KnowledgeSourceSeed("openstax", "OpenStax", "https://openstax.org/", setOf(KnowledgeDomain.MATHEMATICS, KnowledgeDomain.SCIENCE), EvidenceAuthority.PEER_REVIEWED, null, 180),
        KnowledgeSourceSeed("nasa-science", "NASA Science", "https://science.nasa.gov/", setOf(KnowledgeDomain.SCIENCE, KnowledgeDomain.GENERAL), EvidenceAuthority.OFFICIAL_PRIMARY, null, 90),
        KnowledgeSourceSeed("noaa", "NOAA Ocean Service", "https://oceanservice.noaa.gov/", setOf(KnowledgeDomain.SCIENCE, KnowledgeDomain.MARINE_WEATHER), EvidenceAuthority.OFFICIAL_PRIMARY, "US", 90),
        KnowledgeSourceSeed("nasa-systems", "NASA Systems Engineering", "https://www.nasa.gov/reference/systems-engineering-handbook/", setOf(KnowledgeDomain.ENGINEERING), EvidenceAuthority.OFFICIAL_PRIMARY, null, 180),
        KnowledgeSourceSeed("nist-si", "NIST SI Units", "https://www.nist.gov/pml/owm/metric-si/si-units", setOf(KnowledgeDomain.ENGINEERING, KnowledgeDomain.SCIENCE), EvidenceAuthority.OFFICIAL_PRIMARY, "US", 180),
        KnowledgeSourceSeed("cnmv", "Comisión Nacional del Mercado de Valores", "https://www.cnmv.es/", setOf(KnowledgeDomain.FINANCE), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 30),
        KnowledgeSourceSeed("banco-espana", "Banco de España", "https://www.bde.es/", setOf(KnowledgeDomain.FINANCE), EvidenceAuthority.OFFICIAL_PRIMARY, "ES", 30),
        KnowledgeSourceSeed("oecd-sme", "OCDE: pymes y emprendimiento", "https://www.oecd.org/en/topics/smes-and-entrepreneurship.html", setOf(KnowledgeDomain.BUSINESS), EvidenceAuthority.OFFICIAL_PRIMARY, "OECD", 90),
        KnowledgeSourceSeed("eu-sme", "Comisión Europea: pymes", "https://single-market-economy.ec.europa.eu/smes_en", setOf(KnowledgeDomain.BUSINESS, KnowledgeDomain.LAW_AND_REGULATION), EvidenceAuthority.OFFICIAL_PRIMARY, "EU", 30),
        KnowledgeSourceSeed("w3c-wai", "W3C Web Accessibility Initiative", "https://www.w3.org/WAI/", setOf(KnowledgeDomain.COMMUNICATION, KnowledgeDomain.INTERNET), EvidenceAuthority.PRIMARY_TECHNICAL, null, 90),
        KnowledgeSourceSeed("purdue-owl", "Purdue Online Writing Lab", "https://owl.purdue.edu/", setOf(KnowledgeDomain.COMMUNICATION), EvidenceAuthority.PRIMARY_TECHNICAL, null, 180),
        KnowledgeSourceSeed("creative-commons", "Creative Commons", "https://creativecommons.org/", setOf(KnowledgeDomain.CREATIVE_DESIGN, KnowledgeDomain.IMAGE_VIDEO), EvidenceAuthority.PRIMARY_TECHNICAL, null, 90),
        KnowledgeSourceSeed("adobe", "Adobe Help Center", "https://helpx.adobe.com/", setOf(KnowledgeDomain.CREATIVE_DESIGN, KnowledgeDomain.IMAGE_VIDEO), EvidenceAuthority.PRIMARY_TECHNICAL, null, 90),
        KnowledgeSourceSeed("unesco-il", "UNESCO: alfabetización informacional", "https://www.unesco.org/en/education/information-literacy", setOf(KnowledgeDomain.GENERAL, KnowledgeDomain.COMMUNICATION), EvidenceAuthority.OFFICIAL_PRIMARY, null, 180),
        KnowledgeSourceSeed("nasa-earth", "NASA Earth Science", "https://science.nasa.gov/earth/", setOf(KnowledgeDomain.GENERAL, KnowledgeDomain.SCIENCE), EvidenceAuthority.OFFICIAL_PRIMARY, null, 90)
    )
    fun forDomain(domain: KnowledgeDomain): List<KnowledgeSourceSeed> = seeds.filter { domain in it.domains }
}
