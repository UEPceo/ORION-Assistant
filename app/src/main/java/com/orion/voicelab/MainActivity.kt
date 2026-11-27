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
 * ORION Voice Lab 0.3
 * Español offline: Silero VAD (segmentación) + Moonshine Base ES v2 (reconocimiento) con sherpa-onnx,
 * más un panel de medición para la Fase 0 (latencia, RAM, batería, temperatura).
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
}
