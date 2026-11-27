package com.orion.voicelab.audio

import android.content.res.AssetManager
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig

interface VoiceActivityDetector {
    fun acceptWaveform(samples: FloatArray)
    fun isSpeechDetected(): Boolean
    fun drainSegments(minSamples: Int): List<FloatArray>
    fun flush()
    fun reset()
    fun release()
}

class SileroVoiceActivityDetector(
    assets: AssetManager,
    private val sampleRate: Int = SAMPLE_RATE,
    private val threshold: Float = VAD_THRESHOLD,
    private val minSilenceDuration: Float = VAD_MIN_SILENCE_S,
    private val minSpeechDuration: Float = VAD_MIN_SPEECH_S,
    private val windowSize: Int = VAD_WINDOW,
    private val maxSpeechDuration: Float = VAD_MAX_SPEECH_S
) : VoiceActivityDetector {

    companion object {
        const val SAMPLE_RATE = 16_000
        const val VAD_ASSET = "silero_vad.onnx"
        const val VAD_WINDOW = 512
        const val VAD_THRESHOLD = 0.5f
        const val VAD_MIN_SILENCE_S = 0.5f
        const val VAD_MIN_SPEECH_S = 0.25f
        const val VAD_MAX_SPEECH_S = 12.0f
    }

    private val lock = Any()
    private var vad: Vad? = Vad(
        assets,
        VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = VAD_ASSET,
                threshold = threshold,
                minSilenceDuration = minSilenceDuration,
                minSpeechDuration = minSpeechDuration,
                windowSize = windowSize,
                maxSpeechDuration = maxSpeechDuration
            ),
            sampleRate = sampleRate,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
    )

    override fun acceptWaveform(samples: FloatArray) {
        synchronized(lock) {
            vad?.acceptWaveform(samples)
        }
    }

    override fun isSpeechDetected(): Boolean = synchronized(lock) {
        vad?.isSpeechDetected() ?: false
    }

    override fun drainSegments(minSamples: Int): List<FloatArray> = synchronized(lock) {
        val current = vad ?: return emptyList()
        val segments = mutableListOf<FloatArray>()
        while (!current.empty()) {
            val segment = current.front()
            current.pop()
            if (segment.samples.size >= minSamples) {
                segments.add(segment.samples)
            }
        }
        segments
    }

    override fun flush() {
        synchronized(lock) {
            vad?.flush()
        }
    }

    override fun reset() {
        synchronized(lock) {
            vad?.reset()
            vad?.clear()
        }
    }

    override fun release() {
        synchronized(lock) {
            try {
                vad?.release()
            } catch (_: Exception) {
            } finally {
                vad = null
            }
        }
    }
}
