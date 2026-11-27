package com.orion.voicelab.asr

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class AsrRecognitionResult(
    val text: String,
    val audioMs: Long,
    val decodeMs: Long
)

interface SpeechRecognitionEngine {
    fun isLoaded(): Boolean
    suspend fun load(): Long
    suspend fun transcribe(samples: FloatArray, sampleRate: Int): AsrRecognitionResult
    fun release()
}

class MoonshineSpeechRecognitionEngine(
    private val modelDirectory: File
) : SpeechRecognitionEngine {

    private val asrExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OrionMoonshineAsr").apply { isDaemon = true }
    }
    private val destroyed = AtomicBoolean(false)

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    override fun isLoaded(): Boolean = recognizer != null

    override suspend fun load(): Long = suspendCancellableCoroutine { cont ->
        if (destroyed.get()) {
            if (cont.isActive) cont.resumeWithException(IllegalStateException("El motor ASR ya fue cerrado"))
            return@suspendCancellableCoroutine
        }
        try {
            asrExecutor.execute {
                try {
                    if (destroyed.get()) {
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("El motor ASR ya fue cerrado"))
                        return@execute
                    }
                    if (recognizer != null) {
                        if (cont.isActive) cont.resume(0L)
                        return@execute
                    }
                    val encoderFile = File(modelDirectory, "encoder_model.ort")
                    val decoderFile = File(modelDirectory, "decoder_model_merged.ort")
                    val tokensFile = File(modelDirectory, "tokens.txt")
                    check(encoderFile.isFile && decoderFile.isFile && tokensFile.isFile) {
                        "Faltan archivos de Moonshine Base ES en ${modelDirectory.name}"
                    }

                    val t0 = SystemClock.elapsedRealtime()
                    val moonshine = OfflineMoonshineModelConfig(
                        encoder = encoderFile.absolutePath,
                        mergedDecoder = decoderFile.absolutePath
                    )
                    val modelConfig = OfflineModelConfig(
                        moonshine = moonshine,
                        tokens = tokensFile.absolutePath,
                        numThreads = 2,
                        debug = false,
                        provider = "cpu"
                    )
                    val config = OfflineRecognizerConfig(
                        modelConfig = modelConfig,
                        decodingMethod = "greedy_search"
                    )
                    val created = OfflineRecognizer(config = config)
                    if (destroyed.get()) {
                        try {
                            created.release()
                        } catch (_: Exception) {
                        }
                        if (cont.isActive) cont.resumeWithException(IllegalStateException("Carga ASR cancelada al cerrar"))
                        return@execute
                    }
                    recognizer = created
                    val elapsed = SystemClock.elapsedRealtime() - t0
                    if (cont.isActive) cont.resume(elapsed)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        } catch (e: RejectedExecutionException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    override suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int
    ): AsrRecognitionResult = suspendCancellableCoroutine { cont ->
        val audioMs = samples.size * 1000L / sampleRate
        try {
            asrExecutor.execute {
                try {
                    val active = recognizer ?: error("El motor ASR no está cargado")
                    val t0 = SystemClock.elapsedRealtime()
                    val stream = active.createStream()
                    try {
                        stream.acceptWaveform(samples, sampleRate)
                        active.decode(stream)
                        val text = active.getResult(stream).text.trim()
                        val decodeMs = SystemClock.elapsedRealtime() - t0
                        if (cont.isActive) {
                            cont.resume(AsrRecognitionResult(text = text, audioMs = audioMs, decodeMs = decodeMs))
                        }
                    } finally {
                        stream.release()
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resumeWithException(e)
                }
            }
        } catch (e: RejectedExecutionException) {
            if (cont.isActive) cont.resumeWithException(e)
        }
    }

    override fun release() {
        val toRelease = recognizer
        recognizer = null
        if (toRelease != null && !asrExecutor.isShutdown) {
            try {
                asrExecutor.execute {
                    try {
                        toRelease.release()
                    } catch (_: Exception) {
                    }
                }
            } catch (_: RejectedExecutionException) {
            }
        }
    }

    fun shutdown() {
        destroyed.set(true)
        val toRelease = recognizer
        recognizer = null
        if (!asrExecutor.isShutdown) {
            try {
                asrExecutor.execute {
                    try {
                        toRelease?.release()
                    } catch (_: Exception) {
                    } finally {
                        asrExecutor.shutdown()
                    }
                }
            } catch (_: RejectedExecutionException) {
            }
        }
    }
}
