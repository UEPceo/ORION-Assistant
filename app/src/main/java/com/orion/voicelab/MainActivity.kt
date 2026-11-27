package com.orion.voicelab

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.orion.voicelab.tasks.AndroidTaskReminderScheduler
import com.orion.voicelab.ui.OrionVoiceLabScreen
import com.orion.voicelab.ui.VoiceLabViewModel
import com.orion.voicelab.ui.theme.OrionTheme

/**
 * ORION 0.7.0 — Personal Assistant & Task Management
 * Núcleo conversacional offline en español (Silero VAD + Moonshine Base ES + llama.cpp Qwen2.5 + Piper TTS)
 * con memoria persistente (Room), agenda de Co-workers, telefonía real y gestión local de tareas y recordatorios.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: VoiceLabViewModel by viewModels()
    private var requestedInitialTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OrionCrashDiagnostics.install(applicationContext)
        requestedInitialTab = intent?.getStringExtra(AndroidTaskReminderScheduler.EXTRA_OPEN_TAB)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            OrionTheme {
                OrionVoiceLabScreen(
                    viewModel = viewModel,
                    initialTabName = requestedInitialTab
                )
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        OrionCrashDiagnostics.recordMemoryPressure(applicationContext, level)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        OrionCrashDiagnostics.recordLowMemory(applicationContext)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val tab = intent.getStringExtra(AndroidTaskReminderScheduler.EXTRA_OPEN_TAB)
        if (!tab.isNullOrBlank()) {
            requestedInitialTab = tab
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.onAppBackgrounded()
        }
    }
}
