package com.quem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val queueItemId: String,
    val message: String,
    val kind: String,
    val createdAt: Instant
)
