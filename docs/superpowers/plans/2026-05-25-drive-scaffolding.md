# Drive Scaffolding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add testable Google Drive connection and Drive attachment scaffolding without requiring real OAuth credentials yet.

**Architecture:** Keep Drive availability behind a small `DriveConnectionRepository` boundary, and keep Drive picker output as a simple `DriveSelection` model. The app remains local-first: selected Drive file/folder references are saved as local attachment metadata through the existing repository, while disconnected Drive actions show a sign-in-required message and do not mutate data.

**Tech Stack:** Kotlin, Jetpack Compose, StateFlow, Room-backed repository, JUnit, AndroidX Compose UI tests.

---

## Spec

Approved spec:

- `docs/superpowers/specs/2026-05-25-drive-scaffolding-design.md`

## File Map

Create:

- `app/src/main/java/com/quem/drive/DriveConnectionRepository.kt`
  - Defines `DriveAccount`, `DriveConnectionState`, `DriveSelection`, `DriveConnectionRepository`, and a disconnected production implementation.
- `app/src/test/java/com/quem/drive/DriveConnectionRepositoryTest.kt`
  - Covers disconnected repository behavior.

Modify:

- `app/src/main/java/com/quem/app/AppDependencies.kt`
  - Exposes the Drive connection repository to the app.
- `app/src/main/java/com/quem/app/MainActivity.kt`
  - Passes the Drive connection repository into `QueMApp`.
- `app/src/main/java/com/quem/app/QueMApp.kt`
  - Collects Drive connection state, passes Drive availability into detail UI, and accepts fake picker callbacks for tests.
- `app/src/main/java/com/quem/ui/QueueViewModel.kt`
  - Exposes Drive connection state and adds Drive file/folder attachment methods.
- `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`
  - Tests Drive file/folder attachment methods and no-selection no-ops.
- `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
  - Shows Drive actions and sign-in-required messaging while disconnected.
- `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`
  - Tests disconnected Drive actions and connected Drive callbacks.
- `app/src/main/java/com/quem/ui/SettingsScreen.kt`
  - Adds a sign-in action for disconnected state.
- `app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt`
  - Tests connected and disconnected settings actions.
- `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`
  - Adds app-level fake Drive file/folder picker tests.

## Task 1: Add Drive Connection Boundary

**Files:**

- Create: `app/src/main/java/com/quem/drive/DriveConnectionRepository.kt`
- Create: `app/src/test/java/com/quem/drive/DriveConnectionRepositoryTest.kt`

- [ ] **Step 1: Write the failing disconnected repository tests**

Create `app/src/test/java/com/quem/drive/DriveConnectionRepositoryTest.kt`:

```kotlin
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
```

- [ ] **Step 2: Run the failing Drive connection tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.drive.DriveConnectionRepositoryTest"
```

Expected: FAIL because `DriveConnectionRepository`, `DriveConnectionState`, and `DisconnectedDriveConnectionRepository` do not exist.

- [ ] **Step 3: Add the Drive connection model and disconnected implementation**

Create `app/src/main/java/com/quem/drive/DriveConnectionRepository.kt`:

```kotlin
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
```

- [ ] **Step 4: Run the Drive connection tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.drive.DriveConnectionRepositoryTest"
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/quem/drive/DriveConnectionRepository.kt app/src/test/java/com/quem/drive/DriveConnectionRepositoryTest.kt
git commit -m "feat: add Drive connection boundary"
```

## Task 2: Wire Drive Connection Into App Dependencies

**Files:**

- Modify: `app/src/main/java/com/quem/app/AppDependencies.kt`
- Modify: `app/src/main/java/com/quem/app/MainActivity.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`

- [ ] **Step 1: Update app dependency construction**

In `app/src/main/java/com/quem/app/AppDependencies.kt`, add imports:

```kotlin
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
```

Add this property inside `AppDependencies`:

```kotlin
val driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository()
```

- [ ] **Step 2: Pass the dependency from MainActivity**

In `app/src/main/java/com/quem/app/MainActivity.kt`, replace the local repository setup with:

```kotlin
val dependencies = (application as QueMApplication).dependencies
setContent {
    QueMTheme {
        QueMApp(
            queueRepository = dependencies.queueRepository,
            driveConnectionRepository = dependencies.driveConnectionRepository
        )
    }
}
```

- [ ] **Step 3: Add QueMApp parameters with safe defaults**

In `app/src/main/java/com/quem/app/QueMApp.kt`, add imports:

```kotlin
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveSelection
import com.quem.drive.DriveConnectionState
```

Change the function signature to:

```kotlin
@Composable
fun QueMApp(
    queueRepository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    onPickDriveFile: () -> DriveSelection? = { null },
    onPickDriveFolder: () -> DriveSelection? = { null }
) {
```

Create the ViewModel with both repositories:

```kotlin
val viewModel: QueueViewModel = viewModel(
    factory = QueueViewModel.factory(
        repository = queueRepository,
        driveConnectionRepository = driveConnectionRepository
    )
)
```

Collect Drive state:

```kotlin
val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()
```

In the detail branch, before `ItemDetailScreen`, compute:

```kotlin
val driveConnected = driveConnectionState is DriveConnectionState.Connected
```

Pass these detail callbacks:

```kotlin
driveActionsEnabled = driveConnected,
onAttachDriveFile = {
    val selection = onPickDriveFile() ?: return@ItemDetailScreen
    viewModel.addDriveFileAttachment(
        title = selection.name,
        driveFileId = selection.id,
        mimeType = selection.mimeType
    )
},
onAttachDriveFolder = {
    val selection = onPickDriveFolder() ?: return@ItemDetailScreen
    viewModel.addDriveFolderAttachment(
        title = selection.name,
        driveFolderId = selection.id
    )
},
```

- [ ] **Step 4: Run compile to see expected QueueViewModel/ItemDetail gaps**

Run:

```powershell
.\gradlew.bat :app:compileDebugKotlin
```

Expected: FAIL because `QueueViewModel.factory` does not accept `driveConnectionRepository`, `driveConnectionState` does not exist, and `ItemDetailScreen` does not yet accept Drive action parameters.

Do not commit this task until Tasks 3 and 4 complete the API surface and the build passes.

## Task 3: Add ViewModel Drive Attachment Methods

**Files:**

- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel tests**

In `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`, add imports:

```kotlin
import com.quem.drive.DisconnectedDriveConnectionRepository
```

Add these tests after the existing attachment tests:

```kotlin
@Test
fun addDriveFileAttachmentAddsToSelectedItem() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(
        title = "Read contract",
        description = "Legal notes",
        priority = null,
        dueDate = null
    )
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    viewModel.addDriveFileAttachment(
        title = "contract.pdf",
        driveFileId = "drive-file-id",
        mimeType = "application/pdf"
    )
    advanceUntilIdle()

    assertEquals(listOf("contract.pdf"), viewModel.selectedItem.value?.attachments)
}

@Test
fun addDriveFolderAttachmentAddsToSelectedItem() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(
        title = "Read contract",
        description = "Legal notes",
        priority = null,
        dueDate = null
    )
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    viewModel.addDriveFolderAttachment(
        title = "Project folder",
        driveFolderId = "drive-folder-id"
    )
    advanceUntilIdle()

    assertEquals(listOf("Project folder"), viewModel.selectedItem.value?.attachments)
}

@Test
fun addDriveAttachmentWithoutSelectedItemDoesNothing() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(
        title = "Read contract",
        description = "Legal notes",
        priority = null,
        dueDate = null
    )
    val viewModel = QueueViewModel(repository)

    viewModel.addDriveFileAttachment("contract.pdf", "drive-file-id", "application/pdf")
    viewModel.addDriveFolderAttachment("Project folder", "drive-folder-id")
    advanceUntilIdle()

    assertEquals(emptyList<String>(), repository.attachmentDisplayNames())
}
```

Add this test for the injected Drive state:

```kotlin
@Test
fun driveConnectionStateComesFromInjectedRepository() = runTest {
    val driveConnectionRepository = DisconnectedDriveConnectionRepository()
    val viewModel = QueueViewModel(
        repository = FakeQueueRepository(),
        driveConnectionRepository = driveConnectionRepository
    )

    driveConnectionRepository.requestSignIn()

    assertEquals(driveConnectionRepository.state.value, viewModel.driveConnectionState.value)
}
```

- [ ] **Step 2: Run failing ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest"
```

Expected: FAIL because `QueueViewModel` does not accept a Drive connection repository, does not expose `driveConnectionState`, and does not have Drive attachment methods.

- [ ] **Step 3: Implement ViewModel Drive state and methods**

In `app/src/main/java/com/quem/ui/QueueViewModel.kt`, add imports:

```kotlin
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
```

Change the constructor:

```kotlin
class QueueViewModel(
    private val repository: QueueRepository,
    private val driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle()
) : ViewModel() {
```

Add this public property after `selectedItemId`:

```kotlin
val driveConnectionState: StateFlow<DriveConnectionState> =
    driveConnectionRepository.state
```

Add these methods near the existing attachment methods:

```kotlin
fun addDriveFileAttachment(title: String, driveFileId: String, mimeType: String?) {
    addDriveAttachment(
        title = title,
        driveFileId = driveFileId,
        mimeType = mimeType,
        isFolder = false
    )
}

fun addDriveFolderAttachment(title: String, driveFolderId: String) {
    addDriveAttachment(
        title = title,
        driveFileId = driveFolderId,
        mimeType = null,
        isFolder = true
    )
}

private fun addDriveAttachment(
    title: String,
    driveFileId: String,
    mimeType: String?,
    isFolder: Boolean
) {
    val id = selectedItemId.value ?: return
    viewModelScope.launch {
        repository.addDriveAttachment(
            queueItemId = id,
            title = title,
            driveFileId = driveFileId,
            mimeType = mimeType,
            isFolder = isFolder
        )
    }
}
```

Update the factory signature:

```kotlin
fun factory(
    repository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository()
): ViewModelProvider.Factory =
```

In both `create` overloads, pass `driveConnectionRepository = driveConnectionRepository`.

- [ ] **Step 4: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest"
```

Expected: PASS.

Do not commit yet if Task 2 compile gaps remain.

## Task 4: Add Drive Actions To Item Detail

**Files:**

- Modify: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

- [ ] **Step 1: Write failing ItemDetailScreen tests**

In `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`, add tests after the existing attachment form tests:

```kotlin
@Test
fun disconnectedDriveActionsShowSignInMessageAndDoNotCallPickers() {
    var fileClicks = 0
    var folderClicks = 0

    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            driveActionsEnabled = false,
            onAttachDriveFile = { fileClicks += 1 },
            onAttachDriveFolder = { folderClicks += 1 },
            onDismiss = {},
            onDone = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("Drive file").performClick()
    compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()
    compose.onNodeWithText("Drive folder").performClick()
    compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()

    assertEquals(0, fileClicks)
    assertEquals(0, folderClicks)
}

@Test
fun connectedDriveActionsInvokeCallbacks() {
    var fileClicks = 0
    var folderClicks = 0

    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            driveActionsEnabled = true,
            onAttachDriveFile = { fileClicks += 1 },
            onAttachDriveFolder = { folderClicks += 1 },
            onDismiss = {},
            onDone = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("Drive file").performClick()
    compose.onNodeWithText("Drive folder").performClick()

    assertEquals(1, fileClicks)
    assertEquals(1, folderClicks)
}
```

- [ ] **Step 2: Run failing ItemDetailScreen tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest"
```

Expected: FAIL because `ItemDetailScreen` does not accept Drive action parameters and still hides Drive buttons.

- [ ] **Step 3: Implement Drive detail actions**

In `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`, extend the parameters:

```kotlin
driveActionsEnabled: Boolean = false,
driveUnavailableMessage: String = "Sign in to Google Drive to attach files",
onAttachDriveFile: () -> Unit = {},
onAttachDriveFolder: () -> Unit = {},
```

Add local state near the attachment form state:

```kotlin
var driveMessage by rememberSaveable { mutableStateOf<String?>(null) }
```

Replace the `AttachmentEditor` block with:

```kotlin
AttachmentEditor(
    onAddText = { openAttachmentForm(ATTACHMENT_FORM_TEXT) },
    onAddLink = { openAttachmentForm(ATTACHMENT_FORM_LINK) },
    onAttachDriveFile = {
        if (driveActionsEnabled) {
            driveMessage = null
            onAttachDriveFile()
        } else {
            driveMessage = driveUnavailableMessage
        }
    },
    onAttachDriveFolder = {
        if (driveActionsEnabled) {
            driveMessage = null
            onAttachDriveFolder()
        } else {
            driveMessage = driveUnavailableMessage
        }
    },
    showDriveActions = true
)
```

After the editor/form item and before the attachment list, add:

```kotlin
if (driveMessage != null) {
    item {
        DetailEmptyText(driveMessage.orEmpty())
    }
}
```

When opening a text/link form, clear the Drive message:

```kotlin
driveMessage = null
```

- [ ] **Step 4: Run ItemDetailScreen tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest"
```

Expected: PASS.

Do not commit yet if Task 2 compile gaps remain.

## Task 5: Update Settings Sign-In State

**Files:**

- Modify: `app/src/main/java/com/quem/ui/SettingsScreen.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt`

- [ ] **Step 1: Write failing SettingsScreen tests**

In `app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt`, update calls to include `onSignIn`.

Replace `showsAccountAndManualSyncControls` content with:

```kotlin
compose.setContent {
    SettingsScreen(
        accountEmail = "user@example.com",
        syncStatus = "Last synced just now",
        onManualSync = {},
        onSignIn = {},
        onDisconnect = {}
    )
}
```

Replace `invokesManualSyncAndDisconnectCallbacks` with:

```kotlin
@Test
fun invokesManualSyncAndDisconnectCallbacksWhenConnected() {
    var manualSyncClicks = 0
    var disconnectClicks = 0

    compose.setContent {
        SettingsScreen(
            accountEmail = "user@example.com",
            syncStatus = "Last synced just now",
            onManualSync = { manualSyncClicks += 1 },
            onSignIn = {},
            onDisconnect = { disconnectClicks += 1 }
        )
    }

    compose.onNodeWithText("Sync now").performClick()
    compose.onNodeWithText("Disconnect").performClick()

    assertEquals(1, manualSyncClicks)
    assertEquals(1, disconnectClicks)
}
```

Add disconnected sign-in coverage:

```kotlin
@Test
fun disconnectedSettingsShowsSignInAction() {
    var signInClicks = 0

    compose.setContent {
        SettingsScreen(
            accountEmail = null,
            syncStatus = "Sync unavailable",
            onManualSync = {},
            onSignIn = { signInClicks += 1 },
            onDisconnect = {}
        )
    }

    compose.onNodeWithText("Not signed in").assertIsDisplayed()
    compose.onNodeWithText("Sign in").performClick()

    assertEquals(1, signInClicks)
}
```

- [ ] **Step 2: Run failing SettingsScreen tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.SettingsScreenTest"
```

Expected: FAIL because `SettingsScreen` does not have `onSignIn`.

- [ ] **Step 3: Implement sign-in action**

In `app/src/main/java/com/quem/ui/SettingsScreen.kt`, change the signature:

```kotlin
fun SettingsScreen(
    accountEmail: String?,
    syncStatus: String,
    onManualSync: () -> Unit,
    onSignIn: () -> Unit,
    onDisconnect: () -> Unit
)
```

Replace the disconnect button with conditional action:

```kotlin
if (accountEmail == null) {
    OutlinedButton(
        onClick = onSignIn,
        modifier = Modifier.weight(1f)
    ) {
        Text("Sign in", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
} else {
    OutlinedButton(
        onClick = onDisconnect,
        modifier = Modifier.weight(1f)
    ) {
        Text("Disconnect", maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
```

- [ ] **Step 4: Run SettingsScreen tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.SettingsScreenTest"
```

Expected: PASS.

- [ ] **Step 5: Commit Tasks 2-5 together**

After Tasks 2, 3, 4, and 5 compile and tests pass, commit the connected app surface:

```powershell
git add app/src/main/java/com/quem/app/AppDependencies.kt app/src/main/java/com/quem/app/MainActivity.kt app/src/main/java/com/quem/app/QueMApp.kt app/src/main/java/com/quem/ui/QueueViewModel.kt app/src/test/java/com/quem/ui/QueueViewModelTest.kt app/src/main/java/com/quem/ui/ItemDetailScreen.kt app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt app/src/main/java/com/quem/ui/SettingsScreen.kt app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt
git commit -m "feat: scaffold Drive connection UI"
```

## Task 6: Add App-Level Fake Drive Picker Coverage

**Files:**

- Modify: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`

- [ ] **Step 1: Write failing app-level Drive picker tests**

In `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`, add imports:

```kotlin
import com.quem.drive.DriveAccount
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DriveSelection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
```

Add tests after `addingTextAttachmentFromDetailUpdatesDetailAndListCount`:

```kotlin
@Test
fun connectedDriveFilePickerAddsDriveFileAttachment() {
    val repository = FakeQueueRepository.withSampleItem()
    compose.setContent {
        QueMApp(
            queueRepository = repository,
            driveConnectionRepository = FakeDriveConnectionRepository.connected(),
            onPickDriveFile = {
                DriveSelection(
                    id = "drive-file-id",
                    name = "contract.pdf",
                    mimeType = "application/pdf",
                    isFolder = false
                )
            }
        )
    }

    compose.onNodeWithText("Read contract").performClick()
    compose.onNodeWithText("Drive file").performClick()

    compose.onNodeWithText("contract.pdf").assertIsDisplayed()
}

@Test
fun connectedDriveFolderPickerAddsDriveFolderAttachment() {
    val repository = FakeQueueRepository.withSampleItem()
    compose.setContent {
        QueMApp(
            queueRepository = repository,
            driveConnectionRepository = FakeDriveConnectionRepository.connected(),
            onPickDriveFolder = {
                DriveSelection(
                    id = "drive-folder-id",
                    name = "Project folder",
                    mimeType = null,
                    isFolder = true
                )
            }
        )
    }

    compose.onNodeWithText("Read contract").performClick()
    compose.onNodeWithText("Drive folder").performClick()

    compose.onNodeWithText("Project folder").assertIsDisplayed()
}

@Test
fun disconnectedDrivePickerShowsSignInMessageWithoutAddingAttachment() {
    var pickerCalls = 0
    val repository = FakeQueueRepository.withSampleItem()
    compose.setContent {
        QueMApp(
            queueRepository = repository,
            driveConnectionRepository = FakeDriveConnectionRepository.disconnected(),
            onPickDriveFile = {
                pickerCalls += 1
                DriveSelection(
                    id = "drive-file-id",
                    name = "contract.pdf",
                    mimeType = "application/pdf",
                    isFolder = false
                )
            }
        )
    }

    compose.onNodeWithText("Read contract").performClick()
    compose.onNodeWithText("Drive file").performClick()

    compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()
    compose.onAllNodesWithText("contract.pdf").assertCountEquals(1)
    assertEquals(0, pickerCalls)
}
```

Add this fake near the existing fake repository:

```kotlin
private class FakeDriveConnectionRepository(
    initialState: DriveConnectionState
) : DriveConnectionRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<DriveConnectionState> = mutableState

    override fun requestSignIn() {
        mutableState.value = DriveConnectionState.Connected(DriveAccount("user@example.com"))
    }

    override fun disconnect() {
        mutableState.value = DriveConnectionState.Disconnected
    }

    companion object {
        fun connected(): FakeDriveConnectionRepository =
            FakeDriveConnectionRepository(
                DriveConnectionState.Connected(DriveAccount("user@example.com"))
            )

        fun disconnected(): FakeDriveConnectionRepository =
            FakeDriveConnectionRepository(DriveConnectionState.Disconnected)
    }
}
```

- [ ] **Step 2: Run failing app-level tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest "-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.QueueListScreenTest"
```

Expected before Tasks 2-4 are complete: FAIL because `QueMApp` does not accept Drive connection or picker parameters. Expected after Tasks 2-4: PASS.

- [ ] **Step 3: Commit app-level coverage**

```powershell
git add app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt
git commit -m "test: cover Drive picker scaffolding"
```

## Task 7: Final Verification And Push

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

- [ ] **Step 2: Check git status**

Run:

```powershell
git status --short --branch
```

Expected: clean branch after commits.

- [ ] **Step 3: Push main after merge**

If working on a feature branch, merge back to `main` after verification, rerun the full verification from `main`, then push:

```powershell
git push origin main
```

Expected: GitHub `main` includes the Drive scaffolding commits.

## Notes For Future OAuth Work

This plan intentionally does not launch Google sign-in. The future OAuth slice should replace or extend `DisconnectedDriveConnectionRepository` with an implementation that uses `GoogleAuthClient`, starts the sign-in intent from `MainActivity`, and builds a real Drive service after a successful account grant.

The Google Cloud Android OAuth client should use package name:

```text
com.quem.app
```
