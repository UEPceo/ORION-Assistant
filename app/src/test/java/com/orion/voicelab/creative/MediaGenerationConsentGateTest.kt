package com.orion.voicelab.creative

import org.junit.Assert.*
import org.junit.Test

class MediaGenerationConsentGateTest {
 @Test fun requiresAffirmativeAndConsumesPendingRequest() {
  val gate=MediaGenerationConsentGate(nowEpochMs={1000L})
  val pending=gate.prepare(MediaGenerationKind.IMAGE,"comfyui-image","GPU local","Prompt")
  assertNull(gate.confirm(pending.id,false))
  assertNull(gate.confirm(pending.id,true))
 }
 @Test fun validConsentIsSingleUseAndExpires() {
  var now=100L
  val gate=MediaGenerationConsentGate(nowEpochMs={now},ttlMs=50)
  val pending=gate.prepare(MediaGenerationKind.VIDEO,"comfyui-video","https://host","A video prompt")
  assertEquals(pending,gate.confirm(pending.id,true))
  assertNull(gate.confirm(pending.id,true))
  val stale=gate.prepare(MediaGenerationKind.IMAGE,"engine","destination","prompt")
  now=151L
  assertNull(gate.confirm(stale.id,true))
 }
 @Test fun requestPromptIsBoundAndTruncated() {
  val gate=MediaGenerationConsentGate(nowEpochMs={0L})
  val pending=gate.prepare(MediaGenerationKind.IMAGE,"engine","destination","x".repeat(15000))
  assertEquals(12000,pending.prompt.length)
  assertEquals("engine",pending.providerId)
  assertEquals("destination",pending.destinationLabel)
 }
}
