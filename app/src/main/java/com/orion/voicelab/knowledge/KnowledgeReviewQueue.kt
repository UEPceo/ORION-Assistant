package com.orion.voicelab.knowledge

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

enum class KnowledgeReviewStatus { PENDING, PUBLISHED_VERIFIED, PUBLISHED_UNVERIFIED, REJECTED, ROLLED_BACK }

data class KnowledgeReviewItem(
    val id: String,
    val document: KnowledgeDocument,
    val origin: LearningOrigin,
    val decision: LearningDecision,
    val submittedAtMs: Long,
    val status: KnowledgeReviewStatus
)

interface KnowledgeReviewQueue {
    suspend fun enqueue(document: KnowledgeDocument, origin: LearningOrigin, decision: LearningDecision)
    suspend fun pending(): List<KnowledgeReviewItem>
}

/**
 * Persistent quarantine separate from the active retrieval corpus. Publication requires
 * an explicit human action; previous active versions are retained for rollback.
 */
class KnowledgeReviewQueueStore(
    context: Context,
    private val corpus: KnowledgeRepository,
    private val nowMs: () -> Long = System::currentTimeMillis
) : KnowledgeReviewQueue {
    private val root = context.applicationContext.filesDir
    private val queueFile = AtomicFile(File(root, "orion-knowledge-review.json"))
    private val historyFile = AtomicFile(File(root, "orion-knowledge-history.json"))
    private val mutex = Mutex()

    override suspend fun enqueue(document: KnowledgeDocument, origin: LearningOrigin, decision: LearningDecision) =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val current = readQueue().toMutableList()
                val existing = current.indexOfFirst { it.id == document.evidence.id && it.status == KnowledgeReviewStatus.PENDING }
                val item = KnowledgeReviewItem(
                    id = document.evidence.id,
                    document = document,
                    origin = origin,
                    decision = decision,
                    submittedAtMs = nowMs(),
                    status = KnowledgeReviewStatus.PENDING
                )
                if (existing >= 0) current[existing] = item else current.add(item)
                require(current.count { it.status == KnowledgeReviewStatus.PENDING } <= MAX_PENDING) {
                    "La bandeja de conocimiento está llena; revisa o rechaza candidatos pendientes."
                }
                writeQueue(current)
            }
        }

    override suspend fun pending(): List<KnowledgeReviewItem> = withContext(Dispatchers.IO) {
        mutex.withLock { readQueue().filter { it.status == KnowledgeReviewStatus.PENDING }.sortedByDescending { it.submittedAtMs } }
    }

    suspend fun approve(id: String, userConfirmed: Boolean): Boolean = publish(id, userConfirmed, verified = true)

    suspend fun publishUnverified(id: String, userConfirmed: Boolean): Boolean = publish(id, userConfirmed, verified = false)

    private suspend fun publish(id: String, userConfirmed: Boolean, verified: Boolean): Boolean = withContext(Dispatchers.IO) {
        require(userConfirmed) { "La publicación necesita confirmación expresa." }
        mutex.withLock {
            val queue = readQueue().toMutableList()
            val index = queue.indexOfFirst { it.id == id && it.status == KnowledgeReviewStatus.PENDING }
            if (index < 0) return@withLock false
            val item = queue[index]
            if (verified && item.decision != LearningDecision.ACCEPT_FOR_REVIEW) return@withLock false
            if (!verified && item.decision == LearningDecision.REJECT_AS_EVIDENCE) return@withLock false
            val previous = corpus.get(id)
            if (previous != null) appendHistory(id, previous)
            corpus.upsert(item.document)
            queue[index] = item.copy(
                status = if (verified) KnowledgeReviewStatus.PUBLISHED_VERIFIED else KnowledgeReviewStatus.PUBLISHED_UNVERIFIED
            )
            writeQueue(queue)
            true
        }
    }

    suspend fun reject(id: String, userConfirmed: Boolean): Boolean = withContext(Dispatchers.IO) {
        require(userConfirmed) { "El rechazo necesita confirmación expresa." }
        mutex.withLock {
            val queue = readQueue().toMutableList()
            val index = queue.indexOfFirst { it.id == id && it.status == KnowledgeReviewStatus.PENDING }
            if (index < 0) return@withLock false
            queue[index] = queue[index].copy(status = KnowledgeReviewStatus.REJECTED)
            writeQueue(queue)
            true
        }
    }

    suspend fun rollback(documentId: String, userConfirmed: Boolean): Boolean = withContext(Dispatchers.IO) {
        require(userConfirmed) { "La reversión necesita confirmación expresa." }
        mutex.withLock {
            val history = readHistory().toMutableMap()
            val versions = history[documentId].orEmpty().toMutableList()
            val previous = versions.removeLastOrNull()
            if (previous == null) {
                if (!corpus.contains(documentId)) return@withLock false
                corpus.remove(documentId)
            } else {
                corpus.upsert(previous)
                if (versions.isEmpty()) history.remove(documentId) else history[documentId] = versions
            }
            writeHistory(history)
            val queue = readQueue().map { item ->
                if (item.id == documentId && item.status in setOf(
                    KnowledgeReviewStatus.PUBLISHED_VERIFIED,
                    KnowledgeReviewStatus.PUBLISHED_UNVERIFIED
                )) item.copy(status = KnowledgeReviewStatus.ROLLED_BACK) else item
            }
            writeQueue(queue)
            true
        }
    }

    private fun appendHistory(id: String, document: KnowledgeDocument) {
        val history = readHistory().toMutableMap()
        val versions = history[id].orEmpty().toMutableList()
        if (versions.lastOrNull() != document) versions.add(document)
        history[id] = versions.takeLast(MAX_VERSIONS_PER_DOCUMENT)
        writeHistory(history)
    }

    private fun readQueue(): List<KnowledgeReviewItem> = runCatching {
        val array = JSONArray(readText(queueFile))
        buildList {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val document = decodeDocument(item.optJSONObject("document") ?: continue)
                val origin = runCatching { LearningOrigin.valueOf(item.optString("origin")) }.getOrNull() ?: continue
                val decision = runCatching { LearningDecision.valueOf(item.optString("decision")) }.getOrNull() ?: continue
                val status = runCatching { KnowledgeReviewStatus.valueOf(item.optString("status")) }.getOrDefault(KnowledgeReviewStatus.PENDING)
                add(KnowledgeReviewItem(item.optString("id"), document, origin, decision, item.optLong("submittedAt"), status))
            }
        }
    }.getOrDefault(emptyList())

    private fun writeQueue(items: List<KnowledgeReviewItem>) {
        val array = JSONArray()
        items.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("origin", item.origin.name)
                .put("decision", item.decision.name)
                .put("submittedAt", item.submittedAtMs)
                .put("status", item.status.name)
                .put("document", encodeDocument(item.document)))
        }
        writeText(queueFile, array.toString())
    }

    private fun readHistory(): Map<String, List<KnowledgeDocument>> = runCatching {
        val root = JSONObject(readText(historyFile))
        buildMap {
            val keys = root.keys()
            while (keys.hasNext()) {
                val id = keys.next()
                val array = root.optJSONArray(id) ?: continue
                put(id, buildList {
                    for (i in 0 until array.length()) array.optJSONObject(i)?.let { add(decodeDocument(it)) }
                })
            }
        }
    }.getOrDefault(emptyMap())

    private fun writeHistory(history: Map<String, List<KnowledgeDocument>>) {
        val root = JSONObject()
        history.toSortedMap().forEach { (id, versions) ->
            val array = JSONArray()
            versions.forEach { array.put(encodeDocument(it)) }
            root.put(id, array)
        }
        writeText(historyFile, root.toString())
    }

    private fun encodeDocument(document: KnowledgeDocument): JSONObject {
        val e = document.evidence
        val conflicts = JSONArray()
        document.conflictsWithIds.forEach(conflicts::put)
        return JSONObject()
            .put("id", e.id).put("title", e.title).put("url", e.canonicalUrl)
            .put("publisher", e.publisherId).put("domain", e.domain.name)
            .put("authority", e.authority.name).put("retrievedAt", e.retrievedAtMs)
            .put("publishedAt", e.publishedAtMs ?: JSONObject.NULL)
            .put("jurisdiction", e.jurisdiction ?: JSONObject.NULL)
            .put("content", e.content).put("contentHash", e.contentHash ?: JSONObject.NULL)
            .put("excerpt", document.excerpt).put("relevance", document.relevance)
            .put("conflicts", conflicts)
    }

    private fun decodeDocument(o: JSONObject): KnowledgeDocument {
        val e = KnowledgeEvidence(
            id = o.optString("id"),
            title = o.optString("title"),
            canonicalUrl = o.optString("url"),
            publisherId = o.optString("publisher"),
            domain = KnowledgeDomain.valueOf(o.optString("domain")),
            authority = EvidenceAuthority.valueOf(o.optString("authority")),
            retrievedAtMs = o.optLong("retrievedAt"),
            publishedAtMs = o.opt("publishedAt").takeUnless { it == null || it == JSONObject.NULL }?.toString()?.toLongOrNull(),
            jurisdiction = o.opt("jurisdiction").takeUnless { it == null || it == JSONObject.NULL }?.toString(),
            content = o.optString("content"),
            contentHash = o.opt("contentHash").takeUnless { it == null || it == JSONObject.NULL }?.toString()
        )
        val conflicts = o.optJSONArray("conflicts") ?: JSONArray()
        return KnowledgeDocument(e, o.optString("excerpt", e.content), o.optDouble("relevance"), buildSet {
            for (i in 0 until conflicts.length()) conflicts.optString(i).takeIf { it.isNotBlank() }?.let(::add)
        })
    }

    private fun readText(file: AtomicFile): String = runCatching {
        file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
    }.getOrDefault(if (file === queueFile) "[]" else "{}")

    private fun writeText(file: AtomicFile, value: String) {
        var stream: java.io.FileOutputStream? = null
        try {
            stream = file.startWrite()
            stream.write(value.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        } catch (error: Exception) {
            stream?.let { file.failWrite(it) }
            throw IllegalStateException("No se pudo guardar la bandeja de revisión del conocimiento.", error)
        }
    }

    private companion object {
        const val MAX_PENDING = 100
        const val MAX_VERSIONS_PER_DOCUMENT = 10
    }
}
