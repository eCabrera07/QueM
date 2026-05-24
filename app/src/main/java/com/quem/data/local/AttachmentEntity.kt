package com.quem.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "attachments",
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
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val queueItemId: String,
    val type: String,
    val displayName: String,
    val textContent: String?,
    val url: String?,
    val driveFileId: String?,
    val mimeType: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: String
)
