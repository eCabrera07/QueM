package com.quem.data.repository

import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueDao
import com.quem.data.local.QueueItemEntity
import com.quem.data.local.toDomain
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class RoomQueueRepositoryTest {
    @Test
    fun observeItemsReturnsItemsWithMatchingStatus() = runTest {
        val dao = FakeQueueDao()
        val ids = mutableListOf("item-1", "item-2")
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { ids.removeFirst() }
        )
        val queued = repository.createItem(title = "Read contract", description = null)
        repository.createItem(title = "Draft summary", description = null)
        repository.changeStatus("item-2", QueueStatus.DONE)

        val items = repository.observeItems(QueueStatus.QUEUED).first()

        assertEquals(listOf(queued), items)
    }

    @Test
    fun observeItemReturnsItemById() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )
        val created = repository.createItem(title = "Read contract", description = null)

        val item = repository.observeItem("item-1").first()

        assertEquals(created, item)
    }

    @Test
    fun createItemCreatesQueuedPendingSyncItem() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )

        val created = repository.createItem(title = "Read contract", description = null)

        assertEquals("item-1", created.id)
        assertEquals("Read contract", created.title)
        assertEquals(QueueStatus.QUEUED, created.status)
        assertEquals(SyncState.PENDING_SYNC, created.syncState)
        assertEquals(created, dao.items.single())
    }

    @Test
    fun createItemTrimsTitleAndNormalizesBlankDescription() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )

        val created = repository.createItem(title = "  Read contract  ", description = "   ")

        assertEquals("Read contract", created.title)
        assertNull(created.description)
        assertEquals(created, dao.items.single())
    }

    @Test
    fun changeStatusAppliesQueueRulesAndPersistsItem() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )
        repository.createItem(title = "Read contract", description = "Legal")

        val changed = repository.changeStatus("item-1", QueueStatus.DONE)

        requireNotNull(changed)
        assertEquals(QueueStatus.DONE, changed.status)
        assertEquals(Instant.parse("2026-05-23T12:00:00Z"), changed.updatedAt)
        assertEquals(Instant.parse("2026-05-23T12:00:00Z"), changed.completedAt)
        assertNull(changed.dismissedAt)
        assertEquals(SyncState.PENDING_SYNC, changed.syncState)
        assertEquals(changed, dao.items.single())
    }

    @Test
    fun changeStatusNoOpsWhenItemDoesNotExist() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )

        val changed = repository.changeStatus("missing", QueueStatus.DONE)

        assertNull(changed)
        assertEquals(emptyList<QueueItem>(), dao.items)
    }

    @Test
    fun changeStatusPreservesFieldsOutsideStatusPatch() = runTest {
        val dao = FakeQueueDao()
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
            idProvider = { "item-1" }
        )
        dao.upsertItem(
            QueueItemEntity(
                id = "item-1",
                driveId = "drive-1",
                title = "Read contract",
                description = "Legal",
                status = QueueStatus.QUEUED.name,
                priority = Priority.HIGH.name,
                dueDate = LocalDate.parse("2026-05-24"),
                tags = listOf("legal", "urgent"),
                createdAt = Instant.parse("2026-05-22T12:00:00Z"),
                updatedAt = Instant.parse("2026-05-22T12:00:00Z"),
                completedAt = null,
                dismissedAt = null,
                syncState = SyncState.SYNCED.name
            )
        )

        val changed = repository.changeStatus("item-1", QueueStatus.DONE)

        requireNotNull(changed)
        assertEquals("drive-1", changed.driveId)
        assertEquals("Read contract", changed.title)
        assertEquals("Legal", changed.description)
        assertEquals(Priority.HIGH, changed.priority)
        assertEquals(LocalDate.parse("2026-05-24"), changed.dueDate)
        assertEquals(listOf("legal", "urgent"), changed.tags)
        assertEquals(Instant.parse("2026-05-22T12:00:00Z"), changed.createdAt)
        assertEquals(QueueStatus.DONE, changed.status)
        assertEquals(Instant.parse("2026-05-23T12:00:00Z"), changed.updatedAt)
        assertEquals(Instant.parse("2026-05-23T12:00:00Z"), changed.completedAt)
        assertNull(changed.dismissedAt)
        assertEquals(SyncState.PENDING_SYNC, changed.syncState)
    }

    @Test
    fun addAttachmentsCreatesPendingSyncAttachmentsAndObservesByParent() = runTest {
        val dao = FakeQueueDao()
        val ids = mutableListOf("text-1", "link-1", "drive-file-1", "drive-folder-1")
        val now = Instant.parse("2026-05-23T12:00:00Z")
        val repository = RoomQueueRepository(
            dao = dao,
            clock = FixedClock(now),
            idProvider = { ids.removeFirst() }
        )

        repository.addTextAttachment("item-1", "  Note  ", "Remember this")
        repository.addLinkAttachment("item-1", "  Spec  ", "https://example.com/spec")
        repository.addDriveAttachment(
            queueItemId = "item-1",
            title = " Contract ",
            driveFileId = "drive-file-id",
            mimeType = "application/pdf",
            isFolder = false
        )
        repository.addDriveAttachment(
            queueItemId = "item-2",
            title = " Folder ",
            driveFileId = "drive-folder-id",
            mimeType = null,
            isFolder = true
        )

        val attachments = repository.observeAttachments("item-1").first()
        val folderAttachments = repository.observeAttachments("item-2").first()

        assertEquals(
            listOf(
                Attachment(
                    id = "text-1",
                    queueItemId = "item-1",
                    type = AttachmentType.TEXT,
                    displayName = "Note",
                    textContent = "Remember this",
                    url = null,
                    driveFileId = null,
                    mimeType = null,
                    createdAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING_SYNC
                ),
                Attachment(
                    id = "link-1",
                    queueItemId = "item-1",
                    type = AttachmentType.LINK,
                    displayName = "Spec",
                    textContent = null,
                    url = "https://example.com/spec",
                    driveFileId = null,
                    mimeType = null,
                    createdAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING_SYNC
                ),
                Attachment(
                    id = "drive-file-1",
                    queueItemId = "item-1",
                    type = AttachmentType.DRIVE_FILE,
                    displayName = "Contract",
                    textContent = null,
                    url = null,
                    driveFileId = "drive-file-id",
                    mimeType = "application/pdf",
                    createdAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING_SYNC
                )
            ),
            attachments
        )
        assertEquals(
            listOf(
                Attachment(
                    id = "drive-folder-1",
                    queueItemId = "item-2",
                    type = AttachmentType.DRIVE_FOLDER,
                    displayName = "Folder",
                    textContent = null,
                    url = null,
                    driveFileId = "drive-folder-id",
                    mimeType = null,
                    createdAt = now,
                    updatedAt = now,
                    syncState = SyncState.PENDING_SYNC
                )
            ),
            folderAttachments
        )
    }
}

private class FakeQueueDao : QueueDao {
    private val entities = MutableStateFlow<List<QueueItemEntity>>(emptyList())
    private val attachmentEntities = MutableStateFlow<List<AttachmentEntity>>(emptyList())

    val items
        get() = entities.value.map { it.toDomain() }

    override fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>> =
        entities.map { items -> items.filter { it.status == status } }

    override fun observeItem(id: String): Flow<QueueItemEntity?> =
        entities.map { items -> items.singleOrNull { it.id == id } }

    override suspend fun pendingItems(): List<QueueItemEntity> =
        entities.value.filter { it.syncState == SyncState.PENDING_SYNC.name }

    override suspend fun upsertItem(item: QueueItemEntity) {
        entities.value = entities.value.filterNot { it.id == item.id } + item
    }

    override suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Instant,
        completedAt: Instant?,
        dismissedAt: Instant?
    ): Int {
        var updatedRows = 0
        entities.value = entities.value.map { item ->
            if (item.id == id) {
                updatedRows++
                item.copy(
                    status = status,
                    updatedAt = updatedAt,
                    completedAt = completedAt,
                    dismissedAt = dismissedAt,
                    syncState = SyncState.PENDING_SYNC.name
                )
            } else {
                item
            }
        }
        return updatedRows
    }

    override suspend fun upsertAttachment(attachment: AttachmentEntity) {
        attachmentEntities.value = attachmentEntities.value.filterNot { it.id == attachment.id } + attachment
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) = Unit

    override fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>> =
        attachmentEntities.map { attachments -> attachments.filter { it.queueItemId == queueItemId } }

    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>> =
        MutableStateFlow(emptyList())
}
