package com.quem.core.model

import java.time.Instant

enum class HistoryKind {
    NOTE,
    STATUS_CHANGE,
    ATTACHMENT_ADDED,
    ATTACHMENT_REMOVED,
    EDIT
}

data class HistoryEntry(
    val id: String,
    val queueItemId: String,
    val message: String,
    val kind: HistoryKind,
    val createdAt: Instant
)
