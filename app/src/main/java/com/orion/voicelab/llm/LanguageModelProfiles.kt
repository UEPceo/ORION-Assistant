package com.orion.voicelab.llm

enum class ModelExecutionMode { ON_DEVICE_OFFLINE, ON_DEVICE_ACCELERATED, USER_CONFIGURED_REMOTE }
enum class ModelCapability { GENERAL_CHAT, MULTI_STEP_REASONING, CODE, LONG_CONTEXT, IMAGE_UNDERSTANDING, IMAGE_GENERATION, VIDEO_UNDERSTANDING, VIDEO_GENERATION }
data class LanguageModelProfile(val id: String, val displayName: String, val mode: ModelExecutionMode, val contextTokens: Int, val approximateRamMb: Int?, val license: String, val capabilities: Set<ModelCapability>, val operational: Boolean, val configurationNote: String)

object LanguageModelProfileCatalog {
    val profiles = listOf(
        LanguageModelProfile("qwen2.5-1.5b-local", "Qwen2.5 1.5B Instruct Q4_K_M", ModelExecutionMode.ON_DEVICE_OFFLINE, 2048, 1400, "Apache 2.0", setOf(ModelCapability.GENERAL_CHAT, ModelCapability.CODE), true, "Perfil ligero actual. No es suficiente por sí solo para razonamiento complejo."),
        LanguageModelProfile("local-advanced", "Modelo local avanzado (por configurar)", ModelExecutionMode.ON_DEVICE_OFFLINE, 8192, null, "Depende del modelo elegido", setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE, ModelCapability.LONG_CONTEXT), false, "Requiere GGUF compatible y pruebas de RAM, licencia, contexto y rendimiento."),
        LanguageModelProfile("remote-consented", "Modelo remoto elegido por el usuario", ModelExecutionMode.USER_CONFIGURED_REMOTE, 32768, null, "Depende del proveedor", setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE, ModelCapability.LONG_CONTEXT), false, "No se activa por defecto; requiere endpoint, credenciales y consentimiento por operación.")
    )
    fun available(mode: ModelExecutionMode): List<LanguageModelProfile> = profiles.filter { it.mode == mode && it.operational }

    fun select(
        mode: ModelExecutionMode,
        required: Set<ModelCapability>,
        availableRamMb: Int? = null
    ): LanguageModelProfile? = available(mode)
        .filter { profile ->
            profile.capabilities.containsAll(required) &&
                (availableRamMb == null || profile.approximateRamMb == null ||
                    profile.approximateRamMb <= availableRamMb)
        }
        .maxByOrNull { it.capabilities.size }
}
