package com.orion.voicelab.creative

/** Executes remote generation only after consuming an affirmative, short-lived approval. */
class MediaGenerationConsentExecutor(
    private val runtime:OrionMediaRuntime,
    private val gate:MediaGenerationConsentGate,
    private val networkAvailable:()->Boolean
) {
    suspend fun execute(requestId:String,confirmed:Boolean,request:MediaGenerationRequest):GeneratedMedia {
        val approval=gate.confirm(requestId,confirmed)
            ?:throw IllegalStateException("La autorización no existe, ha sido rechazada o ha caducado. Solicita otra.")
        require(request.prompt.take(12000)==approval.prompt) { "El prompt cambió desde la autorización; solicita confirmación de nuevo." }
        val connected = networkAvailable()
        if (!connected) throw MediaCapabilityUnavailableException("No hay conexión de red para contactar con el servidor multimedia.")
        val providerVerified = when (approval.kind) {
            MediaGenerationKind.IMAGE -> runtime.verifyImageProvider(approval.providerId)
            MediaGenerationKind.VIDEO -> runtime.verifyVideoProvider(approval.providerId)
        }
        if (!providerVerified) {
            throw IllegalStateException("El proveedor multimedia no ha superado la comprobación real de conexión y workflow. Revisa el endpoint y el servidor.")
        }
        val policy=MediaRoutingPolicy(allowRemoteProcessing=true,networkAvailable=true)
        return when(approval.kind) {
            MediaGenerationKind.IMAGE -> runtime.generateImage(request,policy,approval.providerId)
            MediaGenerationKind.VIDEO -> runtime.generateVideo(request,policy,approval.providerId)
        }
    }
}
