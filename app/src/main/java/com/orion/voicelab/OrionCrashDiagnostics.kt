package com.orion.voicelab

import android.content.Context
import android.os.Debug
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Minimal on-device crash and memory-pressure diagnostics.
 * The log stays in app-private storage and can be included in the user-exported report.
 */
object OrionCrashDiagnostics {
    private val installed = AtomicBoolean(false)
    private val writeLock = Any()

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            record(appContext, "FATAL", "Uncaught exception on thread ${thread.name}", error)
            previous?.uncaughtException(thread, error)
        }
        record(appContext, "INFO", "Crash diagnostics installed")
    }

    fun recordMemoryPressure(context: Context, level: Int) {
        record(context.applicationContext, "MEMORY", "onTrimMemory level=$level")
    }

    fun recordLowMemory(context: Context) {
        record(context.applicationContext, "MEMORY", "onLowMemory")
    }

    private fun record(context: Context, type: String, message: String, error: Throwable? = null) {
        synchronized(writeLock) {
            runCatching {
                val file = File(context.filesDir, "orion_diagnostics.log")
                file.appendText(buildString {
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                    appendLine("[$timestamp] $type: $message")
                    val runtime = Runtime.getRuntime()
                    val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024L * 1024L)
                    val maxMb = runtime.maxMemory() / (1024L * 1024L)
                    val nativeMb = Debug.getNativeHeapAllocatedSize() / (1024L * 1024L)
                    appendLine("memory: javaUsed=${usedMb}MB javaMax=${maxMb}MB nativeHeap=${nativeMb}MB")
                    error?.let {
                        appendLine(it.stackTraceToString())
                    }
                })
                if (file.length() > MAX_LOG_BYTES) {
                    val tail = file.readText().takeLast((MAX_LOG_BYTES / 2).toInt())
                    file.writeText(tail)
                }
            }
        }
    }

    private const val MAX_LOG_BYTES = 256 * 1024L
}
