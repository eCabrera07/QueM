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
}
