package com.quem.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DriveAccount(
    val email: String
)

sealed interface DriveConnectionState {
    data object Disconnected : DriveConnectionState

    data class Connected(
        val account: DriveAccount
    ) : DriveConnectionState

    data class Error(
        val message: String
    ) : DriveConnectionState
}

data class DriveSelection(
    val id: String,
    val name: String,
    val mimeType: String?,
    val isFolder: Boolean
)

interface DriveConnectionRepository {
    val state: StateFlow<DriveConnectionState>

    fun requestSignIn()

    fun disconnect()
}

class DisconnectedDriveConnectionRepository : DriveConnectionRepository {
    private val mutableState = MutableStateFlow<DriveConnectionState>(DriveConnectionState.Disconnected)

    override val state: StateFlow<DriveConnectionState> = mutableState.asStateFlow()

    override fun requestSignIn() {
        mutableState.value = DriveConnectionState.Error("Google Drive sign-in is not configured yet")
    }

    override fun disconnect() {
        mutableState.value = DriveConnectionState.Disconnected
    }
}
