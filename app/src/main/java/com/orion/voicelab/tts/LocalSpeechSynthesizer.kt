package com.orion.voicelab.tts

import com.orion.voicelab.data.VoiceProfile

data class SynthesisResult(
    val synthMs: Long,
    val audioDurationMs: Long
)

interface LocalSpeechSynthesizer {
    val engineDisplayName: String
    val engineLicense: String

    fun availableVoices(): List<VoiceProfile>
    fun selectedVoice(): VoiceProfile
    fun selectVoice(voiceId: String)

    fun isModelInstalled(): Boolean
    fun isLoaded(): Boolean
    fun modelSizeMb(): Long

    suspend fun load(): Long

    /**
     * Synthesizes text into PCM float audio chunks.
     * The callback returns true to continue or false to abort synthesis immediately.
     */
    suspend fun synthesize(
        text: String,
        onChunk: (samples: FloatArray, sampleRate: Int) -> Boolean
    ): SynthesisResult

    fun cancel()
    fun unload()
}
