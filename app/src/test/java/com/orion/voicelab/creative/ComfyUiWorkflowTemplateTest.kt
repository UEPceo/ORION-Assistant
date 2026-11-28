package com.orion.voicelab.creative

import org.junit.Assert.*
import org.junit.Test

class ComfyUiWorkflowTemplateTest {
 @Test fun endpointAllowsHttpsAndLocalHttpOnly() {
  assertEquals("https://example.com:8188",ComfyUiEndpointConfig("https://example.com:8188/").normalizedBaseUrl())
  assertEquals("http://192.168.1.10:8188",ComfyUiEndpointConfig("http://192.168.1.10:8188").normalizedBaseUrl())
  assertEquals("http://localhost:8188",ComfyUiEndpointConfig("http://localhost:8188").normalizedBaseUrl())
 }
 @Test fun rejectsPlainExternalHttpAndEmbeddedCredentials() {
  assertThrows(IllegalArgumentException::class.java) { ComfyUiEndpointConfig("http://example.com").normalizedBaseUrl() }
  assertThrows(IllegalArgumentException::class.java) { ComfyUiEndpointConfig("https://user:pass@example.com").normalizedBaseUrl() }
 }
 @Test fun replacesPositiveAndNegativePromptFieldsWithoutChangingOtherInputs() {
  val workflow="""{"1":{"class_type":"CLIPTextEncode","inputs":{"text":"old","clip":["2",0]}},"3":{"class_type":"CLIPTextEncode","inputs":{"text":"negative"}}}"""
  val template=ComfyUiWorkflowTemplate(workflow,"1","text","3","text")
  val updated=template.build(MediaGenerationRequest("new prompt","avoid blur"))
  assertEquals("new prompt",updated.getJSONObject("1").getJSONObject("inputs").getString("text"))
  assertEquals("avoid blur",updated.getJSONObject("3").getJSONObject("inputs").getString("text"))
  assertEquals("2",updated.getJSONObject("1").getJSONObject("inputs").getJSONArray("clip").getString(0))
 }
 @Test fun rejectsWorkflowWhenPromptNodeOrInputMissing() {
  val template=ComfyUiWorkflowTemplate("""{"1":{"inputs":{"other":"x"}}}""","9")
  assertThrows(IllegalArgumentException::class.java) { template.build(MediaGenerationRequest("hello")) }
 }
}
