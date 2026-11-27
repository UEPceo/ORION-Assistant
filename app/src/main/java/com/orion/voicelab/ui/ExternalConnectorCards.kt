package com.orion.voicelab.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun GoogleMapsConnectorCard() {
    val context = LocalContext.current
    var destination by rememberSaveable { mutableStateOf("") }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Google Maps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Busca lugares o abre navegación. La ubicación solo se comparte con Maps al iniciar una ruta; ORION no realiza seguimiento en segundo plano.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(destination, { destination = it.take(240) }, modifier = Modifier.fillMaxWidth(), label = { Text("Lugar o dirección") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (destination.isBlank()) Toast.makeText(context, "Escribe un lugar o dirección.", Toast.LENGTH_SHORT).show()
                    else openMaps(context, Uri.parse("geo:0,0?q=" + Uri.encode(destination)))
                }, modifier = Modifier.weight(1f)) { Text("Buscar") }
                Button(onClick = {
                    if (destination.isBlank()) Toast.makeText(context, "Escribe un destino.", Toast.LENGTH_SHORT).show()
                    else openMaps(context, Uri.parse("google.navigation:q=" + Uri.encode(destination)))
                }, modifier = Modifier.weight(1f)) { Text("Navegar") }
            }
        }
    }
}

@Composable
fun PescaIAConnectorCard() {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("PescaIA Surfcasting", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Acceso a la información pública del proyecto. La consulta del contenido de cuenta, bitácora y favoritos todavía requiere una API privada autenticada.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { openPescaIA(context, "https://pescaisurfcasting.company/") }, modifier = Modifier.fillMaxWidth()) { Text("Abrir PescaIA") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { openPescaIA(context, "https://pescaisurfcasting.company/playas.html") }, modifier = Modifier.weight(1f)) { Text("Playas") }
                Button(onClick = { openPescaIA(context, "https://pescaisurfcasting.company/metodologia.html") }, modifier = Modifier.weight(1f)) { Text("Fuentes") }
            }
        }
    }
}

private fun openMaps(context: android.content.Context, uri: Uri) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "No hay una aplicación de mapas compatible.", Toast.LENGTH_LONG).show() }
}

private fun openPescaIA(context: android.content.Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure { Toast.makeText(context, "No se pudo abrir PescaIA.", Toast.LENGTH_LONG).show() }
}
