package com.quem.data.sync

data class ExportableQueueItem(
    val id: String,
    val title: String,
    val status: String,
    val driveId: String? = null,
    val description: String? = null,
    val priority: String? = null,
    val dueDate: String? = null,
    val tags: List<String> = emptyList(),
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val completedAt: String? = null,
    val dismissedAt: String? = null
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
            driveId = driveId,
            title = title,
            description = description,
            status = status,
            priority = priority,
            dueDate = dueDate,
            tags = tags,
            createdAt = createdAt ?: exportedAt,
            updatedAt = updatedAt ?: exportedAt,
            completedAt = completedAt,
            dismissedAt = dismissedAt ?: if (status == DISMISSED_STATUS) exportedAt else null
        )

    private const val DISMISSED_STATUS = "DISMISSED"
}
