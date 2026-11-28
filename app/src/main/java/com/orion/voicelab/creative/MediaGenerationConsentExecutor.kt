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
        val policy=MediaRoutingPolicy(allowRemoteProcessing=true,networkAvailable=networkAvailable())
        return when(approval.kind) {
            MediaGenerationKind.IMAGE -> runtime.generateImage(request,policy)
            MediaGenerationKind.VIDEO -> runtime.generateVideo(request,policy)
        }
    }
}
