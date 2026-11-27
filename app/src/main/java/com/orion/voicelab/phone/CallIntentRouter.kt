package com.orion.voicelab.phone

import com.orion.voicelab.data.CallIntentAction
import com.orion.voicelab.data.HistoryQueryType
import com.orion.voicelab.data.PendingCallRequest
import com.orion.voicelab.memory.MemoryTextSimilarity

/**
 * Enrutador determinista de intenciones telefónicas en español, separado del LLM local.
 * Evita que acciones críticas (confirmar, cancelar, identificar contacto o consultar llamadas)
 * dependan de texto generado libremente por el modelo de lenguaje.
 */
object CallIntentRouter {

    private val cancelPatterns = listOf(
        Regex("""^(?:no|cancela|cancelar|cancelalo|cancelala|anula|anular|aborta|abortar|para|deten|olvida|olvidalo|olvidala|mejor no|dejalo|dejala|espera no)(?:\b.*)?$"""),
        Regex("""\b(?:no\s+llames|cancela\s+la\s+llamada|cancelar\s+llamada|mejor\s+no\s+llames|olvida\s+la\s+llamada|no\s+espera)\b""")
    )

    private val confirmPhrases = setOf(
        "si", "si llama", "si llamalo", "si llamala", "si por favor", "confirmo", "confirmar",
        "adelante", "haz la llamada", "hazlo", "vale", "vale llama", "de acuerdo", "procede",
        "llamalo", "llamala", "llama ya", "inicia la llamada", "claro", "por supuesto"
    )

    private val lastContactRegex = Regex(
        """(?:llama|llamar|marca|comunica)(?:me)?\s+(?:otra vez\s+)?al\s+(?:ultimo|ultima)\s*(?:contacto|numero)?|volver\s+a\s+llamar|rellamar"""
    )

    private val whenCalledRegex = Regex(
        """(?:cuando|que dia|a que hora)\s+llame\s+(?:a\s+)?(.+)"""
    )

    private val pendingWithRegex = Regex(
        """(?:que\s+tenia|que\s+tengo|algo)\s+(?:pendiente(?:\s+(?:de|para)\s+hablar)?|que\s+hablar)\s+con\s+(.+)"""
    )

    private val callbacksRegex = Regex(
        """(?:a\s+quien\s+(?:tengo\s+que|debo)\s+(?:devolver\s+la\s+llamada|llamar)|que\s+llamadas\s+tengo\s+pendientes|llamadas\s+pendientes)"""
    )

    private val prepareCallRegex = Regex(
        """^(?:por favor\s+)?(?:prepara|agenda|programa|anota|apunta)\s+(?:una\s+)?llamada(?:\s+(?:a|con)\s+(.+?))?(?:\s+(para\s+(?:manana|hoy|luego|despues|mas tarde|esta tarde|la semana que viene).*))?$"""
    )

    private val lookupRegex = Regex(
        """^(?:por favor\s+)?(?:busca|dime|cual es|consulta)\s+(?:el\s+)?(?:telefono|numero(?:\s+de\s+telefono)?)\s+(?:de\s+)?(.+)$"""
    )

    private val directCallRegex = Regex(
        """^(?:por favor\s+)?(?:oye\s+)?(?:orion\s+)?(?:quiero\s+llamar|necesito\s+llamar|puedes\s+llamar|quiero\s+hacer\s+una\s+llamada|haz\s+una\s+llamada|realiza\s+una\s+llamada|llama(?:me)?|marca(?:me)?)\s+(?:a|al)\s+(.+)$"""
    )

    fun route(
        utterance: String,
        pendingCall: PendingCallRequest?
    ): CallIntentAction {
        val raw = utterance.trim()
        if (raw.isEmpty()) return CallIntentAction.None
        val norm = MemoryTextSimilarity.normalize(raw)
        if (norm.isEmpty()) return CallIntentAction.None

        // 1. Cancelación inmediata siempre que haya flujo telefónico pendiente o petición explícita de cancelar llamada
        if (cancelPatterns.any { it.matches(norm) || it.containsMatchIn(norm) }) {
            if (pendingCall != null || norm.contains("llamada")) {
                return CallIntentAction.CancelPendingCall
            }
        }

        // 2. Estado de confirmación explícita pendiente
        if (pendingCall != null && pendingCall.awaitingConfirmation) {
            if (isExplicitConfirmation(norm)) {
                return CallIntentAction.ConfirmPendingCall
            }
        }

        // 3. Estado de desambiguación (varios contactos posibles)
        if (pendingCall != null && pendingCall.candidates.size > 1 && !pendingCall.awaitingConfirmation) {
            parseCandidateIndex(norm)?.let { idx ->
                if (idx in pendingCall.candidates.indices) {
                    return CallIntentAction.SelectCandidateByIndex(idx)
                }
            }
            if (norm.split(' ').size <= 5 &&
                !norm.contains("llama") &&
                !norm.contains("busca") &&
                norm !in confirmPhrases
            ) {
                return CallIntentAction.SelectCandidateByName(raw)
            }
        }

        // 4. Consultas locales sobre historial de llamadas y seguimiento
        whenCalledRegex.find(norm)?.let { match ->
            val target = stripPoliteSuffix(extractTargetSegment(raw, match.groupValues[1]))
            if (target.isNotEmpty()) {
                return CallIntentAction.QueryCallHistory(HistoryQueryType.WHEN_CALLED, target)
            }
        }
        pendingWithRegex.find(norm)?.let { match ->
            val target = stripPoliteSuffix(extractTargetSegment(raw, match.groupValues[1]))
            if (target.isNotEmpty()) {
                return CallIntentAction.QueryCallHistory(HistoryQueryType.PENDING_WITH, target)
            }
        }
        if (callbacksRegex.containsMatchIn(norm)) {
            return CallIntentAction.QueryCallHistory(HistoryQueryType.CALLBACKS, null)
        }

        // 5. Llamar al último contacto
        if (lastContactRegex.containsMatchIn(norm)) {
            return CallIntentAction.CallLastContact
        }

        // 6. Preparar / agendar llamada sin marcar todavía
        prepareCallRegex.find(norm)?.let { match ->
            val targetRaw = match.groupValues.getOrNull(1).orEmpty().trim()
            val scheduleTail = match.groupValues.getOrNull(2).orEmpty().trim()
            val (targetName, reason) = splitTargetAndReason(targetRaw)
            val restoredSchedule = scheduleTail
                .replace("manana", "mañana")
                .replace("despues", "después")
                .replace("mas tarde", "más tarde")
            val scheduledNote = restoredSchedule.ifBlank {
                if (norm.contains("manana")) "Para mañana" else "Preparada"
            }
            return CallIntentAction.InitiateCall(
                rawTargetQuery = targetName,
                reason = reason,
                scheduledOnly = true,
                scheduledNote = scheduledNote.replaceFirstChar { it.uppercase() }
            )
        }

        // 7. Buscar número de teléfono
        lookupRegex.find(norm)?.let { match ->
            val target = stripPoliteSuffix(match.groupValues[1])
            if (target.isNotEmpty()) {
                return CallIntentAction.LookupContactNumber(target)
            }
        }

        // 8. Solicitud directa de llamada ("Quiero llamar a Juan", "Llama a mi madre", etc.)
        directCallRegex.find(norm)?.let { match ->
            val tail = match.groupValues[1].trim()
            val (targetName, reason) = splitTargetAndReason(tail)
            if (targetName.isNotEmpty()) {
                return CallIntentAction.InitiateCall(
                    rawTargetQuery = targetName,
                    reason = reason,
                    scheduledOnly = false,
                    scheduledNote = ""
                )
            }
        }

        return CallIntentAction.None
    }

    private fun isExplicitConfirmation(norm: String): Boolean {
        if (norm in confirmPhrases) return true
        return norm.startsWith("si ") && (
            norm.contains("confirm") ||
                norm.contains("llama") ||
                norm.contains("adelante") ||
                norm.contains("hazlo") ||
                norm.contains("vale")
            )
    }

    private fun parseCandidateIndex(norm: String): Int? = when {
        norm.contains("primero") || norm.contains("primera") || norm == "uno" || norm == "el uno" || norm == "1" -> 0
        norm.contains("segundo") || norm.contains("segunda") || norm == "dos" || norm == "el dos" || norm == "2" -> 1
        norm.contains("tercero") || norm.contains("tercera") || norm == "tres" || norm == "el tres" || norm == "3" -> 2
        norm.contains("cuarto") || norm.contains("cuarta") || norm == "cuatro" || norm == "4" -> 3
        else -> null
    }

    private fun splitTargetAndReason(rawTail: String): Pair<String, String> {
        val clean = stripPoliteSuffix(rawTail)
        if (clean.isEmpty()) return "" to ""
        val separators = listOf(" para ", " sobre ", " por ")
        for (sep in separators) {
            val idx = clean.indexOf(sep)
            if (idx > 0) {
                val target = stripPoliteSuffix(clean.substring(0, idx))
                val reason = stripPoliteSuffix(clean.substring(idx + sep.length))
                return target to reason
            }
        }
        return clean to ""
    }

    private fun stripPoliteSuffix(raw: String): String = raw
        .trim()
        .trimEnd('.', ',', ';', '?', '!')
        .replace(Regex("""(?:\s*,?\s*(?:por favor|porfa|gracias|orion))+$""", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun extractTargetSegment(original: String, normalizedMatch: String): String {
        val clean = normalizedMatch.trim().trimEnd('?', '!', '.', ',')
        if (clean.isEmpty()) return ""
        val origTokens = original.trim().trimEnd('?', '!', '.', ',').split(Regex("\\s+"))
        val normTokens = clean.split(Regex("\\s+"))
        return if (origTokens.size >= normTokens.size) {
            origTokens.takeLast(normTokens.size).joinToString(" ")
        } else {
            clean
        }
    }
}
