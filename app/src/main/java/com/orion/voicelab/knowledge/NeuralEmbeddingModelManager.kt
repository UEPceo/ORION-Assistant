package com.orion.voicelab.knowledge

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.coroutineContext

private val DOWNLOAD_MUTEX = Mutex()

/**
 * Explicit, optional download manager for the multilingual ONNX encoder.
 * The model is never bundled in the APK and is never downloaded silently.
 */
class NeuralEmbeddingModelManager(context: Context) {
    private val directory = File(context.applicationContext.filesDir, MODEL_DIRECTORY)
    val modelFile: File get() = File(directory, MODEL_FILE)
    private val partialFile: File get() = File(directory, "$MODEL_FILE.part")
    @Volatile private var verifiedLength: Long = -1L
    @Volatile private var verifiedLastModified: Long = -1L
    private val downloadMutex = DOWNLOAD_MUTEX

    suspend fun isInstalled(): Boolean = withContext(Dispatchers.IO) {
        if (!modelFile.isFile) return@withContext false
        val length = modelFile.length()
        if (length !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) return@withContext false
        val modified = modelFile.lastModified()
        if (verifiedLength == length && verifiedLastModified == modified) return@withContext true
        val valid = sha256(modelFile) == MODEL_SHA256 &&
            modelFile.length() == length && modelFile.lastModified() == modified
        if (valid) {
            verifiedLength = length
            verifiedLastModified = modified
        }
        valid
    }

    suspend fun download(
        onProgress: suspend (downloadedBytes: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        downloadMutex.withLock {
        if (!directory.exists() && !directory.mkdirs()) {
            error("No se pudo crear el directorio privado del encoder.")
        }
        if (isInstalled()) return@withContext modelFile

        var offset = partialFile.takeIf { it.isFile }?.length() ?: 0L
        require(offset <= MAX_MODEL_BYTES) { "La descarga parcial supera el tamaño permitido." }
        val connection = openConnection(MODEL_URL, offset)
        try {
            val response = connection.responseCode
            require(response == HttpURLConnection.HTTP_OK || response == HttpURLConnection.HTTP_PARTIAL) {
                "El servidor del modelo respondió HTTP $response."
            }
            val append = response == HttpURLConnection.HTTP_PARTIAL && offset > 0L
            if (!append) {
                offset = 0L
                if (partialFile.exists()) partialFile.delete()
            }
            val contentLength = connection.contentLengthLong
            val total = if (contentLength > 0L) contentLength + offset else 0L
            require(total <= MAX_MODEL_BYTES || total == 0L) {
                "El encoder supera el tamaño máximo permitido."
            }

            var downloaded = offset
            var lastReported = offset
            connection.inputStream.use { input ->
                FileOutputStream(partialFile, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        downloaded += read
                        require(downloaded <= MAX_MODEL_BYTES) {
                            "La descarga supera el tamaño máximo permitido."
                        }
                        output.write(buffer, 0, read)
                        if (downloaded - lastReported >= 1024L * 1024L || (total > 0L && downloaded >= total)) {
                            onProgress(downloaded, total)
                            lastReported = downloaded
                        }
                    }
                    output.fd.sync()
                }
            }
            require(partialFile.length() >= MIN_MODEL_BYTES) {
                "El archivo descargado está incompleto."
            }
            if (sha256(partialFile) != MODEL_SHA256) {
                partialFile.delete()
                error("La verificación SHA-256 del encoder ha fallado. El archivo no se instalará.")
            }
            if (modelFile.exists() && !modelFile.delete()) {
                error("No se pudo sustituir la versión anterior del encoder.")
            }
            check(partialFile.renameTo(modelFile)) {
                "No se pudo finalizar la instalación del encoder."
            }
            verifiedLength = modelFile.length()
            verifiedLastModified = modelFile.lastModified()
            modelFile
        } catch (error: Exception) {
            // Keep a valid partial file for a later Range request; never expose it as installed.
            throw error
        } finally {
            connection.disconnect()
        }
        }
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        partialFile.delete()
        modelFile.delete()
        verifiedLength = -1L
        verifiedLastModified = -1L
    }

    private fun openConnection(rawUrl: String, offset: Long): HttpURLConnection {
        var current = URL(rawUrl)
        repeat(MAX_REDIRECTS + 1) {
            require(current.protocol == "https" && current.userInfo == null && (current.port == -1 || current.port == 443)) {
                "La URL de descarga no cumple los requisitos HTTPS."
            }
            require(isAllowedHost(current.host)) { "El host de descarga no está permitido." }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Accept", "application/octet-stream")
                if (offset > 0L) setRequestProperty("Range", "bytes=$offset-")
            }
            val status = connection.responseCode
            if (status in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                require(!location.isNullOrBlank()) { "Redirección sin destino." }
                current = URL(current, location)
                require(current.protocol == "https" && current.userInfo == null && (current.port == -1 || current.port == 443)) {
                    "La redirección no cumple los requisitos HTTPS."
                }
                require(isAllowedHost(current.host)) { "Redirección a un host no permitido." }
            } else {
                return connection
            }
        }
        error("Demasiadas redirecciones al descargar el encoder.")
    }

    private fun isAllowedHost(host: String): Boolean {
        val normalized = host.lowercase()
        return normalized == "huggingface.co" ||
            normalized == "hf.co" ||
            normalized == "cdn-lfs.huggingface.co" ||
            normalized == "cas-bridge.xethub.hf.co"
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val MODEL_ID = "distiluse-multilingual-v2-onnx-int8"
        const val MODEL_DIRECTORY = "neural_embeddings"
        const val MODEL_FILE = "multilingual-encoder.onnx"
        const val MODEL_URL = "https://huggingface.co/onnx-community/distiluse-base-multilingual-v2-merged-onnx/resolve/469261b/combined_tokenizer_embedded_model.onnx"
        const val MODEL_SHA256 = "827d1a23e47f8c68a5788e58a06e137edf52aa568a6e4c852f4ce79f21b8a205"
        const val MODEL_LICENSE = "MIT"
        const val MODEL_SIZE_LABEL = "136 MB"
        const val MAX_MODEL_BYTES = 180L * 1024L * 1024L
        const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
        private const val CONNECT_TIMEOUT_MS = 20_000
        private const val READ_TIMEOUT_MS = 60_000
        private const val MAX_REDIRECTS = 4
    }
}
