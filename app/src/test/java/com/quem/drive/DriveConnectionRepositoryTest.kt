package com.quem.drive

import org.junit.Assert.assertEquals
import org.junit.Test

class DriveConnectionRepositoryTest {
    @Test
    fun disconnectedRepositoryStartsDisconnected() {
        val repository = DisconnectedDriveConnectionRepository()

        assertEquals(DriveConnectionState.Disconnected, repository.state.value)
    }

    @Test
    fun disconnectedRepositoryRequestSignInShowsUnavailableError() {
        val repository = DisconnectedDriveConnectionRepository()

        repository.requestSignIn()

        assertEquals(
            DriveConnectionState.Error("Google Drive sign-in is not configured yet"),
            repository.state.value
        )
    }

    @Test
    fun disconnectedRepositoryDisconnectReturnsToDisconnected() {
        val repository = DisconnectedDriveConnectionRepository()

        repository.requestSignIn()
        repository.disconnect()

        assertEquals(DriveConnectionState.Disconnected, repository.state.value)
    }
}
