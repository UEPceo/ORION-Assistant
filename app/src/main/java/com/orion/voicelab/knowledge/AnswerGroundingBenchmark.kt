package com.orion.voicelab.knowledge

data class GroundingBenchmarkCase(
    val id: String,
    val claims: List<AtomicAnswerClaim>,
    val evidence: List<KnowledgeEvidence>,
    val expectedClaimStatuses: Map<String, ClaimSupportStatus>,
    val expectedAbstention: Boolean,
    val criticalDomain: Boolean = false
)

data class GroundingAcceptanceCriteria(
    val minimumFactualClaims: Int = 100,
    val minimumContradictionCases: Int = 20,
    val minimumAbstentionCases: Int = 20,
    val minimumSupportedPrecision: Double = 0.95,
    val minimumContradictionRecall: Double = 0.90,
    val minimumAbstentionAccuracy: Double = 0.90,
    val minimumCitationRejectionRate: Double = 1.0,
    val maximumCriticalFalsePositives: Int = 0
)

data class GroundingBenchmarkMetrics(
    val factualClaims: Int,
    val supportedPrecision: Double,
    val contradictionRecall: Double,
    val abstentionAccuracy: Double,
    val citationRejectionRate: Double,
    val criticalFalsePositives: Int,
    val contradictionCases: Int,
    val abstentionCases: Int
)

data class GroundingBenchmarkReport(
    val passed: Boolean,
    val sampleSufficient: Boolean,
    val metrics: GroundingBenchmarkMetrics,
    val failedCriteria: List<String>,
    val caseReports: Map<String, AnswerGroundingReport>
)

/** Runs a reproducible, human-labelled battery against a pinned auditor implementation. */
class AnswerGroundingBenchmarkRunner(
    private val auditor: AnswerGroundingAuditor,
    private val criteria: GroundingAcceptanceCriteria = GroundingAcceptanceCriteria()
) {
    suspend fun run(cases: List<GroundingBenchmarkCase>): GroundingBenchmarkReport {
        require(cases.map { it.id }.distinct().size == cases.size) { "Benchmark case IDs must be unique." }
        val reports = linkedMapOf<String, AnswerGroundingReport>()
        var tpSupported = 0
        var fpSupported = 0
        var tpContradiction = 0
        var fnContradiction = 0
        var abstentionCorrect = 0
        var invalidCitationExpected = 0
        var invalidCitationRejected = 0
        var criticalFalsePositives = 0
        var factualClaims = 0
        var contradictionCases = 0
        var abstentionCases = 0

        for (case in cases) {
            val report = auditor.audit(case.claims, case.evidence)
            reports[case.id] = report
            val actualById = report.claims.associateBy { it.claimId }
            for ((claimId, expected) in case.expectedClaimStatuses) {
                factualClaims++
                val actual = actualById[claimId]?.status ?: ClaimSupportStatus.UNVERIFIABLE
                if (expected == ClaimSupportStatus.SUPPORTED && actual == ClaimSupportStatus.SUPPORTED) tpSupported++
                if (expected != ClaimSupportStatus.SUPPORTED && actual == ClaimSupportStatus.SUPPORTED) {
                    fpSupported++
                    if (case.criticalDomain) criticalFalsePositives++
                }
                if (expected == ClaimSupportStatus.CONTRADICTED) {
                    contradictionCases++
                    if (actual == ClaimSupportStatus.CONTRADICTED) tpContradiction++ else fnContradiction++
                }
            }
            if (case.expectedAbstention) {
                abstentionCases++
                if (report.requiresAbstention) abstentionCorrect++
            }
            val unknownIds = case.claims.filter { it.isFactual }
                .flatMap { it.citedEvidenceIds }
                .filter { id -> case.evidence.none { it.id == id } }.toSet()
            invalidCitationExpected += unknownIds.size
            invalidCitationRejected += unknownIds.intersect(report.unsupportedCitationIds).size
        }

        fun ratio(n: Int, d: Int) = if (d == 0) 0.0 else n.toDouble() / d
        val metrics = GroundingBenchmarkMetrics(
            factualClaims, ratio(tpSupported, tpSupported + fpSupported),
            ratio(tpContradiction, tpContradiction + fnContradiction),
            ratio(abstentionCorrect, abstentionCases),
            if (invalidCitationExpected == 0) 1.0 else ratio(invalidCitationRejected, invalidCitationExpected),
            criticalFalsePositives, contradictionCases, abstentionCases
        )
        val sampleSufficient = factualClaims >= criteria.minimumFactualClaims &&
            contradictionCases >= criteria.minimumContradictionCases &&
            abstentionCases >= criteria.minimumAbstentionCases
        val failures = buildList {
            if (!sampleSufficient) add("Insufficient benchmark sample size.")
            if (metrics.supportedPrecision < criteria.minimumSupportedPrecision) add("Supported precision below threshold.")
            if (metrics.contradictionRecall < criteria.minimumContradictionRecall) add("Contradiction recall below threshold.")
            if (metrics.abstentionAccuracy < criteria.minimumAbstentionAccuracy) add("Abstention accuracy below threshold.")
            if (metrics.citationRejectionRate < criteria.minimumCitationRejectionRate) add("Unknown citation rejection below threshold.")
            if (metrics.criticalFalsePositives > criteria.maximumCriticalFalsePositives) add("Critical-domain false positives exceed threshold.")
        }
        return GroundingBenchmarkReport(failures.isEmpty(), sampleSufficient, metrics, failures, reports)
    }
}
