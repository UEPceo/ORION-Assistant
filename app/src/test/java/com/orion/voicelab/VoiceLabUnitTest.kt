package com.orion.voicelab

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.orion.voicelab.conversation.ConversationSession
import com.orion.voicelab.conversation.SpeechSentenceChunker
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import com.orion.voicelab.llm.ChatPromptFormatter
import com.orion.voicelab.model.ModelManager
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.nehuatl.llamacpp.LlamaAndroid
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

    @Test
    fun `chat prompt formatter builds ChatML turns and strips markdown and stop tokens`() {
        val history = listOf(
            ChatMessage(ChatRole.USER, "Hola ORION"),
            ChatMessage(ChatRole.ASSISTANT, "Hola, te escucho."),
            ChatMessage(ChatRole.USER, "¿Cómo va todo?")
        )
        val prompt = ChatPromptFormatter.formatChatMl(
            systemPrompt = "Eres ORION.",
            history = history,
            maxTurns = 2
        )
        assertFalse(prompt.contains("Hola ORION"))
        assertTrue(prompt.startsWith("<|im_start|>system\nEres ORION.<|im_end|>"))
        assertTrue(prompt.contains("<|im_start|>assistant\nHola, te escucho.<|im_end|>"))
        assertTrue(prompt.endsWith("<|im_start|>user\n¿Cómo va todo?<|im_end|>\n<|im_start|>assistant\n"))

        val cleaned = ChatPromptFormatter.cleanAssistantText("**Hola** *mundo*<|im_end|>basura")
        assertEquals("Hola mundo", cleaned)
    }

    @Test
    fun `speech sentence chunker splits Spanish sentences and flushes trailing words`() {
        val chunker = SpeechSentenceChunker(minClauseChars = 16, maxClauseChars = 80)
        val emitted = mutableListOf<String>()
        val tokens = listOf("Hola", ", ", "estoy ", "listo. ", "Dime ", "qué ", "necesitas", " hoy")
        for (tok in tokens) {
            emitted.addAll(chunker.appendToken(tok))
        }
        assertEquals(listOf("Hola, estoy listo."), emitted)
        assertEquals("Dime qué necesitas hoy", chunker.flush())
        assertNull(chunker.flush())
    }

    @Test
    fun `conversation session tracks multi-turn context and resets cleanly`() {
        val session = ConversationSession(maxHistoryMessages = 4)
        val u1 = session.addUserMessage("Buenos días", audioMs = 1200L, decodeMs = 210L)
        val latency = TurnLatencyMetrics(
            asrMs = 210L,
            llmFirstTokenMs = 340L,
            llmTotalMs = 1500L,
            ttsSynthMs = 280L,
            endToSpeechStartMs = 890L
        )
        val a1 = session.addAssistantMessage("Buenos días, ¿en qué trabajamos?", latency)
        session.addUserMessage("Revisa el proyecto", audioMs = 1400L, decodeMs = 240L)
        session.addAssistantMessage("Perfecto, dime qué parte revisamos.", latency)
        session.addUserMessage("La voz offline", audioMs = 1100L, decodeMs = 190L)

        assertEquals(1L, u1.id)
        assertEquals(2L, a1.id)
        assertEquals(4, session.historySnapshot().size)
        assertEquals(5, session.turnsSnapshot().size)

        session.reset()
        assertTrue(session.historySnapshot().isEmpty())
        assertTrue(session.turnsSnapshot().isEmpty())
    }

    @Test
    fun `model manager validates GGUF header and extracts TTS archive preserving espeak-ng-data`() {
        val rootDir = createTempDirectory("orion_04_model_test").toFile()
        try {
            val validGguf = File(rootDir, "valid.gguf").apply {
                writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte(), 3, 0, 0, 0))
            }
            val invalidGguf = File(rootDir, "invalid.gguf").apply {
                writeBytes("<!DOCTYPE html>".toByteArray())
            }
            assertTrue(ModelManager.hasGgufMagicHeader(validGguf))
            assertFalse(ModelManager.hasGgufMagicHeader(invalidGguf))

            val ttsArchive = File(rootDir, ModelManager.TTS_ARCHIVE_NAME)
            FileOutputStream(ttsArchive).use { fos ->
                BZip2CompressorOutputStream(BufferedOutputStream(fos)).use { bz ->
                    TarArchiveOutputStream(bz).use { tar ->
                        fun addFile(entryName: String, content: ByteArray) {
                            val entry = TarArchiveEntry(entryName)
                            entry.size = content.size.toLong()
                            tar.putArchiveEntry(entry)
                            tar.write(content)
                            tar.closeArchiveEntry()
                        }

                        addFile(
                            "${ModelManager.TTS_DIR_NAME}/${ModelManager.TTS_MODEL_FILE}",
                            ByteArray(5_500_000) { 7 }
                        )
                        addFile("${ModelManager.TTS_DIR_NAME}/tokens.txt", ByteArray(512) { 65 })
                        addFile("${ModelManager.TTS_DIR_NAME}/espeak-ng-data/phontab", ByteArray(128) { 9 })
                        addFile("${ModelManager.TTS_DIR_NAME}/../../escape.txt", "blocked".toByteArray())
                    }
                }
            }

            val filesDir = File(rootDir, "files").apply { mkdirs() }
            assertFalse(ModelManager.isTtsInstalled(filesDir))

            ModelManager.extractTtsArchive(ttsArchive, filesDir)

            assertTrue(ModelManager.isTtsInstalled(filesDir))
            assertTrue(File(ModelManager.ttsEspeakDataDir(filesDir), "phontab").isFile)
            assertFalse(File(rootDir, "escape.txt").exists())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `sherpa onnx VITS TTS config and llamacpp Android binding symbols resolve on JVM`() {
        val vitsConfig = OfflineTtsVitsModelConfig(
            model = "/tmp/es_ES-sharvard-medium.onnx",
            tokens = "/tmp/tokens.txt",
            dataDir = "/tmp/espeak-ng-data",
            noiseScale = 0.667f,
            noiseScaleW = 0.8f,
            lengthScale = 1.0f
        )
        val ttsConfig = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = vitsConfig,
                numThreads = 2,
                debug = false,
                provider = "cpu"
            ),
            maxNumSentences = 1
        )
        assertEquals("/tmp/es_ES-sharvard-medium.onnx", ttsConfig.model.vits.model)
        assertEquals("/tmp/espeak-ng-data", ttsConfig.model.vits.dataDir)

        val methodNames = LlamaAndroid::class.java.methods.map { it.name }.toSet()
        assertTrue(methodNames.contains("startEngine"))
        assertTrue(methodNames.contains("launchCompletion"))
        assertTrue(methodNames.contains("stopCompletion"))
        assertTrue(methodNames.contains("releaseContext"))
    }
}

