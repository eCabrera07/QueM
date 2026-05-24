package com.quem.core.queue

import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.Clock

object QueueRules {
    fun changeStatus(
        item: QueueItem,
        newStatus: QueueStatus,
        clock: Clock
    ): QueueItem {
        val now = clock.now()
        return item.copy(
            status = newStatus,
            updatedAt = now,
            completedAt = if (newStatus == QueueStatus.DONE) now else null,
            dismissedAt = if (newStatus == QueueStatus.DISMISSED) now else null,
            syncState = SyncState.PENDING_SYNC
        )
    }
}
