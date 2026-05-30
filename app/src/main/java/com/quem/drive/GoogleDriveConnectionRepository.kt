package com.quem.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleDriveConnectionRepository(
    initialAuthorizationCoordinator: DriveAuthorizationCoordinator? = null,
    private val driveAccountPreferences: DriveAccountPreferences? = null
) : DriveConnectionRepository {
    private val mutableState = MutableStateFlow<DriveConnectionState>(DriveConnectionState.Disconnected)
    private var authorizationCoordinator: DriveAuthorizationCoordinator? = initialAuthorizationCoordinator

    override val state: StateFlow<DriveConnectionState> = mutableState.asStateFlow()

    fun setAuthorizationCoordinator(authorizationCoordinator: DriveAuthorizationCoordinator) {
        this.authorizationCoordinator = authorizationCoordinator
    }

    override fun requestSignIn() {
        val coordinator = authorizationCoordinator
        if (coordinator == null) {
            mutableState.value = DriveConnectionState.Error("Google Drive authorization unavailable")
            return
        }

        coordinator.requestAuthorization { result ->
            when (result) {
                is DriveAuthorizationRequestResult.Authorized -> connect(result.grant)
                is DriveAuthorizationRequestResult.ResolutionRequired -> {
                    coordinator.launchResolution(result.request)
                }
                is DriveAuthorizationRequestResult.Failed -> {
                    mutableState.value = DriveConnectionState.Error(result.message)
                }
            }
        }
    }

    override fun disconnect() {
        driveAccountPreferences?.clear()
        mutableState.value = DriveConnectionState.Disconnected
    }

    fun handleResolutionResult(activityResult: ActivityResultData) {
        val coordinator = authorizationCoordinator
        if (coordinator == null) {
            mutableState.value = DriveConnectionState.Error("Google Drive authorization unavailable")
            return
        }

        when (val result = coordinator.parseResolutionResult(activityResult)) {
            is DriveAuthorizationResolutionResult.Authorized -> connect(result.grant)
            DriveAuthorizationResolutionResult.Cancelled -> {
                mutableState.value = DriveConnectionState.Error("Google Drive authorization cancelled")
            }
            is DriveAuthorizationResolutionResult.Failed -> {
                mutableState.value = DriveConnectionState.Error(result.message)
            }
        }
    }

    private fun connect(grant: DriveAuthorizationGrant) {
        driveAccountPreferences?.save(grant.accountEmail)
        mutableState.value = DriveConnectionState.Connected(
            DriveAccount(email = grant.accountEmail)
        )
    }
}
