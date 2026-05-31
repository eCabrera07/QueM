# Item Sharing — Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Allow a user to share a single queue item with another person via Google Drive, granting them writer access to a per-item JSON file stored in the sharer's Drive.

**Architecture:** Two new fields (`sharedDriveFileId`, `sharedWith`) on `QueueItem` and `QueueItemEntity` with a Room migration; a new `DriveShareGateway` interface and `GoogleDriveShareGateway` implementation wraps the Drive Permissions API; `QueueRepository.shareItem` orchestrates read-export-publish-grant-update; `ItemDetailScreen` gains a Share button and email dialog; `QueMApp` builds the Drive gateway from stored credentials and wires the callback.

**Tech Stack:** Room v2 migration, Google Drive Files + Permissions API (`drive.file` scope), Jetpack Compose `AlertDialog`, Kotlin coroutines, existing `MetadataExporter`/`MetadataSerializer` infrastructure

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

Expected: BUILD SUCCESSFUL — all existing tests pass. (The `queueItem()` builders in test files hardcode only the old fields; they will need `sharedDriveFileId = null, sharedWith = emptyList()` added. Fix any compilation errors.)

Note: if test helpers like `queueItem(...)` fail to compile because they use positional args, add the two new fields with defaults to those private helper functions.

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

Context: `GoogleDriveGateway.kt` already contains `ensureFolder`, `findFile` private helpers and the `GoogleDriveQueries` object. `GoogleDriveShareGateway` is a separate class that reuses the same Drive service and follows the same `withContext(ioDispatcher)` pattern. The `app:property` tag `"sharedItem"` distinguishes shared item files from the main metadata file.

No new tests needed for this task — the gateway is tested indirectly through the repository fake in Task 3.

- [ ] **Step 1: Create DriveShareGateway.kt**

```kotlin
package com.quem.drive

interface DriveShareGateway {
    /**
     * Creates or overwrites `QueM/shared-{itemId}.json` in the user's Drive.
     * Returns the Drive file ID of the created/updated file.
     */
    suspend fun publishSharedItemFile(itemId: String, content: String): String

    /**
     * Grants the given email address writer access to the Drive file.
     * Drive sends the recipient a standard "shared with you" email notification.
     */
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
            drive.permissions()
                .create(fileId, permission)
                .setSendNotificationEmail(true)
                .execute()
            Unit
        }

    private fun ensureFolder(folderName: String): String {
        val existing = findFolder(folderName)
        if (existing != null) return existing.id
        return drive.files()
            .create(
                File()
                    .setName(folderName)
                    .setMimeType(FOLDER_MIME_TYPE)
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_ROOT_FOLDER))
            )
            .setFields("id")
            .execute()
            .id
    }

    private fun findFolder(folderName: String): File? = drive.files()
        .list()
        .setQ(
            "mimeType = '$FOLDER_MIME_TYPE' and name = '$folderName' and " +
            "appProperties has { key = '$APP_PROPERTY_ROLE' and value = '$APP_PROPERTY_ROOT_FOLDER' } " +
            "and trashed = false"
        )
        .setSpaces("drive")
        .setFields("files(id, name)")
        .execute()
        .files.orEmpty().firstOrNull()

    private fun findFile(folderId: String, fileName: String): File? = drive.files()
        .list()
        .setQ(
            "'$folderId' in parents and name = '$fileName' and " +
            "appProperties has { key = '$APP_PROPERTY_ROLE' and value = '$APP_PROPERTY_SHARED_ITEM' } " +
            "and trashed = false"
        )
        .setSpaces("drive")
        .setFields("files(id, name)")
        .execute()
        .files.orEmpty().firstOrNull()

    private companion object {
        const val APPLICATION_JSON       = "application/json"
        const val FOLDER_MIME_TYPE       = "application/vnd.google-apps.folder"
        const val QUE_M_FOLDER           = "QueM"
        const val APP_PROPERTY_ROLE      = "quemRole"
        const val APP_PROPERTY_ROOT_FOLDER = "rootFolder"
        const val APP_PROPERTY_SHARED_ITEM = "sharedItem"
    }
}
```

- [ ] **Step 3: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL — no regressions.

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

Context: `QueueDao.updateStatus` and `QueueDao.updateItemFields` are the existing targeted-UPDATE pattern. `RoomQueueRepository.shareItem` uses `MetadataExporter.export` + `MetadataSerializer.encode` (already imported in the file). `FakeQueueDao` in `RoomQueueRepositoryTest.kt` must implement `updateShareInfo`. The `FakeShareGateway` is defined in the test file and captures calls.

- [ ] **Step 1: Add updateShareInfo to QueueDao**

Open `app/src/main/java/com/quem/data/local/QueueDao.kt`. Add after `updateItemFields`:

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

Open `app/src/main/java/com/quem/data/repository/QueueRepository.kt`. Add after `updateItem`:

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

**3a.** Add `updateShareInfo` to `FakeQueueDao` (after `updateItemFields` override):

```kotlin
override suspend fun updateShareInfo(
    id: String,
    sharedDriveFileId: String,
    sharedWith: List<String>
) {
    entities.value = entities.value.map { item ->
        if (item.id == id) {
            item.copy(sharedDriveFileId = sharedDriveFileId, sharedWith = sharedWith)
        } else {
            item
        }
    }
}
```

**3b.** Add `FakeShareGateway` at the bottom of the test file (after `FakeQueueDao`):

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

**3c.** Add 3 tests inside `RoomQueueRepositoryTest` (after `addTextAttachmentWritesAttachmentAddedHistoryEntry`):

```kotlin
@Test
fun shareItemPublishesSnapshotAndGrantsAccess() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1")
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
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
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
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
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    val gateway = FakeShareGateway().apply { shouldThrow = RuntimeException("Drive error") }

    val result = repository.shareItem("item-1", "alice@example.com", gateway)

    assertFalse(result)
    // sharedDriveFileId should remain null
    val item = repository.observeItem("item-1").first()
    assertNull(item?.sharedDriveFileId)
}
```

Add `import org.junit.Assert.assertFalse` and `import org.junit.Assert.assertTrue` and `import com.quem.drive.DriveShareGateway` if not already present.

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: FAILED — `shareItem` not yet implemented in `RoomQueueRepository`.

- [ ] **Step 5: Implement shareItem in RoomQueueRepository**

Open `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`. Add imports:

```kotlin
import com.quem.drive.DriveShareGateway
```

Add method after `updateItem`:

```kotlin
override suspend fun shareItem(
    itemId: String,
    recipientEmail: String,
    shareGateway: DriveShareGateway
): Boolean = runCatching {
    val item = dao.observeItem(itemId).first()?.toDomain() ?: return false
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

    dao.updateShareInfo(
        id                = itemId,
        sharedDriveFileId = fileId,
        sharedWith        = listOf(recipientEmail)
    )
    true
}.getOrElse { false }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

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

Context: `QueueItemDetailUi` currently has 9 fields. Adding `sharedWith` and `sharedDriveFileId` follows the same pattern as `priorityLabel`. `shareError` is a `MutableStateFlow<String?>` — not in `SavedStateHandle` since it's transient UI state that should reset on navigation. `FakeQueueRepository` in `QueueViewModelTest.kt` needs `shareItem` added (always returns `true`, tracks last call).

- [ ] **Step 1: Write the failing ViewModel unit tests**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`.

**1a.** Add `shareItem` to `FakeQueueRepository` (after `updateItem`):

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

Add `import com.quem.drive.DriveShareGateway` to the test file imports.

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

Also update the `queueItem()` helper at the bottom of `QueueViewModelTest.kt` to add the two new parameters:

```kotlin
private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus,
    priority: Priority? = null,
    dueDate: LocalDate? = null,
    syncState: SyncState = SyncState.PENDING_SYNC,
    sharedDriveFileId: String? = null,      // new
    sharedWith: List<String> = emptyList()  // new
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

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`.

**3a.** Add two fields to `QueueItemDetailUi`:

```kotlin
data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val dueDateIso: String?,
    val attachments: List<String>,
    val history: List<String>,
    val syncIndicator: SyncIndicator?,
    val sharedDriveFileId: String?,     // new
    val sharedWith: List<String>        // new
)
```

**3b.** Update `toDetailUi` private function to map the new fields:

```kotlin
private fun QueueItem.toDetailUi(attachments: List<String>, history: List<String>) = QueueItemDetailUi(
    id                = id,
    title             = title,
    description       = description,
    priorityLabel     = priority?.name,
    dueDateLabel      = dueDate?.toString(),
    dueDateIso        = dueDate?.toString(),
    attachments       = attachments,
    history           = history,
    syncIndicator     = syncState.toIndicator(),
    sharedDriveFileId = sharedDriveFileId,
    sharedWith        = sharedWith
)
```

**3c.** Add companion object key and new StateFlows + actions (after `closeArchive` actions):

```kotlin
private const val KEY_IS_SHOWING_SHARE_DIALOG = "isShowingShareDialog"

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

Add `KEY_IS_SHOWING_SHARE_DIALOG` to the companion object constants block (after `KEY_IS_EDITING_ITEM`).

Add imports: `import com.quem.drive.DriveShareGateway` and `import kotlinx.coroutines.flow.MutableStateFlow`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: BUILD SUCCESSFUL, all tests pass including the new one.

- [ ] **Step 5: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

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

Context: The current top row (lines ~89-95) is a `Row(SpaceBetween)` with Back and Edit. Add Share as a third `TextButton`. The `AlertDialog` holds local email state (`rememberSaveable`). `QueMApp.kt` builds the `GoogleDriveShareGateway` from stored credentials exactly as `SyncWorker.doWork()` does. `deps.driveAccountPreferences` is `internal` — accessible from `QueMApp.kt` which is in `com.quem.app`, same module.

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
            history = emptyList(),
            onDismiss = {},
            onDone = {},
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
            history = emptyList(),
            onDismiss = {},
            onDone = {},
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
            history = emptyList(),
            sharedWith = listOf("alice@example.com"),
            onDismiss = {},
            onDone = {},
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

**3a.** Add new parameters to the function signature (after `syncIndicator`, before `onDismiss`):

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
    sharedWith: List<String> = emptyList(),   // new
    isShowingShareDialog: Boolean = false,    // new
    shareError: String? = null,               // new
    onDismiss: () -> Unit,
    onDone: () -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit = {},
    onShare: () -> Unit = {},                 // new — shows share dialog
    onShareConfirm: (email: String) -> Unit = {},  // new — called with entered email
    onShareDialogDismiss: () -> Unit = {},    // new
    ...
)
```

**3b.** Replace the top `Row` (Back/Edit) with a three-button row:

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

**3c.** In the header Column, after the `syncIndicator` Row and before the action buttons, add the "Shared with" indicator:

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

**3d.** Add the share `AlertDialog` — place it at the end of the composable body (outside the `LazyColumn`, as a sibling):

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

Add imports: `import androidx.compose.material3.AlertDialog`, `import androidx.compose.material3.Button`.

- [ ] **Step 4: Update QueMApp.kt**

**4a.** Add state collection (after `archiveResults`):

```kotlin
val isShowingShareDialog by viewModel.isShowingShareDialog.collectAsStateWithLifecycle()
val shareError by viewModel.shareError.collectAsStateWithLifecycle()
```

**4b.** Pass new params to `ItemDetailScreen`:

```kotlin
ItemDetailScreen(
    title                = item.title,
    description          = item.description,
    dueDateLabel         = item.dueDateLabel,
    priorityLabel        = item.priorityLabel,
    attachments          = item.attachments,
    history              = item.history,
    syncIndicator        = item.syncIndicator,
    sharedWith           = item.sharedWith,
    isShowingShareDialog = isShowingShareDialog,
    shareError           = shareError,
    onShare              = viewModel::showShareDialog,
    onShareDialogDismiss = viewModel::closeShareDialog,
    onShareConfirm       = { email ->
        val deps = (context.applicationContext as QueMApplication).dependencies
        val accountEmail = deps.driveAccountPreferences.load()
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
    onEdit        = viewModel::startEdit,
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
    onDone    = viewModel::doneSelectedItem,
    onBack    = viewModel::backToList
)
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

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 7: Commit**

```
git add app/src/main/java/com/quem/ui/ItemDetailScreen.kt \
        app/src/main/java/com/quem/app/QueMApp.kt \
        app/src/androidTest/java/com/quem/ui/ItemDetailScreenTest.kt
git commit -m "feat: add Share button, email dialog, and sharedWith indicator to ItemDetailScreen"
```
