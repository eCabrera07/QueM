package com.quem.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items WHERE status = :status ORDER BY updatedAt DESC")
    fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>>

    @Query(
        """
        SELECT * FROM queue_items
        WHERE status IN (:statuses)
        AND (
            title LIKE '%' || :query || '%' ESCAPE '\'
            OR description LIKE '%' || :query || '%' ESCAPE '\'
        )
        ORDER BY updatedAt DESC, id ASC
        """
    )
    fun searchItems(statuses: List<String>, query: String): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<QueueItemEntity?>

    @Query("SELECT * FROM queue_items WHERE syncState = 'PENDING_SYNC'")
    suspend fun pendingItems(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items")
    suspend fun allItems(): List<QueueItemEntity>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Query("SELECT * FROM history_entries")
    suspend fun allHistory(): List<HistoryEntryEntity>

    @Query("UPDATE queue_items SET syncState = 'SYNCED' WHERE syncState = 'PENDING_SYNC'")
    suspend fun markItemsSynced()

    @Query("UPDATE attachments SET syncState = 'SYNCED' WHERE syncState = 'PENDING_SYNC'")
    suspend fun markAttachmentsSynced()

    @Upsert
    suspend fun upsertItem(item: QueueItemEntity)

    @Query(
        """
        UPDATE queue_items
        SET status = :status,
            updatedAt = :updatedAt,
            completedAt = :completedAt,
            dismissedAt = :dismissedAt,
            syncState = 'PENDING_SYNC'
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Instant,
        completedAt: Instant?,
        dismissedAt: Instant?
    ): Int

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertHistoryEntry(entry: HistoryEntryEntity)

    @Query("SELECT * FROM attachments WHERE queueItemId = :queueItemId ORDER BY createdAt DESC, id ASC")
    fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM history_entries WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>>
}
