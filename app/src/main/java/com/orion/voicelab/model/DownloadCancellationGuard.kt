package com.orion.voicelab.model

import kotlinx.coroutines.Job

/**
 * Bridges coroutine cancellation into blocking model download loops.
 * A null job is treated as cancelled so downloads fail closed outside a coroutine.
 */
internal class DownloadCancellationGuard(private val ownerJob: Job?) {
    fun shouldCancel(): Boolean =
        ownerJob?.isActive != true || Thread.currentThread().isInterrupted
}
