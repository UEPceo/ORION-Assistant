package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.orion.voicelab.R
import com.orion.voicelab.data.DeviceMetrics
import com.orion.voicelab.data.VoiceLabUiState

@Composable
internal fun OrionTelemetryPanel(state: VoiceLabUiState) {
    val snap = state.metricsSnapshot
    val metricsText = remember(snap, state.modelSizeMb, state.modelLoadMs, state.asrStats) {
        buildString {
            if (snap != null) append(DeviceMetrics.describe(snap))
            else append("Leyendo métricas del sistema…")
            append("\nModelo en disco: ${state.modelSizeMb} MB")
            if (state.modelLoadMs >= 0) append(" · carga modelo+VAD: ${state.modelLoadMs} ms")
            append("\n")
            append(state.asrStats.summaryLine())
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("telemetry_panel"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = stringResource(R.string.section_telemetry),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = metricsText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}
