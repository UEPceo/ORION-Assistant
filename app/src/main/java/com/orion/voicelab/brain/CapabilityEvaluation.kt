package com.orion.voicelab.brain

enum class CapabilityArea { KNOWLEDGE_QUALITY, MULTI_STEP_PLANNING, OFFLINE_KNOWLEDGE, REASONING_AND_MODEL, TOOL_EXECUTION, MULTIMODAL, RELIABILITY_AND_SAFETY }
data class EvaluationCriterion(val id: String, val weight: Double, val passed: Boolean, val evidence: String)
data class AreaEvaluation(val area: CapabilityArea, val score: Double, val passedWeight: Double, val totalWeight: Double, val criterionCount: Int, val passedCount: Int, val gaps: List<String>) {
    val meetsTarget: Boolean get() = score >= TARGET_SCORE
    companion object { const val TARGET_SCORE = 8.0 }
}
data class OverallCapabilityEvaluation(val areas: List<AreaEvaluation>, val minimumScore: Double, val averageScore: Double, val meetsTarget: Boolean)

object CapabilityEvaluator {
    fun evaluate(criteria: Map<CapabilityArea, List<EvaluationCriterion>>): OverallCapabilityEvaluation {
        val areas = CapabilityArea.entries.map { area ->
            val valid = criteria[area].orEmpty().filter { it.weight > 0.0 && it.weight.isFinite() }
            val total = valid.sumOf { it.weight }
            val passed = valid.filter { it.passed }.sumOf { it.weight }
            val score = if (total == 0.0) 0.0 else (10.0 * passed / total).coerceIn(0.0, 10.0)
            AreaEvaluation(area, score, passed, total, valid.size, valid.count { it.passed }, valid.filterNot { it.passed }.map { it.evidence.ifBlank { it.id } })
        }
        val minimum = areas.minOfOrNull { it.score } ?: 0.0
        val average = areas.map { it.score }.average().takeIf { it.isFinite() } ?: 0.0
        return OverallCapabilityEvaluation(areas, minimum, average, areas.all { it.meetsTarget && it.criterionCount > 0 })
    }
}
