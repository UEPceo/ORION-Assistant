package com.orion.voicelab.tts

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.orion.voicelab.data.VoiceProfile
import com.orion.voicelab.model.ModelManager
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

class SherpaOnnxSpeechSynthesizer(
    private val filesDir: File
) : LocalSpeechSynthesizer {

    companion object {
        val SPANISH_VOICES = listOf(
            VoiceProfile(
                id = "sharvard_f",
                displayName = "Voz Femenina ES",
                speakerId = 0,
                description = "Piper Sharvard es_ES · Locutora 0"
            ),
            VoiceProfile(
                id = "sharvard_m",
                displayName = "Voz Masculina ES",
                speakerId = 1,
                description = "Piper Sharvard es_ES · Locutor 1"
            )
        )
    }

    override val engineDisplayName: String = "Piper VITS es_ES Sharvard (sherpa-onnx)"
    override val engineLicense: String = "MIT (sherpa-onnx / Piper) · Dataset CC-BY 3.0"

    private val ttsExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OrionSherpaTts").apply { isDaemon = true }
    }
    private val ttsDispatcher = ttsExecutor.asCoroutineDispatcher()
    private val cancellationEpoch = AtomicLong(0L)
    private val destroyed = AtomicBoolean(false)

    @Volatile
    private var tts: OfflineTts? = null

    @Volatile
    private var currentVoice: VoiceProfile = SPANISH_VOICES.first()

    override fun availableVoices(): List<VoiceProfile> = SPANISH_VOICES

    override fun selectedVoice(): VoiceProfile = currentVoice

    override fun selectVoice(voiceId: String) {
        val match = SPANISH_VOICES.firstOrNull { it.id == voiceId }
        if (match != null) {
            currentVoice = match
        }
    }

    override fun isModelInstalled(): Boolean = ModelManager.isTtsInstalled(filesDir)

    override fun isLoaded(): Boolean = tts != null

    override fun modelSizeMb(): Long = ModelManager.ttsSizeMb(filesDir)

    override suspend fun load(): Long {
        if (destroyed.get()) error("El motor TTS ya fue cerrado")
        return try {
            withContext(ttsDispatcher) {
                if (destroyed.get()) error("El motor TTS ya fue cerrado")
                if (tts != null) return@withContext 0L
                check(isModelInstalled()) {
                    "El modelo de voz TTS en español no está instalado."
                }
                val t0 = SystemClock.elapsedRealtime()
                val vitsConfig = OfflineTtsVitsModelConfig(
                    model = ModelManager.ttsModelFile(filesDir).absolutePath,
                    tokens = ModelManager.ttsTokensFile(filesDir).absolutePath,
                    dataDir = ModelManager.ttsEspeakDataDir(filesDir).absolutePath,
                    noiseScale = 0.667f,
                    noiseScaleW = 0.8f,
                    lengthScale = 1.0f
                )
                val modelConfig = OfflineTtsModelConfig(
                    vits = vitsConfig,
                    numThreads = 2,
                    debug = false,
                    provider = "cpu"
                )
                val config = OfflineTtsConfig(
                    model = modelConfig,
                    maxNumSentences = 1
                )
                val created = OfflineTts(config = config)
                if (destroyed.get()) {
                    try {
                        created.release()
                    } catch (_: Exception) {
                    }
                    error("Carga TTS cancelada al cerrar la sesión")
                }
                tts = created
                SystemClock.elapsedRealtime() - t0
            }
        } catch (_: RejectedExecutionException) {
            error("El motor TTS ya fue cerrado")
        }
    }

    override suspend fun synthesize(
        text: String,
        onChunk: (samples: FloatArray, sampleRate: Int) -> Boolean
    ): SynthesisResult {
        coroutineContext.ensureActive()
        if (destroyed.get()) error("El motor TTS ya fue cerrado")
        val synthesisEpoch = cancellationEpoch.get()
        return try {
            withContext(ttsDispatcher) {
                coroutineContext.ensureActive()
                if (destroyed.get()) error("El motor TTS ya fue cerrado")
                if (cancellationEpoch.get() != synthesisEpoch) {
                    return@withContext SynthesisResult(0L, 0L)
                }
                val activeTts = tts ?: error("El motor TTS no está cargado")
                val cleanText = text.trim()
                if (cleanText.isEmpty()) return@withContext SynthesisResult(0L, 0L)

                val t0 = SystemClock.elapsedRealtime()
                val sr = activeTts.sampleRate()
                val speakerId = currentVoice.speakerId.coerceIn(0, maxOf(0, activeTts.numSpeakers() - 1))
                var totalSamples = 0L

                activeTts.generateWithCallback(
                    text = cleanText,
                    sid = speakerId,
                    speed = 1.0f
                ) { chunk ->
                    if (cancellationEpoch.get() != synthesisEpoch || destroyed.get()) {
                        0
                    } else {
                        totalSamples += chunk.size.toLong()
                        val keepGoing = onChunk(chunk, sr)
                        if (keepGoing && cancellationEpoch.get() == synthesisEpoch && !destroyed.get()) 1 else 0
                    }
                }
                val synthMs = SystemClock.elapsedRealtime() - t0
                val audioMs = if (sr > 0) (totalSamples * 1000L) / sr else 0L
                SynthesisResult(synthMs = synthMs, audioDurationMs = audioMs)
            }
        } catch (_: RejectedExecutionException) {
            error("El motor TTS ya fue cerrado")
        }
    }

    override fun cancel() {
        cancellationEpoch.incrementAndGet()
    }

    override fun unload() {
        cancellationEpoch.incrementAndGet()
        val toRelease = tts
        tts = null
        if (toRelease != null && !ttsExecutor.isShutdown) {
            try {
                ttsExecutor.execute {
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
        if (!destroyed.compareAndSet(false, true)) return
        unload()
        if (!ttsExecutor.isShutdown) {
            try {
                ttsExecutor.execute {
                    ttsExecutor.shutdown()
                }
            } catch (_: RejectedExecutionException) {
            }
        }
    }
}
