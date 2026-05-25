package com.quem.drive

import android.app.Activity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope

class GoogleDriveAuthorizationCoordinator(
    private val activity: Activity,
    private val resolutionLauncher: ActivityResultLauncher<IntentSenderRequest>
) : DriveAuthorizationCoordinator {
    private var pendingResolutionCallback: ((ActivityResultData) -> Unit)? = null

    override fun requestAuthorization(onResult: (DriveAuthorizationRequestResult) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requiredScopes())
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener { result ->
                onResult(result.toRequestResult())
            }
            .addOnFailureListener { error ->
                onResult(
                    DriveAuthorizationRequestResult.Failed(
                        error.localizedMessage ?: "Google Drive authorization failed"
                    )
                )
            }
    }

    override fun launchResolution(
        request: IntentSenderRequest,
        onResult: (ActivityResultData) -> Unit
    ) {
        pendingResolutionCallback = onResult
        resolutionLauncher.launch(request)
    }

    override fun parseResolutionResult(result: ActivityResultData): DriveAuthorizationResolutionResult {
        if (result.resultCode != Activity.RESULT_OK || result.data == null) {
            return DriveAuthorizationResolutionResult.Cancelled
        }

        return runCatching {
            val authorizationResult = Identity.getAuthorizationClient(activity)
                .getAuthorizationResultFromIntent(result.data)
            authorizationResult.toResolutionResult()
        }.getOrElse { error ->
            DriveAuthorizationResolutionResult.Failed(
                error.localizedMessage ?: "Google Drive authorization failed"
            )
        }
    }

    fun dispatchResolutionResult(result: ActivityResultData) {
        val callback = pendingResolutionCallback
        pendingResolutionCallback = null
        callback?.invoke(result)
    }

    private fun AuthorizationResult.toRequestResult(): DriveAuthorizationRequestResult {
        if (hasResolution()) {
            val pendingIntent = pendingIntent
                ?: return DriveAuthorizationRequestResult.Failed("Google Drive authorization failed")

            return DriveAuthorizationRequestResult.ResolutionRequired(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }

        return DriveAuthorizationRequestResult.Authorized(toGrant())
    }

    private fun AuthorizationResult.toResolutionResult(): DriveAuthorizationResolutionResult =
        DriveAuthorizationResolutionResult.Authorized(toGrant())

    private fun AuthorizationResult.toGrant(): DriveAuthorizationGrant =
        DriveAuthorizationGrant(
            accountEmail = toGoogleSignInAccount()?.email ?: "Google Drive"
        )

    companion object {
        const val DRIVE_FILE_SCOPE: String = "https://www.googleapis.com/auth/drive.file"

        fun requiredScopeUris(): List<String> = listOf(DRIVE_FILE_SCOPE)

        fun requiredScopes(): List<Scope> = requiredScopeUris().map(::Scope)
    }
}
