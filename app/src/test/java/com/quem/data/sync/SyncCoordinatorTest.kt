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
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

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
    fun syncExcludesLocalFileAttachmentsFromSnapshot() = runTest {
        val now = Instant.parse("2026-06-02T10:00:00Z")
        val dao = FakeCoordinatorDao().apply {
            items = listOf(queueItemEntity(id = "item-1", now = now))
            attachments = listOf(
                attachmentEntity(id = "synced-att", queueItemId = "item-1", now = now),       // TEXT — included
                attachmentEntity(id = "local-att",  queueItemId = "item-1", now = now)         // LOCAL_FILE — excluded
                    .copy(type = AttachmentType.LOCAL_FILE.name, syncState = SyncState.PENDING_UPLOAD.name)
            )
        }
        val driveGateway = FakeCoordinatorDriveGateway()
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        val snapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
        assertEquals(1, snapshot.attachments.size)
        assertEquals("synced-att", snapshot.attachments.single().id)
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
            fail("Expected IOException")
        } catch (_: IOException) {
            // Expected
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

    @Test
    fun syncMergesDownloadedDataBeforeUploading() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao()  // starts empty
        val serverSnapshot = MetadataSnapshot(
            version = 1,
            exportedAt = now.toString(),
            items = listOf(metadataItem("server-item", now)),
            attachments = emptyList(),
            history = emptyList()
        )
        val driveGateway = FakeCoordinatorDriveGateway(
            downloadContent = MetadataSerializer.encode(serverSnapshot)
        )
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        val uploadedSnapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
        assertEquals(1, uploadedSnapshot.items.size)
        assertEquals("server-item", uploadedSnapshot.items.single().id)
    }

    @Test
    fun syncSkipsMergeAndUploadsNormallyWhenNoSnapshotOnDrive() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao().apply {
            items = listOf(queueItemEntity(id = "local-item", now = now))
        }
        // downloadContent = null (default) → no file on Drive
        val driveGateway = FakeCoordinatorDriveGateway()
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        val uploadedSnapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
        assertEquals(1, uploadedSnapshot.items.size)
        assertEquals("local-item", uploadedSnapshot.items.single().id)
    }
}

// ---- Fakes ----

private class FakeCoordinatorDriveGateway(
    private val throwOnUpload: Exception? = null,
    private val downloadContent: String? = null
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

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? =
        downloadContent
}

private class FakeCoordinatorDao : QueueDao {
    private val _items       = mutableListOf<QueueItemEntity>()
    private val _attachments = mutableListOf<AttachmentEntity>()
    private val _history     = mutableListOf<HistoryEntryEntity>()

    var items: List<QueueItemEntity>
        get() = _items.toList()
        set(value) { _items.clear(); _items.addAll(value) }

    var attachments: List<AttachmentEntity>
        get() = _attachments.toList()
        set(value) { _attachments.clear(); _attachments.addAll(value) }

    var history: List<HistoryEntryEntity>
        get() = _history.toList()
        set(value) { _history.clear(); _history.addAll(value) }

    var markItemsSyncedCalls = 0
    var markAttachmentsSyncedCalls = 0

    override suspend fun allItems(): List<QueueItemEntity>        = _items.toList()
    override suspend fun allAttachments(): List<AttachmentEntity> = _attachments.toList()
    override suspend fun allHistory(): List<HistoryEntryEntity>   = _history.toList()
    override suspend fun markItemsSynced()       { markItemsSyncedCalls++ }
    override suspend fun markAttachmentsSynced() { markAttachmentsSyncedCalls++ }

    override suspend fun upsertItem(item: QueueItemEntity) {
        _items.removeAll { it.id == item.id }
        _items.add(item)
    }

    override suspend fun upsertAttachment(attachment: AttachmentEntity) {
        _attachments.removeAll { it.id == attachment.id }
        _attachments.add(attachment)
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) {
        if (_history.none { it.id == entry.id }) _history.add(entry)
    }

    // Unused — throw to detect unintended calls
    override fun observeItemsByStatus(s: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun searchItems(s: List<String>, q: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun observeItem(id: String): Flow<QueueItemEntity?> = throw UnsupportedOperationException()
    override suspend fun pendingItems(): List<QueueItemEntity> = throw UnsupportedOperationException()
    override suspend fun updateStatus(id: String, status: String, updatedAt: Instant, completedAt: Instant?, dismissedAt: Instant?): Int = throw UnsupportedOperationException()
    override suspend fun updateItemFields(id: String, title: String, description: String?, priority: String?, dueDate: LocalDate?, updatedAt: Instant): Int = throw UnsupportedOperationException()
    override suspend fun deleteAttachment(id: String) = throw UnsupportedOperationException()
    override suspend fun updateAttachmentTitle(id: String, title: String, updatedAt: Instant) = throw UnsupportedOperationException()
    override suspend fun deleteHistoryEntry(id: String) = throw UnsupportedOperationException()
    override suspend fun updateShareInfo(id: String, sharedDriveFileId: String, sharedWith: List<String>, updatedAt: java.time.Instant) = throw UnsupportedOperationException()
    override fun observeAttachments(id: String): Flow<List<AttachmentEntity>> = throw UnsupportedOperationException()
    override fun observeHistory(id: String): Flow<List<HistoryEntryEntity>> = throw UnsupportedOperationException()
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
    completedAt       = null,
    dismissedAt       = null,
    syncState         = SyncState.PENDING_SYNC.name,
    sharedDriveFileId = null,
    sharedWith        = emptyList()
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

private fun metadataItem(id: String, updatedAt: Instant) = MetadataQueueItem(
    id = id, driveId = null, title = "Item $id",
    description = null, status = "QUEUED", priority = null,
    dueDate = null, tags = emptyList(),
    createdAt = updatedAt.toString(), updatedAt = updatedAt.toString(),
    completedAt = null, dismissedAt = null
)
