# Queue Create Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist priority and optional due date when users create queue items.

**Architecture:** Extend the existing repository creation contract to carry domain metadata, then let `RoomQueueRepository` save it through the existing Room fields. `QueueViewModel` remains the normalization boundary for free-text create-form input, parsing priority and due date before calling the repository.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX ViewModel/SavedStateHandle, Kotlin Flow, Room, kotlinx-coroutines-test, AndroidX Compose UI tests.

---

## File Structure

- `app/src/main/java/com/quem/data/repository/QueueRepository.kt`: update the `createItem` contract.
- `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`: persist priority and due date during item creation.
- `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`: update existing calls and add repository metadata persistence coverage.
- `app/src/main/java/com/quem/ui/QueueViewModel.kt`: accept create-form metadata strings and parse them to domain values.
- `app/src/main/java/com/quem/app/QueMApp.kt`: forward priority and due date from `CreateItemScreen`.
- `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`: update fake repository and add parsing tests.
- `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`: update fake repository signature for app-level tests.

## Environment Setup

Use Android Studio's bundled JBR and the clean SDK path first. If `C:\Android\SDK` does not yet have platform 36 installed, use the temporary SDK from the existing repository UI worktree for verification until Android Studio installs the missing platform.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Android\SDK'
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    $env:ANDROID_HOME = 'C:\Dev\QueM\.worktrees\repository-ui\.tools\android-sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
```

## Task 1: Persist Metadata In Repository Creation

**Files:**
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

- [ ] **Step 1: Write repository metadata persistence test**

In `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`, add this test after `createItemCreatesQueuedPendingSyncItem`:

```kotlin
@Test
fun createItemPersistsPriorityAndDueDate() = runTest {
    val dao = FakeQueueDao()
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { "item-1" }
    )

    val created = repository.createItem(
        title = "Read contract",
        description = "Legal notes",
        priority = Priority.HIGH,
        dueDate = LocalDate.parse("2026-05-30")
    )

    assertEquals(Priority.HIGH, created.priority)
    assertEquals(LocalDate.parse("2026-05-30"), created.dueDate)
    assertEquals(created, dao.items.single())
}
```

- [ ] **Step 2: Run repository test to verify it fails**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Android\SDK'
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    $env:ANDROID_HOME = 'C:\Dev\QueM\.worktrees\repository-ui\.tools\android-sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.repository.RoomQueueRepositoryTest.createItemPersistsPriorityAndDueDate" --no-daemon
```

Expected: FAIL because `QueueRepository.createItem` and `RoomQueueRepository.createItem` do not accept priority or due date.

- [ ] **Step 3: Update repository interface**

Replace `app/src/main/java/com/quem/data/repository/QueueRepository.kt` with:

```kotlin
package com.quem.data.repository

import com.quem.core.model.Attachment
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface QueueRepository {
    fun observeItems(status: QueueStatus): Flow<List<QueueItem>>

    fun searchArchive(query: String): Flow<List<QueueItem>>

    fun observeItem(id: String): Flow<QueueItem?>

    suspend fun createItem(
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem

    suspend fun changeStatus(id: String, status: QueueStatus): QueueItem?

    fun observeAttachments(queueItemId: String): Flow<List<Attachment>>

    suspend fun addTextAttachment(queueItemId: String, title: String, text: String)

    suspend fun addLinkAttachment(queueItemId: String, title: String, url: String)

    suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    )
}
```

- [ ] **Step 4: Update Room repository implementation**

In `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`, add imports:

```kotlin
import com.quem.core.model.Priority
import java.time.LocalDate
```

Replace the `createItem` function with:

```kotlin
override suspend fun createItem(
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem {
    val now = clock.now()
    val item = QueueItem(
        id = idProvider(),
        driveId = null,
        title = title.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        status = QueueStatus.QUEUED,
        priority = priority,
        dueDate = dueDate,
        tags = emptyList(),
        createdAt = now,
        updatedAt = now,
        completedAt = null,
        dismissedAt = null,
        syncState = SyncState.PENDING_SYNC
    )
    dao.upsertItem(item.toEntity())
    return item
}
```

- [ ] **Step 5: Update repository test call sites**

In `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`, replace every existing two-argument `repository.createItem` call with named `priority = null` and `dueDate = null`. Example replacement:

```kotlin
val created = repository.createItem(
    title = "Read contract",
    description = null,
    priority = null,
    dueDate = null
)
```

Keep the new `createItemPersistsPriorityAndDueDate` test passing `Priority.HIGH` and `LocalDate.parse("2026-05-30")`.

- [ ] **Step 6: Run repository tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.data.repository.RoomQueueRepositoryTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit repository metadata persistence**

```powershell
git add app/src/main/java/com/quem/data/repository/QueueRepository.kt app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: persist queue create metadata"
```

## Task 2: Parse Metadata In QueueViewModel

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

- [ ] **Step 1: Update fake repository signature in ViewModel tests**

In `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`, add imports:

```kotlin
import com.quem.core.model.Priority
import java.time.LocalDate
```

Replace `FakeQueueRepository.createItem` with:

```kotlin
override suspend fun createItem(
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem {
    val item = queueItem(
        id = "item-${nextId++}",
        title = title,
        description = description,
        status = QueueStatus.QUEUED,
        priority = priority,
        dueDate = dueDate
    )
    items.value = items.value + item
    return item
}
```

Replace the `queueItem` helper signature and body tail with:

```kotlin
private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus,
    priority: Priority? = null,
    dueDate: LocalDate? = null
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
    syncState = SyncState.PENDING_SYNC
)
```

Update existing `repository.createItem` calls in this test file to pass `priority = null` and `dueDate = null` until the ViewModel production signature changes.

- [ ] **Step 2: Add ViewModel parsing tests**

In `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`, add these tests after `createItemCreatesQueuedItemSelectsItAndClosesCreateScreen`:

```kotlin
@Test
fun createItemParsesPriorityAndDueDateMetadata() = runTest {
    val repository = FakeQueueRepository()
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.createItem(
        title = "Read contract",
        description = "Legal notes",
        priority = "high",
        dueDate = "2026-05-30"
    )
    advanceUntilIdle()

    val created = repository.items.value.single()
    assertEquals(Priority.HIGH, created.priority)
    assertEquals(LocalDate.parse("2026-05-30"), created.dueDate)
    assertEquals("2026-05-30", viewModel.selectedItem.value?.dueDateLabel)
}

@Test
fun createItemIgnoresInvalidPriorityAndDueDateMetadata() = runTest {
    val repository = FakeQueueRepository()
    val viewModel = QueueViewModel(repository)

    viewModel.createItem(
        title = "Read contract",
        description = null,
        priority = "urgent",
        dueDate = "tomorrow"
    )
    advanceUntilIdle()

    val created = repository.items.value.single()
    assertNull(created.priority)
    assertNull(created.dueDate)
}
```

- [ ] **Step 3: Run ViewModel tests to verify failures**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --no-daemon
```

Expected: FAIL because `QueueViewModel.createItem` does not accept `priority` or `dueDate` strings yet.

- [ ] **Step 4: Implement ViewModel metadata parsing**

In `app/src/main/java/com/quem/ui/QueueViewModel.kt`, add imports:

```kotlin
import com.quem.core.model.Priority
import java.time.LocalDate
```

Replace `createItem` with:

```kotlin
fun createItem(
    title: String,
    description: String?,
    priority: String? = null,
    dueDate: String? = null
) {
    viewModelScope.launch {
        val created = repository.createItem(
            title = title,
            description = description,
            priority = priority.toPriorityOrNull(),
            dueDate = dueDate.toLocalDateOrNull()
        )
        savedStateHandle[KEY_SELECTED_STATUS] = QueueStatus.QUEUED
        savedStateHandle[KEY_SELECTED_ITEM_ID] = created.id
        savedStateHandle[KEY_IS_CREATING_ITEM] = false
    }
}
```

Add these private helpers near the bottom of `QueueViewModel.kt`:

```kotlin
private fun String?.toPriorityOrNull(): Priority? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return Priority.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }
}

private fun String?.toLocalDateOrNull(): LocalDate? {
    val normalized = this?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return runCatching { LocalDate.parse(normalized) }.getOrNull()
}
```

- [ ] **Step 5: Update repository calls in ViewModel tests**

In `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`, update direct fake repository creation calls. Example:

```kotlin
repository.createItem(
    title = "Read contract",
    description = "Legal notes",
    priority = null,
    dueDate = null
)
```

Keep app-facing `viewModel.createItem("Read contract", "Legal notes")` calls unchanged where metadata is not under test; the ViewModel has default null metadata parameters.

- [ ] **Step 6: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Commit ViewModel metadata parsing**

```powershell
git add app/src/main/java/com/quem/ui/QueueViewModel.kt app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: parse queue create metadata"
```

## Task 3: Forward UI Metadata And Update Remaining Fakes

**Files:**
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`
- Modify other test files only where the new `QueueRepository.createItem` signature causes compilation failures.

- [ ] **Step 1: Forward create metadata from QueMApp**

In `app/src/main/java/com/quem/app/QueMApp.kt`, replace the `CreateItemScreen` save lambda with:

```kotlin
onSave = { title, description, priority, dueDate ->
    viewModel.createItem(
        title = title,
        description = description,
        priority = priority,
        dueDate = dueDate
    )
},
```

- [ ] **Step 2: Update app-level fake repository signature**

In `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`, add imports:

```kotlin
import com.quem.core.model.Priority
import java.time.LocalDate
```

Replace `FakeQueueRepository.createItem` with:

```kotlin
override suspend fun createItem(
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem {
    val item = queueItem(
        id = "item-${nextItemId++}",
        title = title,
        description = description,
        status = QueueStatus.QUEUED,
        priority = priority,
        dueDate = dueDate
    )
    items.value = items.value + item
    return item
}
```

Replace the `queueItem` helper signature and body fields with:

```kotlin
private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus,
    priority: Priority? = null,
    dueDate: LocalDate? = null
) = QueueItem(
    id = id,
    driveId = null,
    title = title,
    description = description,
    status = status,
    priority = priority,
    dueDate = dueDate,
    tags = emptyList(),
    createdAt = FIXED_INSTANT,
    updatedAt = FIXED_INSTANT,
    completedAt = null,
    dismissedAt = null,
    syncState = SyncState.PENDING_SYNC
)
```

- [ ] **Step 3: Search for remaining compile breaks**

Run:

```powershell
rg "createItem\\(" app/src/main/java app/src/test/java app/src/androidTest/java
```

Update any direct `QueueRepository.createItem` or fake repository overrides to the four-argument repository signature. Do not change `QueueViewModel.createItem("title", "description")` calls unless they should pass form metadata.

- [ ] **Step 4: Run focused app-level tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.QueueListScreenTest' --no-daemon
```

Expected: both commands PASS.

- [ ] **Step 5: Commit UI forwarding and fake updates**

```powershell
git add app/src/main/java/com/quem/app/QueMApp.kt app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt
git commit -m "feat: forward queue create metadata"
```

## Task 4: Final Verification

**Files:**
- Modify only if verification reveals failures.

- [ ] **Step 1: Run full JVM tests**

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Android\SDK'
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    $env:ANDROID_HOME = 'C:\Dev\QueM\.worktrees\repository-ui\.tools\android-sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 2: Run full connected Android tests**

Run:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Build debug APK**

Run:

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon
```

Expected: `BUILD SUCCESSFUL` and APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Commit verification fixes if needed**

If verification changed files:

```powershell
git add app
git commit -m "test: stabilize queue create metadata"
```

If no files changed:

```powershell
git status --short
```

Expected: no output.
