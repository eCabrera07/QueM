package com.quem.data.sync

import com.quem.core.model.QueueStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class MetadataExporterTest {
    @Test
    fun mapsDismissedItemStatusToMetadata() {
        val item = ExportableQueueItem(
            id = "item-1",
            title = "Old task",
            status = QueueStatus.DISMISSED.name
        )

        val snapshot = MetadataExporter.export(
            exportedAt = "2026-05-23T12:00:00Z",
            items = listOf(item),
            attachments = emptyList(),
            history = emptyList()
        )

        assertEquals("DISMISSED", snapshot.items.single().status)
        assertEquals("2026-05-23T12:00:00Z", snapshot.items.single().dismissedAt)
    }

    @Test
    fun preservesQueueItemMetadataFields() {
        val item = ExportableQueueItem(
            id = "item-1",
            driveId = "drive-1",
            title = "Task with metadata",
            description = "Detailed notes",
            status = QueueStatus.DONE.name,
            priority = "HIGH",
            dueDate = "2026-05-30",
            tags = listOf("work", "urgent"),
            createdAt = "2026-05-20T08:00:00Z",
            updatedAt = "2026-05-21T09:30:00Z",
            completedAt = "2026-05-22T10:45:00Z",
            dismissedAt = "2026-05-23T11:15:00Z"
        )

        val snapshot = MetadataExporter.export(
            exportedAt = "2026-05-24T12:00:00Z",
            items = listOf(item),
            attachments = emptyList(),
            history = emptyList()
        )

        val exportedItem = snapshot.items.single()
        assertEquals("drive-1", exportedItem.driveId)
        assertEquals("Detailed notes", exportedItem.description)
        assertEquals("HIGH", exportedItem.priority)
        assertEquals("2026-05-30", exportedItem.dueDate)
        assertEquals(listOf("work", "urgent"), exportedItem.tags)
        assertEquals("2026-05-20T08:00:00Z", exportedItem.createdAt)
        assertEquals("2026-05-21T09:30:00Z", exportedItem.updatedAt)
        assertEquals("2026-05-22T10:45:00Z", exportedItem.completedAt)
        assertEquals("2026-05-23T11:15:00Z", exportedItem.dismissedAt)
    }

    @Test
    fun passesThroughAttachmentsAndHistory() {
        val attachment = MetadataAttachment(
            id = "attachment-1",
            queueItemId = "item-1",
            type = "note",
            displayName = "Brief",
            textContent = "Attachment text",
            url = "https://example.com/brief",
            driveFileId = "drive-file-1",
            mimeType = "text/plain",
            createdAt = "2026-05-20T08:00:00Z",
            updatedAt = "2026-05-21T09:30:00Z"
        )
        val historyEntry = MetadataHistoryEntry(
            id = "history-1",
            queueItemId = "item-1",
            message = "Created",
            kind = "CREATE",
            createdAt = "2026-05-20T08:00:00Z"
        )

        val snapshot = MetadataExporter.export(
            exportedAt = "2026-05-24T12:00:00Z",
            items = emptyList(),
            attachments = listOf(attachment),
            history = listOf(historyEntry)
        )

        assertEquals(listOf(attachment), snapshot.attachments)
        assertEquals(listOf(historyEntry), snapshot.history)
    }

    @Test
    fun leavesDismissedAtNullForNonDismissedItemsWhenOmitted() {
        val item = ExportableQueueItem(
            id = "item-1",
            title = "Active task",
            status = QueueStatus.QUEUED.name
        )

        val snapshot = MetadataExporter.export(
            exportedAt = "2026-05-24T12:00:00Z",
            items = listOf(item),
            attachments = emptyList(),
            history = emptyList()
        )

        assertEquals(null, snapshot.items.single().dismissedAt)
    }
}
