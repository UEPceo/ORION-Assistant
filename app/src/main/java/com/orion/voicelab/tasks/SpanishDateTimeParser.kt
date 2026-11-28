package com.orion.voicelab.tasks

import com.orion.voicelab.memory.MemoryTextSimilarity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class TemporalParseResult(
    val dueDateEpochDay: Long? = null,
    val dueTimeMinutes: Int? = null,
    val reminderAtMs: Long? = null,
    val cleanedRemainderText: String,
    val matchedExpression: Boolean = false,
    val isAmbiguous: Boolean = false,
    val ambiguityOptionAMinutes: Int? = null,
    val ambiguityOptionBMinutes: Int? = null,
    val ambiguityPrompt: String? = null
)

object SpanishDateTimeParser {

    private val spanishMonths = mapOf(
        "enero" to 1, "febrero" to 2, "marzo" to 3, "abril" to 4,
        "mayo" to 5, "junio" to 6, "julio" to 7, "agosto" to 8,
        "septiembre" to 9, "setiembre" to 9, "octubre" to 10,
        "noviembre" to 11, "diciembre" to 12
    )

    private val spanishWeekdays = mapOf(
        "lunes" to DayOfWeek.MONDAY,
        "martes" to DayOfWeek.TUESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY,
        "jueves" to DayOfWeek.THURSDAY,
        "viernes" to DayOfWeek.FRIDAY,
        "sabado" to DayOfWeek.SATURDAY,
        "domingo" to DayOfWeek.SUNDAY
    )

    private val spanishNumberWords = mapOf(
        "una" to 1, "uno" to 1, "un" to 1,
        "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5,
        "seis" to 6, "siete" to 7, "ocho" to 8, "nueve" to 9,
        "diez" to 10, "once" to 11, "doce" to 12,
        "trece" to 13, "catorce" to 14, "quince" to 15,
        "veinte" to 20, "treinta" to 30, "cuarenta" to 40, "cuarenta y cinco" to 45
    )

    private val relativeOffsetRegex = Regex(
        """\b(?:en|dentro\s+de)\s+(\d+|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|veinte|treinta|cuarenta\s+y\s+cinco|media)\s+(minuto|minutos|hora|horas|dia|dias)\b"""
    )

    private val numericTimeRegex = Regex(
        """\b(?:a\s+la|a\s+las|para\s+las|sobre\s+las)\s+(\d{1,2})(?::(\d{2}))?(?:\s+y\s+(media|cuarto)|(?:\s+menos\s+(cuarto)))?(?:\s+de\s+la\s+(manana|tarde|noche|madrugada))?\b"""
    )

    private val wordTimeRegex = Regex(
        """\b(?:a\s+la|a\s+las|para\s+las|sobre\s+las)\s+(una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce)(?:\s+y\s+(media|cuarto)|(?:\s+menos\s+(cuarto)))?(?:\s+de\s+la\s+(manana|tarde|noche|madrugada))?\b"""
    )

    private val compactClockRegex = Regex("""\b(\d{1,2}):(\d{2})\b""")

    private val slashDateRegex = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")

    private val namedDateRegex = Regex(
        """\b(?:el\s+(?:dia\s+)?)?(\d{1,2})\s+de\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\b"""
    )

    private val dayOnlyRegex = Regex("""\b(?:para\s+el|hasta\s+el|el)\s+dia\s+(\d{1,2})\b""")

    private val weekdayRegex = Regex(
        """\b(?:(?:para|hasta)\s+)?(?:(este|el|proximo|siguiente)\s+)?(lunes|martes|miercoles|jueves|viernes|sabado|domingo)(?:\s+(que\s+viene|proximo))?\b"""
    )

    fun parse(
        rawText: String,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault(),
        defaultReminderIfDateOnly: Boolean = true
    ): TemporalParseResult {
        val norm = MemoryTextSimilarity.normalize(
            rawText.replace(':', ' ').let { raw ->
                // Preserve HH:MM temporarily before normalize strips punctuation
                raw
            }
        )
        // Re-create a normalized string that preserves ':' and '/' for clock & date detection
        val normWithPunct = normalizeKeepingDatePunct(rawText)
        val nowZoned = Instant.ofEpochMilli(nowMs).atZone(zoneId)
        val today = nowZoned.toLocalDate()

        var working = normWithPunct
        var dateResult: LocalDate? = null
        var timeMinutes: Int? = null
        var reminderMs: Long? = null
        var matched = false
        var ambiguous = false
        var optA: Int? = null
        var optB: Int? = null
        var ambiguityMsg: String? = null

        // 1. Relative offsets ("en 20 minutos", "en 2 horas", "dentro de 3 días")
        relativeOffsetRegex.find(working)?.let { m ->
            val rawAmt = m.groupValues[1]
            val unit = m.groupValues[2]
            val amount = if (rawAmt == "media") null else (rawAmt.toIntOrNull() ?: spanishNumberWords[rawAmt])
            if (rawAmt == "media" && unit.startsWith("hora")) {
                val target = nowZoned.plusMinutes(30)
                dateResult = target.toLocalDate()
                timeMinutes = target.hour * 60 + target.minute
                reminderMs = target.toInstant().toEpochMilli()
                matched = true
                working = working.removeRange(m.range).trim()
            } else if (amount != null && amount > 0) {
                val target = when {
                    unit.startsWith("minuto") -> nowZoned.plusMinutes(amount.toLong())
                    unit.startsWith("hora") -> nowZoned.plusHours(amount.toLong())
                    else -> nowZoned.plusDays(amount.toLong())
                }
                dateResult = target.toLocalDate()
                if (!unit.startsWith("dia")) {
                    timeMinutes = target.hour * 60 + target.minute
                    reminderMs = target.toInstant().toEpochMilli()
                }
                matched = true
                working = working.removeRange(m.range).trim()
            }
        }

        // 2. Relative day keywords (pasado mañana MUST be checked before mañana!)
        val dayPatterns = listOf(
            Regex("""\b(?:para\s+|hasta\s+)?pasado\s+manana\b""") to 2L,
            Regex("""\b(?:para\s+|hasta\s+)?manana\b""") to 1L,
            Regex("""\b(?:para\s+|hasta\s+)?hoy\b""") to 0L
        )
        for ((regex, offsetDays) in dayPatterns) {
            val m = regex.find(working)
            if (m != null) {
                if (dateResult == null) {
                    dateResult = today.plusDays(offsetDays)
                }
                matched = true
                working = working.removeRange(m.range).trim()
            }
        }

        // 3. Explicit named date ("15 de diciembre") or numeric ("28/11") or ("el día 5")
        if (dateResult == null) {
            namedDateRegex.find(working)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()
                val month = spanishMonths[m.groupValues[2]]
                if (day != null && month != null && day in 1..31) {
                    var candidate = runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
                    if (candidate != null && candidate.isBefore(today)) {
                        candidate = runCatching { LocalDate.of(today.year + 1, month, day) }.getOrNull()
                    }
                    if (candidate != null) {
                        dateResult = candidate
                        matched = true
                        working = working.removeRange(m.range).trim()
                    }
                }
            }
        }
        if (dateResult == null) {
            slashDateRegex.find(working)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()
                val month = m.groupValues[2].toIntOrNull()
                val yearRaw = m.groupValues[3].toIntOrNull()
                val year = when {
                    yearRaw == null -> today.year
                    yearRaw < 100 -> 2000 + yearRaw
                    else -> yearRaw
                }
                if (day != null && month != null && month in 1..12 && day in 1..31) {
                    var candidate = runCatching { LocalDate.of(year, month, day) }.getOrNull()
                    if (candidate != null && yearRaw == null && candidate.isBefore(today)) {
                        candidate = runCatching { LocalDate.of(year + 1, month, day) }.getOrNull()
                    }
                    if (candidate != null) {
                        dateResult = candidate
                        matched = true
                        working = working.removeRange(m.range).trim()
                    }
                }
            }
        }
        if (dateResult == null) {
            dayOnlyRegex.find(working)?.let { m ->
                val day = m.groupValues[1].toIntOrNull()
                if (day != null && day in 1..31) {
                    var candidate = runCatching { today.withDayOfMonth(day) }.getOrNull()
                    if (candidate != null && candidate.isBefore(today)) {
                        candidate = runCatching { today.plusMonths(1).withDayOfMonth(day) }.getOrNull()
                    }
                    if (candidate != null) {
                        dateResult = candidate
                        matched = true
                        working = working.removeRange(m.range).trim()
                    }
                }
            }
        }

        // 4. Weekdays ("el domingo", "hasta el lunes", "próximo viernes")
        if (dateResult == null) {
            weekdayRegex.find(working)?.let { m ->
                val prefixMod = m.groupValues[1]
                val dayName = m.groupValues[2]
                val suffixMod = m.groupValues[3]
                val targetDow = spanishWeekdays[dayName]
                if (targetDow != null) {
                    var daysAhead = (targetDow.value - today.dayOfWeek.value + 7) % 7
                    if (daysAhead == 0) {
                        // Si hoy ya es ese día y dice "hasta el lunes", "el próximo lunes" o "el lunes" -> +7 días salvo "este"
                        daysAhead = if (prefixMod == "este") 0 else 7
                    } else if (prefixMod == "proximo" || suffixMod.isNotBlank()) {
                        // "próximo X" cuando faltan pocos días sigue siendo el siguiente X o +7 si dice "que viene"
                        if (suffixMod.contains("viene") && daysAhead <= 2) {
                            daysAhead += 7
                        }
                    }
                    dateResult = today.plusDays(daysAhead.toLong())
                    matched = true
                    working = working.removeRange(m.range).trim()
                }
            }
        }

        // 5. Clock time expressions ("a las 9", "a las 14:30", "a las 8 de la tarde", "a las diez y media")
        if (timeMinutes == null) {
            val numTimeMatch = numericTimeRegex.find(working)
            val wordTimeMatch = if (numTimeMatch == null) wordTimeRegex.find(working) else null
            val activeTimeMatch = numTimeMatch ?: wordTimeMatch
            if (activeTimeMatch != null) {
                val parsedClock = extractClockFromMatch(
                    match = activeTimeMatch,
                    isNumeric = numTimeMatch != null,
                    nowZonedHour = nowZoned.hour,
                    dateAlreadySet = dateResult != null
                )
                if (parsedClock != null) {
                    matched = true
                    working = working.removeRange(activeTimeMatch.range).trim()
                    if (parsedClock.isAmbiguous) {
                        ambiguous = true
                        optA = parsedClock.optionAMinutes
                        optB = parsedClock.optionBMinutes
                        timeMinutes = parsedClock.resolvedMinutes
                        ambiguityMsg = parsedClock.ambiguityMessage
                    } else {
                        timeMinutes = parsedClock.resolvedMinutes
                    }
                }
            } else {
                compactClockRegex.find(working)?.let { m ->
                    val h = m.groupValues[1].toIntOrNull()
                    val min = m.groupValues[2].toIntOrNull()
                    if (h != null && min != null && h in 0..23 && min in 0..59) {
                        timeMinutes = h * 60 + min
                        matched = true
                        working = working.removeRange(m.range).trim()
                    }
                }
            }
        }

        // 6. Day-part expressions ("esta tarde", "esta noche", "por la mañana", "mediodía")
        val dayPartPatterns = listOf(
            Regex("""\b(?:para\s+)?esta\s+manana\b""") to (true to 9 * 60),
            Regex("""\b(?:para\s+)?esta\s+tarde\b""") to (true to 17 * 60),
            Regex("""\b(?:para\s+)?esta\s+noche\b""") to (true to 21 * 60),
            Regex("""\bpor\s+la\s+manana\b""") to (false to 9 * 60),
            Regex("""\bpor\s+la\s+tarde\b""") to (false to 17 * 60),
            Regex("""\bpor\s+la\s+noche\b""") to (false to 21 * 60),
            Regex("""\b(?:a\s+)?mediodia\b""") to (false to 12 * 60)
        )
        for ((regex, spec) in dayPartPatterns) {
            val (impliesToday, defaultMins) = spec
            val m = regex.find(working)
            if (m != null) {
                matched = true
                if (impliesToday && dateResult == null) {
                    dateResult = today
                }
                if (timeMinutes == null) {
                    timeMinutes = defaultMins
                } else if (ambiguous && optA != null && optB != null) {
                    // Disambiguate using day part
                    timeMinutes = if (defaultMins >= 12 * 60) optB else optA
                    ambiguous = false
                    ambiguityMsg = null
                }
                working = working.removeRange(m.range).trim()
            }
        }

        // 7. Vague expressions ("en un rato", "luego", "más tarde", "pronto")
        if (!matched) {
            val vagueRegex = Regex("""\b(?:en\s+un\s+rato|mas\s+tarde|luego|pronto)\b""")
            vagueRegex.find(working)?.let { m ->
                matched = true
                ambiguous = true
                dateResult = today
                val fallback = nowZoned.plusHours(2)
                val fallbackMins = fallback.hour * 60 + fallback.minute
                optA = (nowZoned.plusMinutes(30).let { it.hour * 60 + it.minute })
                optB = fallbackMins
                timeMinutes = fallbackMins
                ambiguityMsg = "La expresión «${m.value}» es aproximada. ¿Lo dejamos dentro de 2 horas (${formatTimeLabel(fallbackMins)}) o prefieres otra hora?"
                working = working.removeRange(m.range).trim()
            }
        }

        // If user supplied a time without a date (e.g. "a las 18:00"), infer today (or tomorrow if already passed)
        if (dateResult == null && timeMinutes != null) {
            val candidateTime = LocalTime.of(timeMinutes / 60, timeMinutes % 60)
            val todayTarget = today.atTime(candidateTime).atZone(zoneId).toInstant().toEpochMilli()
            dateResult = if (todayTarget > nowMs) today else today.plusDays(1)
        }

        // Compute reminderAtMs if a reminder timestamp is derivable
        if (reminderMs == null && dateResult != null) {
            if (timeMinutes != null) {
                val t = LocalTime.of((timeMinutes / 60).coerceIn(0, 23), (timeMinutes % 60).coerceIn(0, 59))
                reminderMs = dateResult!!.atTime(t).atZone(zoneId).toInstant().toEpochMilli()
            } else if (defaultReminderIfDateOnly) {
                // Default morning reminder at 09:00 on that day (or +1h if today and after 09:00)
                val defaultDateTime = dateResult!!.atTime(9, 0).atZone(zoneId).toInstant().toEpochMilli()
                reminderMs = if (dateResult == today && defaultDateTime <= nowMs) {
                    nowZoned.plusHours(1).withSecond(0).withNano(0).toInstant().toEpochMilli()
                } else {
                    defaultDateTime
                }
            }
        }

        val cleanedTitle = working
            .replace(Regex("""^(?:para|hasta|el|en)\s+$"""), "")
            .replace(Regex("""\s+(?:para|hasta|sobre|a)\s*$"""), "")
            .replace(Regex("""^(?:para|hasta)\s+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        return TemporalParseResult(
            dueDateEpochDay = dateResult?.toEpochDay(),
            dueTimeMinutes = timeMinutes,
            reminderAtMs = reminderMs,
            cleanedRemainderText = cleanedTitle,
            matchedExpression = matched,
            isAmbiguous = ambiguous,
            ambiguityOptionAMinutes = optA,
            ambiguityOptionBMinutes = optB,
            ambiguityPrompt = ambiguityMsg
        )
    }

    private data class ClockExtraction(
        val resolvedMinutes: Int,
        val isAmbiguous: Boolean = false,
        val optionAMinutes: Int? = null,
        val optionBMinutes: Int? = null,
        val ambiguityMessage: String? = null
    )

    private fun extractClockFromMatch(
        match: MatchResult,
        isNumeric: Boolean,
        nowZonedHour: Int,
        dateAlreadySet: Boolean
    ): ClockExtraction? {
        val rawHour = match.groupValues[1]
        var hour = if (isNumeric) rawHour.toIntOrNull() else spanishNumberWords[rawHour]
        if (hour == null || hour !in 0..23) return null

        val explicitMins = if (isNumeric) match.groupValues.getOrNull(2)?.toIntOrNull() else null
        val plusWord = if (isNumeric) match.groupValues.getOrNull(3).orEmpty() else match.groupValues.getOrNull(2).orEmpty()
        val minusWord = if (isNumeric) match.groupValues.getOrNull(4).orEmpty() else match.groupValues.getOrNull(3).orEmpty()
        val period = if (isNumeric) match.groupValues.getOrNull(5).orEmpty() else match.groupValues.getOrNull(4).orEmpty()

        var mins = explicitMins ?: when {
            plusWord == "media" -> 30
            plusWord == "cuarto" -> 15
            minusWord == "cuarto" -> {
                hour = if (hour == 0) 23 else hour - 1
                45
            }
            else -> 0
        }
        mins = mins.coerceIn(0, 59)

        // A numeric HH:MM expression is an explicit 24-hour clock in Spanish usage.
        // Do not turn "15 de diciembre a las 10:30" into an AM/PM clarification.
        if (explicitMins != null && period.isBlank()) {
            return ClockExtraction(resolvedMinutes = hour * 60 + mins)
        }

        if (hour >= 13 || hour == 0) {
            return ClockExtraction(resolvedMinutes = hour * 60 + mins)
        }

        // Hour is 1..12
        return when (period) {
            "manana", "madrugada" -> {
                val h = if (hour == 12) 0 else hour
                ClockExtraction(resolvedMinutes = h * 60 + mins)
            }
            "tarde", "noche" -> {
                val h = if (hour == 12) 12 else hour + 12
                ClockExtraction(resolvedMinutes = h * 60 + mins)
            }
            else -> {
                if (hour == 12) {
                    ClockExtraction(resolvedMinutes = 12 * 60 + mins)
                } else {
                    // Ambiguous 12-hour Spanish clock ("a las 8", "a las 3"): propose AM vs PM when <= 11
                    val morningMins = hour * 60 + mins
                    val eveningMins = (hour + 12) * 60 + mins
                    val smartDefault = if (dateAlreadySet || nowZonedHour < hour) eveningMins else eveningMins
                    ClockExtraction(
                        resolvedMinutes = smartDefault,
                        isAmbiguous = true,
                        optionAMinutes = morningMins,
                        optionBMinutes = eveningMins,
                        ambiguityMessage = "¿Te refieres a las ${formatTimeLabel(morningMins)} de la mañana o a las ${formatTimeLabel(eveningMins)}?"
                    )
                }
            }
        }
    }

    internal fun normalizeKeepingDatePunct(raw: String): String {
        val decomposed = java.text.Normalizer.normalize(raw.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
        return decomposed
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace(Regex("[^a-z0-9:/\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun formatDateLabel(
        epochDay: Long?,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        if (epochDay == null) return "Sin fecha"
        val today = Instant.ofEpochMilli(nowMs).atZone(zoneId).toLocalDate().toEpochDay()
        return when (epochDay - today) {
            0L -> "Hoy"
            1L -> "Mañana"
            2L -> "Pasado mañana"
            -1L -> "Ayer"
            else -> {
                val date = LocalDate.ofEpochDay(epochDay)
                date.format(DateTimeFormatter.ofPattern("EEE dd/MM", Locale.forLanguageTag("es-ES")))
                    .replaceFirstChar { it.uppercase() }
            }
        }
    }

    fun formatTimeLabel(minutes: Int?): String {
        if (minutes == null) return ""
        val h = (minutes / 60).coerceIn(0, 23)
        val m = (minutes % 60).coerceIn(0, 59)
        return String.format(Locale.ROOT, "%02d:%02d", h, m)
    }

    fun formatDateTimeSummary(
        dueDateEpochDay: Long?,
        dueTimeMinutes: Int?,
        reminderAtMs: Long?,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): String {
        val datePart = if (dueDateEpochDay != null) formatDateLabel(dueDateEpochDay, nowMs, zoneId) else ""
        val timePart = if (dueTimeMinutes != null) formatTimeLabel(dueTimeMinutes) else ""
        val combined = listOf(datePart, timePart).filter { it.isNotBlank() }.joinToString(" · ")
        if (combined.isNotBlank()) return combined
        if (reminderAtMs != null) {
            val zdt = Instant.ofEpochMilli(reminderAtMs).atZone(zoneId)
            return "${formatDateLabel(zdt.toLocalDate().toEpochDay(), nowMs, zoneId)} · ${formatTimeLabel(zdt.hour * 60 + zdt.minute)}"
        }
        return "Sin fecha"
    }
}
