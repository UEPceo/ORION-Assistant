package com.orion.voicelab.llm

import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatPromptFormatterTest {
    @Test fun trimsOldestHistoryBeforeTouchingSystemInstructions() {
        val history = listOf(
            ChatMessage(ChatRole.USER, "OLD_MARKER " + "x".repeat(2_000)),
            ChatMessage(ChatRole.ASSISTANT, "OLD_ASSISTANT " + "y".repeat(1_000)),
            ChatMessage(ChatRole.USER, "LATEST_START " + "z".repeat(700) + " LATEST_END")
        )
        val system = "SYSTEM_BEGIN " + "instructions ".repeat(20) + " SYSTEM_END"
        val prompt = ChatPromptFormatter.formatChatMl(system, history, maxContextTokens = 1024)
        assertTrue(prompt.length <= ContextWindowBudgetPolicy.promptChars(1024))
        assertTrue(prompt.contains("SYSTEM_BEGIN"))
        assertTrue(prompt.contains("SYSTEM_END"))
        assertTrue(prompt.contains("LATEST_START"))
        assertTrue(prompt.contains("LATEST_END"))
        assertFalse(prompt.contains("OLD_MARKER"))
    }

    @Test fun refusesToSilentlyTruncateAnOversizedSystemPrompt() {
        val oversized = "system ".repeat(2_000)
        assertTrue(runCatching {
            ChatPromptFormatter.formatChatMl(oversized, emptyList(), maxContextTokens = 512)
        }.isFailure)
    }

    @Test fun refusesToTruncateAnOversizedCurrentUserMessage() {
        val latest = ChatMessage(ChatRole.USER, "CURRENT_REQUEST " + "x".repeat(4_000))
        assertTrue(runCatching {
            ChatPromptFormatter.formatChatMl("system", listOf(latest), maxContextTokens = 512)
        }.isFailure)
    }

    @Test fun preservesLatestUserMessageAndDropsOlderHistoryFirst() {
        val latest = ChatMessage(ChatRole.USER, "CURRENT_REQUEST_INTACT")
        val history = listOf(
            ChatMessage(ChatRole.USER, "OLD_CONTEXT " + "x".repeat(2_000)),
            ChatMessage(ChatRole.ASSISTANT, "OLD_REPLY " + "y".repeat(1_000)),
            latest
        )
        val prompt = ChatPromptFormatter.formatChatMl("system", history, maxContextTokens = 512)
        assertTrue(prompt.contains("CURRENT_REQUEST_INTACT"))
        assertFalse(prompt.contains("OLD_CONTEXT"))
        assertFalse(prompt.contains("OLD_REPLY"))
    }

    @Test fun keepsRecentTurnsInChronologicalOrder() {
        val prompt = ChatPromptFormatter.formatChatMl(
            systemPrompt = "system",
            history = listOf(
                ChatMessage(ChatRole.USER, "first"),
                ChatMessage(ChatRole.ASSISTANT, "second"),
                ChatMessage(ChatRole.USER, "third")
            )
        )
        assertTrue(prompt.indexOf("first") < prompt.indexOf("second"))
        assertTrue(prompt.indexOf("second") < prompt.indexOf("third"))
    }
}
