# Archive Search Screen — Design Spec

**Date:** 2026-05-30
**Feature:** New screen to browse and search DONE/DISMISSED items
**Status:** Approved

---

## Context

`QueueRepository.searchArchive(query)` is already implemented and tested — it returns a `Flow<List<QueueItem>>` of DONE + DISMISSED items whose title or description contains the query string. An empty/blank query returns all archived items. The gap is entirely presentational: no screen exists to call it, and there is no navigation entry point.

---

## Scope

**In scope:**
- `ArchiveSearchScreen` composable
- `QueueViewModel` additions: `isShowingArchive`, `archiveQuery`, `archiveResults`, 4 actions
- "Archive" button in `QueueListScreen` header
- Navigation wiring in `QueMApp.kt`
- Unit tests for ViewModel query → results mapping
- Instrumented tests for screen behavior

**Out of scope:**
- Pagination
- Sorting options
- Edit or restore actions from archive (read-only for now)

---

## Architecture

### Navigation

`QueMApp.kt` uses `if/else` state-based navigation. One new branch added:

```
isShowingSettings  → SettingsScreen
isCreatingItem     → CreateItemScreen
isShowingArchive   → ArchiveSearchScreen   ← new
selectedItem!=null → ItemDetailScreen
else               → QueueListScreen
```

Tapping an archive result calls `selectArchiveItem(id)`, which closes the archive screen and sets `selectedItemId` in one atomic update. The user then sees `ItemDetailScreen`. "Back" from `ItemDetailScreen` returns to the main queue list (not the archive) — no back-stack needed.

### ViewModel additions (`QueueViewModel.kt`)

New `SavedStateHandle` keys:

```kotlin
private const val KEY_IS_SHOWING_ARCHIVE = "isShowingArchive"
private const val KEY_ARCHIVE_QUERY      = "archiveQuery"
```

New `StateFlow`s:

```kotlin
val isShowingArchive: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_SHOWING_ARCHIVE, false)

val archiveQuery: StateFlow<String> =
    savedStateHandle.getStateFlow(KEY_ARCHIVE_QUERY, "")

val archiveResults: StateFlow<List<QueueListItemUi>> =
    archiveQuery
        .flatMapLatest { query -> repository.searchArchive(query) }
        .flatMapLatest { items ->
            if (items.isEmpty()) flowOf(emptyList())
            else combine(
                items.map { item ->
                    repository.observeAttachments(item.id).map { attachments ->
                        item.toListItemUi(attachmentCount = attachments.size)
                    }
                }
            ) { results -> results.toList() }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = emptyList()
        )
```

New actions:

```kotlin
fun showArchive() {
    savedStateHandle[KEY_ARCHIVE_QUERY]       = ""
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE]  = true
    savedStateHandle[KEY_IS_SHOWING_SETTINGS] = false
    savedStateHandle[KEY_SELECTED_ITEM_ID]    = null
    savedStateHandle[KEY_IS_CREATING_ITEM]    = false
}

fun closeArchive() {
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE] = false
}

fun selectArchiveItem(id: String) {
    savedStateHandle[KEY_IS_SHOWING_ARCHIVE] = false
    savedStateHandle[KEY_SELECTED_ITEM_ID]   = id
}

fun setArchiveQuery(query: String) {
    savedStateHandle[KEY_ARCHIVE_QUERY] = query
}
```

### ArchiveSearchScreen composable (`ArchiveSearchScreen.kt`)

```kotlin
@Composable
fun ArchiveSearchScreen(
    query: String,
    results: List<QueueListItemUi>,
    onQueryChange: (String) -> Unit,
    onItemSelected: (String) -> Unit,
    onBack: () -> Unit
)
```

Layout (top to bottom inside a `Column`):

1. `TextButton("Back", onClick = onBack)` — matches ItemDetailScreen pattern
2. `Text("Archive")` — `headlineMedium`, `SemiBold`
3. `OutlinedTextField` — `value = query`, `onValueChange = onQueryChange`, `singleLine = true`, placeholder "Search…", `keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)`
4. `LazyColumn` of `QueueListItemCard` items — reuses `QueueListItemCard` from `QueueListScreen.kt`
5. Empty state text (via `DetailEmptyText`-style helper):
   - `results.isEmpty() && query.isBlank()` → "No archived items"
   - `results.isEmpty() && query.isNotBlank()` → `No results for "$query"`

`QueueListItemCard` is currently `private` in `QueueListScreen.kt` — promote to `internal` so `ArchiveSearchScreen.kt` can reuse it without duplication.

### QueueListScreen changes

Add an "Archive" `OutlinedButton` to the header row, between "Settings" and "New":

```kotlin
OutlinedButton(onClick = onOpenSettings) { Text("Settings") }
OutlinedButton(onClick = onOpenArchive) { Text("Archive") }  // new
Button(onClick = onCreateItem) { Text("New") }
```

`onOpenArchive: () -> Unit = {}` added as a parameter with a default so existing call sites (tests) don't break.

---

## Error Handling

`searchArchive` is a Room Flow — it never throws; empty results are returned as `emptyList()`. No error state needed.

---

## Testing

### Unit tests — `QueueViewModelTest`

- `archiveResultsShowAllItemsWhenQueryIsBlank` — insert DONE + DISMISSED items, blank query → both appear in `archiveResults`
- `archiveResultsFilterByTitle` — query "contract" → only items whose title/description contains "contract"

`FakeQueueRepository.searchArchive(query)` currently returns `flowOf(emptyList())` — update to filter by title/description containing the trimmed query (case-insensitive), consistent with the real implementation.

### Instrumented tests — `ArchiveSearchScreenTest`

- Screen renders search field and "Back" button
- Typing in the search field calls `onQueryChange`
- Results list shows when `results` is non-empty
- Empty state "No archived items" shows when `results` is empty and `query` is blank
- Tapping a result calls `onItemSelected` with the correct id

---

## What Comes Next

1. **Item editing** — no `updateItem` exists; users can't modify title/description/priority/due date
2. **Download + merge** — pull the Drive snapshot and reconcile with local data
