package com.orion.voicelab.model

import com.orion.voicelab.engine.MoonshineModelManager
import kotlinx.coroutines.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class DownloadSpec(
    val url: String,
    val destinationPartFile: File,
    val minRequiredFreeBytes: Long,
    val expectedExactBytes: Long? = null,
    val expectedSha256: String? = null,
    val connectTimeoutMs: Int = 25_000,
    val readTimeoutMs: Int = 90_000,
    val shouldCancel: () -> Boolean = { Thread.currentThread().isInterrupted }
)

internal data class DownloadOutcome(
    val bytesOnDisk: Long,
    val httpStatusUsed: Int,
    val resumedFromBytes: Long,
    val sha256Hex: String?
)

internal data class ParsedContentRange(
    val start: Long,
    val end: Long,
    val total: Long
) {
    val sliceLength: Long get() = (end - start) + 1L
}

internal object ResumableHttpDownloader {

    private const val MAX_REDIRECTS = 5
    private val contentRangeStrictPattern = Regex("""^bytes\s+(\d+)-(\d+)/(\d+)$""", RegexOption.IGNORE_CASE)
    private val unsatRangePattern = Regex("""^bytes\s+\*/(\d+)$""", RegexOption.IGNORE_CASE)

    fun download(
        spec: DownloadSpec,
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit
    ): DownloadOutcome {
        val partFile = spec.destinationPartFile
        partFile.parentFile?.mkdirs()

        checkCancelled(spec)

        val initialExisting = if (partFile.isFile) partFile.length() else 0L
        val freeSpace = partFile.parentFile?.usableSpace ?: 0L
        val neededAdditional = (spec.minRequiredFreeBytes - initialExisting).coerceAtLeast(16L * 1024L * 1024L)
        check(freeSpace >= neededAdditional) {
            "Espacio insuficiente (${freeSpace / MB} MB libres, necesarios al menos ${neededAdditional / MB} MB)"
        }

        var attempt = 0
        var lastStatus = 0
        var resumedFrom = 0L

        while (attempt < 2) {
            checkCancelled(spec)
            attempt++
            val existingBytes = if (partFile.isFile) partFile.length() else 0L
            var connection: HttpURLConnection? = null
            var shouldDeletePartOnRetry = false

            try {
                connection = openWithRedirects(
                    initialUrl = spec.url,
                    rangeStart = existingBytes.takeIf { it > 0L },
                    connectTimeoutMs = spec.connectTimeoutMs,
                    readTimeoutMs = spec.readTimeoutMs,
                    shouldCancel = spec.shouldCancel
                )

                val code = connection.responseCode
                lastStatus = code

                if (code == 416) {
                    val contentRange = connection.getHeaderField("Content-Range")
                    val remoteTotal = parseUnsatisfiedTotal(contentRange)
                    val coherentWithExpected = spec.expectedExactBytes == null || remoteTotal == spec.expectedExactBytes
                    if (remoteTotal != null && existingBytes > 0L && existingBytes == remoteTotal && coherentWithExpected) {
                        resumedFrom = existingBytes
                        break
                    }
                    partFile.delete()
                    if (attempt < 2) {
                        shouldDeletePartOnRetry = true
                        continue
                    }
                    error("El servidor rechazó el rango (HTTP 416) con tamaño local incompatible")
                }

                val contentType = connection.contentType.orEmpty().lowercase(Locale.US)
                if (contentType.startsWith("text/html")) {
                    partFile.delete()
                    error("Respuesta HTML inesperada al descargar modelo (posible portal cautivo o error remoto)")
                }

                val contentLen = connection.contentLengthLong
                var append = false
                var effectiveExisting = 0L
                var totalBytes = -1L
                var expectedSliceBytes = -1L

                if (code == 206) {
                    if (existingBytes <= 0L) {
                        if (attempt < 2) {
                            shouldDeletePartOnRetry = true
                            continue
                        }
                        error("Respuesta HTTP 206 inesperada sin archivo parcial previo")
                    }

                    val parsedRange = parseStrictContentRange(connection.getHeaderField("Content-Range"))
                    val rangeValid = parsedRange != null &&
                        parsedRange.start == existingBytes &&
                        parsedRange.end >= parsedRange.start &&
                        parsedRange.end < parsedRange.total &&
                        parsedRange.end == parsedRange.total - 1L &&
                        (spec.expectedExactBytes == null || parsedRange.total == spec.expectedExactBytes) &&
                        (contentLen < 0L || contentLen == parsedRange.sliceLength)

                    if (!rangeValid) {
                        partFile.delete()
                        if (attempt < 2) {
                            shouldDeletePartOnRetry = true
                            continue
                        }
                        error(
                            "Cabecera Content-Range inválida o inconsistente en HTTP 206: " +
                                "'${connection.getHeaderField("Content-Range")}'"
                        )
                    }

                    append = true
                    effectiveExisting = existingBytes
                    totalBytes = parsedRange!!.total
                    expectedSliceBytes = parsedRange.sliceLength
                } else if (code == 200) {
                    if (existingBytes > 0L) {
                        // El servidor ignoró Range y envió el archivo completo desde 0: reiniciar .part
                        partFile.delete()
                    }
                    if (spec.expectedExactBytes != null && contentLen > 0L && contentLen != spec.expectedExactBytes) {
                        error("Tamaño remoto ($contentLen bytes) incompatible con el esperado (${spec.expectedExactBytes})")
                    }
                    append = false
                    effectiveExisting = 0L
                    totalBytes = when {
                        contentLen > 0L -> contentLen
                        spec.expectedExactBytes != null -> spec.expectedExactBytes
                        else -> -1L
                    }
                    expectedSliceBytes = if (contentLen > 0L) contentLen else -1L
                } else if (code in 201..299) {
                    if (existingBytes > 0L) partFile.delete()
                    append = false
                    effectiveExisting = 0L
                    totalBytes = if (contentLen > 0L) contentLen else (spec.expectedExactBytes ?: -1L)
                    expectedSliceBytes = if (contentLen > 0L) contentLen else -1L
                } else {
                    error("Descarga HTTP $code")
                }

                resumedFrom = effectiveExisting

                if (totalBytes > 0L) {
                    val remainingNeeded = (totalBytes - effectiveExisting).coerceAtLeast(0L)
                    val availNow = partFile.parentFile?.usableSpace ?: 0L
                    check(availNow >= remainingNeeded) {
                        "Espacio insuficiente durante la descarga (${availNow / MB} MB libres, faltan ${remainingNeeded / MB} MB)"
                    }
                }

                val totalMb = if (totalBytes > 0L) totalBytes / MB else 0L
                var copied = effectiveExisting
                var readThisRequest = 0L
                var lastPercent = -1

                connection.inputStream.use { input ->
                    FileOutputStream(partFile, append).use { output ->
                        val buffer = ByteArray(128 * 1024)
                        var count: Int
                        while (true) {
                            checkCancelled(spec)
                            count = input.read(buffer)
                            if (count < 0) break
                            checkCancelled(spec)
                            if (count == 0) continue
                            output.write(buffer, 0, count)
                            copied += count
                            readThisRequest += count
                            if (totalBytes > 0L) {
                                val percent = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                                if (percent != lastPercent) {
                                    lastPercent = percent
                                    onProgress(percent, copied / MB, totalMb)
                                }
                            }
                        }
                        output.fd.sync()
                    }
                }

                if (expectedSliceBytes >= 0L && readThisRequest != expectedSliceBytes) {
                    if (readThisRequest > expectedSliceBytes) {
                        partFile.delete()
                    }
                    error(
                        "Cuerpo recibido inconsistente con el rango/tamaño declarado: " +
                            "$readThisRequest de $expectedSliceBytes bytes"
                    )
                }

                if (totalBytes > 0L && partFile.length() != totalBytes) {
                    error(
                        "Descarga incompleta: ${partFile.length()} de $totalBytes bytes. Vuelve a pulsar Instalar para reanudar."
                    )
                }
                break
            } finally {
                connection?.disconnect()
                if (shouldDeletePartOnRetry && partFile.exists()) {
                    partFile.delete()
                }
            }
        }

        checkCancelled(spec)

        val actualLength = partFile.length()
        if (spec.expectedExactBytes != null && actualLength != spec.expectedExactBytes) {
            partFile.delete()
            error("Tamaño inesperado del archivo: $actualLength bytes (esperados ${spec.expectedExactBytes})")
        }

        var computedSha256: String? = null
        if (spec.expectedSha256 != null) {
            computedSha256 = sha256Hex(partFile)
            if (!computedSha256.equals(spec.expectedSha256, ignoreCase = true)) {
                partFile.delete()
                error(
                    "Verificación SHA-256 fallida (esperado ${spec.expectedSha256.take(12)}…, obtenido ${computedSha256.take(12)}…)"
                )
            }
        }

        return DownloadOutcome(
            bytesOnDisk = actualLength,
            httpStatusUsed = lastStatus,
            resumedFromBytes = resumedFrom,
            sha256Hex = computedSha256
        )
    }

    private fun openWithRedirects(
        initialUrl: String,
        rangeStart: Long?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        shouldCancel: () -> Boolean
    ): HttpURLConnection {
        var currentUrl = URL(initialUrl)
        var redirects = 0
        while (true) {
            if (shouldCancel() || Thread.currentThread().isInterrupted) {
                throw CancellationException("Descarga cancelada")
            }
            val conn = (currentUrl.openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                if (rangeStart != null && rangeStart > 0L) {
                    setRequestProperty("Range", "bytes=$rangeStart-")
                }
                connect()
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_MOVED_PERM ||
                code == HttpURLConnection.HTTP_MOVED_TEMP ||
                code == HttpURLConnection.HTTP_SEE_OTHER ||
                code == 307 ||
                code == 308
            ) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) {
                    error("Redirección HTTP $code sin cabecera Location")
                }
                if (++redirects > MAX_REDIRECTS) {
                    error("Demasiadas redirecciones HTTP al descargar modelo")
                }
                val nextUrl = URL(currentUrl, location)
                if (currentUrl.protocol.equals("https", ignoreCase = true) &&
                    nextUrl.protocol.equals("http", ignoreCase = true)
                ) {
                    error("Redirección insegura de HTTPS a HTTP rechazada")
                }
                currentUrl = nextUrl
                continue
            }
            return conn
        }
    }

    private fun checkCancelled(spec: DownloadSpec) {
        if (spec.shouldCancel() || Thread.currentThread().isInterrupted) {
            throw CancellationException("Descarga cancelada")
        }
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(128 * 1024)
        FileInputStream(file).use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } >= 0) {
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    internal fun parseStrictContentRange(header: String?): ParsedContentRange? {
        if (header.isNullOrBlank()) return null
        val match = contentRangeStrictPattern.find(header.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull() ?: return null
        return ParsedContentRange(start = start, end = end, total = total)
    }

    internal fun parseRangeStart(header: String?): Long? =
        parseStrictContentRange(header)?.start

    internal fun parseRangeTotal(header: String?): Long? =
        parseStrictContentRange(header)?.total

    internal fun parseUnsatisfiedTotal(header: String?): Long? {
        if (header.isNullOrBlank()) return null
        val match = unsatRangePattern.find(header.trim()) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private const val MB = 1024L * 1024L
}

object ModelManager {

    // 1. ASR: Moonshine Base ES v2 (sherpa-onnx) — sin checksum.txt oficial publicado en tag asr-models
    const val ASR_DIR_NAME = MoonshineModelManager.MODEL_DIR_NAME

    // 2. LLM: Qwen2.5-1.5B-Instruct Q4_K_M (Apache 2.0) — tamaño y SHA-256 oficiales Git LFS de HuggingFace
    const val LLM_DIR_NAME = "qwen2.5-1.5b-instruct-q4_k_m"
    const val LLM_FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val LLM_MODEL_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/$LLM_FILE_NAME"
    const val LLM_EXACT_BYTES = 1_117_320_736L
    const val LLM_SHA256 = "6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e"
    private const val LLM_REQUIRED_FREE_BYTES = 1_250L * 1024L * 1024L

    // 3. TTS: VITS Piper es_ES Sharvard Medium INT8 (MIT / CC-BY 3.0) — sin checksum.txt oficial en tag tts-models
    const val TTS_DIR_NAME = "vits-piper-es_ES-sharvard-medium-int8"
    const val TTS_ARCHIVE_NAME = "$TTS_DIR_NAME.tar.bz2"
    const val TTS_MODEL_FILE = "es_ES-sharvard-medium.onnx"
    const val TTS_MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$TTS_ARCHIVE_NAME"
    private const val TTS_MIN_ONNX_BYTES = 5_000_000L
    private const val TTS_REQUIRED_FREE_BYTES = 120L * 1024L * 1024L

    private const val MB = 1024L * 1024L
    private val inFlightLocks = ConcurrentHashMap<String, Any>()

    private fun modelLock(key: String): Any = inFlightLocks.getOrPut(key) { Any() }

    fun asrDirectory(filesDir: File): File = MoonshineModelManager.modelDirectory(filesDir)

    fun llmDirectory(filesDir: File): File = File(filesDir, "models/$LLM_DIR_NAME")

    fun llmModelFile(filesDir: File): File = File(llmDirectory(filesDir), LLM_FILE_NAME)

    fun llmBackupFile(filesDir: File): File = File(llmDirectory(filesDir), "$LLM_FILE_NAME.bak")

    fun ttsDirectory(filesDir: File): File = File(filesDir, "models/$TTS_DIR_NAME")

    fun ttsVoiceDirectory(filesDir: File, directoryName: String): File = File(filesDir, "models/$directoryName")

    fun ttsVoiceModelFile(filesDir: File, directoryName: String, modelFileName: String): File =
        File(ttsVoiceDirectory(filesDir, directoryName), modelFileName)

    fun isTtsVoiceInstalled(filesDir: File, directoryName: String, modelFileName: String): Boolean {
        val directory = ttsVoiceDirectory(filesDir, directoryName)
        recoverBackupIfNeeded(directory, File(filesDir, "models/$directoryName.bak"))
        val model = File(directory, modelFileName)
        val tokens = File(directory, "tokens.txt")
        val espeak = File(directory, "espeak-ng-data")
        return model.isFile && model.length() >= TTS_MIN_ONNX_BYTES &&
            tokens.isFile && tokens.length() > 100L && espeak.isDirectory &&
            (espeak.listFiles()?.isNotEmpty() == true)
    }

    fun ttsModelFile(filesDir: File): File = File(ttsDirectory(filesDir), TTS_MODEL_FILE)

    fun ttsTokensFile(filesDir: File): File = File(ttsDirectory(filesDir), "tokens.txt")

    fun ttsEspeakDataDir(filesDir: File): File = File(ttsDirectory(filesDir), "espeak-ng-data")

    fun downloadsDirectory(filesDir: File): File = File(filesDir, "models/.downloads")

    fun isAsrInstalled(filesDir: File): Boolean = MoonshineModelManager.modelFilesPresent(filesDir)

    fun isLlmInstalled(filesDir: File, expectedExactBytes: Long = LLM_EXACT_BYTES): Boolean {
        val targetFile = llmModelFile(filesDir)
        recoverFileBackupIfNeeded(targetFile, llmBackupFile(filesDir))
        recoverFileBackupIfNeeded(targetFile, File(llmDirectory(filesDir), "$LLM_FILE_NAME.new"))
        return targetFile.isFile &&
            targetFile.length() == expectedExactBytes &&
            hasGgufMagicHeader(targetFile)
    }

    fun isTtsInstalled(filesDir: File): Boolean {
        recoverBackupIfNeeded(
            targetDir = ttsDirectory(filesDir),
            backupDir = File(filesDir, "models/$TTS_DIR_NAME.bak")
        )
        val model = ttsModelFile(filesDir)
        val tokens = ttsTokensFile(filesDir)
        val espeak = ttsEspeakDataDir(filesDir)
        return model.isFile &&
            model.length() >= TTS_MIN_ONNX_BYTES &&
            tokens.isFile &&
            tokens.length() > 100L &&
            espeak.isDirectory &&
            (espeak.listFiles()?.isNotEmpty() == true)
    }

    fun asrSizeMb(filesDir: File): Long = MoonshineModelManager.modelSizeMb(filesDir)

    fun llmSizeMb(filesDir: File): Long {
        val f = llmModelFile(filesDir)
        return if (f.isFile) f.length() / MB else 0L
    }

    fun ttsSizeMb(filesDir: File): Long = directorySizeMb(ttsDirectory(filesDir))

    fun hasLlmPartialDownload(filesDir: File): Boolean =
        File(downloadsDirectory(filesDir), "$LLM_FILE_NAME.part").let { it.isFile && it.length() > 0L }

    fun downloadAndInstallLlm(
        filesDir: File,
        url: String = LLM_MODEL_URL,
        expectedExactBytes: Long? = LLM_EXACT_BYTES,
        expectedSha256: String? = LLM_SHA256,
        requiredFreeBytes: Long = LLM_REQUIRED_FREE_BYTES,
        shouldCancel: () -> Boolean = { Thread.currentThread().isInterrupted },
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit
    ) {
        synchronized(modelLock("llm")) {
            val targetDir = llmDirectory(filesDir).apply { mkdirs() }
            val targetFile = llmModelFile(filesDir)
            val backupFile = llmBackupFile(filesDir)
            recoverFileBackupIfNeeded(targetFile, backupFile)
            recoverFileBackupIfNeeded(targetFile, File(targetDir, "$LLM_FILE_NAME.new"))
            if (targetFile.isFile) {
                backupFile.delete()
                File(targetDir, "$LLM_FILE_NAME.new").delete()
            }

            val partFile = File(downloadsDirectory(filesDir), "$LLM_FILE_NAME.part")

            ResumableHttpDownloader.download(
                spec = DownloadSpec(
                    url = url,
                    destinationPartFile = partFile,
                    minRequiredFreeBytes = requiredFreeBytes,
                    expectedExactBytes = expectedExactBytes,
                    expectedSha256 = expectedSha256,
                    shouldCancel = shouldCancel
                ),
                onProgress = onProgress
            )

            if (!hasGgufMagicHeader(partFile)) {
                partFile.delete()
                error("El archivo descargado no tiene cabecera GGUF válida")
            }

            val hadTarget = targetFile.exists()
            if (hadTarget) {
                check(targetFile.renameTo(backupFile)) { "No se pudo preparar el reemplazo del modelo LLM" }
            }
            if (!partFile.renameTo(targetFile)) {
                if (hadTarget && backupFile.exists()) backupFile.renameTo(targetFile)
                error("No se pudo activar el modelo LLM descargado")
            }
            backupFile.delete()
        }
    }

    fun downloadAndInstallTts(
        filesDir: File,
        cacheDir: File,
        url: String = TTS_MODEL_URL,
        expectedExactBytes: Long? = null,
        expectedSha256: String? = null,
        requiredFreeBytes: Long = TTS_REQUIRED_FREE_BYTES,
        shouldCancel: () -> Boolean = { Thread.currentThread().isInterrupted },
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit,
        onExtracting: () -> Unit
    ) {
        synchronized(modelLock("tts")) {
            File(cacheDir, TTS_ARCHIVE_NAME).delete()
            val partArchive = File(downloadsDirectory(filesDir), "$TTS_ARCHIVE_NAME.part")

            try {
                ResumableHttpDownloader.download(
                    spec = DownloadSpec(
                        url = url,
                        destinationPartFile = partArchive,
                        minRequiredFreeBytes = requiredFreeBytes,
                        expectedExactBytes = expectedExactBytes,
                        expectedSha256 = expectedSha256,
                        connectTimeoutMs = 20_000,
                        readTimeoutMs = 60_000,
                        shouldCancel = shouldCancel
                    ),
                    onProgress = onProgress
                )

                if (shouldCancel() || Thread.currentThread().isInterrupted) {
                    throw CancellationException("Descarga cancelada")
                }

                onExtracting()
                extractTtsArchive(partArchive, filesDir)
                partArchive.delete()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.message?.contains("tar", ignoreCase = true) == true ||
                    e.message?.contains("bzip2", ignoreCase = true) == true ||
                    e.message?.contains("Corrupt", ignoreCase = true) == true ||
                    e.message?.contains("Falta", ignoreCase = true) == true
                ) {
                    partArchive.delete()
                }
                throw e
            }
        }
    }

    fun downloadAndInstallTtsVoice(
        filesDir: File,
        directoryName: String,
        archiveName: String,
        modelFileName: String,
        url: String,
        shouldCancel: () -> Boolean = { Thread.currentThread().isInterrupted },
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit,
        onExtracting: () -> Unit
    ) {
        require(directoryName.matches(Regex("[a-zA-Z0-9._-]{3,100}")))
        require(archiveName.matches(Regex("[a-zA-Z0-9._-]{3,140}")))
        require(modelFileName.matches(Regex("[a-zA-Z0-9._-]{3,140}")))
        require(url.startsWith("https://"))
        synchronized(modelLock("tts-voice-$directoryName")) {
            if (isTtsVoiceInstalled(filesDir, directoryName, modelFileName)) return
            val partArchive = File(downloadsDirectory(filesDir), "$archiveName.part")
            try {
                ResumableHttpDownloader.download(
                    spec = DownloadSpec(
                        url = url,
                        destinationPartFile = partArchive,
                        minRequiredFreeBytes = TTS_REQUIRED_FREE_BYTES,
                        connectTimeoutMs = 20_000,
                        readTimeoutMs = 60_000,
                        shouldCancel = shouldCancel
                    ),
                    onProgress = onProgress
                )
                if (shouldCancel() || Thread.currentThread().isInterrupted) {
                    throw CancellationException("Descarga de voz cancelada")
                }
                onExtracting()
                extractTtsArchive(partArchive, filesDir, directoryName, modelFileName)
                partArchive.delete()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (error.message?.contains("tar", true) == true ||
                    error.message?.contains("bzip2", true) == true ||
                    error.message?.contains("Corrupt", true) == true ||
                    error.message?.contains("Falta", true) == true
                ) partArchive.delete()
                throw error
            }
        }
    }

    internal fun extractTtsArchive(
        archive: File,
        filesDir: File,
        targetDirectoryName: String = TTS_DIR_NAME,
        targetModelFileName: String = TTS_MODEL_FILE
    ) {
        require(targetDirectoryName.matches(Regex("[a-zA-Z0-9._-]{3,100}")))
        require(targetModelFileName.matches(Regex("[a-zA-Z0-9._-]{3,140}")))
        val targetDir = ttsVoiceDirectory(filesDir, targetDirectoryName)
        val tempDir = File(filesDir, "models/$targetDirectoryName.tmp")
        val backupDir = File(filesDir, "models/$targetDirectoryName.bak")

        recoverBackupIfNeeded(targetDir, backupDir)
        tempDir.deleteRecursively()
        if (targetDir.exists()) backupDir.deleteRecursively()
        check(tempDir.mkdirs() || tempDir.isDirectory) { "No se pudo crear el directorio TTS" }

        val canonicalRoot = tempDir.canonicalPath
        try {
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val relativePath = sanitizeArchiveEntryPath(entry) ?: continue

                        val isAllowedTopFile = relativePath in setOf(
                            targetModelFileName,
                            "$targetModelFileName.json",
                            "tokens.txt",
                            "MODEL_CARD"
                        )
                        val isEspeakEntry = relativePath == "espeak-ng-data" ||
                            relativePath.startsWith("espeak-ng-data/")
                        if (!isAllowedTopFile && !isEspeakEntry) continue

                        val outFile = File(tempDir, relativePath)
                        val canonicalOut = outFile.canonicalPath
                        check(
                            canonicalOut == canonicalRoot ||
                                canonicalOut.startsWith("$canonicalRoot${File.separator}")
                        ) { "Ruta insegura en archivo TTS: $relativePath" }

                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else if (entry.isFile) {
                            outFile.parentFile?.mkdirs()
                            FileOutputStream(outFile).use { out -> tar.copyTo(out) }
                        }
                    }
                }
            }

            check(File(tempDir, targetModelFileName).length() >= TTS_MIN_ONNX_BYTES) {
                "Falta $targetModelFileName válido"
            }
            check(File(tempDir, "tokens.txt").length() > 100L) {
                "Falta tokens.txt de TTS"
            }
            val espeakDir = File(tempDir, "espeak-ng-data")
            check(espeakDir.isDirectory && (espeakDir.listFiles()?.isNotEmpty() == true)) {
                "Falta directorio espeak-ng-data"
            }

            val hadTarget = targetDir.exists()
            if (hadTarget) {
                check(targetDir.renameTo(backupDir)) { "No se pudo actualizar el modelo TTS" }
            }
            if (!tempDir.renameTo(targetDir)) {
                if (hadTarget && backupDir.exists()) backupDir.renameTo(targetDir)
                error("No se pudo activar el modelo TTS descargado")
            }
            backupDir.deleteRecursively()
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }

    internal fun sanitizeArchiveEntryPath(entry: TarArchiveEntry): String? {
        if (entry.isSymbolicLink || entry.isLink) return null
        val raw = entry.name.replace('\\', '/')
        if (raw.contains('\u0000') || raw.startsWith("/")) return null
        val withoutDotPrefix = if (raw.startsWith("./")) raw.removePrefix("./") else raw
        val rawSegments = withoutDotPrefix.split('/')
        if (rawSegments.any { it == ".." }) return null
        val relativePath = if ('/' in withoutDotPrefix) withoutDotPrefix.substringAfter('/') else withoutDotPrefix
        if (relativePath.isBlank()) return null
        val relSegments = relativePath.split('/').filter { it.isNotEmpty() && it != "." }
        return if (relSegments.isEmpty()) null else relSegments.joinToString("/")
    }

    internal fun recoverBackupIfNeeded(targetDir: File, backupDir: File) {
        if (!targetDir.exists() && backupDir.isDirectory) {
            backupDir.renameTo(targetDir)
        }
    }

    internal fun recoverFileBackupIfNeeded(targetFile: File, backupFile: File) {
        if (!targetFile.exists() && backupFile.isFile) {
            backupFile.renameTo(targetFile)
        }
    }

    internal fun hasGgufMagicHeader(file: File): Boolean {
        if (!file.isFile || file.length() < 4L) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                if (input.read(magic) != 4) return false
                magic[0] == 'G'.code.toByte() &&
                    magic[1] == 'G'.code.toByte() &&
                    magic[2] == 'U'.code.toByte() &&
                    magic[3] == 'F'.code.toByte()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun directorySizeMb(dir: File): Long =
        if (dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / MB
        } else {
            0L
        }
}
