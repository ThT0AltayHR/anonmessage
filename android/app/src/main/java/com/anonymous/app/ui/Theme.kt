package com.anonymous.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Mat siyah / grafit; tek vurgu rengi mor (rozet setiyle uyumlu). Animasyon yok. */
private val Dark = darkColorScheme(
    primary = Color(0xFF8B55F0), onPrimary = Color.White,
    background = Color(0xFF0A0A0C), onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF131317), onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF1C1C22), onSurfaceVariant = Color(0xFF9B9BA8),
    outline = Color(0xFF2A2A32), error = Color(0xFFE5484D),
)
private val Light = lightColorScheme(
    primary = Color(0xFF6D35D6), onPrimary = Color.White,
    background = Color(0xFFF5F5F8), onBackground = Color(0xFF15151A),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF15151A),
    surfaceVariant = Color(0xFFEAEAF0), onSurfaceVariant = Color(0xFF6A6A78),
    outline = Color(0xFFD4D4DC), error = Color(0xFFD13438),
)

@Composable
fun AnonTheme(mode: String, content: @Composable () -> Unit) {
    val dark = when (mode) { "light" -> false; "dark" -> true; else -> true }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
