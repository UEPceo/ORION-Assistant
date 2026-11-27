package com.orion.voicelab.data

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import java.io.File
import java.util.Locale

/**
 * Lecturas de recursos del dispositivo para la Fase 0 de ORION.
 * Solo consulta datos del sistema Android; no guarda ni envía nada.
 */
object DeviceMetrics {

    data class Snapshot(
        val totalRamMb: Long,
        val availRamMb: Long,
        val lowMemory: Boolean,
        val appPssMb: Long,
        val batteryPercent: Int,      // -1 si no disponible
        val batteryTempC: Float,      // NaN si no disponible
        val thermalStatus: String,
        val freeStorageMb: Long
    )

    fun snapshot(context: Context, dataDir: File): Snapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)

        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) level * 100 / scale else -1
        val tempTenths = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = if (tempTenths == Int.MIN_VALUE) Float.NaN else tempTenths / 10f

        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            thermalName(power.currentThermalStatus)
        } else {
            "no disponible (Android < 10)"
        }

        return Snapshot(
            totalRamMb = memoryInfo.totalMem / MB,
            availRamMb = memoryInfo.availMem / MB,
            lowMemory = memoryInfo.lowMemory,
            appPssMb = Debug.getPss() / 1024L,
            batteryPercent = percent,
            batteryTempC = tempC,
            thermalStatus = thermal,
            freeStorageMb = dataDir.usableSpace / MB
        )
    }

    fun deviceSummary(): String {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "n/d"
        return "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n" +
            "Hardware: ${Build.HARDWARE} · SoC: $soc · ABI: ${Build.SUPPORTED_ABIS.joinToString()}"
    }

    fun describe(s: Snapshot): String = buildString {
        append("RAM: ${s.availRamMb} MB libres de ${s.totalRamMb} MB")
        if (s.lowMemory) append(" (memoria baja)")
        append("\nMemoria de ORION (PSS): ${s.appPssMb} MB")
        append("\nBatería: ${if (s.batteryPercent >= 0) "${s.batteryPercent} %" else "n/d"}")
        append(" · Temp. batería: ${formatTemp(s.batteryTempC)}")
        append("\nEstado térmico: ${s.thermalStatus}")
        append("\nAlmacenamiento libre: ${s.freeStorageMb} MB")
    }

    fun formatTemp(value: Float): String =
        if (value.isNaN()) "n/d" else String.format(Locale.getDefault(), "%.1f °C", value)

    private fun thermalName(status: Int): String = when (status) {
        0 -> "normal"
        1 -> "ligero"
        2 -> "moderado"
        3 -> "severo"
        4 -> "crítico"
        5 -> "emergencia"
        6 -> "apagado inminente"
        else -> "desconocido ($status)"
    }

    private const val MB = 1024L * 1024L
}
