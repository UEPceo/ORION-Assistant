package com.orion.voicelab.llm

import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteInferencePayloadTest {
    @Test fun remoteEndpointPolicyRejectsCredentialInjectionAndPublicHttp() {
        val client=RemoteInferenceClient("https://example.com/v1","model","secret")
        assertTrue(client.isSafeEndpoint("https://example.com/v1"))
        assertTrue(client.isSafeEndpoint("http://127.0.0.1:8080/v1"))
        assertTrue(client.isSafeEndpoint("http://192.168.1.12:8080"))
        assertTrue(client.isSafeEndpoint("http://10.0.0.4:8000"))
        assertFalse(client.isSafeEndpoint("http://example.com"))
        assertFalse(client.isSafeEndpoint("https://user:pass@example.com"))
        assertFalse(client.isSafeEndpoint("https://example.com/v1?token=leak"))
        assertFalse(client.isSafeEndpoint("https://example.com/v1#fragment"))
    }

    @Test fun includesSystemPromptAndOnlyMostRecentTwelveMessages() {
        val history = (0 until 15).map {
            ChatMessage(ChatRole.USER, "message-$it")
        }
        val payload = RemoteInferencePayload.build("local-server-model", "system-context", history)
        val messages = payload.getJSONArray("messages")

        assertEquals(13, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("system-context", messages.getJSONObject(0).getString("content"))
        assertEquals("message-3", messages.getJSONObject(1).getString("content"))
        assertEquals("message-14", messages.getJSONObject(12).getString("content"))
    }

    @Test fun truncatesSystemAndIndividualMessagesToDocumentedLimits() {
        val payload = RemoteInferencePayload.build(
            "model",
            "s".repeat(RemoteInferencePayload.MAX_SYSTEM_PROMPT_CHARS + 10),
            listOf(ChatMessage(ChatRole.ASSISTANT, "a".repeat(RemoteInferencePayload.MAX_MESSAGE_CHARS + 10)))
        )
        val messages = payload.getJSONArray("messages")

        assertEquals(RemoteInferencePayload.MAX_SYSTEM_PROMPT_CHARS,
            messages.getJSONObject(0).getString("content").length)
        assertEquals(RemoteInferencePayload.MAX_MESSAGE_CHARS,
            messages.getJSONObject(1).getString("content").length)
    }

    @Test fun excludesSystemRoleFromConversationHistory() {
        val payload=RemoteInferencePayload.build("model","trusted system prompt",listOf(
            ChatMessage(ChatRole.SYSTEM,"historic hidden instructions"),
            ChatMessage(ChatRole.USER,"user text")
        ))
        val messages=payload.getJSONArray("messages")
        assertEquals(2,messages.length())
        assertEquals("system",messages.getJSONObject(0).getString("role"))
        assertEquals("trusted system prompt",messages.getJSONObject(0).getString("content"))
        assertEquals("user",messages.getJSONObject(1).getString("role"))
        assertEquals("user text",messages.getJSONObject(1).getString("content"))
    }

    @Test fun mapsConversationRolesToOpenAiRoles() {
        val payload = RemoteInferencePayload.build(
            "model", "system",
            listOf(ChatMessage(ChatRole.USER, "u"), ChatMessage(ChatRole.ASSISTANT, "a"))
        )
        val messages = payload.getJSONArray("messages")

        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertTrue(JSONObject(payload.toString()).has("max_tokens"))
    }
}
