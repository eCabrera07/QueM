package com.quem.drive

import android.app.PendingIntent
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleDriveConnectionRepositoryTest {
    @Test
    fun requestSignInConnectsWhenAlreadyAuthorized() {
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.Authorized(
                DriveAuthorizationGrant(accountEmail = "user@example.com")
            )
        )
        val repository = GoogleDriveConnectionRepository(coordinator)

        repository.requestSignIn()

        assertEquals(
            DriveConnectionState.Connected(DriveAccount("user@example.com")),
            repository.state.value
        )
        assertNull(coordinator.launchedRequest)
    }

    @Test
    fun requestSignInLaunchesResolutionWhenConsentIsRequired() {
        val resolution = IntentSenderRequest.Builder(FakeIntentSender.instance).build()
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.ResolutionRequired(resolution)
        )
        val repository = GoogleDriveConnectionRepository(coordinator)

        repository.requestSignIn()

        assertEquals(DriveConnectionState.Disconnected, repository.state.value)
        assertEquals(resolution, coordinator.launchedRequest)
    }

    @Test
    fun requestSignInMovesToErrorWhenAuthorizationRequestFails() {
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.Failed("Google Drive authorization unavailable")
        )
        val repository = GoogleDriveConnectionRepository(coordinator)

        repository.requestSignIn()

        assertEquals(
            DriveConnectionState.Error("Google Drive authorization unavailable"),
            repository.state.value
        )
    }

    @Test
    fun resolutionSuccessConnectsAccount() {
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.ResolutionRequired(
                IntentSenderRequest.Builder(FakeIntentSender.instance).build()
            ),
            resolutionResult = DriveAuthorizationGrant(accountEmail = "user@example.com")
        )
        val repository = GoogleDriveConnectionRepository(coordinator)

        repository.requestSignIn()
        repository.handleResolutionResult(ActivityResultData(resultCode = RESULT_OK, data = Intent()))

        assertEquals(
            DriveConnectionState.Connected(DriveAccount("user@example.com")),
            repository.state.value
        )
    }

    @Test
    fun resolutionCancellationMovesToError() {
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.ResolutionRequired(
                IntentSenderRequest.Builder(FakeIntentSender.instance).build()
            ),
            resolutionResult = null
        )
        val repository = GoogleDriveConnectionRepository(coordinator)

        repository.requestSignIn()
        repository.handleResolutionResult(ActivityResultData(resultCode = RESULT_CANCELED, data = null))

        assertEquals(
            DriveConnectionState.Error("Google Drive authorization cancelled"),
            repository.state.value
        )
    }

    @Test
    fun coordinatorCanBeAttachedAfterRepositoryCreation() {
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.Authorized(
                DriveAuthorizationGrant(accountEmail = "user@example.com")
            )
        )
        val repository = GoogleDriveConnectionRepository()

        repository.setAuthorizationCoordinator(coordinator)
        repository.requestSignIn()

        assertEquals(
            DriveConnectionState.Connected(DriveAccount("user@example.com")),
            repository.state.value
        )
    }

    @Test
    fun requestSignInWithoutCoordinatorMovesToError() {
        val repository = GoogleDriveConnectionRepository()

        repository.requestSignIn()

        assertEquals(
            DriveConnectionState.Error("Google Drive authorization unavailable"),
            repository.state.value
        )
    }

    @Test
    fun disconnectReturnsToDisconnected() {
        val coordinator = FakeDriveAuthorizationCoordinator(
            requestResult = DriveAuthorizationRequestResult.Authorized(
                DriveAuthorizationGrant(accountEmail = "user@example.com")
            )
        )
        val repository = GoogleDriveConnectionRepository(coordinator)

        repository.requestSignIn()
        repository.disconnect()

        assertEquals(DriveConnectionState.Disconnected, repository.state.value)
    }
}

private const val RESULT_OK = -1
private const val RESULT_CANCELED = 0

private class FakeDriveAuthorizationCoordinator(
    private val requestResult: DriveAuthorizationRequestResult,
    private val resolutionResult: DriveAuthorizationGrant? = null
) : DriveAuthorizationCoordinator {
    var launchedRequest: IntentSenderRequest? = null
        private set

    override fun requestAuthorization(onResult: (DriveAuthorizationRequestResult) -> Unit) {
        onResult(requestResult)
    }

    override fun launchResolution(request: IntentSenderRequest) {
        launchedRequest = request
    }

    override fun parseResolutionResult(result: ActivityResultData): DriveAuthorizationResolutionResult =
        if (result.resultCode == RESULT_OK && resolutionResult != null) {
            DriveAuthorizationResolutionResult.Authorized(resolutionResult)
        } else {
            DriveAuthorizationResolutionResult.Cancelled
        }

}

private object FakeIntentSender {
    val instance = PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE
    ).intentSender
}
