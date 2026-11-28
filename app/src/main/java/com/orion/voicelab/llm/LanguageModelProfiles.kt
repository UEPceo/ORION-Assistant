package com.orion.voicelab.llm

enum class ModelExecutionMode { ON_DEVICE_OFFLINE, ON_DEVICE_ACCELERATED, USER_CONFIGURED_REMOTE }
enum class ModelCapability { GENERAL_CHAT, MULTI_STEP_REASONING, CODE, LONG_CONTEXT, IMAGE_UNDERSTANDING, IMAGE_GENERATION, VIDEO_UNDERSTANDING, VIDEO_GENERATION }

enum class ModelTier { LITE, BALANCED, POWERFUL, FLAGSHIP }

data class LanguageModelProfile(
    val id: String,
    val displayName: String,
    val tier: ModelTier,
    val recommendedModels: List<String>,
    val approximateDownloadMb: Int,
    val minimumDeviceRamGb: Int,
    val recommendedDeviceRamGb: Int,
    val contextTokens: Int,
    val mode: ModelExecutionMode,
    val capabilities: Set<ModelCapability>,
    val operational: Boolean,
    val license: String,
    val configurationNote: String
)

object LanguageModelProfileCatalog {
    val profiles = listOf(
        LanguageModelProfile(
            id = "lite", displayName = "Ligero", tier = ModelTier.LITE,
            recommendedModels = listOf("Qwen2.5-1.5B-Instruct Q4_K_M (actual)", "Qwen3.5-0.8B / 1.5B Q4"),
            approximateDownloadMb = 1_300, minimumDeviceRamGb = 4, recommendedDeviceRamGb = 6,
            contextTokens = 2048, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.CODE),
            operational = true, license = "Apache 2.0 para el modelo actual; verificar cada variante",
            configurationNote = "Voz en tiempo real y dispositivos modestos. Es el único perfil descargable y ejecutable actualmente."
        ),
        LanguageModelProfile(
            id = "balanced", displayName = "Equilibrado", tier = ModelTier.BALANCED,
            recommendedModels = listOf("Qwen2.5-3B Q4_K_M", "Qwen3.5-2B / 4B Q4_K_M"),
            approximateDownloadMb = 2_700, minimumDeviceRamGb = 8, recommendedDeviceRamGb = 8,
            contextTokens = 4096, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE),
            operational = false, license = "Pendiente de verificar por artefacto",
            configurationNote = "Mejor razonamiento manteniendo una latencia moderada. Falta integrar y validar el GGUF."
        ),
        LanguageModelProfile(
            id = "powerful", displayName = "Potente", tier = ModelTier.POWERFUL,
            recommendedModels = listOf("Qwen3.5-4B", "Phi-4-mini", "Gemma 4 E4B"),
            approximateDownloadMb = 3_600, minimumDeviceRamGb = 8, recommendedDeviceRamGb = 12,
            contextTokens = 8192, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE, ModelCapability.LONG_CONTEXT),
            operational = false, license = "Pendiente de verificar por artefacto",
            configurationNote = "Planificación, código y tareas complejas. Cada variante necesita conversión GGUF, licencia y benchmark propios."
        ),
        LanguageModelProfile(
            id = "flagship", displayName = "Flagship", tier = ModelTier.FLAGSHIP,
            recommendedModels = listOf("Qwen3.5-9B Q4", "Modelos Q4 de 7–8B"),
            approximateDownloadMb = 6_000, minimumDeviceRamGb = 12, recommendedDeviceRamGb = 16,
            contextTokens = 8192, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE, ModelCapability.LONG_CONTEXT),
            operational = false, license = "Pendiente de verificar por artefacto",
            configurationNote = "Máxima calidad offline; requiere más memoria y tendrá mayor latencia y consumo."
        )
    )

    /** Selects a recommendation from physical device RAM, not momentary free RAM. */
    fun recommendForDevice(totalRamMb: Long): LanguageModelProfile {
        val ramGb = totalRamMb / 1024L
        return profiles.lastOrNull { ramGb >= it.minimumDeviceRamGb } ?: profiles.first()
    }

    fun byId(id: String): LanguageModelProfile? = profiles.firstOrNull { it.id == id }

    fun effectiveProfile(requestedId: String, totalRamMb: Long): LanguageModelProfile {
        val requested = byId(requestedId) ?: recommendForDevice(totalRamMb)
        if (requested.operational && totalRamMb / 1024L >= requested.minimumDeviceRamGb) return requested
        return profiles.first()
    }

    fun available(mode: ModelExecutionMode): List<LanguageModelProfile> =
        profiles.filter { it.mode == mode && it.operational }

    fun select(
        mode: ModelExecutionMode,
        required: Set<ModelCapability>,
        availableRamMb: Int? = null
    ): LanguageModelProfile? = available(mode)
        .filter { profile ->
            profile.capabilities.containsAll(required) &&
                (availableRamMb == null || profile.minimumDeviceRamGb * 1024 <= availableRamMb)
        }
        .maxByOrNull { it.capabilities.size }
}
