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
    override fun capability()=if(client.isConnectionVerified() && template.isValid()) MediaCapabilityStatus.AVAILABLE else MediaCapabilityStatus.REQUIRES_PROVIDER_VALIDATION
    override suspend fun verifyAvailability():Boolean = template.isValid() && client.verifyConnection()
    override suspend fun generate(request:MediaGenerationRequest):GeneratedMedia {
        val uploaded=request.referenceAssets.map { client.uploadReference(it) }
        val workflow=template.build(request,uploaded)
        val output=client.runWorkflow(workflow,timeoutMs=300000L)
        return GeneratedMedia(MediaAsset(Uri.fromFile(output.localFile),output.mimeType,output.localFile.name,sizeBytes=output.localFile.length()),providerId,modelLabel,System.currentTimeMillis())
    }
}

class ComfyUiVideoGenerationProvider(
    private val client:ComfyUiClient,
    private val template:ComfyUiWorkflowTemplate,
    private val modelLabel:String="Wan2.1 T2V"
):VideoGenerationProvider {
    override val providerId="comfyui-video"
    override fun capability()=if(client.isConnectionVerified() && template.isValid()) MediaCapabilityStatus.AVAILABLE else MediaCapabilityStatus.REQUIRES_PROVIDER_VALIDATION
    override suspend fun verifyAvailability():Boolean = template.isValid() && client.verifyConnection()
    override suspend fun generate(request:MediaGenerationRequest):GeneratedMedia {
        val uploaded=request.referenceAssets.map { client.uploadReference(it) }
        val workflow=template.build(request,uploaded)
        val output=client.runWorkflow(workflow,timeoutMs=900000L)
        return GeneratedMedia(MediaAsset(Uri.fromFile(output.localFile),output.mimeType,output.localFile.name,durationMs=request.durationSeconds?.times(1000L),sizeBytes=output.localFile.length()),providerId,modelLabel,System.currentTimeMillis())
    }
}
