package com.orion.voicelab.asr

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import java.io.File
import java.util.concurrent.Executors
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

    @Volatile
    private var recognizer: OfflineRecognizer? = null

    override fun isLoaded(): Boolean = recognizer != null

    override suspend fun load(): Long = suspendCancellableCoroutine { cont ->
        asrExecutor.execute {
            try {
                if (recognizer != null) {
                    if (cont.isActive) cont.resume(0L)
                    return@execute
                }
                val t0 = SystemClock.elapsedRealtime()
                val encoder = File(modelDirectory, "encoder_model.ort").absolutePath
                val decoder = File(modelDirectory, "decoder_model_merged.ort").absolutePath
                val tokens = File(modelDirectory, "tokens.txt").absolutePath

                val moonshine = OfflineMoonshineModelConfig(
                    encoder = encoder,
                    mergedDecoder = decoder
                )
                val modelConfig = OfflineModelConfig(
                    moonshine = moonshine,
                    tokens = tokens,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
                val config = OfflineRecognizerConfig(
                    modelConfig = modelConfig,
                    decodingMethod = "greedy_search"
                )
                recognizer = OfflineRecognizer(config = config)
                val elapsed = SystemClock.elapsedRealtime() - t0
                if (cont.isActive) cont.resume(elapsed)
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    override suspend fun transcribe(
        samples: FloatArray,
        sampleRate: Int
    ): AsrRecognitionResult = suspendCancellableCoroutine { cont ->
        val audioMs = samples.size * 1000L / sampleRate
        asrExecutor.execute {
            try {
                val active = recognizer ?: error("El motor ASR no está cargado")
                val t0 = SystemClock.elapsedRealtime()
                val stream = active.createStream()
                stream.acceptWaveform(samples, sampleRate)
                active.decode(stream)
                val text = active.getResult(stream).text.trim()
                stream.release()
                val decodeMs = SystemClock.elapsedRealtime() - t0
                if (cont.isActive) {
                    cont.resume(AsrRecognitionResult(text = text, audioMs = audioMs, decodeMs = decodeMs))
                }
            } catch (e: Exception) {
                if (cont.isActive) cont.resumeWithException(e)
            }
        }
    }

    override fun release() {
        val toRelease = recognizer
        recognizer = null
        if (toRelease != null && !asrExecutor.isShutdown) {
            asrExecutor.execute {
                try {
                    toRelease.release()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun shutdown() {
        val toRelease = recognizer
        recognizer = null
        if (!asrExecutor.isShutdown) {
            asrExecutor.execute {
                try {
                    toRelease?.release()
                } catch (_: Exception) {
                } finally {
                    asrExecutor.shutdown()
                }
            }
        }
    }
}
