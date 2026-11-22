package com.juxtaposition.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Green = Color(0xFF1B8A5A)
val GreenLight = Color(0xFFE3F4EC)
val Amber = Color(0xFFF59E0B)
val Ink = Color(0xFF1F2937)
val Muted = Color(0xFF6B7280)

@Composable
fun JuxtaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green, onPrimary = Color.White, primaryContainer = GreenLight,
            surface = Color.White, background = Color(0xFFF7F8FA), onSurface = Ink, onBackground = Ink,
        ),
        content = content,
    )
}
