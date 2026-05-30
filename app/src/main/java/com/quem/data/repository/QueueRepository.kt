package com.quem.data.repository

import com.quem.core.model.Attachment
import com.quem.core.model.HistoryEntry
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface QueueRepository {
    fun observeItems(status: QueueStatus): Flow<List<QueueItem>>

    fun searchArchive(query: String): Flow<List<QueueItem>>

    fun observeItem(id: String): Flow<QueueItem?>

    suspend fun createItem(
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem

    suspend fun changeStatus(id: String, status: QueueStatus): QueueItem?

    fun observeAttachments(queueItemId: String): Flow<List<Attachment>>

    fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>>

    suspend fun addTextAttachment(queueItemId: String, title: String, text: String)

    suspend fun addLinkAttachment(queueItemId: String, title: String, url: String)

    suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    )
}
