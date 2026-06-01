package com.quem.data.sync

import com.quem.core.model.SyncState
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueItemEntity
import java.time.Instant
import java.time.LocalDate

fun MetadataQueueItem.toEntity() = QueueItemEntity(
    id          = id,
    driveId     = driveId,
    title       = title,
    description = description,
    status      = status,
    priority    = priority,
    dueDate     = dueDate?.let { LocalDate.parse(it) },
    tags        = tags,
    createdAt   = Instant.parse(createdAt),
    updatedAt   = Instant.parse(updatedAt),
    completedAt       = completedAt?.let { Instant.parse(it) },
    dismissedAt       = dismissedAt?.let { Instant.parse(it) },
    syncState         = SyncState.SYNCED.name,
    sharedDriveFileId = null,
    sharedWith        = emptyList()
)

fun MetadataAttachment.toEntity() = AttachmentEntity(
    id          = id,
    queueItemId = queueItemId,
    type        = type,
    displayName = displayName,
    textContent = textContent,
    url         = url,
    driveFileId = driveFileId,
    mimeType    = mimeType,
    createdAt   = Instant.parse(createdAt),
    updatedAt   = Instant.parse(updatedAt),
    syncState   = SyncState.SYNCED.name
)

fun MetadataHistoryEntry.toEntity() = HistoryEntryEntity(
    id          = id,
    queueItemId = queueItemId,
    message     = message,
    kind        = kind,
    createdAt   = Instant.parse(createdAt)
)
