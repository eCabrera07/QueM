package com.quem.ui

import androidx.compose.ui.graphics.Color

internal fun SyncIndicator.toColor(): Color = when (this) {
    SyncIndicator.PENDING -> Color(0xFFF4A340)
    SyncIndicator.SYNCING -> Color(0xFF1C95A8)
    SyncIndicator.ERROR -> Color(0xFFD32F2F)
}

internal fun SyncIndicator.toLabel(): String = when (this) {
    SyncIndicator.PENDING -> "Pending sync"
    SyncIndicator.SYNCING -> "Syncing..."
    SyncIndicator.ERROR -> "Sync error"
}
