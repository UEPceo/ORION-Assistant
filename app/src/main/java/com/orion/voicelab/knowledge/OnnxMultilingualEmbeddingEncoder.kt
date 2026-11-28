package com.orion.voicelab.knowledge

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.nio.FloatBuffer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Local sentence encoder backed by ONNX Runtime + ONNX Runtime Extensions.
 * The selected model embeds its tokenizer and accepts a string tensor named "text".
 */
class OnnxMultilingualEmbeddingEncoder(context: Context) : Closeable {
    private val manager = NeuralEmbeddingModelManager(context)
    private val lock = Any()
    private var session: OrtSession? = null
    @Volatile private var smokeTestPassed = false
    @Volatile var lastInitializationError: String? = null
        private set
    private val environment: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    suspend fun isAvailable(): Boolean {
        if (!manager.isInstalled()) return false
        if (smokeTestPassed) return true
        val vector = try {
            encode("ORION local embedding encoder health check.")
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            lastInitializationError = error.message?.take(240) ?: error.javaClass.simpleName
            return false
        } ?: return false
        val norm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
        val passed = vector.size == 512 && norm.isFinite() && kotlin.math.abs(norm - 1.0) < 0.02
        if (passed) {
            smokeTestPassed = true
            lastInitializationError = null
        } else {
            lastInitializationError = "El encoder devolvió una dimensión o norma inesperada."
        }
        return passed
    }

    suspend fun encode(text: String): FloatArray? = withContext(Dispatchers.Default) {
        if (text.isBlank() || !manager.isInstalled()) return@withContext null
        synchronized(lock) {
            val activeSession = session ?: createSession().also { session = it }
            OnnxTensor.createTensor(environment, arrayOf(text)).use { input ->
                activeSession.run(mapOf(INPUT_NAME to input)).use { result ->
                    val outputName = activeSession.outputNames.firstOrNull()
                        ?: error("El modelo ONNX no declara una salida.")
                    val output = result.get(outputName).orElseThrow {
                        IllegalStateException("El modelo ONNX no devolvió su embedding.")
                    } as? OnnxTensor ?: error("La salida ONNX no es un tensor.")
                    val buffer: FloatBuffer = output.floatBuffer
                    val vector = FloatArray(buffer.remaining())
                    buffer.get(vector)
                    require(vector.isNotEmpty() && vector.all { value -> value.isFinite() }) {
                        "El encoder devolvió un vector vacío o no finito."
                    }
                    normalize(vector)
                }
            }
        }
    }

    private fun createSession(): OrtSession {
        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            options.setInterOpNumThreads(1)
            val extensionsPath = Class.forName(EXTENSIONS_PACKAGE)
                .getMethod("getLibraryPath")
                .invoke(null) as String
            options.registerCustomOpLibrary(extensionsPath)
            return environment.createSession(manager.modelFile.absolutePath, options)
        } finally {
            options.close()
        }
    }

    private fun normalize(vector: FloatArray): FloatArray {
        val norm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it.toDouble() }).toFloat()
        if (norm <= 0f) return vector
        return FloatArray(vector.size) { vector[it] / norm }
    }

    override fun close() {
        synchronized(lock) {
            session?.close()
            session = null
        }
    }

    private companion object {
        const val INPUT_NAME = "text"
        const val EXTENSIONS_PACKAGE = "ai.onnxruntime.extensions.OrtxPackage"
    }
}
