package com.orion.voicelab.engine

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object MoonshineModelManager {
    const val MODEL_DIR_NAME = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27"
    private const val MODEL_ARCHIVE = "$MODEL_DIR_NAME.tar.bz2"
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$MODEL_ARCHIVE"

    private val allowedFiles = setOf(
        "encoder_model.ort",
        "decoder_model_merged.ort",
        "tokens.txt",
        "LICENSE"
    )

    fun modelDirectory(filesDir: File): File = File(filesDir, "models/$MODEL_DIR_NAME")

    fun modelFilesPresent(filesDir: File): Boolean {
        val dir = modelDirectory(filesDir)
        return File(dir, "encoder_model.ort").isFile &&
            File(dir, "decoder_model_merged.ort").isFile &&
            File(dir, "tokens.txt").isFile
    }

    fun modelSizeMb(filesDir: File): Long {
        val dir = modelDirectory(filesDir)
        return if (dir.isDirectory) {
            dir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024L * 1024L)
        } else {
            0L
        }
    }

    fun downloadAndInstall(
        filesDir: File,
        cacheDir: File,
        onProgress: (Int) -> Unit,
        onExtracting: () -> Unit
    ) {
        val archive = File(cacheDir, MODEL_ARCHIVE)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                instanceFollowRedirects = true
                connect()
            }
            if (connection.responseCode !in 200..299) {
                error("Descarga HTTP ${connection.responseCode}")
            }
            val total = connection.contentLengthLong
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
                        if (total > 0) {
                            val percent = ((copied * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
            onExtracting()
            extractModelArchive(archive, filesDir)
        } finally {
            archive.delete()
            connection?.disconnect()
        }
    }

    internal fun extractModelArchive(archive: File, filesDir: File) {
        val targetDir = modelDirectory(filesDir)
        val tempDir = File(filesDir, "models/$MODEL_DIR_NAME.tmp")
        val backupDir = File(filesDir, "models/$MODEL_DIR_NAME.bak")
        tempDir.deleteRecursively()
        backupDir.deleteRecursively()
        check(tempDir.mkdirs() || tempDir.isDirectory) { "No se pudo crear el directorio del modelo" }

        try {
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isFile) continue
                        val name = entry.name.substringAfterLast('/')
                        if (name !in allowedFiles) continue
                        val target = File(tempDir, name)
                        FileOutputStream(target).use { out -> tar.copyTo(out) }
                    }
                }
            }

            check(File(tempDir, "encoder_model.ort").length() > 1_000_000) { "Falta encoder_model.ort" }
            check(File(tempDir, "decoder_model_merged.ort").length() > 1_000_000) { "Falta decoder_model_merged.ort" }
            check(File(tempDir, "tokens.txt").length() > 1_000) { "Falta tokens.txt" }

            val hadTarget = targetDir.exists()
            if (hadTarget) {
                check(targetDir.renameTo(backupDir)) { "No se pudo preparar la actualización del modelo" }
            }
            if (!tempDir.renameTo(targetDir)) {
                if (hadTarget) backupDir.renameTo(targetDir)
                error("No se pudo activar el modelo descargado")
            }
            backupDir.deleteRecursively()
        } catch (e: Exception) {
            tempDir.deleteRecursively()
            throw e
        }
    }
}
