package com.nexauren.pdfmax.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val MaxNavy = Color(0xFF071329)
val MaxSurface = Color(0xFF0D1B34)
val MaxCard = Color(0xFF102341)
val MaxBlue = Color(0xFF168BFF)
val MaxCyan = Color(0xFF19C8FF)
val MaxViolet = Color(0xFF7957FF)
val MaxText = Color(0xFFF4F7FF)
val MaxMuted = Color(0xFF9AAAC4)

private val MaxDarkScheme = darkColorScheme(
    primary = MaxBlue, secondary = MaxViolet, tertiary = MaxCyan,
    background = MaxNavy, surface = MaxSurface, onPrimary = Color.White,
    onBackground = MaxText, onSurface = MaxText
)

@Composable
fun PDFMaxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MaxDarkScheme, content = content)
}
