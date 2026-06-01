package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Extracted from the QueM icon
private val TealDark    = Color(0xFF0D7A8A)   // deep teal from icon background
private val TealMid     = Color(0xFF1A8C9B)   // mid teal — brand colour
private val TealLight   = Color(0xFFEAF6F8)   // almost-white teal
private val TealOnDark  = Color.White

private val OrangeAccent     = Color(0xFFE8962A)   // orange from character's arm
private val OrangeContainer  = Color(0xFFFFF0D6)
private val OrangeOnDark     = Color(0xFF3D2000)

private val White       = Color(0xFFFFFFFF)
private val OffWhite    = Color(0xFFF4FAFB)   // very slightly teal-tinted white
private val OnSurface   = Color(0xFF191C1D)

private val QueMColorScheme = lightColorScheme(
    // Primary — teal
    primary              = TealMid,
    onPrimary            = TealOnDark,
    primaryContainer     = TealLight,
    onPrimaryContainer   = TealDark,

    // Secondary — orange (the accent highlight from the icon)
    secondary            = OrangeAccent,
    onSecondary          = TealOnDark,
    secondaryContainer   = OrangeContainer,
    onSecondaryContainer = OrangeOnDark,

    // Tertiary — darker teal for depth
    tertiary             = TealDark,
    onTertiary           = TealOnDark,
    tertiaryContainer    = TealLight,
    onTertiaryContainer  = TealDark,

    // Background — white; surfaces/cards — light teal
    background           = White,
    onBackground         = OnSurface,
    surface              = TealLight,
    onSurface            = OnSurface,
    surfaceVariant       = TealLight,
    onSurfaceVariant     = Color(0xFF3F484A),
    outline              = Color(0xFF6F797A),

    // Error
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
