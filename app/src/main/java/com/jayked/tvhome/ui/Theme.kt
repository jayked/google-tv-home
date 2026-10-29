package com.jayked.tvhome.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

val Background = Color(0xFF101114)

@Composable
fun TvHomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8AB4F8),
            onPrimary = Color(0xFF062E6F),
            background = Background,
            surface = Color(0xFF1F2024),
            onSurface = Color(0xFFE3E3E3),
            onSurfaceVariant = Color(0xFFB0B3B8),
        ),
        content = content,
    )
}
