package com.orion.voicelab.connectors

/**
 * Stable boundary between ORION's deterministic intent routing and external services.
 * Provider implementations must keep credentials out of prompts, logs and action results.
 */
enum class ConnectorId {
    GMAIL,
    GITHUB,
    GOOGLE_CALENDAR,
    GOOGLE_DRIVE,
    ANDROID_NOTES,
    ANDROID_CALENDAR,
    GOOGLE_MAPS,
    PESCAIA
}

enum class ConnectorConnectionState {
    NOT_CONFIGURED,
    AUTHENTICATING,
    CONNECTED,
    EXPIRED,
    ERROR,
    DISABLED
}

enum class ConnectorCapability {
    SEARCH,
    READ,
    CREATE_DRAFT,
    SEND,
    CREATE,
    UPDATE,
    DELETE,
    COMMENT
}

enum class ConnectorRisk {
    READ_ONLY,
    REVERSIBLE_WRITE,
    EXTERNAL_SIDE_EFFECT,
    DESTRUCTIVE
}

data class ConnectorDescriptor(
    val id: ConnectorId,
    val displayName: String,
    val description: String,
    val capabilities: Set<ConnectorCapability>,
    val isImplemented: Boolean
)

data class ConnectorAction(
    val connectorId: ConnectorId,
    val operation: String,
    val arguments: Map<String, String> = emptyMap(),
    val risk: ConnectorRisk = ConnectorRisk.READ_ONLY,
    val idempotencyKey: String? = null
)

sealed interface ConnectorResult {
    data class Success(
        val summary: String,
        val externalId: String? = null
    ) : ConnectorResult

    data class NeedsUserInput(val prompt: String) : ConnectorResult

    data class ConfirmationRequired(val action: ConnectorAction, val prompt: String) : ConnectorResult
    data class Failure(val safeMessage: String, val retryable: Boolean) : ConnectorResult
}

/** Implemented by provider modules; no provider is considered usable until registered. */
interface OrionConnector {
    val descriptor: ConnectorDescriptor
    suspend fun connectionState(): ConnectorConnectionState
    suspend fun execute(action: ConnectorAction): ConnectorResult
}

/**
 * Token persistence boundary. Implementations must encrypt at rest with Android Keystore
 * and must never expose raw tokens to the LLM or diagnostic exports.
 */
interface ConnectorCredentialStore {
    /** Accepts raw credential bytes transiently; implementations must encrypt before persistence. */
    suspend fun save(connectorId: ConnectorId, credential: ByteArray)
    suspend fun read(connectorId: ConnectorId): ByteArray?
    suspend fun delete(connectorId: ConnectorId)
}
