package com.orion.voicelab.llm

import android.content.Context
import com.orion.voicelab.data.ChatMessage
import kotlinx.coroutines.CancellationException

/**
 * Hybrid execution policy: the phone remains the default execution target.
 * Remote execution is possible only after both the remote toggle and context-sharing consent.
 */
class HybridLanguageModel(
    private val context: Context,
    private val local: LocalLanguageModel
) : LocalLanguageModel {
    override val profileId: String get() = local.profileId
    override val modelDisplayName: String get() = local.modelDisplayName
    override val modelLicense: String get() = local.modelLicense

    private fun settings() = RemoteInferenceSettings.read(context)
    private fun remoteOnly() = settings().let {
        it.isConfigured && it.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY
    }

    override fun isModelInstalled(): Boolean = remoteOnly() || local.isModelInstalled()
    override fun isLoaded(): Boolean = remoteOnly() || local.isLoaded()
    override fun modelSizeMb(): Long = if (remoteOnly()) 0L else local.modelSizeMb()
    override suspend fun load(): Long = if (remoteOnly()) 0L else local.load()
    override fun cancelGeneration() = local.cancelGeneration()
    override fun unload() = local.unload()

    override suspend fun generateReply(
        systemPrompt: String,
        history: List<ChatMessage>,
        onToken: (String) -> Unit
    ): LlmGenerationResult {
        val config = settings()
        val remoteAllowed = config.isConfigured && config.mode != RemoteInferenceSettings.MODE_LOCAL
        val remotePreferred = config.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY ||
            (config.mode == RemoteInferenceSettings.MODE_AUTO && isComplexRequest(history.lastOrNull()?.content.orEmpty()))
        if (remoteAllowed && remotePreferred) {
            try {
                val result = RemoteInferenceClient(
                    endpoint = config.endpoint,
                    model = config.model,
                    apiKey = RemoteInferenceSettings.apiKey(context)
                ).generate(systemPrompt, history)
                if (!result.cancelled) onToken(result.text)
                return result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (config.mode == RemoteInferenceSettings.MODE_REMOTE_ONLY) {
                    throw IllegalStateException("No se pudo usar el motor remoto y el modo está configurado como Solo remoto. No se ha enviado la petición al motor local. " +
                        (error.message ?: "Error de conexión."), error)
                }
                // Automatic mode falls back locally; it never changes the privacy setting or sends elsewhere.
            }
        }
        check(!remoteOnly()) { "El modo remoto requiere un endpoint configurado y autorización para compartir contexto." }
        return local.generateReply(systemPrompt, history, onToken)
    }

    private fun isComplexRequest(text: String): Boolean {
        val normalized = text.lowercase()
        val complexitySignals = listOf(
            "programa", "programar", "código", "codigo", "arquitectura", "auditoría", "auditoria",
            "analiza", "compara", "investiga", "planifica", "estrategia", "razona", "depura",
            "implementa", "diseña", "diseño", "matemática", "matematicas", "finanzas",
            "paso a paso", "varios pasos", "informe", "documentación", "documentacion"
        )
        return text.length >= 900 || complexitySignals.any(normalized::contains)
    }
}
