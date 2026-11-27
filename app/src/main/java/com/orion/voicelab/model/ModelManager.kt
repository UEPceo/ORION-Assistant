package com.orion.voicelab.model

import com.orion.voicelab.engine.MoonshineModelManager
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object ModelManager {

    // 1. ASR: Moonshine Base ES v2 (sherpa-onnx)
    const val ASR_DIR_NAME = MoonshineModelManager.MODEL_DIR_NAME

    // 2. LLM: Qwen2.5-1.5B-Instruct Q4_K_M (Apache 2.0, ~1.04 GiB)
    const val LLM_DIR_NAME = "qwen2.5-1.5b-instruct-q4_k_m"
    const val LLM_FILE_NAME = "qwen2.5-1.5b-instruct-q4_k_m.gguf"
    const val LLM_MODEL_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/$LLM_FILE_NAME"
    private const val LLM_MIN_BYTES = 500_000_000L

    // 3. TTS: VITS Piper es_ES Sharvard Medium INT8 (MIT / CC-BY 3.0, ~23.5 MB, 2 voces es_ES)
    const val TTS_DIR_NAME = "vits-piper-es_ES-sharvard-medium-int8"
    const val TTS_ARCHIVE_NAME = "$TTS_DIR_NAME.tar.bz2"
    const val TTS_MODEL_FILE = "es_ES-sharvard-medium.onnx"
    const val TTS_MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/$TTS_ARCHIVE_NAME"
    private const val TTS_MIN_ONNX_BYTES = 5_000_000L

    private const val MB = 1024L * 1024L

    fun asrDirectory(filesDir: File): File = MoonshineModelManager.modelDirectory(filesDir)

    fun llmDirectory(filesDir: File): File = File(filesDir, "models/$LLM_DIR_NAME")

    fun llmModelFile(filesDir: File): File = File(llmDirectory(filesDir), LLM_FILE_NAME)

    fun ttsDirectory(filesDir: File): File = File(filesDir, "models/$TTS_DIR_NAME")

    fun ttsModelFile(filesDir: File): File = File(ttsDirectory(filesDir), TTS_MODEL_FILE)

    fun ttsTokensFile(filesDir: File): File = File(ttsDirectory(filesDir), "tokens.txt")

    fun ttsEspeakDataDir(filesDir: File): File = File(ttsDirectory(filesDir), "espeak-ng-data")

    fun isAsrInstalled(filesDir: File): Boolean = MoonshineModelManager.modelFilesPresent(filesDir)

    fun isLlmInstalled(filesDir: File): Boolean {
        val file = llmModelFile(filesDir)
        return file.isFile && file.length() >= LLM_MIN_BYTES && hasGgufMagicHeader(file)
    }

    fun isTtsInstalled(filesDir: File): Boolean {
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

    fun downloadAndInstallLlm(
        filesDir: File,
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit
    ) {
        val targetDir = llmDirectory(filesDir)
        targetDir.mkdirs()
        val downloadDir = File(filesDir, "models/.downloads").apply { mkdirs() }
        val tempFile = File(downloadDir, "$LLM_FILE_NAME.part")
        val targetFile = llmModelFile(filesDir)

        var existingBytes = if (tempFile.isFile) tempFile.length() else 0L
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LLM_MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 25_000
                readTimeout = 90_000
                instanceFollowRedirects = true
                if (existingBytes > 0L) {
                    setRequestProperty("Range", "bytes=$existingBytes-")
                }
                connect()
            }

            val code = connection.responseCode
            val append = (code == 206 && existingBytes > 0L)
            if (!append) {
                if (code !in 200..299) {
                    error("Descarga LLM HTTP $code")
                }
                existingBytes = 0L
            }

            val contentLen = connection.contentLengthLong
            val totalBytes = if (contentLen > 0L) existingBytes + contentLen else -1L
            val totalMb = if (totalBytes > 0L) totalBytes / MB else 0L

            var copied = existingBytes
            var lastPercent = -1
            connection.inputStream.use { input ->
                FileOutputStream(tempFile, append).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var count: Int
                    while (input.read(buffer).also { count = it } >= 0) {
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        copied += count
                        if (totalBytes > 0L) {
                            val percent = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, copied / MB, totalMb)
                            }
                        }
                    }
                }
            }

            check(tempFile.length() >= LLM_MIN_BYTES) {
                "Archivo GGUF incompleto (${tempFile.length() / MB} MB)"
            }
            check(hasGgufMagicHeader(tempFile)) {
                "El archivo descargado no tiene cabecera GGUF válida"
            }

            if (targetFile.exists()) targetFile.delete()
            check(tempFile.renameTo(targetFile)) {
                "No se pudo activar el modelo LLM descargado"
            }
        } finally {
            connection?.disconnect()
        }
    }

    fun downloadAndInstallTts(
        filesDir: File,
        cacheDir: File,
        onProgress: (percent: Int, downloadedMb: Long, totalMb: Long) -> Unit,
        onExtracting: () -> Unit
    ) {
        val archive = File(cacheDir, TTS_ARCHIVE_NAME)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(TTS_MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                connect()
            }
            if (connection.responseCode !in 200..299) {
                error("Descarga TTS HTTP ${connection.responseCode}")
            }
            val totalBytes = connection.contentLengthLong
            val totalMb = if (totalBytes > 0L) totalBytes / MB else 0L
            var lastPercent = -1

            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var count: Int
                    var copied = 0L
                    while (input.read(buffer).also { count = it } >= 0) {
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        copied += count
                        if (totalBytes > 0L) {
                            val percent = ((copied * 100L) / totalBytes).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent, copied / MB, totalMb)
                            }
                        }
                    }
                }
            }

            onExtracting()
            extractTtsArchive(archive, filesDir)
        } finally {
            archive.delete()
            connection?.disconnect()
        }
    }

    internal fun extractTtsArchive(archive: File, filesDir: File) {
        val targetDir = ttsDirectory(filesDir)
        val tempDir = File(filesDir, "models/$TTS_DIR_NAME.tmp")
        val backupDir = File(filesDir, "models/$TTS_DIR_NAME.bak")
        tempDir.deleteRecursively()
        backupDir.deleteRecursively()
        check(tempDir.mkdirs() || tempDir.isDirectory) { "No se pudo crear el directorio TTS" }

        val canonicalRoot = tempDir.canonicalPath
        try {
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        val cleanName = entry.name.trimStart('/')
                        // Strip optional top-level folder (e.g. vits-piper-es_ES-sharvard-medium-int8/)
                        val relativePath = if ('/' in cleanName) {
                            cleanName.substringAfter('/')
                        } else {
                            cleanName
                        }
                        if (relativePath.isBlank()) continue
                        if (relativePath.split('/').any { it == ".." || it == "." }) continue

                        val isAllowedTopFile = relativePath in setOf(
                            TTS_MODEL_FILE,
                            "$TTS_MODEL_FILE.json",
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

            check(File(tempDir, TTS_MODEL_FILE).length() >= TTS_MIN_ONNX_BYTES) {
                "Falta $TTS_MODEL_FILE válido"
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
                if (hadTarget) backupDir.renameTo(targetDir)
                error("No se pudo activar el modelo TTS descargado")
            }
            backupDir.deleteRecursively()
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
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
