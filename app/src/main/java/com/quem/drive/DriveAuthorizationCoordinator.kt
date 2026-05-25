package com.quem.drive

import android.content.Intent
import androidx.activity.result.IntentSenderRequest

data class DriveAuthorizationGrant(
    val accountEmail: String
)

data class ActivityResultData(
    val resultCode: Int,
    val data: Intent?
)

sealed interface DriveAuthorizationRequestResult {
    data class Authorized(
        val grant: DriveAuthorizationGrant
    ) : DriveAuthorizationRequestResult

    data class ResolutionRequired(
        val request: IntentSenderRequest
    ) : DriveAuthorizationRequestResult

    data class Failed(
        val message: String
    ) : DriveAuthorizationRequestResult
}

sealed interface DriveAuthorizationResolutionResult {
    data class Authorized(
        val grant: DriveAuthorizationGrant
    ) : DriveAuthorizationResolutionResult

    data object Cancelled : DriveAuthorizationResolutionResult

    data class Failed(
        val message: String
    ) : DriveAuthorizationResolutionResult
}

interface DriveAuthorizationCoordinator {
    fun requestAuthorization(onResult: (DriveAuthorizationRequestResult) -> Unit)

    fun launchResolution(
        request: IntentSenderRequest,
        onResult: (ActivityResultData) -> Unit
    )

    fun parseResolutionResult(result: ActivityResultData): DriveAuthorizationResolutionResult
}
