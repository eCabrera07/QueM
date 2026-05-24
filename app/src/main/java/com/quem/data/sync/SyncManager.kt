package com.quem.data.sync

class SyncManager(private val driveGateway: DriveGateway) {
    suspend fun upload(snapshot: MetadataSnapshot) {
        driveGateway.uploadTextFile(
            folderName = QUE_M_FOLDER,
            fileName = METADATA_FILE,
            content = MetadataSerializer.encode(snapshot)
        )
    }

    suspend fun download(): MetadataSnapshot? {
        val content = driveGateway.downloadTextFile(
            folderName = QUE_M_FOLDER,
            fileName = METADATA_FILE
        ) ?: return null

        return MetadataSerializer.decode(content)
    }

    private companion object {
        const val QUE_M_FOLDER = "QueM"
        const val METADATA_FILE = "queue-metadata.json"
    }
}
