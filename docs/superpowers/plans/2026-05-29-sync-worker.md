# SyncWorker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement `SyncWorker.doWork()` so that all local data is uploaded to Google Drive on a 15-minute schedule and on manual trigger from the Settings screen.

**Architecture:** The worker reads a persisted account email from `DriveAccountPreferences`, builds a `Drive` service from `GoogleAccountCredential`, then delegates all logic to a new `SyncCoordinator` that reads all Room data, builds a `MetadataSnapshot`, uploads it, and marks items as synced. The coordinator is testable in isolation with a fake `DriveGateway`.

**Tech Stack:** WorkManager (`CoroutineWorker`), Room, `google-api-client-android` (`GoogleAccountCredential`, `NetHttpTransport`, `GsonFactory`), `google-api-services-drive` (`Drive`), Kotlin coroutines, kotlinx-serialization

---

## File map

| Action | Path |
|---|---|
| Create | `app/src/main/java/com/quem/drive/DriveAccountPreferences.kt` |
| Create | `app/src/androidTest/java/com/quem/drive/DriveAccountPreferencesTest.kt` |
| Modify | `app/src/main/java/com/quem/data/local/QueueDao.kt` |
| Modify | `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt` (FakeQueueDao only) |
| Create | `app/src/main/java/com/quem/data/sync/SyncMappers.kt` |
| Create | `app/src/test/java/com/quem/data/sync/SyncMappersTest.kt` |
| Create | `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt` |
| Create | `app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt` |
| Modify | `app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt` |
| Modify | `app/src/main/java/com/quem/app/AppDependencies.kt` |
| Modify | `app/src/main/java/com/quem/data/sync/SyncWorker.kt` |
| Modify | `app/src/main/java/com/quem/data/sync/SyncScheduler.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |

---

## Task 1: DriveAccountPreferences

**Files:**
- Create: `app/src/main/java/com/quem/drive/DriveAccountPreferences.kt`
- Create: `app/src/androidTest/java/com/quem/drive/DriveAccountPreferencesTest.kt`

Context: The `SyncWorker` runs in the background after the app is killed and needs the connected Google account email. `SharedPreferences` persists it across process restarts. This class is an Android instrumented test (uses `ApplicationProvider`) matching the pattern in `DrivePickerRepositoryTest.kt`.

- [ ] **Step 1: Write the failing instrumented tests**

Create `app/src/androidTest/java/com/quem/drive/DriveAccountPreferencesTest.kt`:

```kotlin
package com.quem.drive

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class DriveAccountPreferencesTest {
    private lateinit var prefs: DriveAccountPreferences

    @Before
    fun setUp() {
        prefs = DriveAccountPreferences(ApplicationProvider.getApplicationContext())
        prefs.clear() // ensure clean state between tests
    }

    @Test
    fun loadReturnsNullWhenNothingSaved() {
        assertNull(prefs.load())
    }

    @Test
    fun saveAndLoadReturnsEmail() {
        prefs.save("user@example.com")
        assertEquals("user@example.com", prefs.load())
    }

    @Test
    fun clearRemovesEmail() {
        prefs.save("user@example.com")
        prefs.clear()
        assertNull(prefs.load())
    }

    @Test
    fun saveOverwritesPreviousEmail() {
        prefs.save("old@example.com")
        prefs.save("new@example.com")
        assertEquals("new@example.com", prefs.load())
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.drive.DriveAccountPreferencesTest`

Expected: FAILED — `DriveAccountPreferences` does not exist yet.

- [ ] **Step 3: Implement DriveAccountPreferences**

Create `app/src/main/java/com/quem/drive/DriveAccountPreferences.kt`:

```kotlin
package com.quem.drive

import android.content.Context

class DriveAccountPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(email: String) {
        prefs.edit().putString(KEY_EMAIL, email).apply()
    }

    fun load(): String? = prefs.getString(KEY_EMAIL, null)

    fun clear() {
        prefs.edit().remove(KEY_EMAIL).apply()
    }

    private companion object {
        const val PREFS_NAME = "drive_account"
        const val KEY_EMAIL  = "account_email"
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.quem.drive.DriveAccountPreferencesTest`

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/quem/drive/DriveAccountPreferences.kt \
        app/src/androidTest/java/com/quem/drive/DriveAccountPreferencesTest.kt
git commit -m "feat: add DriveAccountPreferences to persist account email for background sync"
```

---

## Task 2: QueueDao bulk-read and mark-synced methods

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/QueueDao.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt` (FakeQueueDao class only)

Context: `SyncCoordinator` needs to read ALL items/attachments/history (not just pending-sync) to build the full snapshot, then mark the pending ones as synced after a successful upload. The `QueueDao` interface is extended with 5 new methods; `FakeQueueDao` in the test file must implement them to keep the existing tests compiling.

- [ ] **Step 1: Add 5 methods to the QueueDao interface**

Open `app/src/main/java/com/quem/data/local/QueueDao.kt`. Add these methods after the existing `pendingItems()` method (after line 31):

```kotlin
@Query("SELECT * FROM queue_items")
suspend fun allItems(): List<QueueItemEntity>

@Query("SELECT * FROM attachments")
suspend fun allAttachments(): List<AttachmentEntity>

@Query("SELECT * FROM history_entries")
suspend fun allHistory(): List<HistoryEntryEntity>

@Query("UPDATE queue_items SET syncState = 'SYNCED' WHERE syncState = 'PENDING_SYNC'")
suspend fun markItemsSynced()

@Query("UPDATE attachments SET syncState = 'SYNCED' WHERE syncState = 'PENDING_SYNC'")
suspend fun markAttachmentsSynced()
```

The complete file should look like:

```kotlin
package com.quem.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.Instant

@Dao
interface QueueDao {
    @Query("SELECT * FROM queue_items WHERE status = :status ORDER BY updatedAt DESC")
    fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>>

    @Query(
        """
        SELECT * FROM queue_items
        WHERE status IN (:statuses)
        AND (
            title LIKE '%' || :query || '%' ESCAPE '\'
            OR description LIKE '%' || :query || '%' ESCAPE '\'
        )
        ORDER BY updatedAt DESC, id ASC
        """
    )
    fun searchItems(statuses: List<String>, query: String): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items WHERE id = :id LIMIT 1")
    fun observeItem(id: String): Flow<QueueItemEntity?>

    @Query("SELECT * FROM queue_items WHERE syncState = 'PENDING_SYNC'")
    suspend fun pendingItems(): List<QueueItemEntity>

    @Query("SELECT * FROM queue_items")
    suspend fun allItems(): List<QueueItemEntity>

    @Query("SELECT * FROM attachments")
    suspend fun allAttachments(): List<AttachmentEntity>

    @Query("SELECT * FROM history_entries")
    suspend fun allHistory(): List<HistoryEntryEntity>

    @Query("UPDATE queue_items SET syncState = 'SYNCED' WHERE syncState = 'PENDING_SYNC'")
    suspend fun markItemsSynced()

    @Query("UPDATE attachments SET syncState = 'SYNCED' WHERE syncState = 'PENDING_SYNC'")
    suspend fun markAttachmentsSynced()

    @Upsert
    suspend fun upsertItem(item: QueueItemEntity)

    @Query(
        """
        UPDATE queue_items
        SET status = :status,
            updatedAt = :updatedAt,
            completedAt = :completedAt,
            dismissedAt = :dismissedAt,
            syncState = 'PENDING_SYNC'
        WHERE id = :id
        """
    )
    suspend fun updateStatus(
        id: String,
        status: String,
        updatedAt: Instant,
        completedAt: Instant?,
        dismissedAt: Instant?
    ): Int

    @Upsert
    suspend fun upsertAttachment(attachment: AttachmentEntity)

    @Upsert
    suspend fun upsertHistoryEntry(entry: HistoryEntryEntity)

    @Query("SELECT * FROM attachments WHERE queueItemId = :queueItemId ORDER BY createdAt DESC, id ASC")
    fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>>

    @Query("SELECT * FROM history_entries WHERE queueItemId = :queueItemId ORDER BY createdAt DESC")
    fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>>
}
```

- [ ] **Step 2: Add the 5 new method implementations to FakeQueueDao**

Open `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`. Find `private class FakeQueueDao : QueueDao` (near the bottom). Add these 5 method implementations to it — place them after the existing `pendingItems()` override:

```kotlin
override suspend fun allItems(): List<QueueItemEntity> = entities.value

override suspend fun allAttachments(): List<AttachmentEntity> = attachmentEntities.value

override suspend fun allHistory(): List<HistoryEntryEntity> = historyEntities.value

override suspend fun markItemsSynced() {
    entities.value = entities.value.map { item ->
        if (item.syncState == SyncState.PENDING_SYNC.name) {
            item.copy(syncState = SyncState.SYNCED.name)
        } else {
            item
        }
    }
}

override suspend fun markAttachmentsSynced() {
    attachmentEntities.value = attachmentEntities.value.map { attachment ->
        if (attachment.syncState == SyncState.PENDING_SYNC.name) {
            attachment.copy(syncState = SyncState.SYNCED.name)
        } else {
            attachment
        }
    }
}
```

- [ ] **Step 3: Run existing tests to verify no regression**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/quem/data/local/QueueDao.kt \
        app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: add bulk-read and mark-synced methods to QueueDao"
```

---

## Task 3: SyncMappers

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/SyncMappers.kt`
- Create: `app/src/test/java/com/quem/data/sync/SyncMappersTest.kt`

Context: `SyncCoordinator` needs to convert domain models (`QueueItem`, `Attachment`, `HistoryEntry`) to the export shapes (`ExportableQueueItem`, `MetadataAttachment`, `MetadataHistoryEntry`) that `MetadataExporter` expects. These are pure extension functions with no Android dependencies — they live in `com.quem.data.sync` alongside the other sync classes.

- [ ] **Step 1: Write the failing unit tests**

Create `app/src/test/java/com/quem/data/sync/SyncMappersTest.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SyncMappersTest {

    @Test
    fun queueItemToExportableMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val item = QueueItem(
            id = "item-1",
            driveId = "drive-1",
            title = "Read contract",
            description = "Legal notes",
            status = QueueStatus.QUEUED,
            priority = Priority.HIGH,
            dueDate = LocalDate.parse("2026-05-30"),
            tags = listOf("legal"),
            createdAt = now,
            updatedAt = now,
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )

        val exportable = item.toExportable()

        assertEquals("item-1", exportable.id)
        assertEquals("drive-1", exportable.driveId)
        assertEquals("Read contract", exportable.title)
        assertEquals("Legal notes", exportable.description)
        assertEquals("QUEUED", exportable.status)
        assertEquals("HIGH", exportable.priority)
        assertEquals("2026-05-30", exportable.dueDate)
        assertEquals(listOf("legal"), exportable.tags)
        assertEquals(now.toString(), exportable.createdAt)
        assertEquals(now.toString(), exportable.updatedAt)
        assertNull(exportable.completedAt)
        assertNull(exportable.dismissedAt)
    }

    @Test
    fun queueItemToExportableHandlesNullOptionalFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val completedAt = Instant.parse("2026-05-29T13:00:00Z")
        val item = QueueItem(
            id = "item-2",
            driveId = null,
            title = "Done item",
            description = null,
            status = QueueStatus.DONE,
            priority = null,
            dueDate = null,
            tags = emptyList(),
            createdAt = now,
            updatedAt = completedAt,
            completedAt = completedAt,
            dismissedAt = null,
            syncState = SyncState.SYNCED
        )

        val exportable = item.toExportable()

        assertNull(exportable.driveId)
        assertNull(exportable.description)
        assertNull(exportable.priority)
        assertNull(exportable.dueDate)
        assertEquals(completedAt.toString(), exportable.completedAt)
        assertNull(exportable.dismissedAt)
    }

    @Test
    fun attachmentToMetadataMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val attachment = Attachment(
            id = "att-1",
            queueItemId = "item-1",
            type = AttachmentType.LINK,
            displayName = "Spec",
            textContent = null,
            url = "https://example.com/spec",
            driveFileId = null,
            mimeType = null,
            createdAt = now,
            updatedAt = now,
            syncState = SyncState.PENDING_SYNC
        )

        val metadata = attachment.toMetadata()

        assertEquals("att-1", metadata.id)
        assertEquals("item-1", metadata.queueItemId)
        assertEquals("LINK", metadata.type)
        assertEquals("Spec", metadata.displayName)
        assertNull(metadata.textContent)
        assertEquals("https://example.com/spec", metadata.url)
        assertNull(metadata.driveFileId)
        assertNull(metadata.mimeType)
        assertEquals(now.toString(), metadata.createdAt)
        assertEquals(now.toString(), metadata.updatedAt)
    }

    @Test
    fun historyEntryToMetadataMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val entry = HistoryEntry(
            id = "hist-1",
            queueItemId = "item-1",
            message = "Created",
            kind = HistoryKind.STATUS_CHANGE,
            createdAt = now
        )

        val metadata = entry.toMetadata()

        assertEquals("hist-1", metadata.id)
        assertEquals("item-1", metadata.queueItemId)
        assertEquals("Created", metadata.message)
        assertEquals("STATUS_CHANGE", metadata.kind)
        assertEquals(now.toString(), metadata.createdAt)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.sync.SyncMappersTest"`

Expected: FAILED — `toExportable`, `toMetadata` do not exist yet.

- [ ] **Step 3: Implement SyncMappers**

Create `app/src/main/java/com/quem/data/sync/SyncMappers.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.Attachment
import com.quem.core.model.HistoryEntry
import com.quem.core.model.QueueItem

fun QueueItem.toExportable() = ExportableQueueItem(
    id          = id,
    title       = title,
    status      = status.name,
    driveId     = driveId,
    description = description,
    priority    = priority?.name,
    dueDate     = dueDate?.toString(),
    tags        = tags,
    createdAt   = createdAt.toString(),
    updatedAt   = updatedAt.toString(),
    completedAt = completedAt?.toString(),
    dismissedAt = dismissedAt?.toString()
)

fun Attachment.toMetadata() = MetadataAttachment(
    id          = id,
    queueItemId = queueItemId,
    type        = type.name,
    displayName = displayName,
    textContent = textContent,
    url         = url,
    driveFileId = driveFileId,
    mimeType    = mimeType,
    createdAt   = createdAt.toString(),
    updatedAt   = updatedAt.toString()
)

fun HistoryEntry.toMetadata() = MetadataHistoryEntry(
    id          = id,
    queueItemId = queueItemId,
    message     = message,
    kind        = kind.name,
    createdAt   = createdAt.toString()
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.sync.SyncMappersTest"`

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/quem/data/sync/SyncMappers.kt \
        app/src/test/java/com/quem/data/sync/SyncMappersTest.kt
git commit -m "feat: add SyncMappers — domain to export converters for snapshot building"
```

---

## Task 4: SyncCoordinator

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt`
- Create: `app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt`

Context: `SyncCoordinator` contains all the sync logic: read all data from Room, build a `MetadataSnapshot`, upload via `SyncManager`, mark items/attachments as synced. It takes `QueueDao`, `SyncManager`, and `Clock` as constructor parameters — all fakeable in tests. `SyncWorker` will be a thin shell around it.

`FakeCoordinatorDao` in this test file implements only the 5 methods `SyncCoordinator` calls, throwing `UnsupportedOperationException` for the rest. `FakeDriveGateway` captures upload calls and can be configured to throw.

- [ ] **Step 1: Write the failing unit tests**

Create `app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryKind
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueDao
import com.quem.data.local.QueueItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant

class SyncCoordinatorTest {

    @Test
    fun syncUploadsSnapshotContainingAllItemsAttachmentsAndHistory() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao().apply {
            items = listOf(queueItemEntity(id = "item-1", now = now))
            attachments = listOf(attachmentEntity(id = "att-1", queueItemId = "item-1", now = now))
            history = listOf(historyEntity(id = "hist-1", queueItemId = "item-1", now = now))
        }
        val driveGateway = FakeDriveGateway()
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        assertEquals(1, driveGateway.uploadedContents.size)
        val snapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
        assertEquals(1, snapshot.items.size)
        assertEquals("item-1", snapshot.items.single().id)
        assertEquals(1, snapshot.attachments.size)
        assertEquals("att-1", snapshot.attachments.single().id)
        assertEquals(1, snapshot.history.size)
        assertEquals("hist-1", snapshot.history.single().id)
    }

    @Test
    fun syncMarksItemsAndAttachmentsSyncedAfterSuccessfulUpload() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao()
        val coordinator = SyncCoordinator(dao, SyncManager(FakeDriveGateway()), FixedClock(now))

        coordinator.sync()

        assertEquals(1, dao.markItemsSyncedCalls)
        assertEquals(1, dao.markAttachmentsSyncedCalls)
    }

    @Test
    fun syncDoesNotMarkSyncedIfUploadThrows() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeCoordinatorDao()
        val driveGateway = FakeDriveGateway(throwOnUpload = IOException("Network error"))
        val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

        try {
            coordinator.sync()
        } catch (_: IOException) {
            // expected — let it propagate to the worker
        }

        assertEquals(0, dao.markItemsSyncedCalls)
        assertEquals(0, dao.markAttachmentsSyncedCalls)
    }

    @Test
    fun syncUploadsToCorrectFolderAndFileName() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val driveGateway = FakeDriveGateway()
        val coordinator = SyncCoordinator(FakeCoordinatorDao(), SyncManager(driveGateway), FixedClock(now))

        coordinator.sync()

        assertEquals("QueM", driveGateway.lastFolderName)
        assertEquals("queue-metadata.json", driveGateway.lastFileName)
    }
}

// ---- Fakes ----

private class FakeDriveGateway(
    private val throwOnUpload: Exception? = null
) : DriveGateway {
    val uploadedContents = mutableListOf<String>()
    var lastFolderName: String? = null
    var lastFileName: String? = null

    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) {
        throwOnUpload?.let { throw it }
        lastFolderName = folderName
        lastFileName = fileName
        uploadedContents.add(content)
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? = null
}

private class FakeCoordinatorDao : QueueDao {
    var items: List<QueueItemEntity> = emptyList()
    var attachments: List<AttachmentEntity> = emptyList()
    var history: List<HistoryEntryEntity> = emptyList()
    var markItemsSyncedCalls = 0
    var markAttachmentsSyncedCalls = 0

    override suspend fun allItems(): List<QueueItemEntity> = items
    override suspend fun allAttachments(): List<AttachmentEntity> = attachments
    override suspend fun allHistory(): List<HistoryEntryEntity> = history
    override suspend fun markItemsSynced() { markItemsSyncedCalls++ }
    override suspend fun markAttachmentsSynced() { markAttachmentsSyncedCalls++ }

    // Unused — SyncCoordinator does not call these
    override fun observeItemsByStatus(status: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun searchItems(statuses: List<String>, query: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun observeItem(id: String): Flow<QueueItemEntity?> = throw UnsupportedOperationException()
    override suspend fun pendingItems(): List<QueueItemEntity> = throw UnsupportedOperationException()
    override suspend fun upsertItem(item: QueueItemEntity) = throw UnsupportedOperationException()
    override suspend fun updateStatus(id: String, status: String, updatedAt: Instant, completedAt: Instant?, dismissedAt: Instant?): Int = throw UnsupportedOperationException()
    override suspend fun upsertAttachment(attachment: AttachmentEntity) = throw UnsupportedOperationException()
    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) = throw UnsupportedOperationException()
    override fun observeAttachments(queueItemId: String): Flow<List<AttachmentEntity>> = throw UnsupportedOperationException()
    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>> = throw UnsupportedOperationException()
}

// ---- Builders ----

private fun queueItemEntity(id: String, now: Instant) = QueueItemEntity(
    id          = id,
    driveId     = null,
    title       = "Item $id",
    description = null,
    status      = QueueStatus.QUEUED.name,
    priority    = null,
    dueDate     = null,
    tags        = emptyList(),
    createdAt   = now,
    updatedAt   = now,
    completedAt = null,
    dismissedAt = null,
    syncState   = SyncState.PENDING_SYNC.name
)

private fun attachmentEntity(id: String, queueItemId: String, now: Instant) = AttachmentEntity(
    id          = id,
    queueItemId = queueItemId,
    type        = AttachmentType.TEXT.name,
    displayName = "Attachment $id",
    textContent = "Content",
    url         = null,
    driveFileId = null,
    mimeType    = null,
    createdAt   = now,
    updatedAt   = now,
    syncState   = SyncState.PENDING_SYNC.name
)

private fun historyEntity(id: String, queueItemId: String, now: Instant) = HistoryEntryEntity(
    id          = id,
    queueItemId = queueItemId,
    message     = "Created",
    kind        = HistoryKind.STATUS_CHANGE.name,
    createdAt   = now
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.sync.SyncCoordinatorTest"`

Expected: FAILED — `SyncCoordinator` does not exist yet.

- [ ] **Step 3: Implement SyncCoordinator**

Create `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.time.Clock
import com.quem.data.local.QueueDao
import com.quem.data.local.toDomain

class SyncCoordinator(
    private val dao: QueueDao,
    private val syncManager: SyncManager,
    private val clock: Clock
) {
    suspend fun sync() {
        val items       = dao.allItems().map { it.toDomain() }
        val attachments = dao.allAttachments().map { it.toDomain() }
        val history     = dao.allHistory().map { it.toDomain() }

        val snapshot = MetadataExporter.export(
            exportedAt  = clock.now().toString(),
            items       = items.map { it.toExportable() },
            attachments = attachments.map { it.toMetadata() },
            history     = history.map { it.toMetadata() }
        )

        syncManager.upload(snapshot)

        dao.markItemsSynced()
        dao.markAttachmentsSynced()
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.sync.SyncCoordinatorTest"`

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: Run all unit tests to verify no regression**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/data/sync/SyncCoordinator.kt \
        app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt
git commit -m "feat: add SyncCoordinator — upload all local data to Drive and mark synced"
```

---

## Task 5: SyncWorker + AppDependencies + GoogleDriveConnectionRepository

**Files:**
- Modify: `app/src/main/java/com/quem/data/sync/SyncWorker.kt`
- Modify: `app/src/main/java/com/quem/app/AppDependencies.kt`
- Modify: `app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt`

Context: Three wiring changes. `GoogleDriveConnectionRepository` gains a `DriveAccountPreferences?` parameter (nullable with null default so existing tests don't break) and calls `save/clear` on connect/disconnect. `AppDependencies` creates a `DriveAccountPreferences` instance, exposes the `QueueDao` (needed by `SyncWorker`), and passes preferences to `GoogleDriveConnectionRepository`. `SyncWorker.doWork()` reads the email, builds the `Drive` service, and delegates to `SyncCoordinator`.

No unit tests for `SyncWorker` itself — credential-building and the Application cast are the Android boundary. All logic under test lives in `SyncCoordinator`.

- [ ] **Step 1: Update GoogleDriveConnectionRepository**

Open `app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt`. The full updated file:

```kotlin
package com.quem.drive

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleDriveConnectionRepository(
    initialAuthorizationCoordinator: DriveAuthorizationCoordinator? = null,
    private val driveAccountPreferences: DriveAccountPreferences? = null
) : DriveConnectionRepository {
    private val mutableState = MutableStateFlow<DriveConnectionState>(DriveConnectionState.Disconnected)
    private var authorizationCoordinator: DriveAuthorizationCoordinator? = initialAuthorizationCoordinator

    override val state: StateFlow<DriveConnectionState> = mutableState.asStateFlow()

    fun setAuthorizationCoordinator(authorizationCoordinator: DriveAuthorizationCoordinator) {
        this.authorizationCoordinator = authorizationCoordinator
    }

    override fun requestSignIn() {
        val coordinator = authorizationCoordinator
        if (coordinator == null) {
            mutableState.value = DriveConnectionState.Error("Google Drive authorization unavailable")
            return
        }

        coordinator.requestAuthorization { result ->
            when (result) {
                is DriveAuthorizationRequestResult.Authorized -> connect(result.grant)
                is DriveAuthorizationRequestResult.ResolutionRequired -> {
                    coordinator.launchResolution(result.request)
                }
                is DriveAuthorizationRequestResult.Failed -> {
                    mutableState.value = DriveConnectionState.Error(result.message)
                }
            }
        }
    }

    override fun disconnect() {
        driveAccountPreferences?.clear()
        mutableState.value = DriveConnectionState.Disconnected
    }

    fun handleResolutionResult(activityResult: ActivityResultData) {
        val coordinator = authorizationCoordinator
        if (coordinator == null) {
            mutableState.value = DriveConnectionState.Error("Google Drive authorization unavailable")
            return
        }

        when (val result = coordinator.parseResolutionResult(activityResult)) {
            is DriveAuthorizationResolutionResult.Authorized -> connect(result.grant)
            DriveAuthorizationResolutionResult.Cancelled -> {
                mutableState.value = DriveConnectionState.Error("Google Drive authorization cancelled")
            }
            is DriveAuthorizationResolutionResult.Failed -> {
                mutableState.value = DriveConnectionState.Error(result.message)
            }
        }
    }

    private fun connect(grant: DriveAuthorizationGrant) {
        driveAccountPreferences?.save(grant.accountEmail)
        mutableState.value = DriveConnectionState.Connected(
            DriveAccount(email = grant.accountEmail)
        )
    }
}
```

- [ ] **Step 2: Update AppDependencies**

Open `app/src/main/java/com/quem/app/AppDependencies.kt`. The full updated file:

```kotlin
package com.quem.app

import android.content.Context
import androidx.room.Room
import com.quem.core.time.SystemClock
import com.quem.data.local.QueueDao
import com.quem.data.local.QueMDatabase
import com.quem.data.repository.QueueRepository
import com.quem.data.repository.RoomQueueRepository
import com.quem.drive.DriveAccountPreferences
import com.quem.drive.DrivePickerRepository
import com.quem.drive.GoogleDriveConnectionRepository
import java.util.UUID

class AppDependencies(context: Context) {
    private val database: QueMDatabase = Room.databaseBuilder(
        context.applicationContext,
        QueMDatabase::class.java,
        DATABASE_NAME
    ).build()

    val dao: QueueDao = database.queueDao()

    private val driveAccountPreferences = DriveAccountPreferences(context.applicationContext)

    val queueRepository: QueueRepository = RoomQueueRepository(
        dao = dao,
        clock = SystemClock(),
        idProvider = { UUID.randomUUID().toString() }
    )

    val driveConnectionRepository: GoogleDriveConnectionRepository = GoogleDriveConnectionRepository(
        driveAccountPreferences = driveAccountPreferences
    )

    val drivePickerRepository: DrivePickerRepository = DrivePickerRepository(
        contentResolver = context.applicationContext.contentResolver
    )

    private companion object {
        const val DATABASE_NAME = "quem.db"
    }
}
```

- [ ] **Step 3: Implement SyncWorker.doWork()**

Open `app/src/main/java/com/quem/data/sync/SyncWorker.kt`. The full updated file:

```kotlin
package com.quem.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.quem.app.QueMApplication
import com.quem.core.time.SystemClock
import com.quem.drive.DriveAccountPreferences
import com.quem.drive.GoogleDriveAuthorizationCoordinator
import com.quem.drive.GoogleDriveGateway
import java.io.IOException

class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result {
        return try {
            val email = DriveAccountPreferences(applicationContext).load()
                ?: return Result.success() // not signed in — skip silently

            val credential = GoogleAccountCredential
                .usingOAuth2(applicationContext, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
                .setSelectedAccountName(email)

            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            )
                .setApplicationName("QueM")
                .build()

            val deps = (applicationContext as QueMApplication).dependencies

            SyncCoordinator(
                dao         = deps.dao,
                syncManager = SyncManager(GoogleDriveGateway(drive)),
                clock       = SystemClock()
            ).sync()

            Result.success()
        } catch (e: IOException) {
            Log.w(TAG, "Sync failed — will retry", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed permanently", e)
            Result.failure()
        }
    }

    private companion object {
        const val TAG = "SyncWorker"
    }
}
```

- [ ] **Step 4: Run all unit tests to verify no regression**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/quem/data/sync/SyncWorker.kt \
        app/src/main/java/com/quem/app/AppDependencies.kt \
        app/src/main/java/com/quem/drive/GoogleDriveConnectionRepository.kt
git commit -m "feat: implement SyncWorker.doWork() — upload snapshot to Drive via SyncCoordinator"
```

---

## Task 6: SyncScheduler.scheduleOnce() + QueMApp manual trigger

**Files:**
- Modify: `app/src/main/java/com/quem/data/sync/SyncScheduler.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`

Context: The Settings screen already has an `onManualSync` parameter that is currently `{}`. Adding `SyncScheduler.scheduleOnce()` and wiring it in `QueMApp.kt` completes the manual sync trigger. `LocalContext.current` provides the `Context` from inside the composable.

- [ ] **Step 1: Add scheduleOnce to SyncScheduler**

Open `app/src/main/java/com/quem/data/sync/SyncScheduler.kt`. The full updated file:

```kotlin
package com.quem.data.sync

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.util.concurrent.TimeUnit

object SyncScheduler {
    val periodicInterval: Duration = Duration.ofMinutes(15)

    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<SyncWorker>(
            periodicInterval.toMinutes(),
            TimeUnit.MINUTES
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun scheduleOnce(context: Context) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    private const val PERIODIC_SYNC_NAME = "quem-periodic-sync"
}
```

- [ ] **Step 2: Wire onManualSync in QueMApp**

Open `app/src/main/java/com/quem/app/QueMApp.kt`. Add the `LocalContext` import and wire `onManualSync`. The full updated file:

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
    val items by viewModel.items.collectAsStateWithLifecycle()
    val selectedItem by viewModel.selectedItem.collectAsStateWithLifecycle()
    val driveConnectionState by viewModel.driveConnectionState.collectAsStateWithLifecycle()

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

- [ ] **Step 3: Run all unit tests to verify no regression**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```
git add app/src/main/java/com/quem/data/sync/SyncScheduler.kt \
        app/src/main/java/com/quem/app/QueMApp.kt
git commit -m "feat: wire manual sync trigger — Settings Sync Now button enqueues one-shot SyncWorker"
```
