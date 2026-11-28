package com.orion.voicelab

import android.app.ActivityManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.orion.voicelab.knowledge.NeuralEmbeddingIndex
import com.orion.voicelab.knowledge.NeuralEmbeddingModelManager
import com.orion.voicelab.persistence.OrionDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Runs on a physical/emulated Android runtime, not Robolectric.
 * Encoder test is skipped only when the optional model has not been installed.
 */
@RunWith(AndroidJUnit4::class)
class DeviceRuntimeValidationTest {
    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun roomDatabaseUsesSqlCipherAndIsNotPlaintext() {
        val database = OrionDatabase.getInstance(context)
        val cursor = database.openHelper.writableDatabase.query("PRAGMA cipher_version")
        val cipherVersion = cursor.use {
            assertTrue("SQLCipher PRAGMA did not return a row", it.moveToFirst())
            it.getString(0)
        }
        assertTrue("SQLCipher version must be present", cipherVersion.isNotBlank())

        val file = context.getDatabasePath("orion_offline_memory.db")
        assertTrue(file.isFile)
        val header = file.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
        assertFalse("Room database is still plaintext SQLite", String(header, Charsets.US_ASCII) == "SQLite format 3\u0000")
    }

    @Test
    fun onnxEncoderLoadsAndProducesNormalizedMultilingualEmbeddings() = runBlocking {
        val manager = NeuralEmbeddingModelManager(context)
        assumeTrue("Optional ONNX model is not installed yet", manager.isInstalled())

        val index = NeuralEmbeddingIndex(context)
        val madrid = "Madrid is the capital city of Spain."
        val unrelated = "A banana is a yellow tropical fruit."
        try {
            val installed = index.ensureDocument("device-validation-madrid", listOf(madrid), NeuralEmbeddingIndex.contentFingerprint(listOf(madrid)))
            assertTrue("ONNX document embedding failed", installed)
            val unrelatedInstalled = index.ensureDocument("device-validation-unrelated", listOf(unrelated), NeuralEmbeddingIndex.contentFingerprint(listOf(unrelated)))
            assertTrue("ONNX distractor embedding failed", unrelatedInstalled)
            val query = index.embedQuery("What is the capital of Spain?")
            assertNotNull("ONNX query embedding failed", query)
            val vector = query!!
            assertEquals(512, vector.size)
            val norm = kotlin.math.sqrt(vector.sumOf { it.toDouble() * it.toDouble() })
            assertTrue("Embedding is not normalized", kotlin.math.abs(norm - 1.0) < 0.02)

            val relevantScore = index.score("device-validation-madrid", vector)
            val unrelatedScore = index.score("device-validation-unrelated", vector)
            assertTrue("Relevant passage should outrank unrelated content", relevantScore > unrelatedScore)
            assertTrue("Embedding query should not collapse to a zero vector", vector.any { kotlin.math.abs(it) > 0f })
        } finally {
            index.remove("device-validation-madrid")
            index.remove("device-validation-unrelated")
        }
    }

    @Test
    fun reportsRealDeviceMemoryClassAndLowRamProfileWithoutCrashing() {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        assertTrue("Android memory class must be reported", activityManager.memoryClass > 0)
        val runtime = Runtime.getRuntime()
        assertTrue("Runtime max heap must be reported", runtime.maxMemory() > 0L)
        // The assertion intentionally does not assume a minimum RAM tier.
        val isLowRam = activityManager.isLowRamDevice
        assertNotNull(isLowRam.toString())
    }
}
