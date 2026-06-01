package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Palette sampled from the QueM icon and softened for readable app surfaces.
private val TealDark = Color(0xFF0D6F7B)
private val TealContainer = Color(0xFFDDF0F2)
private val TealItem = Color(0xFFEAF7F8)
private val TealOnDark = Color.White

private val AmberAccent = Color(0xFFF4A340)
private val AmberContainer = Color(0xFFFFE6BF)
private val AmberOnLight = Color(0xFF382000)

private val AppBackground = Color(0xFFF6FBFB)
private val White = Color(0xFFFFFFFF)
private val OnSurface = Color(0xFF153338)

private val QueMColorScheme = lightColorScheme(
    primary = TealDark,
    onPrimary = TealOnDark,
    primaryContainer = TealContainer,
    onPrimaryContainer = TealDark,

    secondary = AmberAccent,
    onSecondary = AmberOnLight,
    secondaryContainer = AmberContainer,
    onSecondaryContainer = AmberOnLight,

    tertiary = TealDark,
    onTertiary = TealOnDark,
    tertiaryContainer = TealItem,
    onTertiaryContainer = TealDark,

    background = AppBackground,
    onBackground = OnSurface,
    surface = White,
    onSurface = OnSurface,
    surfaceVariant = TealItem,
    onSurfaceVariant = Color(0xFF42565A),
    outline = Color(0xFFC9E4E8),

    error = Color(0xFFBA1A1A),
    onError = TealOnDark
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
