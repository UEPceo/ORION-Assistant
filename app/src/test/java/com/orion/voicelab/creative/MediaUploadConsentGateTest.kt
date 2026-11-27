package com.orion.voicelab.creative

import org.junit.Assert.*
import org.junit.Test

class MediaUploadConsentGateTest {
 private val hash="a".repeat(64)
 private class Clock(var now:Long=1000L)
 @Test fun requiresAffirmativeConfirmation(){
  val gate=MediaUploadConsentGate()
  try { gate.authorizeAfterUserConfirmation(MediaUploadConsentRequest(hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS,"Servidor ORION","Analizar imagen",100),false); fail("must reject") }
  catch(_:IllegalArgumentException) { }
 }
 @Test fun grantIsBoundToHashProviderAndOperation(){
  val clock=Clock(); val gate=MediaUploadConsentGate({clock.now})
  val req=MediaUploadConsentRequest(hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS,"Servidor ORION","Analizar imagen",100)
  val grant=gate.authorizeAfterUserConfirmation(req,true)
  assertFalse(gate.consume(grant,"b".repeat(64),"provider",MediaUploadOperation.IMAGE_ANALYSIS))
  assertFalse(gate.consume(grant,hash,"other",MediaUploadOperation.IMAGE_ANALYSIS))
 }
 @Test fun grantCanOnlyBeConsumedOnce(){
  val gate=MediaUploadConsentGate({1000L})
  val grant=gate.authorizeAfterUserConfirmation(MediaUploadConsentRequest(hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS,"Servidor ORION","Analizar imagen",100),true)
  assertTrue(gate.consume(grant,hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS))
  assertFalse(gate.consume(grant,hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS))
 }
 @Test fun grantExpires(){
  val clock=Clock(); val gate=MediaUploadConsentGate({clock.now},100L)
  val grant=gate.authorizeAfterUserConfirmation(MediaUploadConsentRequest(hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS,"Servidor ORION","Analizar imagen",100),true)
  clock.now=1200L
  assertFalse(gate.consume(grant,hash,"provider",MediaUploadOperation.IMAGE_ANALYSIS))
 }
}
