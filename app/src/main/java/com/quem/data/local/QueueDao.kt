package com.quem.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items WHERE status = :status ORDER BY updatedAt DESC")
    fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<QueueItemEntity?>

    @Query("SELECT * FROM queue_items WHERE syncState = 'PENDING_SYNC'")
    suspend fun pendingItems(): List<QueueItemEntity>

    @Upsert
    suspend fun upsertItem(item: QueueItemEntity)

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertHistoryEntry(entry: HistoryEntryEntity)

    @Query("SELECT * FROM attachments WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM history_entries WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>>
}
