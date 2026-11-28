package com.orion.voicelab.knowledge

data class KnowledgeRegressionCase(
    val id:String,
    val query:String,
    val expectedEvidenceIds:Set<String>,
    val domain:KnowledgeDomain,
    val requirePrimary:Boolean=false,
    val requireFresh:Boolean=false,
    val maxEvidenceAgeMs:Long?=null
)

data class KnowledgeRegressionObservation(
    val caseId:String,
    val retrievedEvidenceIds:Set<String>,
    val citedEvidenceIds:Set<String>,
    val evidence:List<KnowledgeEvidence>,
    val answer:String
)

data class KnowledgeRegressionResult(
    val caseId:String,
    val passed:Boolean,
    val missingExpectedEvidence:Set<String>,
    val unsupportedCitations:Set<String>,
    val staleEvidenceIds:Set<String>,
    val domainMismatchIds:Set<String>,
    val answerEmpty:Boolean
)

/** Deterministic evaluator for corpus/retrieval regressions. It does not grade truth by LLM opinion. */
object KnowledgeRegressionEvaluator {
    fun evaluate(test:KnowledgeRegressionCase,observation:KnowledgeRegressionObservation,nowMs:Long):KnowledgeRegressionResult {
        require(test.id==observation.caseId) { "Case/observation IDs do not match." }
        val byId=observation.evidence.associateBy { it.id }
        val retrieved=observation.retrievedEvidenceIds
        val cited=observation.citedEvidenceIds
        val missing=test.expectedEvidenceIds-retrieved
        val unsupported=cited-retrieved
        val stale=if(test.requireFresh&&test.maxEvidenceAgeMs!=null) {
            retrieved.filter { id -> byId[id]?.let { nowMs-it.retrievedAtMs>test.maxEvidenceAgeMs || it.retrievedAtMs>nowMs } ?: true }.toSet()
        } else emptySet()
        val domainMismatch=retrieved.filter { byId[it]?.domain!=test.domain }.toSet()
        val missingPrimary=if(test.requirePrimary) retrieved.filter { byId[it]?.authority !in setOf(EvidenceAuthority.OFFICIAL_PRIMARY,EvidenceAuthority.PRIMARY_TECHNICAL,EvidenceAuthority.PEER_REVIEWED) }.toSet() else emptySet()
        val answerEmpty=observation.answer.isBlank()
        val passed=missing.isEmpty()&&unsupported.isEmpty()&&stale.isEmpty()&&domainMismatch.isEmpty()&&missingPrimary.isEmpty()&&!answerEmpty
        return KnowledgeRegressionResult(test.id,passed,missing,unsupported,stale,domainMismatch+missingPrimary,answerEmpty)
    }

    fun score(results:List<KnowledgeRegressionResult>):Double =
        if(results.isEmpty()) 1.0 else results.count { it.passed }.toDouble()/results.size
}
