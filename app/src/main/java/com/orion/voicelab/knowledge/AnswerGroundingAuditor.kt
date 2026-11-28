package com.orion.voicelab.knowledge

/** Claim-level audit. The semantic judge is untrusted and constrained by deterministic policy. */
enum class ClaimSupportStatus { SUPPORTED, CONTRADICTED, INSUFFICIENT, UNVERIFIABLE }
enum class AnswerGroundingStatus { VERIFIED, PARTIALLY_SUPPORTED, ABSTAIN }

data class AtomicAnswerClaim(
    val id: String,
    val text: String,
    val citedEvidenceIds: Set<String>,
    val isFactual: Boolean = true
)

data class SemanticJudgeAssessment(
    val status: ClaimSupportStatus,
    val confidence: Double,
    val supportingEvidenceIds: Set<String> = emptySet(),
    val contradictingEvidenceIds: Set<String> = emptySet(),
    val explanation: String = ""
)

fun interface SemanticEntailmentJudge {
    suspend fun assess(claim: AtomicAnswerClaim, evidence: List<KnowledgeEvidence>): SemanticJudgeAssessment
}

data class ClaimGroundingResult(
    val claimId: String,
    val status: ClaimSupportStatus,
    val confidence: Double,
    val supportingEvidenceIds: Set<String>,
    val contradictingEvidenceIds: Set<String>,
    val explanation: String
)

data class AnswerGroundingReport(
    val status: AnswerGroundingStatus,
    val claims: List<ClaimGroundingResult>,
    val unsupportedCitationIds: Set<String>,
    val uncitedFactualClaimIds: Set<String>,
    val requiresAbstention: Boolean
) {
    val factualClaimCount: Int get() = claims.size
    val supportedClaimCount: Int get() = claims.count { it.status == ClaimSupportStatus.SUPPORTED }
}

/**
 * Evaluates atomic claims individually. It never lets a judge cite evidence that was not
 * supplied to it, and never upgrades a low-confidence or source-free judgement to supported.
 */
class AnswerGroundingAuditor(
    private val judge: SemanticEntailmentJudge,
    private val minimumConfidence: Double = 0.82
) {
    init { require(minimumConfidence in 0.0..1.0) }

    suspend fun audit(
        claims: List<AtomicAnswerClaim>,
        evidence: List<KnowledgeEvidence>
    ): AnswerGroundingReport {
        val evidenceById = evidence.associateBy { it.id }
        val claimResults = mutableListOf<ClaimGroundingResult>()
        val unsupportedCitations = linkedSetOf<String>()
        val uncited = linkedSetOf<String>()

        for (claim in claims) {
            if (claim.id.isBlank() || claim.text.isBlank()) {
                claimResults += ClaimGroundingResult(
                    claim.id, ClaimSupportStatus.UNVERIFIABLE, 0.0, emptySet(), emptySet(),
                    "La afirmación está vacía o no tiene identificador."
                )
                continue
            }
            if (!claim.isFactual) continue
            unsupportedCitations += claim.citedEvidenceIds - evidenceById.keys
            if (claim.citedEvidenceIds.isEmpty()) uncited += claim.id
            val citedEvidence = claim.citedEvidenceIds.mapNotNull(evidenceById::get)
            if (citedEvidence.isEmpty()) {
                claimResults += ClaimGroundingResult(
                    claim.id, ClaimSupportStatus.INSUFFICIENT, 0.0, emptySet(), emptySet(),
                    "La afirmación factual no tiene evidencia citada disponible."
                )
                continue
            }

            val assessment = try {
                judge.assess(claim, citedEvidence)
            } catch (error: Exception) {
                claimResults += ClaimGroundingResult(
                    claim.id, ClaimSupportStatus.UNVERIFIABLE, 0.0, emptySet(), emptySet(),
                    "No se pudo completar la evaluación semántica (${error::class.java.simpleName})."
                )
                continue
            }

            val suppliedIds = citedEvidence.map { it.id }.toSet()
            val validSupport = assessment.supportingEvidenceIds.intersect(suppliedIds)
            val validContradictions = assessment.contradictingEvidenceIds.intersect(suppliedIds)
            val invalidJudgeIds = (assessment.supportingEvidenceIds + assessment.contradictingEvidenceIds) - suppliedIds
            unsupportedCitations += invalidJudgeIds
            val confidence = assessment.confidence.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
            val status = when {
                validContradictions.isNotEmpty() || assessment.status == ClaimSupportStatus.CONTRADICTED ->
                    ClaimSupportStatus.CONTRADICTED
                assessment.status == ClaimSupportStatus.SUPPORTED &&
                    confidence >= minimumConfidence && validSupport.isNotEmpty() ->
                    ClaimSupportStatus.SUPPORTED
                assessment.status == ClaimSupportStatus.UNVERIFIABLE ->
                    ClaimSupportStatus.UNVERIFIABLE
                else -> ClaimSupportStatus.INSUFFICIENT
            }
            claimResults += ClaimGroundingResult(
                claim.id, status, confidence, validSupport, validContradictions,
                assessment.explanation.take(500)
            )
        }

        val factualCount = claimResults.size
        val supportedCount = claimResults.count { it.status == ClaimSupportStatus.SUPPORTED }
        val status = when {
            factualCount == 0 || supportedCount == 0 -> AnswerGroundingStatus.ABSTAIN
            supportedCount == factualCount && unsupportedCitations.isEmpty() -> AnswerGroundingStatus.VERIFIED
            else -> AnswerGroundingStatus.PARTIALLY_SUPPORTED
        }
        return AnswerGroundingReport(
            status, claimResults, unsupportedCitations, uncited,
            status != AnswerGroundingStatus.VERIFIED
        )
    }
}
