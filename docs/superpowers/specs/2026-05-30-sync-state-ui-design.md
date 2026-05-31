# Sync State UI — Design Spec

**Date:** 2026-05-30
**Feature:** Surface `SyncState` as a visual indicator on queue list items and the item detail screen
**Status:** Approved

---

## Context

`SyncState` (`SYNCED`, `PENDING_SYNC`, `SYNCING`, `ERROR`) is already on `QueueItem` and `Attachment`, and is written by `RoomQueueRepository` and `SyncCoordinator`. Nothing surfaces it to the user today — the UI always shows items the same way regardless of sync status.

The gap is purely presentational: the ViewModel doesn't pass sync state to the UI models, and the composables don't render it.

---

## Scope

**In scope:**
- `SyncIndicator` enum in the ViewModel layer
- `syncIndicator: SyncIndicator?` on `QueueListItemUi` and `QueueItemDetailUi`
- Small colored dot on list items (orange/gray/red; hidden when synced)
- Dot + text label in the detail screen header area (hidden when synced)
- Unit tests for ViewModel mapping; instrumented test for rendering

**Out of scope:**
- Attachment-level sync state (only item-level for now)
- Animated syncing indicator (static dot only)
- Any change to how `SyncState` is written

---

## Design

### `SyncIndicator` enum

Placed at the top of `QueueViewModel.kt` alongside the other UI model types:

```kotlin
enum class SyncIndicator { PENDING, SYNCING, ERROR }
```

`null` means the item is synced — no indicator shown. This keeps the composables simple: they check `syncIndicator != null` to decide whether to render anything.

### Mapping from domain `SyncState`

Top-level private function in `QueueViewModel.kt`:

```kotlin
private fun SyncState.toIndicator(): SyncIndicator? = when (this) {
    SyncState.SYNCED       -> null
    SyncState.PENDING_SYNC -> SyncIndicator.PENDING
    SyncState.SYNCING      -> SyncIndicator.SYNCING
    SyncState.ERROR        -> SyncIndicator.ERROR
}
```

### Updated UI models

**`QueueListItemUi`** (in `QueueListScreen.kt`):
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

**`QueueItemDetailUi`** (in `QueueViewModel.kt`):
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

### ViewModel mapping changes

Both mapping functions are private extensions in `QueueViewModel.kt`. Each gains one new line.

`toListItemUi` — add `syncIndicator`:
```kotlin
private fun QueueItem.toListItemUi(attachmentCount: Int) = QueueListItemUi(
    id                = id,
    title             = title,
    priorityLabel     = priority?.name,
    dueDateLabel      = dueDate?.toString(),
    attachmentSummary = attachmentCount.toAttachmentSummary(),
    syncIndicator     = syncState.toIndicator()          // new
)
```

`toDetailUi` — add `syncIndicator`:
```kotlin
private fun QueueItem.toDetailUi(
    attachments: List<String>,
    history: List<String>
) = QueueItemDetailUi(
    id            = id,
    title         = title,
    description   = description,
    dueDateLabel  = dueDate?.toString(),
    attachments   = attachments,
    history       = history,
    syncIndicator = syncState.toIndicator()
)
```

### List item rendering (`QueueListScreen.kt`)

Wrap the existing list card content in a `Box` and overlay a dot in the top-right corner when `syncIndicator != null`:

```kotlin
Box {
    // existing card content
    syncIndicator?.let { indicator ->
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(9.dp)
                .background(indicator.toColor(), CircleShape)
        )
    }
}
```

Dot colors:
```kotlin
private fun SyncIndicator.toColor(): Color = when (this) {
    SyncIndicator.PENDING -> Color(0xFFF57C00)  // orange
    SyncIndicator.SYNCING -> Color(0xFF9E9E9E)  // gray
    SyncIndicator.ERROR   -> Color(0xFFD32F2F)  // red
}
```

### Detail screen rendering (`ItemDetailScreen.kt`)

Add a `syncIndicator: SyncIndicator?` parameter (default `null`). When non-null, insert a `Row` with a dot and text label between the due-date line and the action buttons:

```kotlin
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
```

Label text:
```kotlin
private fun SyncIndicator.toLabel(): String = when (this) {
    SyncIndicator.PENDING -> "Pending sync"
    SyncIndicator.SYNCING -> "Syncing…"
    SyncIndicator.ERROR   -> "Sync error"
}
```

`toColor()` is the same function as in the list screen — defined privately in each file.

---

## Error Handling

No error paths — this is a pure read-only display feature. If `syncState` has an unexpected value that doesn't map (impossible with the current enum), the `when` is exhaustive and the compiler enforces coverage.

---

## Testing

### Unit tests — `QueueViewModelTest`
- `PENDING_SYNC` item → `selectedItem.syncIndicator == SyncIndicator.PENDING`
- `SYNCED` item → `selectedItem.syncIndicator == null`
- `ERROR` item → `selectedItem.syncIndicator == SyncIndicator.ERROR`
- List items: same three cases via `items` StateFlow

### Instrumented tests — `ItemDetailScreenTest`
- Pass `syncIndicator = SyncIndicator.PENDING` → "Pending sync" label is visible
- Pass `syncIndicator = null` → no sync label visible

---

## What Comes Next

1. **Archive search screen** — `searchArchive()` is implemented; needs a new screen and nav entry point
2. **Item editing** — no `updateItem` exists; users can't modify title/description/priority/due date
3. **Download + merge** — pull the Drive snapshot and reconcile with local data
