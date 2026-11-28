package com.orion.voicelab.llm

/**
 * Mirrors the conservative automatic thread policy in the native Kotlin-LlamaCpp
 * JNI wrapper. In particular, avoid forcing four inference threads on a
 * four-core device, where the native runtime itself selects two.
 */
object LlamaThreadPolicy {
    fun recommend(availableProcessors: Int): Int {
        val cores = availableProcessors.coerceAtLeast(1)
        return if (cores == 4) 2 else minOf(4, cores)
    }
}
