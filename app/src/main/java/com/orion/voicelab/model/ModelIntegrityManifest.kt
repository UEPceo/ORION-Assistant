package com.orion.voicelab.model

import java.io.File
import java.security.MessageDigest
import java.util.Base64

/**
 * Records the cryptographic identity of files extracted from an archive whose
 * SHA-256 was checked against a pinned upstream digest.
 *
 * Small ASR/TTS payloads are re-hashed on every readiness check. Large single
 * file LLM payloads use the recorded size/mtime as a fast-path and are re-hashed
 * whenever either metadata value changes.
 */
internal object ModelIntegrityManifest {
    private const val FILE_NAME = ".orion-integrity-v1"
    private const val HEADER = "ORION-MODEL-INTEGRITY-V1"

    fun createDirectoryManifest(root: File) {
        require(root.isDirectory) { "Directorio de modelo inexistente: ${root.path}" }
        val entries = root.walkTopDown()
            .filter { it.isFile && it.name != FILE_NAME }
            .map { file ->
                val relative = file.relativeTo(root).invariantSeparatorsPath
                Entry(relative, file.length(), file.lastModified(), sha256(file))
            }
            .sortedBy { it.path }
            .toList()
        require(entries.isNotEmpty()) { "No hay archivos para verificar en ${root.path}" }
        write(root, entries)
    }

    fun createSingleFileManifest(root: File, file: File, expectedSha256: String) {
        require(root.isDirectory && file.isFile)
        require(file.canonicalPath.startsWith(root.canonicalPath + File.separator))
        val actual = sha256(file)
        check(actual.equals(expectedSha256, ignoreCase = true)) {
            "SHA-256 del archivo instalado no coincide con el artefacto fijado"
        }
        write(root, listOf(Entry(file.relativeTo(root).invariantSeparatorsPath, file.length(), file.lastModified(), actual)))
    }

    fun verifyDirectory(root: File): Boolean = verify(root, hashEveryFile = true)

    fun verifySingleLargeFile(root: File): Boolean = verify(root, hashEveryFile = false)

    private fun verify(root: File, hashEveryFile: Boolean): Boolean = runCatching {
        val manifest = File(root, FILE_NAME)
        if (!root.isDirectory || !manifest.isFile) return false
        val lines = manifest.readLines(Charsets.UTF_8)
        if (lines.firstOrNull() != HEADER || lines.size < 2) return false
        val entries = lines.drop(1).map { line ->
            val fields = line.split('|')
            require(fields.size == 4)
            val path = String(Base64.getUrlDecoder().decode(fields[0]), Charsets.UTF_8)
            require(path.isNotBlank() && !path.startsWith("/") && path.split('/').none { it == ".." })
            Entry(path, fields[1].toLong(), fields[2].toLong(), fields[3])
        }
        if (entries.map { it.path }.distinct().size != entries.size) return false
        val actualFiles = root.walkTopDown().filter { it.isFile && it.name != FILE_NAME }
            .map { it.relativeTo(root).invariantSeparatorsPath }.toSet()
        if (actualFiles != entries.map { it.path }.toSet()) return false

        entries.all { entry ->
            val file = File(root, entry.path)
            if (!file.isFile || !file.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                false
            } else if (file.length() != entry.length) {
                false
            } else if (!hashEveryFile && file.lastModified() == entry.lastModified) {
                true
            } else {
                sha256(file).equals(entry.sha256, ignoreCase = true)
            }
        }
    }.getOrDefault(false)

    private fun write(root: File, entries: List<Entry>) {
        val target = File(root, FILE_NAME)
        val temporary = File(root, "$FILE_NAME.tmp")
        temporary.writeText(buildString {
            appendLine(HEADER)
            entries.forEach { entry ->
                val encodedPath = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.path.toByteArray(Charsets.UTF_8))
                append(encodedPath)
                append('|')
                append(entry.length)
                append('|')
                append(entry.lastModified)
                append('|')
                appendLine(entry.sha256)
            }
        }, Charsets.UTF_8)
        if (target.exists()) check(target.delete()) { "No se pudo sustituir el manifiesto de integridad" }
        check(temporary.renameTo(target)) { "No se pudo guardar el manifiesto de integridad" }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(128 * 1024).use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private data class Entry(
        val path: String,
        val length: Long,
        val lastModified: Long,
        val sha256: String
    )
}
