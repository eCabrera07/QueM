package com.quem.core.queue

import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class QueueRulesTest {
    private val clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z"))

    @Test
    fun markDoneSetsCompletedAtAndPendingSync() {
        val item = TestItems.queueItem(status = QueueStatus.IN_PROGRESS)
        val result = QueueRules.changeStatus(item, QueueStatus.DONE, clock)

        assertEquals(QueueStatus.DONE, result.status)
        assertEquals(clock.now(), result.updatedAt)
        assertEquals(clock.now(), result.completedAt)
        assertNull(result.dismissedAt)
        assertEquals(SyncState.PENDING_SYNC, result.syncState)
    }

    @Test
    fun markDismissedSetsDismissedAtAndPendingSync() {
        val item = TestItems.queueItem(status = QueueStatus.QUEUED)
        val result = QueueRules.changeStatus(item, QueueStatus.DISMISSED, clock)

        assertEquals(QueueStatus.DISMISSED, result.status)
        assertEquals(clock.now(), result.updatedAt)
        assertEquals(clock.now(), result.dismissedAt)
        assertNull(result.completedAt)
        assertEquals(SyncState.PENDING_SYNC, result.syncState)
    }

    @Test
    fun reopenFromDoneClearsTerminalTimestamps() {
        val item = TestItems.queueItem(
            status = QueueStatus.DONE,
            completedAt = Instant.parse("2026-05-20T12:00:00Z")
        )
        val result = QueueRules.changeStatus(item, QueueStatus.QUEUED, clock)

        assertEquals(QueueStatus.QUEUED, result.status)
        assertNull(result.completedAt)
        assertNull(result.dismissedAt)
    }

    @Test
    fun reopenFromDismissedClearsDismissedAtAndLeavesCompletedAtNull() {
        val item = TestItems.queueItem(
            status = QueueStatus.DISMISSED,
            dismissedAt = Instant.parse("2026-05-20T12:00:00Z")
        )
        val result = QueueRules.changeStatus(item, QueueStatus.QUEUED, clock)

        assertEquals(QueueStatus.QUEUED, result.status)
        assertNull(result.dismissedAt)
        assertNull(result.completedAt)
    }
}

private object TestItems {
    fun queueItem(
        status: QueueStatus,
        completedAt: Instant? = null,
        dismissedAt: Instant? = null
    ) = com.quem.core.model.QueueItem(
        id = "item-1",
        driveId = null,
        title = "Test item",
        description = null,
        status = status,
        priority = null,
        dueDate = null,
        tags = emptyList(),
        createdAt = Instant.parse("2026-05-01T12:00:00Z"),
        updatedAt = Instant.parse("2026-05-01T12:00:00Z"),
        completedAt = completedAt,
        dismissedAt = dismissedAt,
        syncState = SyncState.SYNCED
    )
}
