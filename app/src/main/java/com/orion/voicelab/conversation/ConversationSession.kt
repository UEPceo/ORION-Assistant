package com.orion.voicelab.conversation

import com.orion.voicelab.data.ChatMessage
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.data.ConversationTurn
import com.orion.voicelab.data.TurnLatencyMetrics

class SpeechSentenceChunker(
    private val minClauseChars: Int = 24,
    private val maxClauseChars: Int = 180
) {
    private val buffer = StringBuilder()

    fun appendToken(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        buffer.append(token)
        val emitted = mutableListOf<String>()

        while (true) {
            val current = buffer.toString()
            val breakIdx = findBreakIndex(current)
            if (breakIdx < 0) break

            val chunk = current.substring(0, breakIdx + 1).trim()
            val rest = current.substring(breakIdx + 1).trimStart()
            buffer.clear()
            buffer.append(rest)
            if (chunk.any { it.isLetterOrDigit() }) {
                emitted.add(chunk)
            }
        }
        return emitted
    }

    fun flush(): String? {
        val remaining = buffer.toString().trim()
        buffer.clear()
        return if (remaining.any { it.isLetterOrDigit() }) remaining else null
    }

    fun reset() {
        buffer.clear()
    }

    private fun findBreakIndex(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            val isHardBreak = ch == '.' || ch == '!' || ch == '?' || ch == '\n'
            val isSoftBreak = (ch == ',' || ch == ';' || ch == ':') && i >= minClauseChars
            if ((isHardBreak && i >= 6) || isSoftBreak) {
                return i
            }
            if (i >= maxClauseChars && ch == ' ') {
                return i
            }
        }
        return -1
    }
}

class ConversationSession(
    private val maxHistoryMessages: Int = 16
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "Eres ORION, un asistente personal autónomo por voz. " +
                "Respondes en español natural, cercano, claro y directo, como en una llamada telefónica real. " +
                "Da respuestas breves (máximo 2 o 3 frases claras) sin listas con asteriscos ni formato Markdown, " +
                "pensadas para ser escuchadas en voz alta."
    }

    private val lock = Any()
    private val _history = mutableListOf<ChatMessage>()
    private val _turns = mutableListOf<ConversationTurn>()
    private var nextTurnId = 1L
    var systemPrompt: String = DEFAULT_SYSTEM_PROMPT

    fun addUserMessage(text: String, audioMs: Long, decodeMs: Long): ConversationTurn = synchronized(lock) {
        _history.add(ChatMessage(role = ChatRole.USER, content = text))
        trimHistoryLocked()
        val turn = ConversationTurn(
            id = nextTurnId++,
            role = ChatRole.USER,
            text = text,
            audioMs = audioMs,
            decodeMs = decodeMs
        )
        _turns.add(turn)
        turn
    }

    fun addAssistantMessage(text: String, latency: TurnLatencyMetrics): ConversationTurn = synchronized(lock) {
        _history.add(ChatMessage(role = ChatRole.ASSISTANT, content = text))
        trimHistoryLocked()
        val turn = ConversationTurn(
            id = nextTurnId++,
            role = ChatRole.ASSISTANT,
            text = text,
            latency = latency
        )
        _turns.add(turn)
        turn
    }

    fun historySnapshot(): List<ChatMessage> = synchronized(lock) {
        _history.toList()
    }

    fun turnsSnapshot(): List<ConversationTurn> = synchronized(lock) {
        _turns.toList()
    }

    fun reset() = synchronized(lock) {
        _history.clear()
        _turns.clear()
        nextTurnId = 1L
    }

    private fun trimHistoryLocked() {
        while (_history.size > maxHistoryMessages) {
            _history.removeAt(0)
        }
    }
}
