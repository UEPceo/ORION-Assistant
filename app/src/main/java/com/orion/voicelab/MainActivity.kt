package com.orion.voicelab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.orion.voicelab.ui.OrionVoiceLabScreen
import com.orion.voicelab.ui.VoiceLabViewModel
import com.orion.voicelab.ui.theme.OrionTheme

/**
 * ORION Voice Lab 0.4.1
 * Núcleo conversacional offline en español (Silero VAD + Moonshine Base ES + llama.cpp Qwen2.5 + Piper TTS)
 * y panel de medición y diagnóstico local en dispositivo.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: VoiceLabViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            OrionTheme {
                OrionVoiceLabScreen(viewModel = viewModel)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            viewModel.onAppBackgrounded()
        }
    }
}
