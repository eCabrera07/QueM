package com.quem.data.sync

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncManagerTest {
    @Test
    fun uploadWritesMetadataToQueMFolder() = runTest {
        val gateway = FakeDriveGateway()
        val manager = SyncManager(gateway)
        val snapshot = metadataSnapshot()

        manager.upload(snapshot)

        assertEquals("QueM", gateway.folderName)
        assertEquals("queue-metadata.json", gateway.fileName)
        assertEquals(snapshot, MetadataSerializer.decode(gateway.content))
    }

    @Test
    fun downloadReadsMetadataFromQueMFolder() = runTest {
        val snapshot = metadataSnapshot()
        val gateway = FakeDriveGateway(
            downloadContent = MetadataSerializer.encode(snapshot)
        )
        val manager = SyncManager(gateway)

        val downloaded = manager.download()

        assertEquals("QueM", gateway.folderName)
        assertEquals("queue-metadata.json", gateway.fileName)
        assertEquals(snapshot, downloaded)
    }

    @Test
    fun downloadReturnsNullWhenMetadataIsMissing() = runTest {
        val gateway = FakeDriveGateway()
        val manager = SyncManager(gateway)

        val downloaded = manager.download()

        assertNull(downloaded)
    }
}

private fun metadataSnapshot() = MetadataSnapshot(
    version = 1,
    exportedAt = "2026-05-23T12:00:00Z",
    items = emptyList(),
    attachments = emptyList(),
    history = emptyList()
)

private class FakeDriveGateway(
    private val downloadContent: String? = null
) : DriveGateway {
    lateinit var folderName: String
    lateinit var fileName: String
    lateinit var content: String

    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) {
        this.folderName = folderName
        this.fileName = fileName
        this.content = content
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? {
        this.folderName = folderName
        this.fileName = fileName
        return downloadContent
    }
}
