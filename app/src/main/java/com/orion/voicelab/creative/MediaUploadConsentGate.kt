package com.orion.voicelab.creative

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MediaUploadOperation { IMAGE_ANALYSIS, VIDEO_ANALYSIS, IMAGE_GENERATION_REFERENCE, VIDEO_GENERATION_REFERENCE }

data class MediaUploadConsentRequest(
 val assetSha256:String,
 val providerId:String,
 val operation:MediaUploadOperation,
 val destinationLabel:String,
 val purpose:String,
 val sizeBytes:Long
)

data class MediaUploadGrant internal constructor(
 val id:String,
 val assetSha256:String,
 val providerId:String,
 val operation:MediaUploadOperation,
 val expiresAtEpochMs:Long
)

data class UploadedMediaReference(val providerId:String,val remoteId:String,val uri:String?=null,val expiresAtEpochMs:Long?=null)

interface MediaUploadGateway {
 val providerId:String
 suspend fun upload(asset:MediaAsset,sha256:String,operation:MediaUploadOperation):UploadedMediaReference
}

/**
 * A grant is minted only after the UI has obtained an affirmative user action.
 * It is single-use, provider/operation/hash-bound, short-lived and memory-only.
 */
class MediaUploadConsentGate(
 private val nowEpochMs:()->Long=System::currentTimeMillis,
 private val ttlMs:Long=5*60*1000L
) {
 private val grants=ConcurrentHashMap<String,MediaUploadGrant>()

 fun authorizeAfterUserConfirmation(request:MediaUploadConsentRequest,confirmed:Boolean):MediaUploadGrant {
  require(confirmed){"La subida requiere confirmación expresa del usuario."}
  require(request.assetSha256.matches(Regex("(?i)^[a-f0-9]{64}$"))){"Se necesita SHA-256 válido del archivo."}
  require(request.providerId.isNotBlank()&&request.destinationLabel.isNotBlank()&&request.purpose.isNotBlank())
  require(request.sizeBytes>0L)
  val grant=MediaUploadGrant(UUID.randomUUID().toString(),request.assetSha256.lowercase(),request.providerId,request.operation,nowEpochMs()+ttlMs)
  grants[grant.id]=grant
  return grant
 }

 fun consume(grant:MediaUploadGrant,sha256:String,providerId:String,operation:MediaUploadOperation):Boolean {
  val stored=grants.remove(grant.id) ?: return false
  return stored==grant &&
   stored.expiresAtEpochMs>=nowEpochMs() &&
   stored.assetSha256.equals(sha256,true) &&
   stored.providerId==providerId &&
   stored.operation==operation
 }

 fun revoke(grant:MediaUploadGrant) { grants.remove(grant.id) }
}

class MediaUploadCoordinator(
 private val gate:MediaUploadConsentGate,
 private val gateways:List<MediaUploadGateway>
) {
 suspend fun uploadAfterAuthorization(
  asset:MediaAsset,
  sha256:String,
  operation:MediaUploadOperation,
  grant:MediaUploadGrant
 ):UploadedMediaReference {
  val gateway=gateways.firstOrNull{it.providerId==grant.providerId}
   ?:throw IllegalStateException("El destino de subida no está configurado.")
  check(gate.consume(grant,sha256,gateway.providerId,operation)) {
   "La autorización no existe, ha caducado o no corresponde a este archivo, destino u operación."
  }
  return gateway.upload(asset,sha256.lowercase(),operation)
 }
}
