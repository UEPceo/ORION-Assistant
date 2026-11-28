package com.orion.voicelab.creative

/** Capability-aware media orchestration. Remote execution is disabled by default. */
class OrionMediaRuntime(
 private val understandingProviders:List<MediaUnderstandingProvider> = emptyList(),
 private val imageProviders:List<ImageGenerationProvider> = emptyList(),
 private val videoProviders:List<VideoGenerationProvider> = emptyList()
) {
 fun imageUnderstandingStatus(mimeType:String,policy:MediaRoutingPolicy=MediaRoutingPolicy())=MediaProviderRouter.status(understandingProviders,{it.kind},{it.capability(mimeType)},policy)
 fun videoUnderstandingStatus(policy:MediaRoutingPolicy=MediaRoutingPolicy())=imageUnderstandingStatus("video/mp4",policy)
 fun imageGenerationStatus(policy:MediaRoutingPolicy=MediaRoutingPolicy())=MediaProviderRouter.status(imageProviders,{it.kind},{it.capability()},policy)
 fun videoGenerationStatus(policy:MediaRoutingPolicy=MediaRoutingPolicy())=MediaProviderRouter.status(videoProviders,{it.kind},{it.capability()},policy)
 suspend fun understandImage(asset:MediaAsset,policy:MediaRoutingPolicy=MediaRoutingPolicy()):MediaUnderstandingResult {
  require(asset.mimeType.startsWith("image/")){"El archivo seleccionado no es una imagen."}
  val p=MediaProviderRouter.select(understandingProviders,{it.kind},{it.capability(asset.mimeType)},policy)
   ?:throw MediaCapabilityUnavailableException(imageUnderstandingStatus(asset.mimeType,policy).message())
  return p.understandImage(asset)
 }
 suspend fun understandVideo(asset:MediaAsset,frames:List<MediaFrame>,policy:MediaRoutingPolicy=MediaRoutingPolicy()):MediaUnderstandingResult {
  require(asset.mimeType.startsWith("video/")){"El archivo seleccionado no es un vídeo."}
  require(frames.size<=MAX_VIDEO_FRAMES){"El análisis supera el límite de fotogramas."}
  val ordered=frames.sortedBy{it.timestampMs}
  require(ordered.all{it.timestampMs>=0L&&(asset.durationMs==null||it.timestampMs<asset.durationMs)}){"La cronología de fotogramas no es válida."}
  val p=MediaProviderRouter.select(understandingProviders,{it.kind},{it.capability(asset.mimeType)},policy)
   ?:throw MediaCapabilityUnavailableException(videoUnderstandingStatus(policy).message())
  return p.understandVideo(asset,ordered)
 }
 suspend fun generateImage(request:MediaGenerationRequest,policy:MediaRoutingPolicy=MediaRoutingPolicy()):GeneratedMedia {
  val p=MediaProviderRouter.select(imageProviders,{it.kind},{it.capability()},policy)
   ?:throw MediaCapabilityUnavailableException(MediaProviderRouter.status(selectedProviders,{it.kind},{it.capability()},policy).message())
  return p.generate(request)
 }
 suspend fun generateVideo(request:MediaGenerationRequest,policy:MediaRoutingPolicy=MediaRoutingPolicy()):GeneratedMedia {
  val p=MediaProviderRouter.select(videoProviders,{it.kind},{it.capability()},policy)
   ?:throw MediaCapabilityUnavailableException(MediaProviderRouter.status(selectedProviders,{it.kind},{it.capability()},policy).message())
  return p.generate(request)
 }
 companion object { const val MAX_VIDEO_FRAMES=24 }
}
private fun MediaCapabilityStatus.message()=when(this) {
 MediaCapabilityStatus.REQUIRES_USER_CONSENT -> "El proveedor disponible requiere autorización expresa para enviar el contenido a la nube."
 MediaCapabilityStatus.REQUIRES_NETWORK -> "El proveedor remoto está configurado, pero no hay conexión de red."
 MediaCapabilityStatus.REQUIRES_MODEL -> "Falta descargar o preparar el modelo local."
 MediaCapabilityStatus.REQUIRES_CONFIGURATION -> "No hay ningún proveedor multimedia configurado."
 MediaCapabilityStatus.UNSUPPORTED -> "Ningún proveedor admite este tipo de contenido."
 MediaCapabilityStatus.AVAILABLE -> "La capacidad multimedia está disponible."
}
class MediaCapabilityUnavailableException(message:String):IllegalStateException(message)
