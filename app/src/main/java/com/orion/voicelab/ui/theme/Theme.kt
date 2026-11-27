package com.orion.voicelab.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val OrionDarkColorScheme = darkColorScheme(
    primary = OrionCyan,
    onPrimary = OrionCyanDark,
    primaryContainer = OrionSurfaceVariant,
    onPrimaryContainer = OrionCyan,
    secondary = OrionVoiceActive,
    onSecondary = OrionCyanDark,
    secondaryContainer = OrionSurfaceElevated,
    onSecondaryContainer = OrionCyanMuted,
    tertiary = OrionCyanMuted,
    background = OrionNavyBg,
    onBackground = OrionTextPrimary,
    surface = OrionSurface,
    onSurface = OrionTextPrimary,
    surfaceVariant = OrionSurfaceElevated,
    onSurfaceVariant = OrionTextSecondary,
    outline = OrionOutline,
    error = OrionStopCoral,
    onError = OrionTextPrimary
)

private val OrionShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun OrionTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = OrionDarkColorScheme,
        typography = OrionTypography,
        shapes = OrionShapes,
        content = content
    )
}
