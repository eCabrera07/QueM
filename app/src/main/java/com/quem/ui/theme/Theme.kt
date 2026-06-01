package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand teal: #1A8C9B
private val Teal700    = Color(0xFF1A8C9B)
private val Teal900    = Color(0xFF00363D)
private val Teal100    = Color(0xFFB2EBF2)
private val Teal50     = Color(0xFFE0F7FA)
private val TealOnDark = Color.White

private val QueMColorScheme = lightColorScheme(
    primary              = Teal700,
    onPrimary            = TealOnDark,
    primaryContainer     = Teal100,
    onPrimaryContainer   = Teal900,
    secondary            = Color(0xFF4A7C86),
    onSecondary          = TealOnDark,
    secondaryContainer   = Color(0xFFBDE8EF),
    onSecondaryContainer = Color(0xFF001F24),
    tertiary             = Color(0xFF5B5EA6),
    onTertiary           = TealOnDark,
    tertiaryContainer    = Color(0xFFE0E0FF),
    onTertiaryContainer  = Color(0xFF15164A),
    background           = Color(0xFFF5FAFB),
    onBackground         = Color(0xFF191C1D),
    surface              = Color(0xFFF5FAFB),
    onSurface            = Color(0xFF191C1D),
    surfaceVariant       = Teal50,
    onSurfaceVariant     = Color(0xFF3F484A),
    outline              = Color(0xFF6F797A),
    error                = Color(0xFFBA1A1A),
    onError              = TealOnDark
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
