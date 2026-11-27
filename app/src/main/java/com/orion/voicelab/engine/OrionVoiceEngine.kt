package com.orion.voicelab.engine

import android.annotation.SuppressLint
import android.content.res.AssetManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

class OrionVoiceEngine {

    companion object {
        const val SAMPLE_RATE = 16_000
        const val VAD_ASSET = "silero_vad.onnx"
        const val VAD_WINDOW = 512            // muestras por ventana a 16 kHz (32 ms)
        const val VAD_THRESHOLD = 0.5f
        const val VAD_MIN_SILENCE_S = 0.5f    // pausa que cierra la frase
        const val VAD_MIN_SPEECH_S = 0.25f
        const val VAD_MAX_SPEECH_S = 12.0f
        const val MIN_SEGMENT_SAMPLES = SAMPLE_RATE / 4
    }

    interface Listener {
        fun onModelsLoaded(loadTimeMs: Long)
        fun onListeningStarted()
        fun onSpeechStateChanged(detected: Boolean)
        fun onAudioLevel(level: Float)
        fun onTranscriptReady(text: String, audioMs: Long, decodeMs: Long)
        fun onError(message: String)
        fun onStopped()
    }

    private val running = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    @Volatile private var destroyed = false

    // Hilo único y "daemon" para decodificar; las liberaciones del reconocedor pasan por él
    // para no liberar el modelo mientras se decodifica una frase.
    private val asrExecutor = Executors.newSingleThreadExecutor { r ->
        Thread(r, "OrionAsr").apply { isDaemon = true }
    }

    @Volatile private var recognizer: OfflineRecognizer? = null
    @Volatile private var recorder: AudioRecord? = null

    val isRunning: Boolean get() = running.get()
    val isBusy: Boolean get() = busy.get()

    @SuppressLint("MissingPermission")
    fun start(
        assets: AssetManager,
        modelDirectory: File,
        listener: Listener
    ): Boolean {
        if (destroyed || !busy.compareAndSet(false, true)) return false

        thread(name = "OrionCapture") {
            var audioRecord: AudioRecord? = null
            var vad: Vad? = null
            var activeRecognizer: OfflineRecognizer? = null
            try {
                val loadStart = SystemClock.elapsedRealtime()
                activeRecognizer = createRecognizer(modelDirectory)
                recognizer = activeRecognizer
                vad = createVad(assets)
                val loadTimeMs = SystemClock.elapsedRealtime() - loadStart
                listener.onModelsLoaded(loadTimeMs)

                val minBuffer = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBuffer > 0) { "No se pudo obtener el búfer de audio" }

                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer * 2, VAD_WINDOW * 8)
                )
                check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    "No se pudo inicializar el micrófono"
                }
                recorder = audioRecord
                if (destroyed) return@thread

                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                running.set(true)
                audioRecord.startRecording()
                listener.onListeningStarted()

                captureLoop(audioRecord, vad, activeRecognizer, listener)
            } catch (e: SecurityException) {
                listener.onError("Permiso de micrófono requerido: ${e.message ?: "denegado"}")
            } catch (e: Exception) {
                listener.onError(e.message ?: "error desconocido")
            } finally {
                running.set(false)
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                recorder = null
                try { vad?.release() } catch (_: Exception) {}
                val toRelease = activeRecognizer ?: recognizer
                recognizer = null
                releaseRecognizerAfterPendingWork(toRelease, shutdownOnComplete = destroyed)
                busy.set(false)
                listener.onStopped()
            }
        }
        return true
    }

    fun stop() {
        running.set(false)
    }

    fun destroy() {
        destroyed = true
        running.set(false)
        if (!busy.get()) {
            val toRelease = recognizer
            recognizer = null
            releaseRecognizerAfterPendingWork(toRelease, shutdownOnComplete = true)
        }
    }

    private fun createRecognizer(modelDirectory: File): OfflineRecognizer {
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
        return OfflineRecognizer(config = config)
    }

    private fun createVad(assets: AssetManager): Vad {
        val config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = VAD_ASSET,
                threshold = VAD_THRESHOLD,
                minSilenceDuration = VAD_MIN_SILENCE_S,
                minSpeechDuration = VAD_MIN_SPEECH_S,
                windowSize = VAD_WINDOW,
                maxSpeechDuration = VAD_MAX_SPEECH_S
            ),
            sampleRate = SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
        return Vad(assets, config)
    }

    private fun captureLoop(
        audioRecord: AudioRecord,
        vad: Vad,
        activeRecognizer: OfflineRecognizer,
        listener: Listener
    ) {
        val shortBuffer = ShortArray(VAD_WINDOW)
        val floatBuffer = FloatArray(VAD_WINDOW)
        var lastSpeech = false
        var levelTick = 0
        var lastLevel = 0f

        while (running.get()) {
            val count = audioRecord.read(shortBuffer, 0, shortBuffer.size)
            if (count < 0) error("Error de lectura del micrófono ($count)")
            if (count == 0) continue

            var sumSq = 0.0
            for (i in 0 until count) {
                val sample = shortBuffer[i] / 32768.0f
                floatBuffer[i] = sample
                sumSq += sample * sample
            }

            if (++levelTick % 3 == 0) {
                val rms = sqrt(sumSq / count).toFloat()
                val normalized = (rms * 4.5f).coerceIn(0f, 1f)
                if (abs(normalized - lastLevel) >= 0.03f || (normalized == 0f && lastLevel != 0f)) {
                    lastLevel = normalized
                    listener.onAudioLevel(normalized)
                }
            }

            vad.acceptWaveform(if (count == VAD_WINDOW) floatBuffer else floatBuffer.copyOf(count))
            drainVad(vad, activeRecognizer, listener)

            val speech = vad.isSpeechDetected()
            if (speech != lastSpeech) {
                lastSpeech = speech
                listener.onSpeechStateChanged(speech)
            }
        }
        vad.flush()
        drainVad(vad, activeRecognizer, listener)
    }

    private fun drainVad(
        vad: Vad,
        activeRecognizer: OfflineRecognizer,
        listener: Listener
    ) {
        while (!vad.empty()) {
            val segment = vad.front()
            vad.pop()
            if (segment.samples.size >= MIN_SEGMENT_SAMPLES) {
                enqueueRecognition(segment.samples, activeRecognizer, listener)
            }
        }
    }

    private fun enqueueRecognition(
        samples: FloatArray,
        activeRecognizer: OfflineRecognizer,
        listener: Listener
    ) {
        val audioMs = samples.size * 1000L / SAMPLE_RATE
        try {
            asrExecutor.execute {
                val t0 = SystemClock.elapsedRealtime()
                try {
                    val stream = activeRecognizer.createStream()
                    try {
                        stream.acceptWaveform(samples, SAMPLE_RATE)
                        activeRecognizer.decode(stream)
                        val text = activeRecognizer.getResult(stream).text.trim()
                        val decodeMs = SystemClock.elapsedRealtime() - t0
                        listener.onTranscriptReady(text, audioMs, decodeMs)
                    } finally {
                        try { stream.release() } catch (_: Exception) {}
                    }
                } catch (e: Exception) {
                    listener.onError("Error de reconocimiento: ${e.message ?: "desconocido"}")
                }
            }
        } catch (_: RejectedExecutionException) {
            // El ejecutor ya no acepta trabajo; se descarta la frase.
        }
    }

    private fun releaseRecognizerAfterPendingWork(
        toRelease: OfflineRecognizer?,
        shutdownOnComplete: Boolean
    ) {
        try {
            asrExecutor.execute {
                try { toRelease?.release() } catch (_: Exception) {}
                if (shutdownOnComplete) {
                    asrExecutor.shutdown()
                }
            }
        } catch (_: RejectedExecutionException) {
            try { toRelease?.release() } catch (_: Exception) {}
            if (shutdownOnComplete) {
                asrExecutor.shutdown()
            }
        }
    }
}
