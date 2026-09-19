// Theme.kt — v1.0.0 — 2026-09-20 00:10 UTC
package com.plumberdiary.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Stessa palette del mockup (Artifact "Plumber Diary — Mockup schermate"):
// teal primario #0F766E, accento arancio #D97706.
val PlumberTeal = Color(0xFF0F766E)
val PlumberOrange = Color(0xFFD97706)
val PlumberBackground = Color(0xFFF5F7F8)

private val LightColors = lightColorScheme(
    primary = PlumberTeal,
    secondary = PlumberOrange,
    background = PlumberBackground,
)

private val DarkColors = darkColorScheme(
    primary = PlumberTeal,
    secondary = PlumberOrange,
)

@Composable
fun PlumberDiaryTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) DarkColors else LightColors
    MaterialTheme(colorScheme = colors, content = content)
}
