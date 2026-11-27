package com.orion.voicelab

import android.app.Application

/**
 * Installs diagnostics before any Activity or native voice component is initialized.
 */
class OrionApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OrionCrashDiagnostics.install(this)
        OrionCrashDiagnostics.recordHistoricalProcessExits(this)
    }
}
