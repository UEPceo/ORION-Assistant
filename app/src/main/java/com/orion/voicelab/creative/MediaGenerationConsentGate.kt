package com.orion.voicelab.creative

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class MediaGenerationKind { IMAGE, VIDEO }

data class MediaGenerationApprovalRequest(
    val id:String,
    val kind:MediaGenerationKind,
    val providerId:String,
    val destinationLabel:String,
    val prompt:String,
    val expiresAtEpochMs:Long
)

/** Explicit, ephemeral, one-shot consent gate for sending a creative prompt to a remote engine. */
class MediaGenerationConsentGate(
    private val nowEpochMs:()->Long=System::currentTimeMillis,
    private val ttlMs:Long=5*60*1000L
) {
    private val pending=ConcurrentHashMap<String,MediaGenerationApprovalRequest>()
    fun prepare(kind:MediaGenerationKind,providerId:String,destinationLabel:String,prompt:String):MediaGenerationApprovalRequest {
        require(providerId.isNotBlank()&&destinationLabel.isNotBlank()&&prompt.isNotBlank())
        val request=MediaGenerationApprovalRequest(UUID.randomUUID().toString(),kind,providerId,destinationLabel,prompt.take(12000),nowEpochMs()+ttlMs)
        pending[request.id]=request
        return request
    }
    fun confirm(requestId:String,affirmative:Boolean):MediaGenerationApprovalRequest? {
        val request=pending.remove(requestId)?:return null
        if(!affirmative||request.expiresAtEpochMs<nowEpochMs()) return null
        return request
    }
    fun cancel(requestId:String) { pending.remove(requestId) }
    fun clear() { pending.clear() }
}
