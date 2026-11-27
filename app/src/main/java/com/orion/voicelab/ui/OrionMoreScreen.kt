package com.orion.voicelab.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun OrionMoreScreen(
    onOpenConversations: () -> Unit,
    onOpenMemory: () -> Unit,
    onOpenConnectors: () -> Unit,
    onOpenLab: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Más herramientas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Historial, memoria, conexiones y diagnóstico.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        MoreDestinationCard("Conversaciones", "Consulta y recupera tus conversaciones anteriores.", Icons.Outlined.Forum, onOpenConversations, "tab_conversations")
        MoreDestinationCard("Memoria", "Gestiona los recuerdos y la identidad de ORION.", Icons.Outlined.Memory, onOpenMemory, "tab_memory")
        MoreDestinationCard("Conectores", "Administra las aplicaciones y servicios autorizados.", Icons.Outlined.Lock, onOpenConnectors, "tab_connectors")
        MoreDestinationCard("Laboratorio y diagnóstico", "Estado de modelos, rendimiento e informes técnicos.", Icons.Outlined.Science, onOpenLab, "tab_lab")
    }
}

@Composable
private fun MoreDestinationCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    testTag: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(12.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
