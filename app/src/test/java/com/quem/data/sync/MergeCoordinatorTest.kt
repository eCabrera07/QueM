package com.quem.data.sync

import com.quem.core.model.SyncState
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueDao
import com.quem.data.local.QueueItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MergeCoordinatorTest {

    @Test
    fun mergeAppliesNewItemFromSnapshot() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()

        MergeCoordinator(dao).merge(snapshot(items = listOf(metadataItem("server-1", now))))

        assertEquals(1, dao.items.size)
        assertEquals("server-1", dao.items.single().id)
        assertEquals(SyncState.SYNCED.name, dao.items.single().syncState)
    }

    @Test
    fun mergeAppliesNewerServerItem() = runTest {
        val earlier = Instant.parse("2026-05-29T10:00:00Z")
        val later   = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.items.add(localItem(id = "item-1", updatedAt = earlier, title = "Old title"))

        MergeCoordinator(dao).merge(snapshot(items = listOf(
            metadataItem("item-1", later).copy(title = "New title")
        )))

        assertEquals("New title", dao.items.single().title)
    }

    @Test
    fun mergePreservesNewerLocalItem() = runTest {
        val earlier = Instant.parse("2026-05-29T10:00:00Z")
        val later   = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.items.add(localItem(id = "item-1", updatedAt = later, title = "Local title"))

        MergeCoordinator(dao).merge(snapshot(items = listOf(
            metadataItem("item-1", earlier).copy(title = "Server title")
        )))

        assertEquals("Local title", dao.items.single().title)
    }

    @Test
    fun mergeDoesNotUpsertItemWithSameUpdatedAt() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.items.add(localItem(id = "item-1", updatedAt = now, title = "Local title"))

        MergeCoordinator(dao).merge(snapshot(items = listOf(
            metadataItem("item-1", now).copy(title = "Server title")
        )))

        // same updatedAt → local wins, not overwritten
        assertEquals("Local title", dao.items.single().title)
    }

    @Test
    fun mergeAppliesNewAttachmentFromSnapshot() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()

        MergeCoordinator(dao).merge(snapshot(attachments = listOf(metadataAttachment("att-1", "item-1", now))))

        assertEquals(1, dao.attachments.size)
        assertEquals("att-1", dao.attachments.single().id)
        assertEquals(SyncState.SYNCED.name, dao.attachments.single().syncState)
    }

    @Test
    fun mergePreservesNewerLocalAttachment() = runTest {
        val earlier = Instant.parse("2026-05-29T10:00:00Z")
        val later   = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.attachments.add(localAttachment(id = "att-1", queueItemId = "item-1", updatedAt = later))

        MergeCoordinator(dao).merge(snapshot(attachments = listOf(metadataAttachment("att-1", "item-1", earlier))))

        assertEquals(1, dao.attachments.size)  // not duplicated
        assertEquals(later, dao.attachments.single().updatedAt)  // local preserved
    }

    @Test
    fun mergeAppendsNewHistoryEntry() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()

        MergeCoordinator(dao).merge(snapshot(history = listOf(metadataHistory("hist-1", "item-1", now))))

        assertEquals(1, dao.history.size)
        assertEquals("hist-1", dao.history.single().id)
    }

    @Test
    fun mergeSkipsExistingHistoryEntry() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.history.add(HistoryEntryEntity("hist-1", "item-1", "Created", "STATUS_CHANGE", now))

        MergeCoordinator(dao).merge(snapshot(history = listOf(metadataHistory("hist-1", "item-1", now))))

        assertEquals(1, dao.history.size)  // not duplicated
    }

    @Test
    fun mergeContinuesAfterMalformedSnapshotEntry() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        val badItem = MetadataQueueItem(
            id = "bad", driveId = null, title = "Bad",
            description = null, status = "QUEUED", priority = null,
            dueDate = null, tags = emptyList(),
            createdAt = now.toString(),
            updatedAt = "not-a-date",    // malformed — DateTimeParseException
            completedAt = null, dismissedAt = null
        )

        MergeCoordinator(dao).merge(snapshot(items = listOf(badItem, metadataItem("good-1", now))))

        assertEquals(1, dao.items.size)
        assertEquals("good-1", dao.items.single().id)
    }
}

// ---- Fakes ----

private class FakeMergeDao : QueueDao {
    val items       = mutableListOf<QueueItemEntity>()
    val attachments = mutableListOf<AttachmentEntity>()
    val history     = mutableListOf<HistoryEntryEntity>()

    override suspend fun allItems(): List<QueueItemEntity> = items.toList()
    override suspend fun allAttachments(): List<AttachmentEntity> = attachments.toList()
    override suspend fun allHistory(): List<HistoryEntryEntity> = history.toList()

    override suspend fun upsertItem(item: QueueItemEntity) {
        items.removeAll { it.id == item.id }
        items.add(item)
    }

    override suspend fun upsertAttachment(attachment: AttachmentEntity) {
        attachments.removeAll { it.id == attachment.id }
        attachments.add(attachment)
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) {
        // MergeCoordinator only calls this when id is not already present
        history.add(entry)
    }

    // Unused — MergeCoordinator does not call these
    override fun observeItemsByStatus(s: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun searchItems(s: List<String>, q: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun observeItem(id: String): Flow<QueueItemEntity?> = throw UnsupportedOperationException()
    override suspend fun pendingItems(): List<QueueItemEntity> = throw UnsupportedOperationException()
    override suspend fun markItemsSynced() = throw UnsupportedOperationException()
    override suspend fun markAttachmentsSynced() = throw UnsupportedOperationException()
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

private fun snapshot(
    now: Instant = Instant.parse("2026-05-29T12:00:00Z"),
    items: List<MetadataQueueItem> = emptyList(),
    attachments: List<MetadataAttachment> = emptyList(),
    history: List<MetadataHistoryEntry> = emptyList()
) = MetadataSnapshot(version = 1, exportedAt = now.toString(),
    items = items, attachments = attachments, history = history)

private fun metadataItem(id: String, updatedAt: Instant) = MetadataQueueItem(
    id = id, driveId = null, title = "Item $id",
    description = null, status = "QUEUED", priority = null,
    dueDate = null, tags = emptyList(),
    createdAt = updatedAt.toString(), updatedAt = updatedAt.toString(),
    completedAt = null, dismissedAt = null
)

private fun metadataAttachment(id: String, queueItemId: String, updatedAt: Instant) = MetadataAttachment(
    id = id, queueItemId = queueItemId, type = "TEXT",
    displayName = "Attachment $id", textContent = null,
    url = null, driveFileId = null, mimeType = null,
    createdAt = updatedAt.toString(), updatedAt = updatedAt.toString()
)

private fun metadataHistory(id: String, queueItemId: String, createdAt: Instant) = MetadataHistoryEntry(
    id = id, queueItemId = queueItemId,
    message = "Created", kind = "STATUS_CHANGE",
    createdAt = createdAt.toString()
)

private fun localItem(id: String, updatedAt: Instant, title: String = "Item $id") = QueueItemEntity(
    id = id, driveId = null, title = title, description = null,
    status = "QUEUED", priority = null, dueDate = null, tags = emptyList(),
    createdAt = updatedAt, updatedAt = updatedAt, completedAt = null, dismissedAt = null,
    syncState = SyncState.PENDING_SYNC.name, sharedDriveFileId = null, sharedWith = emptyList()
)

private fun localAttachment(id: String, queueItemId: String, updatedAt: Instant) = AttachmentEntity(
    id = id, queueItemId = queueItemId, type = "TEXT",
    displayName = "Attachment $id", textContent = null,
    url = null, driveFileId = null, mimeType = null,
    createdAt = updatedAt, updatedAt = updatedAt,
    syncState = SyncState.PENDING_SYNC.name
)
