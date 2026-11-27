package com.orion.voicelab.brain

import com.orion.voicelab.connectors.ConnectorId
import org.json.JSONObject
import java.util.Locale

sealed interface PlanProposalParseResult {
    data class Valid(val plan: OrionPlan) : PlanProposalParseResult
    data class Invalid(val errors: List<String>) : PlanProposalParseResult
}

/** Strict parser for a future structured LLM plan channel. Parsing never executes a plan. */
object PlanProposalParser {
    private const val MAX_INPUT_CHARS = 24_000
    private const val MAX_STEPS = 12
    private const val MAX_TEXT_FIELD = 1_200
    private val connectorKinds = setOf(
        PlanStepKind.CONNECTOR_READ,
        PlanStepKind.CONNECTOR_WRITE,
        PlanStepKind.SEND_MESSAGE,
        PlanStepKind.PLACE_CALL,
        PlanStepKind.DELETE_DATA,
        PlanStepKind.PURCHASE
    )

    fun parse(raw: String, planId: String): PlanProposalParseResult {
        if (raw.length !in 2..MAX_INPUT_CHARS) return PlanProposalParseResult.Invalid(listOf("La propuesta está vacía o supera el límite de tamaño."))
        if (planId.isBlank() || planId.length > 100) return PlanProposalParseResult.Invalid(listOf("Identificador de plan no válido."))
        return try {
            val root = JSONObject(raw)
            val goal = root.optString("goal").trim().take(MAX_TEXT_FIELD)
            val stepsJson = root.optJSONArray("steps")
                ?: return PlanProposalParseResult.Invalid(listOf("La propuesta no contiene un array de pasos."))
            if (stepsJson.length() !in 1..MAX_STEPS) {
                return PlanProposalParseResult.Invalid(listOf("El plan debe contener entre 1 y $MAX_STEPS pasos."))
            }
            val budgetJson = root.optJSONObject("budget") ?: JSONObject()
            val budget = ExecutionBudget(
                maxSteps = budgetJson.optInt("maxSteps", MAX_STEPS).coerceIn(1, MAX_STEPS),
                maxRetriesPerStep = budgetJson.optInt("maxRetriesPerStep", 1).coerceIn(0, 3),
                maxDurationMs = budgetJson.optLong("maxDurationMs", 5 * 60_000L).coerceIn(1L, 30L * 60_000L)
            )
            val steps = buildList {
                for (index in 0 until stepsJson.length()) {
                    val item = stepsJson.optJSONObject(index)
                        ?: return PlanProposalParseResult.Invalid(listOf("El paso ${index + 1} no es un objeto JSON."))
                    val id = item.optString("id").trim().take(80)
                    val title = item.optString("title").trim().take(180)
                    val rationale = item.optString("rationale").trim().take(MAX_TEXT_FIELD)
                    val kind = runCatching { PlanStepKind.valueOf(item.optString("kind").uppercase(Locale.ROOT)) }.getOrNull()
                        ?: return PlanProposalParseResult.Invalid(listOf("Tipo de paso desconocido en la posición ${index + 1}."))
                    val risk = runCatching { PlanRisk.valueOf(item.optString("risk").uppercase(Locale.ROOT)) }.getOrNull()
                        ?: return PlanProposalParseResult.Invalid(listOf("Riesgo desconocido en la posición ${index + 1}."))
                    val dependenciesJson = item.optJSONArray("dependsOn")
                    val dependencies = buildSet {
                        if (dependenciesJson != null) {
                            for (i in 0 until dependenciesJson.length()) add(dependenciesJson.getString(i).take(80))
                        }
                    }
                    val argumentsJson = item.optJSONObject("connectorArguments") ?: JSONObject()
                    val arguments = buildMap {
                        val keys = argumentsJson.keys()
                        while (keys.hasNext()) {
                            val key = keys.next().take(100)
                            val value = argumentsJson.opt(key)
                            if (value !is String) {
                                return PlanProposalParseResult.Invalid(listOf("Todos los argumentos del conector deben ser texto plano."))
                            }
                            put(key, value.take(MAX_TEXT_FIELD))
                        }
                    }
                    val connectorId = item.optString("connectorId").takeUnless { it.isBlank() || it == "null" }
                        ?.let { name ->
                            runCatching { ConnectorId.valueOf(name.uppercase(Locale.ROOT)) }.getOrNull()
                                ?: return PlanProposalParseResult.Invalid(listOf("Conector no reconocido en el paso $id."))
                        }
                    val operation = item.optString("connectorOperation").takeUnless { it.isBlank() || it == "null" }?.take(100)
                    val requiresApproval = item.optBoolean("requiresApproval", risk >= PlanRisk.MEDIUM)
                    if (kind in connectorKinds && (connectorId == null || operation.isNullOrBlank())) {
                        return PlanProposalParseResult.Invalid(listOf("El paso externo $id necesita conector y operación explícitos."))
                    }
                    add(PlannedStep(
                        id = id,
                        kind = kind,
                        title = title,
                        rationale = rationale,
                        risk = risk,
                        dependsOn = dependencies,
                        requiresApproval = requiresApproval,
                        idempotencyKey = item.optString("idempotencyKey").takeUnless { it.isBlank() || it == "null" }?.take(160),
                        inputSummary = item.optString("inputSummary").take(MAX_TEXT_FIELD),
                        connectorId = connectorId,
                        connectorOperation = operation,
                        connectorArguments = arguments
                    ))
                }
            }
            val plan = OrionPlan(planId, goal, steps, budget, PlanStatus.READY)
            val validation = PlanValidator.validate(plan)
            if (!validation.valid) PlanProposalParseResult.Invalid(validation.errors)
            else PlanProposalParseResult.Valid(plan)
        } catch (error: Exception) {
            PlanProposalParseResult.Invalid(listOf("JSON de plan no válido: ${error::class.java.simpleName}."))
        }
    }
}
