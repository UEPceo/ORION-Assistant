package com.orion.voicelab.llm

import android.content.ContentResolver
import android.net.Uri
import android.os.SystemClock
import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.model.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object ChatPromptFormatter {

    val STOP_TOKENS = arrayOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")

    fun formatChatMl(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTurns: Int = 10
    ): String {
        val recent = if (history.size > maxTurns) {
            history.takeLast(maxTurns)
        } else {
            history
        }
        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt.trim())
            append("<|im_end|>\n")
            for (msg in recent) {
                val roleTag = when (msg.role) {
                    ChatRole.SYSTEM -> "system"
                    ChatRole.USER -> "user"
                    ChatRole.ASSISTANT -> "assistant"
                }
                append("<|im_start|>")
                append(roleTag)
                append('\n')
                append(msg.content.trim())
                append("<|im_end|>\n")
            }
            append("<|im_start|>assistant\n")
        }
    }

    fun cleanAssistantText(raw: String): String {
        var cleaned = raw
        for (stop in STOP_TOKENS) {
            val idx = cleaned.indexOf(stop)
            if (idx >= 0) {
                cleaned = cleaned.substring(0, idx)
            }
        }
        return cleaned
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .trim()
    }
}

class LlamaCppLanguageModel(
    private val contentResolver: ContentResolver,
    private val modelFile: File
) : LocalLanguageModel {

    companion object {
        private const val CONTEXT_SIZE = 2048
        private const val BATCH_SIZE = 256
        private const val THREADS_LOAD_AND_GEN = 4
        private const val MAX_PREDICT_TOKENS = 180
        private const val TEMPERATURE = 0.65
        private const val TOP_P = 0.90
        private const val REPEAT_PENALTY = 1.12
    }

    override val modelDisplayName: String = "Qwen2.5-1.5B-Instruct (Q4_K_M)"
    override val modelLicense: String = "Apache 2.0 (Modelo y llama.cpp / llamacpp-kotlin)"

    private val llamaExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OrionLlamaCpp").apply { isDaemon = true }
    }
    private val llamaDispatcher = llamaExecutor.asCoroutineDispatcher()
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val llamaAndroid by lazy { LlamaAndroid(contentResolver) }
    private val cancelled = AtomicBoolean(false)

    @Volatile
    private var contextId: Int? = null

    @Volatile
    private var activeTokenSink: ((String) -> Unit)? = null

    override fun isModelInstalled(): Boolean =
        modelFile.isFile && ModelManager.hasGgufMagicHeader(modelFile)

    override fun isLoaded(): Boolean = contextId != null

    override fun modelSizeMb(): Long =
        if (modelFile.isFile) modelFile.length() / (1024L * 1024L) else 0L

    override suspend fun load(): Long = mutex.withLock {
        withContext(llamaDispatcher) {
            if (contextId != null) return@withContext 0L
            check(isModelInstalled()) {
                "El modelo LLM local no está instalado."
            }

            val t0 = SystemClock.elapsedRealtime()
            val uri = Uri.fromFile(modelFile)
            val pfd = contentResolver.openFileDescriptor(uri, "r")
                ?: error("No se pudo abrir el descriptor del archivo GGUF")
            val fd = pfd.detachFd()

            val params = mapOf(
                "model" to uri.toString(),
                "model_fd" to fd,
                "use_mmap" to true,
                "use_mlock" to false,
                "n_ctx" to CONTEXT_SIZE,
                "n_batch" to BATCH_SIZE,
                "n_threads" to THREADS_LOAD_AND_GEN,
                "n_gpu_layers" to 0
            )

            val result = llamaAndroid.startEngine(params) { token ->
                if (!cancelled.get()) {
                    activeTokenSink?.invoke(token)
                }
            }
            val id = (result?.get("contextId") as? Number)?.toInt()
                ?: error("No se obtuvo contextId de llama.cpp")
            contextId = id
            SystemClock.elapsedRealtime() - t0
        }
    }

    override suspend fun generateReply(
        systemPrompt: String,
        history: List<ChatMessage>,
        onToken: (String) -> Unit
    ): LlmGenerationResult = mutex.withLock {
        withContext(llamaDispatcher) {
            val activeId = contextId ?: error("El modelo LLM no está cargado en memoria")
            cancelled.set(false)

            val prompt = ChatPromptFormatter.formatChatMl(systemPrompt, history)
            val startMs = SystemClock.elapsedRealtime()
            var firstTokenMs = -1L
            val tokenCounter = AtomicInteger(0)
            val buffer = StringBuilder()

            activeTokenSink = { rawToken ->
                if (!cancelled.get() && rawToken.isNotEmpty()) {
                    if (firstTokenMs < 0L) {
                        firstTokenMs = SystemClock.elapsedRealtime() - startMs
                    }
                    tokenCounter.incrementAndGet()
                    buffer.append(rawToken)
                    val filtered = filterStopFragment(rawToken)
                    if (filtered.isNotEmpty()) {
                        onToken(filtered)
                    }
                }
            }

            try {
                val params = mapOf(
                    "prompt" to prompt,
                    "temperature" to TEMPERATURE,
                    "top_k" to 40,
                    "top_p" to TOP_P,
                    "min_p" to 0.05,
                    "n_threads" to THREADS_LOAD_AND_GEN,
                    "n_predict" to MAX_PREDICT_TOKENS,
                    "penalty_last_n" to 64,
                    "penalty_repeat" to REPEAT_PENALTY,
                    "emit_partial_completion" to true,
                    "stop" to ChatPromptFormatter.STOP_TOKENS
                )

                val completionResult = llamaAndroid.launchCompletion(activeId, params)
                val totalMs = SystemClock.elapsedRealtime() - startMs
                val rawOut = if (buffer.isNotEmpty()) {
                    buffer.toString()
                } else {
                    (completionResult?.get("text") as? String).orEmpty()
                }
                val cleaned = ChatPromptFormatter.cleanAssistantText(rawOut)
                val count = tokenCounter.get().let {
                    if (it > 0) it else (completionResult?.get("tokens_predicted") as? Number)?.toInt() ?: 0
                }
                LlmGenerationResult(
                    text = cleaned,
                    firstTokenMs = if (firstTokenMs >= 0L) firstTokenMs else totalMs,
                    totalMs = totalMs,
                    tokensGenerated = count,
                    cancelled = cancelled.get()
                )
            } finally {
                activeTokenSink = null
            }
        }
    }

    override fun cancelGeneration() {
        cancelled.set(true)
        activeTokenSink = null
        val activeId = contextId ?: return
        controlScope.launch {
            try {
                llamaAndroid.stopCompletion(activeId)
            } catch (_: Exception) {
            }
        }
    }

    override fun unload() {
        cancelled.set(true)
        activeTokenSink = null
        val idToRelease = contextId
        contextId = null
        if (idToRelease != null && !llamaExecutor.isShutdown) {
            llamaExecutor.execute {
                try {
                    llamaAndroid.releaseContext(idToRelease)
                } catch (_: Exception) {
                }
            }
        }
    }

    fun shutdown() {
        unload()
        if (!llamaExecutor.isShutdown) {
            llamaExecutor.execute {
                llamaExecutor.shutdown()
            }
        }
    }

    private fun filterStopFragment(token: String): String {
        for (stop in ChatPromptFormatter.STOP_TOKENS) {
            if (token == stop || token.contains("<|im_")) {
                return token.substringBefore("<|")
            }
        }
        return token
    }
}
