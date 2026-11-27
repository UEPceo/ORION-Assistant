package com.orion.voicelab.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Process
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs
import kotlin.math.sqrt

class VoiceInputEngine(
    private val sampleRate: Int = 16_000,
    private val windowSamples: Int = 512,
    private val useVoiceCommunicationSource: Boolean = true
) {
    interface Listener {
        fun onCaptureStarted()
        fun onAudioFrame(samples: FloatArray, rmsNormalized: Float)
        fun onError(message: String)
        fun onCaptureStopped()
    }

    private val running = AtomicBoolean(false)
    private val muted = AtomicBoolean(false)

    val isRunning: Boolean get() = running.get()
    val isMuted: Boolean get() = muted.get()

    fun setMuted(value: Boolean) {
        muted.set(value)
    }

    @SuppressLint("MissingPermission")
    fun start(listener: Listener): Boolean {
        if (!running.compareAndSet(false, true)) return false

        thread(name = "OrionVoiceInput") {
            var audioRecord: AudioRecord? = null
            var aec: AcousticEchoCanceler? = null
            var ns: NoiseSuppressor? = null
            try {
                val minBuffer = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                check(minBuffer > 0) { "No se pudo obtener el búfer de audio" }

                val source = if (useVoiceCommunicationSource) {
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION
                } else {
                    MediaRecorder.AudioSource.VOICE_RECOGNITION
                }

                audioRecord = AudioRecord(
                    source,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    maxOf(minBuffer * 2, windowSamples * 8)
                )
                check(audioRecord.state == AudioRecord.STATE_INITIALIZED) {
                    "No se pudo inicializar el micrófono"
                }

                if (useVoiceCommunicationSource) {
                    try {
                        if (AcousticEchoCanceler.isAvailable()) {
                            aec = AcousticEchoCanceler.create(audioRecord.audioSessionId)?.apply {
                                enabled = true
                            }
                        }
                    } catch (_: Exception) {}
                    try {
                        if (NoiseSuppressor.isAvailable()) {
                            ns = NoiseSuppressor.create(audioRecord.audioSessionId)?.apply {
                                enabled = true
                            }
                        }
                    } catch (_: Exception) {}
                }

                Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
                audioRecord.startRecording()
                listener.onCaptureStarted()

                val shortBuffer = ShortArray(windowSamples)
                val floatBuffer = FloatArray(windowSamples)
                var levelTick = 0
                var lastReportedLevel = -1f

                while (running.get()) {
                    val count = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                    if (count < 0) error("Error de lectura del micrófono ($count)")
                    if (count == 0) continue

                    val isMicMuted = muted.get()
                    var sumSq = 0.0
                    for (i in 0 until count) {
                        val sample = if (isMicMuted) 0f else shortBuffer[i] / 32768.0f
                        floatBuffer[i] = sample
                        sumSq += sample * sample
                    }

                    val normalized = if (isMicMuted) {
                        0f
                    } else if (++levelTick % 3 == 0) {
                        (sqrt(sumSq / count).toFloat() * 4.5f).coerceIn(0f, 1f)
                    } else {
                        lastReportedLevel.coerceAtLeast(0f)
                    }
                    if (abs(normalized - lastReportedLevel) >= 0.03f || normalized == 0f) {
                        lastReportedLevel = normalized
                    }

                    val frame = if (count == windowSamples) floatBuffer else floatBuffer.copyOf(count)
                    listener.onAudioFrame(frame, lastReportedLevel.coerceAtLeast(0f))
                }
            } catch (e: SecurityException) {
                listener.onError("Permiso de micrófono requerido: ${e.message ?: "denegado"}")
            } catch (e: Exception) {
                listener.onError(e.message ?: "error de captura de audio")
            } finally {
                running.set(false)
                try { aec?.release() } catch (_: Exception) {}
                try { ns?.release() } catch (_: Exception) {}
                try { audioRecord?.stop() } catch (_: Exception) {}
                try { audioRecord?.release() } catch (_: Exception) {}
                listener.onCaptureStopped()
            }
        }
        return true
    }

    fun stop() {
        running.set(false)
    }
}
