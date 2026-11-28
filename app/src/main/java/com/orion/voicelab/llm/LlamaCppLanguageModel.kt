package com.orion.voicelab.llm

import android.content.ContentResolver
import android.content.Context
import android.app.ActivityManager
import android.net.Uri
import android.os.SystemClock
import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.model.ProfileModelArtifact
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object ChatPromptFormatter {

    val STOP_TOKENS = arrayOf("<|im_end|>", "<|endoftext|>", "<|im_start|>")

    fun formatChatMl(
        systemPrompt: String,
        history: List<ChatMessage>,
        maxTurns: Int = 10,
        maxContextTokens: Int = 2048
    ): String {
        val promptBudget = ContextWindowBudgetPolicy.promptChars(maxContextTokens)
        val safeSystem = systemPrompt.trim()
        require(safeSystem.length + SYSTEM_OVERHEAD_CHARS <= promptBudget) {
            "El prompt de sistema supera el presupuesto del contexto efectivo; reduce contexto antes de generar."
        }
        val selected = mutableListOf<ChatMessage>()
        var usedChars = safeSystem.length + SYSTEM_OVERHEAD_CHARS
        for (message in history.takeLast(maxTurns).asReversed()) {
            val overhead = MESSAGE_OVERHEAD_CHARS
            val available = promptBudget - usedChars - overhead
            if (available <= 0) break
            val content = if (message.content.length <= available) {
                message.content
            } else if (selected.isEmpty()) {
                truncateMiddle(message.content, available)
            } else {
                break
            }
            selected += message.copy(content = content)
            usedChars += content.length + overhead
            if (content.length < message.content.length) break
        }
        return buildString {
            append("<|im_start|>system\n")
            append(safeSystem)
            append("<|im_end|>\n")
            for (msg in selected.asReversed()) {
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

    private fun truncateMiddle(value: String, maxChars: Int): String {
        if (value.length <= maxChars) return value
        if (maxChars <= 32) return value.take(maxChars)
        val marker = "\n[CONTENIDO RECORTADO]\n"
        val remaining = maxChars - marker.length
        val head = (remaining * 0.65).toInt()
        return value.take(head) + marker + value.takeLast(remaining - head)
    }

    private const val SYSTEM_OVERHEAD_CHARS = 32
    private const val MESSAGE_OVERHEAD_CHARS = 32

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

internal interface LlamaRuntimeBridge {
    fun openModelDescriptor(modelFile: File): Pair<String, Int>
    suspend fun startEngine(params: Map<String, Any>, onToken: (String) -> Unit): Map<String, Any>?
    suspend fun launchCompletion(contextId: Int, params: Map<String, Any>): Map<String, Any>?
    suspend fun stopCompletion(contextId: Int)
    suspend fun releaseContext(contextId: Int)
}

internal class AndroidLlamaRuntimeBridge(
    private val contentResolver: ContentResolver
) : LlamaRuntimeBridge {

    private val llamaAndroid by lazy { LlamaAndroid(contentResolver) }

    override fun openModelDescriptor(modelFile: File): Pair<String, Int> {
        val uri = Uri.fromFile(modelFile)
        val pfd = contentResolver.openFileDescriptor(uri, "r")
            ?: error("No se pudo abrir el descriptor del archivo GGUF")
        return uri.toString() to pfd.detachFd()
    }

    override suspend fun startEngine(
        params: Map<String, Any>,
        onToken: (String) -> Unit
    ): Map<String, Any>? = llamaAndroid.startEngine(params, onToken)

    override suspend fun launchCompletion(
        contextId: Int,
        params: Map<String, Any>
    ): Map<String, Any>? = llamaAndroid.launchCompletion(contextId, params)

    override suspend fun stopCompletion(contextId: Int) {
        llamaAndroid.stopCompletion(contextId)
    }

    override suspend fun releaseContext(contextId: Int) {
        llamaAndroid.releaseContext(contextId)
    }
}

class LlamaCppLanguageModel internal constructor(
    private val modelFile: File,
    private val bridge: LlamaRuntimeBridge,
    private val modelInstalledCheck: (File) -> Boolean = { file ->
        file.isFile && ModelManager.hasGgufMagicHeader(file)
    },
    private val clockMs: () -> Long = { SystemClock.elapsedRealtime() },
    private val memorySnapshotProvider: () -> ModelMemorySnapshot = {
        ModelMemorySnapshot(Long.MAX_VALUE, Long.MAX_VALUE)
    },
    private val contextTokens: Int = 2048,
    private val batchSize: Int = 256,
    private val threadCount: Int = LlamaThreadPolicy.recommend(Runtime.getRuntime().availableProcessors()),
    override val profileId: String = "lite",
    override val modelDisplayName: String = "Qwen2.5-1.5B-Instruct (Q4_K_M)",
    override val modelLicense: String = "Apache 2.0 (Modelo y llama.cpp / llamacpp-kotlin)"
) : LocalLanguageModel {

    constructor(
        context: Context,
        modelFile: File
    ) : this(
        modelFile = modelFile,
        bridge = AndroidLlamaRuntimeBridge(context.contentResolver),
        memorySnapshotProvider = {
            val activityManager = context.applicationContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            ModelMemorySnapshot(memoryInfo.totalMem, memoryInfo.availMem)
        }
    )

    constructor(contentResolver: ContentResolver, modelFile: File) : this(
        modelFile = modelFile,
        bridge = AndroidLlamaRuntimeBridge(contentResolver)
    )

    companion object {
        fun forProfile(context: Context, filesDir: File, artifact: ProfileModelArtifact): LlamaCppLanguageModel {
            val artifactVerified = AtomicBoolean(false)
            val appContext = context.applicationContext
            val memoryProvider = {
                val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val info = ActivityManager.MemoryInfo()
                manager.getMemoryInfo(info)
                ModelMemorySnapshot(info.totalMem, info.availMem)
            }
            return LlamaCppLanguageModel(
                modelFile = ModelManager.profileModelFile(filesDir, artifact.profileId),
                bridge = AndroidLlamaRuntimeBridge(appContext.contentResolver),
                modelInstalledCheck = { file ->
                    if (artifactVerified.get()) file.isFile && ModelManager.hasGgufMagicHeader(file)
                    else ModelManager.isProfileLlmInstalled(filesDir, artifact.profileId).also { valid ->
                        if (valid) artifactVerified.set(true)
                    }
                },
                memorySnapshotProvider = memoryProvider,
                contextTokens = artifact.contextTokens,
                batchSize = 128,
                threadCount = LlamaThreadPolicy.recommend(Runtime.getRuntime().availableProcessors()),
                profileId = artifact.profileId,
                modelDisplayName = artifact.modelId,
                modelLicense = artifact.license
            )
        }
        private const val MAX_PREDICT_TOKENS = 180
        private const val TEMPERATURE = 0.65
        private const val TOP_P = 0.90
        private const val REPEAT_PENALTY = 1.12
    }

    private val llamaExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OrionLlamaCpp").apply { isDaemon = true }
    }
    private val llamaDispatcher = llamaExecutor.asCoroutineDispatcher()
    private val controlScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val contextIdRef = AtomicReference<Int?>(null)
    private val effectiveContextTokens = AtomicInteger(0)
    override val contextWindowTokens: Int
        get() = effectiveContextTokens.get().takeIf { it > 0 } ?: contextTokens
    private val cancelled = AtomicBoolean(false)
    private val generating = AtomicBoolean(false)
    private val unloadRequested = AtomicBoolean(false)
    private val destroyed = AtomicBoolean(false)
    private val generationEpoch = AtomicLong(0L)

    @Volatile
    private var activeTokenSink: ((String) -> Unit)? = null

    override fun isModelInstalled(): Boolean = modelInstalledCheck(modelFile)

    override fun isLoaded(): Boolean = contextIdRef.get() != null

    override fun modelSizeMb(): Long =
        if (modelFile.isFile) modelFile.length() / (1024L * 1024L) else 0L

    override suspend fun load(): Long = mutex.withLock {
        if (destroyed.get()) error("El motor LLM ya fue cerrado")
        unloadRequested.set(false)
        try {
            withContext(llamaDispatcher) {
                if (destroyed.get()) error("El motor LLM ya fue cerrado")
                if (contextIdRef.get() != null) return@withContext 0L
                val memorySnapshot = memorySnapshotProvider()
                val selectedContextTokens = ModelMemoryPolicy.chooseSafeContextTokens(
                    memorySnapshot,
                    modelFile.length(),
                    contextTokens
                )
                check(selectedContextTokens > 0) {
                    ModelMemoryPolicy.evaluate(memorySnapshot, modelFile.length(), contextTokens).reason
                }

                check(isModelInstalled()) {
                    "El modelo LLM local no está instalado o está corrupto."
                }

                val t0 = clockMs()
                val (modelUri, fd) = bridge.openModelDescriptor(modelFile)

                val params = mapOf(
                    "model" to modelUri,
                    "model_fd" to fd,
                    "use_mmap" to true,
                    "use_mlock" to false,
                    "n_ctx" to selectedContextTokens,
                    "n_batch" to batchSize,
                    "n_threads" to threadCount,
                    "n_gpu_layers" to 0
                )

                val result = try {
                    bridge.startEngine(params) { token ->
                        if (!cancelled.get()) activeTokenSink?.invoke(token)
                    }
                } catch (memoryError: OutOfMemoryError) {
                    System.gc()
                    error("Memoria insuficiente al inicializar llama.cpp. Se ha rechazado la carga para proteger ORION.")
                }

                val nativeError = result?.get("error")?.toString()?.takeIf { it.isNotBlank() }
                val id = (result?.get("contextId") as? Number)?.toInt()

                if (nativeError != null) {
                    if (id != null) {
                        try {
                            bridge.releaseContext(id)
                        } catch (_: Exception) {
                        }
                    }
                    error("Error nativo al iniciar llama.cpp: $nativeError")
                }

                if (id == null) {
                    error("No se obtuvo contextId de llama.cpp")
                }

                if (unloadRequested.get() || destroyed.get()) {
                    try {
                        bridge.releaseContext(id)
                    } catch (_: Exception) {
                    }
                    error("Carga LLM cancelada al liberar memoria")
                }

                if (!contextIdRef.compareAndSet(null, id)) {
                    try {
                        bridge.releaseContext(id)
                    } catch (_: Exception) {
                    }
                    return@withContext 0L
                }

                effectiveContextTokens.set(selectedContextTokens)
                clockMs() - t0
            }
        } catch (e: RejectedExecutionException) {
            error("El motor LLM ya fue cerrado")
        }
    }

    override suspend fun generateReply(
        systemPrompt: String,
        history: List<ChatMessage>,
        onToken: (String) -> Unit
    ): LlmGenerationResult {
        val callEpoch = generationEpoch.get()
        if (destroyed.get()) {
            return LlmGenerationResult("", 0L, 0L, 0, cancelled = true)
        }
        return mutex.withLock {
            if (destroyed.get() || unloadRequested.get() || generationEpoch.get() != callEpoch) {
                return@withLock LlmGenerationResult("", 0L, 0L, 0, cancelled = true)
            }
            try {
                withContext(llamaDispatcher) {
                    val activeId = contextIdRef.get()
                        ?: error("El modelo LLM no está cargado en memoria")
                    if (destroyed.get() || unloadRequested.get() || generationEpoch.get() != callEpoch) {
                        return@withContext LlmGenerationResult(
                            text = "",
                            firstTokenMs = 0L,
                            totalMs = 0L,
                            tokensGenerated = 0,
                            cancelled = true
                        )
                    }

                    cancelled.set(false)
                    generating.set(true)

                    val prompt = ChatPromptFormatter.formatChatMl(systemPrompt, history, maxContextTokens = contextWindowTokens)
                    val startMs = clockMs()
                    var firstTokenMs = -1L
                    val tokenCounter = AtomicInteger(0)
                    val buffer = StringBuilder()

                    activeTokenSink = { rawToken ->
                        if (!cancelled.get() &&
                            !destroyed.get() &&
                            generationEpoch.get() == callEpoch &&
                            rawToken.isNotEmpty()
                        ) {
                            if (firstTokenMs < 0L) {
                                firstTokenMs = clockMs() - startMs
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
                        "n_threads" to threadCount,
                        "n_predict" to MAX_PREDICT_TOKENS,
                        "penalty_last_n" to 64,
                        "penalty_repeat" to REPEAT_PENALTY,
                        "emit_partial_completion" to true,
                        "stop" to ChatPromptFormatter.STOP_TOKENS
                    )

                    val completionResult = bridge.launchCompletion(activeId, params)
                    val totalMs = clockMs() - startMs
                    val nativeError = completionResult?.get("error")?.toString()?.takeIf { it.isNotBlank() }
                    if (nativeError != null && !cancelled.get()) {
                        error("Error nativo en llama.cpp: $nativeError")
                    }

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
                        cancelled = cancelled.get() || generationEpoch.get() != callEpoch
                    )
                } finally {
                    generating.set(false)
                    activeTokenSink = null
                }
                }
            } catch (_: RejectedExecutionException) {
                LlmGenerationResult("", 0L, 0L, 0, cancelled = true)
            }
        }
    }

    override fun cancelGeneration() {
        generationEpoch.incrementAndGet()
        cancelled.set(true)
        activeTokenSink = null
        val activeId = contextIdRef.get() ?: return
        if (generating.get() && !destroyed.get()) {
            controlScope.launch {
                try {
                    bridge.stopCompletion(activeId)
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun unload() {
        unloadRequested.set(true)
        generationEpoch.incrementAndGet()
        cancelled.set(true)
        activeTokenSink = null
        val idToRelease = contextIdRef.getAndSet(null) ?: return
        effectiveContextTokens.set(0)

        if (generating.get() && !destroyed.get()) {
            controlScope.launch {
                try {
                    bridge.stopCompletion(idToRelease)
                } catch (_: Exception) {
                }
            }
        }

        if (!llamaExecutor.isShutdown) {
            try {
                llamaExecutor.execute {
                    kotlinx.coroutines.runBlocking {
                        try {
                            bridge.releaseContext(idToRelease)
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: RejectedExecutionException) {
            }
        }
    }

    fun shutdown() {
        if (!destroyed.compareAndSet(false, true)) return
        unloadRequested.set(true)
        generationEpoch.incrementAndGet()
        cancelled.set(true)
        activeTokenSink = null
        val wasGenerating = generating.get()
        val idToRelease = contextIdRef.getAndSet(null)
        effectiveContextTokens.set(0)

        if (idToRelease != null && wasGenerating) {
            controlScope.launch {
                try {
                    bridge.stopCompletion(idToRelease)
                } catch (_: Exception) {
                }
            }
        }

        if (!llamaExecutor.isShutdown) {
            try {
                llamaExecutor.execute {
                    if (idToRelease != null) {
                        kotlinx.coroutines.runBlocking {
                            try {
                                bridge.releaseContext(idToRelease)
                            } catch (_: Exception) {
                            }
                        }
                    }
                    controlScope.cancel()
                    llamaExecutor.shutdown()
                }
            } catch (_: RejectedExecutionException) {
                controlScope.cancel()
            }
        } else {
            controlScope.cancel()
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

