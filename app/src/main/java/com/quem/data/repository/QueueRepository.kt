package com.quem.data.repository

import com.quem.core.model.Attachment
import com.quem.core.model.HistoryEntry
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.drive.DriveFileUploadGateway
import com.quem.drive.DriveShareGateway
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

    suspend fun updateItem(
        id: String,
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem?

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

    suspend fun deleteAttachment(attachmentId: String)

    suspend fun updateAttachmentTitle(attachmentId: String, title: String)

    suspend fun deleteHistoryEntry(historyEntryId: String)

    suspend fun shareItem(
        itemId: String,
        recipientEmail: String,
        shareGateway: DriveShareGateway
    ): Boolean

    /** Stores a LOCAL_FILE / PENDING_UPLOAD attachment immediately. Returns the new attachment ID. */
    suspend fun attachLocalFile(
        queueItemId: String,
        uri: String,
        displayName: String,
        mimeType: String?
    ): String

    /**
     * Uploads the file referenced by the LOCAL_FILE attachment to Drive.
     * On success: transitions attachment to DRIVE_FILE / SYNCED.
     * On failure: transitions attachment to UPLOAD_FAILED, preserving uri for retry.
     * Returns true on success.
     */
    suspend fun uploadPendingFile(
        attachmentId: String,
        contentResolver: android.content.ContentResolver,
        gateway: DriveFileUploadGateway
    ): Boolean

    /** Retries a UPLOAD_FAILED attachment. Identical contract to uploadPendingFile. */
    suspend fun retryFileUpload(
        attachmentId: String,
        contentResolver: android.content.ContentResolver,
        gateway: DriveFileUploadGateway
    ): Boolean
}
