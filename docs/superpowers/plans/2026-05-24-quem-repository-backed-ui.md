# QueM Repository-Backed UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace sample in-memory queue UI state with Room-backed repository state so create, Done, and Dismiss persist locally.

**Architecture:** `QueMApplication` owns a lightweight app dependency container with `QueMDatabase` and `RoomQueueRepository`. `MainActivity` passes the repository into `QueMApp`, and `QueMApp` creates a `QueueViewModel` that translates repository flows into the existing parameter-driven Compose screens.

**Tech Stack:** Kotlin, Jetpack Compose, AndroidX ViewModel, Kotlin Flow, Room, JUnit, kotlinx-coroutines-test, AndroidX Compose UI tests.

---

## File Structure

- `app/src/main/java/com/quem/app/AppDependencies.kt`: creates app-scoped Room database and repository.
- `app/src/main/java/com/quem/app/QueMApplication.kt`: owns `AppDependencies` and keeps periodic sync scheduling.
- `app/src/main/java/com/quem/app/MainActivity.kt`: passes the app repository into Compose.
- `app/src/main/java/com/quem/app/QueMApp.kt`: renders list/create/detail from `QueueViewModel` instead of sample state.
- `app/src/main/java/com/quem/ui/QueueViewModel.kt`: owns UI state and repository actions.
- `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`: tests repository-backed create and status transitions.
- `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`: updates app-level tests to use a fake repository.

## Environment Setup

The current Windows host does not have a system `java` command, Android Studio JBR, or Android SDK installed in their usual locations. Before running Gradle commands in the execution worktree, install local build tools into `.tools` from the worktree root:

```powershell
$toolsDir = Join-Path (Get-Location).Path '.tools'
$jdkZip = Join-Path $toolsDir 'temurin-21.0.8.zip'
$sdkZip = Join-Path $toolsDir 'commandlinetools-win-14742923_latest.zip'
$sdkRoot = Join-Path $toolsDir 'android-sdk'
$cmdlineLatest = Join-Path $sdkRoot 'cmdline-tools\latest'
$cmdlineTemp = Join-Path $toolsDir 'cmdline-tools-temp'

New-Item -ItemType Directory -Force -Path $toolsDir, $sdkRoot, $cmdlineLatest, $cmdlineTemp | Out-Null
Invoke-WebRequest -Uri 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.8%2B9/OpenJDK21U-jdk_x64_windows_hotspot_21.0.8_9.zip' -OutFile $jdkZip
Expand-Archive -Path $jdkZip -DestinationPath $toolsDir -Force
Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-14742923_latest.zip' -OutFile $sdkZip
Expand-Archive -Path $sdkZip -DestinationPath $cmdlineTemp -Force
Move-Item -Path (Join-Path $cmdlineTemp 'cmdline-tools\*') -Destination $cmdlineLatest -Force
Remove-Item -LiteralPath $cmdlineTemp -Recurse -Force

$env:JAVA_HOME = Join-Path $toolsDir 'jdk-21.0.8+9'
$env:ANDROID_HOME = $sdkRoot
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"

1..20 | ForEach-Object { 'y' } | sdkmanager.bat --licenses
sdkmanager.bat 'platform-tools' 'platforms;android-36' 'build-tools;36.0.0'
```

For every later Gradle command in a fresh shell, set the same environment variables:

```powershell
$toolsDir = Join-Path (Get-Location).Path '.tools'
$env:JAVA_HOME = Join-Path $toolsDir 'jdk-21.0.8+9'
$env:ANDROID_HOME = Join-Path $toolsDir 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"
```

## Task 1: Add App Dependencies Container

**Files:**
- Create: `app/src/main/java/com/quem/app/AppDependencies.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApplication.kt`

- [ ] **Step 1: Create app dependency container**

Create `app/src/main/java/com/quem/app/AppDependencies.kt`:

```kotlin
package com.quem.app

import android.content.Context
import androidx.room.Room
import com.quem.core.time.SystemClock
import com.quem.data.local.QueMDatabase
import com.quem.data.repository.QueueRepository
import com.quem.data.repository.RoomQueueRepository
import java.util.UUID

class AppDependencies(context: Context) {
    private val database: QueMDatabase = Room.databaseBuilder(
        context.applicationContext,
        QueMDatabase::class.java,
        DATABASE_NAME
    ).build()

    val queueRepository: QueueRepository = RoomQueueRepository(
        dao = database.queueDao(),
        clock = SystemClock(),
        idProvider = { UUID.randomUUID().toString() }
    )

    private companion object {
        const val DATABASE_NAME = "quem.db"
    }
}
```

- [ ] **Step 2: Initialize dependencies from the application**

Replace `app/src/main/java/com/quem/app/QueMApplication.kt` with:

```kotlin
package com.quem.app

import android.app.Application
import com.quem.data.sync.SyncScheduler

class QueMApplication : Application() {
    lateinit var dependencies: AppDependencies
        private set

    override fun onCreate() {
        super.onCreate()
        dependencies = AppDependencies(this)
        SyncScheduler.schedulePeriodic(this)
    }
}
```

- [ ] **Step 3: Run compile check**

Run:

```powershell
$toolsDir = Join-Path (Get-Location).Path '.tools'
$env:JAVA_HOME = Join-Path $toolsDir 'jdk-21.0.8+9'
$env:ANDROID_HOME = Join-Path $toolsDir 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"
.\gradlew.bat :app:compileDebugKotlin --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit app dependencies**

```powershell
git add app/src/main/java/com/quem/app/AppDependencies.kt app/src/main/java/com/quem/app/QueMApplication.kt
git commit -m "feat: add app repository dependencies"
```

## Task 2: Add Queue ViewModel

**Files:**
- Create: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Create: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

- [ ] **Step 1: Write ViewModel tests**

Create `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`:

```kotlin
package com.quem.ui

import com.quem.core.model.Attachment
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.data.repository.QueueRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createItemPersistsThroughRepositoryAndSelectsDetail() = runTest {
        val repository = FakeQueueRepository()
        val viewModel = QueueViewModel(repository)

        viewModel.startCreate()
        viewModel.createItem("Read contract", "Legal notes")
        advanceUntilIdle()

        assertEquals(QueueStatus.QUEUED, viewModel.selectedStatus.value)
        assertEquals("item-1", viewModel.selectedItem.value?.id)
        assertEquals(false, viewModel.isCreatingItem.value)
        assertEquals("Read contract", repository.items.value.single().title)
    }

    @Test
    fun dismissMovesItemOutOfQueuedListAndIntoDismissedStatus() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem("Read contract", null)
        val viewModel = QueueViewModel(repository)
        advanceUntilIdle()

        viewModel.selectItem("item-1")
        viewModel.dismissSelectedItem()
        advanceUntilIdle()

        assertEquals(QueueStatus.DISMISSED, viewModel.selectedStatus.value)
        assertNull(viewModel.selectedItem.value)
        assertEquals(QueueStatus.DISMISSED, repository.items.value.single().status)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
private class MainDispatcherRule(
    val dispatcher: StandardTestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeQueueRepository : QueueRepository {
    val items = MutableStateFlow<List<QueueItem>>(emptyList())
    private var nextId = 1

    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        items.map { rows -> rows.filter { it.status == status } }

    override fun searchArchive(query: String): Flow<List<QueueItem>> =
        items.map { rows -> rows.filter { it.status == QueueStatus.DONE || it.status == QueueStatus.DISMISSED } }

    override fun observeItem(id: String): Flow<QueueItem?> =
        items.map { rows -> rows.singleOrNull { it.id == id } }

    override suspend fun createItem(title: String, description: String?): QueueItem {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val item = QueueItem(
            id = "item-${nextId++}",
            driveId = null,
            title = title,
            description = description,
            status = QueueStatus.QUEUED,
            priority = null,
            dueDate = null,
            tags = emptyList(),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )
        items.value = items.value + item
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? {
        var changed: QueueItem? = null
        items.value = items.value.map { item ->
            if (item.id == id) {
                item.copy(status = status).also { changed = it }
            } else {
                item
            }
        }
        return changed
    }

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        MutableStateFlow(emptyList())

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) = Unit
    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) = Unit
    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) = Unit
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --no-daemon
```

Expected: FAIL because `QueueViewModel` does not exist.

- [ ] **Step 3: Implement QueueViewModel**

Create `app/src/main/java/com/quem/ui/QueueViewModel.kt`:

```kotlin
package com.quem.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.data.repository.QueueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModel(
    private val repository: QueueRepository
) : ViewModel() {
    private val _selectedStatus = MutableStateFlow(QueueStatus.QUEUED)
    val selectedStatus: StateFlow<QueueStatus> = _selectedStatus.asStateFlow()

    private val selectedItemId = MutableStateFlow<String?>(null)

    private val _isCreatingItem = MutableStateFlow(false)
    val isCreatingItem: StateFlow<Boolean> = _isCreatingItem.asStateFlow()

    val items: StateFlow<List<QueueListItemUi>> = selectedStatus
        .flatMapLatest { status -> repository.observeItems(status) }
        .map { rows -> rows.map { it.toListItemUi() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedItem: StateFlow<QueueItemDetailUi?> = selectedItemId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                repository.observeItem(id).map { item -> item?.toDetailUi() }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectStatus(status: QueueStatus) {
        _selectedStatus.value = status
    }

    fun selectItem(id: String) {
        selectedItemId.value = id
    }

    fun startCreate() {
        _isCreatingItem.value = true
    }

    fun cancelCreate() {
        _isCreatingItem.value = false
    }

    fun createItem(title: String, description: String?) {
        viewModelScope.launch {
            val created = repository.createItem(title = title, description = description)
            _selectedStatus.value = QueueStatus.QUEUED
            selectedItemId.value = created.id
            _isCreatingItem.value = false
        }
    }

    fun doneSelectedItem() {
        moveSelectedItemTo(QueueStatus.DONE)
    }

    fun dismissSelectedItem() {
        moveSelectedItemTo(QueueStatus.DISMISSED)
    }

    fun backToList() {
        selectedItemId.value = null
    }

    private fun moveSelectedItemTo(status: QueueStatus) {
        val id = selectedItemId.value ?: return
        viewModelScope.launch {
            repository.changeStatus(id, status)
            _selectedStatus.value = status
            selectedItemId.value = null
        }
    }

    companion object {
        fun factory(repository: QueueRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return QueueViewModel(repository) as T
                }
            }
    }
}

data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val dueDateLabel: String?,
    val attachments: List<String>,
    val history: List<String>
)

private fun QueueItem.toListItemUi(): QueueListItemUi =
    QueueListItemUi(
        id = id,
        title = title,
        priorityLabel = priority?.name,
        dueDateLabel = dueDate?.toString(),
        attachmentSummary = "0 attachments"
    )

private fun QueueItem.toDetailUi(): QueueItemDetailUi =
    QueueItemDetailUi(
        id = id,
        title = title,
        description = description,
        dueDateLabel = dueDate?.toString(),
        attachments = emptyList(),
        history = emptyList()
    )
```

- [ ] **Step 4: Run ViewModel tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --no-daemon
```

Expected: PASS.

- [ ] **Step 5: Commit ViewModel**

```powershell
git add app/src/main/java/com/quem/ui/QueueViewModel.kt app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: add repository backed queue viewmodel"
```

## Task 3: Wire QueMApp To Repository State

**Files:**
- Modify: `app/src/main/java/com/quem/app/MainActivity.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`

- [ ] **Step 1: Update QueMApp to accept repository**

Replace `app/src/main/java/com/quem/app/QueMApp.kt` with:

```kotlin
package com.quem.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.quem.data.repository.QueueRepository
import com.quem.ui.CreateItemScreen
import com.quem.ui.ItemDetailScreen
import com.quem.ui.QueueListScreen
import com.quem.ui.QueueViewModel

@Composable
fun QueMApp(queueRepository: QueueRepository) {
    val viewModel: QueueViewModel = viewModel(
        factory = QueueViewModel.factory(queueRepository)
    )
    val selectedStatus by viewModel.selectedStatus.collectAsStateWithLifecycle()
    val isCreatingItem by viewModel.isCreatingItem.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()

    when {
        isCreatingItem -> {
            CreateItemScreen(
                onSave = { title, description, _, _ ->
                    viewModel.createItem(title = title, description = description)
                },
                onCancel = viewModel::cancelCreate
            )
        }
        selectedItem == null -> {
            QueueListScreen(
                selectedStatus = selectedStatus,
                items = items,
                onStatusSelected = viewModel::selectStatus,
                onItemSelected = viewModel::selectItem,
                onCreateItem = viewModel::startCreate
            )
        }
        else -> {
            val item = requireNotNull(selectedItem)
            ItemDetailScreen(
                title = item.title,
                description = item.description,
                dueDateLabel = item.dueDateLabel,
                attachments = item.attachments,
                history = item.history,
                onDismiss = viewModel::dismissSelectedItem,
                onDone = viewModel::doneSelectedItem,
                onBack = viewModel::backToList
            )
        }
    }
}
```

- [ ] **Step 2: Pass repository from MainActivity**

Replace `app/src/main/java/com/quem/app/MainActivity.kt` with:

```kotlin
package com.quem.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.quem.ui.theme.QueMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val queueRepository = (application as QueMApplication).dependencies.queueRepository
        setContent {
            QueMTheme {
                QueMApp(queueRepository = queueRepository)
            }
        }
    }
}
```

- [ ] **Step 3: Update app-level Compose tests**

In `app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt`, replace direct `QueMApp()` usages with a fake repository-backed app:

```kotlin
compose.setContent {
    QueMApp(queueRepository = FakeQueueRepository.withSampleItem())
}
```

Add a private fake repository to the same test file:

```kotlin
private class FakeQueueRepository(
    initialItems: List<QueueItem> = emptyList()
) : QueueRepository {
    private val items = MutableStateFlow(initialItems)

    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        items.map { rows -> rows.filter { it.status == status } }

    override fun searchArchive(query: String): Flow<List<QueueItem>> =
        items.map { rows -> rows.filter { it.status == QueueStatus.DONE || it.status == QueueStatus.DISMISSED } }

    override fun observeItem(id: String): Flow<QueueItem?> =
        items.map { rows -> rows.singleOrNull { it.id == id } }

    override suspend fun createItem(title: String, description: String?): QueueItem {
        val item = sampleQueueItem(id = "created-${items.value.size + 1}", title = title, description = description)
        items.value = items.value + item
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? {
        var changed: QueueItem? = null
        items.value = items.value.map { item ->
            if (item.id == id) item.copy(status = status).also { changed = it } else item
        }
        return changed
    }

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        MutableStateFlow(emptyList())

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) = Unit
    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) = Unit
    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) = Unit

    companion object {
        fun withSampleItem(): FakeQueueRepository =
            FakeQueueRepository(listOf(sampleQueueItem()))
    }
}

private fun sampleQueueItem(
    id: String = "sample-1",
    title: String = "Read contract",
    description: String? = "Review renewal terms.",
    status: QueueStatus = QueueStatus.QUEUED
): QueueItem {
    val now = Instant.parse("2026-05-24T12:00:00Z")
    return QueueItem(
        id = id,
        driveId = null,
        title = title,
        description = description,
        status = status,
        priority = null,
        dueDate = null,
        tags = emptyList(),
        createdAt = now,
        updatedAt = now,
        completedAt = null,
        dismissedAt = null,
        syncState = SyncState.PENDING_SYNC
    )
}
```

Add imports used by the fake:

```kotlin
import com.quem.app.QueMApp
import com.quem.core.model.Attachment
import com.quem.core.model.QueueItem
import com.quem.core.model.SyncState
import com.quem.data.repository.QueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant
```

- [ ] **Step 4: Run affected tests**

Run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.quem.ui.QueueViewModelTest" --no-daemon
.\gradlew.bat :app:connectedDebugAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.QueueListScreenTest' --no-daemon
```

Expected: both commands PASS.

- [ ] **Step 5: Commit app wiring**

```powershell
git add app/src/main/java/com/quem/app/MainActivity.kt app/src/main/java/com/quem/app/QueMApp.kt app/src/androidTest/java/com/quem/ui/QueueListScreenTest.kt
git commit -m "feat: wire queue UI to repository"
```

## Task 4: Final Verification

**Files:**
- Modify only if verification reveals failures.

- [ ] **Step 1: Run full JVM tests**

Run:

```powershell
$toolsDir = Join-Path (Get-Location).Path '.tools'
$env:JAVA_HOME = Join-Path $toolsDir 'jdk-21.0.8+9'
$env:ANDROID_HOME = Join-Path $toolsDir 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\cmdline-tools\latest\bin;$env:PATH"
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
git commit -m "test: stabilize repository backed UI"
```

If no files changed:

```powershell
git status --short
```

Expected: no output.
