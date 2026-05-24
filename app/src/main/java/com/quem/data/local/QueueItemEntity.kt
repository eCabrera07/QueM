package com.quem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(tableName = "queue_items")
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String?,
    val dueDate: LocalDate?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val dismissedAt: Instant?,
    val syncState: String
)
