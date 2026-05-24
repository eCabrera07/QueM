package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val QueMColorScheme = lightColorScheme()

@Composable
fun QueMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QueMColorScheme,
        content = content
    )
}
