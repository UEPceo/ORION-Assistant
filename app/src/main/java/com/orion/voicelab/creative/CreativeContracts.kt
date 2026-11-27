package com.orion.voicelab.creative

import android.net.Uri

data class CreativeBrandProfile(
    val id: String,
    val name: String,
    val visualDirection: String,
    val primaryColors: List<String>,
    val typographyDirection: String,
    val compositionRules: List<String>,
    val toneOfVoice: String,
    val avoid: List<String> = emptyList()
) {
    companion object {
        val ORION_DEFAULT = CreativeBrandProfile(
            id = "orion-editorial-cyan",
            name = "ORION Editorial",
            visualDirection = "Tecnológico, editorial, sobrio y reconocible; jerarquía clara y composición limpia.",
            primaryColors = listOf("#080D1A", "#16C7E8", "#FFFFFF"),
            typographyDirection = "Sans serif contemporánea, titulares compactos y texto funcional muy legible.",
            compositionRules = listOf("Un mensaje principal por pieza", "Contraste alto y espacio negativo suficiente", "Jerarquía visual clara para móvil", "Respetar zonas seguras de cada plataforma"),
            toneOfVoice = "Directo, útil, humano y sin exageraciones",
            avoid = listOf("Texto ilegible", "Marcas de agua falsas", "Afirmaciones sin fuente", "Clics engañosos")
        )
    }
}

enum class CreativeTaskType { BRAND_IDENTITY, SOCIAL_POST, CAROUSEL, THUMBNAIL, POSTER, IMAGE, VIDEO, SCRIPT, CONTENT_CALENDAR }
enum class CreativeOutputKind { TEXT_BRIEF, DESIGN_SPEC, IMAGE, VIDEO, STORYBOARD }
enum class MediaProviderKind { LOCAL, REMOTE, EXTERNAL_APP }
enum class MediaCapabilityStatus { AVAILABLE, REQUIRES_MODEL, REQUIRES_CONFIGURATION, UNSUPPORTED }

data class CreativeBrief(
    val task: CreativeTaskType,
    val objective: String,
    val audience: String,
    val platform: String? = null,
    val brand: CreativeBrandProfile = CreativeBrandProfile.ORION_DEFAULT,
    val requiredText: List<String> = emptyList(),
    val durationSeconds: Int? = null,
    val aspectRatio: String? = null,
    val referenceUris: List<Uri> = emptyList()
)

data class CreativeDesignSpec(
    val concept: String,
    val hook: String?,
    val layout: String,
    val palette: List<String>,
    val typography: String,
    val scenePlan: List<String>,
    val copy: List<String>,
    val accessibilityNotes: List<String>,
    val verificationNotes: List<String>
)

data class MediaAsset(
    val uri: Uri,
    val mimeType: String,
    val displayName: String? = null,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val sizeBytes: Long? = null
)

data class MediaFrame(val timestampMs: Long, val image: MediaAsset)

data class MediaUnderstandingResult(
    val summary: String,
    val objectsAndSubjects: List<String>,
    val visibleText: List<String>,
    val actionsAndEvents: List<String>,
    val timeline: List<String>,
    val uncertaintyNotes: List<String>,
    val provider: String,
    val processedAtMs: Long
)

data class MediaGenerationRequest(
    val prompt: String,
    val negativePrompt: String? = null,
    val aspectRatio: String? = null,
    val durationSeconds: Int? = null,
    val referenceAssets: List<MediaAsset> = emptyList(),
    val brand: CreativeBrandProfile = CreativeBrandProfile.ORION_DEFAULT
)

data class GeneratedMedia(
    val asset: MediaAsset,
    val provider: String,
    val model: String,
    val createdAtMs: Long,
    val watermarkOrDisclosure: String? = null
)

interface MediaUnderstandingProvider {
    val providerId: String
    fun capability(mimeType: String): MediaCapabilityStatus
    suspend fun understandImage(asset: MediaAsset): MediaUnderstandingResult
    suspend fun understandVideo(asset: MediaAsset, sampledFrames: List<MediaFrame>): MediaUnderstandingResult
}

interface ImageGenerationProvider {
    val providerId: String
    fun capability(): MediaCapabilityStatus
    suspend fun generate(request: MediaGenerationRequest): GeneratedMedia
}

interface VideoGenerationProvider {
    val providerId: String
    fun capability(): MediaCapabilityStatus
    suspend fun generate(request: MediaGenerationRequest): GeneratedMedia
}

object VideoFrameSamplingPolicy {
    fun timestamps(durationMs: Long, maxFrames: Int = 12): List<Long> {
        if (durationMs <= 0L || maxFrames <= 0) return emptyList()
        val count = minOf(maxFrames, ((durationMs / 2_000L).coerceAtLeast(1L)).toInt())
        if (count == 1) return listOf(durationMs / 2L)
        val last = (durationMs - 1L).coerceAtLeast(0L)
        return (0 until count).map { index -> (last * index) / (count - 1) }
    }
}
