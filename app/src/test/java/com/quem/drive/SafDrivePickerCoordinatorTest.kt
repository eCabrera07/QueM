package com.quem.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafDrivePickerCoordinatorTest {
    @Test
    fun extractDriveIdFromFileDocumentId() {
        val documentId = "acc=0/doc=1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74ogVXXXX"
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74ogVXXXX", extractDriveId(documentId))
    }

    @Test
    fun extractDriveIdFromFolderDocumentId() {
        val documentId = "acc=0/type=dir/root=SomeRootId/doc=1FolderDriveId"
        assertEquals("1FolderDriveId", extractDriveId(documentId))
    }

    @Test
    fun extractDriveIdFromUrlEncodedDocumentId() {
        val documentId = "acc%3D0%2Fdoc%3D1BxiMVs0XRA5"
        assertEquals("1BxiMVs0XRA5", extractDriveId(documentId))
    }

    @Test
    fun extractDriveIdReturnsNullForNonDriveDocumentId() {
        assertNull(extractDriveId("primary:Documents/report.pdf"))
    }

    @Test
    fun extractDriveIdReturnsNullForEmptyString() {
        assertNull(extractDriveId(""))
    }

    @Test
    fun extractDriveIdReturnsNullWhenDocValueIsEmpty() {
        assertNull(extractDriveId("acc=0/doc="))
    }
}
