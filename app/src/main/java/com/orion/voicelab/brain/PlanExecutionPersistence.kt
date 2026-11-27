package com.orion.voicelab.brain

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Index
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import com.orion.voicelab.connectors.ConnectorId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "plan_executions", indices = [Index(value = ["updatedAtMs"])])
data class PlanExecutionEntity(
    @PrimaryKey val id: String,
    val goal: String,
    val planJson: String,
    val resultsJson: String,
    val status: String,
    val activeStepId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val lastError: String
)

@Dao
interface PlanExecutionDao {
    @Query("SELECT * FROM plan_executions ORDER BY updatedAtMs DESC")
    fun observeAll(): Flow<List<PlanExecutionEntity>>

    @Query("SELECT * FROM plan_executions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlanExecutionEntity?

    @Query("SELECT * FROM plan_executions WHERE status = 'RUNNING'")
    suspend fun getRunning(): List<PlanExecutionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanExecutionEntity)

    @Query("UPDATE plan_executions SET resultsJson = :resultsJson, status = :status, activeStepId = :activeStepId, updatedAtMs = :updatedAtMs, lastError = :lastError WHERE id = :id")
    suspend fun updateProgress(
        id: String,
        resultsJson: String,
        status: String,
        activeStepId: String?,
        updatedAtMs: Long,
        lastError: String
    )

    @Query("DELETE FROM plan_executions WHERE id = :id AND status != 'RUNNING'")
    suspend fun deleteById(id: String): Int
}

data class PersistedPlanExecution(
    val plan: OrionPlan,
    val results: Map<String, StepExecution>,
    val status: PlanStatus,
    val activeStepId: String?,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val lastError: String
)

object PlanExecutionJsonCodec {
    fun encodePlan(plan: OrionPlan): String {
        val root = JSONObject()
            .put("id", plan.id)
            .put("goal", plan.goal)
            .put("status", plan.status.name)
            .put("budget", JSONObject()
                .put("maxSteps", plan.budget.maxSteps)
                .put("maxRetriesPerStep", plan.budget.maxRetriesPerStep)
                .put("maxDurationMs", plan.budget.maxDurationMs))
        val steps = JSONArray()
        plan.steps.forEach { step ->
            val args = JSONObject()
            step.connectorArguments.toSortedMap().forEach { (key, value) -> args.put(key, value) }
            val dependencies = JSONArray()
            step.dependsOn.sorted().forEach(dependencies::put)
            steps.put(JSONObject()
                .put("id", step.id)
                .put("kind", step.kind.name)
                .put("title", step.title)
                .put("rationale", step.rationale)
                .put("risk", step.risk.name)
                .put("dependsOn", dependencies)
                .put("requiresApproval", step.requiresApproval)
                .put("idempotencyKey", step.idempotencyKey ?: JSONObject.NULL)
                .put("inputSummary", step.inputSummary)
                .put("connectorId", step.connectorId?.name ?: JSONObject.NULL)
                .put("connectorOperation", step.connectorOperation ?: JSONObject.NULL)
                .put("connectorArguments", args))
        }
        root.put("steps", steps)
        return root.toString()
    }

    fun decodePlan(json: String): OrionPlan {
        val root = JSONObject(json)
        val budgetJson = root.optJSONObject("budget") ?: JSONObject()
        val budget = ExecutionBudget(
            maxSteps = budgetJson.optInt("maxSteps", 12),
            maxRetriesPerStep = budgetJson.optInt("maxRetriesPerStep", 1),
            maxDurationMs = budgetJson.optLong("maxDurationMs", 5 * 60_000L)
        )
        val stepsJson = root.optJSONArray("steps") ?: JSONArray()
        val steps = buildList {
            for (index in 0 until stepsJson.length()) {
                val item = stepsJson.getJSONObject(index)
                val dependenciesJson = item.optJSONArray("dependsOn") ?: JSONArray()
                val dependencies = buildSet {
                    for (i in 0 until dependenciesJson.length()) add(dependenciesJson.getString(i))
                }
                val argsJson = item.optJSONObject("connectorArguments") ?: JSONObject()
                val args = buildMap {
                    val keys = argsJson.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        put(key, argsJson.optString(key))
                    }
                }
                val connectorName = item.optString("connectorId").takeUnless { it.isBlank() || it == "null" }
                add(PlannedStep(
                    id = item.getString("id"),
                    kind = PlanStepKind.valueOf(item.getString("kind")),
                    title = item.getString("title"),
                    rationale = item.optString("rationale"),
                    risk = PlanRisk.valueOf(item.getString("risk")),
                    dependsOn = dependencies,
                    requiresApproval = item.optBoolean("requiresApproval", true),
                    idempotencyKey = item.optString("idempotencyKey").takeUnless { it.isBlank() || it == "null" },
                    inputSummary = item.optString("inputSummary"),
                    connectorId = connectorName?.let { ConnectorId.valueOf(it) },
                    connectorOperation = item.optString("connectorOperation").takeUnless { it.isBlank() || it == "null" },
                    connectorArguments = args
                ))
            }
        }
        return OrionPlan(
            id = root.getString("id"),
            goal = root.getString("goal"),
            steps = steps,
            budget = budget,
            status = runCatching { PlanStatus.valueOf(root.optString("status", PlanStatus.DRAFT.name)) }.getOrDefault(PlanStatus.DRAFT)
        )
    }

    fun encodeResults(results: Map<String, StepExecution>): String {
        val array = JSONArray()
        results.toSortedMap().forEach { (id, result) ->
            array.put(JSONObject()
                .put("id", id)
                .put("status", result.status.name)
                .put("summary", result.summary.take(6_000))
                .put("outputReference", result.outputReference?.take(300) ?: JSONObject.NULL))
        }
        return array.toString()
    }

    fun decodeResults(json: String): Map<String, StepExecution> {
        val array = JSONArray(json)
        return buildMap {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                val output = item.optString("outputReference").takeUnless { it.isBlank() || it == "null" }
                put(item.getString("id"), StepExecution(
                    StepStatus.valueOf(item.getString("status")),
                    item.optString("summary"),
                    output
                ))
            }
        }
    }
}

class PlanExecutionRepository(private val dao: PlanExecutionDao) {
    fun observeAll(): Flow<List<PlanExecutionEntity>> = dao.observeAll()

    suspend fun create(plan: OrionPlan, nowMs: Long = System.currentTimeMillis()) {
        val validation = PlanValidator.validate(plan)
        require(validation.valid) { validation.errors.joinToString(" ") }
        require(dao.getById(plan.id) == null) { "Ya existe una ejecución con ese identificador." }
        dao.upsert(PlanExecutionEntity(
            id = plan.id,
            goal = plan.goal,
            planJson = PlanExecutionJsonCodec.encodePlan(plan),
            resultsJson = "[]",
            status = PlanStatus.READY.name,
            activeStepId = null,
            createdAtMs = nowMs,
            updatedAtMs = nowMs,
            lastError = ""
        ))
    }

    suspend fun load(id: String): PersistedPlanExecution? {
        val entity = dao.getById(id) ?: return null
        return runCatching {
            PersistedPlanExecution(
                plan = PlanExecutionJsonCodec.decodePlan(entity.planJson),
                results = PlanExecutionJsonCodec.decodeResults(entity.resultsJson),
                status = PlanStatus.valueOf(entity.status),
                activeStepId = entity.activeStepId,
                createdAtMs = entity.createdAtMs,
                updatedAtMs = entity.updatedAtMs,
                lastError = entity.lastError
            )
        }.getOrNull()
    }

    suspend fun saveProgress(
        id: String,
        status: PlanStatus,
        results: Map<String, StepExecution>,
        activeStepId: String?,
        lastError: String = "",
        nowMs: Long = System.currentTimeMillis()
    ) {
        dao.updateProgress(
            id = id,
            resultsJson = PlanExecutionJsonCodec.encodeResults(results),
            status = status.name,
            activeStepId = activeStepId,
            updatedAtMs = nowMs,
            lastError = lastError.take(1200)
        )
    }

    /** A process death during a step is an uncertain outcome, never an automatic retry. */
    suspend fun recoverInterruptedExecutions(nowMs: Long = System.currentTimeMillis()): Int {
        val running = dao.getRunning()
        running.forEach { recoverEntity(it, nowMs) }
        return running.size
    }

    suspend fun recoverInterruptedExecution(id: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        val entity = dao.getById(id) ?: return false
        if (entity.status != PlanStatus.RUNNING.name) return false
        recoverEntity(entity, nowMs)
        return true
    }

    private suspend fun recoverEntity(entity: PlanExecutionEntity, nowMs: Long) {
        try {
            val results = PlanExecutionJsonCodec.decodeResults(entity.resultsJson).toMutableMap()
            val uncertainStepIds = results.filterValues { it.status == StepStatus.RUNNING }.keys +
                listOfNotNull(entity.activeStepId)
            uncertainStepIds.distinct().forEach { activeId ->
                results[activeId] = StepExecution(
                    StepStatus.FAILED,
                    "ORION se cerró durante este paso. El resultado de la operación puede ser incierto; verifica el servicio antes de reintentar."
                )
            }
            dao.updateProgress(
                id = entity.id,
                resultsJson = PlanExecutionJsonCodec.encodeResults(results),
                status = PlanStatus.PAUSED.name,
                activeStepId = null,
                updatedAtMs = nowMs,
                lastError = "Ejecución interrumpida por cierre del proceso; requiere revisión."
            )
        } catch (_: Exception) {
            dao.updateProgress(
                id = entity.id,
                resultsJson = "[]",
                status = PlanStatus.FAILED.name,
                activeStepId = null,
                updatedAtMs = nowMs,
                lastError = "El estado guardado está dañado y no se puede recuperar de forma segura."
            )
        }
    }

    suspend fun delete(id: String) = dao.deleteById(id)
}

class PersistentPlanExecutionService(
    private val repository: PlanExecutionRepository,
    private val coordinator: PlanExecutionCoordinator = PlanExecutionCoordinator()
) {
    private val executionLocks = ConcurrentHashMap<String, Mutex>()

    suspend fun create(plan: OrionPlan) = repository.create(plan)

    suspend fun execute(
        planId: String,
        approvedStepIds: Set<String>,
        runner: PlanStepRunner,
        retryStepIds: Set<String> = emptySet()
    ): PlanExecutionReport {
        val lock = executionLocks.computeIfAbsent(planId) { Mutex() }
        return lock.withLock { executeLocked(planId, approvedStepIds, runner, retryStepIds) }
    }

    private suspend fun executeLocked(
        planId: String,
        approvedStepIds: Set<String>,
        runner: PlanStepRunner,
        retryStepIds: Set<String>
    ): PlanExecutionReport {
        repository.recoverInterruptedExecution(planId)
        val saved = repository.load(planId)
            ?: return PlanExecutionReport(planId, PlanStatus.FAILED, emptyMap(), "No existe la ejecución guardada.")
        val validation = PlanValidator.validate(saved.plan)
        if (!validation.valid) return PlanExecutionReport(planId, PlanStatus.FAILED, saved.results, validation.errors.joinToString(" "))
        val retryableIds = saved.results.filterValues { it.status == StepStatus.FAILED }.keys
        if (!retryStepIds.all { it in retryableIds }) {
            return PlanExecutionReport(planId, PlanStatus.FAILED, saved.results, "Solo se pueden reintentar pasos fallidos guardados.")
        }
        val initial = saved.results.filterValues { it.status !in setOf(StepStatus.BLOCKED, StepStatus.SKIPPED, StepStatus.RUNNING) }
        return coordinator.executeResumable(
            plan = saved.plan,
            approvedStepIds = approvedStepIds,
            runner = runner,
            initialResults = initial,
            retryStepIds = retryStepIds,
            observer = PlanExecutionObserver { status, results, active ->
                repository.saveProgress(
                    id = planId,
                    status = status,
                    results = results,
                    activeStepId = active,
                    lastError = if (status == PlanStatus.PAUSED) "Ejecución pausada o interrumpida." else ""
                )
            }
        )
    }

    suspend fun recoverInterruptedExecutions(): Int = repository.recoverInterruptedExecutions()
}
