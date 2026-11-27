package com.orion.voicelab

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory

class VoiceLabUnitTest {

    @Test
    fun `asr stats calculates average decode time and real time factor`() {
        var stats = AsrStats()
        assertEquals("Frases reconocidas: 0", stats.summaryLine())

        stats = stats.withSample(audio = 2_000L, decode = 500L)
        stats = stats.withSample(audio = 4_000L, decode = 700L)

        assertEquals(2, stats.count)
        assertEquals(6_000L, stats.audioMs)
        assertEquals(1_200L, stats.decodeMs)
        assertEquals(600L, stats.avgDecodeMs)
        assertEquals(700L, stats.maxDecodeMs)
        assertEquals(0.20, stats.rtf, 0.001)
    }

    @Test
    fun `silero vad asset exists with expected size and sha256`() {
        val assetFile = listOf(
            File("src/main/assets/${OrionVoiceEngine.VAD_ASSET}"),
            File("app/src/main/assets/${OrionVoiceEngine.VAD_ASSET}")
        ).firstOrNull { it.isFile }
        assertTrue("silero_vad.onnx debe existir en assets", assetFile != null)

        val bytes = assetFile!!.readBytes()
        assertEquals(643_854, bytes.size)

        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }
        assertEquals(
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
            hex
        )
    }

    @Test
    fun `sherpa onnx moonshine and vad configs instantiate cleanly`() {
        val moonshine = OfflineMoonshineModelConfig(
            encoder = "/tmp/encoder_model.ort",
            mergedDecoder = "/tmp/decoder_model_merged.ort"
        )
        val modelConfig = OfflineModelConfig(
            moonshine = moonshine,
            tokens = "/tmp/tokens.txt",
            numThreads = 2,
            debug = false,
            provider = "cpu"
        )
        val recognizerConfig = OfflineRecognizerConfig(
            modelConfig = modelConfig,
            decodingMethod = "greedy_search"
        )
        assertEquals("/tmp/encoder_model.ort", recognizerConfig.modelConfig.moonshine.encoder)
        assertEquals("/tmp/decoder_model_merged.ort", recognizerConfig.modelConfig.moonshine.mergedDecoder)
        assertEquals("greedy_search", recognizerConfig.decodingMethod)

        val vadConfig = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = OrionVoiceEngine.VAD_ASSET,
                threshold = OrionVoiceEngine.VAD_THRESHOLD,
                minSilenceDuration = OrionVoiceEngine.VAD_MIN_SILENCE_S,
                minSpeechDuration = OrionVoiceEngine.VAD_MIN_SPEECH_S,
                windowSize = OrionVoiceEngine.VAD_WINDOW,
                maxSpeechDuration = OrionVoiceEngine.VAD_MAX_SPEECH_S
            ),
            sampleRate = OrionVoiceEngine.SAMPLE_RATE,
            numThreads = 1,
            provider = "cpu",
            debug = false
        )
        assertEquals(16_000, vadConfig.sampleRate)
        assertEquals(512, vadConfig.sileroVadModelConfig.windowSize)
        assertEquals(0.5f, vadConfig.sileroVadModelConfig.minSilenceDuration, 0.0001f)
    }

    @Test
    fun `model manager extracts allowed files safely and ignores unexpected entries`() {
        val rootDir = createTempDirectory("orion_model_test").toFile()
        try {
            val archive = File(rootDir, "test_model.tar.bz2")
            FileOutputStream(archive).use { fos ->
                BZip2CompressorOutputStream(BufferedOutputStream(fos)).use { bz ->
                    TarArchiveOutputStream(bz).use { tar ->
                        fun addFile(entryName: String, content: ByteArray) {
                            val entry = TarArchiveEntry(entryName)
                            entry.size = content.size.toLong()
                            tar.putArchiveEntry(entry)
                            tar.write(content)
                            tar.closeArchiveEntry()
                        }

                        addFile("model/encoder_model.ort", ByteArray(1_100_000) { 1 })
                        addFile("model/decoder_model_merged.ort", ByteArray(1_100_000) { 2 })
                        addFile("model/tokens.txt", ByteArray(2_000) { 3 })
                        addFile("model/LICENSE", "Moonshine AI Community License".toByteArray())
                        addFile("model/../../evil.sh", "rm -rf /".toByteArray())
                    }
                }
            }

            val filesDir = File(rootDir, "files").apply { mkdirs() }
            assertFalse(MoonshineModelManager.modelFilesPresent(filesDir))

            MoonshineModelManager.extractModelArchive(archive, filesDir)

            assertTrue(MoonshineModelManager.modelFilesPresent(filesDir))
            val modelDir = MoonshineModelManager.modelDirectory(filesDir)
            assertTrue(File(modelDir, "LICENSE").isFile)
            assertFalse(File(rootDir, "evil.sh").exists())
            assertFalse(File(modelDir, "evil.sh").exists())
            assertTrue(MoonshineModelManager.modelSizeMb(filesDir) >= 2L)
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `device metrics formatting handles NaN temperature cleanly`() {
        assertEquals("n/d", DeviceMetrics.formatTemp(Float.NaN))
        assertTrue(DeviceMetrics.formatTemp(32.5f).contains("32"))
    }
}
