package com.orion.voicelab.creative

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MediaGenerationConsentExecutorTest {
 @Test fun noApprovalCannotInvokeRemoteProvider()=runBlocking {
  val gate=MediaGenerationConsentGate(nowEpochMs={100L})
  val pending=gate.prepare(MediaGenerationKind.IMAGE,"remote","server","make an image")
  val executor=MediaGenerationConsentExecutor(OrionMediaRuntime(),gate,{true})
  assertThrows(IllegalStateException::class.java) { runBlocking { executor.execute(pending.id,false,MediaGenerationRequest(pending.prompt)) } }
 }
 @Test fun networkMissingStopsAfterApprovalAndApprovalCannotBeReused()=runBlocking {
  val gate=MediaGenerationConsentGate(nowEpochMs={100L})
  val pending=gate.prepare(MediaGenerationKind.VIDEO,"remote","server","make video")
  val executor=MediaGenerationConsentExecutor(OrionMediaRuntime(),gate,{false})
  assertThrows(MediaCapabilityUnavailableException::class.java) { runBlocking { executor.execute(pending.id,true,MediaGenerationRequest(pending.prompt)) } }
  assertThrows(IllegalStateException::class.java) { runBlocking { executor.execute(pending.id,true,MediaGenerationRequest(pending.prompt)) } }
 }
}
