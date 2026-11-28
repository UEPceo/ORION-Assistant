package com.orion.voicelab.model

import kotlinx.coroutines.Job
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCancellationGuardTest {
    @Test fun activeInstallJobAllowsDownload() {
        val job = Job()
        assertFalse(DownloadCancellationGuard(job).shouldCancel())
        job.cancel()
    }

    @Test fun cancellingInstallJobStopsDownload() {
        val job = Job()
        val guard = DownloadCancellationGuard(job)
        job.cancel()
        assertTrue(guard.shouldCancel())
    }

    @Test fun missingCoroutineJobFailsClosed() {
        assertTrue(DownloadCancellationGuard(null).shouldCancel())
    }
}
