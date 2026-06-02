package com.quem.data.sync

import com.quem.core.model.AttachmentType
import com.quem.core.time.Clock
import com.quem.data.local.QueueDao
import com.quem.data.local.toDomain

class SyncCoordinator(
    private val dao: QueueDao,
    private val syncManager: SyncManager,
    private val clock: Clock,
    private val mergeCoordinator: MergeCoordinator = MergeCoordinator(dao)
) {
    suspend fun sync() {
        // 1. Download remote snapshot and merge into local DB
        val remoteSnapshot = syncManager.download()
        if (remoteSnapshot != null) {
            mergeCoordinator.merge(remoteSnapshot)
        }

        // 2. Upload merged local state — exclude LOCAL_FILE attachments (upload pending/failed)
        val items       = dao.allItems().map { it.toDomain() }
        val attachments = dao.allAttachments()
            .map { it.toDomain() }
            .filter { it.type != AttachmentType.LOCAL_FILE }
        val history     = dao.allHistory().map { it.toDomain() }

        val snapshot = MetadataExporter.export(
            exportedAt  = clock.now().toString(),
            items       = items.map { it.toExportable() },
            attachments = attachments.map { it.toMetadata() },
            history     = history.map { it.toMetadata() }
        )

        syncManager.upload(snapshot)

        // 3. Mark synced
        dao.markItemsSynced()
        dao.markAttachmentsSynced()
    }
}
