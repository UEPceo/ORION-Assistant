package com.orion.voicelab

import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.Debug
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * On-device crash, process-exit and memory-pressure diagnostics.
 * Logs remain in app-private storage and are included in the exported device report.
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

    /**
     * Reads Android's historical process-exit records once per newly observed exit.
     * Available from API 30; older Android versions continue using the uncaught handler.
     */
    fun recordHistoricalProcessExits(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        runCatching {
            val appContext = context.applicationContext
            val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastRecordedTimestamp = prefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0L)
            val exits = appContext.getSystemService(Context.ACTIVITY_SERVICE)
                .let { it as android.app.ActivityManager }
                .getHistoricalProcessExitReasons(appContext.packageName, 0, MAX_EXIT_RECORDS)

            var newestTimestamp = lastRecordedTimestamp
            exits.orEmpty()
                .filter { it.timestamp > lastRecordedTimestamp }
                .sortedBy { it.timestamp }
                .forEach { exit ->
                    val timestamp = SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss.SSS",
                        Locale.US
                    ).format(Date(exit.timestamp))
                    val trace = readTrace(exit)
                    val details = buildString {
                        append("process=${exit.processName}")
                        append(" reason=${exit.reason} (${reasonLabel(exit.reason)})")
                        append(" status=${exit.status}")
                        append(" importance=${exit.importance}")
                        append(" pss=${exit.pss}KB")
                        append(" rss=${exit.rss}KB")
                        if (!exit.description.isNullOrBlank()) {
                            append(" description=${exit.description}")
                        }
                        append(" timestamp=$timestamp")
                        if (trace.isNotBlank()) {
                            appendLine()
                            append("nativeTrace:\n")
                            append(trace)
                        } else {
                            append(" nativeTrace=unavailable")
                        }
                    }
                    record(appContext, "PROCESS_EXIT", details)
                    newestTimestamp = maxOf(newestTimestamp, exit.timestamp)
                }

            if (newestTimestamp > lastRecordedTimestamp) {
                prefs.edit().putLong(KEY_LAST_EXIT_TIMESTAMP, newestTimestamp).apply()
            }
        }.onFailure {
            record(context.applicationContext, "WARN", "Unable to read historical process exits", it)
        }
    }

    private fun readTrace(exit: ApplicationExitInfo): String {
        return runCatching {
            val input = exit.traceInputStream ?: return ""
            input.use { stream ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var remaining = MAX_TRACE_BYTES
                while (remaining > 0) {
                    val count = stream.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count <= 0) break
                    output.write(buffer, 0, count)
                    remaining -= count
                }
                output.toString(Charsets.UTF_8.name()).takeLast(MAX_TRACE_CHARS)
            }
        }.getOrDefault("")
    }

    private fun reasonLabel(reason: Int): String = when (reason) {
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_CRASH -> "CRASH"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_USER_STOPPED -> "USER_STOPPED"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_OTHER -> "OTHER"
        else -> "UNKNOWN"
    }

    /** Records a recoverable runtime error with a stable event identifier and full technical cause. */
    fun recordOperationalError(
        context: Context,
        eventId: String,
        component: String,
        message: String,
        error: Throwable? = null
    ) {
        record(
            context.applicationContext,
            "ERROR",
            "eventId=$eventId component=$component cause=$message",
            error
        )
    }

    /** Returns a bounded tail of the private diagnostic log for local self-diagnosis. */
    fun readRecentDiagnostics(context: Context, maxChars: Int = 6_000): String {
        if (maxChars <= 0) return ""
        return runCatching {
            val file = File(context.applicationContext.filesDir, "orion_diagnostics.log")
            if (!file.exists() || !file.isFile) "" else file.readText().takeLast(maxChars)
        }.getOrDefault("")
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
                    error?.let { appendLine(it.stackTraceToString()) }
                })
                if (file.length() > MAX_LOG_BYTES) {
                    val tail = file.readText().takeLast((MAX_LOG_BYTES / 2).toInt())
                    file.writeText(tail)
                }
            }
        }
    }

    private const val PREFS_NAME = "orion_crash_diagnostics"
    private const val KEY_LAST_EXIT_TIMESTAMP = "last_recorded_exit_timestamp"
    private const val MAX_EXIT_RECORDS = 10
    private const val MAX_TRACE_BYTES = 32 * 1024
    private const val MAX_TRACE_CHARS = 24 * 1024
    private const val MAX_LOG_BYTES = 256 * 1024L
}
