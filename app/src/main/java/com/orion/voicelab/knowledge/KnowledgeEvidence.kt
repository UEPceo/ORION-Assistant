package com.orion.voicelab.knowledge

/**
 * Evidence metadata used by ORION's retrieval layer.
 *
 * The model deliberately stores provenance and freshness separately from generated text.
 * A language model response is never itself evidence.
 */
enum class KnowledgeDomain {
    GENERAL,
    PROGRAMMING,
    COMPUTER_SCIENCE,
    MATHEMATICS,
    SCIENCE,
    ENGINEERING,
    COMMUNICATION,
    INTERNET,
    CYBERSECURITY,
    BUSINESS,
    FINANCE,
    LAW_AND_REGULATION,
    HEALTH_AND_SAFETY,
    FISHING,
    SURFCASTING,
    MARINE_WEATHER,
    PERSONAL_PRODUCTIVITY,
    CREATIVE_DESIGN,
    SOCIAL_MEDIA,
    IMAGE_VIDEO
}

enum class EvidenceAuthority {
    OFFICIAL_PRIMARY,
    PRIMARY_TECHNICAL,
    PEER_REVIEWED,
    REPUTABLE_SECONDARY,
    COMMUNITY,
    USER_SUPPLIED,
    UNKNOWN
}

enum class KnowledgeVerdict {
    SUPPORTED,
    NEEDS_CORROBORATION,
    PRIMARY_SOURCE_REQUIRED,
    STALE,
    CONFLICTING,
    NO_EVIDENCE,
    INVALID_TIMESTAMP,
    AGE_UNCONFIRMED
}

data class KnowledgeEvidence(
    val id: String,
    val title: String,
    val canonicalUrl: String,
    val publisherId: String,
    val domain: KnowledgeDomain,
    val authority: EvidenceAuthority,
    val retrievedAtMs: Long,
    val publishedAtMs: Long? = null,
    val jurisdiction: String? = null,
    val content: String,
    val contentHash: String? = null
)

data class KnowledgeVerification(
    val verdict: KnowledgeVerdict,
    val evidenceCount: Int,
    val independentPublisherCount: Int,
    val hasPrimaryEvidence: Boolean,
    val hasOfficialPrimaryEvidence: Boolean = false,
    val newestEvidenceAtMs: Long?,
    val reason: String
)

/**
 * Conservative evidence gate. It does not claim to prove truth; it decides whether
 * the available sources are sufficient to present a claim as verified.
 */
object KnowledgeVerifier {
    fun assess(
        evidence: List<KnowledgeEvidence>,
        domain: KnowledgeDomain,
        nowMs: Long,
        maxAgeMs: Long,
        conflictDetected: Boolean = false,
        requiresPublishedDate: Boolean = false
    ): KnowledgeVerification {
        val relevant = evidence.filter { it.domain == domain && it.canonicalUrl.isNotBlank() }
        val publishers = relevant.map { it.publisherId.trim().lowercase() }
            .filter(String::isNotBlank).toSet()
        val corroboratingPublishers = relevant.filter {
            it.authority in setOf(
                EvidenceAuthority.OFFICIAL_PRIMARY,
                EvidenceAuthority.PRIMARY_TECHNICAL,
                EvidenceAuthority.PEER_REVIEWED,
                EvidenceAuthority.REPUTABLE_SECONDARY
            )
        }.map { it.publisherId.trim().lowercase() }.filter(String::isNotBlank).toSet()
        val hasOfficialPrimary = relevant.any { it.authority == EvidenceAuthority.OFFICIAL_PRIMARY }
        val hasPrimary = hasOfficialPrimary || relevant.any { it.authority == EvidenceAuthority.PRIMARY_TECHNICAL }
        val newest = relevant.maxOfOrNull { it.publishedAtMs ?: it.retrievedAtMs }
        val base = KnowledgeVerification(
            verdict = KnowledgeVerdict.NO_EVIDENCE,
            evidenceCount = relevant.size,
            independentPublisherCount = publishers.size,
            hasPrimaryEvidence = hasPrimary,
            hasOfficialPrimaryEvidence = hasOfficialPrimary,
            newestEvidenceAtMs = newest,
            reason = ""
        )

        val verdict: KnowledgeVerdict
        val reason: String
        when {
            relevant.isEmpty() -> {
                verdict = KnowledgeVerdict.NO_EVIDENCE
                reason = "No se ha recuperado evidencia identificable para este ámbito."
            }
            relevant.any { it.retrievedAtMs <= 0L || it.retrievedAtMs > nowMs + 5L * 60L * 1000L } -> {
                verdict = KnowledgeVerdict.INVALID_TIMESTAMP
                reason = "Una o varias fuentes tienen una fecha de recuperación inválida o futura."
            }
            conflictDetected -> {
                verdict = KnowledgeVerdict.CONFLICTING
                reason = "Las fuentes recuperadas presentan información contradictoria."
            }
            maxAgeMs >= 0L && newest != null && nowMs - newest > maxAgeMs -> {
                verdict = KnowledgeVerdict.STALE
                reason = "La evidencia supera el periodo de vigencia configurado."
            }
            domain in setOf(
                KnowledgeDomain.LAW_AND_REGULATION,
                KnowledgeDomain.HEALTH_AND_SAFETY,
                KnowledgeDomain.MARINE_WEATHER,
                KnowledgeDomain.SOCIAL_MEDIA
            ) && !hasOfficialPrimary -> {
                verdict = KnowledgeVerdict.PRIMARY_SOURCE_REQUIRED
                reason = "Este ámbito exige una fuente oficial o primaria antes de afirmar que el dato está verificado."
            }
            requiresPublishedDate && relevant.any { it.publishedAtMs == null } -> {
                verdict = KnowledgeVerdict.AGE_UNCONFIRMED
                reason = "La página se ha consultado ahora, pero no aporta fecha de publicación o actualización verificable; no se puede afirmar que su contenido siga vigente."
            }
            corroboratingPublishers.size < 2 && !hasPrimary -> {
                verdict = KnowledgeVerdict.NEEDS_CORROBORATION
                reason = "No hay dos fuentes independientes con autoridad suficiente; los fragmentos de buscador y las fuentes desconocidas no cuentan como corroboración."
            }
            else -> {
                verdict = KnowledgeVerdict.SUPPORTED
                reason = "La evidencia cumple el umbral de fuentes configurado; no constituye una garantía absoluta de verdad."
            }
        }
        return base.copy(verdict = verdict, reason = reason)
    }
}
