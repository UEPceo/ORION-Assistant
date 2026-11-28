package com.orion.voicelab.llm

import android.os.Build

/**
 * Reports only what the Android app can know without native instrumentation.
 * It deliberately does not claim which runtime kernel was selected.
 */
data class LlamaRuntimeSnapshot(
    val abi: String,
    val availableProcessors: Int,
    val configuredThreads: Int,
    val backend: String = "CPU"
) {
    val dispatchDescription: String
        get() = "selección automática de kernels ARM por la biblioteca nativa"
}

object LlamaRuntimeDiagnostics {
    fun snapshot(
        supportedAbis: Array<String> = Build.SUPPORTED_ABIS,
        availableProcessors: Int = Runtime.getRuntime().availableProcessors()
    ): LlamaRuntimeSnapshot {
        val cores = availableProcessors.coerceAtLeast(1)
        return LlamaRuntimeSnapshot(
            abi = supportedAbis.firstOrNull().orEmpty().ifBlank { "desconocida" },
            availableProcessors = cores,
            configuredThreads = LlamaThreadPolicy.recommend(cores)
        )
    }
}
