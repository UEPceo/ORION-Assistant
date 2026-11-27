package com.orion.voicelab.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean

interface AudioOutputSink {
    val isPlaying: Boolean
    fun setPlaybackStateListener(listener: ((Boolean) -> Unit)?)
    fun beginSession(sampleRate: Int)
    fun writeSamples(samples: FloatArray, sampleRate: Int): Boolean
    fun finishSessionGracefully()
    fun stopImmediately()
    fun release()
}

class AudioPlaybackEngine(
    onPlaybackStateChanged: ((Boolean) -> Unit)? = null
) : AudioOutputSink {

    private val lock = Any()
    private val cancelled = AtomicBoolean(false)
    private val playing = AtomicBoolean(false)

    @Volatile
    private var playbackStateListener: ((Boolean) -> Unit)? = onPlaybackStateChanged

    private var audioTrack: AudioTrack? = null
    private var currentSampleRate: Int = 0

    override val isPlaying: Boolean get() = playing.get()

    override fun setPlaybackStateListener(listener: ((Boolean) -> Unit)?) {
        playbackStateListener = listener
    }

    override fun beginSession(sampleRate: Int) {
        cancelled.set(false)
        synchronized(lock) {
            if (audioTrack != null && currentSampleRate == sampleRate) {
                try {
                    audioTrack?.pause()
                    audioTrack?.flush()
                    audioTrack?.play()
                    setPlayingState(true)
                    return
                } catch (_: Exception) {
                    releaseLocked()
                }
            } else {
                releaseLocked()
            }

            val rawMin = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_FLOAT
            )
            val minBuf = if (rawMin <= 0) sampleRate else rawMin.coerceAtLeast(sampleRate / 2)

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            audioTrack = track
            currentSampleRate = sampleRate
            setPlayingState(true)
        }
    }

    /**
     * Writes synthesized PCM float samples synchronously to AudioTrack.
     * Returns false if playback was cancelled mid-stream.
     */
    override fun writeSamples(samples: FloatArray, sampleRate: Int): Boolean {
        if (cancelled.get() || samples.isEmpty()) return !cancelled.get()

        val track = synchronized(lock) {
            if (cancelled.get()) return false
            if (audioTrack == null || currentSampleRate != sampleRate) {
                beginSession(sampleRate)
            }
            audioTrack
        } ?: return false

        // Soft clip to avoid distortion on speaker
        val safe = FloatArray(samples.size) { i -> samples[i].coerceIn(-0.98f, 0.98f) }
        var offset = 0
        while (offset < safe.size && !cancelled.get()) {
            val chunk = minOf(2048, safe.size - offset)
            val written = try {
                track.write(safe, offset, chunk, AudioTrack.WRITE_BLOCKING)
            } catch (_: Exception) {
                return false
            }
            if (written <= 0) break
            offset += written
        }
        return !cancelled.get()
    }

    override fun finishSessionGracefully() {
        synchronized(lock) {
            try {
                if (!cancelled.get()) {
                    audioTrack?.stop()
                }
            } catch (_: Exception) {
            } finally {
                setPlayingState(false)
            }
        }
    }

    override fun stopImmediately() {
        cancelled.set(true)
        setPlayingState(false)
        synchronized(lock) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
                audioTrack?.stop()
            } catch (_: Exception) {
            }
        }
    }

    override fun release() {
        cancelled.set(true)
        setPlayingState(false)
        synchronized(lock) {
            releaseLocked()
        }
    }

    private fun setPlayingState(active: Boolean) {
        val prev = playing.getAndSet(active)
        if (prev != active) {
            playbackStateListener?.invoke(active)
        }
    }

    private fun releaseLocked() {
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.stop()
        } catch (_: Exception) {
        }
        try {
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        currentSampleRate = 0
    }
}

