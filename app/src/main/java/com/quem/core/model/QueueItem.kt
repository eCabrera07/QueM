package com.quem.core.model

import java.time.Instant
import java.time.LocalDate

data class QueueItem(
    val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: QueueStatus,
    val priority: Priority?,
    val dueDate: LocalDate?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val dismissedAt: Instant?,
    val syncState: SyncState
)
