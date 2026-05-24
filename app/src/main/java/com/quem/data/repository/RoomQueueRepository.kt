package com.quem.data.repository

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.Clock
import com.quem.data.local.QueueDao
import com.quem.data.local.toDomain
import com.quem.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomQueueRepository(
    private val dao: QueueDao,
    private val clock: Clock,
    private val idProvider: () -> String
) : QueueRepository {
    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        dao.observeItemsByStatus(status.name).map { items -> items.map { it.toDomain() } }

    override fun observeItem(id: String): Flow<QueueItem?> =
        dao.observeItem(id).map { it?.toDomain() }

    override suspend fun createItem(title: String, description: String?): QueueItem {
        val now = clock.now()
        val item = QueueItem(
            id = idProvider(),
            driveId = null,
            title = title.trim(),
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            status = QueueStatus.QUEUED,
            priority = null,
            dueDate = null,
            tags = emptyList(),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )
        dao.upsertItem(item.toEntity())
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? {
        val now = clock.now()
        val updatedRows = dao.updateStatus(
            id = id,
            status = status.name,
            updatedAt = now,
            completedAt = if (status == QueueStatus.DONE) now else null,
            dismissedAt = if (status == QueueStatus.DISMISSED) now else null
        )
        if (updatedRows == 0) return null

        return dao.observeItem(id).first()?.toDomain()
    }
}
