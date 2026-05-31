# Item Editing — Design Spec

**Date:** 2026-05-30
**Feature:** Allow users to edit title, description, priority, and due date of existing queue items
**Status:** Approved

---

## Context

Items can currently be created, status-changed, and dismissed — but not edited. The four user-facing fields (`title`, `description`, `priority`, `dueDate`) are frozen after creation. `QueueDao.upsertItem` already exists; a targeted `updateItemFields` SQL query is all the persistence layer needs. `CreateItemScreen` provides the exact form layout to reuse.

`QueueItemDetailUi` also currently omits `priorityLabel`, which means priority is invisible to the user on the detail screen — this is fixed as part of this feature.

---

## Scope

**In scope:**
- Edit `title`, `description`, `priority`, `dueDate`
- `priorityLabel` added to `QueueItemDetailUi` and displayed in `ItemDetailScreen`
- `EDIT` history entry written on save
- Unit tests for repository + ViewModel
- Instrumented tests for `ItemDetailScreen`

**Out of scope:**
- Editing `tags`, `status`, or `driveId`
- Edit history diff (only "Edited" message, not per-field change tracking)
- Bulk editing

---

## Architecture

### Data layer

**`QueueDao.kt`** — new targeted UPDATE (mirrors the `updateStatus` pattern):

```kotlin
@Query("""
    UPDATE queue_items
    SET title       = :title,
        description = :description,
        priority    = :priority,
        dueDate     = :dueDate,
        updatedAt   = :updatedAt,
        syncState   = 'PENDING_SYNC'
    WHERE id = :id
""")
suspend fun updateItemFields(
    id: String,
    title: String,
    description: String?,
    priority: String?,
    dueDate: LocalDate?,
    updatedAt: Instant
): Int
```

**`QueueRepository.kt`** — new interface method:

```kotlin
suspend fun updateItem(
    id: String,
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem?
```

**`RoomQueueRepository.kt`** — implementation:

```kotlin
override suspend fun updateItem(
    id: String,
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem? {
    val now = clock.now()
    val updatedRows = dao.updateItemFields(
        id          = id,
        title       = title.trim(),
        description = description?.trim()?.takeIf { it.isNotEmpty() },
        priority    = priority?.name,
        dueDate     = dueDate,
        updatedAt   = now
    )
    if (updatedRows == 0) return null

    runCatching {
        dao.upsertHistoryEntry(
            HistoryEntryEntity(
                id          = idProvider(),
                queueItemId = id,
                message     = "Edited",
                kind        = HistoryKind.EDIT.name,
                createdAt   = now
            )
        )
    }.onFailure { e -> Log.w(TAG, "Failed to write history entry", e) }

    return dao.observeItem(id).first()?.toDomain()
}
```

Fields are trimmed and normalized identically to `createItem`.

### ViewModel layer

**`QueueItemDetailUi`** — add `priorityLabel: String?`:

```kotlin
data class QueueItemDetailUi(
    val id: String,
    val title: String,
    val description: String?,
    val priorityLabel: String?,
    val dueDateLabel: String?,
    val attachments: List<String>,
    val history: List<String>,
    val syncIndicator: SyncIndicator?
)
```

`toDetailUi` updated:
```kotlin
private fun QueueItem.toDetailUi(attachments: List<String>, history: List<String>) = QueueItemDetailUi(
    id            = id,
    title         = title,
    description   = description,
    priorityLabel = priority?.name,
    dueDateLabel  = dueDate?.toString(),
    attachments   = attachments,
    history       = history,
    syncIndicator = syncState.toIndicator()
)
```

**New SavedStateHandle key and StateFlow:**

```kotlin
private const val KEY_IS_EDITING_ITEM = "isEditingItem"

val isEditingItem: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_EDITING_ITEM, false)
```

**New actions:**

```kotlin
fun startEdit() {
    savedStateHandle[KEY_IS_EDITING_ITEM]     = true
    savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE]  = false
    savedStateHandle[KEY_IS_CREATING_ITEM]    = false
}

fun cancelEdit() {
    savedStateHandle[KEY_IS_EDITING_ITEM] = false
}

fun saveEdit(title: String, description: String?, priority: String?, dueDate: String?) {
    val id = selectedItemId.value ?: return
    viewModelScope.launch {
        repository.updateItem(
            id          = id,
            title       = title,
            description = description,
            priority    = priority.toPriorityOrNull(),
            dueDate     = dueDate.toLocalDateOrNull()
        )
        savedStateHandle[KEY_IS_EDITING_ITEM] = false
    }
}
```

`toPriorityOrNull()` and `toLocalDateOrNull()` are existing private extension functions already used by `createItem`.

### UI layer

**`EditItemScreen.kt`** *(new)* — identical form to `CreateItemScreen`, pre-populated:

```kotlin
@Composable
fun EditItemScreen(
    initialTitle: String,
    initialDescription: String,
    initialPriority: String,
    initialDueDate: String,
    onSave: (title: String, description: String?, priority: String?, dueDate: String?) -> Unit,
    onCancel: () -> Unit
)
```

- Headline: "Edit item"
- Fields start with `rememberSaveable { mutableStateOf(initialX) }`
- Save button enabled when `title.isNotBlank()`
- Same trim/takeUnless logic as `CreateItemScreen`

**`ItemDetailScreen.kt`** changes:
- Add `priorityLabel: String? = null` parameter — displayed as `Text` below `description`, above `dueDateLabel`, only when non-null
- Add `onEdit: () -> Unit = {}` parameter
- Add `TextButton("Edit", onClick = onEdit)` in the same `Row` as "Back"

```kotlin
// Row at top of screen
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
) {
    TextButton(onClick = onBack) { Text("Back") }
    TextButton(onClick = onEdit) { Text("Edit") }
}
```

**`QueMApp.kt`** — new branch and wiring:

```kotlin
val isEditingItem by viewModel.isEditingItem.collectAsStateWithLifecycle()

// Navigation chain:
} else if (isEditingItem) {
    val item = selectedItem ?: return
    EditItemScreen(
        initialTitle       = item.title,
        initialDescription = item.description ?: "",
        initialPriority    = item.priorityLabel ?: "",
        initialDueDate     = item.dueDateLabel ?: "",
        onSave             = viewModel::saveEdit,
        onCancel           = viewModel::cancelEdit
    )
}

// QueueListScreen and ItemDetailScreen now pass onEdit = viewModel::startEdit:
ItemDetailScreen(
    ...
    onEdit = viewModel::startEdit
)
```

---

## Error Handling

- `updateItem` returns `null` if the item doesn't exist — `saveEdit` ignores null (item already navigated away)
- History write failures are silent (`runCatching` + `Log.w`), same as all other history writes
- `title.isBlank()` is prevented by the Save button `enabled = title.isNotBlank()` guard

---

## Testing

### Unit tests — `RoomQueueRepositoryTest`
- `updateItem` patches exactly title/description/priority/dueDate; leaves status, createdAt, completedAt unchanged
- `updateItem` returns `null` when item does not exist
- `updateItem` writes an EDIT history entry with message "Edited"
- `updateItem` does not propagate history write failure to caller

### Unit tests — `QueueViewModelTest`
- `selectedItem.priorityLabel` reflects item priority (`"HIGH"`, `null`)
- `saveEdit` calls `repository.updateItem` with correct trimmed args
- After `saveEdit`, `isEditingItem` becomes `false`

### Instrumented tests — `ItemDetailScreenTest`
- Priority label visible when `priorityLabel` is non-null
- Edit button calls `onEdit` callback

---

## What Comes Next

1. **Download + merge** — pull the Drive snapshot and reconcile with local data
