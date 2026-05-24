package com.quem.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MetadataSerializerTest {
    @Test
    fun roundTripPreservesDismissedStatusAndOptionalDueDate() {
        val snapshot = MetadataSnapshot(
            version = 1,
            exportedAt = "2026-05-23T12:00:00Z",
            items = listOf(
                MetadataQueueItem(
                    id = "item-1",
                    driveId = null,
                    title = "Cancelled task",
                    description = "No longer relevant",
                    status = "DISMISSED",
                    priority = "HIGH",
                    dueDate = null,
                    tags = listOf("client"),
                    createdAt = "2026-05-20T12:00:00Z",
                    updatedAt = "2026-05-23T12:00:00Z",
                    completedAt = null,
                    dismissedAt = "2026-05-23T12:00:00Z"
                )
            ),
            attachments = emptyList(),
            history = emptyList()
        )

        val json = MetadataSerializer.encode(snapshot)
        val decoded = MetadataSerializer.decode(json)

        assertTrue(json.contains("\"status\":\"DISMISSED\""))
        assertEquals(snapshot, decoded)
    }
}
