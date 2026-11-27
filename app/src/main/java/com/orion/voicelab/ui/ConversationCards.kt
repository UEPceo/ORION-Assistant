package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.orion.voicelab.data.ChatRole
import com.orion.voicelab.data.ConversationTurn
import java.util.Locale

@Composable
internal fun OrionConversationTurnCard(
    turn: ConversationTurn,
    assistantName: String = "ORION"
) {
    val isUser = turn.role == ChatRole.USER
    Surface(
        modifier = Modifier.fillMaxWidth().testTag("call_turn_${turn.id}"),
        shape = MaterialTheme.shapes.medium,
        color = if (isUser) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (isUser) MaterialTheme.colorScheme.outline
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (isUser) "TÚ" else assistantName.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                color = if (isUser) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
            )
            Text(text = turn.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (isUser && turn.audioMs > 0) {
                Text(
                    text = String.format(Locale.getDefault(), "audio %.1f s · ASR %d ms", turn.audioMs / 1000.0, turn.decodeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (!isUser && turn.latency != null) {
                val lat = turn.latency
                Text(
                    text = "voz→respuesta ${lat.endToSpeechStartMs} ms · LLM ${lat.llmTotalMs} ms · TTS ${lat.ttsSynthMs} ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

@Composable
internal fun OrionStreamingReplyCard(text: String, assistantName: String = "ORION") {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${assistantName.uppercase(Locale.getDefault())} · RESPONDIENDO",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}
