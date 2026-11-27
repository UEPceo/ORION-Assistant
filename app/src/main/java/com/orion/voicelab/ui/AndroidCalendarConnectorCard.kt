package com.orion.voicelab.ui

import android.content.Intent
import android.provider.CalendarContract
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
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Permission-free calendar handoff. The selected calendar app owns the final
 * review and save step; ORION does not silently write to a calendar.
 */
@Composable
fun AndroidCalendarConnectorCard() {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var start by rememberSaveable { mutableStateOf("10:00") }
    var end by rememberSaveable { mutableStateOf("11:00") }
    var description by rememberSaveable { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            Text("Calendario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Preparar un evento y abrir la aplicación de calendario para revisarlo y guardarlo. No requiere permiso de lectura ni modifica eventos existentes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(title, { title = it.take(160) }, modifier = Modifier.fillMaxWidth(), label = { Text("Título") }, singleLine = true)
            OutlinedTextField(date, { date = it.take(10) }, modifier = Modifier.fillMaxWidth(), label = { Text("Fecha (AAAA-MM-DD)") }, singleLine = true)
            OutlinedTextField(start, { start = it.take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("Hora de inicio (HH:MM)") }, singleLine = true)
            OutlinedTextField(end, { end = it.take(5) }, modifier = Modifier.fillMaxWidth(), label = { Text("Hora de fin (HH:MM)") }, singleLine = true)
            OutlinedTextField(description, { description = it.take(2_000) }, modifier = Modifier.fillMaxWidth(), label = { Text("Descripción (opcional)") }, minLines = 2)
            Button(
                onClick = {
                    runCatching {
                        require(title.isNotBlank()) { "Indica el título del evento." }
                        val localDate = LocalDate.parse(date)
                        val startDateTime = localDate.atTime(LocalTime.parse(start))
                        var endDateTime = localDate.atTime(LocalTime.parse(end))
                        if (!endDateTime.isAfter(startDateTime)) {
                            endDateTime = endDateTime.plusDays(1)
                        }
                        val zone = ZoneId.systemDefault()
                        val beginMillis = startDateTime.atZone(zone).toInstant().toEpochMilli()
                        val endMillis = endDateTime.atZone(zone).toInstant().toEpochMilli()
                        require(endMillis > beginMillis)
                        Intent(Intent.ACTION_INSERT).apply {
                            data = CalendarContract.Events.CONTENT_URI
                            putExtra(CalendarContract.Events.TITLE, title.trim())
                            putExtra(CalendarContract.Events.DESCRIPTION, description.trim())
                            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, beginMillis)
                            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
                        }
                    }.onSuccess { intent ->
                        runCatching { context.startActivity(intent) }
                            .onFailure {
                                Toast.makeText(context, "No hay una aplicación de calendario compatible.", Toast.LENGTH_LONG).show()
                            }
                    }.onFailure { error ->
                        Toast.makeText(
                            context,
                            error.message ?: "Revisa la fecha y las horas introducidas.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Revisar y guardar evento")
            }
        }
    }
}
