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
        aggregateStatus(understandingProviders.map { it.capability(mimeType) })

    private fun aggregateStatus(statuses: List<MediaCapabilityStatus>): MediaCapabilityStatus = when {
        statuses.any { it == MediaCapabilityStatus.AVAILABLE } -> MediaCapabilityStatus.AVAILABLE
        statuses.any { it == MediaCapabilityStatus.REQUIRES_MODEL } -> MediaCapabilityStatus.REQUIRES_MODEL
        statuses.any { it == MediaCapabilityStatus.REQUIRES_CONFIGURATION } -> MediaCapabilityStatus.REQUIRES_CONFIGURATION
        statuses.isEmpty() -> MediaCapabilityStatus.REQUIRES_CONFIGURATION
        else -> MediaCapabilityStatus.UNSUPPORTED
    }

    fun videoUnderstandingStatus(): MediaCapabilityStatus =
        imageUnderstandingStatus("video/mp4")

    fun imageGenerationStatus(): MediaCapabilityStatus = aggregateStatus(imageProviders.map { it.capability() })

    fun videoGenerationStatus(): MediaCapabilityStatus = aggregateStatus(videoProviders.map { it.capability() })

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
