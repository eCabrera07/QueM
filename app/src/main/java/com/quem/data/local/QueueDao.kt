package com.quem.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

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

    @Query(
        """
        UPDATE queue_items
        SET title       = :title,
            description = :description,
            priority    = :priority,
            dueDate     = :dueDate,
            updatedAt   = :updatedAt,
            syncState   = 'PENDING_SYNC'
        WHERE id = :id
        """
    )
    suspend fun updateItemFields(
        id: String,
        title: String,
        description: String?,
        priority: String?,
        dueDate: LocalDate?,
        updatedAt: Instant
    ): Int

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteAttachment(id: String)

    @Query("UPDATE attachments SET displayName = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateAttachmentTitle(id: String, title: String, updatedAt: Instant)

    @Upsert
    suspend fun upsertHistoryEntry(entry: HistoryEntryEntity)

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryEntry(id: String)

    @Query("""
        UPDATE queue_items
        SET sharedDriveFileId = :sharedDriveFileId,
            sharedWith        = :sharedWith,
            syncState         = 'PENDING_SYNC',
            updatedAt         = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateShareInfo(
        id: String,
        sharedDriveFileId: String,
        sharedWith: List<String>,
        updatedAt: java.time.Instant
    )

    @Query("SELECT * FROM attachments WHERE queueItemId = :queueItemId ORDER BY createdAt DESC, id ASC")
    fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM history_entries WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>>
}
