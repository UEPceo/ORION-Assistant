package com.orion.voicelab.knowledge

import java.text.Normalizer
import java.util.Locale

data class KnowledgePublicationCheck(
    val passed: Boolean,
    val baselineScore: Double,
    val candidateScore: Double,
    val evaluatedBaselineCases: Int,
    val regressions: List<String>,
    val candidateCasePassed: Boolean
)

/**
 * Runs the deterministic retrieval suite against the current corpus and a read-only
 * candidate overlay. It never mutates the active corpus.
 */
class KnowledgeCorpusRegressionGate(
    private val corpus: KnowledgeRepository,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val maxBaselineCases: Int = 120
) {
    suspend fun evaluate(candidate: KnowledgeDocument): KnowledgePublicationCheck {
        require(maxBaselineCases in 1..500)

        // Include the previous version when this is an update. The overlay replaces it
        // during candidate searches, so old retrieval expectations remain protected.
        val baselineDocuments = corpus.allDocuments()
            .groupBy { it.evidence.domain }
            .toSortedMap(compareBy { it.name })
            .values
            .flatMap { documents ->
                documents.sortedByDescending { it.evidence.retrievedAtMs }.take(MAX_CASES_PER_DOMAIN)
            }
            .take(maxBaselineCases)

        val regressions = mutableListOf<String>()
        var baselinePassed = 0
        var candidatePassed = 0
        val evaluated = mutableListOf<Pair<KnowledgeRegressionCase, KnowledgeRegressionResult>>()

        for (document in baselineDocuments) {
            val test = regressionCase(document)
            val baselineObservation = observe(test, corpus.searchForRegression(queryFor(test), RETRIEVAL_LIMIT))
            val baselineResult = KnowledgeRegressionEvaluator.evaluate(test, baselineObservation, nowMs())
            if (baselineResult.passed) baselinePassed++

            val overlaid = corpus.searchWithCandidate(queryFor(test), RETRIEVAL_LIMIT, candidate)
            val candidateResult = KnowledgeRegressionEvaluator.evaluate(test, observe(test, overlaid), nowMs())
            evaluated += test to candidateResult
            if (baselineResult.passed && !candidateResult.passed) {
                regressions += "${test.id}: ${describe(candidateResult)}"
            }
        }

        val candidateTest = regressionCase(candidate)
        val candidateObservation = observe(
            candidateTest,
            corpus.searchWithCandidate(queryFor(candidateTest), RETRIEVAL_LIMIT, candidate)
        )
        val candidateResult = KnowledgeRegressionEvaluator.evaluate(candidateTest, candidateObservation, nowMs())
        if (candidateResult.passed) candidatePassed++
        else regressions += "candidate:${candidateTest.id}: ${describe(candidateResult)}"

        val denominator = baselineDocuments.size + 1
        val baselineScore = if (denominator == 1) 0.0 else baselinePassed.toDouble() / denominator
        val candidateScore = (candidatePassed + evaluated.count { (_, result) -> result.passed })
            .toDouble() / denominator
        val noRegressions = regressions.none { !it.startsWith("candidate:") }
        val passed = candidateResult.passed && noRegressions &&
            candidateScore + SCORE_EPSILON >= baselineScore

        return KnowledgePublicationCheck(
            passed = passed,
            baselineScore = baselineScore,
            candidateScore = candidateScore,
            evaluatedBaselineCases = baselineDocuments.size,
            regressions = regressions.take(MAX_REPORTED_REGRESSIONS),
            candidateCasePassed = candidateResult.passed
        )
    }

    private fun regressionCase(document: KnowledgeDocument): KnowledgeRegressionCase {
        val evidence = document.evidence
        val primary = evidence.authority in PRIMARY_AUTHORITIES
        return KnowledgeRegressionCase(
            id = evidence.id,
            query = evidence.title,
            expectedEvidenceIds = setOf(evidence.id),
            domain = evidence.domain,
            requirePrimary = primary
        )
    }

    private fun queryFor(test: KnowledgeRegressionCase): BrainQuery {
        val normalized = Normalizer.normalize(test.query.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .trim()
        return BrainQuery(
            rawText = test.query,
            normalizedText = normalized,
            domain = test.domain,
            requiresFreshEvidence = false,
            requiresPrimaryEvidence = test.requirePrimary
        )
    }

    private fun observe(
        test: KnowledgeRegressionCase,
        documents: List<KnowledgeDocument>
    ): KnowledgeRegressionObservation {
        val ids = documents.map { it.evidence.id }.toSet()
        return KnowledgeRegressionObservation(
            caseId = test.id,
            retrievedEvidenceIds = ids,
            citedEvidenceIds = ids,
            evidence = documents.map { it.evidence },
            answer = if (documents.isEmpty()) "" else "retrieval-regression-check"
        )
    }

    private fun describe(result: KnowledgeRegressionResult): String = buildList {
        if (result.missingExpectedEvidence.isNotEmpty()) add("expected evidence missing")
        if (result.domainMismatchIds.isNotEmpty()) add("domain mismatch")
        if (result.primaryEvidenceRequiredMissing) add("primary evidence missing")
        if (result.staleEvidenceIds.isNotEmpty()) add("stale evidence")
        if (result.unsupportedCitations.isNotEmpty()) add("unsupported retrieval reference")
        if (result.answerEmpty) add("empty retrieval")
    }.joinToString(", ").ifBlank { "regression detected" }

    private companion object {
        const val RETRIEVAL_LIMIT = 5
        const val MAX_REPORTED_REGRESSIONS = 20
        const val MAX_CASES_PER_DOMAIN = 8
        const val SCORE_EPSILON = 0.000001
        val PRIMARY_AUTHORITIES = setOf(
            EvidenceAuthority.OFFICIAL_PRIMARY,
            EvidenceAuthority.PRIMARY_TECHNICAL,
            EvidenceAuthority.PEER_REVIEWED
        )
    }
}
