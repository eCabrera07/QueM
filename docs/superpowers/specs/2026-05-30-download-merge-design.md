# Download + Merge — Design Spec

**Date:** 2026-05-30
**Feature:** Extend the sync cycle to download the remote snapshot and merge it into the local Room database before uploading
**Status:** Approved

---

## Context

The `SyncWorker` currently uploads all local data to Drive on each cycle. `SyncManager.download()` is already implemented but never called. The merge layer is the gap: given a downloaded `MetadataSnapshot`, apply it to the local Room DB using a last-write-wins strategy, then upload the merged state.

---

## Scope

**In scope:**
- `MergeMappers.kt` — `MetadataQueueItem/Attachment/HistoryEntry → *Entity` converters (reverse of `SyncMappers.kt`)
- `MergeCoordinator` — pure merge logic; last-write-wins by `updatedAt` for items/attachments; append-only for history
- `SyncCoordinator.sync()` extended — download → merge → upload
- Unit tests for all three components

**Out of scope:**
- Deletion propagation (items are never truly deleted, only archived as DONE/DISMISSED)
- Conflict UI (no user-facing conflict resolution; last-write-wins is silent)
- Multi-device simultaneous edit recovery (rely on `updatedAt` ordering)

---

## Architecture

### Merge strategy

**Items and Attachments — last-write-wins by `updatedAt`:**

For each entity in the downloaded snapshot:
- Not in local DB → `upsertItem`/`upsertAttachment` (new from another device)
- Exists locally and `snapshot.updatedAt > local.updatedAt` → `upsert` (server is newer)
- Exists locally and `local.updatedAt >= snapshot.updatedAt` → skip (local wins or same)

Entities from the server are stamped `syncState = SYNCED`.

**History entries — append-only:**

History is immutable. If the entry ID exists locally, skip. Otherwise insert it. This is equivalent to a set union by ID — history only grows, never changes.

---

### `MergeMappers.kt` (new — `com.quem.data.sync`)

Reverse of `SyncMappers.kt`. Each converter wraps parsing in `runCatching`; callers skip the entry on failure.

```kotlin
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

If any ISO-8601 string is malformed, `Instant.parse` / `LocalDate.parse` throws — the `runCatching` wrapping in `MergeCoordinator` catches it and skips the entry.

---

### `MergeCoordinator` (new — `com.quem.data.sync`)

Pure class. Takes only `QueueDao`. No clock or ID provider needed.

```kotlin
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

---

### `SyncCoordinator.kt` (modify)

Add `mergeCoordinator: MergeCoordinator` with a default so existing tests don't break.

```kotlin
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

        // 2. Upload merged local state (unchanged logic)
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

The `mergeCoordinator` default `MergeCoordinator(dao)` means existing `SyncCoordinatorTest` tests continue to work without passing a merge coordinator — when download returns null (as the existing `FakeDriveGateway` does), the merge step is skipped entirely.

---

## Error Handling

- Malformed ISO strings in the snapshot → `runCatching` skips the entry; sync continues
- `download()` returns `null` (no file on Drive yet, or network failure) → merge is skipped; sync proceeds with upload-only
- `dao.upsertItem/Attachment/HistoryEntry` failure during merge → not wrapped; propagates to `SyncWorker` which returns `Result.retry()` on `IOException`

---

## Testing

### Unit tests — `MergeMappersTest`
- `MetadataQueueItem.toEntity()` maps all fields; `syncState` is `SYNCED`
- Null optional fields (`dueDate`, `completedAt`, `dismissedAt`) → null in entity
- Malformed `updatedAt` string throws `DateTimeParseException` (caller's `runCatching` is responsible for skipping)

### Unit tests — `MergeCoordinatorTest`
Uses `FakeCoordinatorDao` (the same fake from `SyncCoordinatorTest` but extended):
- New item in snapshot → upserted locally
- Snapshot item newer than local → local overwritten
- Local item newer than snapshot → local preserved
- Items with same `updatedAt` → local preserved (not re-upserted)
- New attachment in snapshot → upserted
- History entry not in local → inserted
- History entry already in local → skipped (not overwritten)
- Malformed ISO string in snapshot item → entry skipped, other items still processed

### Unit tests — `SyncCoordinatorTest` (extend existing)
- `syncManager.download()` called before `upload()` (verify call order via `FakeDriveGateway`)
- When download returns non-null, `mergeCoordinator.merge()` is called
- When download returns null, merge is skipped and upload proceeds
