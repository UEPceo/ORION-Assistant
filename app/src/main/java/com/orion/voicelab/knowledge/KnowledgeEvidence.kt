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
    INTERNET,
    CYBERSECURITY,
    BUSINESS,
    FINANCE,
    LAW_AND_REGULATION,
    HEALTH_AND_SAFETY,
    FISHING,
    SURFCASTING,
    MARINE_WEATHER,
    PERSONAL_PRODUCTIVITY
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
    NO_EVIDENCE
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
        conflictDetected: Boolean = false
    ): KnowledgeVerification {
        val relevant = evidence.filter { it.domain == domain && it.canonicalUrl.isNotBlank() }
        val publishers = relevant.map { it.publisherId.trim().lowercase() }
            .filter(String::isNotBlank).toSet()
        val hasPrimary = relevant.any {
            it.authority == EvidenceAuthority.OFFICIAL_PRIMARY ||
                it.authority == EvidenceAuthority.PRIMARY_TECHNICAL
        }
        val newest = relevant.maxOfOrNull { it.retrievedAtMs }
        val base = KnowledgeVerification(
            verdict = KnowledgeVerdict.NO_EVIDENCE,
            evidenceCount = relevant.size,
            independentPublisherCount = publishers.size,
            hasPrimaryEvidence = hasPrimary,
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
                KnowledgeDomain.MARINE_WEATHER
            ) && !hasPrimary -> {
                verdict = KnowledgeVerdict.PRIMARY_SOURCE_REQUIRED
                reason = "Este ámbito exige una fuente oficial o primaria antes de afirmar que el dato está verificado."
            }
            publishers.size < 2 && !hasPrimary -> {
                verdict = KnowledgeVerdict.NEEDS_CORROBORATION
                reason = "Solo hay una fuente independiente y no es primaria."
            }
            else -> {
                verdict = KnowledgeVerdict.SUPPORTED
                reason = "La evidencia cumple el umbral de fuentes configurado; no constituye una garantía absoluta de verdad."
            }
        }
        return base.copy(verdict = verdict, reason = reason)
    }
}
