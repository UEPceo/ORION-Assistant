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
            recommendedModels = listOf("Qwen2.5-3B-Instruct Q4_K_M (GGUF verificado)"),
            approximateDownloadMb = 2_100, minimumDeviceRamGb = 8, recommendedDeviceRamGb = 8,
            contextTokens = 4096, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE),
            operational = true, license = "Apache 2.0",
            configurationNote = "Descargable: Qwen2.5-3B Q4_K_M. SHA-256 fijado; la carga se valida contra RAM libre y contexto."
        ),
        LanguageModelProfile(
            id = "powerful", displayName = "Potente", tier = ModelTier.POWERFUL,
            recommendedModels = listOf("Qwen2.5-7B-Instruct Q4_K_M (GGUF verificado)"),
            approximateDownloadMb = 4_680, minimumDeviceRamGb = 12, recommendedDeviceRamGb = 12,
            contextTokens = 4096, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE, ModelCapability.LONG_CONTEXT),
            operational = true, license = "Apache 2.0",
            configurationNote = "Descargable: Qwen2.5-7B Q4_K_M. Mayor capacidad; requiere margen amplio de RAM libre."
        ),
        LanguageModelProfile(
            id = "flagship", displayName = "Flagship", tier = ModelTier.FLAGSHIP,
            recommendedModels = listOf("Qwen2.5-14B-Instruct Q3_K_M (GGUF verificado)"),
            approximateDownloadMb = 7_340, minimumDeviceRamGb = 16, recommendedDeviceRamGb = 16,
            contextTokens = 2048, mode = ModelExecutionMode.ON_DEVICE_OFFLINE,
            capabilities = setOf(ModelCapability.GENERAL_CHAT, ModelCapability.MULTI_STEP_REASONING, ModelCapability.CODE, ModelCapability.LONG_CONTEXT),
            operational = true, license = "Apache 2.0 (modelo base Qwen2.5)",
            configurationNote = "Descargable: Qwen2.5-14B Q3_K_M. Perfil experimental; solo se carga con RAM libre suficiente."
        )
    )

    /** Android reports binary MiB; 950 MiB per marketed GB leaves a modest OS/runtime reserve. */
    private const val DEVICE_RAM_MB_PER_GB = 950L

    /** Selects a recommendation from physical device RAM, not momentary free RAM. */
    fun recommendForDevice(totalRamMb: Long): LanguageModelProfile =
        profiles.lastOrNull { totalRamMb >= it.recommendedDeviceRamGb * DEVICE_RAM_MB_PER_GB } ?: profiles.first()


    fun byId(id: String): LanguageModelProfile? = profiles.firstOrNull { it.id == id }

    fun effectiveProfile(requestedId: String, totalRamMb: Long): LanguageModelProfile {
        val requested = byId(requestedId) ?: recommendForDevice(totalRamMb)
        if (requested.operational && totalRamMb >= requested.minimumDeviceRamGb * DEVICE_RAM_MB_PER_GB) return requested
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
                (availableRamMb == null || profile.minimumDeviceRamGb * DEVICE_RAM_MB_PER_GB <= availableRamMb)
        }
        .maxByOrNull { it.capabilities.size }
}
