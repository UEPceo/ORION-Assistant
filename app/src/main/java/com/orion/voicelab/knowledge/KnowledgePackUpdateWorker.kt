package com.orion.voicelab.knowledge

import android.content.Context
import android.util.AtomicFile
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import org.json.JSONObject
import com.orion.voicelab.model.ModelManager

/** Downloads only allowlisted, hash-verified knowledge package updates. */
class KnowledgePackUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            // Avoid network, disk and CPU contention while the mandatory offline stack is being installed.
            if (!ModelManager.areCoreModelsInstalled(applicationContext.filesDir)) return Result.retry()
            val manifestText = download(MANIFEST_URL, MAX_MANIFEST_BYTES)
            val manifest = JSONObject(manifestText)
            require(manifest.optInt("schema") == 1) { "Esquema de manifiesto no compatible." }
            val packages = manifest.optJSONArray("packages") ?: error("Falta la lista de paquetes.")
            val updateDirectory = File(applicationContext.filesDir, "knowledge_pack_updates")
            if (!updateDirectory.exists() && !updateDirectory.mkdirs()) {
                error("No se pudo crear el directorio de actualizaciones.")
            }

            for (index in 0 until packages.length()) {
                if (isStopped) return Result.retry()
                val item = packages.getJSONObject(index)
                val id = item.getString("id")
                require(id.matches(Regex("[a-z0-9-]{2,64}"))) { "Identificador de paquete inválido." }
                val version = item.getInt("version")
                require(version > 0) { "Versión inválida." }
                val bundledVersion = KnowledgePackCatalog.packs.firstOrNull { it.id == id }?.version
                    ?: error("Paquete no reconocido.")
                require(version >= bundledVersion) { "El manifiesto intenta ofrecer una versión anterior a la incluida." }
                val url = item.getString("url")
                require(isAllowedPackageUrl(url, id)) { "Origen de paquete no permitido." }
                val expectedHash = item.getString("sha256").lowercase()
                require(expectedHash.matches(Regex("[a-f0-9]{64}"))) { "Hash SHA-256 inválido." }

                val destination = File(updateDirectory, "$id.json")
                val cachedBytes = destination.takeIf { it.isFile }?.readBytes()
                val cachedVersion = cachedBytes?.let {
                    runCatching { JSONObject(it.toString(Charsets.UTF_8)).optInt("version") }.getOrDefault(0)
                } ?: 0
                if (cachedVersion > version) continue
                if (cachedVersion == version && cachedBytes != null && sha256(cachedBytes) == expectedHash) {
                    File(updateDirectory, "$id.sha256").writeText(expectedHash, Charsets.US_ASCII)
                    continue
                }

                val bytes = downloadBytes(url, MAX_PACKAGE_BYTES)
                require(sha256(bytes) == expectedHash) { "La integridad del paquete $id no coincide." }
                val payload = JSONObject(bytes.toString(Charsets.UTF_8))
                require(payload.optString("id") == id && payload.optInt("version") == version) {
                    "Los metadatos del paquete no coinciden con el manifiesto."
                }
                val atomic = AtomicFile(destination)
                val output = atomic.startWrite()
                try {
                    output.write(bytes)
                    atomic.finishWrite(output)
                    File(updateDirectory, "$id.sha256").writeText(expectedHash, Charsets.US_ASCII)
                } catch (error: Exception) {
                    atomic.failWrite(output)
                    throw error
                }
            }

            val report = OrionBrainRuntime(applicationContext).installOfflineKnowledge()
            if (report.failedPackIds.isNotEmpty()) Result.retry() else Result.success()
        } catch (error: Exception) {
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private fun download(url: String, maxBytes: Int): String =
        downloadBytes(url, maxBytes).toString(Charsets.UTF_8)

    private fun downloadBytes(url: String, maxBytes: Int): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            instanceFollowRedirects = false
        }
        try {
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "El servidor respondió HTTP ${connection.responseCode}."
            }
            val length = connection.contentLengthLong
            require(length in -1L..maxBytes.toLong()) { "El recurso supera el tamaño permitido." }
            val bytes = connection.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    require(total <= maxBytes) { "El recurso supera el tamaño permitido." }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            require(bytes.isNotEmpty()) { "El recurso descargado está vacío." }
            return bytes
        } finally {
            connection.disconnect()
        }
    }

    private fun isAllowedPackageUrl(raw: String, id: String): Boolean = runCatching {
        val url = URL(raw)
        url.protocol == "https" &&
            url.host.equals("raw.githubusercontent.com", ignoreCase = true) &&
            url.path == "/UEPceo/ORION-Assistant/main/app/src/main/assets/knowledge_packs/updates/$id.json"
    }.getOrDefault(false)

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MANIFEST_URL = "https://raw.githubusercontent.com/UEPceo/ORION-Assistant/main/app/src/main/assets/knowledge_packs/updates/manifest.json"
        const val MAX_MANIFEST_BYTES = 128 * 1024
        const val MAX_PACKAGE_BYTES = 2 * 1024 * 1024
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val MAX_ATTEMPTS = 5
    }
}
