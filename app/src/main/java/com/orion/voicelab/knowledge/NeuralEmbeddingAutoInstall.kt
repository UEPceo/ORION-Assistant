package com.orion.voicelab.knowledge

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.orion.voicelab.model.ModelManager
import androidx.work.CoroutineWorker

/** Downloads the optional multilingual encoder by default, but only on unmetered, battery-safe networks. */
object NeuralEmbeddingAutoInstallScheduler {
    const val PREFERENCES = "orion_knowledge_settings"
    const val AUTO_INSTALL_KEY = "auto_install_multilingual_encoder"
    private const val UNIQUE_WORK = "orion-neural-encoder-auto-install"

    fun enabled(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getBoolean(AUTO_INSTALL_KEY, true)

    fun schedule(context: Context) {
        if (!enabled(context)) return
        // The optional 136 MB encoder must not compete with first-run voice model downloads.
        if (!ModelManager.areCoreModelsInstalled(context.applicationContext.filesDir)) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = OneTimeWorkRequestBuilder<NeuralEmbeddingAutoInstallWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context.applicationContext)
            .enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.KEEP, request)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit().putBoolean(AUTO_INSTALL_KEY, enabled).apply()
        if (enabled) schedule(context)
        else WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_WORK)
    }
}

class NeuralEmbeddingAutoInstallWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            if (!ModelManager.areCoreModelsInstalled(applicationContext.filesDir)) {
                return Result.retry()
            }
            val manager = NeuralEmbeddingModelManager(applicationContext)
            if (manager.isInstalled()) return Result.success()
            val availableBytes = applicationContext.filesDir.usableSpace
            require(availableBytes >= NeuralEmbeddingModelManager.MAX_MODEL_BYTES + 80L * 1024L * 1024L) {
                "Espacio libre insuficiente para instalar y verificar el encoder."
            }
            manager.download { downloaded, total ->
                setProgress(Data.Builder()
                    .putLong("downloadedBytes", downloaded)
                    .putLong("totalBytes", total)
                    .putString("status", "Descargando encoder ONNX")
                    .build())
            }
            val encoder = OnnxMultilingualEmbeddingEncoder(applicationContext)
            val inferencePassed = try {
                encoder.isAvailable()
            } finally {
                encoder.close()
            }
            if (!inferencePassed) {
                manager.delete()
                error("El archivo ONNX pasó SHA-256, pero falló la prueba real de inferencia. Se ha retirado.")
            }
            setProgress(Data.Builder().putInt("progress", 100).putString("status", "Encoder instalado y prueba de inferencia superada").build())
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(Data.Builder().putString("error", error.message?.take(240) ?: "Error de descarga").build())
        }
    }
}
