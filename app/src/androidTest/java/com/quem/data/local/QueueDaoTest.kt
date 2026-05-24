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
        dao.upsertItem(QueueItemEntity(id = "queued", driveId = null, title = "Queued", description = null, status = "QUEUED", priority = null, dueDate = null, tags = emptyList(), createdAt = now, updatedAt = now, completedAt = null, dismissedAt = null, syncState = "PENDING_SYNC"))
        dao.upsertItem(QueueItemEntity(id = "dismissed", driveId = null, title = "Dismissed", description = null, status = "DISMISSED", priority = null, dueDate = null, tags = emptyList(), createdAt = now, updatedAt = now, completedAt = null, dismissedAt = now, syncState = "PENDING_SYNC"))

        val queued = dao.observeItemsByStatus("QUEUED").first()

        assertEquals(listOf("queued"), queued.map { it.id })
    }
}
