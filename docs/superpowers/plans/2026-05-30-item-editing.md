# Item Editing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users edit the title, description, priority, and due date of any queue item; also surface priority on the detail screen.

**Architecture:** New `updateItemFields` DAO query (targeted UPDATE mirroring `updateStatus`); `QueueRepository.updateItem` implemented in `RoomQueueRepository` with an EDIT history entry; `QueueViewModel` adds `isEditingItem` StateFlow + 3 actions; new `EditItemScreen` pre-populated from `QueueItemDetailUi`; `ItemDetailScreen` gains priority display + "Edit" button.

**Tech Stack:** Room (`@Query` UPDATE), Kotlin coroutines, Jetpack Compose (Material3), `SavedStateHandle`, existing ViewModel + repository patterns in `com.quem`

---

## File map

| Action | Path |
|---|---|
| Modify | `app/src/main/java/com/quem/data/local/QueueDao.kt` |
| Modify | `app/src/main/java/com/quem/data/repository/QueueRepository.kt` |
| Modify | `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt` |
| Modify | `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt` |
| Modify | `app/src/main/java/com/quem/ui/QueueViewModel.kt` |
| Modify | `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` |
| Create | `app/src/main/java/com/quem/ui/EditItemScreen.kt` |
| Modify | `app/src/main/java/com/quem/ui/ItemDetailScreen.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |
| Modify | `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt` |

---

## Task 1: QueueDao + QueueRepository interface + FakeQueueDao + repository unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/QueueDao.kt`
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

Context: `QueueDao` currently has `updateStatus` as a targeted SQL UPDATE returning `Int` rows affected. `updateItemFields` follows the identical pattern. `QueueRepository` has no `updateItem` method yet. `FakeQueueDao` in `RoomQueueRepositoryTest.kt` implements every `QueueDao` method — adding `updateItemFields` to the interface requires a matching override.

- [ ] **Step 1: Add updateItemFields to QueueDao interface**

Open `app/src/main/java/com/quem/data/local/QueueDao.kt`. Add after the existing `updateStatus` method:

```kotlin
@Query(
    """
    UPDATE queue_items
    SET title       = :title,
        description = :description,
        priority    = :priority,
        dueDate     = :dueDate,
        updatedAt   = :updatedAt,
        syncState   = 'PENDING_SYNC'
    WHERE id = :id
    """
)
suspend fun updateItemFields(
    id: String,
    title: String,
    description: String?,
    priority: String?,
    dueDate: LocalDate?,
    updatedAt: Instant
): Int
```

- [ ] **Step 2: Add updateItem to QueueRepository interface**

Open `app/src/main/java/com/quem/data/repository/QueueRepository.kt`. Add after `changeStatus`:

```kotlin
suspend fun updateItem(
    id: String,
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem?
```

- [ ] **Step 3: Add updateItemFields to FakeQueueDao in RoomQueueRepositoryTest.kt**

Open `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`. Find `private class FakeQueueDao : QueueDao`. Add this override after the existing `updateStatus` override:

```kotlin
override suspend fun updateItemFields(
    id: String,
    title: String,
    description: String?,
    priority: String?,
    dueDate: LocalDate?,
    updatedAt: Instant
): Int {
    var updatedRows = 0
    entities.value = entities.value.map { item ->
        if (item.id == id) {
            updatedRows++
            item.copy(
                title       = title,
                description = description,
                priority    = priority,
                dueDate     = dueDate,
                updatedAt   = updatedAt,
                syncState   = SyncState.PENDING_SYNC.name
            )
        } else {
            item
        }
    }
    return updatedRows
}
```

- [ ] **Step 4: Write the failing repository unit tests**

Add these 4 tests inside `RoomQueueRepositoryTest` (after the existing `addTextAttachmentWritesAttachmentAddedHistoryEntry` test):

```kotlin
@Test
fun updateItemPatchesEditableFieldsAndPreservesOthers() = runTest {
    val dao = FakeQueueDao()
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val later = Instant.parse("2026-05-24T12:00:00Z")
    val ids = mutableListOf("item-1", "history-create", "history-edit")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Old title", description = "Old desc", priority = null, dueDate = null)

    // Advance clock for updatedAt
    val repositoryAtLater = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(later),
        idProvider = { ids.removeFirst() }
    )
    val updated = repositoryAtLater.updateItem(
        id          = "item-1",
        title       = "New title",
        description = "New desc",
        priority    = Priority.HIGH,
        dueDate     = LocalDate.parse("2026-06-01")
    )

    requireNotNull(updated)
    assertEquals("New title", updated.title)
    assertEquals("New desc", updated.description)
    assertEquals(Priority.HIGH, updated.priority)
    assertEquals(LocalDate.parse("2026-06-01"), updated.dueDate)
    assertEquals(later, updated.updatedAt)
    assertEquals(SyncState.PENDING_SYNC, updated.syncState)
    // Status and createdAt unchanged
    assertEquals(QueueStatus.QUEUED, updated.status)
    assertEquals(now, updated.createdAt)
}

@Test
fun updateItemReturnsNullWhenItemDoesNotExist() = runTest {
    val dao = FakeQueueDao()
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { "unused" }
    )

    val result = repository.updateItem(
        id          = "missing",
        title       = "Title",
        description = null,
        priority    = null,
        dueDate     = null
    )

    assertNull(result)
}

@Test
fun updateItemWritesEditHistoryEntry() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-create", "history-edit")
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)

    repository.updateItem(id = "item-1", title = "Updated", description = null, priority = null, dueDate = null)

    val history = repository.observeHistory("item-1").first()
    val editEntry = history.first { it.kind == HistoryKind.EDIT }
    assertEquals("Edited", editEntry.message)
    assertEquals(HistoryKind.EDIT, editEntry.kind)
}

@Test
fun updateItemHistoryWriteFailureDoesNotPropagateToCaller() = runTest {
    val dao = object : FakeQueueDao() {
        override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) {
            throw RuntimeException("DB error")
        }
    }
    val ids = mutableListOf("item-1", "history-edit")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    dao.upsertItem(
        queueItemEntity(
            id = "item-1",
            now = Instant.parse("2026-05-23T12:00:00Z")
        )
    )

    // Must not throw
    val result = repository.updateItem(id = "item-1", title = "New", description = null, priority = null, dueDate = null)

    assertEquals("New", result?.title)
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: FAILED — `updateItemFields` not implemented in `RoomQueueRepository`, `updateItem` not implemented.

- [ ] **Step 6: Run full suite to verify nothing else broke (compilation only)**

Run: `./gradlew :app:test`

Expected: only failures related to `updateItem` not yet implemented — existing tests pass.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/quem/data/local/QueueDao.kt \
        app/src/main/java/com/quem/data/repository/QueueRepository.kt \
        app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: add updateItemFields to QueueDao and updateItem to QueueRepository interface"
```

---

## Task 2: RoomQueueRepository.updateItem + ViewModel changes + ViewModel unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

Context: `RoomQueueRepository.changeStatus` (lines ~84–116) is the template for `updateItem` — same pattern: call DAO, check rows, write history, return fetched item. The ViewModel currently has 4 `SavedStateHandle` keys in `companion object`; add `KEY_IS_EDITING_ITEM`. `QueueItemDetailUi` needs `priorityLabel: String?` — this is a non-breaking addition since `toDetailUi` is private. `FakeQueueRepository` in `QueueViewModelTest.kt` has `searchArchive` currently returning a stub — `updateItem` must be added as a real implementation.

- [ ] **Step 1: Implement RoomQueueRepository.updateItem**

Open `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`. Add after `changeStatus`:

```kotlin
override suspend fun updateItem(
    id: String,
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem? {
    val now = clock.now()
    val updatedRows = dao.updateItemFields(
        id          = id,
        title       = title.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        priority    = priority?.name,
        dueDate     = dueDate,
        updatedAt   = now
    )
    if (updatedRows == 0) return null

    runCatching {
        dao.upsertHistoryEntry(
            HistoryEntryEntity(
                id          = idProvider(),
                queueItemId = id,
                message     = "Edited",
                kind        = HistoryKind.EDIT.name,
                createdAt   = now
            )
        )
    }.onFailure { e ->
        Log.w(TAG, "Failed to write history entry", e)
    }

    return dao.observeItem(id).first()?.toDomain()
}
```

- [ ] **Step 2: Run repository tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: BUILD SUCCESSFUL, all tests pass including the 4 new ones.

- [ ] **Step 3: Add updateItem to FakeQueueRepository in QueueViewModelTest.kt**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`. Find `private class FakeQueueRepository`. Add after `changeStatus`:

```kotlin
override suspend fun updateItem(
    id: String,
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem? {
    var updatedItem: QueueItem? = null
    items.value = items.value.map { item ->
        if (item.id == id) {
            item.copy(title = title, description = description, priority = priority, dueDate = dueDate)
                .also { updatedItem = it }
        } else {
            item
        }
    }
    return updatedItem
}
```

- [ ] **Step 4: Write the failing ViewModel unit tests**

Add these 3 tests inside `QueueViewModelTest` (after the archive tests):

```kotlin
@Test
fun selectedItemIncludesPriorityLabelWhenPrioritySet() = runTest {
    val repository = FakeQueueRepository()
    repository.items.value = listOf(
        queueItem(id = "item-1", title = "Read contract", description = null,
            status = QueueStatus.QUEUED, priority = Priority.HIGH)
    )
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertEquals("HIGH", viewModel.selectedItem.value?.priorityLabel)
}

@Test
fun selectedItemHasNullPriorityLabelWhenNoPriority() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertNull(viewModel.selectedItem.value?.priorityLabel)
}

@Test
fun saveEditUpdatesItemAndClearsEditingState() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Old title", description = null, priority = null, dueDate = null)
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    viewModel.startEdit()
    assertEquals(true, viewModel.isEditingItem.value)

    viewModel.saveEdit("New title", "New desc", "high", null)
    advanceUntilIdle()

    assertFalse(viewModel.isEditingItem.value)
    assertEquals("New title", viewModel.selectedItem.value?.title)
    assertEquals("New desc", viewModel.selectedItem.value?.description)
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: FAILED — `isEditingItem`, `startEdit`, `saveEdit`, `priorityLabel` do not exist yet.

- [ ] **Step 6: Update QueueViewModel.kt**

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`.

**6a.** Add `priorityLabel: String?` to `QueueItemDetailUi` (after `description`):

```kotlin
data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachments: List<String>,
    val history: List<String>,
    val syncIndicator: SyncIndicator?
)
```

**6b.** Update `toDetailUi` private function to pass `priorityLabel`:

```kotlin
private fun QueueItem.toDetailUi(attachments: List<String>, history: List<String>) = QueueItemDetailUi(
    id            = id,
    title         = title,
    description   = description,
    priorityLabel = priority?.name,
    dueDateLabel  = dueDate?.toString(),
    attachments   = attachments,
    history       = history,
    syncIndicator = syncState.toIndicator()
)
```

**6c.** Add `KEY_IS_EDITING_ITEM` to the `companion object` (after the existing keys):

```kotlin
private const val KEY_IS_EDITING_ITEM = "isEditingItem"
```

**6d.** Add `isEditingItem` StateFlow inside the class body (after `isShowingArchive`):

```kotlin
val isEditingItem: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_EDITING_ITEM, false)
```

**6e.** Add 3 action functions inside the class body (after `closeArchive`):

```kotlin
fun startEdit() {
    savedStateHandle[KEY_IS_EDITING_ITEM]     = true
    savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE]  = false
    savedStateHandle[KEY_IS_CREATING_ITEM]    = false
}

fun cancelEdit() {
    savedStateHandle[KEY_IS_EDITING_ITEM] = false
}

fun saveEdit(title: String, description: String?, priority: String?, dueDate: String?) {
    val id = selectedItemId.value ?: return
    viewModelScope.launch {
        repository.updateItem(
            id          = id,
            title       = title,
            description = description,
            priority    = priority.toPriorityOrNull(),
            dueDate     = dueDate.toLocalDateOrNull()
        )
        savedStateHandle[KEY_IS_EDITING_ITEM] = false
    }
}
```

- [ ] **Step 7: Run ViewModel tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: BUILD SUCCESSFUL, all tests pass including the 3 new ones.

- [ ] **Step 8: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 9: Commit**

```
git add app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt \
        app/src/main/java/com/quem/ui/QueueViewModel.kt \
        app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: implement updateItem in repository and add edit state to QueueViewModel"
```

---

## Task 3: EditItemScreen + ItemDetailScreen changes + instrumented tests + QueMApp wiring

**Files:**
- Create: `app/src/main/java/com/quem/ui/EditItemScreen.kt`
- Modify: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

Context: `EditItemScreen` is nearly identical to `CreateItemScreen` but with a different headline and pre-populated fields. `ItemDetailScreen` currently shows Back as a standalone `TextButton`; replace it with a `Row` containing Back + Edit. `QueMApp.kt` follows the same pattern as the archive branch added previously.

- [ ] **Step 1: Write the failing instrumented tests**

Open `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`. Add 2 tests:

```kotlin
@Test
fun priorityLabelDisplayedWhenSet() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            priorityLabel = "HIGH",
            attachments = emptyList(),
            history = emptyList(),
            onDismiss = {},
            onDone = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("HIGH").assertIsDisplayed()
}

@Test
fun editButtonInvokesCallback() {
    var edited = false
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            onDismiss = {},
            onDone = {},
            onBack = {},
            onEdit = { edited = true }
        )
    }

    compose.onNodeWithText("Edit").performClick()

    assertTrue(edited)
}
```

Add `import org.junit.Assert.assertTrue` if not already present.

- [ ] **Step 2: Run instrumented tests to verify they fail**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: FAILED — `priorityLabel` and `onEdit` parameters do not exist yet.

- [ ] **Step 3: Create EditItemScreen.kt**

Create `app/src/main/java/com/quem/ui/EditItemScreen.kt`:

```kotlin
package com.quem.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun EditItemScreen(
    initialTitle: String,
    initialDescription: String,
    initialPriority: String,
    initialDueDate: String,
    onSave: (title: String, description: String?, priority: String?, dueDate: String?) -> Unit,
    onCancel: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(initialTitle) }
    var description by rememberSaveable { mutableStateOf(initialDescription) }
    var priority by rememberSaveable { mutableStateOf(initialPriority) }
    var dueDate by rememberSaveable { mutableStateOf(initialDueDate) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Edit item",
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineMedium
            )
        }

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
            OutlinedTextField(
                value = priority,
                onValueChange = { priority = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Priority") },
                singleLine = true
            )
        }

        item {
            OutlinedTextField(
                value = dueDate,
                onValueChange = { dueDate = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Due date optional") },
                singleLine = true
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = {
                        onSave(
                            title.trim(),
                            description.trim().takeUnless { it.isBlank() },
                            priority.trim().takeUnless { it.isBlank() },
                            dueDate.trim().takeUnless { it.isBlank() }
                        )
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank()
                ) {
                    Text("Save", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
```

- [ ] **Step 4: Update ItemDetailScreen.kt**

Open `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`.

**4a.** Add `priorityLabel: String? = null` and `onEdit: () -> Unit = {}` to the function signature (after `history: List<String>`):

```kotlin
@Composable
fun ItemDetailScreen(
    title: String,
    description: String?,
    dueDateLabel: String?,
    attachments: List<String>,
    history: List<String>,
    priorityLabel: String? = null,
    syncIndicator: SyncIndicator? = null,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
    onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
    driveActionsEnabled: Boolean = false,
    driveUnavailableMessage: String = "Sign in to Google Drive to attach files",
    onAttachDriveFile: () -> Unit = {},
    onAttachDriveFolder: () -> Unit = {}
)
```

**4b.** Replace the first `item { TextButton(onClick = onBack) { Text("Back") } }` with a Row containing both Back and Edit:

```kotlin
item {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        TextButton(onClick = onEdit) { Text("Edit") }
    }
}
```

You need to add `import androidx.compose.foundation.layout.Row` if not already present (it is already imported).
You need `import androidx.compose.foundation.layout.Arrangement` — already imported.

**4c.** In the header `Column` (the one with title, description, due date), add priority display after the description and before the due date. Add this after the `description?.takeIf { it.isNotBlank() }?.let { ... }` block:

```kotlin
priorityLabel?.let { label ->
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodyMedium
    )
}
```

- [ ] **Step 5: Update QueMApp.kt**

Open `app/src/main/java/com/quem/app/QueMApp.kt`.

**5a.** Add `import com.quem.ui.EditItemScreen`.

**5b.** Collect the new state:
```kotlin
val isEditingItem by viewModel.isEditingItem.collectAsStateWithLifecycle()
```

**5c.** Insert the `isEditingItem` branch between `isCreatingItem` and `isShowingArchive`:

```kotlin
} else if (isEditingItem) {
    val item = selectedItem ?: return
    EditItemScreen(
        initialTitle       = item.title,
        initialDescription = item.description ?: "",
        initialPriority    = item.priorityLabel ?: "",
        initialDueDate     = item.dueDateLabel ?: "",
        onSave             = viewModel::saveEdit,
        onCancel           = viewModel::cancelEdit
    )
} else if (isShowingArchive) {
```

**5d.** Pass `onEdit = viewModel::startEdit` and `priorityLabel = item.priorityLabel` to `ItemDetailScreen`:

```kotlin
ItemDetailScreen(
    title         = item.title,
    description   = item.description,
    dueDateLabel  = item.dueDateLabel,
    priorityLabel = item.priorityLabel,
    attachments   = item.attachments,
    history       = item.history,
    syncIndicator = item.syncIndicator,
    onEdit        = viewModel::startEdit,
    onAddTextAttachment = viewModel::addTextAttachment,
    onAddLinkAttachment = viewModel::addLinkAttachment,
    driveActionsEnabled = driveConnected,
    onAttachDriveFile   = { ... },
    onAttachDriveFolder = { ... },
    onDismiss = viewModel::dismissSelectedItem,
    onDone    = viewModel::doneSelectedItem,
    onBack    = viewModel::backToList
)
```

- [ ] **Step 6: Run instrumented tests to verify they pass**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: BUILD SUCCESSFUL, all tests pass including the 2 new ones.

- [ ] **Step 7: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/quem/ui/EditItemScreen.kt \
        app/src/main/java/com/quem/ui/ItemDetailScreen.kt \
        app/src/main/java/com/quem/app/QueMApp.kt \
        app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "feat: add EditItemScreen, priority display in ItemDetailScreen, and edit navigation wiring"
```
