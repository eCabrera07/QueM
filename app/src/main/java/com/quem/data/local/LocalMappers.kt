package com.quem.data.local

import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState

fun QueueItemEntity.toDomain(): QueueItem = QueueItem(
    id = id,
    driveId = driveId,
    title = title,
    description = description,
    status = QueueStatus.valueOf(status),
    priority = priority?.let(Priority::valueOf),
    dueDate = dueDate,
    tags = tags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    dismissedAt = dismissedAt,
    syncState = SyncState.valueOf(syncState)
)

fun QueueItem.toEntity(): QueueItemEntity = QueueItemEntity(
    id = id,
    driveId = driveId,
    title = title,
    description = description,
    status = status.name,
    priority = priority?.name,
    dueDate = dueDate,
    tags = tags,
    createdAt = createdAt,
    updatedAt = updatedAt,
    completedAt = completedAt,
    dismissedAt = dismissedAt,
    syncState = syncState.name
)

fun AttachmentEntity.toDomain(): Attachment = Attachment(
    id = id,
    queueItemId = queueItemId,
    type = AttachmentType.valueOf(type),
    displayName = displayName,
    textContent = textContent,
    url = url,
    driveFileId = driveFileId,
    mimeType = mimeType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = SyncState.valueOf(syncState)
)

fun Attachment.toEntity(): AttachmentEntity = AttachmentEntity(
    id = id,
    queueItemId = queueItemId,
    type = type.name,
    displayName = displayName,
    textContent = textContent,
    url = url,
    driveFileId = driveFileId,
    mimeType = mimeType,
    createdAt = createdAt,
    updatedAt = updatedAt,
    syncState = syncState.name
)

fun HistoryEntryEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    queueItemId = queueItemId,
    message = message,
    kind = runCatching { HistoryKind.valueOf(kind) }.getOrElse { HistoryKind.NOTE },
    createdAt = createdAt
)
