package com.orion.voicelab.data

enum class PhoneContactSource {
    NATIVE_AGENDA,
    NATIVE_PICKER,
    MANUAL
}

data class PhoneContactEntry(
    val contactKey: String,
    val displayName: String,
    val phoneNumber: String,
    val normalizedNumber: String,
    val label: String = "Teléfono",
    val isFavorite: Boolean = false,
    val source: PhoneContactSource = PhoneContactSource.NATIVE_AGENDA
)

enum class PhoneCallStatus(val displayLabel: String) {
    PREPARED("Preparada"),
    AWAITING_CONFIRMATION("Esperando confirmación"),
    DIALING_UI_OPENED("Marcador abierto"),
    CALL_INITIATED("Llamada iniciada"),
    IN_PROGRESS("En curso"),
    COMPLETED("Finalizada"),
    CANCELLED("Cancelada"),
    FAILED("Error");

    companion object {
        fun fromStored(raw: String?): PhoneCallStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: PREPARED
    }
}

enum class SystemTelephonyState(val displayLabel: String) {
    IDLE("En espera"),
    OFFHOOK("Llamada activa"),
    RINGING("Llamada entrante"),
    UNAVAILABLE("Estado no disponible")
}

data class PhoneCallRecord(
    val id: Long = 0L,
    val contactName: String,
    val phoneNumber: String,
    val reason: String = "",
    val status: PhoneCallStatus = PhoneCallStatus.PREPARED,
    val createdAtMs: Long = 0L,
    val startedAtMs: Long? = null,
    val endedAtMs: Long? = null,
    val durationSeconds: Long = 0L,
    val scheduledNote: String = "",
    val userNotes: String = "",
    val outcome: String = "",
    val followUpPending: Boolean = false
)

data class PendingCallRequest(
    val requestId: String,
    val contactName: String,
    val phoneNumber: String,
    val reason: String = "",
    val scheduledNote: String = "",
    val candidates: List<PhoneContactEntry> = emptyList(),
    val selectedCandidate: PhoneContactEntry? = null,
    val awaitingConfirmation: Boolean = false,
    val source: String = "UI"
)

enum class HistoryQueryType {
    WHEN_CALLED,
    PENDING_WITH,
    CALLBACKS
}

sealed interface CallIntentAction {
    data object None : CallIntentAction

    data class InitiateCall(
        val rawTargetQuery: String,
        val reason: String = "",
        val scheduledOnly: Boolean = false,
        val scheduledNote: String = ""
    ) : CallIntentAction

    data object CallLastContact : CallIntentAction

    data class LookupContactNumber(
        val rawTargetQuery: String
    ) : CallIntentAction

    data object ConfirmPendingCall : CallIntentAction

    data object CancelPendingCall : CallIntentAction

    data class SelectCandidateByIndex(
        val index: Int
    ) : CallIntentAction

    data class SelectCandidateByName(
        val query: String
    ) : CallIntentAction

    data class QueryCallHistory(
        val queryType: HistoryQueryType,
        val contactQuery: String? = null
    ) : CallIntentAction
}
