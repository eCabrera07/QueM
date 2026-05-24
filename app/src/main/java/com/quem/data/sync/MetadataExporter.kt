package com.quem.data.sync

data class ExportableQueueItem(
    val id: String,
    val title: String,
    val status: String
)

object MetadataExporter {
    fun export(
        exportedAt: String,
        items: List<ExportableQueueItem>,
        attachments: List<MetadataAttachment>,
        history: List<MetadataHistoryEntry>
    ): MetadataSnapshot = MetadataSnapshot(
        version = 1,
        exportedAt = exportedAt,
        items = items.map { item -> item.toMetadataQueueItem(exportedAt) },
        attachments = attachments,
        history = history
    )

    private fun ExportableQueueItem.toMetadataQueueItem(exportedAt: String): MetadataQueueItem =
        MetadataQueueItem(
            id = id,
            driveId = null,
            title = title,
            description = null,
            status = status,
            priority = null,
            dueDate = null,
            tags = emptyList(),
            createdAt = exportedAt,
            updatedAt = exportedAt,
            completedAt = null,
            dismissedAt = if (status == DISMISSED_STATUS) exportedAt else null
        )

    private const val DISMISSED_STATUS = "DISMISSED"
}
