# Archive Search Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an archive search screen that lets users browse and search DONE/DISMISSED items, reachable via an "Archive" button in the main queue list header.

**Architecture:** Add `isShowingArchive`, `archiveQuery`, and `archiveResults` to `QueueViewModel` using the existing `if/else` navigation pattern. A new `ArchiveSearchScreen` composable reuses the existing `QueueListItemCard` (promoted `private` → `internal`). Tapping a result closes the archive screen and opens `ItemDetailScreen` via the existing `selectItem` path.

**Tech Stack:** Jetpack Compose (Material3), Kotlin coroutines/Flow, `SavedStateHandle`, existing ViewModel + Room repository pattern in `com.quem.ui`

---

## File map

| Action | Path |
|---|---|
| Modify | `app/src/main/java/com/quem/ui/QueueViewModel.kt` |
| Modify | `app/src/main/java/com/quem/ui/QueueListScreen.kt` |
| Create | `app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |
| Modify | `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` |
| Create | `app/src/androidTest/java/com/quem/ui/ArchiveSearchScreenTest.kt` |

---

## Task 1: ViewModel — archiveResults + isShowingArchive + 4 actions + unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

Context: `QueueViewModel.kt` currently has 4 `SavedStateHandle` keys (`KEY_SELECTED_STATUS`, `KEY_IS_CREATING_ITEM`, `KEY_IS_SHOWING_SETTINGS`, `KEY_SELECTED_ITEM_ID`) in its `companion object`. The `items` StateFlow (line ~67) uses `flatMapLatest` + `combine` for reactive attachment counts — use the exact same pattern for `archiveResults`. The existing `FakeQueueRepository.searchArchive` in `QueueViewModelTest.kt` (line ~505) returns `flowOf(emptyList())` — it must be updated to actually filter so tests are meaningful.

- [ ] **Step 1: Update FakeQueueRepository.searchArchive in QueueViewModelTest.kt**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`. Find `override fun searchArchive(query: String)` (around line 505). Replace the stub with a real filter:

```kotlin
override fun searchArchive(query: String): Flow<List<QueueItem>> =
    items.map { queueItems ->
        val trimmed = query.trim()
        queueItems
            .filter { it.status == QueueStatus.DONE || it.status == QueueStatus.DISMISSED }
            .filter { item ->
                trimmed.isEmpty() ||
                item.title.contains(trimmed, ignoreCase = true) ||
                item.description?.contains(trimmed, ignoreCase = true) == true
            }
            .sortedByDescending { it.updatedAt }
    }
```

- [ ] **Step 2: Write the failing unit tests**

Add these 2 tests to `QueueViewModelTest` (inside the class, after the existing `listItemsHavePendingIndicatorWhenItemIsPendingSync` test):

```kotlin
@Test
fun archiveResultsShowAllArchivedItemsWhenQueryIsBlank() = runTest {
    val repository = FakeQueueRepository()
    repository.items.value = listOf(
        queueItem(id = "done-1", title = "Done item", description = null, status = QueueStatus.DONE),
        queueItem(id = "dismissed-1", title = "Dismissed item", description = null, status = QueueStatus.DISMISSED),
        queueItem(id = "queued-1", title = "Queued item", description = null, status = QueueStatus.QUEUED)
    )
    val viewModel = QueueViewModel(repository)
    collectArchiveResults(viewModel)

    runCurrent()

    assertEquals(
        setOf("done-1", "dismissed-1"),
        viewModel.archiveResults.value.map { it.id }.toSet()
    )
}

@Test
fun archiveResultsFilterByQueryString() = runTest {
    val repository = FakeQueueRepository()
    repository.items.value = listOf(
        queueItem(id = "contract-1", title = "Read contract", description = null, status = QueueStatus.DONE),
        queueItem(id = "other-1", title = "Call accountant", description = null, status = QueueStatus.DONE)
    )
    val viewModel = QueueViewModel(repository)
    collectArchiveResults(viewModel)

    viewModel.setArchiveQuery("contract")
    advanceUntilIdle()

    assertEquals(listOf("contract-1"), viewModel.archiveResults.value.map { it.id })
}
```

Also add the `collectArchiveResults` helper alongside the existing `collectSelectedItem` / `collectItems` helpers (around line 427):

```kotlin
private fun TestScope.collectArchiveResults(viewModel: QueueViewModel) {
    backgroundScope.launch { viewModel.archiveResults.collect() }
    runCurrent()
}
```

`setArchiveQuery` and `archiveResults` don't exist yet — tests fail to compile.

- [ ] **Step 3: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: FAILED — `setArchiveQuery`, `archiveResults` do not exist yet.

- [ ] **Step 4: Add archive StateFlows and actions to QueueViewModel.kt**

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`.

**4a.** Add 2 new constants to the `companion object` (after the existing 4 keys around line 279):

```kotlin
private const val KEY_IS_SHOWING_ARCHIVE = "isShowingArchive"
private const val KEY_ARCHIVE_QUERY      = "archiveQuery"
```

**4b.** Add the 3 new `StateFlow`s inside the class body, after `selectedItem` (around line 108):

```kotlin
val isShowingArchive: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_SHOWING_ARCHIVE, false)

val archiveQuery: StateFlow<String> =
    savedStateHandle.getStateFlow(KEY_ARCHIVE_QUERY, "")

val archiveResults: StateFlow<List<QueueListItemUi>> =
    archiveQuery
        .flatMapLatest { query -> repository.searchArchive(query) }
        .flatMapLatest { items ->
            if (items.isEmpty()) flowOf(emptyList())
            else combine(
                items.map { item ->
                    repository.observeAttachments(item.id).map { attachments ->
                        item.toListItemUi(attachmentCount = attachments.size)
                    }
                }
            ) { results -> results.toList() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )
```

**4c.** Add 4 new action functions inside the class body, after `closeSettings()`:

```kotlin
fun showArchive() {
    savedStateHandle[KEY_ARCHIVE_QUERY]       = ""
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE]  = true
    savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
    savedStateHandle[KEY_SELECTED_ITEM_ID]    = null
    savedStateHandle[KEY_IS_CREATING_ITEM]    = false
}

fun closeArchive() {
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE] = false
}

fun selectArchiveItem(id: String) {
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE] = false
    savedStateHandle[KEY_SELECTED_ITEM_ID]   = id
}

fun setArchiveQuery(query: String) {
    savedStateHandle[KEY_ARCHIVE_QUERY] = query
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: BUILD SUCCESSFUL, all tests pass including the 2 new ones.

- [ ] **Step 6: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/quem/ui/QueueViewModel.kt \
        app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: add archive search state and actions to QueueViewModel"
```

---

## Task 2: ArchiveSearchScreen + QueueListScreen changes + instrumented tests

**Files:**
- Create: `app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueListScreen.kt`
- Create: `app/src/androidTest/java/com/quem/ui/ArchiveSearchScreenTest.kt`

Context: `QueueListItemCard` is currently `private` in `QueueListScreen.kt` — promote to `internal` so `ArchiveSearchScreen.kt` can reuse it (both are in `com.quem.ui`). `QueueListScreen` gets a new `onOpenArchive: () -> Unit = {}` parameter (default `{}` so existing tests don't break). The `ArchiveSearchScreen` is a pure composable that receives all data as parameters — no ViewModel dependency, fully testable in isolation.

- [ ] **Step 1: Write the failing instrumented tests**

Create `app/src/androidTest/java/com/quem/ui/ArchiveSearchScreenTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArchiveSearchScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun headlineAndBackButtonDisplayed() {
        compose.setContent {
            ArchiveSearchScreen(
                query = "",
                results = emptyList(),
                onQueryChange = {},
                onItemSelected = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("Back").assertIsDisplayed()
        compose.onNodeWithText("Archive").assertIsDisplayed()
    }

    @Test
    fun emptyStateShownWhenNoResultsAndQueryBlank() {
        compose.setContent {
            ArchiveSearchScreen(
                query = "",
                results = emptyList(),
                onQueryChange = {},
                onItemSelected = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("No archived items").assertIsDisplayed()
    }

    @Test
    fun emptyStateIncludesQueryWhenQueryNotBlank() {
        compose.setContent {
            ArchiveSearchScreen(
                query = "xyz",
                results = emptyList(),
                onQueryChange = {},
                onItemSelected = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("No results for \"xyz\"").assertIsDisplayed()
    }

    @Test
    fun resultItemDisplayedWhenResultsNonEmpty() {
        val results = listOf(
            QueueListItemUi(
                id = "item-1",
                title = "Read contract",
                priorityLabel = null,
                dueDateLabel = null,
                attachmentSummary = "0 attachments"
            )
        )
        compose.setContent {
            ArchiveSearchScreen(
                query = "contract",
                results = results,
                onQueryChange = {},
                onItemSelected = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("Read contract").assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var backed = false
        compose.setContent {
            ArchiveSearchScreen(
                query = "",
                results = emptyList(),
                onQueryChange = {},
                onItemSelected = {},
                onBack = { backed = true }
            )
        }

        compose.onNodeWithText("Back").performClick()

        assertTrue(backed)
    }

    @Test
    fun tappingResultInvokesOnItemSelectedWithCorrectId() {
        var selectedId: String? = null
        val results = listOf(
            QueueListItemUi(
                id = "item-1",
                title = "Read contract",
                priorityLabel = null,
                dueDateLabel = null,
                attachmentSummary = "0 attachments"
            )
        )
        compose.setContent {
            ArchiveSearchScreen(
                query = "",
                results = results,
                onQueryChange = {},
                onItemSelected = { selectedId = it },
                onBack = {}
            )
        }

        compose.onNodeWithText("Read contract").performClick()

        assertEquals("item-1", selectedId)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ArchiveSearchScreenTest`

Expected: FAILED — `ArchiveSearchScreen` does not exist yet.

- [ ] **Step 3: Promote QueueListItemCard from private to internal**

Open `app/src/main/java/com/quem/ui/QueueListScreen.kt`. Change line ~98:

```kotlin
// Before:
@Composable
private fun QueueListItemCard(

// After:
@Composable
internal fun QueueListItemCard(
```

- [ ] **Step 4: Add onOpenArchive parameter to QueueListScreen**

In the same file, update the `QueueListScreen` function signature to add `onOpenArchive`:

```kotlin
@Composable
fun QueueListScreen(
    selectedStatus: QueueStatus,
    items: List<QueueListItemUi>,
    onStatusSelected: (QueueStatus) -> Unit,
    onItemSelected: (String) -> Unit,
    onCreateItem: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenArchive: () -> Unit = {}       // new — default {} keeps existing call sites working
)
```

Add the "Archive" `OutlinedButton` in the header `Row`, between the Settings button and the New button:

```kotlin
Row(
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically
) {
    OutlinedButton(onClick = onOpenSettings) {
        Text("Settings")
    }
    OutlinedButton(onClick = onOpenArchive) {
        Text("Archive")
    }
    Button(onClick = onCreateItem) {
        Text("New")
    }
}
```

- [ ] **Step 5: Create ArchiveSearchScreen.kt**

Create `app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
fun ArchiveSearchScreen(
    query: String,
    results: List<QueueListItemUi>,
    onQueryChange: (String) -> Unit,
    onItemSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) {
            Text("Back")
        }
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Archive",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
            )
        }
        if (results.isEmpty()) {
            Text(
                text = if (query.isBlank()) "No archived items" else "No results for \"$query\"",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(results, key = { it.id }) { item ->
                    QueueListItemCard(item = item, onClick = { onItemSelected(item.id) })
                }
            }
        }
    }
}
```

- [ ] **Step 6: Run instrumented tests to verify they pass**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ArchiveSearchScreenTest`

Expected: BUILD SUCCESSFUL, all 6 tests pass.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt \
        app/src/main/java/com/quem/ui/QueueListScreen.kt \
        app/src/androidTest/java/com/quem/ui/ArchiveSearchScreenTest.kt
git commit -m "feat: add ArchiveSearchScreen and Archive button to QueueListScreen"
```

---

## Task 3: QueMApp navigation wiring

**Files:**
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`

Context: `QueMApp.kt` currently collects 6 `StateFlow`s from the ViewModel and routes between 4 screens via `if/else`. Add `isShowingArchive`, `archiveQuery`, `archiveResults` collection and insert the `ArchiveSearchScreen` branch between `isCreatingItem` and `selectedItem == null`. Pass `onOpenArchive = viewModel::showArchive` to `QueueListScreen`.

- [ ] **Step 1: Update QueMApp.kt**

The full updated file:

```kotlin
package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quem.data.repository.QueueRepository
import com.quem.data.sync.SyncScheduler
import com.quem.drive.DisconnectedDriveConnectionRepository
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DrivePickerCoordinator
import com.quem.drive.NoOpDrivePickerCoordinator
import com.quem.ui.ArchiveSearchScreen
import com.quem.ui.CreateItemScreen
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListScreen
import com.quem.ui.QueueViewModel
import com.quem.ui.SettingsScreen

@Composable
fun QueMApp(
    queueRepository: QueueRepository,
    driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    drivePickerCoordinator: DrivePickerCoordinator = NoOpDrivePickerCoordinator
) {
    val context = LocalContext.current
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(
            repository = queueRepository,
            driveConnectionRepository = driveConnectionRepository
        )
    )
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val isCreatingItem by viewModel.isCreatingItem.collectAsStateWithLifecycle()
    val isShowingSettings by viewModel.isShowingSettings.collectAsStateWithLifecycle()
    val isShowingArchive by viewModel.isShowingArchive.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()
    val archiveQuery by viewModel.archiveQuery.collectAsStateWithLifecycle()
    val archiveResults by viewModel.archiveResults.collectAsStateWithLifecycle()

    if (isShowingSettings) {
        SettingsScreen(
            accountEmail = driveConnectionState.accountEmail(),
            syncStatus = driveConnectionState.syncStatusLabel(),
            onManualSync = { SyncScheduler.scheduleOnce(context) },
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
    } else if (isShowingArchive) {
        ArchiveSearchScreen(
            query = archiveQuery,
            results = archiveResults,
            onQueryChange = viewModel::setArchiveQuery,
            onItemSelected = viewModel::selectArchiveItem,
            onBack = viewModel::closeArchive
        )
    } else if (selectedItem == null) {
        QueueListScreen(
            selectedStatus = selectedStatus,
            items = items,
            onStatusSelected = viewModel::selectStatus,
            onItemSelected = viewModel::selectItem,
            onCreateItem = viewModel::startCreate,
            onOpenSettings = viewModel::showSettings,
            onOpenArchive = viewModel::showArchive
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
            syncIndicator = item.syncIndicator,
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

- [ ] **Step 2: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Commit**

```
git add app/src/main/java/com/quem/app/QueMApp.kt
git commit -m "feat: wire ArchiveSearchScreen into QueMApp navigation"
```
