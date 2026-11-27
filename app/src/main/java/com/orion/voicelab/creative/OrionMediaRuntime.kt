package com.orion.voicelab.creative

/**
 * Media orchestration boundary. Concrete local/remote providers are injected by product
 * configuration; absence is reported honestly instead of fabricating an output.
 */
class OrionMediaRuntime(
    private val understandingProviders: List<MediaUnderstandingProvider> = emptyList(),
    private val imageProviders: List<ImageGenerationProvider> = emptyList(),
    private val videoProviders: List<VideoGenerationProvider> = emptyList()
) {
    fun imageUnderstandingStatus(mimeType: String): MediaCapabilityStatus =
        understandingProviders.firstNotNullOfOrNull { provider ->
            provider.capability(mimeType).takeIf { it == MediaCapabilityStatus.AVAILABLE }
        } ?: if (understandingProviders.isEmpty()) MediaCapabilityStatus.REQUIRES_CONFIGURATION
        else MediaCapabilityStatus.UNSUPPORTED

    fun videoUnderstandingStatus(): MediaCapabilityStatus =
        imageUnderstandingStatus("video/mp4")

    fun imageGenerationStatus(): MediaCapabilityStatus =
        imageProviders.firstNotNullOfOrNull { it.capability().takeIf { status -> status == MediaCapabilityStatus.AVAILABLE } }
            ?: if (imageProviders.isEmpty()) MediaCapabilityStatus.REQUIRES_CONFIGURATION else MediaCapabilityStatus.UNSUPPORTED

    fun videoGenerationStatus(): MediaCapabilityStatus =
        videoProviders.firstNotNullOfOrNull { it.capability().takeIf { status -> status == MediaCapabilityStatus.AVAILABLE } }
            ?: if (videoProviders.isEmpty()) MediaCapabilityStatus.REQUIRES_CONFIGURATION else MediaCapabilityStatus.UNSUPPORTED

    suspend fun understandImage(asset: MediaAsset): MediaUnderstandingResult {
        require(asset.mimeType.startsWith("image/")) { "El archivo seleccionado no es una imagen." }
        val provider = understandingProviders.firstOrNull {
            it.capability(asset.mimeType) == MediaCapabilityStatus.AVAILABLE
        } ?: throw MediaCapabilityUnavailableException("No hay un motor de visión configurado para imágenes.")
        return provider.understandImage(asset)
    }

    suspend fun understandVideo(asset: MediaAsset, frames: List<MediaFrame>): MediaUnderstandingResult {
        require(asset.mimeType.startsWith("video/")) { "El archivo seleccionado no es un vídeo." }
        require(frames.size <= MAX_VIDEO_FRAMES) { "El análisis supera el límite de fotogramas." }
        val ordered = frames.sortedBy { it.timestampMs }
        require(ordered.all { it.timestampMs >= 0L && (asset.durationMs == null || it.timestampMs < asset.durationMs) }) {
            "La cronología de fotogramas no es válida."
        }
        val provider = understandingProviders.firstOrNull {
            it.capability(asset.mimeType) == MediaCapabilityStatus.AVAILABLE
        } ?: throw MediaCapabilityUnavailableException("No hay un motor de visión configurado para vídeo.")
        return provider.understandVideo(asset, ordered)
    }

    suspend fun generateImage(request: MediaGenerationRequest): GeneratedMedia {
        val provider = imageProviders.firstOrNull { it.capability() == MediaCapabilityStatus.AVAILABLE }
            ?: throw MediaCapabilityUnavailableException("No hay un proveedor de generación de imágenes configurado.")
        return provider.generate(request)
    }

    suspend fun generateVideo(request: MediaGenerationRequest): GeneratedMedia {
        val provider = videoProviders.firstOrNull { it.capability() == MediaCapabilityStatus.AVAILABLE }
            ?: throw MediaCapabilityUnavailableException("No hay un proveedor de generación de vídeo configurado.")
        return provider.generate(request)
    }

    companion object {
        const val MAX_VIDEO_FRAMES = 24
    }
}

class MediaCapabilityUnavailableException(message: String) : IllegalStateException(message)
