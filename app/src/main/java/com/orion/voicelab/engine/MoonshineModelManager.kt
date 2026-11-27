package com.orion.voicelab.engine

import com.orion.voicelab.model.DownloadSpec
import com.orion.voicelab.model.ModelManager
import com.orion.voicelab.model.ResumableHttpDownloader
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream

object MoonshineModelManager {
    const val MODEL_DIR_NAME = "sherpa-onnx-moonshine-base-es-quantized-2026-02-27"
    private const val MODEL_ARCHIVE = "$MODEL_DIR_NAME.tar.bz2"
    private const val MODEL_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/$MODEL_ARCHIVE"
    private const val ASR_REQUIRED_FREE_BYTES = 320L * 1024L * 1024L

    private val allowedFiles = setOf(
        "encoder_model.ort",
        "decoder_model_merged.ort",
        "tokens.txt",
        "LICENSE"
    )
    private val installLock = Any()

    fun modelDirectory(filesDir: File): File = File(filesDir, "models/$MODEL_DIR_NAME")

    fun modelFilesPresent(filesDir: File): Boolean {
        ModelManager.recoverBackupIfNeeded(
            targetDir = modelDirectory(filesDir),
            backupDir = File(filesDir, "models/$MODEL_DIR_NAME.bak")
        )
        val dir = modelDirectory(filesDir)
        return File(dir, "encoder_model.ort").let { it.isFile && it.length() > 1_000_000L } &&
            File(dir, "decoder_model_merged.ort").let { it.isFile && it.length() > 1_000_000L } &&
            File(dir, "tokens.txt").let { it.isFile && it.length() > 1_000L }
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
        url: String = MODEL_URL,
        expectedExactBytes: Long? = null,
        expectedSha256: String? = null,
        requiredFreeBytes: Long = ASR_REQUIRED_FREE_BYTES,
        onProgress: (Int) -> Unit,
        onExtracting: () -> Unit
    ) {
        synchronized(installLock) {
            // Limpieza de posibles residuos antiguos en cacheDir
            File(cacheDir, MODEL_ARCHIVE).delete()
            val partArchive = File(ModelManager.downloadsDirectory(filesDir), "$MODEL_ARCHIVE.part")

            try {
                ResumableHttpDownloader.download(
                    spec = DownloadSpec(
                        url = url,
                        destinationPartFile = partArchive,
                        minRequiredFreeBytes = requiredFreeBytes,
                        expectedExactBytes = expectedExactBytes,
                        expectedSha256 = expectedSha256,
                        connectTimeoutMs = 20_000,
                        readTimeoutMs = 60_000
                    ),
                    onProgress = { percent, _, _ -> onProgress(percent) }
                )
                onExtracting()
                extractModelArchive(partArchive, filesDir)
                partArchive.delete()
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

    internal fun extractModelArchive(archive: File, filesDir: File) {
        val targetDir = modelDirectory(filesDir)
        val tempDir = File(filesDir, "models/$MODEL_DIR_NAME.tmp")
        val backupDir = File(filesDir, "models/$MODEL_DIR_NAME.bak")

        ModelManager.recoverBackupIfNeeded(targetDir, backupDir)
        tempDir.deleteRecursively()
        backupDir.deleteRecursively()
        check(tempDir.mkdirs() || tempDir.isDirectory) { "No se pudo crear el directorio del modelo" }

        val canonicalRoot = tempDir.canonicalPath
        try {
            BZip2CompressorInputStream(BufferedInputStream(archive.inputStream())).use { bz ->
                TarArchiveInputStream(bz).use { tar ->
                    while (true) {
                        val entry = tar.nextTarEntry ?: break
                        if (!entry.isFile) continue
                        val safeRelPath = ModelManager.sanitizeArchiveEntryPath(entry) ?: continue
                        val name = safeRelPath.substringAfterLast('/')
                        if (name !in allowedFiles) continue

                        val target = File(tempDir, name)
                        val canonicalTarget = target.canonicalPath
                        check(canonicalTarget.startsWith("$canonicalRoot${File.separator}")) {
                            "Ruta insegura en archivo ASR: ${entry.name}"
                        }
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

