package com.orion.voicelab.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelIntegrityManifestTest {
    @get:Rule val temporaryFolder = TemporaryFolder()

    @Test fun directoryManifestDetectsContentTamperingEvenWhenFileLengthIsUnchanged() {
        val root = temporaryFolder.newFolder("asr")
        val model = root.resolve("encoder_model.ort")
        model.writeBytes(ByteArray(4096) { it.toByte() })
        root.resolve("tokens.txt").writeText("token-a\ntoken-b\n")

        ModelIntegrityManifest.createDirectoryManifest(root)
        assertTrue(ModelIntegrityManifest.verifyDirectory(root))

        model.writeBytes(ByteArray(4096) { (it + 1).toByte() })
        assertFalse(ModelIntegrityManifest.verifyDirectory(root))
    }

    @Test fun directoryManifestRejectsUnexpectedFiles() {
        val root = temporaryFolder.newFolder("tts")
        root.resolve("model.onnx").writeBytes(ByteArray(2048) { it.toByte() })
        ModelIntegrityManifest.createDirectoryManifest(root)
        root.resolve("injected.bin").writeText("unexpected")
        assertFalse(ModelIntegrityManifest.verifyDirectory(root))
    }

    @Test fun largeFileManifestUsesPinnedDigestAndDetectsMetadataChanges() {
        val root = temporaryFolder.newFolder("llm")
        val model = root.resolve("model.gguf")
        model.writeText("GGUF-model-data")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(model.readBytes()).joinToString("") { "%02x".format(it) }

        ModelIntegrityManifest.createSingleFileManifest(root, model, digest)
        assertTrue(ModelIntegrityManifest.verifySingleLargeFile(root, digest))

        model.appendText("-tampered")
        assertFalse(ModelIntegrityManifest.verifySingleLargeFile(root, digest))
    }
}
