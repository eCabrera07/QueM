package com.quem.data.sync

import com.quem.core.time.Clock
import com.quem.data.local.QueueDao
import com.quem.data.local.toDomain

class SyncCoordinator(
    private val dao: QueueDao,
    private val syncManager: SyncManager,
    private val clock: Clock
) {
    suspend fun sync() {
        val items       = dao.allItems().map { it.toDomain() }
        val attachments = dao.allAttachments().map { it.toDomain() }
        val history     = dao.allHistory().map { it.toDomain() }

        val snapshot = MetadataExporter.export(
            exportedAt  = clock.now().toString(),
            items       = items.map { it.toExportable() },
            attachments = attachments.map { it.toMetadata() },
            history     = history.map { it.toMetadata() }
        )

        syncManager.upload(snapshot)

        dao.markItemsSynced()
        dao.markAttachmentsSynced()
    }
}
