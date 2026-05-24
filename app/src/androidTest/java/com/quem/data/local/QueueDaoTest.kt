package com.quem.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class QueueDaoTest {
    private lateinit var db: QueMDatabase
    private lateinit var dao: QueueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            QueMDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.queueDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun statusQueryExcludesDismissedFromActiveWhenCallerRequestsQueued() = runBlocking {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        dao.upsertItem(queueItem(id = "queued", title = "Queued", status = "QUEUED", now = now))
        dao.upsertItem(queueItem(id = "dismissed", title = "Dismissed", status = "DISMISSED", dismissedAt = now, now = now))

        val queued = dao.observeItemsByStatus("QUEUED").first()

        assertEquals(listOf("queued"), queued.map { it.id })
    }

    @Test
    fun tagsRoundTripThroughRoomWithoutDelimiterLoss() = runBlocking {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        val tags = listOf("alpha\u001Fbeta", """{"kind":"json"}""", "", " spaced ")
        dao.upsertItem(queueItem(id = "tagged", title = "Tagged", tags = tags, now = now))

        val item = dao.observeItem("tagged").first()

        assertEquals(tags, item?.tags)
    }

    @Test
    fun updateStatusPreservesUnrelatedFieldsInRoom() = runBlocking {
        val createdAt = Instant.parse("2026-05-23T12:00:00Z")
        val previousUpdatedAt = Instant.parse("2026-05-23T12:30:00Z")
        val changedAt = Instant.parse("2026-05-23T13:00:00Z")
        val tags = listOf("work", "needs-review")
        dao.upsertItem(
            queueItem(
                id = "status-update",
                driveId = "drive-123",
                title = "Preserve me",
                description = "Do not overwrite non-status fields.",
                status = "QUEUED",
                priority = "HIGH",
                dueDate = LocalDate.parse("2026-05-30"),
                tags = tags,
                now = createdAt,
                updatedAt = previousUpdatedAt,
                syncState = "SYNCED"
            )
        )

        val updatedRows = dao.updateStatus(
            id = "status-update",
            status = "DONE",
            updatedAt = changedAt,
            completedAt = changedAt,
            dismissedAt = null
        )
        val item = dao.observeItem("status-update").first()

        assertEquals(1, updatedRows)
        assertEquals("drive-123", item?.driveId)
        assertEquals("Preserve me", item?.title)
        assertEquals("Do not overwrite non-status fields.", item?.description)
        assertEquals("HIGH", item?.priority)
        assertEquals(LocalDate.parse("2026-05-30"), item?.dueDate)
        assertEquals(tags, item?.tags)
        assertEquals(createdAt, item?.createdAt)
        assertEquals("DONE", item?.status)
        assertEquals(changedAt, item?.updatedAt)
        assertEquals(changedAt, item?.completedAt)
        assertEquals(null, item?.dismissedAt)
        assertEquals("PENDING_SYNC", item?.syncState)
    }

    @Test
    fun deletingQueueItemCascadesToAttachmentsAndHistory() = runBlocking {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        dao.upsertItem(queueItem(id = "parent", title = "Parent", now = now))
        dao.upsertAttachment(attachment(id = "attachment", queueItemId = "parent", now = now))
        dao.upsertHistoryEntry(historyEntry(id = "history", queueItemId = "parent", now = now))

        db.openHelper.writableDatabase.execSQL("DELETE FROM queue_items WHERE id = 'parent'")

        assertEquals(emptyList<AttachmentEntity>(), dao.observeAttachments("parent").first())
        assertEquals(emptyList<HistoryEntryEntity>(), dao.observeHistory("parent").first())
    }

    @Test
    fun observeAttachmentsOrdersCreatedAtDescendingThenIdAscending() = runBlocking {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        dao.upsertItem(queueItem(id = "parent", title = "Parent", now = now))
        dao.upsertAttachment(attachment(id = "b-attachment", queueItemId = "parent", now = now))
        dao.upsertAttachment(attachment(id = "a-attachment", queueItemId = "parent", now = now))

        val attachments = dao.observeAttachments("parent").first()

        assertEquals(listOf("a-attachment", "b-attachment"), attachments.map { it.id })
    }

    private fun queueItem(
        id: String,
        title: String,
        driveId: String? = null,
        description: String? = null,
        status: String = "QUEUED",
        priority: String? = null,
        dueDate: LocalDate? = null,
        tags: List<String> = emptyList(),
        dismissedAt: Instant? = null,
        now: Instant,
        updatedAt: Instant = now,
        syncState: String = "PENDING_SYNC"
    ) = QueueItemEntity(
        id = id,
        driveId = driveId,
        title = title,
        description = description,
        status = status,
        priority = priority,
        dueDate = dueDate,
        tags = tags,
        createdAt = now,
        updatedAt = updatedAt,
        completedAt = null,
        dismissedAt = dismissedAt,
        syncState = syncState
    )

    private fun attachment(
        id: String,
        queueItemId: String,
        now: Instant
    ) = AttachmentEntity(
        id = id,
        queueItemId = queueItemId,
        type = "TEXT",
        displayName = "Attachment",
        textContent = "Body",
        url = null,
        driveFileId = null,
        mimeType = "text/plain",
        createdAt = now,
        updatedAt = now,
        syncState = "PENDING_SYNC"
    )

    private fun historyEntry(
        id: String,
        queueItemId: String,
        now: Instant
    ) = HistoryEntryEntity(
        id = id,
        queueItemId = queueItemId,
        message = "Created",
        kind = "CREATE",
        createdAt = now
    )
}
