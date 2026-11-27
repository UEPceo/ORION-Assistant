package com.orion.voicelab.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Calendar connector: read upcoming events only after runtime permission;
 * event creation remains a reviewed ACTION_INSERT handoff.
 */
@Composable
fun AndroidCalendarConnectorCard() {
    val context = LocalContext.current
    var title by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().plusDays(1).toString()) }
    var start by rememberSaveable { mutableStateOf("10:00") }
    var end by rememberSaveable { mutableStateOf("11:00") }
    var description by rememberSaveable { mutableStateOf("") }
    var agenda by rememberSaveable { mutableStateOf("La agenda todavía no se ha consultado.") }

    fun loadAgenda() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            agenda = "Permiso de lectura no concedido."
            return
        }
        runCatching {
            val begin = System.currentTimeMillis()
            val finish = begin + 14L * 24L * 60L * 60L * 1_000L
            val projection = arrayOf(
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END,
                CalendarContract.Instances.EVENT_LOCATION
            )
            val cursor = CalendarContract.Instances.query(context.contentResolver, projection, begin, finish)
            val formatter = SimpleDateFormat("EEE d MMM, HH:mm", Locale("es", "ES"))
            val lines = mutableListOf<String>()
            cursor?.use {
                val titleIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val locationIndex = it.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_LOCATION)
                while (it.moveToNext() && lines.size < 15) {
                    val eventTitle = it.getString(titleIndex).orEmpty().ifBlank { "Evento sin título" }
                    val eventBegin = it.getLong(beginIndex)
                    val eventEnd = it.getLong(endIndex)
                    val location = it.getString(locationIndex).orEmpty()
                    lines += "${formatter.format(Date(eventBegin))}–${formatter.format(Date(eventEnd))}: ${eventTitle.take(120)}" +
                        location.takeIf { value -> value.isNotBlank() }?.let { value -> " · ${value.take(100)}" }.orEmpty()
                }
            }
            agenda = if (lines.isEmpty()) "No hay eventos en los próximos 14 días." else lines.joinToString("\n")
        }.onFailure {
            agenda = "No se pudo leer la agenda. Comprueba el permiso y la aplicación de calendario."
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) loadAgenda() else agenda = "Has denegado el permiso de lectura del calendario."
    }

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
                "Consulta los próximos 14 días solo después de conceder permiso de lectura. Para crear eventos, ORION abre tu calendario y tú revisas y guardas; no modifica ni elimina eventos existentes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
                        loadAgenda()
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Consultar próximos eventos") }
            Text(agenda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        if (!endDateTime.isAfter(startDateTime)) endDateTime = endDateTime.plusDays(1)
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
                            .onFailure { Toast.makeText(context, "No hay una aplicación de calendario compatible.", Toast.LENGTH_LONG).show() }
                    }.onFailure { error ->
                        Toast.makeText(context, error.message ?: "Revisa la fecha y las horas introducidas.", Toast.LENGTH_LONG).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Revisar y guardar evento") }
        }
    }
}
