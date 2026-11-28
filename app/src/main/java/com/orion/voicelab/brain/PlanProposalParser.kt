package com.orion.voicelab.brain

import com.orion.voicelab.connectors.ConnectorId
import org.json.JSONObject
import java.util.Locale

sealed interface PlanProposalParseResult {
    data class Valid(val plan: OrionPlan) : PlanProposalParseResult
    data class Invalid(val errors: List<String>) : PlanProposalParseResult
}

/** Strict parser for structured LLM plan proposals. Parsing never executes a plan. */
object PlanProposalParser {
    private const val MAX_INPUT_CHARS = 24_000
    private const val MAX_STEPS = 12
    private const val MAX_TEXT_FIELD = 1_200
    private val supportedReadOperations = mapOf(
        ConnectorId.GMAIL to setOf("search_messages", "get_message", "read_latest_message"),
        ConnectorId.GITHUB to setOf("list_repositories", "get_issue", "list_commits", "list_workflow_runs", "list_files", "list_issues"),
        ConnectorId.GOOGLE_MAPS to setOf("search_place"),
        ConnectorId.PESCAIA to setOf("search_public")
    )

    fun parse(raw: String, planId: String): PlanProposalParseResult {
        if (raw.length !in 2..MAX_INPUT_CHARS) return invalid("La propuesta está vacía o supera el límite de tamaño.")
        if (planId.isBlank() || planId.length > 100) return invalid("Identificador de plan no válido.")
        return try {
            val root = JSONObject(raw)
            val goal = readText(root, "goal", MAX_TEXT_FIELD).trim()
            val stepsJson = root.optJSONArray("steps") ?: return invalid("La propuesta no contiene un array de pasos.")
            if (stepsJson.length() !in 1..MAX_STEPS) return invalid("El plan debe contener entre 1 y $MAX_STEPS pasos.")

            val budgetJson = root.optJSONObject("budget") ?: JSONObject()
            val budget = ExecutionBudget(
                maxSteps = readInt(budgetJson, "maxSteps", MAX_STEPS).also { require(it in 1..MAX_STEPS) },
                maxRetriesPerStep = readInt(budgetJson, "maxRetriesPerStep", 1).also { require(it in 0..3) },
                maxDurationMs = readLong(budgetJson, "maxDurationMs", 5 * 60_000L).also { require(it in 1L..30L * 60_000L) }
            )
            val steps = mutableListOf<PlannedStep>()
            for (index in 0 until stepsJson.length()) {
                val item = stepsJson.optJSONObject(index) ?: return invalid("El paso ${index + 1} no es un objeto JSON.")
                val id = readText(item, "id", 80).trim()
                val title = readText(item, "title", 180).trim()
                val rationale = readText(item, "rationale", MAX_TEXT_FIELD).trim()
                require(id.isNotBlank() && title.isNotBlank()) { "Cada paso necesita identificador y título." }
                val kind = runCatching { PlanStepKind.valueOf(item.optString("kind").uppercase(Locale.ROOT)) }.getOrNull()
                    ?: return invalid("Tipo de paso desconocido en la posición ${index + 1}.")
                val risk = runCatching { PlanRisk.valueOf(item.optString("risk").uppercase(Locale.ROOT)) }.getOrNull()
                    ?: return invalid("Riesgo desconocido en la posición ${index + 1}.")

                val dependencies = mutableSetOf<String>()
                val dependenciesJson = item.optJSONArray("dependsOn")
                if (dependenciesJson != null) {
                    for (i in 0 until dependenciesJson.length()) {
                        val dependency = dependenciesJson.opt(i)
                        require(dependency is String && dependency.isNotBlank() && dependency.length <= 80) {
                            "Dependencia no válida en el paso $id."
                        }
                        require(dependencies.add(dependency)) { "Dependencia duplicada en el paso $id." }
                    }
                }

                val arguments = linkedMapOf<String, String>()
                val argumentsJson = item.optJSONObject("connectorArguments") ?: JSONObject()
                val keys = argumentsJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    require(key.length <= 100) { "Clave de argumento demasiado larga en el paso $id." }
                    val value = argumentsJson.opt(key)
                    require(value is String && value.length <= MAX_TEXT_FIELD) {
                        "Todos los argumentos deben ser texto plano y no superar $MAX_TEXT_FIELD caracteres."
                    }
                    arguments[key] = value
                }

                val connectorName = readOptionalText(item, "connectorId", 80).takeUnless { it.isBlank() || it == "null" }
                val connectorId = if (connectorName == null) null else {
                    runCatching { ConnectorId.valueOf(connectorName.uppercase(Locale.ROOT)) }.getOrNull()
                        ?: return invalid("Conector no reconocido en el paso $id.")
                }
                val operation = readOptionalText(item, "connectorOperation", 100).takeUnless { it.isBlank() || it == "null" }
                val localKinds = setOf(
                    PlanStepKind.LOCAL_CREATE_TASK,
                    PlanStepKind.LOCAL_CREATE_NOTE,
                    PlanStepKind.LOCAL_CREATE_REMINDER
                )
                if (kind == PlanStepKind.CONNECTOR_READ) {
                    if (connectorId == null || operation.isNullOrBlank()) {
                        return invalid("El paso externo $id necesita conector y operación explícitos.")
                    }
                    if (operation !in supportedReadOperations[connectorId].orEmpty()) {
                        return invalid("La operación $operation no está implementada para $connectorId.")
                    }
                    val requiredArguments = when (connectorId to operation) {
                        ConnectorId.GMAIL to "search_messages" -> setOf("query")
                        ConnectorId.GMAIL to "get_message" -> setOf("message_id")
                        ConnectorId.GITHUB to "get_issue" -> setOf("repository", "number")
                        ConnectorId.GITHUB to "list_commits",
                        ConnectorId.GITHUB to "list_workflow_runs",
                        ConnectorId.GITHUB to "list_files",
                        ConnectorId.GITHUB to "list_issues" -> setOf("repository")
                        ConnectorId.GOOGLE_MAPS to "search_place" -> setOf("destination")
                        ConnectorId.PESCAIA to "search_public" -> setOf("query")
                        else -> emptySet()
                    }
                    val missingArguments = requiredArguments - arguments.keys
                    if (missingArguments.isNotEmpty()) return invalid("Al paso $id le faltan argumentos: ${missingArguments.joinToString()}.")
                } else if (kind in localKinds) {
                    if (connectorId != null || operation != null) return invalid("Los pasos locales no admiten conectores.")
                    if (kind != PlanStepKind.LOCAL_CREATE_NOTE && arguments["title"].isNullOrBlank()) return invalid("El paso local $id necesita título.")
                    if (kind == PlanStepKind.LOCAL_CREATE_NOTE && arguments["content"].isNullOrBlank() && arguments["notes"].isNullOrBlank()) return invalid("La nota $id necesita contenido.")
                    if (kind == PlanStepKind.LOCAL_CREATE_REMINDER) {
                        val rawReminder = arguments["reminderAtMs"].orEmpty()
                        val reminderAt = rawReminder.toLongOrNull()
                        if (reminderAt == null && PlanOutputReferences.find(listOf(rawReminder)).isEmpty()) return invalid("El recordatorio $id necesita reminderAtMs válido o una referencia de salida.")
                        if (reminderAt != null && reminderAt <= System.currentTimeMillis()) return invalid("El recordatorio $id debe estar en el futuro.")
                    }
                } else {
                    return invalid("Tipo de paso no ejecutable en esta fase: $kind.")
                }

                steps += PlannedStep(
                    id = id,
                    kind = kind,
                    title = title,
                    rationale = rationale,
                    risk = risk,
                    dependsOn = dependencies,
                    requiresApproval = readBoolean(item, "requiresApproval", risk >= PlanRisk.MEDIUM),
                    idempotencyKey = readOptionalText(item, "idempotencyKey", 160).takeUnless { it.isBlank() || it == "null" },
                    inputSummary = readText(item, "inputSummary", MAX_TEXT_FIELD),
                    connectorId = connectorId,
                    connectorOperation = operation,
                    connectorArguments = arguments
                )
            }

            val plan = OrionPlan(planId, goal, steps, budget, PlanStatus.READY)
            val validation = PlanValidator.validate(plan)
            if (!validation.valid) PlanProposalParseResult.Invalid(validation.errors)
            else PlanProposalParseResult.Valid(plan)
        } catch (error: Exception) {
            invalid("JSON de plan no válido: ${error::class.java.simpleName}.")
        }
    }

    private fun readText(json: JSONObject, key: String, maxLength: Int): String {
        val value = json.opt(key) ?: return ""
        require(value is String && value.length <= maxLength) {
            "El campo $key debe ser texto y no superar $maxLength caracteres."
        }
        return value
    }

    private fun readOptionalText(json: JSONObject, key: String, maxLength: Int): String =
        readText(json, key, maxLength)

    private fun readInt(json: JSONObject, key: String, default: Int): Int {
        if (!json.has(key)) return default
        val value = json.opt(key)
        require(value is Number) { "El campo $key debe ser numérico." }
        return value.toInt().also { require(value.toDouble() == it.toDouble()) { "El campo $key debe ser entero." } }
    }

    private fun readLong(json: JSONObject, key: String, default: Long): Long {
        if (!json.has(key)) return default
        val value = json.opt(key)
        require(value is Number) { "El campo $key debe ser numérico." }
        return value.toLong().also { require(value.toDouble() == it.toDouble()) { "El campo $key debe ser entero." } }
    }

    private fun readBoolean(json: JSONObject, key: String, default: Boolean): Boolean {
        if (!json.has(key)) return default
        val value = json.opt(key)
        require(value is Boolean) { "El campo $key debe ser booleano." }
        return value
    }

    private fun invalid(message: String) = PlanProposalParseResult.Invalid(listOf(message))
}
