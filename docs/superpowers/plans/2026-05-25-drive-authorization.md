# Drive Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a real, authorization-first Google Drive sign-in handoff for the existing Drive connection scaffold.

**Architecture:** Keep app state behind `DriveConnectionRepository`, and add a fakeable `DriveAuthorizationCoordinator` that abstracts Google Identity Services authorization. `GoogleDriveConnectionRepository` will update connection state and request an Activity resolution launch when needed; `MainActivity` owns only Android Activity Result plumbing.

**Tech Stack:** Kotlin, AndroidX Activity Result APIs, Google Play Services Auth/Identity authorization APIs, StateFlow, JUnit, AndroidX Compose UI tests.

---

## Spec

Approved spec:

- `docs/superpowers/specs/2026-05-25-drive-authorization-design.md`

## File Map

Create:

- `app/src/main/java/com/quem/drive/DriveAuthorizationCoordinator.kt`
  - Defines fakeable authorization result types and coordinator interface.
- `app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt`
  - Implements `DriveConnectionRepository` using `DriveAuthorizationCoordinator`.
- `app/src/androidTest/java/com/quem/drive/GoogleDriveConnectionRepositoryTest.kt`
  - Unit tests for state transitions, launcher requests, success, cancellation, and disconnect.
- `app/src/main/java/com/quem/drive/GoogleDriveAuthorizationCoordinator.kt`
  - Android/Google Identity Services adapter.
- `app/src/test/java/com/quem/drive/GoogleDriveAuthorizationCoordinatorTest.kt`
  - Unit tests for scope constants and simple result mapping helpers.

Modify:

- `app/src/main/java/com/quem/drive/DriveConnectionRepository.kt`
  - Keep existing models and interface; no broad API break.
- `app/src/main/java/com/quem/app/AppDependencies.kt`
  - Use `GoogleDriveConnectionRepository` in production.
- `app/src/main/java/com/quem/app/MainActivity.kt`
  - Register authorization resolution launcher and pass it into the Drive connection repository.
- `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`
  - Keep fake repository tests working; add an error-status app-level check if needed.

## Task 1: Add Fakeable Authorization Contract

**Files:**

- Create: `app/src/main/java/com/quem/drive/DriveAuthorizationCoordinator.kt`
- Create: `app/src/androidTest/java/com/quem/drive/GoogleDriveConnectionRepositoryTest.kt`
- Create: `app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt`

- [ ] **Step 1: Write failing repository tests**

Create `app/src/androidTest/java/com/quem/drive/GoogleDriveConnectionRepositoryTest.kt`:

```kotlin
package com.quem.drive

import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        coordinator.completeLaunchedResolution(ActivityResultData(resultCode = RESULT_OK, data = Intent()))

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
        coordinator.completeLaunchedResolution(ActivityResultData(resultCode = RESULT_CANCELED, data = null))

        assertEquals(
            DriveConnectionState.Error("Google Drive authorization cancelled"),
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
    private var resolutionCallback: ((ActivityResultData) -> Unit)? = null
    var launchedRequest: IntentSenderRequest? = null
        private set

    override fun requestAuthorization(onResult: (DriveAuthorizationRequestResult) -> Unit) {
        onResult(requestResult)
    }

    override fun launchResolution(
        request: IntentSenderRequest,
        onResult: (ActivityResultData) -> Unit
    ) {
        launchedRequest = request
        resolutionCallback = onResult
    }

    override fun parseResolutionResult(result: ActivityResultData): DriveAuthorizationResolutionResult =
        if (result.resultCode == RESULT_OK && resolutionResult != null) {
            DriveAuthorizationResolutionResult.Authorized(resolutionResult)
        } else {
            DriveAuthorizationResolutionResult.Cancelled
        }

    fun completeLaunchedResolution(result: ActivityResultData) {
        assertNotNull(resolutionCallback)
        resolutionCallback?.invoke(result)
    }
}
```

Add this helper in the same test file:

```kotlin
private object FakeIntentSender {
    val instance = android.app.PendingIntent.getActivity(
        ApplicationProvider.getApplicationContext(),
        0,
        Intent(),
        android.app.PendingIntent.FLAG_IMMUTABLE
    ).intentSender
}
```

- [ ] **Step 2: Run failing repository tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.drive.GoogleDriveConnectionRepositoryTest"
```

Expected: FAIL because `DriveAuthorizationCoordinator`, result models, and `GoogleDriveConnectionRepository` do not exist.

- [ ] **Step 3: Add authorization contract**

Create `app/src/main/java/com/quem/drive/DriveAuthorizationCoordinator.kt`:

```kotlin
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
```

- [ ] **Step 4: Add repository implementation**

Create `app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt`:

```kotlin
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
            DriveAccount(grant.accountEmail)
        )
    }
}
```

- [ ] **Step 5: Run repository tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.drive.GoogleDriveConnectionRepositoryTest"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/drive/DriveAuthorizationCoordinator.kt app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt app/src/androidTest/java/com/quem/drive/GoogleDriveConnectionRepositoryTest.kt
git commit -m "feat: add Drive authorization connection repository"
```

## Task 2: Add Google Identity Services Coordinator

**Files:**

- Create: `app/src/main/java/com/quem/drive/GoogleDriveAuthorizationCoordinator.kt`
- Create: `app/src/test/java/com/quem/drive/GoogleDriveAuthorizationCoordinatorTest.kt`

- [ ] **Step 1: Write failing scope/mapping tests**

Create `app/src/test/java/com/quem/drive/GoogleDriveAuthorizationCoordinatorTest.kt`:

```kotlin
package com.quem.drive

import com.google.android.gms.common.api.Scope
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
    fun authorizationRequestIncludesDriveFileScope() {
        val scopes = GoogleDriveAuthorizationCoordinator.requiredScopes()

        assertEquals(listOf(Scope(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE)), scopes)
    }
}
```

- [ ] **Step 2: Run failing coordinator tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.drive.GoogleDriveAuthorizationCoordinatorTest"
```

Expected: FAIL because `GoogleDriveAuthorizationCoordinator` does not exist.

- [ ] **Step 3: Implement Google coordinator**

Create `app/src/main/java/com/quem/drive/GoogleDriveAuthorizationCoordinator.kt`:

```kotlin
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
        return if (hasResolution()) {
            DriveAuthorizationRequestResult.ResolutionRequired(
                IntentSenderRequest.Builder(getPendingIntent().intentSender).build()
            )
        } else {
            DriveAuthorizationRequestResult.Authorized(toGrant())
        }
    }

    private fun AuthorizationResult.toResolutionResult(): DriveAuthorizationResolutionResult =
        DriveAuthorizationResolutionResult.Authorized(toGrant())

    private fun AuthorizationResult.toGrant(): DriveAuthorizationGrant =
        DriveAuthorizationGrant(
            accountEmail = toGoogleSignInAccount()?.email ?: "Google Drive"
        )

    private var pendingResolutionCallback: ((ActivityResultData) -> Unit)? = null

    companion object {
        const val DRIVE_FILE_SCOPE: String = "https://www.googleapis.com/auth/drive.file"

        fun requiredScopes(): List<Scope> = listOf(Scope(DRIVE_FILE_SCOPE))
    }
}
```

- [ ] **Step 4: Run coordinator tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.drive.GoogleDriveAuthorizationCoordinatorTest"
```

Expected: PASS.

- [ ] **Step 5: Compile production Kotlin**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/drive/GoogleDriveAuthorizationCoordinator.kt app/src/test/java/com/quem/drive/GoogleDriveAuthorizationCoordinatorTest.kt
git commit -m "feat: add Google Drive authorization coordinator"
```

## Task 3: Wire Production Activity And Dependencies

**Files:**

- Modify: `app/src/main/java/com/quem/app/AppDependencies.kt`
- Modify: `app/src/main/java/com/quem/app/MainActivity.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`

- [ ] **Step 1: Update dependencies to allow Activity-provided Drive connection**

In `app/src/main/java/com/quem/app/AppDependencies.kt`, keep `queueRepository` as-is and remove the production `DisconnectedDriveConnectionRepository` property. Add:

```kotlin
fun driveConnectionRepository(
    authorizationCoordinator: com.quem.drive.DriveAuthorizationCoordinator
): DriveConnectionRepository =
    com.quem.drive.GoogleDriveConnectionRepository(authorizationCoordinator)
```

Remove the unused `DisconnectedDriveConnectionRepository` import.

- [ ] **Step 2: Register authorization resolution launcher in MainActivity**

In `app/src/main/java/com/quem/app/MainActivity.kt`, add imports:

```kotlin
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.quem.drive.ActivityResultData
import com.quem.drive.GoogleDriveAuthorizationCoordinator
```

Inside `onCreate`, before `setContent`, create a lateinit coordinator and launcher:

```kotlin
lateinit var driveAuthorizationCoordinator: GoogleDriveAuthorizationCoordinator
val driveAuthorizationLauncher = registerForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult()
) { result ->
    driveAuthorizationCoordinator.dispatchResolutionResult(
        ActivityResultData(
            resultCode = result.resultCode,
            data = result.data
        )
    )
}

driveAuthorizationCoordinator = GoogleDriveAuthorizationCoordinator(
    activity = this,
    resolutionLauncher = driveAuthorizationLauncher
)
```

Then create the Drive connection repository:

```kotlin
val driveConnectionRepository = dependencies.driveConnectionRepository(
    authorizationCoordinator = driveAuthorizationCoordinator
)
```

Pass it to `QueMApp`:

```kotlin
QueMApp(
    queueRepository = dependencies.queueRepository,
    driveConnectionRepository = driveConnectionRepository
)
```

Remove unused imports after compiling.

- [ ] **Step 3: Add app-level error status coverage**

In `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`, add a fake repository test if not already present:

```kotlin
@Test
fun settingsShowsDriveAuthorizationError() {
    val driveRepository = FakeDriveConnectionRepository.disconnected()
    val repository = FakeQueueRepository.withSampleItem()
    compose.setContent {
        QueMApp(
            queueRepository = repository,
            driveConnectionRepository = driveRepository
        )
    }

    compose.onNodeWithText("Settings").performClick()
    driveRepository.fail("Google Drive authorization unavailable")

    compose.onNodeWithText("Google Drive authorization unavailable").assertIsDisplayed()
}
```

Extend `FakeDriveConnectionRepository` with:

```kotlin
fun fail(message: String) {
    mutableState.value = DriveConnectionState.Error(message)
}
```

- [ ] **Step 4: Run app-level connected tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.QueueListScreenTest"
```

Expected: PASS.

- [ ] **Step 5: Compile production app**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/app/AppDependencies.kt app/src/main/java/com/quem/app/MainActivity.kt app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt
git commit -m "feat: wire Drive authorization into app"
```

## Task 4: Final Verification And Push

**Files:**

- No code changes unless verification exposes a defect.

- [ ] **Step 1: Run full verification**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Android\SDK'
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    $env:ANDROID_HOME = 'C:\Dev\QueM\.worktrees\repository-ui\.tools\android-sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

Expected: PASS. Connected tests should run on the available emulator.

- [ ] **Step 2: Review**

Ask for a spec/code-quality review focused on:

- only `drive.file` scope is requested,
- no broad Drive scopes are introduced,
- no full Credential Manager work appears in this slice,
- Settings sign-in uses the production Drive repository,
- cancellation/error states do not crash and surface in Settings,
- local queue and attachment flows remain local-first.

- [ ] **Step 3: Merge and push**

If working in a feature worktree:

```powershell
git checkout main
git merge --ff-only feature/drive-authorization
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
git push origin main
```

Expected: GitHub `main` includes the Drive authorization commits.

## Notes

The future Google Cloud Android OAuth client must use:

```text
Package name: com.quem.app
```

Use the SHA-1 fingerprint for the keystore that signs the installed build. Debug builds use the debug keystore.
