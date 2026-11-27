package com.orion.voicelab.knowledge

import android.content.Context
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import java.security.MessageDigest

data class OfflinePackInstallReport(
    val installedPackIds: Set<String>,
    val installedDocuments: Int,
    val skippedPackIds: Set<String>,
    val failedPackIds: Map<String, String>
)

/**
 * Installs source-attributed knowledge bundled in APK assets into the private local corpus.
 * Installation is idempotent and versioned. No network access is performed.
 */
class BundledKnowledgePackInstaller(
    context: Context,
    private val ingestion: KnowledgeIngestionService,
    private val repository: KnowledgeRepository,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    suspend fun installIfNeeded(): OfflinePackInstallReport {
        val installed = linkedSetOf<String>()
        val skipped = linkedSetOf<String>()
        val failed = linkedMapOf<String, String>()
        var documentCount = 0

        for (manifest in KnowledgePackCatalog.packs) {
            try {
                val cachedFile = File(appContext.filesDir, "knowledge_pack_updates/${manifest.id}.json")
                val bundledRaw = appContext.assets.open("knowledge_packs/${manifest.id}.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val cachedHashFile = File(appContext.filesDir, "knowledge_pack_updates/${manifest.id}.sha256")
                val cachedRaw = cachedFile.takeIf { file ->
                    val expected = cachedHashFile.takeIf { it.isFile }?.readText(Charsets.US_ASCII)?.trim()
                    expected != null && expected.matches(Regex("[a-f0-9]{64}")) &&
                        sha256(file.readBytes()).equals(expected, ignoreCase = true)
                }?.readText(Charsets.UTF_8)?.takeIf { candidate ->
                    runCatching { JSONObject(candidate).optInt("version") >= manifest.version }.getOrDefault(false)
                }
                val raw = cachedRaw ?: bundledRaw
                val pack = JSONObject(raw)
                require(pack.optString("id") == manifest.id) { "El identificador del paquete no coincide." }
                val packVersion = pack.optInt("version", 0)
                require(packVersion >= manifest.version) { "La versión remota es anterior al paquete incluido." }
                require(packVersion > 0) { "Versión de paquete inválida." }
                val documents = pack.optJSONArray("documents")
                    ?: error("El paquete no contiene documentos.")
                require(documents.length() > 0) { "El paquete está vacío." }
                val currentVersion = preferences.getInt(versionKey(manifest.id), 0)
                val documentIdsPresent = if (currentVersion >= packVersion) {
                    (0 until documents.length()).all { index ->
                        val url = documents.getJSONObject(index).getString("url")
                        repository.contains(sha256(url).take(32))
                    }
                } else false
                if (documentIdsPresent) {
                    skipped += manifest.id
                    installed += manifest.id
                    continue
                }

                var accepted = 0
                for (index in 0 until documents.length()) {
                    val item = documents.getJSONObject(index)
                    val authority = EvidenceAuthority.valueOf(item.getString("authority"))
                    val domain = KnowledgeDomain.valueOf(item.getString("domain"))
                    val content = item.getString("content").trim()
                    val title = item.getString("title").trim()
                    val url = item.getString("url").trim()
                    require(content.length >= MIN_DOCUMENT_CHARS) { "Documento demasiado corto: $title" }
                    require(content.length <= MAX_DOCUMENT_CHARS) { "Documento demasiado largo: $title" }
                    require(title.isNotBlank() && url.startsWith("https://")) { "Metadatos incompletos: $title" }
                    val publisher = item.getString("publisher").trim()
                    require(publisher.isNotBlank()) { "Falta el editor de la fuente: $title" }
                    val reviewedAt = item.optLong("reviewedAtMs", DEFAULT_REVIEWED_AT_MS)
                    require(reviewedAt in 1L..now() + CLOCK_SKEW_MS) { "Fecha de revisión inválida: $title" }

                    val evidence = KnowledgeEvidence(
                        id = item.optString("id").ifBlank { sha256(url).take(32) },
                        title = title,
                        canonicalUrl = url,
                        publisherId = publisher,
                        domain = domain,
                        authority = authority,
                        retrievedAtMs = reviewedAt,
                        publishedAtMs = item.takeIf { it.has("publishedAtMs") }?.optLong("publishedAtMs"),
                        jurisdiction = item.takeUnless { it.isNull("jurisdiction") }?.optString("jurisdiction")?.takeIf { it.isNotBlank() },
                        content = content,
                        contentHash = sha256(content)
                    )
                    val origin = when (authority) {
                        EvidenceAuthority.OFFICIAL_PRIMARY -> LearningOrigin.OFFICIAL_SOURCE
                        EvidenceAuthority.PRIMARY_TECHNICAL -> LearningOrigin.TECHNICAL_SOURCE
                        EvidenceAuthority.PEER_REVIEWED -> LearningOrigin.PEER_REVIEWED_SOURCE
                        else -> LearningOrigin.REPUTABLE_SECONDARY
                    }
                    val decision = ingestion.ingest(
                        LearningCandidate(
                            document = KnowledgeDocument(evidence, excerpt = content),
                            origin = origin
                        )
                    )
                    require(decision != LearningDecision.REJECT_AS_EVIDENCE) {
                        "El documento no ha superado la validación de ingesta: $title"
                    }
                    accepted++
                }
                require(accepted == documents.length()) { "No se han instalado todos los documentos." }
                preferences.edit().putInt(versionKey(manifest.id), packVersion).apply()
                installed += manifest.id
                documentCount += accepted
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                failed[manifest.id] = error.message?.take(180) ?: error::class.java.simpleName
            }
        }
        return OfflinePackInstallReport(installed, documentCount, skipped, failed)
    }

    fun installedPackIds(): Set<String> = KnowledgePackCatalog.packs
        .filter { preferences.getInt(versionKey(it.id), 0) >= it.version }
        .map { it.id }
        .toSet()

    private fun versionKey(id: String) = "pack_version_$id"

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val PREFERENCES = "orion_offline_knowledge_packs"
        const val MIN_DOCUMENT_CHARS = 180
        const val MAX_DOCUMENT_CHARS = 20_000
        const val CLOCK_SKEW_MS = 5L * 60L * 1000L
        const val DEFAULT_REVIEWED_AT_MS = 1795737600000L
    }
}
