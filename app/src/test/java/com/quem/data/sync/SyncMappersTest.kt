package com.quem.data.sync

import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SyncMappersTest {

    @Test
    fun queueItemToExportableMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val item = QueueItem(
            id = "item-1",
            driveId = "drive-1",
            title = "Read contract",
            description = "Legal notes",
            status = QueueStatus.QUEUED,
            priority = Priority.HIGH,
            dueDate = LocalDate.parse("2026-05-30"),
            tags = listOf("legal"),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )

        val exportable = item.toExportable()

        assertEquals("item-1", exportable.id)
        assertEquals("drive-1", exportable.driveId)
        assertEquals("Read contract", exportable.title)
        assertEquals("Legal notes", exportable.description)
        assertEquals("QUEUED", exportable.status)
        assertEquals("HIGH", exportable.priority)
        assertEquals("2026-05-30", exportable.dueDate)
        assertEquals(listOf("legal"), exportable.tags)
        assertEquals(now.toString(), exportable.createdAt)
        assertEquals(now.toString(), exportable.updatedAt)
        assertNull(exportable.completedAt)
        assertNull(exportable.dismissedAt)
    }

    @Test
    fun queueItemToExportableHandlesNullOptionalFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val completedAt = Instant.parse("2026-05-29T13:00:00Z")
        val item = QueueItem(
            id = "item-2",
            driveId = null,
            title = "Done item",
            description = null,
            status = QueueStatus.DONE,
            priority = null,
            dueDate = null,
            tags = emptyList(),
            createdAt = now,
            updatedAt = completedAt,
            completedAt = completedAt,
            dismissedAt = null,
            syncState = SyncState.SYNCED
        )

        val exportable = item.toExportable()

        assertNull(exportable.driveId)
        assertNull(exportable.description)
        assertNull(exportable.priority)
        assertNull(exportable.dueDate)
        assertEquals(completedAt.toString(), exportable.completedAt)
        assertNull(exportable.dismissedAt)
    }

    @Test
    fun attachmentToMetadataMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val attachment = Attachment(
            id = "att-1",
            queueItemId = "item-1",
            type = AttachmentType.LINK,
            displayName = "Spec",
            textContent = null,
            url = "https://example.com/spec",
            driveFileId = null,
            mimeType = null,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_SYNC
        )

        val metadata = attachment.toMetadata()

        assertEquals("att-1", metadata.id)
        assertEquals("item-1", metadata.queueItemId)
        assertEquals("LINK", metadata.type)
        assertEquals("Spec", metadata.displayName)
        assertNull(metadata.textContent)
        assertEquals("https://example.com/spec", metadata.url)
        assertNull(metadata.driveFileId)
        assertNull(metadata.mimeType)
        assertEquals(now.toString(), metadata.createdAt)
        assertEquals(now.toString(), metadata.updatedAt)
    }

    @Test
    fun historyEntryToMetadataMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val entry = HistoryEntry(
            id = "hist-1",
            queueItemId = "item-1",
            message = "Created",
            kind = HistoryKind.STATUS_CHANGE,
            createdAt = now
        )

        val metadata = entry.toMetadata()

        assertEquals("hist-1", metadata.id)
        assertEquals("item-1", metadata.queueItemId)
        assertEquals("Created", metadata.message)
        assertEquals("STATUS_CHANGE", metadata.kind)
        assertEquals(now.toString(), metadata.createdAt)
    }
}
