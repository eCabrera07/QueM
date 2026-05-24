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
    fun deletingQueueItemCascadesToAttachmentsAndHistory() = runBlocking {
        val now = Instant.parse("2026-05-23T12:00:00Z")
        dao.upsertItem(queueItem(id = "parent", title = "Parent", now = now))
        dao.upsertAttachment(attachment(id = "attachment", queueItemId = "parent", now = now))
        dao.upsertHistoryEntry(historyEntry(id = "history", queueItemId = "parent", now = now))

        db.openHelper.writableDatabase.execSQL("DELETE FROM queue_items WHERE id = 'parent'")

        assertEquals(emptyList<AttachmentEntity>(), dao.observeAttachments("parent").first())
        assertEquals(emptyList<HistoryEntryEntity>(), dao.observeHistory("parent").first())
    }

    private fun queueItem(
        id: String,
        title: String,
        status: String = "QUEUED",
        tags: List<String> = emptyList(),
        dismissedAt: Instant? = null,
        now: Instant
    ) = QueueItemEntity(
        id = id,
        driveId = null,
        title = title,
        description = null,
        status = status,
        priority = null,
        dueDate = null,
        tags = tags,
        createdAt = now,
        updatedAt = now,
        completedAt = null,
        dismissedAt = dismissedAt,
        syncState = "PENDING_SYNC"
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
