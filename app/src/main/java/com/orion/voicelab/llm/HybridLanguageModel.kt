package com.orion.voicelab.llm

import android.content.Context
import com.orion.voicelab.data.ChatMessage
import kotlinx.coroutines.CancellationException

/**
 * Hybrid execution policy: the phone remains the default execution target.
 * Remote execution requires both the remote toggle and explicit context-sharing consent.
 */
class HybridLanguageModel(
    private val context: Context,
    private val local: LocalLanguageModel
) : LocalLanguageModel {
    override val profileId: String get() = local.profileId
    override val modelDisplayName: String get() = local.modelDisplayName
    override val modelLicense: String get() = local.modelLicense

    @Volatile private var activeRemoteClient: RemoteInferenceClient? = null

    private fun settings() = RemoteInferenceSettings.read(context)
    private fun remoteOnlyRequested() = settings().let {
        it.enabled && it.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY
    }
    private fun remoteOnlyConfigured() = settings().let {
        it.isConfigured && it.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY
    }

    override fun isModelInstalled(): Boolean =
        if (remoteOnlyRequested()) remoteOnlyConfigured() else local.isModelInstalled()

    override fun isLoaded(): Boolean =
        if (remoteOnlyRequested()) remoteOnlyConfigured() else local.isLoaded()

    override fun modelSizeMb(): Long = if (remoteOnlyRequested()) 0L else local.modelSizeMb()

    override suspend fun load(): Long {
        if (!remoteOnlyRequested()) return local.load()
        check(remoteOnlyConfigured()) {
            "El modo Solo remoto necesita endpoint, modelo y consentimiento de contexto configurados."
        }
        return 0L
    }

    override fun cancelGeneration() {
        activeRemoteClient?.cancel()
        local.cancelGeneration()
    }
    override fun unload() = local.unload()

    override suspend fun generateReply(
        systemPrompt: String,
        history: List<ChatMessage>,
        onToken: (String) -> Unit
    ): LlmGenerationResult {
        val config = settings()
        val remoteAllowed = config.isConfigured && config.mode != RemoteInferenceSettings.MODE_LOCAL
        val remotePreferred = HybridRoutingPolicy.shouldUseRemote(
            mode = config.mode,
            request = history.lastOrNull()?.content.orEmpty()
        )
        if (config.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY && !remoteAllowed) {
            error("El modo Solo remoto necesita endpoint, modelo y consentimiento de contexto configurados.")
        }
        if (remoteAllowed && remotePreferred) {
            val client = RemoteInferenceClient(
                endpoint = config.endpoint,
                model = config.model,
                apiKey = RemoteInferenceSettings.apiKey(context)
            )
            activeRemoteClient = client
            try {
                val result = client.generate(systemPrompt, history)
                if (!result.cancelled) onToken(result.text)
                return result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (config.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY) {
                    throw IllegalStateException(
                        "No se pudo usar el motor remoto y el modo está configurado como Solo remoto. No se ha enviado la petición al motor local. " +
                            (error.message ?: "Error de conexión."),
                        error
                    )
                }
                // Automatic mode falls back locally without changing privacy settings or provider.
            } finally {
                if (activeRemoteClient === client) activeRemoteClient = null
            }
        }
        check(!remoteOnlyRequested()) {
            "El modo remoto requiere un endpoint configurado y autorización para compartir contexto."
        }
        return local.generateReply(systemPrompt, history, onToken)
    }
}
