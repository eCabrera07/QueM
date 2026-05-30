package com.quem.data.sync

import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryKind
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueDao
import com.quem.data.local.QueueItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

class SyncCoordinatorTest {

    @Test
    fun syncUploadsSnapshotContainingAllItemsAttachmentsAndHistory() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao().apply {
            items = listOf(queueItemEntity(id = "item-1", now = now))
            attachments = listOf(attachmentEntity(id = "att-1", queueItemId = "item-1", now = now))
            history = listOf(historyEntity(id = "hist-1", queueItemId = "item-1", now = now))
        }
        val driveGateway = FakeCoordinatorDriveGateway()
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        assertEquals(1, driveGateway.uploadedContents.size)
        val snapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
        assertEquals(1, snapshot.items.size)
        assertEquals("item-1", snapshot.items.single().id)
        assertEquals(1, snapshot.attachments.size)
        assertEquals("att-1", snapshot.attachments.single().id)
        assertEquals(1, snapshot.history.size)
        assertEquals("hist-1", snapshot.history.single().id)
    }

    @Test
    fun syncMarksItemsAndAttachmentsSyncedAfterSuccessfulUpload() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao()
        val coordinator = SyncCoordinator(dao, SyncManager(FakeCoordinatorDriveGateway()), FixedClock(now))

        coordinator.sync()

        assertEquals(1, dao.markItemsSyncedCalls)
        assertEquals(1, dao.markAttachmentsSyncedCalls)
    }

    @Test
    fun syncDoesNotMarkSyncedIfUploadThrows() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao()
        val driveGateway = FakeCoordinatorDriveGateway(throwOnUpload = IOException("Network error"))
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        try {
            coordinator.sync()
        } catch (_: IOException) {
            // expected — let it propagate to the worker
        }

        assertEquals(0, dao.markItemsSyncedCalls)
        assertEquals(0, dao.markAttachmentsSyncedCalls)
    }

    @Test
    fun syncUploadsToCorrectFolderAndFileName() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val driveGateway = FakeCoordinatorDriveGateway()
        val coordinator = SyncCoordinator(FakeCoordinatorDao(), SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        assertEquals("QueM", driveGateway.lastFolderName)
        assertEquals("queue-metadata.json", driveGateway.lastFileName)
    }
}

// ---- Fakes ----

private class FakeCoordinatorDriveGateway(
    private val throwOnUpload: Exception? = null
) : DriveGateway {
    val uploadedContents = mutableListOf<String>()
    var lastFolderName: String? = null
    var lastFileName: String? = null

    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) {
        throwOnUpload?.let { throw it }
        lastFolderName = folderName
        lastFileName = fileName
        uploadedContents.add(content)
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? = null
}

private class FakeCoordinatorDao : QueueDao {
    var items: List<QueueItemEntity> = emptyList()
    var attachments: List<AttachmentEntity> = emptyList()
    var history: List<HistoryEntryEntity> = emptyList()
    var markItemsSyncedCalls = 0
    var markAttachmentsSyncedCalls = 0

    override suspend fun allItems(): List<QueueItemEntity> = items
    override suspend fun allAttachments(): List<AttachmentEntity> = attachments
    override suspend fun allHistory(): List<HistoryEntryEntity> = history
    override suspend fun markItemsSynced() { markItemsSyncedCalls++ }
    override suspend fun markAttachmentsSynced() { markAttachmentsSyncedCalls++ }

    // Unused — SyncCoordinator does not call these
    override fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun searchItems(statuses: List<String>, query: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun observeItem(id: String): Flow<QueueItemEntity?> = throw UnsupportedOperationException()
    override suspend fun pendingItems(): List<QueueItemEntity> = throw UnsupportedOperationException()
    override suspend fun upsertItem(item: QueueItemEntity) = throw UnsupportedOperationException()
    override suspend fun updateStatus(id: String, status: String, updatedAt: Instant, completedAt: Instant?, dismissedAt: Instant?): Int = throw UnsupportedOperationException()
    override suspend fun upsertAttachment(attachment: AttachmentEntity) = throw UnsupportedOperationException()
    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) = throw UnsupportedOperationException()
    override fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>> = throw UnsupportedOperationException()
    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>> = throw UnsupportedOperationException()
}

// ---- Builders ----

private fun queueItemEntity(id: String, now: Instant) = QueueItemEntity(
    id          = id,
    driveId     = null,
    title       = "Item $id",
    description = null,
    status      = QueueStatus.QUEUED.name,
    priority    = null,
    dueDate     = null,
    tags        = emptyList(),
    createdAt   = now,
    updatedAt   = now,
    completedAt = null,
    dismissedAt = null,
    syncState   = SyncState.PENDING_SYNC.name
)

private fun attachmentEntity(id: String, queueItemId: String, now: Instant) = AttachmentEntity(
    id          = id,
    queueItemId = queueItemId,
    type        = AttachmentType.TEXT.name,
    displayName = "Attachment $id",
    textContent = "Content",
    url         = null,
    driveFileId = null,
    mimeType    = null,
    createdAt   = now,
    updatedAt   = now,
    syncState   = SyncState.PENDING_SYNC.name
)

private fun historyEntity(id: String, queueItemId: String, now: Instant) = HistoryEntryEntity(
    id          = id,
    queueItemId = queueItemId,
    message     = "Created",
    kind        = HistoryKind.STATUS_CHANGE.name,
    createdAt   = now
)
