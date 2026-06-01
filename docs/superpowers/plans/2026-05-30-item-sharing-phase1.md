# Item Sharing — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow a user to share a single queue item with another person via Google Drive, granting them writer access to a per-item JSON file stored in the sharer's Drive.

**Architecture:** Two new fields (`sharedDriveFileId`, `sharedWith`) on `QueueItem` and `QueueItemEntity` with a Room migration (v1→v2); a new `DriveShareGateway` interface and `GoogleDriveShareGateway` implementation wraps the Drive Permissions API; `QueueRepository.shareItem` orchestrates read-export-publish-grant-update; `ItemDetailScreen` gains a Share button and email dialog; `QueMApp` builds the Drive gateway from stored credentials and wires the callback.

**Tech Stack:** Room v2 migration, Google Drive Files + Permissions API (`drive.file` scope), Jetpack Compose `AlertDialog`, Kotlin coroutines, existing `MetadataExporter`/`MetadataSerializer` infrastructure.

**Codebase context (as of plan update 2026-06-01):**
- `QueueItemDetailUi` already has `status: QueueStatus`, `attachments: List<AttachmentUi>`, `history: List<HistoryEntryUi>` — Tasks 4/5 add `sharedWith` and `sharedDriveFileId` to these existing fields.
- Navigation is `QueMScreen` sealed class + `viewModel.screen: StateFlow<QueMScreen>`. `QueMApp` uses `when (screen)` — item detail lives in `is QueMScreen.Detail ->`.
- `ItemDetailScreen` has `currentStatus`, `onStatusChange`, `onDeleteAttachment`, `onRenameAttachment`, `onDeleteHistoryEntry` — no `onDone`/`onDismiss`.
- DB is currently at `version = 1`.

---

## File map

| Action | Path |
|---|---|
| Modify | `app/src/main/java/com/quem/core/model/QueueItem.kt` |
| Modify | `app/src/main/java/com/quem/data/local/QueueItemEntity.kt` |
| Modify | `app/src/main/java/com/quem/data/local/QueMDatabase.kt` |
| Modify | `app/src/main/java/com/quem/data/local/LocalMappers.kt` |
| Modify | `app/src/main/java/com/quem/app/AppDependencies.kt` |
| Create | `app/src/main/java/com/quem/drive/DriveShareGateway.kt` |
| Create | `app/src/main/java/com/quem/drive/GoogleDriveShareGateway.kt` |
| Modify | `app/src/main/java/com/quem/data/local/QueueDao.kt` |
| Modify | `app/src/main/java/com/quem/data/repository/QueueRepository.kt` |
| Modify | `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt` |
| Modify | `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt` |
| Modify | `app/src/main/java/com/quem/ui/QueueViewModel.kt` |
| Modify | `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` |
| Modify | `app/src/main/java/com/quem/ui/ItemDetailScreen.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |
| Modify | `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt` |

---

## Task 1: Data model + Room migration + LocalMappers

**Files:**
- Modify: `app/src/main/java/com/quem/core/model/QueueItem.kt`
- Modify: `app/src/main/java/com/quem/data/local/QueueItemEntity.kt`
- Modify: `app/src/main/java/com/quem/data/local/QueMDatabase.kt`
- Modify: `app/src/main/java/com/quem/data/local/LocalMappers.kt`
- Modify: `app/src/main/java/com/quem/app/AppDependencies.kt`

Context: `QueueItemEntity` currently has 13 fields. `sharedWith` uses the **same** `List<String>` JSON converter as `tags` (already defined in `Converters.kt`) — no new `TypeConverter` needed. The Room database is at `version = 1`; this task bumps it to `2`. The existing `LocalMappers.toDomain()` and `toEntity()` must include both new fields.

- [ ] **Step 1: Update QueueItem.kt**

```kotlin
package com.quem.core.model

import java.time.Instant
import java.time.LocalDate

data class QueueItem(
    val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: QueueStatus,
    val priority: Priority?,
    val dueDate: LocalDate?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val dismissedAt: Instant?,
    val syncState: SyncState,
    val sharedDriveFileId: String?,     // Drive file ID of QueM/shared-{id}.json; null = not shared
    val sharedWith: List<String>        // recipient emails; empty = not shared
)
```

- [ ] **Step 2: Update QueueItemEntity.kt**

```kotlin
package com.quem.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

@Entity(
    tableName = "queue_items",
    indices = [
        Index(value = ["status", "updatedAt"]),
        Index(value = ["syncState"])
    ]
)
data class QueueItemEntity(
    @PrimaryKey val id: String,
    val driveId: String?,
    val title: String,
    val description: String?,
    val status: String,
    val priority: String?,
    val dueDate: LocalDate?,
    val tags: List<String>,
    val createdAt: Instant,
    val updatedAt: Instant,
    val completedAt: Instant?,
    val dismissedAt: Instant?,
    val syncState: String,
    val sharedDriveFileId: String?,     // null = not shared
    val sharedWith: List<String>        // JSON-encoded via existing Converters.tagsToString
)
```

- [ ] **Step 3: Update LocalMappers.kt**

Replace `QueueItemEntity.toDomain()` and `QueueItem.toEntity()` to include the new fields:

```kotlin
fun QueueItemEntity.toDomain(): QueueItem = QueueItem(
    id                = id,
    driveId           = driveId,
    title             = title,
    description       = description,
    status            = QueueStatus.valueOf(status),
    priority          = priority?.let(Priority::valueOf),
    dueDate           = dueDate,
    tags              = tags,
    createdAt         = createdAt,
    updatedAt         = updatedAt,
    completedAt       = completedAt,
    dismissedAt       = dismissedAt,
    syncState         = SyncState.valueOf(syncState),
    sharedDriveFileId = sharedDriveFileId,
    sharedWith        = sharedWith
)

fun QueueItem.toEntity(): QueueItemEntity = QueueItemEntity(
    id                = id,
    driveId           = driveId,
    title             = title,
    description       = description,
    status            = status.name,
    priority          = priority?.name,
    dueDate           = dueDate,
    tags              = tags,
    createdAt         = createdAt,
    updatedAt         = updatedAt,
    completedAt       = completedAt,
    dismissedAt       = dismissedAt,
    syncState         = syncState.name,
    sharedDriveFileId = sharedDriveFileId,
    sharedWith        = sharedWith
)
```

- [ ] **Step 4: Bump QueMDatabase to version 2 with migration**

```kotlin
package com.quem.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [QueueItemEntity::class, AttachmentEntity::class, HistoryEntryEntity::class],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class QueMDatabase : RoomDatabase() {
    abstract fun queueDao(): QueueDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE queue_items ADD COLUMN sharedDriveFileId TEXT")
                db.execSQL("ALTER TABLE queue_items ADD COLUMN sharedWith TEXT NOT NULL DEFAULT '[]'")
            }
        }
    }
}
```

- [ ] **Step 5: Wire migration in AppDependencies.kt**

Open `app/src/main/java/com/quem/app/AppDependencies.kt`. Add `.addMigrations(QueMDatabase.MIGRATION_1_2)` to the Room builder:

```kotlin
private val database: QueMDatabase = Room.databaseBuilder(
    context.applicationContext,
    QueMDatabase::class.java,
    DATABASE_NAME
)
    .addMigrations(QueMDatabase.MIGRATION_1_2)
    .build()
```

- [ ] **Step 6: Run full unit test suite to verify no compilation errors**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL — all existing tests pass. Fix any compilation errors caused by the new required fields on `QueueItem` (add `sharedDriveFileId = null, sharedWith = emptyList()` to any `QueueItem(...)` constructors in test helpers).

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/quem/core/model/QueueItem.kt \
        app/src/main/java/com/quem/data/local/QueueItemEntity.kt \
        app/src/main/java/com/quem/data/local/QueMDatabase.kt \
        app/src/main/java/com/quem/data/local/LocalMappers.kt \
        app/src/main/java/com/quem/app/AppDependencies.kt
git commit -m "feat: add sharedDriveFileId and sharedWith fields to QueueItem; Room migration v1→v2"
```

---

## Task 2: DriveShareGateway interface + GoogleDriveShareGateway

**Files:**
- Create: `app/src/main/java/com/quem/drive/DriveShareGateway.kt`
- Create: `app/src/main/java/com/quem/drive/GoogleDriveShareGateway.kt`

Context: `GoogleDriveGateway.kt` already contains `ensureFolder`, `findFile` private helpers. `GoogleDriveShareGateway` is a separate class that follows the same `withContext(ioDispatcher)` pattern. The `appProperty` tag `"sharedItem"` distinguishes shared item files from the main metadata file. No new tests for this task — gateway is tested indirectly via `FakeShareGateway` in Task 3.

- [ ] **Step 1: Create DriveShareGateway.kt**

```kotlin
package com.quem.drive

interface DriveShareGateway {
    /** Creates or overwrites `QueM/shared-{itemId}.json`. Returns Drive file ID. */
    suspend fun publishSharedItemFile(itemId: String, content: String): String

    /** Grants writer access to the Drive file; Drive emails the recipient. */
    suspend fun grantWriterAccess(fileId: String, recipientEmail: String)
}
```

- [ ] **Step 2: Create GoogleDriveShareGateway.kt**

```kotlin
package com.quem.drive

import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class GoogleDriveShareGateway(
    private val drive: Drive,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DriveShareGateway {

    override suspend fun publishSharedItemFile(itemId: String, content: String): String =
        withContext(ioDispatcher) {
            val folderId = ensureFolder(QUE_M_FOLDER)
            val fileName = "shared-$itemId.json"
            val existingFile = findFile(folderId, fileName)
            val mediaContent = ByteArrayContent(
                APPLICATION_JSON,
                content.toByteArray(StandardCharsets.UTF_8)
            )
            if (existingFile == null) {
                val metadata = File()
                    .setName(fileName)
                    .setParents(listOf(folderId))
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_SHARED_ITEM))
                drive.files().create(metadata, mediaContent).setFields("id").execute().id
            } else {
                drive.files().update(existingFile.id, null, mediaContent).setFields("id").execute()
                existingFile.id
            }
        }

    override suspend fun grantWriterAccess(fileId: String, recipientEmail: String) =
        withContext(ioDispatcher) {
            val permission = Permission()
                .setType("user")
                .setRole("writer")
                .setEmailAddress(recipientEmail)
            drive.permissions().create(fileId, permission)
                .setSendNotificationEmail(true)
                .execute()
            Unit
        }

    private fun ensureFolder(folderName: String): String {
        val existing = findFolder(folderName)
        if (existing != null) return existing.id
        return drive.files()
            .create(
                File().setName(folderName).setMimeType(FOLDER_MIME_TYPE)
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_ROOT_FOLDER))
            ).setFields("id").execute().id
    }

    private fun findFolder(folderName: String): File? = drive.files().list()
        .setQ("mimeType = '$FOLDER_MIME_TYPE' and name = '$folderName' and appProperties has { key = '$APP_PROPERTY_ROLE' and value = '$APP_PROPERTY_ROOT_FOLDER' } and trashed = false")
        .setSpaces("drive").setFields("files(id, name)").execute().files.orEmpty().firstOrNull()

    private fun findFile(folderId: String, fileName: String): File? = drive.files().list()
        .setQ("'$folderId' in parents and name = '$fileName' and appProperties has { key = '$APP_PROPERTY_ROLE' and value = '$APP_PROPERTY_SHARED_ITEM' } and trashed = false")
        .setSpaces("drive").setFields("files(id, name)").execute().files.orEmpty().firstOrNull()

    private companion object {
        const val APPLICATION_JSON         = "application/json"
        const val FOLDER_MIME_TYPE         = "application/vnd.google-apps.folder"
        const val QUE_M_FOLDER             = "QueM"
        const val APP_PROPERTY_ROLE        = "quemRole"
        const val APP_PROPERTY_ROOT_FOLDER = "rootFolder"
        const val APP_PROPERTY_SHARED_ITEM = "sharedItem"
    }
}
```

- [ ] **Step 3: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/quem/drive/DriveShareGateway.kt \
        app/src/main/java/com/quem/drive/GoogleDriveShareGateway.kt
git commit -m "feat: add DriveShareGateway interface and GoogleDriveShareGateway implementation"
```

---

## Task 3: QueueDao + QueueRepository + RoomQueueRepository + repository unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/QueueDao.kt`
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

Context: `QueueDao` already has `updateItemFields`, `deleteAttachment`, `updateAttachmentTitle`, `deleteHistoryEntry` from previous work. `RoomQueueRepository.shareItem` uses `MetadataExporter.export` + `MetadataSerializer.encode` (already imported). `FakeQueueDao` in `RoomQueueRepositoryTest.kt` must implement `updateShareInfo`. `FakeShareGateway` is defined in the test file.

- [ ] **Step 1: Add updateShareInfo to QueueDao**

Open `app/src/main/java/com/quem/data/local/QueueDao.kt`. Add after `deleteHistoryEntry`:

```kotlin
@Query("""
    UPDATE queue_items
    SET sharedDriveFileId = :sharedDriveFileId,
        sharedWith        = :sharedWith
    WHERE id = :id
""")
suspend fun updateShareInfo(
    id: String,
    sharedDriveFileId: String,
    sharedWith: List<String>
)
```

- [ ] **Step 2: Add shareItem to QueueRepository interface**

Open `app/src/main/java/com/quem/data/repository/QueueRepository.kt`. Add after `deleteHistoryEntry`:

```kotlin
suspend fun shareItem(
    itemId: String,
    recipientEmail: String,
    shareGateway: DriveShareGateway
): Boolean
```

Add import: `import com.quem.drive.DriveShareGateway`

- [ ] **Step 3: Write the failing repository unit tests**

Open `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`.

**3a.** Add `updateShareInfo` to `FakeQueueDao` (after `deleteHistoryEntry` override):

```kotlin
override suspend fun updateShareInfo(
    id: String,
    sharedDriveFileId: String,
    sharedWith: List<String>
) {
    entities.value = entities.value.map { item ->
        if (item.id == id) item.copy(sharedDriveFileId = sharedDriveFileId, sharedWith = sharedWith)
        else item
    }
}
```

**3b.** Add `FakeShareGateway` at the bottom of the test file:

```kotlin
private class FakeShareGateway : DriveShareGateway {
    var publishedItemId: String? = null
    var publishedContent: String? = null
    var grantedFileId: String? = null
    var grantedEmail: String? = null
    var shouldThrow: Exception? = null

    override suspend fun publishSharedItemFile(itemId: String, content: String): String {
        shouldThrow?.let { throw it }
        publishedItemId = itemId
        publishedContent = content
        return "fake-file-id"
    }

    override suspend fun grantWriterAccess(fileId: String, recipientEmail: String) {
        grantedFileId = fileId
        grantedEmail = recipientEmail
    }
}
```

**3c.** Add 3 tests inside `RoomQueueRepositoryTest`:

```kotlin
@Test
fun shareItemPublishesSnapshotAndGrantsAccess() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    val gateway = FakeShareGateway()

    val result = repository.shareItem("item-1", "alice@example.com", gateway)

    assertTrue(result)
    assertEquals("item-1", gateway.publishedItemId)
    assertEquals("fake-file-id", gateway.grantedFileId)
    assertEquals("alice@example.com", gateway.grantedEmail)
}

@Test
fun shareItemUpdatesLocalSharedFields() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    repository.shareItem("item-1", "alice@example.com", FakeShareGateway())

    val updated = repository.observeItem("item-1").first()
    requireNotNull(updated)
    assertEquals("fake-file-id", updated.sharedDriveFileId)
    assertEquals(listOf("alice@example.com"), updated.sharedWith)
}

@Test
fun shareItemReturnsFalseWhenGatewayThrows() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    val gateway = FakeShareGateway().apply { shouldThrow = RuntimeException("Drive error") }

    val result = repository.shareItem("item-1", "alice@example.com", gateway)

    assertFalse(result)
    assertNull(repository.observeItem("item-1").first()?.sharedDriveFileId)
}
```

Add `import com.quem.drive.DriveShareGateway` if not already present.

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: FAILED — `shareItem` not yet implemented.

- [ ] **Step 5: Implement shareItem in RoomQueueRepository**

Add import: `import com.quem.drive.DriveShareGateway`

Add method after `deleteHistoryEntry`:

```kotlin
override suspend fun shareItem(
    itemId: String,
    recipientEmail: String,
    shareGateway: DriveShareGateway
): Boolean = runCatching {
    val item        = dao.observeItem(itemId).first()?.toDomain() ?: return false
    val attachments = dao.observeAttachments(itemId).first().map { it.toDomain() }
    val history     = dao.observeHistory(itemId).first().map { it.toDomain() }

    val snapshot = MetadataExporter.export(
        exportedAt  = clock.now().toString(),
        items       = listOf(item.toExportable()),
        attachments = attachments.map { it.toMetadata() },
        history     = history.map { it.toMetadata() }
    )
    val content = MetadataSerializer.encode(snapshot)

    val fileId = shareGateway.publishSharedItemFile(itemId, content)
    shareGateway.grantWriterAccess(fileId, recipientEmail)
    dao.updateShareInfo(id = itemId, sharedDriveFileId = fileId, sharedWith = listOf(recipientEmail))
    true
}.getOrElse { false }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/quem/data/local/QueueDao.kt \
        app/src/main/java/com/quem/data/repository/QueueRepository.kt \
        app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt \
        app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: add shareItem to QueueRepository and RoomQueueRepository"
```

---

## Task 4: ViewModel changes + unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

Context: `QueueItemDetailUi` currently has 10 fields (`id`, `title`, `description`, `priorityLabel`, `dueDateLabel`, `dueDateIso`, `attachments: List<AttachmentUi>`, `history: List<HistoryEntryUi>`, `syncIndicator`, `status`). Adding `sharedWith` and `sharedDriveFileId` follows the same pattern. `shareError` is a `MutableStateFlow<String?>` (already imported) — transient UI state, not in `SavedStateHandle`. `isShowingShareDialog` goes in `SavedStateHandle` so it survives config changes. The companion object already has `KEY_SCREEN_ROUTE`, `KEY_IS_CREATING_ITEM`, etc — add `KEY_IS_SHOWING_SHARE_DIALOG` there. `FakeQueueRepository` in `QueueViewModelTest.kt` must add `shareItem` (always returns `true`).

- [ ] **Step 1: Write the failing ViewModel unit tests**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`.

**1a.** Add `shareItem` to `FakeQueueRepository` (after `deleteHistoryEntry`):

```kotlin
var lastSharedItemId: String? = null
var lastSharedEmail: String? = null

override suspend fun shareItem(
    itemId: String,
    recipientEmail: String,
    shareGateway: DriveShareGateway
): Boolean {
    lastSharedItemId = itemId
    lastSharedEmail = recipientEmail
    return true
}
```

Add `import com.quem.drive.DriveShareGateway` to test file imports.

**1b.** Add this test inside `QueueViewModelTest`:

```kotlin
@Test
fun selectedItemIncludesSharedWithWhenItemIsShared() = runTest {
    val repository = FakeQueueRepository()
    repository.items.value = listOf(
        queueItem(
            id = "item-1",
            title = "Read contract",
            description = null,
            status = QueueStatus.QUEUED,
            sharedWith = listOf("alice@example.com"),
            sharedDriveFileId = "file-123"
        )
    )
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertEquals(listOf("alice@example.com"), viewModel.selectedItem.value?.sharedWith)
    assertEquals("file-123", viewModel.selectedItem.value?.sharedDriveFileId)
}
```

**1c.** Update the `queueItem()` helper at the bottom of `QueueViewModelTest.kt` to add the two new parameters:

```kotlin
private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus,
    priority: Priority? = null,
    dueDate: LocalDate? = null,
    syncState: SyncState = SyncState.PENDING_SYNC,
    sharedDriveFileId: String? = null,
    sharedWith: List<String> = emptyList()
) = QueueItem(
    id                = id,
    driveId           = null,
    title             = title,
    description       = description,
    status            = status,
    priority          = priority,
    dueDate           = dueDate,
    tags              = emptyList(),
    createdAt         = Instant.parse("2026-05-23T12:00:00Z"),
    updatedAt         = Instant.parse("2026-05-23T12:00:00Z"),
    completedAt       = null,
    dismissedAt       = null,
    syncState         = syncState,
    sharedDriveFileId = sharedDriveFileId,
    sharedWith        = sharedWith
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: FAILED — `sharedWith`, `sharedDriveFileId` not yet on `QueueItemDetailUi`.

- [ ] **Step 3: Update QueueViewModel.kt**

**3a.** Add two fields to `QueueItemDetailUi` (after `status`):

```kotlin
data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val dueDateIso: String?,
    val attachments: List<AttachmentUi>,
    val history: List<HistoryEntryUi>,
    val syncIndicator: SyncIndicator?,
    val status: QueueStatus,
    val sharedDriveFileId: String?,     // new
    val sharedWith: List<String>        // new
)
```

**3b.** Update `toDetailUi` to map the new fields (add to the existing function):

```kotlin
private fun QueueItem.toDetailUi(attachments: List<AttachmentUi>, history: List<HistoryEntryUi>) = QueueItemDetailUi(
    id                = id,
    title             = title,
    description       = description,
    priorityLabel     = priority?.name,
    dueDateLabel      = dueDate?.toString(),
    dueDateIso        = dueDate?.toString(),
    attachments       = attachments,
    history           = history,
    syncIndicator     = syncState.toIndicator(),
    status            = status,
    sharedDriveFileId = sharedDriveFileId,
    sharedWith        = sharedWith
)
```

**3c.** Add share dialog state and actions. Add after the `navigateBack()` function:

```kotlin
val isShowingShareDialog: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_SHOWING_SHARE_DIALOG, false)

private val _shareError = MutableStateFlow<String?>(null)
val shareError: StateFlow<String?> = _shareError.asStateFlow()

fun showShareDialog() {
    savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = true
    _shareError.value = null
}

fun closeShareDialog() {
    savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = false
    _shareError.value = null
}

fun setShareError(message: String) {
    _shareError.value = message
}

fun shareItem(recipientEmail: String, shareGateway: DriveShareGateway) {
    val id = selectedItemId.value ?: return
    viewModelScope.launch {
        val success = repository.shareItem(id, recipientEmail, shareGateway)
        if (success) {
            savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = false
            _shareError.value = null
        } else {
            _shareError.value = "Could not share item. Check the email and try again."
        }
    }
}
```

**3d.** Add `KEY_IS_SHOWING_SHARE_DIALOG` to the companion object constants (after `KEY_IS_EDITING_ITEM`):

```kotlin
private const val KEY_IS_SHOWING_SHARE_DIALOG = "isShowingShareDialog"
```

Add imports: `import com.quem.drive.DriveShareGateway` (if not already present).

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/ui/QueueViewModel.kt \
        app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: add share dialog state and shareItem action to QueueViewModel"
```

---

## Task 5: ItemDetailScreen Share UI + QueMApp wiring + instrumented tests

**Files:**
- Modify: `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`
- Modify: `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`

Context: The current top row is `Row(SpaceBetween)` with `TextButton("Back")` on the left and `TextButton("Edit")` on the right. Add `TextButton("Share")` next to Edit. `QueMApp` now uses `when (screen)` with `QueMScreen` sealed class — the item detail is in the `is QueMScreen.Detail ->` branch. `driveConnectionState` is already collected in `QueMApp`; the account email comes from `(driveConnectionState as DriveConnectionState.Connected).account.email` (user is always signed in at this point due to the sign-in gate). No `onDone`/`onDismiss` in `ItemDetailScreen` — those are replaced by `onStatusChange`.

- [ ] **Step 1: Write the failing instrumented tests**

Open `app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt`. Add:

```kotlin
@Test
fun shareButtonDisplayed() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList<HistoryEntryUi>(),
            onBack = {}
        )
    }

    compose.onNodeWithText("Share").assertIsDisplayed()
}

@Test
fun shareButtonInvokesOnShareCallback() {
    var shared = false
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList<HistoryEntryUi>(),
            onBack = {},
            onShare = { shared = true }
        )
    }

    compose.onNodeWithText("Share").performClick()

    assertTrue(shared)
}

@Test
fun sharedWithLabelDisplayedWhenItemIsShared() {
    compose.setContent {
        ItemDetailScreen(
            title = "Read contract",
            description = null,
            dueDateLabel = null,
            attachments = emptyList(),
            history = emptyList<HistoryEntryUi>(),
            sharedWith = listOf("alice@example.com"),
            onBack = {}
        )
    }

    compose.onNodeWithText("Shared with alice@example.com").assertIsDisplayed()
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: FAILED — `onShare` and `sharedWith` params don't exist yet.

- [ ] **Step 3: Update ItemDetailScreen.kt**

**3a.** Add new parameters to the function signature (after `onDeleteHistoryEntry`):

```kotlin
@Composable
fun ItemDetailScreen(
    title: String,
    description: String?,
    dueDateLabel: String?,
    attachments: List<AttachmentUi>,
    history: List<HistoryEntryUi>,
    priorityLabel: String? = null,
    syncIndicator: SyncIndicator? = null,
    currentStatus: QueueStatus = QueueStatus.QUEUED,
    onStatusChange: (QueueStatus) -> Unit = {},
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onDeleteAttachment: (attachmentId: String) -> Unit = {},
    onRenameAttachment: (attachmentId: String, newTitle: String) -> Unit = { _, _ -> },
    onDeleteHistoryEntry: (historyEntryId: String) -> Unit = {},
    sharedWith: List<String> = emptyList(),          // new
    isShowingShareDialog: Boolean = false,           // new
    shareError: String? = null,                      // new
    onShare: () -> Unit = {},                        // new
    onShareConfirm: (email: String) -> Unit = {},   // new
    onShareDialogDismiss: () -> Unit = {}            // new
)
```

**3b.** Replace the top `Row` (Back / Edit) with a three-button row:

```kotlin
item {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Row {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onShare) { Text("Share") }
        }
    }
}
```

**3c.** After the `syncIndicator` block and before `StatusActionRow`, add the "Shared with" indicator:

```kotlin
if (sharedWith.isNotEmpty()) {
    Text(
        text = "Shared with ${sharedWith.first()}",
        modifier = Modifier.fillMaxWidth(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}
```

**3d.** After the closing brace of the `LazyColumn`, add the share `AlertDialog` as a sibling:

```kotlin
if (isShowingShareDialog) {
    var shareEmail by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onShareDialogDismiss,
        title = { Text("Share item") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = shareEmail,
                    onValueChange = { shareEmail = it },
                    label = { Text("Recipient email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                shareError?.let { error ->
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onShareConfirm(shareEmail.trim()) },
                enabled = shareEmail.contains("@")
            ) { Text("Share") }
        },
        dismissButton = {
            TextButton(onClick = onShareDialogDismiss) { Text("Cancel") }
        }
    )
}
```

Add imports: `import androidx.compose.material3.AlertDialog`, `import androidx.compose.material3.Button`, `import androidx.compose.runtime.saveable.rememberSaveable`.

- [ ] **Step 4: Update QueMApp.kt**

**4a.** Add state collection after `archiveResults`:

```kotlin
val isShowingShareDialog by viewModel.isShowingShareDialog.collectAsStateWithLifecycle()
val shareError           by viewModel.shareError.collectAsStateWithLifecycle()
```

**4b.** In the `is QueMScreen.Detail ->` branch, add share params to `ItemDetailScreen`:

```kotlin
is QueMScreen.Detail -> {
    val item = selectedItem ?: return
    ItemDetailScreen(
        title                = item.title,
        description          = item.description,
        dueDateLabel         = item.dueDateLabel,
        priorityLabel        = item.priorityLabel,
        attachments          = item.attachments,
        history              = item.history,
        syncIndicator        = item.syncIndicator,
        currentStatus        = item.status,
        onStatusChange       = viewModel::changeStatusOfSelectedItem,
        onEdit               = viewModel::startEdit,
        onDeleteAttachment   = viewModel::deleteAttachment,
        onRenameAttachment   = viewModel::updateAttachmentTitle,
        onDeleteHistoryEntry = viewModel::deleteHistoryEntry,
        sharedWith           = item.sharedWith,
        isShowingShareDialog = isShowingShareDialog,
        shareError           = shareError,
        onShare              = viewModel::showShareDialog,
        onShareDialogDismiss = viewModel::closeShareDialog,
        onShareConfirm       = { email ->
            val connectedState = driveConnectionState as? DriveConnectionState.Connected
            val accountEmail = connectedState?.account?.email
            if (accountEmail == null) {
                viewModel.setShareError("Sign in to Google Drive to share items")
            } else {
                val credential = GoogleAccountCredential
                    .usingOAuth2(context, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
                    .setSelectedAccountName(accountEmail)
                val drive = Drive.Builder(
                    NetHttpTransport(),
                    GsonFactory.getDefaultInstance(),
                    credential
                ).setApplicationName("QueM").build()
                viewModel.shareItem(email, GoogleDriveShareGateway(drive))
            }
        },
        onBack               = viewModel::backToList
    )
}
```

Add imports to `QueMApp.kt`:

```kotlin
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.drive.GoogleDriveShareGateway
```

- [ ] **Step 5: Run instrumented tests**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.ui.ItemDetailScreenTest`

Expected: BUILD SUCCESSFUL, all tests pass including 3 new ones.

- [ ] **Step 6: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/quem/ui/ItemDetailScreen.kt \
        app/src/main/java/com/quem/app/QueMApp.kt \
        app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "feat: add Share button, email dialog, and sharedWith indicator to ItemDetailScreen"
```
