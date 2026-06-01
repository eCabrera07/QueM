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
    val syncState: SyncState,
    val sharedDriveFileId: String?,     // Drive file ID of QueM/shared-{id}.json; null = not shared
    val sharedWith: List<String>        // recipient emails; empty = not shared
)
