package com.quem.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "attachments")
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
