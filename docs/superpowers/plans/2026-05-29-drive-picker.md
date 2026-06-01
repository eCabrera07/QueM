# Drive File/Folder Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the no-op `() -> DriveSelection?` stubs in `QueMApp` with real SAF-based pickers so users can attach existing Google Drive files and folders from the item detail screen.

**Architecture:** A new `DrivePickerCoordinator` interface (thin, fakeable) mirrors the existing `DriveAuthorizationCoordinator` pattern. `SafDrivePickerCoordinator` wraps two `ActivityResultLauncher`s registered in `MainActivity` — one for `OpenDocument` (files) and one for `OpenDocumentTree` (folders) — and converts the returned SAF `Uri` into a `DriveSelection` by extracting the Drive file ID from the document ID string. `QueMApp` drops its synchronous picker lambdas and accepts a `DrivePickerCoordinator` instead; when the tapped button fires, the coordinator launches the picker and calls back with the selection.

**Tech Stack:** AndroidX Activity `ActivityResultContracts.OpenDocument` / `OpenDocumentTree`, `android.provider.DocumentsContract`, `android.content.ContentResolver`, existing `DriveSelection` data class in `DriveConnectionRepository.kt`.

---

## File Map

| Action | File |
|--------|------|
| Create | `app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt` |
| Create | `app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |
| Modify | `app/src/main/java/com/quem/app/MainActivity.kt` |
| Create | `app/src/test/java/com/quem/drive/DrivePickerCoordinatorTest.kt` |
| Create | `app/src/test/java/com/quem/drive/SafDrivePickerCoordinatorTest.kt` |
| Create | `app/src/androidTest/java/com/quem/app/QueMAppPickerTest.kt` |

---

### Task 1: DrivePickerCoordinator interface and NoOpDrivePickerCoordinator

**Files:**
- Create: `app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt`
- Create: `app/src/test/java/com/quem/drive/DrivePickerCoordinatorTest.kt`

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/quem/drive/DrivePickerCoordinatorTest.kt
package com.quem.drive

import org.junit.Assert.assertNull
import org.junit.Test

class DrivePickerCoordinatorTest {
    @Test
    fun noOpPickFileCallsCallbackWithNull() {
        val coordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator()
        var result: DriveSelection? = DriveSelection("initial", "initial", null, false)

        coordinator.pickFile { result = it }

        assertNull(result)
    }

    @Test
    fun noOpPickFolderCallsCallbackWithNull() {
        val coordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator()
        var result: DriveSelection? = DriveSelection("initial", "initial", null, true)

        coordinator.pickFolder { result = it }

        assertNull(result)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:test --tests "com.quem.drive.DrivePickerCoordinatorTest"
```

Expected: FAIL — `DrivePickerCoordinator` and `NoOpDrivePickerCoordinator` do not exist yet.

- [ ] **Step 3: Write the interface and no-op implementation**

```kotlin
// app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt
package com.quem.drive

interface DrivePickerCoordinator {
    fun pickFile(onResult: (DriveSelection?) -> Unit)
    fun pickFolder(onResult: (DriveSelection?) -> Unit)
}

class NoOpDrivePickerCoordinator : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit) = onResult(null)
    override fun pickFolder(onResult: (DriveSelection?) -> Unit) = onResult(null)
}
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:test --tests "com.quem.drive.DrivePickerCoordinatorTest"
```

Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt \
        app/src/test/java/com/quem/drive/DrivePickerCoordinatorTest.kt
git commit -m "feat: add DrivePickerCoordinator interface and no-op implementation"
```

---

### Task 2: Drive ID extraction with unit tests

**Files:**
- Create: `app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt`
- Create: `app/src/test/java/com/quem/drive/SafDrivePickerCoordinatorTest.kt`

The Google Drive SAF provider (`com.google.android.apps.docs.storage`) encodes the Drive file ID inside the document ID string in the form `acc=<n>/doc=<drive_id>`. For folder tree URIs the path can be longer: `acc=<n>/type=dir/root=<r>/doc=<drive_id>`. We extract the last `doc=` segment.

- [ ] **Step 1: Write the failing tests**

```kotlin
// app/src/test/java/com/quem/drive/SafDrivePickerCoordinatorTest.kt
package com.quem.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SafDrivePickerCoordinatorTest {
    @Test
    fun extractDriveIdFromFileDocumentId() {
        val documentId = "acc=0/doc=1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74ogVXXXX"
        assertEquals("1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74ogVXXXX", extractDriveId(documentId))
    }

    @Test
    fun extractDriveIdFromFolderDocumentId() {
        val documentId = "acc=0/type=dir/root=SomeRootId/doc=1FolderDriveId"
        assertEquals("1FolderDriveId", extractDriveId(documentId))
    }

    @Test
    fun extractDriveIdFromUrlEncodedDocumentId() {
        val documentId = "acc%3D0%2Fdoc%3D1BxiMVs0XRA5"
        assertEquals("1BxiMVs0XRA5", extractDriveId(documentId))
    }

    @Test
    fun extractDriveIdReturnsNullForNonDriveDocumentId() {
        assertNull(extractDriveId("primary:Documents/report.pdf"))
    }

    @Test
    fun extractDriveIdReturnsNullForEmptyString() {
        assertNull(extractDriveId(""))
    }

    @Test
    fun extractDriveIdReturnsNullWhenDocValueIsEmpty() {
        assertNull(extractDriveId("acc=0/doc="))
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

```
./gradlew :app:test --tests "com.quem.drive.SafDrivePickerCoordinatorTest"
```

Expected: FAIL — `extractDriveId` does not exist yet.

- [ ] **Step 3: Create SafDrivePickerCoordinator.kt with extractDriveId and the full class**

```kotlin
// app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt
package com.quem.drive

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher

class SafDrivePickerCoordinator(
    private val fileLauncher: ActivityResultLauncher<Array<String>>,
    private val folderLauncher: ActivityResultLauncher<Uri?>,
    private val contentResolver: ContentResolver
) : DrivePickerCoordinator {
    private var pendingFileCallback: ((DriveSelection?) -> Unit)? = null
    private var pendingFolderCallback: ((DriveSelection?) -> Unit)? = null

    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        pendingFileCallback = onResult
        fileLauncher.launch(arrayOf("*/*"))
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        pendingFolderCallback = onResult
        folderLauncher.launch(null)
    }

    fun handleFileResult(uri: Uri?) {
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.invoke(uri?.toFileSelection(contentResolver))
    }

    fun handleFolderResult(uri: Uri?) {
        val callback = pendingFolderCallback
        pendingFolderCallback = null
        callback?.invoke(uri?.toFolderSelection(contentResolver))
    }
}

internal fun extractDriveId(documentId: String): String? {
    val decoded = if ('%' in documentId) Uri.decode(documentId) else documentId
    return decoded.split("/")
        .lastOrNull { it.startsWith("doc=") }
        ?.removePrefix("doc=")
        ?.takeIf { it.isNotEmpty() }
}

private fun Uri.toFileSelection(contentResolver: ContentResolver): DriveSelection? {
    val documentId = runCatching { DocumentsContract.getDocumentId(this) }.getOrNull() ?: return null
    val driveId = extractDriveId(documentId) ?: return null
    val (displayName, mimeType) = queryMetadata(contentResolver)
    return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = mimeType, isFolder = false)
}

private fun Uri.toFolderSelection(contentResolver: ContentResolver): DriveSelection? {
    val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val driveId = extractDriveId(treeDocId) ?: return null
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(this, treeDocId)
    val (displayName, _) = documentUri.queryMetadata(contentResolver)
    return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = null, isFolder = true)
}

private fun Uri.queryMetadata(contentResolver: ContentResolver): Pair<String?, String?> =
    contentResolver.query(
        this,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
        null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1)
        else null to null
    } ?: (null to null)
```

- [ ] **Step 4: Run tests to verify they pass**

```
./gradlew :app:test --tests "com.quem.drive.SafDrivePickerCoordinatorTest"
```

Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt \
        app/src/test/java/com/quem/drive/SafDrivePickerCoordinatorTest.kt
git commit -m "feat: add SafDrivePickerCoordinator with Drive ID extraction"
```

---

### Task 3: Update QueMApp to use DrivePickerCoordinator

**Files:**
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`

Replace the two synchronous `() -> DriveSelection?` parameters with a single `DrivePickerCoordinator`. The `onAttachDriveFile` and `onAttachDriveFolder` lambdas passed to `ItemDetailScreen` now call the coordinator asynchronously and save the result via the ViewModel in the callback.

- [ ] **Step 1: Replace the function signature and usages in QueMApp.kt**

The full file after changes (only the function signature and the two picker callbacks change; everything else stays identical):

```kotlin
// app/src/main/java/com/quem/app/QueMApp.kt
package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quem.data.repository.QueueRepository
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DrivePickerCoordinator
import com.quem.drive.NoOpDrivePickerCoordinator
import com.quem.ui.CreateItemScreen
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListScreen
import com.quem.ui.QueueViewModel
import com.quem.ui.SettingsScreen

@Composable
fun QueMApp(
    queueRepository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    drivePickerCoordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator()
) {
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(
            repository = queueRepository,
            driveConnectionRepository = driveConnectionRepository
        )
    )
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val isCreatingItem by viewModel.isCreatingItem.collectAsStateWithLifecycle()
    val isShowingSettings by viewModel.isShowingSettings.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()

    if (isShowingSettings) {
        SettingsScreen(
            accountEmail = driveConnectionState.accountEmail(),
            syncStatus = driveConnectionState.syncStatusLabel(),
            onManualSync = {},
            onSignIn = viewModel::requestDriveSignIn,
            onDisconnect = viewModel::disconnectDrive,
            onBack = viewModel::closeSettings
        )
    } else if (isCreatingItem) {
        CreateItemScreen(
            onSave = { title, description, priority, dueDate ->
                viewModel.createItem(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDate
                )
            },
            onCancel = viewModel::cancelCreate
        )
    } else if (selectedItem == null) {
        QueueListScreen(
            selectedStatus = selectedStatus,
            items = items,
            onStatusSelected = viewModel::selectStatus,
            onItemSelected = viewModel::selectItem,
            onCreateItem = viewModel::startCreate,
            onOpenSettings = viewModel::showSettings
        )
    } else {
        val item = selectedItem ?: return
        val driveConnected = driveConnectionState is DriveConnectionState.Connected
        ItemDetailScreen(
            title = item.title,
            description = item.description,
            dueDateLabel = item.dueDateLabel,
            attachments = item.attachments,
            history = item.history,
            onAddTextAttachment = viewModel::addTextAttachment,
            onAddLinkAttachment = viewModel::addLinkAttachment,
            driveActionsEnabled = driveConnected,
            onAttachDriveFile = {
                drivePickerCoordinator.pickFile { selection ->
                    if (selection != null) {
                        viewModel.addDriveFileAttachment(
                            title = selection.name,
                            driveFileId = selection.id,
                            mimeType = selection.mimeType
                        )
                    }
                }
            },
            onAttachDriveFolder = {
                drivePickerCoordinator.pickFolder { selection ->
                    if (selection != null) {
                        viewModel.addDriveFolderAttachment(
                            title = selection.name,
                            driveFolderId = selection.id
                        )
                    }
                }
            },
            onDismiss = viewModel::dismissSelectedItem,
            onDone = viewModel::doneSelectedItem,
            onBack = viewModel::backToList
        )
    }
}

private fun DriveConnectionState.accountEmail(): String? =
    when (this) {
        is DriveConnectionState.Connected -> account.email
        DriveConnectionState.Disconnected,
        is DriveConnectionState.Error -> null
    }

private fun DriveConnectionState.syncStatusLabel(): String =
    when (this) {
        is DriveConnectionState.Connected -> "Drive connected"
        DriveConnectionState.Disconnected -> "Sync unavailable"
        is DriveConnectionState.Error -> message
    }
```

- [ ] **Step 2: Verify the project compiles**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. `DriveSelection` import in `QueMApp.kt` is no longer needed (was used by the old sync params) — the compiler will flag it if present. Remove it if the import is unused after the change.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/quem/app/QueMApp.kt
git commit -m "feat: wire DrivePickerCoordinator into QueMApp"
```

---

### Task 4: Update MainActivity to register SAF launchers and create SafDrivePickerCoordinator

**Files:**
- Modify: `app/src/main/java/com/quem/app/MainActivity.kt`

`registerForActivityResult` must be called before the Activity starts, so both launchers are registered at the top of `onCreate` — identical to how the Drive auth resolution launcher is registered. A `lateinit var` breaks the circular dependency (launchers reference the coordinator, coordinator references the launchers).

- [ ] **Step 1: Replace MainActivity.kt with the updated version**

```kotlin
// app/src/main/java/com/quem/app/MainActivity.kt
package com.quem.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.quem.drive.ActivityResultData
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.drive.SafDrivePickerCoordinator
import com.quem.ui.theme.QueMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dependencies = (application as QueMApplication).dependencies

        lateinit var driveAuthorizationCoordinator: GoogleDriveAuthorizationCoordinator
        val driveAuthorizationLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            dependencies.driveConnectionRepository.handleResolutionResult(
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
        dependencies.driveConnectionRepository.setAuthorizationCoordinator(driveAuthorizationCoordinator)

        lateinit var drivePickerCoordinator: SafDrivePickerCoordinator
        val filePickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri: Uri? ->
            drivePickerCoordinator.handleFileResult(uri)
        }
        val folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree()
        ) { uri: Uri? ->
            drivePickerCoordinator.handleFolderResult(uri)
        }
        drivePickerCoordinator = SafDrivePickerCoordinator(
            fileLauncher = filePickerLauncher,
            folderLauncher = folderPickerLauncher,
            contentResolver = contentResolver
        )

        setContent {
            QueMTheme {
                QueMApp(
                    queueRepository = dependencies.queueRepository,
                    driveConnectionRepository = dependencies.driveConnectionRepository,
                    drivePickerCoordinator = drivePickerCoordinator
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify the project compiles**

```
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/quem/app/MainActivity.kt
git commit -m "feat: register SAF launchers and wire SafDrivePickerCoordinator in MainActivity"
```

---

### Task 5: Instrumented test for QueMApp Drive picker

**Files:**
- Create: `app/src/androidTest/java/com/quem/app/QueMAppPickerTest.kt`

This test uses in-file fakes (no shared test fixtures needed) and verifies the full chain: tapping "Drive file" calls the coordinator, delivering a `DriveSelection` via the fake coordinator causes the attachment to appear in the detail screen.

- [ ] **Step 1: Write the test**

```kotlin
// app/src/androidTest/java/com/quem/app/QueMAppPickerTest.kt
package com.quem.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.data.repository.QueueRepository
import com.quem.drive.DriveAccount
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DrivePickerCoordinator
import com.quem.drive.DriveSelection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class QueMAppPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun driveFilePickerAttachesSelectionToCurrentItem() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()
        val picker = FakeDrivePickerCoordinator()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo,
                drivePickerCoordinator = picker
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Drive file").performClick()

        picker.deliverFileSelection(
            DriveSelection(id = "drive-123", name = "contract.pdf", mimeType = "application/pdf", isFolder = false)
        )

        compose.onNodeWithText("contract.pdf").assertIsDisplayed()
    }

    @Test
    fun driveFolderPickerAttachesSelectionToCurrentItem() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()
        val picker = FakeDrivePickerCoordinator()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo,
                drivePickerCoordinator = picker
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Drive folder").performClick()

        picker.deliverFolderSelection(
            DriveSelection(id = "folder-456", name = "Project folder", mimeType = null, isFolder = true)
        )

        compose.onNodeWithText("Project folder").assertIsDisplayed()
    }

    @Test
    fun cancellingPickerDoesNotAddAttachment() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()
        val picker = FakeDrivePickerCoordinator()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo,
                drivePickerCoordinator = picker
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Drive file").performClick()

        picker.deliverFileSelection(null)  // user cancelled

        compose.onNodeWithText("No attachments").assertIsDisplayed()
    }
}

private class FakeDrivePickerCoordinator : DrivePickerCoordinator {
    private var pendingFileCallback: ((DriveSelection?) -> Unit)? = null
    private var pendingFolderCallback: ((DriveSelection?) -> Unit)? = null

    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        pendingFileCallback = onResult
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        pendingFolderCallback = onResult
    }

    fun deliverFileSelection(selection: DriveSelection?) {
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.invoke(selection)
    }

    fun deliverFolderSelection(selection: DriveSelection?) {
        val callback = pendingFolderCallback
        pendingFolderCallback = null
        callback?.invoke(selection)
    }
}

private class ConnectedDriveConnectionRepository : DriveConnectionRepository {
    override val state = MutableStateFlow<DriveConnectionState>(
        DriveConnectionState.Connected(DriveAccount("test@example.com"))
    ).asStateFlow()

    override fun requestSignIn() {}
    override fun disconnect() {}
}

private class FakePickerQueueRepository : QueueRepository {
    private val items = MutableStateFlow<List<QueueItem>>(emptyList())
    private val attachments = MutableStateFlow<List<Attachment>>(emptyList())
    private var nextId = 1
    private var nextAttachmentId = 1

    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        items.map { list -> list.filter { it.status == status } }

    override fun searchArchive(query: String) = kotlinx.coroutines.flow.flowOf(emptyList<QueueItem>())

    override fun observeItem(id: String): Flow<QueueItem?> =
        items.map { list -> list.singleOrNull { it.id == id } }

    override suspend fun createItem(
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem {
        val item = QueueItem(
            id = "item-${nextId++}",
            driveId = null,
            title = title,
            description = description,
            status = QueueStatus.QUEUED,
            priority = priority,
            dueDate = dueDate,
            tags = emptyList(),
            createdAt = Instant.parse("2026-05-29T12:00:00Z"),
            updatedAt = Instant.parse("2026-05-29T12:00:00Z"),
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )
        items.value = items.value + item
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? = null

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        attachments.map { list -> list.filter { it.queueItemId == queueItemId } }

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) {}

    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) {}

    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) {
        attachments.value = attachments.value + Attachment(
            id = "attachment-${nextAttachmentId++}",
            queueItemId = queueItemId,
            type = if (isFolder) AttachmentType.DRIVE_FOLDER else AttachmentType.DRIVE_FILE,
            displayName = title,
            textContent = null,
            url = null,
            driveFileId = driveFileId,
            mimeType = mimeType,
            createdAt = Instant.parse("2026-05-29T12:00:00Z"),
            updatedAt = Instant.parse("2026-05-29T12:00:00Z"),
            syncState = SyncState.PENDING_SYNC
        )
    }
}
```

- [ ] **Step 2: Run the instrumented tests**

Connect an Android device or emulator, then run:

```
./gradlew :app:connectedAndroidTest --tests "com.quem.app.QueMAppPickerTest"
```

Expected: PASS (3 tests). If the test fails because `observeItems` emits items before the compose tree is ready, add `compose.waitForIdle()` after `createItem` and before `setContent`.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/com/quem/app/QueMAppPickerTest.kt
git commit -m "test: add instrumented tests for QueMApp Drive picker integration"
```

---

## Self-Review

### Spec Coverage

| Requirement | Covered by |
|-------------|------------|
| Launch Drive file picker when user taps "Drive file" | Task 3 (QueMApp wires coordinator) + Task 5 (test) |
| Launch Drive folder picker when user taps "Drive folder" | Task 3 (QueMApp wires coordinator) + Task 5 (test) |
| Only available when Drive is connected (`driveActionsEnabled`) | Already enforced by `ItemDetailScreen` — unchanged |
| Return `DriveSelection` with id, name, mimeType | Task 2 (`extractDriveId` + `queryMetadata`) |
| Add attachment to item after selection | Task 3 (coordinator callback → `viewModel.addDriveFileAttachment`) |
| Cancel (null selection) does not add attachment | Task 5 (third test) |
| Non-Drive SAF file silently drops (no attachment added) | Task 2 — `extractDriveId` returns null → `toFileSelection` returns null → callback receives null |
| Existing text/link attachment flows unchanged | No changes to `ItemDetailScreen`, `AttachmentEditor`, or `QueueViewModel` |

### Placeholder Check

No TBD, TODO, or "similar to Task N" references. Every step has complete code.

### Type Consistency

- `DrivePickerCoordinator.pickFile(onResult: (DriveSelection?) -> Unit)` — used consistently in Tasks 1, 2, 3, 4, 5.
- `SafDrivePickerCoordinator` constructor: `fileLauncher: ActivityResultLauncher<Array<String>>`, `folderLauncher: ActivityResultLauncher<Uri?>` — matches `ActivityResultContracts.OpenDocument()` and `OpenDocumentTree()` contract input types, and matches the launcher registrations in Task 4.
- `extractDriveId(documentId: String): String?` — defined in Task 2, called only within the same file. No cross-task name drift.
- `DriveSelection.isFolder: Boolean` — the field already exists in `DriveConnectionRepository.kt`; used correctly in both `toFileSelection` (false) and `toFolderSelection` (true).
