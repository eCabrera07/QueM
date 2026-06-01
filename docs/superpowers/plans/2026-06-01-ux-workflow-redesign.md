# QueM UX Workflow Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make QueM's current Android app workflow easier to navigate, scan, and act on by redesigning navigation state, list/detail flows, attachment entry points, and visual system.

**Architecture:** Keep the existing local-first Room repository and `QueueViewModel` ownership model, but replace scattered screen booleans with explicit UI navigation state. Add small reusable Compose UI components for top bars, chips, empty states, and bottom actions, then apply them screen by screen.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, AndroidX lifecycle ViewModel, Room-backed repository, JUnit unit tests, Compose instrumentation tests.

---

## File Structure

- Create `app/src/main/java/com/quem/ui/QueMScreen.kt`: explicit screen state model.
- Create `app/src/main/java/com/quem/ui/QueMScaffoldComponents.kt`: shared top bar, empty state, chips, and bottom action bar.
- Modify `app/src/main/java/com/quem/ui/theme/Theme.kt`: QueM-specific color scheme.
- Modify `app/src/main/java/com/quem/ui/QueueViewModel.kt`: screen state, back behavior, direct status actions, and archive/list transitions.
- Modify `app/src/main/java/com/quem/app/QueMApp.kt`: route rendering through explicit screen state.
- Modify `app/src/main/java/com/quem/ui/QueueListScreen.kt`: app bar, FAB, list cards, empty states.
- Modify `app/src/main/java/com/quem/ui/QueueStatusTabs.kt`: non-wrapping status selector.
- Modify `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`: direct workflow actions and attachment add entry point.
- Modify `app/src/main/java/com/quem/ui/EditItemScreen.kt`: metadata-only edit form and safe bottom actions.
- Modify `app/src/main/java/com/quem/ui/CreateItemScreen.kt`: safe bottom actions and visual hierarchy.
- Modify `app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt`: archive state chips and empty states.
- Modify `app/src/main/java/com/quem/ui/SettingsScreen.kt`: calmer account-focused layout.
- Modify `docs/superpowers/plans/2026-05-30-manual-test-plan.md`: align manual QA with the redesigned workflow.
- Modify tests under `app/src/test/java/com/quem/ui/` and `app/src/androidTest/java/com/quem/ui/` as listed per task.

## Task 1: Introduce Explicit Navigation State

**Files:**
- Create: `app/src/main/java/com/quem/ui/QueMScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Test: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

- [ ] **Step 1: Write failing ViewModel navigation tests**

Add tests that prove screen transitions and Back behavior are explicit:

```kotlin
@Test
fun startCreateMovesToCreateAndBackReturnsToList() = runTest {
    val viewModel = queueViewModel(repository = FakeQueueRepository.empty())

    viewModel.startCreate()
    assertEquals(QueMScreen.Create, viewModel.screen.value)

    viewModel.navigateBack()
    assertEquals(QueMScreen.List, viewModel.screen.value)
}

@Test
fun editBackReturnsToSelectedItemDetail() = runTest {
    val repository = FakeQueueRepository.withSampleItem()
    val viewModel = queueViewModel(repository = repository)

    viewModel.selectItem("sample-1")
    viewModel.startEdit()
    assertEquals(QueMScreen.Edit("sample-1"), viewModel.screen.value)

    viewModel.navigateBack()
    assertEquals(QueMScreen.Detail("sample-1"), viewModel.screen.value)
}

@Test
fun archiveItemSelectionOpensDetail() = runTest {
    val repository = FakeQueueRepository.withArchivedItem()
    val viewModel = queueViewModel(repository = repository)

    viewModel.showArchive()
    viewModel.selectArchiveItem("archived-1")

    assertEquals(QueMScreen.Detail("archived-1"), viewModel.screen.value)
}
```

- [ ] **Step 2: Run the failing tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --console=plain
```

Expected: FAIL because `QueMScreen`, `screen`, and `navigateBack()` do not exist.

- [ ] **Step 3: Add the screen model**

Create `app/src/main/java/com/quem/ui/QueMScreen.kt`:

```kotlin
package com.quem.ui

sealed interface QueMScreen {
    data object List : QueMScreen
    data object Create : QueMScreen
    data object Settings : QueMScreen
    data object Archive : QueMScreen
    data class Detail(val itemId: String) : QueMScreen
    data class Edit(val itemId: String) : QueMScreen
}
```

- [ ] **Step 4: Implement screen state in `QueueViewModel`**

Replace the public boolean screen state with a single `screen` state and keep compatibility methods by routing them:

```kotlin
private val selectedItemId: StateFlow<String?> =
    savedStateHandle.getStateFlow(KEY_SELECTED_ITEM_ID, null)

val screen: StateFlow<QueMScreen> =
    savedStateHandle.getStateFlow(KEY_SCREEN, QueMScreen.List)

fun startCreate() {
    savedStateHandle[KEY_SCREEN] = QueMScreen.Create
    savedStateHandle[KEY_SELECTED_ITEM_ID] = null
}

fun cancelCreate() {
    savedStateHandle[KEY_SCREEN] = QueMScreen.List
}

fun showSettings() {
    savedStateHandle[KEY_SCREEN] = QueMScreen.Settings
    savedStateHandle[KEY_SELECTED_ITEM_ID] = null
}

fun closeSettings() {
    savedStateHandle[KEY_SCREEN] = QueMScreen.List
}

fun showArchive() {
    savedStateHandle[KEY_ARCHIVE_QUERY] = ""
    savedStateHandle[KEY_SCREEN] = QueMScreen.Archive
    savedStateHandle[KEY_SELECTED_ITEM_ID] = null
}

fun closeArchive() {
    savedStateHandle[KEY_SCREEN] = QueMScreen.List
}

fun selectItem(id: String) {
    savedStateHandle[KEY_SELECTED_ITEM_ID] = id
    savedStateHandle[KEY_SCREEN] = QueMScreen.Detail(id)
}

fun startEdit() {
    val id = selectedItemId.value ?: return
    savedStateHandle[KEY_SCREEN] = QueMScreen.Edit(id)
}

fun cancelEdit() {
    val id = selectedItemId.value
    savedStateHandle[KEY_SCREEN] = if (id == null) QueMScreen.List else QueMScreen.Detail(id)
}

fun selectArchiveItem(id: String) {
    savedStateHandle[KEY_SELECTED_ITEM_ID] = id
    savedStateHandle[KEY_SCREEN] = QueMScreen.Detail(id)
}

fun backToList() {
    savedStateHandle[KEY_SELECTED_ITEM_ID] = null
    savedStateHandle[KEY_SCREEN] = QueMScreen.List
}

fun navigateBack() {
    when (val current = screen.value) {
        QueMScreen.List -> Unit
        QueMScreen.Create,
        QueMScreen.Settings,
        QueMScreen.Archive -> savedStateHandle[KEY_SCREEN] = QueMScreen.List
        is QueMScreen.Detail -> backToList()
        is QueMScreen.Edit -> {
            savedStateHandle[KEY_SELECTED_ITEM_ID] = current.itemId
            savedStateHandle[KEY_SCREEN] = QueMScreen.Detail(current.itemId)
        }
    }
}
```

Add this key in the companion object:

```kotlin
private const val KEY_SCREEN = "screen"
```

- [ ] **Step 5: Run tests and fix saved-state issues**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --console=plain
```

Expected: PASS. If `SavedStateHandle` cannot store `QueMScreen`, store a `screenName` string plus `selectedItemId`, then expose `screen` with a mapped `StateFlow<QueMScreen>`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/ui/QueMScreen.kt app/src/main/java/com/quem/ui/QueueViewModel.kt app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "refactor: add explicit QueM screen state"
```

## Task 2: Route Screens Through `QueMApp`

**Files:**
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Test: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`

- [ ] **Step 1: Write failing app routing tests**

Add or update tests to verify disconnected Drive does not block local queue usage and Back returns from edit to detail:

```kotlin
@Test
fun disconnectedDriveStillShowsQueueList() {
    compose.setContent {
        QueMApp(
            queueRepository = FakeQueueRepository.withSampleItem(),
            driveConnectionRepository = FakeDriveConnectionRepository.disconnected()
        )
    }

    compose.onNodeWithText("QueM").assertIsDisplayed()
    compose.onNodeWithText("Read contract").assertIsDisplayed()
}

@Test
fun editCancelReturnsToDetail() {
    compose.setContent {
        QueMApp(queueRepository = FakeQueueRepository.withSampleItem())
    }

    compose.onNodeWithText("Read contract").performClick()
    compose.onNodeWithText("Edit").performClick()
    compose.onNodeWithText("Cancel").performClick()

    compose.onNodeWithText("Read contract").assertIsDisplayed()
    compose.onNodeWithText("Attachments").assertIsDisplayed()
}
```

- [ ] **Step 2: Run the failing instrumentation test**

Run with a connected device:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: FAIL if the app still gates the whole UI behind Drive sign-in or still uses boolean routing.

- [ ] **Step 3: Render through `screen`**

In `QueMApp`, collect `screen` and replace boolean routing with:

```kotlin
val screen by viewModel.screen.collectAsStateWithLifecycle()

when (val current = screen) {
    QueMScreen.List -> QueueListScreen(
        selectedStatus = selectedStatus,
        items = items,
        onStatusSelected = viewModel::selectStatus,
        onItemSelected = viewModel::selectItem,
        onCreateItem = viewModel::startCreate,
        onOpenSettings = viewModel::showSettings,
        onOpenArchive = viewModel::showArchive
    )
    QueMScreen.Create -> CreateItemScreen(
        onSave = { title, description, priority, dueDate ->
            viewModel.createItem(title, description, priority, dueDate)
        },
        onCancel = viewModel::cancelCreate
    )
    QueMScreen.Settings -> SettingsScreen(
        accountEmail = driveConnectionState.accountEmail(),
        syncStatus = driveConnectionState.syncStatusLabel(),
        onManualSync = { SyncScheduler.scheduleOnce(context) },
        onSignIn = viewModel::requestDriveSignIn,
        onDisconnect = viewModel::disconnectDrive,
        onBack = viewModel::closeSettings
    )
    QueMScreen.Archive -> ArchiveSearchScreen(
        query = archiveQuery,
        results = archiveResults,
        onQueryChange = viewModel::setArchiveQuery,
        onItemSelected = viewModel::selectArchiveItem,
        onBack = viewModel::closeArchive
    )
    is QueMScreen.Detail -> selectedItem?.let { item ->
        ItemDetailScreen(
            title = item.title,
            description = item.description,
            dueDateLabel = item.dueDateLabel,
            priorityLabel = item.priorityLabel,
            attachments = item.attachments,
            history = item.history,
            syncIndicator = item.syncIndicator,
            currentStatus = item.status,
            onStatusChange = viewModel::changeStatusOfSelectedItem,
            onEdit = viewModel::startEdit,
            onDeleteAttachment = viewModel::deleteAttachment,
            onRenameAttachment = viewModel::updateAttachmentTitle,
            onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
            onBack = viewModel::backToList
        )
    }
    is QueMScreen.Edit -> selectedItem?.let { item ->
        EditItemScreen(
            initialTitle = item.title,
            initialDescription = item.description ?: "",
            initialPriority = item.priorityLabel ?: "",
            initialDueDate = item.dueDateIso ?: "",
            onSave = viewModel::saveEdit,
            onCancel = viewModel::cancelEdit
        )
    }
}
```

Remove the top-level `SignInScreen` early return so disconnected users can still use local queue features.

- [ ] **Step 4: Run routing tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS for the updated routing tests.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/quem/app/QueMApp.kt app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt
git commit -m "refactor: route QueM screens through explicit state"
```

## Task 3: Add Shared UX Components and Theme

**Files:**
- Create: `app/src/main/java/com/quem/ui/QueMScaffoldComponents.kt`
- Modify: `app/src/main/java/com/quem/ui/theme/Theme.kt`
- Test: `app/src/androidTest/java/com/quem/ui/QueMScaffoldComponentsTest.kt`

- [ ] **Step 1: Write component display tests**

Create `QueMScaffoldComponentsTest.kt`:

```kotlin
package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class QueMScaffoldComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun topBarShowsTitleAndActions() {
        compose.setContent {
            QueMTopBar(
                title = "QueM",
                onBack = null,
                onSettings = {},
                onArchive = {}
            )
        }

        compose.onNodeWithText("QueM").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Archive").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsActionText() {
        compose.setContent {
            QueMEmptyState(
                title = "No queued items",
                message = "Capture the next thing you do not want to lose.",
                actionLabel = "New item",
                onAction = {}
            )
        }

        compose.onNodeWithText("No queued items").assertIsDisplayed()
        compose.onNodeWithText("New item").assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Run the failing component tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: FAIL because the shared components do not exist.

- [ ] **Step 3: Implement shared components**

Create `QueMScaffoldComponents.kt` with these public composables:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.quem.core.model.QueueStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueMTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    onArchive: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null
) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (onEdit != null) {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit")
                }
            }
            if (onArchive != null) {
                IconButton(onClick = onArchive) {
                    Icon(Icons.Filled.Archive, contentDescription = "Archive")
                }
            }
            if (onSettings != null) {
                IconButton(onClick = onSettings) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings")
                }
            }
        }
    )
}

@Composable
fun QueMEmptyState(
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null && onAction != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun PriorityChip(label: String, modifier: Modifier = Modifier) {
    AssistChip(onClick = {}, label = { Text(label.lowercase().replaceFirstChar { it.uppercase() }) }, modifier = modifier)
}

@Composable
fun StatusChip(status: QueueStatus, modifier: Modifier = Modifier) {
    AssistChip(onClick = {}, label = { Text(status.toUiLabel()) }, modifier = modifier)
}

@Composable
fun SyncStatusChip(indicator: SyncIndicator, modifier: Modifier = Modifier) {
    AssistChip(onClick = {}, label = { Text(indicator.toLabel()) }, modifier = modifier)
}

@Composable
fun BottomActionBar(
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    primaryEnabled: Boolean = true
) {
    Surface(shadowElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) {
                Text(secondaryLabel)
            }
            Button(onClick = onPrimary, modifier = Modifier.weight(1f), enabled = primaryEnabled) {
                Text(primaryLabel)
            }
        }
    }
}

fun QueueStatus.toUiLabel(): String = when (this) {
    QueueStatus.QUEUED -> "Queued"
    QueueStatus.IN_PROGRESS -> "In Progress"
    QueueStatus.DONE -> "Done"
    QueueStatus.DISMISSED -> "Dismissed"
}
```

- [ ] **Step 4: Define a QueM color scheme**

Update `Theme.kt`:

```kotlin
package com.quem.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val QueMColorScheme = lightColorScheme(
    primary = Color(0xFF5E4BB6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E1FF),
    onPrimaryContainer = Color(0xFF20124D),
    secondary = Color(0xFF006B5F),
    onSecondary = Color.White,
    tertiary = Color(0xFFB26A00),
    error = Color(0xFFBA1A1A),
    surface = Color(0xFFFFFBFE),
    surfaceVariant = Color(0xFFE8E1EA),
    onSurface = Color(0xFF1C1B1F),
    onSurfaceVariant = Color(0xFF49454F)
)

@Composable
fun QueMTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = QueMColorScheme,
        content = content
    )
}
```

- [ ] **Step 5: Run component tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS for `QueMScaffoldComponentsTest`.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/ui/QueMScaffoldComponents.kt app/src/main/java/com/quem/ui/theme/Theme.kt app/src/androidTest/java/com/quem/ui/QueMScaffoldComponentsTest.kt
git commit -m "feat: add QueM shared UI components"
```

## Task 4: Redesign Queue List and Status Controls

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueListScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueStatusTabs.kt`
- Test: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`

- [ ] **Step 1: Write failing list UI tests**

Add tests for icon actions, empty state, and non-wrapping status labels:

```kotlin
@Test
fun queueListUsesIconActionsAndEmptyState() {
    compose.setContent {
        QueueListScreen(
            selectedStatus = QueueStatus.QUEUED,
            items = emptyList(),
            onStatusSelected = {},
            onItemSelected = {},
            onCreateItem = {},
            onOpenSettings = {},
            onOpenArchive = {}
        )
    }

    compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
    compose.onNodeWithContentDescription("Archive").assertIsDisplayed()
    compose.onNodeWithContentDescription("Create item").assertIsDisplayed()
    compose.onNodeWithText("No queued items").assertIsDisplayed()
}

@Test
fun dismissedStatusLabelIsDisplayedOnOneLine() {
    compose.setContent {
        QueueStatusTabs(
            selectedStatus = QueueStatus.QUEUED,
            onStatusSelected = {}
        )
    }

    compose.onNodeWithText("Dismissed").assertIsDisplayed()
}
```

- [ ] **Step 2: Run failing tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: FAIL because the FAB and empty state are missing.

- [ ] **Step 3: Update `QueueStatusTabs`**

Use a scrollable status row or compact filter chips. Preferred implementation:

```kotlin
@Composable
fun QueueStatusTabs(
    selectedStatus: QueueStatus,
    onStatusSelected: (QueueStatus) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(StatusTabs, key = { it.first.name }) { (status, label) ->
            FilterChip(
                selected = status == selectedStatus,
                onClick = { onStatusSelected(status) },
                label = { Text(label, maxLines = 1) }
            )
        }
    }
}
```

Add imports for `LazyRow`, `items`, `PaddingValues`, `Arrangement`, `Modifier`, `dp`, and `FilterChip`.

- [ ] **Step 4: Update `QueueListScreen`**

Use a Scaffold, top bar, FAB, empty state, and improved cards:

```kotlin
Scaffold(
    topBar = {
        QueMTopBar(
            title = "QueM",
            onSettings = onOpenSettings,
            onArchive = onOpenArchive
        )
    },
    floatingActionButton = {
        FloatingActionButton(onClick = onCreateItem) {
            Icon(Icons.Filled.Add, contentDescription = "Create item")
        }
    }
) { padding ->
    Column(modifier = Modifier.fillMaxSize().padding(padding)) {
        QueueStatusTabs(selectedStatus = selectedStatus, onStatusSelected = onStatusSelected)
        if (items.isEmpty()) {
            QueMEmptyState(
                title = "No ${selectedStatus.toUiLabel().lowercase()} items",
                message = "Capture the next thing you do not want to lose.",
                actionLabel = "New item",
                onAction = onCreateItem
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { item ->
                    QueueListItemCard(item = item, onClick = { onItemSelected(item.id) })
                }
            }
        }
    }
}
```

- [ ] **Step 5: Run list tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS for list and status tests.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/ui/QueueListScreen.kt app/src/main/java/com/quem/ui/QueueStatusTabs.kt app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt
git commit -m "feat: improve queue list workflow surface"
```

## Task 5: Move Primary Workflow Actions to Item Detail

**Files:**
- Modify: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Test: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

- [ ] **Step 1: Write failing detail action tests**

Add tests for direct workflow buttons:

```kotlin
@Test
fun queuedItemShowsStartDoneAndDismissActions() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            currentStatus = QueueStatus.QUEUED,
            onBack = {}
        )
    }

    compose.onNodeWithText("Start").assertIsDisplayed()
    compose.onNodeWithText("Mark done").assertIsDisplayed()
    compose.onNodeWithText("Dismiss").assertIsDisplayed()
}

@Test
fun doneItemShowsRestoreAction() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            currentStatus = QueueStatus.DONE,
            onBack = {}
        )
    }

    compose.onNodeWithText("Restore").assertIsDisplayed()
}
```

- [ ] **Step 2: Run failing detail tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: FAIL because direct action buttons are not present.

- [ ] **Step 3: Add `StatusActionRow`**

Add this composable in `ItemDetailScreen.kt`:

```kotlin
@Composable
private fun StatusActionRow(
    currentStatus: QueueStatus,
    onStatusChange: (QueueStatus) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when (currentStatus) {
            QueueStatus.QUEUED -> {
                Button(onClick = { onStatusChange(QueueStatus.IN_PROGRESS) }, modifier = Modifier.weight(1f)) { Text("Start") }
                Button(onClick = { onStatusChange(QueueStatus.DONE) }, modifier = Modifier.weight(1f)) { Text("Mark done") }
                OutlinedButton(onClick = { onStatusChange(QueueStatus.DISMISSED) }, modifier = Modifier.weight(1f)) { Text("Dismiss") }
            }
            QueueStatus.IN_PROGRESS -> {
                Button(onClick = { onStatusChange(QueueStatus.DONE) }, modifier = Modifier.weight(1f)) { Text("Mark done") }
                OutlinedButton(onClick = { onStatusChange(QueueStatus.QUEUED) }, modifier = Modifier.weight(1f)) { Text("Move to queued") }
                OutlinedButton(onClick = { onStatusChange(QueueStatus.DISMISSED) }, modifier = Modifier.weight(1f)) { Text("Dismiss") }
            }
            QueueStatus.DONE,
            QueueStatus.DISMISSED -> {
                Button(onClick = { onStatusChange(QueueStatus.QUEUED) }, modifier = Modifier.fillMaxWidth()) { Text("Restore") }
            }
        }
    }
}
```

Place it above the status dropdown or replace the visible dropdown if tests do not require it.

- [ ] **Step 4: Run detail tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS for direct status action tests.

- [ ] **Step 5: Commit**

```powershell
git add app/src/main/java/com/quem/ui/ItemDetailScreen.kt app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "feat: add direct item status actions"
```

## Task 6: Add Attachments from Detail and Simplify Edit

**Files:**
- Modify: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/EditItemScreen.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Test: `app/src/androidTest/java/com/quem/ui/AttachmentEditorTest.kt`
- Test: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

- [ ] **Step 1: Write failing attachment entry tests**

Add a detail test that opens the text attachment form without entering edit:

```kotlin
@Test
fun detailCanAddTextAttachment() {
    compose.setContent {
        QueMApp(queueRepository = FakeQueueRepository.withSampleItem())
    }

    compose.onNodeWithText("Read contract").performClick()
    compose.onNodeWithText("Text").performClick()
    compose.onNodeWithText("Attachment title").performTextInput("Notes")
    compose.onNode(hasText("Text") and hasSetTextAction()).performTextInput("Check clause 7")
    compose.onNodeWithText("Save").performClick()

    compose.onNodeWithText("Notes").assertIsDisplayed()
}
```

Add an edit test that proves attachment action buttons are not shown there:

```kotlin
@Test
fun editScreenDoesNotShowAttachmentAddButtons() {
    compose.setContent {
        EditItemScreen(
            initialTitle = "Read contract",
            initialDescription = "",
            initialPriority = "",
            initialDueDate = "",
            onSave = { _, _, _, _ -> },
            onCancel = {}
        )
    }

    compose.onNodeWithText("Text").assertDoesNotExist()
    compose.onNodeWithText("Drive file").assertDoesNotExist()
}
```

- [ ] **Step 2: Run failing attachment tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: FAIL because detail does not expose attachment add flows.

- [ ] **Step 3: Move attachment form state into detail**

Add the same attachment form state currently used in `EditItemScreen` to `ItemDetailScreen`, passing these callbacks from `QueMApp`:

```kotlin
onAddTextAttachment = viewModel::addTextAttachment,
onAddLinkAttachment = viewModel::addLinkAttachment,
onAttachDriveFile = { title, id, mime -> viewModel.addDriveFileAttachment(title, id, mime) },
onAttachDriveFolder = { title, id -> viewModel.addDriveFolderAttachment(title, id) }
```

In `ItemDetailScreen`, add parameters:

```kotlin
onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
onAttachDriveFile: (title: String, driveFileId: String, mimeType: String?) -> Unit = { _, _, _ -> },
onAttachDriveFolder: (title: String, driveFolderId: String) -> Unit = { _, _ -> }
```

Render `AttachmentEditor` and `AttachmentForm` in the Attachments section.

- [ ] **Step 4: Remove attachment add controls from edit**

In `EditItemScreen`, remove the attachment list, attachment form state, and `AttachmentEditor` rendering. Keep only title, description, priority, due date, Cancel, and Save.

- [ ] **Step 5: Run attachment tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS for detail attachment add and edit simplification tests.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/ui/ItemDetailScreen.kt app/src/main/java/com/quem/ui/EditItemScreen.kt app/src/main/java/com/quem/app/QueMApp.kt app/src/androidTest/java/com/quem/ui/AttachmentEditorTest.kt app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "feat: add attachments from item detail"
```

## Task 7: Fix Create and Edit Bottom Actions

**Files:**
- Modify: `app/src/main/java/com/quem/ui/CreateItemScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/EditItemScreen.kt`
- Test: `app/src/androidTest/java/com/quem/ui/CreateItemScreenTest.kt`
- Test: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

- [ ] **Step 1: Write bottom action tests**

Update create and edit screen tests to assert Save and Cancel remain displayed:

```kotlin
@Test
fun createShowsBottomActions() {
    compose.setContent {
        CreateItemScreen(
            onSave = { _, _, _, _ -> },
            onCancel = {}
        )
    }

    compose.onNodeWithText("Cancel").assertIsDisplayed()
    compose.onNodeWithText("Save").assertIsDisplayed()
}
```

```kotlin
@Test
fun editShowsBottomActions() {
    compose.setContent {
        EditItemScreen(
            initialTitle = "Read contract",
            initialDescription = "",
            initialPriority = "",
            initialDueDate = "",
            onSave = { _, _, _, _ -> },
            onCancel = {}
        )
    }

    compose.onNodeWithText("Cancel").assertIsDisplayed()
    compose.onNodeWithText("Save").assertIsDisplayed()
}
```

- [ ] **Step 2: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS before or after implementation, but visual QA in Step 5 must confirm no clipping.

- [ ] **Step 3: Use `BottomActionBar`**

Convert Create and Edit to `Scaffold(bottomBar = { BottomActionBar(...) })`. Keep the form in a `LazyColumn` with bottom content padding:

```kotlin
Scaffold(
    topBar = { QueMTopBar(title = "Create item", onBack = onCancel) },
    bottomBar = {
        BottomActionBar(
            primaryLabel = "Save",
            onPrimary = { onSave(title.trim(), description.trim().takeUnless { it.isBlank() }, priority, dueDate) },
            secondaryLabel = "Cancel",
            onSecondary = onCancel,
            primaryEnabled = title.isNotBlank()
        )
    }
) { padding ->
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                singleLine = true
            )
        }
        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                minLines = 3
            )
        }
        item {
            PriorityDropdown(selected = priority, onSelect = { priority = it })
        }
        item {
            DueDatePicker(selected = dueDate, onSelect = { dueDate = it })
        }
    }
}
```

Use the same structure for `EditItemScreen`.

- [ ] **Step 4: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS.

- [ ] **Step 5: Run visual QA on device**

Install and capture create/edit screens:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:installDebug --console=plain
& 'C:\Android\SDK\platform-tools\adb.exe' shell monkey -p com.quem.app 1
```

Expected: Create and Edit Save/Cancel actions are visible above the Android navigation bar.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/ui/CreateItemScreen.kt app/src/main/java/com/quem/ui/EditItemScreen.kt app/src/androidTest/java/com/quem/ui/CreateItemScreenTest.kt app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "fix: keep form actions above system navigation"
```

## Task 8: Polish Archive and Settings

**Files:**
- Modify: `app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/SettingsScreen.kt`
- Test: `app/src/androidTest/java/com/quem/ui/ArchiveSearchScreenTest.kt`
- Test: `app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt`

- [ ] **Step 1: Write archive and settings tests**

Add archive empty state tests:

```kotlin
@Test
fun archiveShowsEmptyStateWhenNoArchivedItems() {
    compose.setContent {
        ArchiveSearchScreen(
            query = "",
            results = emptyList(),
            onQueryChange = {},
            onItemSelected = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("Nothing archived yet").assertIsDisplayed()
}

@Test
fun archiveShowsNoResultsForQuery() {
    compose.setContent {
        ArchiveSearchScreen(
            query = "missing",
            results = emptyList(),
            onQueryChange = {},
            onItemSelected = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("No results for \"missing\"").assertIsDisplayed()
}
```

Add settings tests:

```kotlin
@Test
fun settingsShowsAccountAndSyncActions() {
    compose.setContent {
        SettingsScreen(
            accountEmail = "user@example.com",
            syncStatus = "Drive connected",
            onManualSync = {},
            onDisconnect = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("user@example.com").assertIsDisplayed()
    compose.onNodeWithText("Drive connected").assertIsDisplayed()
    compose.onNodeWithText("Sync now").assertIsDisplayed()
    compose.onNodeWithText("Disconnect").assertIsDisplayed()
}
```

- [ ] **Step 2: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: Archive empty-state tests fail until copy is updated.

- [ ] **Step 3: Update Archive**

Use `QueMTopBar`, a search text field, list cards, and `QueMEmptyState`:

```kotlin
val emptyTitle = if (query.isBlank()) "Nothing archived yet" else "No results for \"$query\""
val emptyMessage = if (query.isBlank()) {
    "Done and dismissed items will appear here."
} else {
    "Try a different title or attachment keyword."
}
QueMEmptyState(title = emptyTitle, message = emptyMessage)
```

Add status labels to archive cards by extending `QueueListItemUi`:

```kotlin
data class QueueListItemUi(
    val id: String,
    val title: String,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachmentSummary: String,
    val syncIndicator: SyncIndicator? = null,
    val statusLabel: String? = null
)
```

Update `QueueItem.toListItemUi`:

```kotlin
private fun QueueItem.toListItemUi(attachmentCount: Int) = QueueListItemUi(
    id = id,
    title = title,
    priorityLabel = priority?.name,
    dueDateLabel = dueDate?.toString(),
    attachmentSummary = attachmentCount.toAttachmentSummary(),
    syncIndicator = syncState.toIndicator(),
    statusLabel = status.toUiLabel()
)
```

Render the status chip in `QueueListItemCard` when `item.statusLabel != null`:

```kotlin
item.statusLabel?.let { label ->
    QueueItemMetadataText(label)
}
```

- [ ] **Step 4: Update Settings**

Use `QueMTopBar(title = "Settings", onBack = onBack)` and group account and sync content:

```kotlin
Column(
    modifier = Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp)
) {
    Text(accountEmail ?: "Not signed in", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    Text(syncStatus, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Button(onClick = onManualSync, modifier = Modifier.weight(1f)) { Text("Sync now") }
        OutlinedButton(onClick = if (accountEmail == null) onSignIn else onDisconnect, modifier = Modifier.weight(1f)) {
            Text(if (accountEmail == null) "Sign in" else "Disconnect")
        }
    }
}
```

- [ ] **Step 5: Run tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: PASS for archive and settings tests.

- [ ] **Step 6: Commit**

```powershell
git add app/src/main/java/com/quem/ui/ArchiveSearchScreen.kt app/src/main/java/com/quem/ui/SettingsScreen.kt app/src/androidTest/java/com/quem/ui/ArchiveSearchScreenTest.kt app/src/androidTest/java/com/quem/ui/SettingsScreenTest.kt
git commit -m "feat: polish archive and settings screens"
```

## Task 9: Update Manual QA and Final Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-05-30-manual-test-plan.md`
- Test: full JVM and connected Android test suites

- [ ] **Step 1: Update manual test plan workflow**

Revise the manual plan so it matches the redesigned UI:

```markdown
## 1. Core Queue Flow

### 1a. Create item
1. Tap the create floating action button.
2. Enter title: `Read contract`, priority: `High`, due date: `2026-06-15`.
3. Tap **Save**.

Expected:
- Item appears in Queued.
- Item detail opens or the item is visible from Queued.
- Priority, due date, attachment count, and sync state are visible.

### 1b. Move through workflow
1. Open `Read contract`.
2. Tap **Start**.
3. Open the item from In Progress.
4. Tap **Mark done**.

Expected:
- Item moves from Queued to In Progress, then Done.
- Archive search includes the Done item.
```

- [ ] **Step 2: Run JVM tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run connected Android tests**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:connectedDebugAndroidTest --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Install debug build and inspect key screens**

Run:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat :app:installDebug --console=plain
& 'C:\Android\SDK\platform-tools\adb.exe' shell monkey -p com.quem.app 1
```

Expected:
- Queue list top bar is not crowded.
- `Dismissed` no longer wraps as `Dismisse` plus `d`.
- Create and Edit bottom actions are visible above system navigation.
- Detail has clear workflow actions.
- Archive and Settings have consistent top bars and empty states.

- [ ] **Step 5: Commit docs and verification updates**

```powershell
git add docs/superpowers/plans/2026-05-30-manual-test-plan.md
git commit -m "docs: refresh manual QA for UX redesign"
```

## Self-Review Notes

- Spec coverage: Navigation, list, detail actions, detail attachments, edit simplification, create/edit safe actions, archive/settings polish, visual system, and manual QA are covered.
- Completeness scan: Each task has concrete files, tests, commands, and expected outcomes.
- Type consistency: `QueMScreen`, `QueMTopBar`, `QueMEmptyState`, `BottomActionBar`, `PriorityChip`, `StatusChip`, `SyncStatusChip`, and `toUiLabel()` names are consistent across tasks.
