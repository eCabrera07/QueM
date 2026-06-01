package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QueMColorScheme = lightColorScheme(
    primary = Color(0xFF256A5D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB9E7DC),
    onPrimaryContainer = Color(0xFF0B332C),
    secondary = Color(0xFF6D5D2E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1DF9B),
    onSecondaryContainer = Color(0xFF2B240A),
    tertiary = Color(0xFF7B4E66),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E8),
    onTertiaryContainer = Color(0xFF311020),
    background = Color(0xFFFAFBF8),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFFAFBF8),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFE0E6DE),
    onSurfaceVariant = Color(0xFF444B46),
    outline = Color(0xFF747D76),
    error = Color(0xFFBA1A1A),
    onError = Color.White
)

private val QueMTypography = Typography()

@Composable
fun QueMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QueMColorScheme,
        typography = QueMTypography,
        content = content
    )
}
