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
 @Test fun appliesAspectRatioAndVideoDurationWhenMappingsConfigured() {
  val workflow="""{"1":{"inputs":{"text":"prompt"}},"2":{"inputs":{"width":512,"height":512}},"3":{"inputs":{"length":81}}}"""
  val template=ComfyUiWorkflowTemplate(workflow,"1",dimensionNodeId="2",durationNodeId="3",durationInput="length",framesPerSecond=16)
  val result=template.build(MediaGenerationRequest("motion","", "9:16",5))
  assertEquals(432,result.getJSONObject("2").getJSONObject("inputs").getInt("width"))
  assertEquals(768,result.getJSONObject("2").getJSONObject("inputs").getInt("height"))
  assertEquals(80,result.getJSONObject("3").getJSONObject("inputs").getInt("length"))
 }
 @Test fun ignoresAspectAndDurationMappingsWhenNotConfigured() {
  val workflow="""{"1":{"inputs":{"text":"prompt"}},"2":{"inputs":{"width":512,"height":512}}}"""
  val result=ComfyUiWorkflowTemplate(workflow,"1").build(MediaGenerationRequest("prompt",null,"16:9",5))
  assertEquals(512,result.getJSONObject("2").getJSONObject("inputs").getInt("width"))
  assertEquals(512,result.getJSONObject("2").getJSONObject("inputs").getInt("height"))
 }

}
