package com.orion.voicelab.creative

data class MediaRoutingPolicy(val allowRemoteProcessing:Boolean=false,val networkAvailable:Boolean=false)

/** Local-first. Remote inference requires per-operation consent and a live network. */
object MediaProviderRouter {
 fun <T> select(providers:List<T>,kind:(T)->MediaProviderKind,capability:(T)->MediaCapabilityStatus,policy:MediaRoutingPolicy):T? {
  providers.firstOrNull { kind(it)==MediaProviderKind.LOCAL && capability(it)==MediaCapabilityStatus.AVAILABLE }?.let{return it}
  if(!policy.allowRemoteProcessing||!policy.networkAvailable)return null
  return providers.firstOrNull { kind(it)==MediaProviderKind.REMOTE && capability(it)==MediaCapabilityStatus.AVAILABLE }
 }
 fun <T> status(providers:List<T>,kind:(T)->MediaProviderKind,capability:(T)->MediaCapabilityStatus,policy:MediaRoutingPolicy):MediaCapabilityStatus {
  if(providers.any { kind(it)==MediaProviderKind.LOCAL && capability(it)==MediaCapabilityStatus.AVAILABLE })return MediaCapabilityStatus.AVAILABLE
  val remote=providers.filter { kind(it)==MediaProviderKind.REMOTE }
  if(remote.any { capability(it)==MediaCapabilityStatus.AVAILABLE })return when {
   !policy.allowRemoteProcessing -> MediaCapabilityStatus.REQUIRES_USER_CONSENT
   !policy.networkAvailable -> MediaCapabilityStatus.REQUIRES_NETWORK
   else -> MediaCapabilityStatus.AVAILABLE
  }
  if(providers.any { kind(it)==MediaProviderKind.LOCAL && capability(it)==MediaCapabilityStatus.REQUIRES_MODEL })return MediaCapabilityStatus.REQUIRES_MODEL
  if(providers.isEmpty()||providers.all { capability(it)==MediaCapabilityStatus.REQUIRES_CONFIGURATION })return MediaCapabilityStatus.REQUIRES_CONFIGURATION
  return providers.firstNotNullOfOrNull { capability(it).takeIf { s -> s!=MediaCapabilityStatus.UNSUPPORTED } } ?: MediaCapabilityStatus.UNSUPPORTED
 }
}
