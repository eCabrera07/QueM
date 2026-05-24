package com.quem.data.local

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
