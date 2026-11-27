package com.orion.voicelab.brain
import org.junit.Assert.*
import org.junit.Test
class CapabilityBaselineTest {
 @Test fun currentBaselineDoesNotClaimEightOutOfTenYet() {
  val result=CapabilityBaseline.current()
  assertFalse(result.meetsTarget)
  assertEquals(7,result.areas.size)
  assertTrue(result.minimumScore<8.0)
  assertTrue(result.areas.all { it.criterionCount>0 })
 }
}
