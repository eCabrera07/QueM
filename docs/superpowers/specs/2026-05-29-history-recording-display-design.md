# History Recording & Display — Design Spec

**Date:** 2026-05-29  
**Feature:** Record history entries for item lifecycle events; display them in ItemDetailScreen  
**Status:** Approved

---

## Context

The data layer for history is fully in place:
- `HistoryEntry` domain model (`id`, `queueItemId`, `message`, `kind`, `createdAt`)
- `HistoryKind` enum: `NOTE`, `STATUS_CHANGE`, `ATTACHMENT_ADDED`, `ATTACHMENT_REMOVED`, `EDIT`
- `HistoryEntryEntity` Room entity with `CASCADE` delete on parent `QueueItem`
- `QueueDao.upsertHistoryEntry(entry)` and `QueueDao.observeHistory(queueItemId)` both implemented
- `ItemDetailScreen` already renders `history: List<String>` (shows "No history" when empty)

The gap is the repository (never writes entries) and the ViewModel (never reads them, hardcodes `emptyList()`).

---

## Scope

**In scope:**
- `QueueRepository` interface: add `observeHistory`
- `RoomQueueRepository`: implement `observeHistory` + write entries on 5 mutating operations
- `QueueViewModel`: 3-way combine item + attachments + history; format for display

**Out of scope:**
- Manual history notes (NOTE kind)
- Attachment removal history (ATTACHMENT_REMOVED kind)
- Edit history (EDIT kind) — no item editing exists yet
- A dedicated history screen or pagination

---

## Events to Record

| Trigger | `HistoryKind` | Message |
|---|---|---|
| `createItem` | `STATUS_CHANGE` | `"Created"` |
| `changeStatus(QUEUED)` | `STATUS_CHANGE` | `"Moved back to Queued"` |
| `changeStatus(IN_PROGRESS)` | `STATUS_CHANGE` | `"Moved to In Progress"` |
| `changeStatus(DONE)` | `STATUS_CHANGE` | `"Marked as Done"` |
| `changeStatus(DISMISSED)` | `STATUS_CHANGE` | `"Dismissed"` |
| `addTextAttachment` | `ATTACHMENT_ADDED` | `"Attachment added: {title}"` |
| `addLinkAttachment` | `ATTACHMENT_ADDED` | `"Attachment added: {title}"` |
| `addDriveAttachment` | `ATTACHMENT_ADDED` | `"Attachment added: {title}"` |

History entries are ordered newest-first (`ORDER BY createdAt DESC` already in the DAO).

---

## Architecture

### Layer 1: `QueueRepository` interface

Add one method:

```kotlin
fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>>
```

### Layer 2: `RoomQueueRepository`

**`observeHistory` implementation:**
```kotlin
override fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>> =
    dao.observeHistory(queueItemId).map { entries -> entries.map { it.toDomain() } }
```

**`HistoryEntryEntity.toDomain()`** extension — new mapping function:
```kotlin
fun HistoryEntryEntity.toDomain() = HistoryEntry(
    id = id,
    queueItemId = queueItemId,
    message = message,
    kind = HistoryKind.valueOf(kind),
    createdAt = createdAt
)
```

**Writing entries** — after each mutating call succeeds, insert a `HistoryEntryEntity`:

- `createItem`: after `dao.upsertItem(...)` write history with `kind = STATUS_CHANGE`, `message = "Created"`
- `changeStatus`: after `dao.updateStatus(...)` returns non-zero, write history with the status-specific message above
- `addAttachment` (private helper): after `dao.upsertAttachment(...)` write history with `kind = ATTACHMENT_ADDED`, `message = "Attachment added: $displayName"`

All entries use `id = idProvider()` and `createdAt = now` (already available in each method).

### Layer 3: `QueueViewModel`

**`selectedItem` — 3-way combine:**

```kotlin
val selectedItem: StateFlow<QueueItemDetailUi?> =
    selectedItemId.flatMapLatest { id ->
        if (id == null) flowOf(null)
        else combine(
            repository.observeItem(id),
            repository.observeAttachments(id),
            repository.observeHistory(id)
        ) { item, attachments, history ->
            item?.toDetailUi(
                attachments = attachments.map { it.displayName },
                history = history.map { it.toDisplayString(now = clock.now()) }
            )
        }
    }.stateIn(...)
```

**`HistoryEntry.toDisplayString(now: Instant): String`** — pure top-level function:

```kotlin
fun HistoryEntry.toDisplayString(now: Instant): String {
    val elapsed = Duration.between(createdAt, now)
    val timeLabel = when {
        elapsed.seconds < 60 -> "just now"
        elapsed.toMinutes() < 60 -> "${elapsed.toMinutes()} minutes ago"
        elapsed.toHours() < 24 -> "${elapsed.toHours()} hours ago"
        else -> "${elapsed.toDays()} days ago"
    }
    return "$timeLabel · $message"
}
```

Placed in `QueueViewModel.kt` alongside the other private mapping functions.

**`toDetailUi` signature change:**
```kotlin
private fun QueueItem.toDetailUi(
    attachments: List<String>,
    history: List<String>
) = QueueItemDetailUi(id, title, description, dueDateLabel, attachments, history)
```

**`QueueViewModel` receives `clock`** — the ViewModel already has no clock reference; it must be injected so `toDisplayString` can snapshot `now` at render time. Add `clock: Clock` to the ViewModel constructor (with a `SystemClock()` default) and thread it into `factory()`.

---

## Error Handling

- History write failures are silent: if `upsertHistoryEntry` throws, it must not prevent the primary operation from completing. Wrap history writes in `runCatching { ... }` in the repository.
- If `HistoryKind.valueOf(kind)` encounters an unknown string (e.g., from a future schema migration), it throws. Protect with `runCatching { ... }.getOrElse { HistoryKind.NOTE }` in `toDomain()`.

---

## Testing

### Unit tests — `RoomQueueRepositoryTest`
- `createItem` produces a STATUS_CHANGE history entry with message "Created"
- `changeStatus(DONE)` produces a STATUS_CHANGE history entry with message "Marked as Done"
- `addTextAttachment` produces an ATTACHMENT_ADDED entry with message "Attachment added: {title}"
- History write failure (DAO throws) does not propagate to caller

### Unit tests — `QueueViewModelTest`
- `selectedItem` history list is empty when no entries exist
- `selectedItem` history list contains formatted strings once entries are present
- Relative time formatting: < 60s → "just now", minutes, hours, days

### Instrumented test — `ItemDetailScreenTest` (existing)
- Update to pass non-empty history list; verify entries render

---

## Roadmap — What Comes Next

After history is complete, the recommended build order:

1. **SyncWorker implementation** — The worker shell exists and is already scheduled. This is the core value proposition: upload item metadata to Drive, download changes. Blocked on nothing.
2. **Manual sync trigger** — Wire `onManualSync` in `QueMApp.kt` (currently `{}`) to enqueue a one-shot WorkManager request. ~30-minute task once SyncWorker exists.
3. **Per-item sync state UI** — `SyncState` enum already on `QueueItem`/`Attachment`; surface it in `ItemDetailScreen` (a small chip or icon). Unblocks user feedback on sync progress.
4. **Archive search screen** — `searchArchive()` is implemented and tested in the repository. Needs a new screen + navigation entry point.
5. **Item editing** — No `updateItem` exists; users can't modify title/description/priority/due date after creation.
