# Sync State UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Surface `SyncState` as a colored dot on queue list items and a dot + text label in the item detail screen, hidden when synced.

**Architecture:** Add a `SyncIndicator` enum (`PENDING`, `SYNCING`, `ERROR`) to the ViewModel layer. Both UI models (`QueueItemDetailUi`, `QueueListItemUi`) gain a nullable `syncIndicator` field — null means synced and nothing is shown. The composables render the indicator purely from this field; no sync domain knowledge leaks into the UI layer.

**Tech Stack:** Jetpack Compose (Material3), Kotlin, existing ViewModel/UI model pattern in `com.quem.ui`

---

## File map

| Action | Path |
|---|---|
| Modify | `app/src/main/java/com/quem/ui/QueueViewModel.kt` |
| Modify | `app/src/main/java/com/quem/ui/QueueListScreen.kt` |
| Modify | `app/src/main/java/com/quem/ui/ItemDetailScreen.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |
| Modify | `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` |
| Modify | `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt` |

---

## Task 1: SyncIndicator enum + UI model updates + ViewModel unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueListScreen.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

Context: `QueueViewModel.kt` currently defines `QueueItemDetailUi` without a sync field. `toListItemUi` and `toDetailUi` are private extensions at the bottom of `QueueViewModel.kt`. `QueueListItemUi` is a data class at the top of `QueueListScreen.kt`. Both are in the `com.quem.ui` package so no import is needed between them.

- [ ] **Step 1: Write the failing unit tests**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`. Add these 3 tests inside `QueueViewModelTest`:

```kotlin
@Test
fun selectedItemHasPendingIndicatorWhenItemIsPendingSync() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    // createItem() produces a PENDING_SYNC item via the queueItem() helper
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertEquals(SyncIndicator.PENDING, viewModel.selectedItem.value?.syncIndicator)
}

@Test
fun selectedItemHasNullIndicatorWhenItemIsSynced() = runTest {
    val repository = FakeQueueRepository()
    // Insert a SYNCED item directly — createItem() always creates PENDING_SYNC
    repository.items.value = listOf(
        queueItem(id = "item-1", title = "Read contract", description = null,
            status = QueueStatus.QUEUED, syncState = SyncState.SYNCED)
    )
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertNull(viewModel.selectedItem.value?.syncIndicator)
}

@Test
fun listItemsHavePendingIndicatorWhenItemIsPendingSync() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    val viewModel = QueueViewModel(repository)
    collectItems(viewModel)

    runCurrent()

    assertEquals(SyncIndicator.PENDING, viewModel.items.value.single().syncIndicator)
}
```

Also update the `queueItem()` private helper at the bottom of `QueueViewModelTest.kt` to accept a `syncState` parameter (needed by `selectedItemHasNullIndicatorWhenItemIsSynced`):

```kotlin
private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus,
    priority: Priority? = null,
    dueDate: LocalDate? = null,
    syncState: SyncState = SyncState.PENDING_SYNC   // add this parameter
) = QueueItem(
    id = id,
    driveId = null,
    title = title,
    description = description,
    status = status,
    priority = priority,
    dueDate = dueDate,
    tags = emptyList(),
    createdAt = Instant.parse("2026-05-23T12:00:00Z"),
    updatedAt = Instant.parse("2026-05-23T12:00:00Z"),
    completedAt = null,
    dismissedAt = null,
    syncState = syncState   // use the parameter
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: FAILED — `SyncIndicator` does not exist yet.

- [ ] **Step 3: Add SyncIndicator enum and toIndicator() to QueueViewModel.kt**

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`. Add `import com.quem.core.model.SyncState` to the imports block.

Add the `SyncIndicator` enum directly before `QueueItemDetailUi` (around line 32):

```kotlin
enum class SyncIndicator { PENDING, SYNCING, ERROR }
```

Add `toIndicator()` as a private top-level function at the bottom of `QueueViewModel.kt` (after the existing private functions like `toAttachmentSummary`):

```kotlin
private fun SyncState.toIndicator(): SyncIndicator? = when (this) {
    SyncState.SYNCED       -> null
    SyncState.PENDING_SYNC -> SyncIndicator.PENDING
    SyncState.SYNCING      -> SyncIndicator.SYNCING
    SyncState.ERROR        -> SyncIndicator.ERROR
}
```

- [ ] **Step 4: Add syncIndicator to QueueItemDetailUi**

In `QueueViewModel.kt`, update `QueueItemDetailUi` to add the new field:

```kotlin
data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val dueDateLabel: String?,
    val attachments: List<String>,
    val history: List<String>,
    val syncIndicator: SyncIndicator?
)
```

Update `toDetailUi` (currently at line ~290) to pass the new field:

```kotlin
private fun QueueItem.toDetailUi(attachments: List<String>, history: List<String>) = QueueItemDetailUi(
    id            = id,
    title         = title,
    description   = description,
    dueDateLabel  = dueDate?.toString(),
    attachments   = attachments,
    history       = history,
    syncIndicator = syncState.toIndicator()
)
```

- [ ] **Step 5: Add syncIndicator to QueueListItemUi**

Open `app/src/main/java/com/quem/ui/QueueListScreen.kt`. Update `QueueListItemUi` (currently at line 28):

```kotlin
data class QueueListItemUi(
    val id: String,
    val title: String,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachmentSummary: String,
    val syncIndicator: SyncIndicator?
)
```

Back in `QueueViewModel.kt`, update `toListItemUi` (currently at line ~282) to pass the new field:

```kotlin
private fun QueueItem.toListItemUi(attachmentCount: Int) = QueueListItemUi(
    id                = id,
    title             = title,
    priorityLabel     = priority?.name,
    dueDateLabel      = dueDate?.toString(),
    attachmentSummary = attachmentCount.toAttachmentSummary(),
    syncIndicator     = syncState.toIndicator()
)
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: BUILD SUCCESSFUL, all tests pass (including the 3 new ones).

- [ ] **Step 7: Run full unit test suite to verify no regression**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/quem/ui/QueueViewModel.kt \
        app/src/main/java/com/quem/ui/QueueListScreen.kt \
        app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: add SyncIndicator enum and syncIndicator field to both UI models"
```

---

## Task 2: ItemDetailScreen rendering + instrumented tests + QueMApp wiring

**Files:**
- Modify: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

Context: `ItemDetailScreen` currently has no sync state parameter. The indicator goes inside the header `Column`, after the due-date `Text` and before the `Done`/`Dismiss` buttons row. `QueMApp.kt` constructs `ItemDetailScreen` and must pass `item.syncIndicator`. The existing instrumented tests pass `syncIndicator` as its default `null` — no changes needed to existing tests.

- [ ] **Step 1: Write the failing instrumented tests**

Open `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`. Add these 2 tests:

```kotlin
@Test
fun syncIndicatorLabelDisplayedWhenPending() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            syncIndicator = SyncIndicator.PENDING,
            onDismiss = {},
            onDone = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("Pending sync").assertIsDisplayed()
}

@Test
fun syncErrorLabelDisplayedWhenError() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList(),
            syncIndicator = SyncIndicator.ERROR,
            onDismiss = {},
            onDone = {},
            onBack = {}
        )
    }

    compose.onNodeWithText("Sync error").assertIsDisplayed()
}
// Note: the null/synced case is implicitly covered — all existing tests
// omit syncIndicator (defaults to null) and none of them show a sync label.
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: FAILED — `ItemDetailScreen` has no `syncIndicator` parameter yet.

- [ ] **Step 3: Add syncIndicator parameter and rendering to ItemDetailScreen**

Open `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`. Add these imports:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
```

Add `syncIndicator: SyncIndicator? = null` to the `ItemDetailScreen` function parameters (after `history: List<String>`):

```kotlin
@Composable
fun ItemDetailScreen(
    title: String,
    description: String?,
    dueDateLabel: String?,
    attachments: List<String>,
    history: List<String>,
    syncIndicator: SyncIndicator? = null,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
    onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
    driveActionsEnabled: Boolean = false,
    driveUnavailableMessage: String = "Sign in to Google Drive to attach files",
    onAttachDriveFile: () -> Unit = {},
    onAttachDriveFolder: () -> Unit = {}
)
```

Inside the header item's `Column` (the one that shows title, description, due date), add the indicator Row after the due-date `Text`:

```kotlin
item {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        description?.takeIf { it.isNotBlank() }?.let { body ->
            Text(
                text = body,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        Text(
            text = dueDateLabel?.takeIf { it.isNotBlank() } ?: "No due date",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium
        )
        syncIndicator?.let { indicator ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(indicator.toColor(), CircleShape)
                )
                Text(
                    text = indicator.toLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = indicator.toColor()
                )
            }
        }
    }
}
```

Add private helper functions at the bottom of `ItemDetailScreen.kt` (before or after the existing private composables):

```kotlin
private fun SyncIndicator.toColor(): Color = when (this) {
    SyncIndicator.PENDING -> Color(0xFFF57C00)
    SyncIndicator.SYNCING -> Color(0xFF9E9E9E)
    SyncIndicator.ERROR   -> Color(0xFFD32F2F)
}

private fun SyncIndicator.toLabel(): String = when (this) {
    SyncIndicator.PENDING -> "Pending sync"
    SyncIndicator.SYNCING -> "Syncing…"
    SyncIndicator.ERROR   -> "Sync error"
}
```

- [ ] **Step 4: Wire syncIndicator in QueMApp.kt**

Open `app/src/main/java/com/quem/app/QueMApp.kt`. Find the `ItemDetailScreen(...)` call. Add `syncIndicator = item.syncIndicator`:

```kotlin
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
    onAttachDriveFile = { ... },
    onAttachDriveFolder = { ... },
    onDismiss = viewModel::dismissSelectedItem,
    onDone = viewModel::doneSelectedItem,
    onBack = viewModel::backToList
)
```

- [ ] **Step 5: Run instrumented tests to verify they pass**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: BUILD SUCCESSFUL, all tests pass (including the 2 new ones).

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/ui/ItemDetailScreen.kt \
        app/src/main/java/com/quem/app/QueMApp.kt \
        app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "feat: show sync state indicator in ItemDetailScreen"
```

---

## Task 3: QueueListScreen dot overlay

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueListScreen.kt`

Context: `QueueListItemCard` currently wraps content in a `Card` → `Column`. To overlay a dot in the top-right corner, the `Column` must be wrapped in a `Box`. The dot is only shown when `item.syncIndicator != null`. This task has no new unit tests — the ViewModel mapping is already tested in Task 1.

- [ ] **Step 1: Add new imports to QueueListScreen.kt**

Open `app/src/main/java/com/quem/ui/QueueListScreen.kt`. Add these imports:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
```

- [ ] **Step 2: Update QueueListItemCard to add the dot overlay**

Replace the `QueueListItemCard` composable with this version (wraps existing content in `Box`, adds the dot):

```kotlin
@Composable
private fun QueueListItemCard(
    item: QueueListItemUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.priorityLabel?.let { label ->
                        QueueItemMetadataText(label)
                    }
                    item.dueDateLabel?.let { label ->
                        QueueItemMetadataText(label)
                    }
                    QueueItemMetadataText(item.attachmentSummary)
                }
            }
            item.syncIndicator?.let { indicator ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(9.dp)
                        .background(indicator.toColor(), CircleShape)
                )
            }
        }
    }
}
```

- [ ] **Step 3: Add toColor() private function to QueueListScreen.kt**

Add this private function at the bottom of `QueueListScreen.kt` (after `QueueItemMetadataText`):

```kotlin
private fun SyncIndicator.toColor(): Color = when (this) {
    SyncIndicator.PENDING -> Color(0xFFF57C00)
    SyncIndicator.SYNCING -> Color(0xFF9E9E9E)
    SyncIndicator.ERROR   -> Color(0xFFD32F2F)
}
```

- [ ] **Step 4: Run full unit test suite to verify no regression**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/quem/ui/QueueListScreen.kt
git commit -m "feat: show sync state dot on queue list items"
```
