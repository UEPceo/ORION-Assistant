package com.orion.voicelab.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

/**
 * Google Keep has no public consumer API for enumerating a user's private notes.
 * This bridge hands user-authored text to Android Sharesheet; Keep can receive it
 * when installed and registered as a text share target.
 */
@Composable
fun GoogleKeepConnectorCard() {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var body by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Google Keep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Enviar una nota a Google Keep mediante el menú Compartir de Android. Keep debe estar instalado. ORION todavía no puede leer todas las notas privadas de tu cuenta.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Título") },
                singleLine = true
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it.take(12_000) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Contenido de la nota") },
                minLines = 3
            )
            Button(
                onClick = {
                    val text = buildString {
                        if (title.isNotBlank()) append(title.trim()).append("\n\n")
                        append(body.trim())
                    }.trim()
                    if (text.isBlank()) {
                        Toast.makeText(context, "Escribe el contenido de la nota.", Toast.LENGTH_SHORT).show()
                    } else {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                            if (title.isNotBlank()) putExtra(Intent.EXTRA_SUBJECT, title.trim())
                        }
                        runCatching {
                            context.startActivity(Intent.createChooser(sendIntent, "Guardar nota con…"))
                        }.onFailure {
                            Toast.makeText(context, "No hay ninguna aplicación disponible para compartir la nota.", Toast.LENGTH_LONG).show()
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Compartir nota con Google Keep")
            }
        }
    }
}
