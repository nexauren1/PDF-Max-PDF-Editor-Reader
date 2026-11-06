package com.nexauren.pdfmax.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF10CDE3),
    onPrimary = Color(0xFF06162D),
    secondary = Color(0xFF2674E8),
    background = Color(0xFF09152E),
    onBackground = Color(0xFFF5F8FF),
    surface = Color(0xFF14264A),
    onSurface = Color(0xFFF5F8FF),
    surfaceVariant = Color(0xFF1C3159),
    onSurfaceVariant = Color(0xFFADBCD5)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00AFC8),
    onPrimary = Color.White,
    secondary = Color(0xFF2674E8),
    background = Color(0xFFF3F6FC),
    onBackground = Color(0xFF14213A),
    surface = Color.White,
    onSurface = Color(0xFF14213A),
    surfaceVariant = Color(0xFFE7EEF9),
    onSurfaceVariant = Color(0xFF52627D)
)

@Composable
fun PDFMaxTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        content = content
    )
}
