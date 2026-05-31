package com.quem.data.sync

import com.quem.data.local.QueueDao
import java.time.Instant

class MergeCoordinator(private val dao: QueueDao) {

    suspend fun merge(snapshot: MetadataSnapshot) {
        mergeItems(snapshot.items)
        mergeAttachments(snapshot.attachments)
        mergeHistory(snapshot.history)
    }

    private suspend fun mergeItems(remoteItems: List<MetadataQueueItem>) {
        val localById = dao.allItems().associateBy { it.id }
        for (remote in remoteItems) {
            val entity = runCatching { remote.toEntity() }.getOrNull() ?: continue
            val local = localById[entity.id]
            if (local == null || Instant.parse(remote.updatedAt) > local.updatedAt) {
                dao.upsertItem(entity)
            }
        }
    }

    private suspend fun mergeAttachments(remoteAttachments: List<MetadataAttachment>) {
        val localById = dao.allAttachments().associateBy { it.id }
        for (remote in remoteAttachments) {
            val entity = runCatching { remote.toEntity() }.getOrNull() ?: continue
            val local = localById[entity.id]
            if (local == null || Instant.parse(remote.updatedAt) > local.updatedAt) {
                dao.upsertAttachment(entity)
            }
        }
    }

    private suspend fun mergeHistory(remoteHistory: List<MetadataHistoryEntry>) {
        val localIds = dao.allHistory().map { it.id }.toSet()
        for (remote in remoteHistory) {
            if (remote.id !in localIds) {
                runCatching { dao.upsertHistoryEntry(remote.toEntity()) }
            }
        }
    }
}
