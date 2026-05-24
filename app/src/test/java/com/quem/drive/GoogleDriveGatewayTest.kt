package com.quem.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveGatewayTest {
    @Test
    fun metadataFileQueryEscapesFolderAndFileNames() {
        val query = GoogleDriveQueries.fileInFolderQuery(
            folderId = "folder123",
            fileName = "queue-metadata.json"
        )

        assertEquals("'folder123' in parents and name = 'queue-metadata.json' and trashed = false", query)
    }

    @Test
    fun fileQueryEscapesApostrophesAndBackslashes() {
        val query = GoogleDriveQueries.fileInFolderQuery(
            folderId = "folder\\'123",
            fileName = "queue\\metadata's.json"
        )

        assertEquals(
            "'folder\\\\\\'123' in parents and name = 'queue\\\\metadata\\'s.json' and trashed = false",
            query
        )
    }
}
