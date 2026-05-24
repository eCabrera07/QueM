package com.quem.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "history_entries",
    foreignKeys = [
        ForeignKey(
            entity = QueueItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueItemId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("queueItemId")]
)
data class HistoryEntryEntity(
    @PrimaryKey val id: String,
    val queueItemId: String,
    val message: String,
    val kind: String,
    val createdAt: Instant
)
