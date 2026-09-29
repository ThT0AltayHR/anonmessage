package com.anonymous.app.ui

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Mat siyah / grafit; tek vurgu rengi mor (rozet setiyle uyumlu). Animasyon yok. */
private val Dark = darkColorScheme(
    primary = Color(0xFF8B55F0), onPrimary = Color.White,
    primaryContainer = Color(0xFF2B1B52), onPrimaryContainer = Color(0xFFE6D9FF),
    secondaryContainer = Color(0xFF26203A), onSecondaryContainer = Color(0xFFE6D9FF),
    background = Color(0xFF0A0A0C), onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF131317), onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF1C1C22), onSurfaceVariant = Color(0xFF9B9BA8),
    surfaceContainerLowest = Color(0xFF07070A), surfaceContainerLow = Color(0xFF101014),
    surfaceContainer = Color(0xFF131317), surfaceContainerHigh = Color(0xFF1A1A20),
    surfaceContainerHighest = Color(0xFF22222A),
    outline = Color(0xFF2A2A32), outlineVariant = Color(0xFF24242C), error = Color(0xFFE5484D),
)
private val Light = lightColorScheme(
    primary = Color(0xFF6D35D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFE8DDFB), onPrimaryContainer = Color(0xFF2B1B52),
    secondaryContainer = Color(0xFFEDE7FA), onSecondaryContainer = Color(0xFF2B1B52),
    background = Color(0xFFF5F5F8), onBackground = Color(0xFF15151A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF15151A),
    surfaceVariant = Color(0xFFEAEAF0), onSurfaceVariant = Color(0xFF6A6A78),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = Color(0xFFF1F1F5), surfaceContainerHigh = Color(0xFFEBEBF0),
    surfaceContainerHighest = Color(0xFFE5E5EB),
    outline = Color(0xFFD4D4DC), outlineVariant = Color(0xFFDCDCE4), error = Color(0xFFD13438),
)

@Composable
fun AnonTheme(mode: String, content: @Composable () -> Unit) {
    val dark = mode != "light"
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
