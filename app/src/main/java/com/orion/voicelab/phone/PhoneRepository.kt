package com.orion.voicelab.phone

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import com.orion.voicelab.data.HistoryQueryType
import com.orion.voicelab.data.PhoneCallRecord
import com.orion.voicelab.data.PhoneCallStatus
import com.orion.voicelab.data.PhoneContactEntry
import com.orion.voicelab.data.PhoneContactSource
import com.orion.voicelab.memory.MemoryTextSimilarity
import com.orion.voicelab.persistence.FavoritePhoneContactEntity
import com.orion.voicelab.persistence.PhoneCallRecordEntity
import com.orion.voicelab.persistence.PhoneDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class PhoneRepository(
    private val phoneDao: PhoneDao,
    private val contentResolver: ContentResolver? = null,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val MAX_NATIVE_SCAN_ROWS = 400
    }

    fun observeFavorites(): Flow<List<PhoneContactEntry>> =
        phoneDao.observeFavorites().map { list -> list.map { it.toDomain() } }

    suspend fun getFavorites(): List<PhoneContactEntry> =
        phoneDao.getFavorites().map { it.toDomain() }

    suspend fun setFavorite(contact: PhoneContactEntry, favorite: Boolean) {
        if (favorite) {
            phoneDao.upsertFavorite(
                FavoritePhoneContactEntity(
                    contactKey = contact.contactKey.ifBlank { "num:${contact.normalizedNumber}" },
                    displayName = contact.displayName.trim(),
                    phoneNumber = contact.phoneNumber.trim(),
                    label = contact.label.trim().ifBlank { "Teléfono" },
                    createdAtMs = clockMs()
                )
            )
        } else {
            phoneDao.deleteFavoriteByKey(contact.contactKey)
        }
    }

    fun observeCallRecords(): Flow<List<PhoneCallRecord>> =
        phoneDao.observeCallRecords().map { list -> list.map { it.toDomain() } }

    suspend fun getAllCallRecords(): List<PhoneCallRecord> =
        phoneDao.getAllCallRecords().map { it.toDomain() }

    suspend fun getLatestCallRecord(): PhoneCallRecord? =
        phoneDao.getLatestCallRecord()?.toDomain()

    suspend fun createCallRecord(
        contactName: String,
        phoneNumber: String,
        reason: String = "",
        status: PhoneCallStatus,
        scheduledNote: String = "",
        startedAtMs: Long? = null,
        followUpPending: Boolean = false
    ): PhoneCallRecord {
        val now = clockMs()
        val entity = PhoneCallRecordEntity(
            contactName = contactName.trim().ifBlank { phoneNumber.trim() },
            phoneNumber = phoneNumber.trim(),
            reason = reason.trim(),
            status = status.name,
            createdAtMs = now,
            startedAtMs = startedAtMs,
            endedAtMs = null,
            durationSeconds = 0L,
            scheduledNote = scheduledNote.trim(),
            userNotes = "",
            outcome = "",
            followUpPending = followUpPending
        )
        val id = phoneDao.insertCallRecord(entity)
        return entity.copy(id = id).toDomain()
    }

    suspend fun updateCallStatusAndTiming(
        id: Long,
        status: PhoneCallStatus,
        startedAtMs: Long? = null,
        endedAtMs: Long? = null,
        durationSeconds: Long = 0L
    ) {
        phoneDao.updateCallStatusAndTiming(
            id = id,
            status = status.name,
            startedAtMs = startedAtMs,
            endedAtMs = endedAtMs,
            durationSeconds = durationSeconds.coerceAtLeast(0L)
        )
    }

    suspend fun updateCallNotesAndOutcome(
        id: Long,
        reason: String,
        userNotes: String,
        outcome: String,
        followUpPending: Boolean
    ) {
        phoneDao.updateCallNotesAndOutcome(
            id = id,
            reason = reason.trim(),
            userNotes = userNotes.trim(),
            outcome = outcome.trim(),
            followUpPending = followUpPending
        )
    }

    suspend fun deleteCallRecord(id: Long) {
        phoneDao.deleteCallRecordById(id)
    }

    /**
     * Consulta bajo demanda la agenda nativa de Android cuando READ_CONTACTS está concedido.
     * No almacena ni replica la agenda completa en la base de datos local de ORION.
     */
    fun queryNativeContacts(
        query: String = "",
        favoriteKeys: Set<String> = emptySet(),
        limit: Int = 40
    ): List<PhoneContactEntry> {
        val resolver = contentResolver ?: return emptyList()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.STARRED
        )
        val cleanQuery = query.trim()
        val normQuery = MemoryTextSimilarity.normalize(cleanQuery)
        val queryDigits = cleanQuery.filter { it.isDigit() }

        val scannedEntries = mutableListOf<PhoneContactEntry>()
        val seenKeys = hashSetOf<String>()
        val targetScanCap = if (cleanQuery.isEmpty()) limit else MAX_NATIVE_SCAN_ROWS
        try {
            resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, ${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)
                val starredIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.STARRED)

                var scanned = 0
                while (cursor.moveToNext() && scanned < MAX_NATIVE_SCAN_ROWS) {
                    scanned++
                    val rawName = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                    val rawNumber = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                    val normalized = PhoneContactMatcher.normalizePhoneNumber(rawNumber)
                    if (rawName.isEmpty() || normalized.isEmpty()) continue

                    val contactId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val dedupKey = "${MemoryTextSimilarity.normalize(rawName)}|$normalized"
                    if (!seenKeys.add(dedupKey)) continue

                    val typeInt = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                    val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                    val label = PhoneContactMatcher.phoneTypeLabel(typeInt, customLabel)
                    val key = "native:$contactId:$normalized"
                    val systemStarred = starredIdx >= 0 && cursor.getInt(starredIdx) == 1

                    scannedEntries.add(
                        PhoneContactEntry(
                            contactKey = key,
                            displayName = rawName,
                            phoneNumber = rawNumber,
                            normalizedNumber = normalized,
                            label = label,
                            isFavorite = systemStarred || key in favoriteKeys,
                            source = PhoneContactSource.NATIVE_AGENDA
                        )
                    )
                    if (cleanQuery.isEmpty() && scannedEntries.size >= targetScanCap) break
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            // Retorna coincidencias parciales recogidas hasta el momento
        }

        if (cleanQuery.isEmpty()) return scannedEntries.take(limit)

        val directHits = scannedEntries.filter { entry ->
            val normName = MemoryTextSimilarity.normalize(entry.displayName)
            val nameMatch = normQuery.isNotEmpty() && normName.contains(normQuery)
            val digitsMatch = queryDigits.length >= 2 && entry.normalizedNumber.contains(queryDigits)
            nameMatch || digitsMatch
        }
        val fuzzyHits = if (normQuery.length >= 2) {
            PhoneContactMatcher.findMatches(cleanQuery, scannedEntries, maxCandidates = limit)
        } else {
            emptyList()
        }
        return (directHits + fuzzyHits)
            .distinctBy { it.contactKey }
            .take(limit)
    }

    /**
     * Resuelve un contacto elegido desde el selector nativo de Android sin requerir READ_CONTACTS.
     */
    fun resolvePickedContactUri(uri: Uri, favoriteKeys: Set<String> = emptySet()): PhoneContactEntry? {
        val resolver = contentResolver ?: return null
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL
        )
        return try {
            resolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
                val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val typeIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
                val labelIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL)

                val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().trim() else ""
                val number = if (numIdx >= 0) cursor.getString(numIdx).orEmpty().trim() else ""
                val normalized = PhoneContactMatcher.normalizePhoneNumber(number)
                if (normalized.isEmpty()) return@use null

                val contactId = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                val typeInt = if (typeIdx >= 0) cursor.getInt(typeIdx) else ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                val customLabel = if (labelIdx >= 0) cursor.getString(labelIdx) else null
                val key = "picked:$contactId:$normalized"

                PhoneContactEntry(
                    contactKey = key,
                    displayName = name.ifBlank { number },
                    phoneNumber = number,
                    normalizedNumber = normalized,
                    label = PhoneContactMatcher.phoneTypeLabel(typeInt, customLabel),
                    isFavorite = key in favoriteKeys,
                    source = PhoneContactSource.NATIVE_PICKER
                )
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Busca candidatos combinando favoritos locales y agenda nativa (si hay permiso) para resolver
     * nombres hablados mediante ASR (incluyendo pequeñas variaciones fonéticas en español).
     */
    suspend fun resolveVoiceContactMatches(query: String): List<PhoneContactEntry> {
        val favorites = getFavorites()
        val favKeys = favorites.map { it.contactKey }.toSet()
        val nativeAll = queryNativeContacts(query = "", favoriteKeys = favKeys, limit = 250)

        val combined = LinkedHashMap<String, PhoneContactEntry>()
        for (fav in favorites) {
            combined["${MemoryTextSimilarity.normalize(fav.displayName)}|${fav.normalizedNumber}"] = fav
        }
        for (nat in nativeAll) {
            val k = "${MemoryTextSimilarity.normalize(nat.displayName)}|${nat.normalizedNumber}"
            if (!combined.containsKey(k)) {
                combined[k] = nat
            }
        }
        return PhoneContactMatcher.findMatches(query = query, pool = combined.values.toList())
    }

    fun answerCallHistoryQuery(
        queryType: HistoryQueryType,
        contactQuery: String?,
        records: List<PhoneCallRecord>
    ): String {
        val now = clockMs()
        val cleanTarget = contactQuery?.trim().orEmpty()

        return when (queryType) {
            HistoryQueryType.WHEN_CALLED -> {
                val matched = if (cleanTarget.isNotEmpty()) {
                    records.filter {
                        PhoneContactMatcher.scoreNameMatch(cleanTarget, it.contactName) >= 0.55 ||
                            it.phoneNumber.contains(cleanTarget)
                    }
                } else {
                    records
                }
                val latest = matched.firstOrNull { it.status != PhoneCallStatus.CANCELLED }
                    ?: matched.firstOrNull()
                if (latest == null) {
                    if (cleanTarget.isNotEmpty()) {
                        "No encuentro ninguna llamada registrada con $cleanTarget."
                    } else {
                        "Todavía no hay llamadas registradas desde ORION."
                    }
                } else {
                    val dateLabel = formatRelativeDateTime(latest.createdAtMs, now)
                    val durText = if (latest.durationSeconds > 0) {
                        " durante ${latest.durationSeconds} segundos"
                    } else {
                        ""
                    }
                    val reasonText = if (latest.reason.isNotBlank()) " Motivo: ${latest.reason}." else ""
                    "El último registro con ${latest.contactName} fue $dateLabel (${latest.status.displayLabel}$durText).$reasonText"
                }
            }

            HistoryQueryType.PENDING_WITH -> {
                val matched = if (cleanTarget.isNotEmpty()) {
                    records.filter {
                        PhoneContactMatcher.scoreNameMatch(cleanTarget, it.contactName) >= 0.55
                    }
                } else {
                    records
                }
                val pending = matched.filter {
                    it.followUpPending ||
                        it.status == PhoneCallStatus.PREPARED ||
                        it.userNotes.isNotBlank() ||
                        it.outcome.isNotBlank() ||
                        it.reason.isNotBlank()
                }
                if (pending.isEmpty()) {
                    if (cleanTarget.isNotEmpty()) {
                        "No tienes notas ni temas pendientes guardados con $cleanTarget."
                    } else {
                        "No tienes temas pendientes registrados en tus llamadas."
                    }
                } else {
                    val item = pending.first()
                    val detail = listOf(item.reason, item.userNotes, item.outcome, item.scheduledNote)
                        .filter { it.isNotBlank() }
                        .joinToString(". ")
                    "Con ${item.contactName} tienes anotado: $detail."
                }
            }

            HistoryQueryType.CALLBACKS -> {
                val callbacks = records.filter {
                    it.followUpPending || it.status == PhoneCallStatus.PREPARED
                }
                if (callbacks.isEmpty()) {
                    "No tienes llamadas pendientes por devolver ni preparadas ahora mismo."
                } else {
                    val summary = callbacks.take(3).joinToString("; ") { rec ->
                        val note = rec.reason.ifBlank { rec.scheduledNote.ifBlank { rec.status.displayLabel } }
                        "${rec.contactName} ($note)"
                    }
                    "Tienes ${callbacks.size} pendiente${if (callbacks.size == 1) "" else "s"}: $summary."
                }
            }
        }
    }

    private fun formatRelativeDateTime(timestampMs: Long, nowMs: Long): String {
        val targetCal = Calendar.getInstance().apply { timeInMillis = timestampMs }
        val nowCal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestampMs))
        val sameYear = targetCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
        val dayDiff = nowCal.get(Calendar.DAY_OF_YEAR) - targetCal.get(Calendar.DAY_OF_YEAR)
        return when {
            sameYear && dayDiff == 0 -> "hoy a las $timeFmt"
            sameYear && dayDiff == 1 -> "ayer a las $timeFmt"
            else -> {
                val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestampMs))
                "el $dateFmt a las $timeFmt"
            }
        }
    }

    private fun FavoritePhoneContactEntity.toDomain(): PhoneContactEntry = PhoneContactEntry(
        contactKey = contactKey,
        displayName = displayName,
        phoneNumber = phoneNumber,
        normalizedNumber = PhoneContactMatcher.normalizePhoneNumber(phoneNumber),
        label = label,
        isFavorite = true,
        source = PhoneContactSource.NATIVE_AGENDA
    )

    private fun PhoneCallRecordEntity.toDomain(): PhoneCallRecord = PhoneCallRecord(
        id = id,
        contactName = contactName,
        phoneNumber = phoneNumber,
        reason = reason,
        status = PhoneCallStatus.fromStored(status),
        createdAtMs = createdAtMs,
        startedAtMs = startedAtMs,
        endedAtMs = endedAtMs,
        durationSeconds = durationSeconds,
        scheduledNote = scheduledNote,
        userNotes = userNotes,
        outcome = outcome,
        followUpPending = followUpPending
    )
}

object PhoneContactMatcher {

    private val relationshipAliases = mapOf(
        "mi madre" to listOf("mama", "madre", "mami"),
        "madre" to listOf("mama", "madre", "mami"),
        "mama" to listOf("mama", "madre", "mami"),
        "mi padre" to listOf("papa", "padre", "papi"),
        "padre" to listOf("papa", "padre", "papi"),
        "papa" to listOf("papa", "padre", "papi"),
        "mi hermano" to listOf("hermano"),
        "mi hermana" to listOf("hermana"),
        "mi mujer" to listOf("mujer", "esposa"),
        "mi marido" to listOf("marido", "esposo")
    )

    fun normalizePhoneNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        return if (hasPlus) "+$digits" else digits
    }

    fun phoneTypeLabel(type: Int, customLabel: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> "Móvil"
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> "Casa"
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> "Trabajo"
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> "Principal"
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> customLabel?.trim()?.ifBlank { "Otro" } ?: "Otro"
        else -> "Teléfono"
    }

    /**
     * Resuelve coincidencias de nombres pronunciados mediante ASR (errores fonéticos leves,
     * omisión de tildes o alias familiares como "mi madre" -> "Mamá").
     */
    fun findMatches(
        query: String,
        pool: List<PhoneContactEntry>,
        maxCandidates: Int = 4
    ): List<PhoneContactEntry> {
        val clean = query.trim()
        if (clean.isEmpty() || pool.isEmpty()) return emptyList()

        // Si el usuario dictó directamente un número telefónico
        val queryDigits = clean.filter { it.isDigit() }
        if (queryDigits.length >= 6) {
            val numberMatches = pool.filter { it.normalizedNumber.endsWith(queryDigits) || it.normalizedNumber.contains(queryDigits) }
            if (numberMatches.isNotEmpty()) return numberMatches.take(maxCandidates)
        }

        val expandedQueries = expandAliases(clean)
        val scored = pool.mapNotNull { entry ->
            val bestScore = expandedQueries.maxOf { q -> scoreNameMatch(q, entry.displayName) }
            val boosted = bestScore + if (entry.isFavorite) 0.04 else 0.0
            if (bestScore >= 0.56) entry to boosted else null
        }.sortedWith(
            compareByDescending<Pair<PhoneContactEntry, Double>> { it.second }
                .thenByDescending { it.first.isFavorite }
                .thenBy { it.first.displayName }
        )

        if (scored.isEmpty()) return emptyList()
        val top = scored.first()
        val second = scored.getOrNull(1)
        // Si hay una coincidencia claramente ganadora y única (p. ej. nombre+apellido exacto), devuélvela sola
        if (second == null || (top.second >= 0.96 && second.second < 0.85)) {
            return listOf(top.first)
        }
        return scored.take(maxCandidates).map { it.first }
    }

    fun scoreNameMatch(query: String, candidateName: String): Double {
        val normQ = MemoryTextSimilarity.normalize(query)
        val normC = MemoryTextSimilarity.normalize(candidateName)
        if (normQ.isEmpty() || normC.isEmpty()) return 0.0
        if (normQ == normC) return 1.0

        val qTokens = normQ.split(' ').filter { it.isNotBlank() }
        val cTokens = normC.split(' ').filter { it.isNotBlank() }
        if (qTokens.isEmpty() || cTokens.isEmpty()) return 0.0

        // Coincidencia exacta de prefijo o palabra completa (p. ej. "Juan" vs "Juan Pérez")
        if (qTokens.size == 1 && cTokens.first() == qTokens.first()) return 0.88
        if (normC.startsWith("$normQ ")) return 0.92
        if (cTokens.containsAll(qTokens)) return 0.93

        // Similitud fonética española por cada palabra de la consulta
        val qPhonetic = qTokens.map { spanishPhoneticKey(it) }
        val cPhonetic = cTokens.map { spanishPhoneticKey(it) }

        var matchedScoreSum = 0.0
        for (idx in qTokens.indices) {
            val qt = qTokens[idx]
            val qp = qPhonetic[idx]
            var bestToken = 0.0
            for (cIdx in cTokens.indices) {
                val ct = cTokens[cIdx]
                val cp = cPhonetic[cIdx]
                val rawSim = normalizedLevenshteinSimilarity(qt, ct)
                val phonSim = normalizedLevenshteinSimilarity(qp, cp)
                val tokenScore = if (qp == cp) max(0.90, rawSim) else max(rawSim, phonSim * 0.95)
                if (tokenScore > bestToken) bestToken = tokenScore
            }
            matchedScoreSum += bestToken
        }
        val avgTokenMatch = matchedScoreSum / qTokens.size.toDouble()
        val wholePhonetic = normalizedLevenshteinSimilarity(
            spanishPhoneticKey(normQ),
            spanishPhoneticKey(normC)
        )
        return max(avgTokenMatch * 0.92, wholePhonetic * 0.85)
    }

    private fun expandAliases(query: String): List<String> {
        val norm = MemoryTextSimilarity.normalize(query)
        val extras = relationshipAliases[norm] ?: emptyList()
        return (listOf(query) + extras).distinct()
    }

    internal fun spanishPhoneticKey(word: String): String {
        val norm = MemoryTextSimilarity.normalize(word)
        if (norm.isEmpty()) return ""
        return norm
            .replace("qu", "k")
            .replace("gu", "g")
            .replace("ll", "y")
            .replace("ch", "x")
            .replace("ce", "se")
            .replace("ci", "si")
            .replace("ge", "je")
            .replace("gi", "ji")
            .replace("v", "b")
            .replace("z", "s")
            .replace("c", "k")
            .replace("h", "")
            .replace(Regex("(.)\\1+"), "$1")
    }

    internal fun normalizedLevenshteinSimilarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = max(a.length, b.length)
        if (maxLen == 0) return 1.0
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val temp = dp[j]
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[j] = minOf(
                    dp[j] + 1,
                    dp[j - 1] + 1,
                    prev + cost
                )
                prev = temp
            }
        }
        return (1.0 - dp[b.length].toDouble() / maxLen.toDouble()).coerceIn(0.0, 1.0)
    }
}
