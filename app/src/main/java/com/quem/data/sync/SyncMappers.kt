package com.quem.data.sync

import com.quem.core.model.Attachment
import com.quem.core.model.HistoryEntry
import com.quem.core.model.QueueItem

fun QueueItem.toExportable() = ExportableQueueItem(
    id          = id,
    title       = title,
    status      = status.name,
    driveId     = driveId,
    description = description,
    priority    = priority?.name,
    dueDate     = dueDate?.toString(),
    tags        = tags,
    createdAt   = createdAt.toString(),
    updatedAt   = updatedAt.toString(),
    completedAt = completedAt?.toString(),
    dismissedAt = dismissedAt?.toString()
)

fun Attachment.toMetadata() = MetadataAttachment(
    id          = id,
    queueItemId = queueItemId,
    type        = type.name,
    displayName = displayName,
    textContent = textContent,
    url         = url,
    driveFileId = driveFileId,
    mimeType    = mimeType,
    createdAt   = createdAt.toString(),
    updatedAt   = updatedAt.toString()
)

fun HistoryEntry.toMetadata() = MetadataHistoryEntry(
    id          = id,
    queueItemId = queueItemId,
    message     = message,
    kind        = kind.name,
    createdAt   = createdAt.toString()
)
