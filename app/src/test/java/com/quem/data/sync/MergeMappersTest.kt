package com.quem.data.sync

import com.quem.core.model.SyncState
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MergeMappersTest {

    @Test
    fun metadataQueueItemToEntityMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val completedAt = Instant.parse("2026-05-30T12:00:00Z")
        val remote = MetadataQueueItem(
            id = "item-1", driveId = "drive-1", title = "Read contract",
            description = "Legal notes", status = "QUEUED", priority = "HIGH",
            dueDate = "2026-06-01", tags = listOf("legal"),
            createdAt = now.toString(), updatedAt = now.toString(),
            completedAt = completedAt.toString(), dismissedAt = null
        )

        val entity = remote.toEntity()

        assertEquals("item-1", entity.id)
        assertEquals("drive-1", entity.driveId)
        assertEquals("Read contract", entity.title)
        assertEquals("Legal notes", entity.description)
        assertEquals("QUEUED", entity.status)
        assertEquals("HIGH", entity.priority)
        assertEquals(LocalDate.parse("2026-06-01"), entity.dueDate)
        assertEquals(listOf("legal"), entity.tags)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
        assertEquals(completedAt, entity.completedAt)
        assertNull(entity.dismissedAt)
        assertEquals(SyncState.SYNCED.name, entity.syncState)
    }

    @Test
    fun metadataQueueItemHandlesNullOptionalFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val remote = MetadataQueueItem(
            id = "item-1", driveId = null, title = "Read",
            description = null, status = "QUEUED", priority = null,
            dueDate = null, tags = emptyList(),
            createdAt = now.toString(), updatedAt = now.toString(),
            completedAt = null, dismissedAt = null
        )

        val entity = remote.toEntity()

        assertNull(entity.driveId)
        assertNull(entity.description)
        assertNull(entity.priority)
        assertNull(entity.dueDate)
        assertNull(entity.completedAt)
        assertNull(entity.dismissedAt)
    }

    @Test
    fun metadataAttachmentToEntityMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val remote = MetadataAttachment(
            id = "att-1", queueItemId = "item-1", type = "LINK",
            displayName = "Spec", textContent = null,
            url = "https://example.com", driveFileId = null,
            mimeType = null, createdAt = now.toString(), updatedAt = now.toString()
        )

        val entity = remote.toEntity()

        assertEquals("att-1", entity.id)
        assertEquals("item-1", entity.queueItemId)
        assertEquals("LINK", entity.type)
        assertEquals("Spec", entity.displayName)
        assertNull(entity.textContent)
        assertEquals("https://example.com", entity.url)
        assertNull(entity.driveFileId)
        assertNull(entity.mimeType)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
        assertEquals(SyncState.SYNCED.name, entity.syncState)
    }

    @Test
    fun metadataHistoryEntryToEntityMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val remote = MetadataHistoryEntry(
            id = "hist-1", queueItemId = "item-1",
            message = "Created", kind = "STATUS_CHANGE",
            createdAt = now.toString()
        )

        val entity = remote.toEntity()

        assertEquals("hist-1", entity.id)
        assertEquals("item-1", entity.queueItemId)
        assertEquals("Created", entity.message)
        assertEquals("STATUS_CHANGE", entity.kind)
        assertEquals(now, entity.createdAt)
    }
}
