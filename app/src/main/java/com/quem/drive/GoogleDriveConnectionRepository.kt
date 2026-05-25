package com.quem.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleDriveConnectionRepository(
    private val authorizationCoordinator: DriveAuthorizationCoordinator
) : DriveConnectionRepository {
    private val mutableState = MutableStateFlow<DriveConnectionState>(DriveConnectionState.Disconnected)

    override val state: StateFlow<DriveConnectionState> = mutableState.asStateFlow()

    override fun requestSignIn() {
        authorizationCoordinator.requestAuthorization { result ->
            when (result) {
                is DriveAuthorizationRequestResult.Authorized -> connect(result.grant)
                is DriveAuthorizationRequestResult.ResolutionRequired -> {
                    authorizationCoordinator.launchResolution(result.request) { activityResult ->
                        handleResolutionResult(activityResult)
                    }
                }
                is DriveAuthorizationRequestResult.Failed -> {
                    mutableState.value = DriveConnectionState.Error(result.message)
                }
            }
        }
    }

    override fun disconnect() {
        mutableState.value = DriveConnectionState.Disconnected
    }

    private fun handleResolutionResult(activityResult: ActivityResultData) {
        when (val result = authorizationCoordinator.parseResolutionResult(activityResult)) {
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
        mutableState.value = DriveConnectionState.Connected(
            DriveAccount(email = grant.accountEmail)
        )
    }
}
