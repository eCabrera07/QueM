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
    override fun requestAuthorization(onResult: (DriveAuthorizationRequestResult) -> Unit) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(requiredScopes())
            .build()

        Identity.getAuthorizationClient(activity)
            .authorize(request)
            .addOnSuccessListener(activity) { result ->
                onResult(result.toRequestResult())
            }
            .addOnFailureListener(activity) { error ->
                onResult(
                    DriveAuthorizationRequestResult.Failed(
                        error.localizedMessage ?: "Google Drive authorization failed"
                    )
                )
            }
    }

    override fun launchResolution(request: IntentSenderRequest) {
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

    private fun AuthorizationResult.toRequestResult(): DriveAuthorizationRequestResult {
        if (hasResolution()) {
            val pendingIntent = pendingIntent
                ?: return DriveAuthorizationRequestResult.Failed("Google Drive authorization failed")

            return DriveAuthorizationRequestResult.ResolutionRequired(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }

        val grant = toGrant()
            ?: return DriveAuthorizationRequestResult.Failed("Could not retrieve Google account email")
        return DriveAuthorizationRequestResult.Authorized(grant)
    }

    private fun AuthorizationResult.toResolutionResult(): DriveAuthorizationResolutionResult {
        val grant = toGrant()
            ?: return DriveAuthorizationResolutionResult.Failed("Could not retrieve Google account email")
        return DriveAuthorizationResolutionResult.Authorized(grant)
    }

    private fun AuthorizationResult.toGrant(): DriveAuthorizationGrant? {
        val email = toGoogleSignInAccount()?.email ?: return null
        return DriveAuthorizationGrant(accountEmail = email)
    }

    companion object {
        const val DRIVE_FILE_SCOPE: String = "https://www.googleapis.com/auth/drive.file"
        const val EMAIL_SCOPE: String = "email"

        fun requiredScopeUris(): List<String> = listOf(DRIVE_FILE_SCOPE, EMAIL_SCOPE)

        fun requiredScopes(): List<Scope> = requiredScopeUris().map(::Scope)
    }
}
