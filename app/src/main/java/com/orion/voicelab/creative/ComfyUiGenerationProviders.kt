package com.orion.voicelab.creative

import android.net.Uri

/**
 * Executable ComfyUI adapters. They are usable only when an endpoint and validated
 * workflow template are supplied; the app ships no model weights or endpoint secrets.
 */
class ComfyUiImageGenerationProvider(
    private val client:ComfyUiClient,
    private val template:ComfyUiWorkflowTemplate,
    private val modelLabel:String="FLUX.1-schnell"
):ImageGenerationProvider {
    override val providerId="comfyui-image"
    override fun capability()=MediaCapabilityStatus.AVAILABLE
    override suspend fun generate(request:MediaGenerationRequest):GeneratedMedia {
        require(request.referenceAssets.isEmpty()) { "La generación con imágenes de referencia requiere el flujo de consentimiento de subida, aún no conectado." }
        val workflow=template.build(request)
        val output=client.runWorkflow(workflow)
        return GeneratedMedia(MediaAsset(Uri.fromFile(output.localFile),output.mimeType,output.localFile.name,sizeBytes=output.localFile.length()),providerId,modelLabel,System.currentTimeMillis())
    }
}

class ComfyUiVideoGenerationProvider(
    private val client:ComfyUiClient,
    private val template:ComfyUiWorkflowTemplate,
    private val modelLabel:String="Wan2.1 T2V"
):VideoGenerationProvider {
    override val providerId="comfyui-video"
    override fun capability()=MediaCapabilityStatus.AVAILABLE
    override suspend fun generate(request:MediaGenerationRequest):GeneratedMedia {
        require(request.referenceAssets.isEmpty()) { "I2V necesita conectar la autorización de subida de referencias antes de habilitarse." }
        val workflow=template.build(request)
        val output=client.runWorkflow(workflow)
        return GeneratedMedia(MediaAsset(Uri.fromFile(output.localFile),output.mimeType,output.localFile.name,durationMs=request.durationSeconds?.times(1000L),sizeBytes=output.localFile.length()),providerId,modelLabel,System.currentTimeMillis())
    }
}
