package com.quem.data.sync

import kotlinx.serialization.Serializable

@Serializable
data class MetadataSnapshot(
    val version: Int,
    val exportedAt: String,
    val items: List<MetadataQueueItem>,
    val attachments: List<MetadataAttachment>,
    val history: List<MetadataHistoryEntry>
)

@Serializable
data class MetadataQueueItem(
    val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String?,
    val dueDate: String?,
    val tags: List<String>,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?,
    val dismissedAt: String?
)

@Serializable
data class MetadataAttachment(
    val id: String,
    val queueItemId: String,
    val type: String,
    val displayName: String,
    val textContent: String?,
    val url: String?,
    val driveFileId: String?,
    val mimeType: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class MetadataHistoryEntry(
    val id: String,
    val queueItemId: String,
    val message: String,
    val kind: String,
    val createdAt: String
)
