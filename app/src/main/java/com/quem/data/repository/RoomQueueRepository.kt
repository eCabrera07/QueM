package com.quem.data.repository

import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
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

    override fun searchArchive(query: String): Flow<List<QueueItem>> =
        dao.searchItems(
            statuses = QueueFilters.archiveStatuses.map { it.name },
            query = query.trim()
        ).map { items -> items.map { it.toDomain() } }

    override fun observeItem(id: String): Flow<QueueItem?> =
        dao.observeItem(id).map { it?.toDomain() }

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        dao.observeAttachments(queueItemId).map { attachments -> attachments.map { it.toDomain() } }

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

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) {
        if (text.isBlank()) return

        addAttachment(
            queueItemId = queueItemId,
            title = title,
            type = AttachmentType.TEXT,
            textContent = text,
            url = null,
            driveFileId = null,
            mimeType = null
        )
    }

    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) {
        val normalizedUrl = url.trim()
        if (normalizedUrl.isBlank()) return

        addAttachment(
            queueItemId = queueItemId,
            title = title,
            type = AttachmentType.LINK,
            textContent = null,
            url = normalizedUrl,
            driveFileId = null,
            mimeType = null
        )
    }

    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) {
        val normalizedDriveFileId = driveFileId.trim()
        if (normalizedDriveFileId.isBlank()) return

        addAttachment(
            queueItemId = queueItemId,
            title = title,
            type = if (isFolder) AttachmentType.DRIVE_FOLDER else AttachmentType.DRIVE_FILE,
            textContent = null,
            url = null,
            driveFileId = normalizedDriveFileId,
            mimeType = mimeType?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    private suspend fun addAttachment(
        queueItemId: String,
        title: String,
        type: AttachmentType,
        textContent: String?,
        url: String?,
        driveFileId: String?,
        mimeType: String?
    ) {
        val displayName = title.trim()
        if (displayName.isBlank()) return
        if (dao.observeItem(queueItemId).first() == null) return

        val now = clock.now()
        val attachment = Attachment(
            id = idProvider(),
            queueItemId = queueItemId,
            type = type,
            displayName = displayName,
            textContent = textContent,
            url = url,
            driveFileId = driveFileId,
            mimeType = mimeType,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_SYNC
        )
        dao.upsertAttachment(attachment.toEntity())
    }
}
