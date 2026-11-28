package com.orion.voicelab.tasks

import com.orion.voicelab.data.PendingTaskPrompt
import com.orion.voicelab.data.TaskIntentAction
import com.orion.voicelab.data.TaskPriority
import com.orion.voicelab.data.TaskQueryScope
import com.orion.voicelab.memory.MemoryTextSimilarity
import java.time.ZoneId

/**
 * Enrutador determinista de lenguaje natural en español para tareas, recordatorios y seguimientos.
 * Separa la extracción y validación de parámetros de la generación libre del LLM.
 */
object TaskIntentRouter {

    private val cancelPromptPatterns = listOf(
        Regex("""^(?:no|cancela|cancelar|cancelalo|anula|olvida|olvidalo|mejor no|dejalo)(?:\b.*)?$""")
    )

    private val postCallPrefixRegex = Regex(
        """^(?:cuando\s+(?:termine|acabe|cuelgue)\s+(?:la\s+llamada|esta\s+llamada|de\s+hablar(?:\s+con\s+.+?)?),?\s*)(?:recuerdame|apunta|anota)\s+(.+)$"""
    )

    private val reminderCreateRegex = Regex(
        """^(?:por\s+favor\s+)?(?:oye\s+)?(?:orion\s+)?(?:recuerdame(?:\s+que(?:\s+(?:tengo\s+que|debo|hay\s+que))?)?|pon(?:me)?\s+un\s+recordatorio(?:\s+(?:de|para))?|crea\s+un\s+recordatorio(?:\s+(?:de|para))?)\s+(.+)$"""
    )

    private val agendaCreateRegex = Regex(
        """^(?:por\s+favor\s+)?(?:oye\s+)?(?:orion\s+)?(?:anade|agrega|pon|mete|apunta)\s+(?:(?:a|en)\s+(?:mi\s+)?agenda|(?:un\s+)?evento(?:\s+(?:en\s+)?(?:mi\s+)?agenda)?)\s*(.+)$"""
    )

    private val agendaQueryRegex = Regex(
        """^(?:orion\s+)?(?:que\s+(?:tengo|hay)\s+(?:en\s+)?(?:mi\s+)?agenda(?:\s+(?:hoy|manana|esta\s+semana))?|(?:consulta|muestra|lista)\s+(?:mi\s+)?agenda|eventos\s+(?:para\s+)?hoy)$"""
    )

    private val explicitTaskCreateRegex = Regex(
        """^(?:por\s+favor\s+)?(?:oye\s+)?(?:orion\s+)?(?:apunta(?:\s+que\s+(?:tengo\s+que|debo|hay\s+que))?|anota(?:\s+que\s+(?:tengo\s+que|debo|hay\s+que))?|apunta\s+(?:una\s+)?tarea(?:\s+(?:de|para))?|anota\s+(?:una\s+)?tarea(?:\s+(?:de|para))?|(?:anade|agrega|crea|nueva)\s+(?:una\s+)?tarea(?:\s+(?:de|para))?)\s*(.*)$"""
    )

    private val obligationTaskRegex = Regex(
        """^(?:tengo\s+que|debo|necesito)\s+(.+)$"""
    )

    private val callbackFollowUpRegex = Regex(
        """^(?:tengo\s+que\s+)?devolver(?:\s+una)?\s+llamada\s+a\s+(.+)$"""
    )

    private val completeTaskRegex = Regex(
        """^(?:por\s+favor\s+)?(?:marca(?:\s+como)?\s+(?:terminad[oa]|completad[oa]|hech[oa])|da\s+por\s+(?:terminad[oa]|completad[oa]|hech[oa])|he\s+terminado(?:\s+con)?|complet[ao](?:\s+la\s+tarea(?:\s+de)?)?|termina(?:\s+la\s+tarea(?:\s+de)?)?)\s+(.+)$"""
    )

    private val postponeTaskRegex = Regex(
        """^(?:por\s+favor\s+)?(?:pospon(?:er)?|aplaza(?:r)?|mueve|cambia)\s+(.+)$"""
    )

    private val cancelTaskRegex = Regex(
        """^(?:por\s+favor\s+)?(?:cancela|anula|borra|elimina|quita)\s+(?:el\s+recordatorio(?:\s+(?:de|para))?|la\s+tarea(?:\s+(?:de|para))?)\s*(.+)$"""
    )

    private val queryOverdueRegex = Regex(
        """(?:que\s+(?:tareas\s+)?(?:tengo\s+)?(?:atrasad[oa]s?|vencid[oa]s?|pasad[oa]s?)|tareas\s+atrasadas|recordatorios\s+vencidos)"""
    )

    private val queryRemindersRegex = Regex(
        """(?:que\s+recordatorios\s+(?:tengo|hay)|cuales\s+son\s+mis\s+recordatorios|mis\s+recordatorios)"""
    )

    private val queryTodayRegex = Regex(
        """(?:que\s+(?:tareas|cosas)\s+(?:tengo|hay)(?:\s+pendientes)?\s+para\s+hoy|tareas\s+de\s+hoy|que\s+tengo\s+hoy|pendientes\s+(?:para\s+)?hoy)"""
    )

    private val queryUpcomingRegex = Regex(
        """(?:que\s+(?:tareas|cosas)\s+(?:tengo|hay)\s+(?:proximas|para\s+(?:manana|pasado\s+manana|esta\s+semana|el\s+(?:lunes|martes|miercoles|jueves|viernes|sabado|domingo)))|proximas\s+tareas)"""
    )

    private val queryPendingRegex = Regex(
        """^(?:orion\s+)?(?:que\s+(?:tareas|cosas)\s+(?:tengo|hay)\s+pendientes?|que\s+tengo\s+pendiente|cuales\s+son\s+mis\s+tareas(?:\s+pendientes)?|lista\s+(?:de\s+)?tareas)$"""
    )

    private val callTargetInTitleRegex = Regex(
        """^(?:(?:tengo\s+que|debo)\s+)?(?:llamar|volver\s+a\s+llamar|devolver(?:\s+una)?\s+llamada)\s+a\s+(.+)$"""
    )

    fun route(
        utterance: String,
        pendingPrompt: PendingTaskPrompt? = null,
        nowMs: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): TaskIntentAction {
        val raw = utterance.trim()
        if (raw.isEmpty()) return TaskIntentAction.None
        val norm = MemoryTextSimilarity.normalize(raw)
        if (norm.isEmpty()) return TaskIntentAction.None

        // 1. Resolver aclaración o desambiguación pendiente
        if (pendingPrompt != null) {
            if (cancelPromptPatterns.any { it.matches(norm) }) {
                return TaskIntentAction.CancelPendingPrompt
            }
            when (pendingPrompt) {
                is PendingTaskPrompt.DisambiguateTasks -> {
                    parseCandidateIndex(norm)?.let { idx ->
                        if (idx in pendingPrompt.candidates.indices) {
                            return TaskIntentAction.SelectTaskCandidateByIndex(idx)
                        }
                    }
                    if (norm.split(' ').size <= 6) {
                        return TaskIntentAction.SelectTaskCandidateByTitle(raw)
                    }
                }

                is PendingTaskPrompt.ClarifyTemporalAmbiguity -> {
                    val parsedChoice = SpanishDateTimeParser.parse(raw, nowMs = nowMs, zoneId = zoneId)
                    if (parsedChoice.dueTimeMinutes != null && !parsedChoice.isAmbiguous) {
                        return TaskIntentAction.ResolveTemporalChoice(parsedChoice.dueTimeMinutes)
                    }
                    if (norm.contains("manana") || norm.contains("primera") || norm.contains("am")) {
                        return TaskIntentAction.ResolveTemporalChoice(pendingPrompt.optionA24HourMinutes)
                    }
                    if (norm.contains("tarde") || norm.contains("noche") || norm.contains("segunda") || norm.contains("pm")) {
                        return TaskIntentAction.ResolveTemporalChoice(pendingPrompt.optionB24HourMinutes)
                    }
                }

                is PendingTaskPrompt.ClarifyMissingTitle -> {
                    if (norm.split(' ').size in 1..12) {
                        val cleaned = stripPoliteSuffix(raw)
                        if (cleaned.isNotBlank()) {
                            return TaskIntentAction.ProvideMissingTitle(cleaned.replaceFirstChar { it.uppercase() })
                        }
                    }
                }
            }
        }

        // 2. Consultas deterministas de tareas / recordatorios
        if (queryOverdueRegex.containsMatchIn(norm)) {
            return TaskIntentAction.QueryTasks(TaskQueryScope.OVERDUE)
        }
        if (queryRemindersRegex.containsMatchIn(norm)) {
            return TaskIntentAction.QueryTasks(TaskQueryScope.REMINDERS)
        }
        if (agendaQueryRegex.matches(norm)) {
            val parsedDate = SpanishDateTimeParser.parse(raw, nowMs = nowMs, zoneId = zoneId)
            return if (norm.contains("hoy") && parsedDate.dueDateEpochDay != null) {
                TaskIntentAction.QueryTasks(TaskQueryScope.SPECIFIC_DATE, parsedDate.dueDateEpochDay)
            } else {
                TaskIntentAction.QueryTasks(TaskQueryScope.ALL_PENDING)
            }
        }
        if (queryTodayRegex.containsMatchIn(norm)) {
            return TaskIntentAction.QueryTasks(TaskQueryScope.TODAY)
        }
        if (queryUpcomingRegex.containsMatchIn(norm)) {
            val temp = SpanishDateTimeParser.parse(raw, nowMs = nowMs, zoneId = zoneId)
            return if (temp.dueDateEpochDay != null && !norm.contains("proximas")) {
                TaskIntentAction.QueryTasks(TaskQueryScope.SPECIFIC_DATE, temp.dueDateEpochDay)
            } else {
                TaskIntentAction.QueryTasks(TaskQueryScope.UPCOMING)
            }
        }
        if (queryPendingRegex.matches(norm)) {
            return TaskIntentAction.QueryTasks(TaskQueryScope.ALL_PENDING)
        }

        // 3. Completar tarea
        completeTaskRegex.find(norm)?.let { m ->
            val target = cleanTaskTargetPhrase(m.groupValues[1])
            if (target.isNotEmpty()) {
                return TaskIntentAction.CompleteTask(target)
            }
        }

        // 4. Posponer tarea ("Pospón esa tarea hasta el lunes", "Pospón lo del banco para mañana")
        postponeTaskRegex.find(norm)?.let { m ->
            val tail = m.groupValues[1].trim()
            val temporal = SpanishDateTimeParser.parse(raw, nowMs = nowMs, zoneId = zoneId)
            val cleanedRemainder = cleanPostponeRemainder(temporal.cleanedRemainderText)
            val refersToLast = cleanedRemainder.isEmpty() ||
                cleanedRemainder in setOf("esa tarea", "esta tarea", "la tarea", "ese recordatorio", "este recordatorio", "eso", "la")
            if (temporal.matchedExpression || refersToLast) {
                return TaskIntentAction.PostponeTask(
                    query = if (refersToLast) "" else cleanedRemainder,
                    refersToLastTask = refersToLast,
                    newDueDateEpochDay = temporal.dueDateEpochDay,
                    newDueTimeMinutes = temporal.dueTimeMinutes,
                    newReminderAtMs = temporal.reminderAtMs
                )
            }
        }

        // 5. Cancelar o eliminar recordatorio / tarea
        cancelTaskRegex.find(norm)?.let { m ->
            val target = cleanTaskTargetPhrase(m.groupValues[1])
            if (target.isNotEmpty()) {
                return TaskIntentAction.CancelTask(target)
            }
        }

        // 6. Seguimiento tras finalizar llamada ("Cuando termine la llamada, recuérdame enviarle el presupuesto")
        postCallPrefixRegex.find(norm)?.let { m ->
            val body = m.groupValues[1].trim()
            return buildCreateTaskAction(
                rawBody = body,
                originalUtterance = raw,
                nowMs = nowMs,
                zoneId = zoneId,
                forceReminder = true,
                linkLatestOrActiveCall = true
            )
        }

        // Agenda/evento: keep the calendar action local in the task store.
        agendaCreateRegex.find(norm)?.let { match ->
            val body = match.groupValues[1].trim()
            val temporal = SpanishDateTimeParser.parse(body, nowMs = nowMs, zoneId = zoneId)
            return buildCreateTaskAction(
                rawBody = body,
                originalUtterance = raw,
                nowMs = nowMs,
                zoneId = zoneId,
                forceReminder = temporal.matchedExpression
            )
        }

        // 7. Recordatorios explícitos ("Recuérdame comprar comida mañana", "Recuérdame llamar a Juan mañana")
        reminderCreateRegex.find(norm)?.let { m ->
            val body = m.groupValues[1].trim()
            return buildCreateTaskAction(
                rawBody = body,
                originalUtterance = raw,
                nowMs = nowMs,
                zoneId = zoneId,
                forceReminder = true
            )
        }

        // 8. Creación explícita ("Apunta que tengo que llamar a mi madre el domingo", "Añade una tarea urgente para esta tarde")
        explicitTaskCreateRegex.find(norm)?.let { m ->
            val body = m.groupValues[1].trim()
            val normStart = norm.startsWith("apunta que") || norm.startsWith("anota que")
            val isExplicitTaskKeyword = norm.contains("tarea") || norm.contains("recordatorio")
            val hasObligationOrDate = norm.contains("tengo que") ||
                norm.contains("debo") ||
                norm.contains("hay que") ||
                SpanishDateTimeParser.parse(raw, nowMs = nowMs, zoneId = zoneId).matchedExpression
            if (!normStart || isExplicitTaskKeyword || hasObligationOrDate) {
                return buildCreateTaskAction(
                    rawBody = body,
                    originalUtterance = raw,
                    nowMs = nowMs,
                    zoneId = zoneId,
                    forceReminder = isExplicitTaskKeyword || hasObligationOrDate
                )
            }
        }

        // 9. Devolver llamada ("Tengo que devolver una llamada a María")
        callbackFollowUpRegex.find(norm)?.let { m ->
            val target = cleanTaskTargetPhrase(m.groupValues[1])
            if (target.isNotEmpty()) {
                val temporal = SpanishDateTimeParser.parse(raw, nowMs = nowMs, zoneId = zoneId)
                val cleanContact = cleanTaskTargetPhrase(
                    SpanishDateTimeParser.parse(m.groupValues[1], nowMs = nowMs, zoneId = zoneId).cleanedRemainderText
                )
                return TaskIntentAction.CreateTask(
                    title = "Devolver llamada a ${cleanContact.replaceFirstChar { it.uppercase() }}",
                    priority = TaskPriority.HIGH,
                    dueDateEpochDay = temporal.dueDateEpochDay,
                    dueTimeMinutes = temporal.dueTimeMinutes,
                    reminderAtMs = temporal.reminderAtMs,
                    category = "Llamadas",
                    contactQuery = cleanContact,
                    isCallFollowUp = true
                )
            }
        }

        // 10. Obligación con verbo de acción claro ("Tengo que comprar pan mañana")
        obligationTaskRegex.find(norm)?.let { m ->
            val body = m.groupValues[1].trim()
            val temporal = SpanishDateTimeParser.parse(body, nowMs = nowMs, zoneId = zoneId)
            val isCall = callTargetInTitleRegex.matches(body)
            if (temporal.matchedExpression || isCall) {
                return buildCreateTaskAction(
                    rawBody = body,
                    originalUtterance = raw,
                    nowMs = nowMs,
                    zoneId = zoneId,
                    forceReminder = temporal.matchedExpression
                )
            }
        }

        return TaskIntentAction.None
    }

    private fun buildCreateTaskAction(
        rawBody: String,
        originalUtterance: String,
        nowMs: Long,
        zoneId: ZoneId,
        forceReminder: Boolean,
        linkLatestOrActiveCall: Boolean = false
    ): TaskIntentAction.CreateTask {
        var priority = TaskPriority.NORMAL
        var body = stripPoliteSuffix(rawBody)

        // Detectar prioridad en lenguaje natural
        val urgentRegex = Regex("""\b(?:muy\s+)?urgent(?:e|emente)?\b|\bprioridad\s+alta\b|\bimportante\b""")
        val lowRegex = Regex("""\b(?:cuando\s+pueda|sin\s+prisa|baja\s+prioridad|prioridad\s+baja)\b""")
        if (urgentRegex.containsMatchIn(body) || MemoryTextSimilarity.normalize(originalUtterance).contains("urgente")) {
            priority = if (MemoryTextSimilarity.normalize(originalUtterance).contains("urgente")) {
                TaskPriority.URGENT
            } else {
                TaskPriority.HIGH
            }
            body = body.replace(urgentRegex, " ").replace(Regex("""\s+"""), " ").trim()
        } else if (lowRegex.containsMatchIn(body)) {
            priority = TaskPriority.LOW
            body = body.replace(lowRegex, " ").replace(Regex("""\s+"""), " ").trim()
        }

        val temporal = SpanishDateTimeParser.parse(
            rawText = body,
            nowMs = nowMs,
            zoneId = zoneId,
            defaultReminderIfDateOnly = forceReminder
        )
        var cleanedTitle = temporal.cleanedRemainderText
            .replace(Regex("""^(?:de|para|que|una\s+tarea|tarea|el|la)\s+"""), "")
            .replace(Regex("""\s+(?:para|el|en)$"""), "")
            .trim()

        // Evitar restos de palabras vacías cuando el usuario solo dijo "Añade una tarea urgente para esta tarde"
        if (cleanedTitle in setOf("tarea", "una tarea", "recordatorio", "un recordatorio", "urgente", "para", "de")) {
            cleanedTitle = ""
        }

        val callMatch = callTargetInTitleRegex.find(cleanedTitle)
        val isCallFollowUp = callMatch != null || linkLatestOrActiveCall
        val contactQuery = callMatch?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }

        val category = when {
            isCallFollowUp -> "Llamadas"
            cleanedTitle.contains("compr") || cleanedTitle.contains("super") -> "Compras"
            cleanedTitle.contains("banco") || cleanedTitle.contains("pag") || cleanedTitle.contains("factur") || cleanedTitle.contains("presupuest") -> "Finanzas"
            else -> ""
        }

        val displayTitle = if (cleanedTitle.isNotEmpty()) {
            cleanedTitle.replaceFirstChar { it.uppercase() }
        } else {
            ""
        }

        if (displayTitle.isEmpty()) {
            val whenLabel = SpanishDateTimeParser.formatDateTimeSummary(
                temporal.dueDateEpochDay,
                temporal.dueTimeMinutes,
                temporal.reminderAtMs,
                nowMs,
                zoneId
            )
            val prioLabel = if (priority == TaskPriority.URGENT) " urgente" else ""
            val prompt = "Entendido, una tarea$prioLabel ($whenLabel). ¿Qué título quieres que apunte?"
            return TaskIntentAction.CreateTask(
                title = "",
                priority = priority,
                dueDateEpochDay = temporal.dueDateEpochDay,
                dueTimeMinutes = temporal.dueTimeMinutes,
                reminderAtMs = temporal.reminderAtMs,
                category = category,
                missingTitlePrompt = PendingTaskPrompt.ClarifyMissingTitle(
                    priority = priority,
                    category = category,
                    dueDateEpochDay = temporal.dueDateEpochDay,
                    dueTimeMinutes = temporal.dueTimeMinutes,
                    reminderAtMs = temporal.reminderAtMs,
                    promptMessage = prompt
                )
            )
        }

        val ambiguityPrompt = if (
            temporal.isAmbiguous &&
            temporal.ambiguityOptionAMinutes != null &&
            temporal.ambiguityOptionBMinutes != null &&
            temporal.dueDateEpochDay != null &&
            temporal.ambiguityPrompt != null
        ) {
            PendingTaskPrompt.ClarifyTemporalAmbiguity(
                draftTitle = displayTitle,
                priority = priority,
                category = category,
                isCallFollowUp = isCallFollowUp,
                contactQuery = contactQuery,
                linkLatestOrActiveCall = linkLatestOrActiveCall,
                dateEpochDay = temporal.dueDateEpochDay,
                optionA24HourMinutes = temporal.ambiguityOptionAMinutes,
                optionB24HourMinutes = temporal.ambiguityOptionBMinutes,
                promptMessage = "Para «$displayTitle», ${temporal.ambiguityPrompt.replaceFirstChar { it.lowercase() }}"
            )
        } else {
            null
        }

        return TaskIntentAction.CreateTask(
            title = displayTitle,
            priority = priority,
            dueDateEpochDay = temporal.dueDateEpochDay,
            dueTimeMinutes = temporal.dueTimeMinutes,
            reminderAtMs = if (forceReminder) temporal.reminderAtMs else temporal.reminderAtMs?.takeIf { temporal.dueTimeMinutes != null },
            category = category,
            contactQuery = contactQuery,
            isCallFollowUp = isCallFollowUp,
            linkLatestOrActiveCall = linkLatestOrActiveCall,
            ambiguityPrompt = ambiguityPrompt
        )
    }

    private fun cleanTaskTargetPhrase(raw: String): String = stripPoliteSuffix(raw)
        .replace(Regex("""^(?:lo\s+de(?:l)?|la\s+tarea(?:\s+de)?|el\s+recordatorio(?:\s+de)?|con|de)\s+"""), "")
        .trim()

    private fun cleanPostponeRemainder(raw: String): String = stripPoliteSuffix(raw)
        .replace(Regex("""^(?:pospon(?:er)?|aplaza(?:r)?|mueve|cambia)\s+"""), "")
        .replace(Regex("""^(?:lo\s+de(?:l)?|la\s+tarea(?:\s+de)?|el\s+recordatorio(?:\s+de)?)\s+"""), "")
        .trim()

    private fun parseCandidateIndex(norm: String): Int? = when {
        norm.contains("primero") || norm.contains("primera") || norm == "uno" || norm == "el uno" || norm == "la una" || norm == "1" -> 0
        norm.contains("segundo") || norm.contains("segunda") || norm == "dos" || norm == "el dos" || norm == "2" -> 1
        norm.contains("tercero") || norm.contains("tercera") || norm == "tres" || norm == "el tres" || norm == "3" -> 2
        norm.contains("cuarto") || norm.contains("cuarta") || norm == "cuatro" || norm == "4" -> 3
        else -> null
    }

    private fun stripPoliteSuffix(raw: String): String = raw
        .trim()
        .trimEnd('.', ',', ';', '?', '!')
        .replace(Regex("""(?:\s*,?\s*(?:por favor|porfa|gracias|orion))+$""", RegexOption.IGNORE_CASE), "")
        .trim()
}
