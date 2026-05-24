package com.quem.core.model

import java.time.Instant

data class Attachment(
    val id: String,
    val queueItemId: String,
    val type: AttachmentType,
    val displayName: String,
    val textContent: String?,
    val url: String?,
    val driveFileId: String?,
    val mimeType: String?,
    val createdAt: Instant,
    val updatedAt: Instant,
    val syncState: SyncState
)
