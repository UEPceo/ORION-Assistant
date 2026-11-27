package com.orion.voicelab

import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineMoonshineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.VadModelConfig
import com.orion.voicelab.asr.AsrRecognitionResult
import com.orion.voicelab.asr.MoonshineSpeechRecognitionEngine
import com.orion.voicelab.asr.SpeechRecognitionEngine
import com.orion.voicelab.audio.AudioCaptureCapabilities
import com.orion.voicelab.audio.AudioOutputSink
import com.orion.voicelab.audio.AudioRoutingBackend
import com.orion.voicelab.audio.AudioRoutingController
import com.orion.voicelab.audio.OutputDeviceKind
import com.orion.voicelab.audio.VoiceActivityDetector
import com.orion.voicelab.audio.VoiceInputController
import com.orion.voicelab.audio.VoiceInputEngine
import com.orion.voicelab.conversation.ConversationEngine
import com.orion.voicelab.conversation.ConversationSession
import com.orion.voicelab.conversation.SpeechSentenceChunker
import com.orion.voicelab.data.AsrStats
import com.orion.voicelab.data.AssistantIdentity
import com.orion.voicelab.data.CallTelemetry
import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.data.ContactProfile
import com.orion.voicelab.data.ConversationState
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.FormalityLevel
import com.orion.voicelab.data.MemoryCategory
import com.orion.voicelab.data.MemoryItem
import com.orion.voicelab.data.TurnContext
import com.orion.voicelab.data.TurnLatencyMetrics
import com.orion.voicelab.data.VoiceProfile
import com.orion.voicelab.engine.MoonshineModelManager
import com.orion.voicelab.engine.OrionVoiceEngine
import com.orion.voicelab.llm.ChatPromptFormatter
import com.orion.voicelab.llm.LlamaCppLanguageModel
import com.orion.voicelab.llm.LlamaRuntimeBridge
import com.orion.voicelab.llm.LlmGenerationResult
import com.orion.voicelab.llm.LocalLanguageModel
import com.orion.voicelab.memory.ContextPromptBuilder
import com.orion.voicelab.memory.MemoryCandidateExtractor
import com.orion.voicelab.memory.MemorySelector
import com.orion.voicelab.model.DownloadSpec
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.model.ResumableHttpDownloader
import com.orion.voicelab.persistence.AssistantIdentityEntity
import com.orion.voicelab.persistence.ContactDao
import com.orion.voicelab.persistence.ContactRepository
import com.orion.voicelab.persistence.ConversationDao
import com.orion.voicelab.persistence.ConversationEntity
import com.orion.voicelab.persistence.ConversationMessageEntity
import com.orion.voicelab.persistence.ConversationRepository
import com.orion.voicelab.persistence.ConversationRowTuple
import com.orion.voicelab.persistence.CoworkerContactEntity
import com.orion.voicelab.persistence.IdentityDao
import com.orion.voicelab.persistence.IdentityRepository
import com.orion.voicelab.persistence.MemoryDao
import com.orion.voicelab.persistence.MemoryItemEntity
import com.orion.voicelab.persistence.MemoryRepository
import com.orion.voicelab.tts.LocalSpeechSynthesizer
import com.orion.voicelab.tts.SherpaOnnxSpeechSynthesizer
import com.orion.voicelab.tts.SynthesisResult
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.nehuatl.llamacpp.LlamaAndroid
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.createTempDirectory

class VoiceLabUnitTest {

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun buildValidGgufPayload(size: Int = 64_000): ByteArray {
        val bytes = ByteArray(size) { ((it * 31) % 251).toByte() }
        bytes[0] = 'G'.code.toByte()
        bytes[1] = 'G'.code.toByte()
        bytes[2] = 'U'.code.toByte()
        bytes[3] = 'F'.code.toByte()
        bytes[4] = 3
        return bytes
    }

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
        assertEquals(
            "9e2449e1087496d8d4caba907f23e0bd3f78d91fa552479bb9c23ac09cbb1fd6",
            sha256Hex(bytes)
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
    fun `model manager extracts allowed files safely and blocks symlinks and path traversal`() {
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

                        val symlinkEntry = TarArchiveEntry("model/tokens_link", TarArchiveEntry.LF_SYMLINK)
                        symlinkEntry.linkName = "/etc/passwd"
                        tar.putArchiveEntry(symlinkEntry)
                        tar.closeArchiveEntry()
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
            assertFalse(File(modelDir, "tokens_link").exists())
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
            llmTokensGenerated = 24,
            llmTokensPerSec = 16.0,
            ttsSynthMs = 280L,
            synthesizedAudioMs = 1800L,
            endToSpeechStartMs = 890L,
            responseDurationMs = 1950L
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

    @Test
    fun `resumable download handles 206 resume, 416 complete or corrupt, and sha256 verification`() {
        val rootDir = createTempDirectory("orion_download_resume").toFile()
        val payload = buildValidGgufPayload(48_000)
        val expectedSha = sha256Hex(payload)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            val mode = AtomicInteger(0) // 0=206 resume, 1=416 exact complete, 2=416 mismatch -> 200 retry
            server.createContext("/model.gguf") { ex ->
                val rangeHeader = ex.requestHeaders.getFirst("Range")
                when (mode.get()) {
                    0 -> {
                        val start = rangeHeader?.removePrefix("bytes=")?.removeSuffix("-")?.toIntOrNull() ?: 0
                        val slice = payload.copyOfRange(start, payload.size)
                        ex.responseHeaders.add("Content-Range", "bytes $start-${payload.size - 1}/${payload.size}")
                        ex.sendResponseHeaders(206, slice.size.toLong())
                        ex.responseBody.use { it.write(slice) }
                    }
                    1 -> {
                        ex.responseHeaders.add("Content-Range", "bytes */${payload.size}")
                        ex.sendResponseHeaders(416, -1)
                        ex.close()
                    }
                    else -> {
                        if (rangeHeader != null) {
                            ex.responseHeaders.add("Content-Range", "bytes */${payload.size}")
                            ex.sendResponseHeaders(416, -1)
                            ex.close()
                        } else {
                            ex.sendResponseHeaders(200, payload.size.toLong())
                            ex.responseBody.use { it.write(payload) }
                        }
                    }
                }
            }
            server.start()
            val url = "http://127.0.0.1:${server.address.port}/model.gguf"

            // 1. Resume from partial download via HTTP 206 + SHA-256 verification
            val filesDir1 = File(rootDir, "case206").apply { mkdirs() }
            val partFile1 = File(ModelManager.downloadsDirectory(filesDir1), "${ModelManager.LLM_FILE_NAME}.part")
            partFile1.parentFile?.mkdirs()
            partFile1.writeBytes(payload.copyOfRange(0, 16_000))
            assertTrue(ModelManager.hasLlmPartialDownload(filesDir1))

            ModelManager.downloadAndInstallLlm(
                filesDir = filesDir1,
                url = url,
                expectedExactBytes = payload.size.toLong(),
                expectedSha256 = expectedSha,
                requiredFreeBytes = 1_024L
            ) { _, _, _ -> }
            assertEquals(expectedSha, sha256Hex(ModelManager.llmModelFile(filesDir1).readBytes()))
            assertFalse(partFile1.exists())

            // 2. HTTP 416 when .part is already exact remote total
            mode.set(1)
            val filesDir2 = File(rootDir, "case416_complete").apply { mkdirs() }
            val partFile2 = File(ModelManager.downloadsDirectory(filesDir2), "${ModelManager.LLM_FILE_NAME}.part")
            partFile2.parentFile?.mkdirs()
            partFile2.writeBytes(payload)

            ModelManager.downloadAndInstallLlm(
                filesDir = filesDir2,
                url = url,
                expectedExactBytes = payload.size.toLong(),
                expectedSha256 = expectedSha,
                requiredFreeBytes = 1_024L
            ) { _, _, _ -> }
            assertTrue(ModelManager.llmModelFile(filesDir2).isFile)

            // 3. HTTP 416 when .part is larger/corrupt -> wipes .part and retries from byte 0
            mode.set(2)
            val filesDir3 = File(rootDir, "case416_corrupt").apply { mkdirs() }
            val partFile3 = File(ModelManager.downloadsDirectory(filesDir3), "${ModelManager.LLM_FILE_NAME}.part")
            partFile3.parentFile?.mkdirs()
            partFile3.writeBytes(ByteArray(payload.size + 512) { 9 })

            ModelManager.downloadAndInstallLlm(
                filesDir = filesDir3,
                url = url,
                expectedExactBytes = payload.size.toLong(),
                expectedSha256 = expectedSha,
                requiredFreeBytes = 1_024L
            ) { _, _, _ -> }
            assertEquals(expectedSha, sha256Hex(ModelManager.llmModelFile(filesDir3).readBytes()))
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `resumable download rejects wrong sha256, html errors, truncated streams and insufficient space`() {
        val rootDir = createTempDirectory("orion_download_errors").toFile()
        val payload = buildValidGgufPayload(32_000)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/ok") { ex ->
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.use { it.write(payload) }
            }
            server.createContext("/html") { ex ->
                val html = "<html><body>Captive Portal</body></html>".toByteArray()
                ex.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
                ex.sendResponseHeaders(200, html.size.toLong())
                ex.responseBody.use { it.write(html) }
            }
            server.createContext("/truncated") { ex ->
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.write(payload.copyOfRange(0, 8_000))
                ex.close()
            }
            server.createContext("/notfound") { ex ->
                ex.sendResponseHeaders(404, -1)
                ex.close()
            }
            server.start()
            val baseUrl = "http://127.0.0.1:${server.address.port}"

            // Wrong SHA-256 deletes file & fails cleanly
            val f1 = File(rootDir, "bad_sha").apply { mkdirs() }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = f1,
                    url = "$baseUrl/ok",
                    expectedExactBytes = payload.size.toLong(),
                    expectedSha256 = "0".repeat(64),
                    requiredFreeBytes = 1_024L
                ) { _, _, _ -> }
                fail("Expected SHA-256 mismatch failure")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("SHA-256"))
                assertFalse(ModelManager.llmModelFile(f1).exists())
            }

            // HTML captive portal response rejected cleanly
            val f2 = File(rootDir, "html_err").apply { mkdirs() }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = f2,
                    url = "$baseUrl/html",
                    expectedExactBytes = null,
                    expectedSha256 = null,
                    requiredFreeBytes = 1_024L
                ) { _, _, _ -> }
                fail("Expected HTML error rejection")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("HTML"))
            }

            // Truncated stream fails and does not promote target
            val f3 = File(rootDir, "trunc").apply { mkdirs() }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = f3,
                    url = "$baseUrl/truncated",
                    expectedExactBytes = payload.size.toLong(),
                    expectedSha256 = null,
                    requiredFreeBytes = 1_024L
                ) { _, _, _ -> }
                fail("Expected incomplete download failure")
            } catch (_: Exception) {
                assertFalse(ModelManager.llmModelFile(f3).exists())
            }

            // HTTP 404 fails cleanly
            val f4 = File(rootDir, "http404").apply { mkdirs() }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = f4,
                    url = "$baseUrl/notfound",
                    expectedExactBytes = null,
                    expectedSha256 = null,
                    requiredFreeBytes = 1_024L
                ) { _, _, _ -> }
                fail("Expected HTTP 404 error")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("404"))
            }

            // Insufficient space before starting
            val f5 = File(rootDir, "nospace").apply { mkdirs() }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = f5,
                    url = "$baseUrl/ok",
                    expectedExactBytes = payload.size.toLong(),
                    expectedSha256 = null,
                    requiredFreeBytes = Long.MAX_VALUE / 4
                ) { _, _, _ -> }
                fail("Expected insufficient space failure")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("Espacio insuficiente"))
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `llama cpp language model prevents double release, duplicate contexts and mid-generation cancellation`() = runBlocking {
        val rootDir = createTempDirectory("orion_llama_bridge").toFile()
        try {
            val gguf = File(rootDir, "model.gguf").apply { writeBytes(buildValidGgufPayload(1_024)) }
            val startCalls = AtomicInteger(0)
            val stopCalls = AtomicInteger(0)
            val releaseCalls = AtomicInteger(0)
            var nextContextError: String? = null
            val completionStarted = CompletableDeferred<Unit>()
            val allowCompletionFinish = CompletableDeferred<Unit>()

            val fakeBridge = object : LlamaRuntimeBridge {
                private var tokenCallback: ((String) -> Unit)? = null

                override fun openModelDescriptor(modelFile: File): Pair<String, Int> =
                    modelFile.toURI().toString() to 42

                override suspend fun startEngine(
                    params: Map<String, Any>,
                    onToken: (String) -> Unit
                ): Map<String, Any> {
                    startCalls.incrementAndGet()
                    tokenCallback = onToken
                    return if (nextContextError != null) {
                        mapOf("error" to nextContextError!!, "contextId" to 77)
                    } else {
                        mapOf("contextId" to 10)
                    }
                }

                override suspend fun launchCompletion(
                    contextId: Int,
                    params: Map<String, Any>
                ): Map<String, Any> {
                    completionStarted.complete(Unit)
                    tokenCallback?.invoke("Hola ")
                    allowCompletionFinish.await()
                    return mapOf("text" to "Hola", "tokens_predicted" to 1)
                }

                override suspend fun stopCompletion(contextId: Int) {
                    stopCalls.incrementAndGet()
                    allowCompletionFinish.complete(Unit)
                }

                override suspend fun releaseContext(contextId: Int) {
                    releaseCalls.incrementAndGet()
                }
            }

            val clock = AtomicLong(1000L)

            // 1. Native error on startEngine releases leaked contextId and reports error
            nextContextError = "out_of_memory"
            val failingLlm = LlamaCppLanguageModel(
                modelFile = gguf,
                bridge = fakeBridge,
                clockMs = { clock.getAndAdd(10) }
            )
            try {
                failingLlm.load()
                fail("Expected native error to propagate")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("out_of_memory"))
            }
            assertEquals(1, releaseCalls.get())
            failingLlm.shutdown()

            // 2. Duplicate load, mid-generation cancel, and single context release
            nextContextError = null
            startCalls.set(0)
            releaseCalls.set(0)
            stopCalls.set(0)

            val llm = LlamaCppLanguageModel(
                modelFile = gguf,
                bridge = fakeBridge,
                clockMs = { clock.getAndAdd(25) }
            )
            llm.load()
            llm.load()
            assertEquals(1, startCalls.get())
            assertTrue(llm.isLoaded())

            val tokensSeen = CopyOnWriteArrayList<String>()
            val genJob = async {
                llm.generateReply("Eres ORION", listOf(ChatMessage(ChatRole.USER, "Hola"))) { tok ->
                    tokensSeen.add(tok)
                }
            }
            completionStarted.await()
            llm.cancelGeneration()
            val result = genJob.await()
            assertTrue(result.cancelled)
            assertEquals(listOf("Hola "), tokensSeen.toList())
            assertTrue(stopCalls.get() >= 1)

            // Repeated unload must release context strictly once
            llm.unload()
            llm.unload()
            llm.shutdown()
            delay(150)
            assertFalse(llm.isLoaded())
            assertEquals(1, releaseCalls.get())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `conversation engine handles load failures, single turn, barge-in interruption and session reset`() = runBlocking {
        // 1. Model load failure transitions cleanly to ERROR and notifies recorded errors
        val badAsr = FakeSpeechRecognizer(loadFailure = IllegalStateException("Modelo ASR ausente"))
        val badEngine = ConversationEngine(
            speechRecognizer = badAsr,
            languageModel = FakeLanguageModel(),
            speechSynthesizer = FakeSpeechSynthesizer(),
            playbackEngine = FakeAudioOutputSink(),
            vadFactory = { FakeVad() },
            clockMs = { 100L },
            inputFactory = { FakeVoiceInputController() }
        )
        val errors = CopyOnWriteArrayList<String>()
        val endedLatch = CountDownLatch(1)
        badEngine.attachCallbacks(object : ConversationEngine.Callbacks {
            override fun onStateChanged(state: ConversationState, statusText: String) {}
            override fun onSpeechDetectedChanged(detected: Boolean) {}
            override fun onAudioLevel(level: Float) {}
            override fun onTurnsUpdated(turns: List<ConversationTurn>) {}
            override fun onStreamingText(text: String) {}
            override fun onTelemetryUpdated(telemetry: CallTelemetry) {}
            override fun onModelsInRamChanged(loaded: Boolean) {}
            override fun onErrorRecorded(message: String) {
                errors.add(message)
            }
            override fun onCallEnded() {
                endedLatch.countDown()
            }
        })
        badEngine.startCall()
        assertTrue(endedLatch.await(3, TimeUnit.SECONDS))
        assertEquals(ConversationState.ERROR, badEngine.state.value)
        assertFalse(badEngine.isCallActive())
        assertTrue(errors.any { it.contains("Modelo ASR ausente") })
        badEngine.shutdown()

        // 2. Full turn orchestration + duplicate frame protection + reset + barge-in interrupt
        val fakeInput = FakeVoiceInputController()
        val fakeVad = FakeVad()
        val fakeAsr = FakeSpeechRecognizer()
        val gateLlm = CompletableDeferred<Unit>()
        val fakeLlm = FakeLanguageModel(generationGate = gateLlm)
        val fakeTts = FakeSpeechSynthesizer()
        val fakePlayback = FakeAudioOutputSink()
        val nowMs = AtomicLong(1_000L)

        val engine = ConversationEngine(
            speechRecognizer = fakeAsr,
            languageModel = fakeLlm,
            speechSynthesizer = fakeTts,
            playbackEngine = fakePlayback,
            vadFactory = { fakeVad },
            clockMs = { nowMs.addAndGet(20L) },
            inputFactory = { fakeInput }
        )

        val turnsSeen = CopyOnWriteArrayList<List<ConversationTurn>>()
        val listeningLatch = CountDownLatch(1)
        val telemetryLatch = CountDownLatch(1)
        val turn1ReturnedToListening = CompletableDeferred<Unit>()
        val interruptedLatch = CountDownLatch(1)
        val statesSeen = CopyOnWriteArrayList<ConversationState>()

        engine.attachCallbacks(object : ConversationEngine.Callbacks {
            override fun onStateChanged(state: ConversationState, statusText: String) {
                statesSeen.add(state)
                if (state == ConversationState.LISTENING) {
                    listeningLatch.countDown()
                    if (telemetryLatch.count == 0L) {
                        turn1ReturnedToListening.complete(Unit)
                    }
                }
                if (state == ConversationState.INTERRUPTED) interruptedLatch.countDown()
            }
            override fun onSpeechDetectedChanged(detected: Boolean) {}
            override fun onAudioLevel(level: Float) {}
            override fun onTurnsUpdated(turns: List<ConversationTurn>) {
                turnsSeen.add(turns)
            }
            override fun onStreamingText(text: String) {}
            override fun onTelemetryUpdated(telemetry: CallTelemetry) {
                if (telemetry.llmTurnsCount > 0) telemetryLatch.countDown()
            }
            override fun onModelsInRamChanged(loaded: Boolean) {}
            override fun onCallEnded() {}
        })

        engine.startCall()
        assertTrue(listeningLatch.await(3, TimeUnit.SECONDS))

        // Enqueue speech segment -> trigger 2 frames rapidly; only 1 turn should run
        fakeVad.enqueueSegment(FloatArray(8_000) { 0.2f })
        fakeInput.emitFrame(FloatArray(512) { 0.2f }, 0.3f)
        fakeInput.emitFrame(FloatArray(512) { 0.2f }, 0.3f)

        gateLlm.complete(Unit)
        assertTrue(telemetryLatch.await(4, TimeUnit.SECONDS))
        turn1ReturnedToListening.await()
        assertEquals(1, fakeAsr.transcribeCalls.get())
        assertTrue(turnsSeen.last().size == 2)

        // Reset session clears conversation turns cleanly
        engine.resetSession()
        assertTrue(turnsSeen.last().isEmpty())

        // Advance fake clock past ECHO_COOLDOWN_MS and start turn 2
        nowMs.addAndGet(500L)

        // Test manual barge-in interrupt while turn 2 is generating
        val blockedGate = CompletableDeferred<Unit>()
        val secondGenStarted = CompletableDeferred<Unit>()
        fakeLlm.setNextGate(blockedGate, secondGenStarted)
        fakeVad.enqueueSegment(FloatArray(8_000) { 0.2f })
        fakeInput.emitFrame(FloatArray(512) { 0.2f }, 0.3f)
        secondGenStarted.await()
        engine.interruptSpeaking()
        blockedGate.complete(Unit)

        assertTrue(interruptedLatch.await(3, TimeUnit.SECONDS))
        assertTrue(fakeLlm.cancelCount.get() >= 1)
        assertTrue(fakeTts.cancelCount.get() >= 1)
        assertTrue(fakePlayback.stopCount.get() >= 1)

        engine.shutdown()
    }

    @Test
    fun `http 206 without Content-Range rejects without appending`() {
        val rootDir = createTempDirectory("orion_206_no_cr").toFile()
        val payload = buildValidGgufPayload(1_000)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            val requestCount = AtomicInteger(0)
            server.createContext("/model.gguf") { ex ->
                requestCount.incrementAndGet()
                val slice = payload.copyOfRange(200, payload.size)
                // Always returns 206 without Content-Range even after clean retry
                ex.sendResponseHeaders(206, slice.size.toLong())
                ex.responseBody.use { it.write(slice) }
            }
            server.start()

            val partFile = File(rootDir, "downloads/m.gguf.part").apply {
                parentFile?.mkdirs()
                writeBytes(payload.copyOfRange(0, 200))
            }

            try {
                ResumableHttpDownloader.download(
                    DownloadSpec(
                        url = "http://127.0.0.1:${server.address.port}/model.gguf",
                        destinationPartFile = partFile,
                        minRequiredFreeBytes = 1_024L,
                        expectedExactBytes = payload.size.toLong()
                    )
                ) { _, _, _ -> }
                fail("Expected HTTP 206 without Content-Range to fail")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("206") || e.message!!.contains("Content-Range"))
                assertFalse("Corrupt .part must not be appended or left around", partFile.exists())
                assertEquals(2, requestCount.get())
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `http 206 with mismatched start offset rejects without appending`() {
        val rootDir = createTempDirectory("orion_206_bad_start").toFile()
        val payload = buildValidGgufPayload(500)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/model.gguf") { ex ->
                val slice = payload.copyOfRange(100, payload.size)
                ex.responseHeaders.add("Content-Range", "bytes 100-499/500")
                ex.sendResponseHeaders(206, slice.size.toLong())
                ex.responseBody.use { it.write(slice) }
            }
            server.start()

            val partFile = File(rootDir, "downloads/m.gguf.part").apply {
                parentFile?.mkdirs()
                writeBytes(payload.copyOfRange(0, 200))
            }

            try {
                ResumableHttpDownloader.download(
                    DownloadSpec(
                        url = "http://127.0.0.1:${server.address.port}/model.gguf",
                        destinationPartFile = partFile,
                        minRequiredFreeBytes = 1_024L,
                        expectedExactBytes = 500L
                    )
                ) { _, _, _ -> }
                fail("Expected HTTP 206 with mismatched start offset to fail")
            } catch (_: IllegalStateException) {
                assertFalse(partFile.exists())
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `http 206 with invalid end range rejects without appending`() {
        val rootDir = createTempDirectory("orion_206_bad_end").toFile()
        val payload = buildValidGgufPayload(500)
        val headerCase = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/model.gguf") { ex ->
                val slice = payload.copyOfRange(200, payload.size)
                val cr = when (headerCase.get()) {
                    0 -> "bytes 200-100/500" // end < start
                    1 -> "bytes 200-500/500" // end >= total
                    else -> "bytes 200-399/500" // incomplete end < total - 1
                }
                ex.responseHeaders.add("Content-Range", cr)
                ex.sendResponseHeaders(206, slice.size.toLong())
                ex.responseBody.use { it.write(slice) }
            }
            server.start()

            for (caseIdx in 0..2) {
                headerCase.set(caseIdx)
                val partFile = File(rootDir, "case_$caseIdx/m.gguf.part").apply {
                    parentFile?.mkdirs()
                    writeBytes(payload.copyOfRange(0, 200))
                }
                try {
                    ResumableHttpDownloader.download(
                        DownloadSpec(
                            url = "http://127.0.0.1:${server.address.port}/model.gguf",
                            destinationPartFile = partFile,
                            minRequiredFreeBytes = 1_024L,
                            expectedExactBytes = 500L
                        )
                    ) { _, _, _ -> }
                    fail("Expected invalid end range case $caseIdx to fail")
                } catch (_: IllegalStateException) {
                    assertFalse("Case $caseIdx should delete corrupt .part", partFile.exists())
                }
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `http 206 with incompatible remote total rejects without appending`() {
        val rootDir = createTempDirectory("orion_206_bad_total").toFile()
        val payload = buildValidGgufPayload(1_000)
        val headerCase = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/model.gguf") { ex ->
                val slice = payload.copyOfRange(100, 500)
                val cr = if (headerCase.get() == 0) {
                    "bytes 100-999/1000" // mismatch with expectedExactBytes=500
                } else {
                    "bytes 100-499/*" // ambiguous total '*'
                }
                ex.responseHeaders.add("Content-Range", cr)
                ex.sendResponseHeaders(206, slice.size.toLong())
                ex.responseBody.use { it.write(slice) }
            }
            server.start()

            for (caseIdx in 0..1) {
                headerCase.set(caseIdx)
                val partFile = File(rootDir, "case_$caseIdx/m.gguf.part").apply {
                    parentFile?.mkdirs()
                    writeBytes(payload.copyOfRange(0, 100))
                }
                try {
                    ResumableHttpDownloader.download(
                        DownloadSpec(
                            url = "http://127.0.0.1:${server.address.port}/model.gguf",
                            destinationPartFile = partFile,
                            minRequiredFreeBytes = 1_024L,
                            expectedExactBytes = 500L
                        )
                    ) { _, _, _ -> }
                    fail("Expected incompatible remote total case $caseIdx to fail")
                } catch (_: IllegalStateException) {
                    assertFalse(partFile.exists())
                }
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `http 206 with truncated body rejects without installing partial`() {
        val rootDir = createTempDirectory("orion_206_trunc_body").toFile()
        val payload = buildValidGgufPayload(500)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/model.gguf") { ex ->
                ex.responseHeaders.add("Content-Range", "bytes 100-499/500")
                ex.sendResponseHeaders(206, 400L)
                ex.responseBody.write(payload.copyOfRange(100, 200))
                ex.close()
            }
            server.start()

            val filesDir = File(rootDir, "files").apply { mkdirs() }
            val partFile = File(ModelManager.downloadsDirectory(filesDir), "${ModelManager.LLM_FILE_NAME}.part").apply {
                parentFile?.mkdirs()
                writeBytes(payload.copyOfRange(0, 100))
            }

            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = filesDir,
                    url = "http://127.0.0.1:${server.address.port}/model.gguf",
                    expectedExactBytes = 500L,
                    expectedSha256 = sha256Hex(payload),
                    requiredFreeBytes = 1_024L
                ) { _, _, _ -> }
                fail("Expected truncated 206 body to fail")
            } catch (_: Exception) {
                assertFalse(ModelManager.llmModelFile(filesDir).exists())
                assertTrue(partFile.length() <= 200L)
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `http 200 during resumed download resets partial file instead of concatenating`() {
        val rootDir = createTempDirectory("orion_200_reset").toFile()
        val payload = buildValidGgufPayload(1_200)
        val expectedSha = sha256Hex(payload)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/model.gguf") { ex ->
                // Server ignores Range and replies 200 with complete file
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.use { it.write(payload) }
            }
            server.start()

            val filesDir = File(rootDir, "files").apply { mkdirs() }
            val partFile = File(ModelManager.downloadsDirectory(filesDir), "${ModelManager.LLM_FILE_NAME}.part").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(400) { 0x55 }) // stale garbage partial
            }

            ModelManager.downloadAndInstallLlm(
                filesDir = filesDir,
                url = "http://127.0.0.1:${server.address.port}/model.gguf",
                expectedExactBytes = payload.size.toLong(),
                expectedSha256 = expectedSha,
                requiredFreeBytes = 1_024L
            ) { _, _, _ -> }

            val installed = ModelManager.llmModelFile(filesDir)
            assertTrue(installed.isFile)
            assertEquals(payload.size.toLong(), installed.length())
            assertEquals(expectedSha, sha256Hex(installed.readBytes()))
            assertFalse(partFile.exists())
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `http 416 completes only when Content-Range matches local file size`() {
        val rootDir = createTempDirectory("orion_416_cr").toFile()
        val payload = buildValidGgufPayload(1_000)
        val expectedSha = sha256Hex(payload)
        val includeCr = AtomicInteger(0)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/model.gguf") { ex ->
                if (includeCr.get() == 1) {
                    ex.responseHeaders.add("Content-Range", "bytes */${payload.size}")
                }
                ex.sendResponseHeaders(416, -1)
                ex.close()
            }
            server.start()

            // Without Content-Range on 416 -> must NOT blindly promote partial file
            val f1 = File(rootDir, "no_cr").apply { mkdirs() }
            File(ModelManager.downloadsDirectory(f1), "${ModelManager.LLM_FILE_NAME}.part").apply {
                parentFile?.mkdirs()
                writeBytes(payload)
            }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = f1,
                    url = "http://127.0.0.1:${server.address.port}/model.gguf",
                    expectedExactBytes = payload.size.toLong(),
                    expectedSha256 = expectedSha,
                    requiredFreeBytes = 1_024L
                ) { _, _, _ -> }
                fail("HTTP 416 without Content-Range must not assume completion")
            } catch (_: IllegalStateException) {
                assertFalse(ModelManager.llmModelFile(f1).exists())
            }

            // With matching Content-Range on 416 -> succeeds and verifies SHA-256
            includeCr.set(1)
            val f2 = File(rootDir, "with_cr").apply { mkdirs() }
            File(ModelManager.downloadsDirectory(f2), "${ModelManager.LLM_FILE_NAME}.part").apply {
                parentFile?.mkdirs()
                writeBytes(payload)
            }
            ModelManager.downloadAndInstallLlm(
                filesDir = f2,
                url = "http://127.0.0.1:${server.address.port}/model.gguf",
                expectedExactBytes = payload.size.toLong(),
                expectedSha256 = expectedSha,
                requiredFreeBytes = 1_024L
            ) { _, _, _ -> }
            assertEquals(expectedSha, sha256Hex(ModelManager.llmModelFile(f2).readBytes()))
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `download cancellation mid-stream aborts cleanly without promoting partial`() {
        val rootDir = createTempDirectory("orion_cancel_dl").toFile()
        val payload = buildValidGgufPayload(256_000)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val cancelFlag = AtomicInteger(0)
        try {
            server.createContext("/model.gguf") { ex ->
                ex.sendResponseHeaders(200, payload.size.toLong())
                ex.responseBody.use { out ->
                    var offset = 0
                    while (offset < payload.size) {
                        val len = minOf(16_384, payload.size - offset)
                        out.write(payload, offset, len)
                        out.flush()
                        offset += len
                        cancelFlag.incrementAndGet()
                        Thread.sleep(15)
                    }
                }
            }
            server.start()

            val filesDir = File(rootDir, "files").apply { mkdirs() }
            try {
                ModelManager.downloadAndInstallLlm(
                    filesDir = filesDir,
                    url = "http://127.0.0.1:${server.address.port}/model.gguf",
                    expectedExactBytes = payload.size.toLong(),
                    expectedSha256 = sha256Hex(payload),
                    requiredFreeBytes = 1_024L,
                    shouldCancel = { cancelFlag.get() >= 2 }
                ) { _, _, _ -> }
                fail("Expected CancellationException when aborted mid-stream")
            } catch (_: CancellationException) {
                assertFalse(ModelManager.llmModelFile(filesDir).exists())
            }
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `interrupted model replacement recovers cleanly from bak for llm and tar models`() {
        val rootDir = createTempDirectory("orion_bak_recovery").toFile()
        try {
            // 1. LLM .bak recovery (crash after target -> .bak before .part -> target)
            val filesLlm = File(rootDir, "llm_crash").apply { mkdirs() }
            val validLlm = buildValidGgufPayload(4_096)
            ModelManager.llmDirectory(filesLlm).mkdirs()
            ModelManager.llmBackupFile(filesLlm).writeBytes(validLlm)
            assertFalse(ModelManager.llmModelFile(filesLlm).exists())

            assertTrue(ModelManager.isLlmInstalled(filesLlm, expectedExactBytes = validLlm.size.toLong()))
            assertTrue(ModelManager.llmModelFile(filesLlm).isFile)
            assertFalse(ModelManager.llmBackupFile(filesLlm).exists())

            // 2. TTS .bak directory recovery
            val filesTts = File(rootDir, "tts_crash").apply { mkdirs() }
            val ttsBackupDir = File(filesTts, "models/${ModelManager.TTS_DIR_NAME}.bak").apply { mkdirs() }
            File(ttsBackupDir, ModelManager.TTS_MODEL_FILE).writeBytes(ByteArray(5_100_000) { 1 })
            File(ttsBackupDir, "tokens.txt").writeBytes(ByteArray(200) { 65 })
            File(ttsBackupDir, "espeak-ng-data/phontab").apply {
                parentFile?.mkdirs()
                writeBytes(ByteArray(64) { 2 })
            }
            assertTrue(ModelManager.isTtsInstalled(filesTts))
            assertTrue(ModelManager.ttsDirectory(filesTts).isDirectory)
            assertFalse(ttsBackupDir.exists())

            // 3. ASR .bak directory recovery
            val filesAsr = File(rootDir, "asr_crash").apply { mkdirs() }
            val asrBackupDir = File(filesAsr, "models/${MoonshineModelManager.MODEL_DIR_NAME}.bak").apply { mkdirs() }
            File(asrBackupDir, "encoder_model.ort").writeBytes(ByteArray(1_100_000) { 3 })
            File(asrBackupDir, "decoder_model_merged.ort").writeBytes(ByteArray(1_100_000) { 4 })
            File(asrBackupDir, "tokens.txt").writeBytes(ByteArray(2_000) { 5 })
            assertTrue(MoonshineModelManager.modelFilesPresent(filesAsr))
            assertTrue(MoonshineModelManager.modelDirectory(filesAsr).isDirectory)
            assertFalse(asrBackupDir.exists())
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `archive sanitizer blocks leading slash and hardlinks and downloader follows redirects`() {
        val rootDir = createTempDirectory("orion_redirect_tar").toFile()
        val payload = buildValidGgufPayload(2_048)
        val expectedSha = sha256Hex(payload)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        try {
            server.createContext("/redirect") { ex ->
                ex.responseHeaders.add("Location", "/final")
                ex.sendResponseHeaders(302, -1)
                ex.close()
            }
            server.createContext("/final") { ex ->
                val rangeHeader = ex.requestHeaders.getFirst("Range")
                if (rangeHeader == "bytes=512-") {
                    val slice = payload.copyOfRange(512, payload.size)
                    ex.responseHeaders.add("Content-Range", "bytes 512-${payload.size - 1}/${payload.size}")
                    ex.sendResponseHeaders(206, slice.size.toLong())
                    ex.responseBody.use { it.write(slice) }
                } else {
                    ex.sendResponseHeaders(500, -1)
                }
            }
            server.start()

            // Redirect preserves Range header on HTTP 206 resume
            val filesDir = File(rootDir, "files").apply { mkdirs() }
            val partFile = File(ModelManager.downloadsDirectory(filesDir), "${ModelManager.LLM_FILE_NAME}.part").apply {
                parentFile?.mkdirs()
                writeBytes(payload.copyOfRange(0, 512))
            }
            ModelManager.downloadAndInstallLlm(
                filesDir = filesDir,
                url = "http://127.0.0.1:${server.address.port}/redirect",
                expectedExactBytes = payload.size.toLong(),
                expectedSha256 = expectedSha,
                requiredFreeBytes = 1_024L
            ) { _, _, _ -> }
            assertEquals(expectedSha, sha256Hex(ModelManager.llmModelFile(filesDir).readBytes()))
            assertFalse(partFile.exists())

            // Leading slash (when preserved in TAR header) & hardlink blocked by sanitizeArchiveEntryPath
            val absEntry = TarArchiveEntry("/etc/passwd", true)
            assertNull(ModelManager.sanitizeArchiveEntryPath(absEntry))
            val hardlink = TarArchiveEntry("model/tokens.txt", TarArchiveEntry.LF_LINK)
            hardlink.linkName = "/etc/shadow"
            assertNull(ModelManager.sanitizeArchiveEntryPath(hardlink))
        } finally {
            server.stop(0)
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `conversation engine hangup during load prevents ghost call and clobbering subsequent call`() = runBlocking {
        val gateFirstLoad = CompletableDeferred<Unit>()
        val firstLoadStarted = CompletableDeferred<Unit>()
        val callCount = AtomicInteger(0)
        val inputStarts = AtomicInteger(0)

        val gatedAsr = object : SpeechRecognitionEngine {
            private var loaded = false
            override fun isLoaded(): Boolean = loaded
            override suspend fun load(): Long {
                if (callCount.incrementAndGet() == 1) {
                    firstLoadStarted.complete(Unit)
                    gateFirstLoad.await()
                }
                loaded = true
                return 10L
            }
            override suspend fun transcribe(samples: FloatArray, sampleRate: Int) =
                AsrRecognitionResult("Hola", 500L, 20L)
            override fun release() { loaded = false }
        }

        val trackingInput = object : VoiceInputController {
            override val isRunning: Boolean get() = active
            override val isMuted: Boolean get() = false
            private var active = false
            override fun setMuted(value: Boolean) {}
            override fun start(listener: VoiceInputEngine.Listener): Boolean {
                inputStarts.incrementAndGet()
                active = true
                listener.onCaptureStarted(
                    AudioCaptureCapabilities("VOICE_COMMUNICATION", 16_000, true, true, true, true)
                )
                return true
            }
            override fun stop() { active = false }
        }

        val engine = ConversationEngine(
            speechRecognizer = gatedAsr,
            languageModel = FakeLanguageModel(),
            speechSynthesizer = FakeSpeechSynthesizer(),
            playbackEngine = FakeAudioOutputSink(),
            vadFactory = { FakeVad() },
            clockMs = { 1_000L },
            inputFactory = { trackingInput }
        )

        // Start call #1 -> blocks in ASR load; user hangs up -> immediately starts call #2
        engine.startCall()
        firstLoadStarted.await()
        engine.endCall()
        assertFalse(engine.isCallActive())

        val secondListening = CompletableDeferred<Unit>()
        engine.attachCallbacks(object : ConversationEngine.Callbacks {
            override fun onStateChanged(state: ConversationState, statusText: String) {
                if (state == ConversationState.LISTENING) secondListening.complete(Unit)
            }
            override fun onSpeechDetectedChanged(detected: Boolean) {}
            override fun onAudioLevel(level: Float) {}
            override fun onTurnsUpdated(turns: List<ConversationTurn>) {}
            override fun onStreamingText(text: String) {}
            override fun onTelemetryUpdated(telemetry: CallTelemetry) {}
            override fun onModelsInRamChanged(loaded: Boolean) {}
            override fun onCallEnded() {}
        })

        engine.startCall()
        gateFirstLoad.complete(Unit) // Unblock aborted first session AFTER call #2 began!
        secondListening.await()
        delay(50)

        assertTrue("Call #2 must remain active without being clobbered by aborted call #1", engine.isCallActive())
        assertEquals("Only call #2 may start microphone capture", 1, inputStarts.get())
        engine.shutdown()
    }

    @Test
    fun `engines reject calls cleanly after shutdown without RejectedExecutionException`() = runBlocking {
        val rootDir = createTempDirectory("orion_shutdown_safety").toFile()
        try {
            val gguf = File(rootDir, "m.gguf").apply { writeBytes(buildValidGgufPayload(1_024)) }
            val completionStarted = CompletableDeferred<Unit>()
            val allowFinish = CompletableDeferred<Unit>()
            val stopCalls = AtomicInteger(0)
            val releaseCalls = AtomicInteger(0)

            val bridge = object : LlamaRuntimeBridge {
                override fun openModelDescriptor(modelFile: File) = modelFile.toURI().toString() to 9
                override suspend fun startEngine(params: Map<String, Any>, onToken: (String) -> Unit) =
                    mapOf("contextId" to 33)
                override suspend fun launchCompletion(contextId: Int, params: Map<String, Any>): Map<String, Any> {
                    completionStarted.complete(Unit)
                    allowFinish.await()
                    return mapOf("text" to "", "tokens_predicted" to 0)
                }
                override suspend fun stopCompletion(contextId: Int) {
                    stopCalls.incrementAndGet()
                    allowFinish.complete(Unit)
                }
                override suspend fun releaseContext(contextId: Int) {
                    releaseCalls.incrementAndGet()
                }
            }

            val clock = AtomicLong(1_000L)
            val llm = LlamaCppLanguageModel(
                modelFile = gguf,
                bridge = bridge,
                clockMs = { clock.getAndAdd(10L) }
            )
            llm.load()
            val gen = async {
                llm.generateReply("Eres ORION", listOf(ChatMessage(ChatRole.USER, "Hola"))) {}
            }
            completionStarted.await()

            // Calling shutdown mid-generation must invoke stopCompletion and release once
            llm.shutdown()
            llm.shutdown()
            val cancelledResult = gen.await()
            assertTrue(cancelledResult.cancelled)
            delay(150)
            assertEquals(1, stopCalls.get())
            assertEquals(1, releaseCalls.get())

            // Calling generateReply after shutdown must not throw RejectedExecutionException
            val afterResult = llm.generateReply("Eres ORION", emptyList()) {}
            assertTrue(afterResult.cancelled)

            // TTS and ASR shutdowns are idempotent and reject subsequent calls cleanly
            val tts = SherpaOnnxSpeechSynthesizer(rootDir)
            tts.shutdown()
            tts.shutdown()
            try {
                tts.load()
                fail("Expected TTS load after shutdown to fail with IllegalStateException")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("cerrado"))
            }
            try {
                tts.synthesize("hola") { _, _ -> true }
                fail("Expected TTS synthesize after shutdown to fail cleanly")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("cerrado"))
            }

            val asr = MoonshineSpeechRecognitionEngine(rootDir)
            asr.shutdown()
            asr.shutdown()
            try {
                asr.transcribe(FloatArray(512), 16_000)
                fail("Expected ASR transcribe after shutdown to fail cleanly")
            } catch (e: IllegalStateException) {
                assertTrue(e.message!!.contains("cerrado"))
            }
        } finally {
            rootDir.deleteRecursively()
        }
    }

    @Test
    fun `audio routing controller switches routes restores mode and ignores post-stop callbacks`() {
        var modeNow = android.media.AudioManager.MODE_NORMAL
        var speakerNow = false
        var commCleared = 0
        val selectedDevices = mutableListOf<OutputDeviceKind>()
        val availableOutputs = mutableListOf<OutputDeviceKind>()
        var registeredCb: (() -> Unit)? = null

        val fakeBackend = object : AudioRoutingBackend {
            override val isApiSOrAbove: Boolean = true
            override fun getMode(): Int = modeNow
            override fun setMode(mode: Int) { modeNow = mode }
            override fun isSpeakerphoneOn(): Boolean = speakerNow
            override fun setSpeakerphoneOn(on: Boolean) { speakerNow = on }
            override fun getOutputDevices(): List<OutputDeviceKind> = availableOutputs.toList()
            override fun selectCommunicationDevice(preferredKind: OutputDeviceKind): Boolean {
                selectedDevices.add(preferredKind)
                return preferredKind != OutputDeviceKind.OTHER
            }
            override fun clearCommunicationDevice() { commCleared++ }
            override fun registerDeviceCallback(onDevicesChanged: () -> Unit) { registeredCb = onDevicesChanged }
            override fun unregisterDeviceCallback() { registeredCb = null }
        }

        val controller = AudioRoutingController(fakeBackend)
        val routesSeen = mutableListOf<String>()

        availableOutputs.add(OutputDeviceKind.BLUETOOTH)
        val initial = controller.startCallRouting { routesSeen.add(it) }
        assertEquals("Bluetooth", initial)
        assertEquals(android.media.AudioManager.MODE_IN_COMMUNICATION, modeNow)
        assertEquals(OutputDeviceKind.BLUETOOTH, selectedDevices.last())

        // Plugging wired headset overrides Bluetooth
        availableOutputs.add(OutputDeviceKind.WIRED_HEADSET)
        val staleCb = registeredCb!!
        staleCb.invoke()
        assertEquals("Auriculares", routesSeen.last())
        assertEquals(OutputDeviceKind.WIRED_HEADSET, selectedDevices.last())

        // Ending call restores MODE_NORMAL & clears communication device
        controller.stopCallRouting()
        assertEquals(android.media.AudioManager.MODE_NORMAL, modeNow)
        assertEquals(1, commCleared)
        val selectedCountAtStop = selectedDevices.size

        // Delayed system callback firing after stopCallRouting must not mutate state or re-select routes
        staleCb.invoke()
        assertEquals(selectedCountAtStop, selectedDevices.size)
        assertEquals(android.media.AudioManager.MODE_NORMAL, modeNow)
    }

    private class FakeVoiceInputController : VoiceInputController {
        override val isRunning: Boolean get() = active
        override val isMuted: Boolean get() = muted
        private var active = false
        private var muted = false
        private var listener: VoiceInputEngine.Listener? = null

        override fun setMuted(value: Boolean) {
            muted = value
        }

        override fun start(listener: VoiceInputEngine.Listener): Boolean {
            active = true
            this.listener = listener
            listener.onCaptureStarted(
                AudioCaptureCapabilities(
                    audioSourceName = "VOICE_COMMUNICATION",
                    sampleRateHz = 16_000,
                    aecAvailable = true,
                    aecActive = true,
                    nsAvailable = true,
                    nsActive = true
                )
            )
            return true
        }

        fun emitFrame(samples: FloatArray, rms: Float) {
            listener?.onAudioFrame(samples, rms)
        }

        override fun stop() {
            active = false
            listener?.onCaptureStopped()
        }
    }

    private class FakeVad : VoiceActivityDetector {
        private val pending = ArrayDeque<FloatArray>()
        private var speech = false

        fun enqueueSegment(segment: FloatArray) {
            pending.addLast(segment)
        }

        override fun acceptWaveform(samples: FloatArray) {
            speech = pending.isNotEmpty()
        }

        override fun isSpeechDetected(): Boolean = speech

        override fun drainSegments(minSamples: Int): List<FloatArray> {
            val out = pending.filter { it.size >= minSamples }
            pending.clear()
            speech = false
            return out
        }

        override fun flush() {}
        override fun reset() {
            pending.clear()
            speech = false
        }
        override fun release() {
            pending.clear()
        }
    }

    private class FakeSpeechRecognizer(
        private val loadFailure: Exception? = null
    ) : SpeechRecognitionEngine {
        private var loaded = false
        val transcribeCalls = AtomicInteger(0)

        override fun isLoaded(): Boolean = loaded

        override suspend fun load(): Long {
            loadFailure?.let { throw it }
            loaded = true
            return 15L
        }

        override suspend fun transcribe(samples: FloatArray, sampleRate: Int): AsrRecognitionResult {
            transcribeCalls.incrementAndGet()
            return AsrRecognitionResult(text = "Hola ORION", audioMs = 500L, decodeMs = 45L)
        }

        override fun release() {
            loaded = false
        }
    }

    private class FakeLanguageModel(
        private var generationGate: CompletableDeferred<Unit> = CompletableDeferred<Unit>().apply { complete(Unit) }
    ) : LocalLanguageModel {
        override val modelDisplayName: String = "FakeQwen"
        override val modelLicense: String = "Apache 2.0"
        private var loaded = false
        val cancelCount = AtomicInteger(0)
        @Volatile
        private var cancelled = false
        @Volatile
        private var startedSignal: CompletableDeferred<Unit>? = null

        fun setNextGate(gate: CompletableDeferred<Unit>, onStarted: CompletableDeferred<Unit>? = null) {
            generationGate = gate
            startedSignal = onStarted
        }

        override fun isModelInstalled(): Boolean = true
        override fun isLoaded(): Boolean = loaded
        override fun modelSizeMb(): Long = 1065L

        override suspend fun load(): Long {
            loaded = true
            return 25L
        }

        override suspend fun generateReply(
            systemPrompt: String,
            history: List<ChatMessage>,
            onToken: (String) -> Unit
        ): LlmGenerationResult {
            cancelled = false
            startedSignal?.complete(Unit)
            generationGate.await()
            if (cancelled) {
                return LlmGenerationResult("", 0L, 10L, 0, true)
            }
            val reply = "Hola, estoy listo para ayudarte."
            onToken(reply)
            return LlmGenerationResult(reply, 30L, 120L, 8, false)
        }

        override fun cancelGeneration() {
            cancelled = true
            cancelCount.incrementAndGet()
        }

        override fun unload() {
            loaded = false
        }
    }

    private class FakeSpeechSynthesizer : LocalSpeechSynthesizer {
        override val engineDisplayName: String = "FakePiper"
        override val engineLicense: String = "MIT"
        private var loaded = false
        val cancelCount = AtomicInteger(0)

        override fun availableVoices(): List<VoiceProfile> = emptyList()
        override fun selectedVoice(): VoiceProfile = VoiceProfile("f", "F", 0, "F")
        override fun selectVoice(voiceId: String) {}
        override fun isModelInstalled(): Boolean = true
        override fun isLoaded(): Boolean = loaded
        override fun modelSizeMb(): Long = 24L

        override suspend fun load(): Long {
            loaded = true
            return 10L
        }

        override suspend fun synthesize(
            text: String,
            onChunk: (samples: FloatArray, sampleRate: Int) -> Boolean
        ): SynthesisResult {
            onChunk(FloatArray(2205) { 0.1f }, 22050)
            return SynthesisResult(synthMs = 20L, audioDurationMs = 100L)
        }

        override fun cancel() {
            cancelCount.incrementAndGet()
        }

        override fun unload() {
            loaded = false
        }
    }

    private class FakeAudioOutputSink : AudioOutputSink {
        private var playing = false
        private var listener: ((Boolean) -> Unit)? = null
        val stopCount = AtomicInteger(0)

        override val isPlaying: Boolean get() = playing

        override fun setPlaybackStateListener(listener: ((Boolean) -> Unit)?) {
            this.listener = listener
        }

        override fun beginSession(sampleRate: Int) {
            playing = true
            listener?.invoke(true)
        }

        override fun writeSamples(samples: FloatArray, sampleRate: Int): Boolean = true

        override fun finishSessionGracefully() {
            playing = false
            listener?.invoke(false)
        }

        override fun stopImmediately() {
            stopCount.incrementAndGet()
            playing = false
            listener?.invoke(false)
        }

        override fun release() {
            playing = false
        }
    }

    // =========================================================================
    // Phase 0.5 tests: persistent conversations, controlled memory, identity
    // =========================================================================

    @Test
    fun `conversation repository persists turns, auto-titles, summarizes older messages, and deletes cleanly`() = runBlocking {
        val clock = AtomicLong(10_000L)
        val dao = InMemoryConversationDao()
        val repo = ConversationRepository(dao = dao, clockMs = { clock.addAndGet(1_000L) })

        val conv1 = repo.ensureLatestOrCreateConversation()
        val u1 = repo.appendUserMessage(
            conv1,
            "Hola ORION, revisemos el proyecto de voz offline",
            audioMs = 1_500L,
            decodeMs = 210L
        )
        assertEquals(ChatRole.USER, u1.role)

        val latency = TurnLatencyMetrics(
            asrMs = 210L,
            llmFirstTokenMs = 300L,
            llmTotalMs = 1_100L,
            llmTokensGenerated = 18,
            llmTokensPerSec = 16.4,
            ttsSynthMs = 220L,
            synthesizedAudioMs = 1_600L,
            endToSpeechStartMs = 730L,
            responseDurationMs = 1_650L
        )
        repo.appendAssistantMessage(conv1, "Claro, dime qué parte revisamos primero.", latency)

        // Append enough additional turns to trigger rolling summary (> 8 messages)
        for (i in 2..6) {
            repo.appendUserMessage(conv1, "Punto $i sobre memoria local", audioMs = 900L, decodeMs = 150L)
            repo.appendAssistantMessage(conv1, "Anotado punto $i.", latency)
        }

        val turns = repo.loadConversationTurns(conv1)
        assertEquals(12, turns.size)
        assertEquals("Hola ORION, revisemos el proyecto de voz offline", turns.first().text)
        assertEquals(1_100L, turns[1].latency?.llmTotalMs)

        val summary1 = repo.getConversationSummary(conv1)!!
        assertEquals("Hola ORION, revisemos el proyecto de voz…", summary1.title)
        assertTrue(summary1.summaryText.contains("revisemos"))

        // Create second conversation without losing the first
        val conv2 = repo.createConversation()
        repo.appendUserMessage(conv2, "Plan de mañana", audioMs = 800L, decodeMs = 120L)
        val list = repo.observeConversations().first()
        assertEquals(2, list.size)
        assertEquals(conv2, list.first().id)

        // Delete first conversation keeps second intact
        repo.deleteConversation(conv1)
        val remaining = repo.observeConversations().first()
        assertEquals(1, remaining.size)
        assertEquals(conv2, remaining.single().id)
        assertTrue(repo.loadConversationTurns(conv1).isEmpty())
    }

    @Test
    fun `memory repository handles CRUD, near-duplicate merge, and clear all`() = runBlocking {
        val clock = AtomicLong(1_000L)
        val repo = MemoryRepository(dao = InMemoryMemoryDao(), clockMs = { clock.addAndGet(500L) })

        val id1 = repo.addMemory(
            category = MemoryCategory.IDENTITY,
            content = "El usuario prefiere que le llamen Marcos",
            importance = 5,
            sourceConversationId = 1L
        )
        // Near-duplicate must update existing row instead of duplicating
        val id1Dup = repo.addMemory(
            category = MemoryCategory.IDENTITY,
            content = "El usuario prefiere que le llamen Marcos!",
            importance = 4,
            sourceConversationId = 2L
        )
        assertEquals(id1, id1Dup)
        assertEquals(1, repo.getAllMemories().size)
        assertEquals(5, repo.getAllMemories().single().importance)

        val id2 = repo.addMemory(MemoryCategory.PROJECT, "Proyecto activo: ORION Voice Lab", importance = 4)
        assertEquals(2, repo.observeMemories().first().size)

        repo.updateMemory(id2, MemoryCategory.PROJECT, "Proyecto activo: ORION 0.5", importance = 5)
        assertEquals("Proyecto activo: ORION 0.5", repo.getAllMemories().first { it.id == id2 }.content)

        repo.deleteMemory(id1)
        assertEquals(1, repo.getAllMemories().size)

        repo.clearAllMemories()
        assertTrue(repo.getAllMemories().isEmpty())
    }

    @Test
    fun `identity repository initializes defaults, saves sanitized config, toggles memory, and resets`() = runBlocking {
        val clock = AtomicLong(5_000L)
        val repo = IdentityRepository(dao = InMemoryIdentityDao(), clockMs = { clock.addAndGet(100L) })

        val initial = repo.ensureInitialized()
        assertEquals(AssistantIdentity.DEFAULT_NAME, initial.assistantName)
        assertEquals(FormalityLevel.CERCANO, initial.formality)
        assertTrue(initial.memoryEnabled)

        repo.saveIdentity(
            AssistantIdentity(
                assistantName = "Atlas",
                personality = "Técnico y conciso",
                tone = "Directo",
                formality = FormalityLevel.FORMAL,
                basePrompt = "Responde con máxima precisión.",
                memoryEnabled = false
            )
        )
        val saved = repo.observeIdentity().first()
        assertEquals("Atlas", saved.assistantName)
        assertEquals(FormalityLevel.FORMAL, saved.formality)
        assertFalse(saved.memoryEnabled)

        // Blank fields safely fall back to defaults
        repo.saveIdentity(saved.copy(assistantName = "   ", basePrompt = ""))
        val sanitized = repo.getIdentity()
        assertEquals(AssistantIdentity.DEFAULT_NAME, sanitized.assistantName)
        assertEquals(AssistantIdentity.DEFAULT_BASE_PROMPT, sanitized.basePrompt)

        // Reset restores defaults while preserving memoryEnabled preference
        repo.setMemoryEnabled(false)
        repo.resetToDefaults()
        val afterReset = repo.getIdentity()
        assertEquals(AssistantIdentity.DEFAULT_PERSONALITY, afterReset.personality)
        assertEquals(FormalityLevel.CERCANO, afterReset.formality)
        assertFalse(afterReset.memoryEnabled)
    }

    @Test
    fun `memory candidate extractor extracts controlled facts and ignores casual chatter or duplicates`() {
        val nameCandidates = MemoryCandidateExtractor.extractCandidates(
            userUtterance = "Hola, me llamo Carlos y recuerda que trabajo desde casa los viernes",
            existingMemories = emptyList(),
            sourceConversationId = 7L
        )
        assertEquals(2, nameCandidates.size)
        assertTrue(nameCandidates.any { it.category == MemoryCategory.IDENTITY && it.content.contains("Carlos") })
        assertTrue(nameCandidates.any { it.category == MemoryCategory.GENERAL && it.content.contains("viernes") })

        val projectCandidates = MemoryCandidateExtractor.extractCandidates(
            userUtterance = "Estoy trabajando en el asistente offline ORION para Android",
            existingMemories = emptyList()
        )
        assertEquals(1, projectCandidates.size)
        assertEquals(MemoryCategory.PROJECT, projectCandidates.single().category)

        // Casual small-talk must NOT produce unwanted memory clutter
        val smallTalk = MemoryCandidateExtractor.extractCandidates(
            userUtterance = "Qué hora es hoy, gracias",
            existingMemories = emptyList()
        )
        assertTrue(smallTalk.isEmpty())

        // Duplicate against already-saved memory is filtered out
        val existing = listOf(
            MemoryItem(
                id = 1L,
                category = MemoryCategory.IDENTITY,
                content = "El usuario prefiere que le llamen Carlos",
                importance = 5
            )
        )
        val dup = MemoryCandidateExtractor.extractCandidates(
            userUtterance = "Me llamo Carlos",
            existingMemories = existing
        )
        assertTrue(dup.isEmpty())
    }

    @Test
    fun `memory selector and context prompt builder enforce budgets, formality, and anti-hallucination`() {
        val memories = listOf(
            MemoryItem(id = 1L, category = MemoryCategory.IDENTITY, content = "El usuario se llama Elena", importance = 5),
            MemoryItem(id = 2L, category = MemoryCategory.ROUTINE, content = "Rutina: corre a las siete de la mañana", importance = 3),
            MemoryItem(id = 3L, category = MemoryCategory.PROJECT, content = "Proyecto: motor Room para ORION offline", importance = 4),
            MemoryItem(id = 4L, category = MemoryCategory.PREFERENCE, content = "Prefiere respuestas muy cortas sin rodeos", importance = 4)
        )
        val identity = AssistantIdentity(
            assistantName = "ORION",
            personality = "Directo y práctico",
            tone = "Natural",
            formality = FormalityLevel.FORMAL,
            basePrompt = "Asistente local por voz.",
            memoryEnabled = true
        )
        val history = listOf(
            ChatMessage(ChatRole.USER, "Mensaje antiguo 1 " + "x".repeat(400)),
            ChatMessage(ChatRole.ASSISTANT, "Respuesta antigua 1 " + "y".repeat(400)),
            ChatMessage(ChatRole.USER, "¿Cómo llevamos el proyecto Room?"),
            ChatMessage(ChatRole.ASSISTANT, "Listo para integrar.")
        )

        val ctx = ContextPromptBuilder.buildTurnContext(
            identity = identity,
            allMemories = memories,
            conversationSummary = "U: Hablamos de arquitectura offline",
            recentHistory = history,
            currentQuery = "Sigue con el proyecto Room de ORION",
            maxHistoryMessages = 3,
            maxHistoryChars = 500
        )

        assertTrue(ctx.systemPrompt.contains("Tu nombre es ORION."))
        assertTrue(ctx.systemPrompt.contains("Trata de usted"))
        assertTrue(ctx.systemPrompt.contains("nunca inventes hechos"))
        assertTrue(ctx.systemPrompt.contains("Elena"))
        assertTrue(ctx.systemPrompt.contains("motor Room para ORION offline"))
        assertTrue(ctx.systemPrompt.contains("Resumen previo"))
        assertEquals(2, ctx.recentHistory.size) // trimmed by 500 char budget
        assertEquals(memories.first { it.id == 3L }, ctx.usedMemories.first())

        // Memory disabled skips memory injection cleanly
        val noMemCtx = ContextPromptBuilder.buildTurnContext(
            identity = identity.copy(memoryEnabled = false),
            allMemories = memories,
            conversationSummary = "",
            recentHistory = history,
            currentQuery = "¿Cómo me llamo?"
        )
        assertTrue(noMemCtx.usedMemories.isEmpty())
        assertFalse(noMemCtx.systemPrompt.contains("Elena"))
    }

    @Test
    fun `conversation session restores saved turns and engine invokes turn context and commit callbacks`() = runBlocking {
        val session = ConversationSession(maxHistoryMessages = 6)
        session.loadTurns(
            listOf(
                ConversationTurn(id = 10L, role = ChatRole.USER, text = "Hola", timestampMs = 100L),
                ConversationTurn(id = 12L, role = ChatRole.ASSISTANT, text = "Buenas", timestampMs = 200L)
            )
        )
        assertEquals(2, session.historySnapshot().size)
        val next = session.addUserMessage("Seguimos", audioMs = 500L, decodeMs = 40L)
        assertEquals(13L, next.id)

        val fakeInput = FakeVoiceInputController()
        val fakeVad = FakeVad()
        val fakeAsr = FakeSpeechRecognizer()
        val fakeLlm = FakeLanguageModel()
        val fakeTts = FakeSpeechSynthesizer()
        val fakePlayback = FakeAudioOutputSink()
        val nowMs = AtomicLong(1_000L)

        var resolvedPromptSeen = ""
        val engine = ConversationEngine(
            speechRecognizer = fakeAsr,
            languageModel = fakeLlm,
            speechSynthesizer = fakeTts,
            playbackEngine = fakePlayback,
            vadFactory = { fakeVad },
            clockMs = { nowMs.addAndGet(20L) },
            turnContextResolver = { query, history ->
                resolvedPromptSeen = "SYS_CON_MEMORIA:$query:${history.size}"
                TurnContext(systemPrompt = resolvedPromptSeen, recentHistory = history)
            },
            inputFactory = { fakeInput }
        )

        val listeningLatch = CountDownLatch(1)
        val assistantCommittedLatch = CountDownLatch(1)
        val committedUserTexts = CopyOnWriteArrayList<String>()
        val committedAssistantTexts = CopyOnWriteArrayList<String>()

        engine.attachCallbacks(object : ConversationEngine.Callbacks {
            override fun onStateChanged(state: ConversationState, statusText: String) {
                if (state == ConversationState.LISTENING) listeningLatch.countDown()
            }
            override fun onSpeechDetectedChanged(detected: Boolean) {}
            override fun onAudioLevel(level: Float) {}
            override fun onTurnsUpdated(turns: List<ConversationTurn>) {}
            override fun onUserTurnCommitted(turn: ConversationTurn) {
                committedUserTexts.add(turn.text)
            }
            override fun onAssistantTurnCommitted(turn: ConversationTurn) {
                committedAssistantTexts.add(turn.text)
                assistantCommittedLatch.countDown()
            }
            override fun onStreamingText(text: String) {}
            override fun onTelemetryUpdated(telemetry: CallTelemetry) {}
            override fun onModelsInRamChanged(loaded: Boolean) {}
            override fun onCallEnded() {}
        })

        engine.startCall()
        assertTrue(listeningLatch.await(3, TimeUnit.SECONDS))
        fakeVad.enqueueSegment(FloatArray(8_000) { 0.2f })
        fakeInput.emitFrame(FloatArray(512) { 0.2f }, 0.3f)

        assertTrue(assistantCommittedLatch.await(4, TimeUnit.SECONDS))
        assertTrue(resolvedPromptSeen.startsWith("SYS_CON_MEMORIA:Hola ORION"))
        assertEquals(listOf("Hola ORION"), committedUserTexts.toList())
        assertEquals(1, committedAssistantTexts.size)
        engine.shutdown()
    }

    private class InMemoryConversationDao : ConversationDao {
        private var nextConvId = 1L
        private var nextMsgId = 1L
        private val conversations = linkedMapOf<Long, ConversationEntity>()
        private val messages = mutableListOf<ConversationMessageEntity>()
        private val rowsFlow = MutableStateFlow<List<ConversationRowTuple>>(emptyList())

        private fun emit() {
            rowsFlow.value = conversations.values
                .sortedByDescending { it.updatedAtMs }
                .map { conv ->
                    val convMsgs = messages.filter { it.conversationId == conv.id }
                    ConversationRowTuple(
                        id = conv.id,
                        title = conv.title,
                        createdAtMs = conv.createdAtMs,
                        updatedAtMs = conv.updatedAtMs,
                        summaryText = conv.summaryText,
                        messageCount = convMsgs.size,
                        lastPreview = convMsgs.maxByOrNull { it.createdAtMs }?.text
                    )
                }
        }

        override fun observeConversationRows(): Flow<List<ConversationRowTuple>> = rowsFlow

        override suspend fun insertConversation(entity: ConversationEntity): Long {
            val id = nextConvId++
            conversations[id] = entity.copy(id = id)
            emit()
            return id
        }

        override suspend fun getLatestConversation(): ConversationEntity? =
            conversations.values.maxByOrNull { it.updatedAtMs }

        override suspend fun getConversationById(id: Long): ConversationEntity? =
            conversations[id]

        override suspend fun updateTitleAndTimestamp(id: Long, title: String, updatedAtMs: Long) {
            conversations[id]?.let {
                conversations[id] = it.copy(title = title, updatedAtMs = updatedAtMs)
                emit()
            }
        }

        override suspend fun updateTimestampAndSummary(id: Long, updatedAtMs: Long, summaryText: String) {
            conversations[id]?.let {
                conversations[id] = it.copy(updatedAtMs = updatedAtMs, summaryText = summaryText)
                emit()
            }
        }

        override suspend fun deleteConversationById(id: Long) {
            conversations.remove(id)
            messages.removeAll { it.conversationId == id }
            emit()
        }

        override suspend fun insertMessage(entity: ConversationMessageEntity): Long {
            val id = nextMsgId++
            messages.add(entity.copy(id = id))
            emit()
            return id
        }

        override suspend fun getMessagesForConversation(conversationId: Long): List<ConversationMessageEntity> =
            messages.filter { it.conversationId == conversationId }.sortedWith(compareBy({ it.createdAtMs }, { it.id }))
    }

    private class InMemoryMemoryDao : MemoryDao {
        private var nextId = 1L
        private val items = MutableStateFlow<List<MemoryItemEntity>>(emptyList())

        override fun observeAllMemories(): Flow<List<MemoryItemEntity>> =
            items.map { list -> list.sortedWith(compareByDescending<MemoryItemEntity> { it.importance }.thenByDescending { it.updatedAtMs }) }

        override suspend fun getAllMemories(): List<MemoryItemEntity> =
            items.value.sortedWith(compareByDescending<MemoryItemEntity> { it.importance }.thenByDescending { it.updatedAtMs })

        override suspend fun insertMemory(entity: MemoryItemEntity): Long {
            val id = nextId++
            items.value = items.value + entity.copy(id = id)
            return id
        }

        override suspend fun updateMemory(
            id: Long,
            category: String,
            content: String,
            importance: Int,
            updatedAtMs: Long
        ) {
            items.value = items.value.map {
                if (it.id == id) it.copy(category = category, content = content, importance = importance, updatedAtMs = updatedAtMs) else it
            }
        }

        override suspend fun deleteMemoryById(id: Long) {
            items.value = items.value.filterNot { it.id == id }
        }

        override suspend fun deleteAllMemories() {
            items.value = emptyList()
        }
    }

    private class InMemoryIdentityDao : IdentityDao {
        private val flow = MutableStateFlow<AssistantIdentityEntity?>(null)

        override fun observeIdentity(): Flow<AssistantIdentityEntity?> = flow

        override suspend fun getIdentity(): AssistantIdentityEntity? = flow.value

        override suspend fun upsertIdentity(entity: AssistantIdentityEntity) {
            flow.value = entity
        }
    }

    @Test
    fun `contact repository handles coworker CRUD, defaults, and reactive ordering`() = runBlocking {
        val clock = AtomicLong(2_000L)
        val repo = ContactRepository(dao = InMemoryContactDao(), clockMs = { clock.addAndGet(500L) })

        val id1 = repo.addCoworker(
            name = "Vega",
            roleTitle = "Estrategia y Finanzas",
            specialtyPrompt = "Ayuda a evaluar presupuestos y rentabilidad.",
            voiceId = "sharvard_f"
        )
        val id2 = repo.addCoworker(
            name = "Kael",
            roleTitle = "",
            specialtyPrompt = "",
            voiceId = "sharvard_m"
        )

        val initial = repo.observeCoworkers().first()
        assertEquals(2, initial.size)
        assertEquals("Kael", initial.first().name)
        assertEquals(ContactRepository.DEFAULT_ROLE, initial.first().roleTitle)
        assertEquals(ContactRepository.DEFAULT_SPECIALTY_PROMPT, initial.first().specialtyPrompt)

        repo.updateCoworker(
            id = id1,
            name = "Vega Pro",
            roleTitle = "Analista Financiera",
            specialtyPrompt = "Calcula flujo de caja y prioridades.",
            voiceId = "sharvard_f"
        )
        val updated = repo.getAllCoworkers().first { it.id == id1 }
        assertEquals("Vega Pro", updated.name)
        assertEquals("Analista Financiera", updated.roleTitle)

        repo.deleteCoworker(id2)
        val remaining = repo.observeCoworkers().first()
        assertEquals(1, remaining.size)
        assertEquals(id1, remaining.single().id)
    }

    @Test
    fun `primary contact and context prompt builder integrate coworker specialty with ORION identity and memories`() {
        val identity = AssistantIdentity(
            assistantName = "ORION Prime",
            personality = "Analítico y claro",
            tone = "Directo",
            formality = FormalityLevel.CERCANO,
            basePrompt = "Asistente coordinador.",
            memoryEnabled = true
        )
        val primary = ContactProfile.primaryFromIdentity(identity, voiceId = "sharvard_m")
        assertTrue(primary.isPrimary)
        assertEquals(ContactProfile.PRIMARY_CONTACT_ID, primary.id)
        assertEquals("ORION Prime", primary.name)

        val coworker = ContactProfile(
            id = 3L,
            name = "Nora",
            roleTitle = "Arquitecta Android",
            specialtyPrompt = "Revisa código Kotlin y rendimiento offline en móviles.",
            voiceId = "sharvard_f",
            isPrimary = false
        )
        val memories = listOf(
            MemoryItem(id = 1L, category = MemoryCategory.IDENTITY, content = "El usuario se llama Luis", importance = 5),
            MemoryItem(id = 2L, category = MemoryCategory.PROJECT, content = "Proyecto Kotlin offline para Galaxy A17", importance = 4)
        )

        val ctx = ContextPromptBuilder.buildTurnContext(
            identity = identity,
            allMemories = memories,
            conversationSummary = "",
            recentHistory = emptyList(),
            currentQuery = "Revisa el rendimiento Kotlin",
            activeCoworker = coworker
        )

        assertTrue(ctx.systemPrompt.contains("Tu nombre es Nora."))
        assertTrue(ctx.systemPrompt.contains("Arquitecta Android"))
        assertTrue(ctx.systemPrompt.contains("complementa a ORION Prime (ORION Principal)"))
        assertTrue(ctx.systemPrompt.contains("Revisa código Kotlin y rendimiento offline en móviles."))
        assertTrue(ctx.systemPrompt.contains("Luis"))
        assertTrue(ctx.systemPrompt.contains("Tutea de forma natural"))
    }

    private class InMemoryContactDao : ContactDao {
        private var nextId = 1L
        private val items = MutableStateFlow<List<CoworkerContactEntity>>(emptyList())

        override fun observeCoworkers(): Flow<List<CoworkerContactEntity>> =
            items.map { list -> list.sortedWith(compareByDescending<CoworkerContactEntity> { it.updatedAtMs }.thenByDescending { it.id }) }

        override suspend fun getAllCoworkers(): List<CoworkerContactEntity> =
            items.value.sortedWith(compareByDescending<CoworkerContactEntity> { it.updatedAtMs }.thenByDescending { it.id })

        override suspend fun insertCoworker(entity: CoworkerContactEntity): Long {
            val id = nextId++
            items.value = items.value + entity.copy(id = id)
            return id
        }

        override suspend fun updateCoworker(
            id: Long,
            name: String,
            roleTitle: String,
            specialtyPrompt: String,
            voiceId: String,
            updatedAtMs: Long
        ) {
            items.value = items.value.map {
                if (it.id == id) {
                    it.copy(
                        name = name,
                        roleTitle = roleTitle,
                        specialtyPrompt = specialtyPrompt,
                        voiceId = voiceId,
                        updatedAtMs = updatedAtMs
                    )
                } else {
                    it
                }
            }
        }

        override suspend fun deleteCoworkerById(id: Long) {
            items.value = items.value.filterNot { it.id == id }
        }
    }
}
