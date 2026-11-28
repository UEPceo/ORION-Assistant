package com.orion.voicelab.llm

/**
 * Conservative preflight for native GGUF allocation.
 *
 * The model file is only part of the footprint: llama.cpp also allocates KV cache,
 * graph/work buffers and native metadata. We reserve an additional fixed margin
 * for Android, audio and the rest of ORION.
 */
data class ModelMemorySnapshot(
    val totalRamBytes: Long,
    val availableRamBytes: Long
)

data class ModelMemoryDecision(
    val allowed: Boolean,
    val estimatedRequiredBytes: Long,
    val availableBytes: Long,
    val reason: String
)

object ModelMemoryPolicy {
    private const val MIB = 1024L * 1024L
    private const val BASE_RESERVE_BYTES = 768L * MIB
    private const val MODEL_MULTIPLIER_NUMERATOR = 135L
    private const val MODEL_MULTIPLIER_DENOMINATOR = 100L
    private const val CONTEXT_BYTES_PER_TOKEN = 48L * 1024L
    private const val MIN_FREE_HEADROOM_BYTES = 512L * MIB

    fun estimateRequiredBytes(modelBytes: Long, contextTokens: Int): Long {
        require(modelBytes > 0L) { "El tamaño del modelo debe ser positivo." }
        require(contextTokens in 512..32_768) { "Contexto fuera de límites seguros." }
        val modelFootprint = modelBytes * MODEL_MULTIPLIER_NUMERATOR / MODEL_MULTIPLIER_DENOMINATOR
        val contextFootprint = contextTokens.toLong() * CONTEXT_BYTES_PER_TOKEN
        return modelFootprint + contextFootprint + BASE_RESERVE_BYTES
    }

    fun evaluate(
        snapshot: ModelMemorySnapshot,
        modelBytes: Long,
        contextTokens: Int
    ): ModelMemoryDecision {
        val required = estimateRequiredBytes(modelBytes, contextTokens)
        val totalValid = snapshot.totalRamBytes > 0L &&
            snapshot.availableRamBytes >= 0L &&
            snapshot.availableRamBytes <= snapshot.totalRamBytes
        val allowed = totalValid &&
            snapshot.availableRamBytes >= required + MIN_FREE_HEADROOM_BYTES
        val reason = when {
            !totalValid -> "No se pudo obtener una medición fiable de memoria."
            allowed -> "Memoria disponible suficiente para iniciar el motor."
            else -> "RAM libre insuficiente: se necesitan aproximadamente " +
                "${(required + MIN_FREE_HEADROOM_BYTES) / MIB} MB libres."
        }
        return ModelMemoryDecision(allowed, required, snapshot.availableRamBytes, reason)
    }

    /** Chooses the largest requested context that passes the complete model + native headroom check. */
    fun chooseSafeContextTokens(
        snapshot: ModelMemorySnapshot,
        modelBytes: Long,
        requested: Int
    ): Int {
        val candidates = listOf(8192, 4096, 2048, 1024, 512).filter { it <= requested }
        return candidates.firstOrNull { evaluate(snapshot, modelBytes, it).allowed } ?: 0
    }

    /** Reduces context before model size is ever compromised. */
    fun safeContextTokens(requested: Int, availableBytes: Long): Int {
        val candidates = listOf(8192, 4096, 2048, 1024, 512).filter { it <= requested }
        return candidates.firstOrNull {
            availableBytes >= it.toLong() * CONTEXT_BYTES_PER_TOKEN + BASE_RESERVE_BYTES
        } ?: 0
    }
}
