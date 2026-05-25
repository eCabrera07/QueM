package com.quem.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class GoogleDriveAuthorizationCoordinatorTest {
    @Test
    fun driveFileScopeUsesLeastBroadDrivePermission() {
        assertEquals(
            "https://www.googleapis.com/auth/drive.file",
            GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE
        )
    }

    @Test
    fun requestedScopeUrisIncludeDriveFileScope() {
        val scopes = GoogleDriveAuthorizationCoordinator.requiredScopeUris()

        assertEquals(listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE), scopes)
    }
}
