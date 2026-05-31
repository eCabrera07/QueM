# Download + Merge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the sync cycle to download the remote snapshot, merge it into the local Room database using last-write-wins, then upload the merged state.

**Architecture:** New `MergeMappers.kt` converts server `Metadata*` types to Room entities (reverse of `SyncMappers.kt`). New `MergeCoordinator` applies a snapshot to the DAO: last-write-wins by `updatedAt` for items/attachments, append-only for history. `SyncCoordinator.sync()` is extended with a download → merge step before the existing upload.

**Tech Stack:** Kotlin coroutines, Room (`QueueDao`), `java.time.Instant`/`LocalDate`, existing `SyncManager`, `MetadataSnapshot` model

---

## File map

| Action | Path |
|---|---|
| Create | `app/src/main/java/com/quem/data/sync/MergeMappers.kt` |
| Create | `app/src/test/java/com/quem/data/sync/MergeMappersTest.kt` |
| Create | `app/src/main/java/com/quem/data/sync/MergeCoordinator.kt` |
| Create | `app/src/test/java/com/quem/data/sync/MergeCoordinatorTest.kt` |
| Modify | `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt` |
| Modify | `app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt` |

---

## Task 1: MergeMappers + unit tests

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/MergeMappers.kt`
- Create: `app/src/test/java/com/quem/data/sync/MergeMappersTest.kt`

Context: `SyncMappers.kt` converts domain models → server types. `MergeMappers.kt` is the reverse: server `Metadata*` types → Room entities. All three target entity types are in `com.quem.data.local`. Timestamps in the snapshot are ISO-8601 strings; entities use `java.time.Instant` and `java.time.LocalDate`. Items from the server are stamped `SyncState.SYNCED` — they came from Drive, so they're already synced.

- [ ] **Step 1: Write the failing unit tests**

Create `app/src/test/java/com/quem/data/sync/MergeMappersTest.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.SyncState
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueItemEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MergeMappersTest {

    @Test
    fun metadataQueueItemToEntityMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val completedAt = Instant.parse("2026-05-30T12:00:00Z")
        val remote = MetadataQueueItem(
            id = "item-1", driveId = "drive-1", title = "Read contract",
            description = "Legal notes", status = "QUEUED", priority = "HIGH",
            dueDate = "2026-06-01", tags = listOf("legal"),
            createdAt = now.toString(), updatedAt = now.toString(),
            completedAt = completedAt.toString(), dismissedAt = null
        )

        val entity = remote.toEntity()

        assertEquals("item-1", entity.id)
        assertEquals("drive-1", entity.driveId)
        assertEquals("Read contract", entity.title)
        assertEquals("Legal notes", entity.description)
        assertEquals("QUEUED", entity.status)
        assertEquals("HIGH", entity.priority)
        assertEquals(LocalDate.parse("2026-06-01"), entity.dueDate)
        assertEquals(listOf("legal"), entity.tags)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
        assertEquals(completedAt, entity.completedAt)
        assertNull(entity.dismissedAt)
        assertEquals(SyncState.SYNCED.name, entity.syncState)
    }

    @Test
    fun metadataQueueItemHandlesNullOptionalFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val remote = MetadataQueueItem(
            id = "item-1", driveId = null, title = "Read",
            description = null, status = "QUEUED", priority = null,
            dueDate = null, tags = emptyList(),
            createdAt = now.toString(), updatedAt = now.toString(),
            completedAt = null, dismissedAt = null
        )

        val entity = remote.toEntity()

        assertNull(entity.driveId)
        assertNull(entity.description)
        assertNull(entity.priority)
        assertNull(entity.dueDate)
        assertNull(entity.completedAt)
        assertNull(entity.dismissedAt)
    }

    @Test
    fun metadataAttachmentToEntityMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val remote = MetadataAttachment(
            id = "att-1", queueItemId = "item-1", type = "LINK",
            displayName = "Spec", textContent = null,
            url = "https://example.com", driveFileId = null,
            mimeType = null, createdAt = now.toString(), updatedAt = now.toString()
        )

        val entity = remote.toEntity()

        assertEquals("att-1", entity.id)
        assertEquals("item-1", entity.queueItemId)
        assertEquals("LINK", entity.type)
        assertEquals("Spec", entity.displayName)
        assertNull(entity.textContent)
        assertEquals("https://example.com", entity.url)
        assertNull(entity.driveFileId)
        assertNull(entity.mimeType)
        assertEquals(now, entity.createdAt)
        assertEquals(now, entity.updatedAt)
        assertEquals(SyncState.SYNCED.name, entity.syncState)
    }

    @Test
    fun metadataHistoryEntryToEntityMapsAllFields() {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val remote = MetadataHistoryEntry(
            id = "hist-1", queueItemId = "item-1",
            message = "Created", kind = "STATUS_CHANGE",
            createdAt = now.toString()
        )

        val entity = remote.toEntity()

        assertEquals("hist-1", entity.id)
        assertEquals("item-1", entity.queueItemId)
        assertEquals("Created", entity.message)
        assertEquals("STATUS_CHANGE", entity.kind)
        assertEquals(now, entity.createdAt)
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.sync.MergeMappersTest"`

Expected: FAILED — `toEntity()` functions do not exist yet.

- [ ] **Step 3: Create MergeMappers.kt**

Create `app/src/main/java/com/quem/data/sync/MergeMappers.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.SyncState
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueItemEntity
import java.time.Instant
import java.time.LocalDate

fun MetadataQueueItem.toEntity() = QueueItemEntity(
    id          = id,
    driveId     = driveId,
    title       = title,
    description = description,
    status      = status,
    priority    = priority,
    dueDate     = dueDate?.let { LocalDate.parse(it) },
    tags        = tags,
    createdAt   = Instant.parse(createdAt),
    updatedAt   = Instant.parse(updatedAt),
    completedAt = completedAt?.let { Instant.parse(it) },
    dismissedAt = dismissedAt?.let { Instant.parse(it) },
    syncState   = SyncState.SYNCED.name
)

fun MetadataAttachment.toEntity() = AttachmentEntity(
    id          = id,
    queueItemId = queueItemId,
    type        = type,
    displayName = displayName,
    textContent = textContent,
    url         = url,
    driveFileId = driveFileId,
    mimeType    = mimeType,
    createdAt   = Instant.parse(createdAt),
    updatedAt   = Instant.parse(updatedAt),
    syncState   = SyncState.SYNCED.name
)

fun MetadataHistoryEntry.toEntity() = HistoryEntryEntity(
    id          = id,
    queueItemId = queueItemId,
    message     = message,
    kind        = kind,
    createdAt   = Instant.parse(createdAt)
)
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.sync.MergeMappersTest"`

Expected: BUILD SUCCESSFUL, 4 tests passed.

- [ ] **Step 5: Commit**

```
git add app/src/main/java/com/quem/data/sync/MergeMappers.kt \
        app/src/test/java/com/quem/data/sync/MergeMappersTest.kt
git commit -m "feat: add MergeMappers — server Metadata* types to Room entity converters"
```

---

## Task 2: MergeCoordinator + unit tests

**Files:**
- Create: `app/src/main/java/com/quem/data/sync/MergeCoordinator.kt`
- Create: `app/src/test/java/com/quem/data/sync/MergeCoordinatorTest.kt`

Context: `MergeCoordinator` is pure — takes only a `QueueDao`. It calls `allItems()`, `allAttachments()`, `allHistory()` to read current local state, then `upsertItem/Attachment/HistoryEntry` to apply server changes. `FakeMergeDao` in the test file implements exactly these 6 methods with mutable backing lists; all other `QueueDao` methods throw `UnsupportedOperationException`. The `MergeMappers` from Task 1 are used internally.

- [ ] **Step 1: Write the failing unit tests**

Create `app/src/test/java/com/quem/data/sync/MergeCoordinatorTest.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.model.SyncState
import com.quem.data.local.AttachmentEntity
import com.quem.data.local.HistoryEntryEntity
import com.quem.data.local.QueueDao
import com.quem.data.local.QueueItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class MergeCoordinatorTest {

    @Test
    fun mergeAppliesNewItemFromSnapshot() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()

        MergeCoordinator(dao).merge(snapshot(items = listOf(metadataItem("server-1", now))))

        assertEquals(1, dao.items.size)
        assertEquals("server-1", dao.items.single().id)
        assertEquals(SyncState.SYNCED.name, dao.items.single().syncState)
    }

    @Test
    fun mergeAppliesNewerServerItem() = runTest {
        val earlier = Instant.parse("2026-05-29T10:00:00Z")
        val later   = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.items.add(localItem(id = "item-1", updatedAt = earlier, title = "Old title"))

        MergeCoordinator(dao).merge(snapshot(items = listOf(
            metadataItem("item-1", later).copy(title = "New title")
        )))

        assertEquals("New title", dao.items.single().title)
    }

    @Test
    fun mergePreservesNewerLocalItem() = runTest {
        val earlier = Instant.parse("2026-05-29T10:00:00Z")
        val later   = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.items.add(localItem(id = "item-1", updatedAt = later, title = "Local title"))

        MergeCoordinator(dao).merge(snapshot(items = listOf(
            metadataItem("item-1", earlier).copy(title = "Server title")
        )))

        assertEquals("Local title", dao.items.single().title)
    }

    @Test
    fun mergeDoesNotUpsertItemWithSameUpdatedAt() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.items.add(localItem(id = "item-1", updatedAt = now, title = "Local title"))

        MergeCoordinator(dao).merge(snapshot(items = listOf(
            metadataItem("item-1", now).copy(title = "Server title")
        )))

        // same updatedAt → local wins, not overwritten
        assertEquals("Local title", dao.items.single().title)
    }

    @Test
    fun mergeAppliesNewAttachmentFromSnapshot() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()

        MergeCoordinator(dao).merge(snapshot(attachments = listOf(metadataAttachment("att-1", "item-1", now))))

        assertEquals(1, dao.attachments.size)
        assertEquals("att-1", dao.attachments.single().id)
        assertEquals(SyncState.SYNCED.name, dao.attachments.single().syncState)
    }

    @Test
    fun mergePreservesNewerLocalAttachment() = runTest {
        val earlier = Instant.parse("2026-05-29T10:00:00Z")
        val later   = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.attachments.add(localAttachment(id = "att-1", queueItemId = "item-1", updatedAt = later))

        MergeCoordinator(dao).merge(snapshot(attachments = listOf(metadataAttachment("att-1", "item-1", earlier))))

        assertEquals(1, dao.attachments.size)  // not duplicated
        assertEquals(later, dao.attachments.single().updatedAt)  // local preserved
    }

    @Test
    fun mergeAppendsNewHistoryEntry() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()

        MergeCoordinator(dao).merge(snapshot(history = listOf(metadataHistory("hist-1", "item-1", now))))

        assertEquals(1, dao.history.size)
        assertEquals("hist-1", dao.history.single().id)
    }

    @Test
    fun mergeSkipsExistingHistoryEntry() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        dao.history.add(HistoryEntryEntity("hist-1", "item-1", "Created", "STATUS_CHANGE", now))

        MergeCoordinator(dao).merge(snapshot(history = listOf(metadataHistory("hist-1", "item-1", now))))

        assertEquals(1, dao.history.size)  // not duplicated
    }

    @Test
    fun mergeContinuesAfterMalformedSnapshotEntry() = runTest {
        val now = Instant.parse("2026-05-29T12:00:00Z")
        val dao = FakeMergeDao()
        val badItem = MetadataQueueItem(
            id = "bad", driveId = null, title = "Bad",
            description = null, status = "QUEUED", priority = null,
            dueDate = null, tags = emptyList(),
            createdAt = now.toString(),
            updatedAt = "not-a-date",    // malformed — will throw DateTimeParseException
            completedAt = null, dismissedAt = null
        )

        MergeCoordinator(dao).merge(snapshot(items = listOf(badItem, metadataItem("good-1", now))))

        assertEquals(1, dao.items.size)
        assertEquals("good-1", dao.items.single().id)
    }
}

// ---- Fakes ----

private class FakeMergeDao : QueueDao {
    val items       = mutableListOf<QueueItemEntity>()
    val attachments = mutableListOf<AttachmentEntity>()
    val history     = mutableListOf<HistoryEntryEntity>()

    override suspend fun allItems(): List<QueueItemEntity> = items.toList()
    override suspend fun allAttachments(): List<AttachmentEntity> = attachments.toList()
    override suspend fun allHistory(): List<HistoryEntryEntity> = history.toList()

    override suspend fun upsertItem(item: QueueItemEntity) {
        items.removeAll { it.id == item.id }
        items.add(item)
    }

    override suspend fun upsertAttachment(attachment: AttachmentEntity) {
        attachments.removeAll { it.id == attachment.id }
        attachments.add(attachment)
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) {
        // MergeCoordinator only calls this when id is not already present
        history.add(entry)
    }

    // Unused — MergeCoordinator does not call these
    override fun observeItemsByStatus(s: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun searchItems(s: List<String>, q: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun observeItem(id: String): Flow<QueueItemEntity?> = throw UnsupportedOperationException()
    override suspend fun pendingItems(): List<QueueItemEntity> = throw UnsupportedOperationException()
    override suspend fun markItemsSynced() = throw UnsupportedOperationException()
    override suspend fun markAttachmentsSynced() = throw UnsupportedOperationException()
    override suspend fun updateStatus(id: String, status: String, updatedAt: Instant, completedAt: Instant?, dismissedAt: Instant?): Int = throw UnsupportedOperationException()
    override suspend fun updateItemFields(id: String, title: String, description: String?, priority: String?, dueDate: LocalDate?, updatedAt: Instant): Int = throw UnsupportedOperationException()
    override fun observeAttachments(id: String): Flow<List<AttachmentEntity>> = throw UnsupportedOperationException()
    override fun observeHistory(id: String): Flow<List<HistoryEntryEntity>> = throw UnsupportedOperationException()
}

// ---- Builders ----

private fun snapshot(
    now: Instant = Instant.parse("2026-05-29T12:00:00Z"),
    items: List<MetadataQueueItem> = emptyList(),
    attachments: List<MetadataAttachment> = emptyList(),
    history: List<MetadataHistoryEntry> = emptyList()
) = MetadataSnapshot(version = 1, exportedAt = now.toString(),
    items = items, attachments = attachments, history = history)

private fun metadataItem(id: String, updatedAt: Instant) = MetadataQueueItem(
    id = id, driveId = null, title = "Item $id",
    description = null, status = "QUEUED", priority = null,
    dueDate = null, tags = emptyList(),
    createdAt = updatedAt.toString(), updatedAt = updatedAt.toString(),
    completedAt = null, dismissedAt = null
)

private fun metadataAttachment(id: String, queueItemId: String, updatedAt: Instant) = MetadataAttachment(
    id = id, queueItemId = queueItemId, type = "TEXT",
    displayName = "Attachment $id", textContent = null,
    url = null, driveFileId = null, mimeType = null,
    createdAt = updatedAt.toString(), updatedAt = updatedAt.toString()
)

private fun metadataHistory(id: String, queueItemId: String, createdAt: Instant) = MetadataHistoryEntry(
    id = id, queueItemId = queueItemId,
    message = "Created", kind = "STATUS_CHANGE",
    createdAt = createdAt.toString()
)

private fun localItem(id: String, updatedAt: Instant, title: String = "Item $id") = QueueItemEntity(
    id = id, driveId = null, title = title, description = null,
    status = "QUEUED", priority = null, dueDate = null, tags = emptyList(),
    createdAt = updatedAt, updatedAt = updatedAt, completedAt = null, dismissedAt = null,
    syncState = SyncState.PENDING_SYNC.name
)

private fun localAttachment(id: String, queueItemId: String, updatedAt: Instant) = AttachmentEntity(
    id = id, queueItemId = queueItemId, type = "TEXT",
    displayName = "Attachment $id", textContent = null,
    url = null, driveFileId = null, mimeType = null,
    createdAt = updatedAt, updatedAt = updatedAt,
    syncState = SyncState.PENDING_SYNC.name
)
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.sync.MergeCoordinatorTest"`

Expected: FAILED — `MergeCoordinator` does not exist yet.

- [ ] **Step 3: Create MergeCoordinator.kt**

Create `app/src/main/java/com/quem/data/sync/MergeCoordinator.kt`:

```kotlin
package com.quem.data.sync

import com.quem.data.local.QueueDao
import java.time.Instant

class MergeCoordinator(private val dao: QueueDao) {

    suspend fun merge(snapshot: MetadataSnapshot) {
        mergeItems(snapshot.items)
        mergeAttachments(snapshot.attachments)
        mergeHistory(snapshot.history)
    }

    private suspend fun mergeItems(remoteItems: List<MetadataQueueItem>) {
        val localById = dao.allItems().associateBy { it.id }
        for (remote in remoteItems) {
            val entity = runCatching { remote.toEntity() }.getOrNull() ?: continue
            val local = localById[entity.id]
            if (local == null || Instant.parse(remote.updatedAt) > local.updatedAt) {
                dao.upsertItem(entity)
            }
        }
    }

    private suspend fun mergeAttachments(remoteAttachments: List<MetadataAttachment>) {
        val localById = dao.allAttachments().associateBy { it.id }
        for (remote in remoteAttachments) {
            val entity = runCatching { remote.toEntity() }.getOrNull() ?: continue
            val local = localById[entity.id]
            if (local == null || Instant.parse(remote.updatedAt) > local.updatedAt) {
                dao.upsertAttachment(entity)
            }
        }
    }

    private suspend fun mergeHistory(remoteHistory: List<MetadataHistoryEntry>) {
        val localIds = dao.allHistory().map { it.id }.toSet()
        for (remote in remoteHistory) {
            if (remote.id !in localIds) {
                runCatching { dao.upsertHistoryEntry(remote.toEntity()) }
            }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.sync.MergeCoordinatorTest"`

Expected: BUILD SUCCESSFUL, 9 tests passed.

- [ ] **Step 5: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/data/sync/MergeCoordinator.kt \
        app/src/test/java/com/quem/data/sync/MergeCoordinatorTest.kt
git commit -m "feat: add MergeCoordinator — last-write-wins merge of remote snapshot into local DB"
```

---

## Task 3: Extend SyncCoordinator + update SyncCoordinatorTest

**Files:**
- Modify: `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt`
- Modify: `app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt`

Context: `SyncCoordinator` gains a `mergeCoordinator: MergeCoordinator = MergeCoordinator(dao)` parameter so existing `SyncCoordinatorTest` tests continue to pass (their `FakeCoordinatorDriveGateway.downloadTextFile` returns `null`, so merge is skipped). To test the new download→merge path, `FakeCoordinatorDriveGateway` gets a `downloadContent` parameter, and `FakeCoordinatorDao` gets working `upsertItem`/`upsertAttachment`/`upsertHistoryEntry` implementations (currently throwing — the merge will call them).

- [ ] **Step 1: Update FakeCoordinatorDriveGateway to support configurable download**

Open `app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt`. Change `FakeCoordinatorDriveGateway` to:

```kotlin
private class FakeCoordinatorDriveGateway(
    private val throwOnUpload: Exception? = null,
    private val downloadContent: String? = null          // new: null = no file on Drive
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

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? =
        downloadContent
}
```

- [ ] **Step 2: Update FakeCoordinatorDao to support upsert operations**

In the same file, update `FakeCoordinatorDao` to implement `upsertItem`, `upsertAttachment`, `upsertHistoryEntry` (they currently throw). Also convert the `var` list fields to use setters backed by mutable lists so they continue to work as before:

```kotlin
private class FakeCoordinatorDao : QueueDao {
    private val _items       = mutableListOf<QueueItemEntity>()
    private val _attachments = mutableListOf<AttachmentEntity>()
    private val _history     = mutableListOf<HistoryEntryEntity>()

    // Existing tests set these via `dao.items = listOf(...)` — keep the same API
    var items: List<QueueItemEntity>
        get() = _items.toList()
        set(value) { _items.clear(); _items.addAll(value) }

    var attachments: List<AttachmentEntity>
        get() = _attachments.toList()
        set(value) { _attachments.clear(); _attachments.addAll(value) }

    var history: List<HistoryEntryEntity>
        get() = _history.toList()
        set(value) { _history.clear(); _history.addAll(value) }

    var markItemsSyncedCalls = 0
    var markAttachmentsSyncedCalls = 0

    override suspend fun allItems(): List<QueueItemEntity>       = _items.toList()
    override suspend fun allAttachments(): List<AttachmentEntity> = _attachments.toList()
    override suspend fun allHistory(): List<HistoryEntryEntity>   = _history.toList()
    override suspend fun markItemsSynced()       { markItemsSyncedCalls++ }
    override suspend fun markAttachmentsSynced() { markAttachmentsSyncedCalls++ }

    override suspend fun upsertItem(item: QueueItemEntity) {
        _items.removeAll { it.id == item.id }
        _items.add(item)
    }

    override suspend fun upsertAttachment(attachment: AttachmentEntity) {
        _attachments.removeAll { it.id == attachment.id }
        _attachments.add(attachment)
    }

    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) {
        if (_history.none { it.id == entry.id }) _history.add(entry)
    }

    // Unused — SyncCoordinator and MergeCoordinator do not call these
    override fun observeItemsByStatus(s: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun searchItems(s: List<String>, q: String): Flow<List<QueueItemEntity>> = throw UnsupportedOperationException()
    override fun observeItem(id: String): Flow<QueueItemEntity?> = throw UnsupportedOperationException()
    override suspend fun pendingItems(): List<QueueItemEntity> = throw UnsupportedOperationException()
    override suspend fun updateStatus(id: String, status: String, updatedAt: Instant, completedAt: Instant?, dismissedAt: Instant?): Int = throw UnsupportedOperationException()
    override suspend fun updateItemFields(id: String, title: String, description: String?, priority: String?, dueDate: LocalDate?, updatedAt: Instant): Int = throw UnsupportedOperationException()
    override fun observeAttachments(id: String): Flow<List<AttachmentEntity>> = throw UnsupportedOperationException()
    override fun observeHistory(id: String): Flow<List<HistoryEntryEntity>> = throw UnsupportedOperationException()
}
```

Add `import java.time.LocalDate` to the test file imports if not present.

- [ ] **Step 3: Write the failing new SyncCoordinator tests**

Add these 2 new tests inside `SyncCoordinatorTest`. Also add `metadataItem` builder at the bottom alongside the existing builders:

```kotlin
@Test
fun syncMergesDownloadedDataBeforeUploading() = runTest {
    val now = Instant.parse("2026-05-29T12:00:00Z")
    val dao = FakeCoordinatorDao()  // starts empty
    val serverSnapshot = MetadataSnapshot(
        version = 1,
        exportedAt = now.toString(),
        items = listOf(metadataItem("server-item", now)),
        attachments = emptyList(),
        history = emptyList()
    )
    val driveGateway = FakeCoordinatorDriveGateway(
        downloadContent = MetadataSerializer.encode(serverSnapshot)
    )
    val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

    coordinator.sync()

    // Upload must contain the server item (proves download + merge ran before upload)
    val uploadedSnapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
    assertEquals(1, uploadedSnapshot.items.size)
    assertEquals("server-item", uploadedSnapshot.items.single().id)
}

@Test
fun syncSkipsMergeAndUploadsNormallyWhenNoSnapshotOnDrive() = runTest {
    val now = Instant.parse("2026-05-29T12:00:00Z")
    val dao = FakeCoordinatorDao().apply {
        items = listOf(queueItemEntity(id = "local-item", now = now))
    }
    // downloadContent = null (default) → no file on Drive
    val driveGateway = FakeCoordinatorDriveGateway()
    val coordinator = SyncCoordinator(dao, SyncManager(driveGateway), FixedClock(now))

    coordinator.sync()

    val uploadedSnapshot = MetadataSerializer.decode(driveGateway.uploadedContents.single())
    assertEquals(1, uploadedSnapshot.items.size)
    assertEquals("local-item", uploadedSnapshot.items.single().id)
}

// Add alongside existing builders at bottom of file:
private fun metadataItem(id: String, updatedAt: Instant) = MetadataQueueItem(
    id = id, driveId = null, title = "Item $id",
    description = null, status = "QUEUED", priority = null,
    dueDate = null, tags = emptyList(),
    createdAt = updatedAt.toString(), updatedAt = updatedAt.toString(),
    completedAt = null, dismissedAt = null
)
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.sync.SyncCoordinatorTest"`

Expected: FAILED — `SyncCoordinator` doesn't download or merge yet.

- [ ] **Step 5: Update SyncCoordinator.kt**

Replace the entire content of `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt`:

```kotlin
package com.quem.data.sync

import com.quem.core.time.Clock
import com.quem.data.local.QueueDao
import com.quem.data.local.toDomain

class SyncCoordinator(
    private val dao: QueueDao,
    private val syncManager: SyncManager,
    private val clock: Clock,
    private val mergeCoordinator: MergeCoordinator = MergeCoordinator(dao)
) {
    suspend fun sync() {
        // 1. Download remote snapshot and merge into local DB
        val remoteSnapshot = syncManager.download()
        if (remoteSnapshot != null) {
            mergeCoordinator.merge(remoteSnapshot)
        }

        // 2. Upload merged local state
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

        // 3. Mark synced
        dao.markItemsSynced()
        dao.markAttachmentsSynced()
    }
}
```

- [ ] **Step 6: Run all tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.sync.SyncCoordinatorTest"`

Expected: BUILD SUCCESSFUL, all 6 tests pass (4 existing + 2 new).

- [ ] **Step 7: Run full unit test suite**

Run: `./gradlew :app:test`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/quem/data/sync/SyncCoordinator.kt \
        app/src/test/java/com/quem/data/sync/SyncCoordinatorTest.kt
git commit -m "feat: extend SyncCoordinator to download and merge remote snapshot before uploading"
```
