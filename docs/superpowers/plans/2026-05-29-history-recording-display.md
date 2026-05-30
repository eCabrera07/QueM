# History Recording & Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Record history entries for item lifecycle events (create, status change, attachment added) and display them as formatted relative-time strings in ItemDetailScreen.

**Architecture:** The data layer is already complete (HistoryEntryEntity, QueueDao.upsertHistoryEntry, QueueDao.observeHistory). Work is in three layers: (1) add the mapper + interface method, (2) write entries in RoomQueueRepository after each mutation, (3) thread history into QueueViewModel's 3-way combine and format for display.

**Tech Stack:** Kotlin, Room (already wired), kotlinx.coroutines Flow, java.time.Duration (relative time formatting), JUnit4 + kotlinx-coroutines-test (unit tests)

---

## File Map

| File | Change |
|---|---|
| `app/src/main/java/com/quem/data/local/LocalMappers.kt` | Add `HistoryEntryEntity.toDomain()` |
| `app/src/main/java/com/quem/data/repository/QueueRepository.kt` | Add `observeHistory` to interface |
| `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt` | Implement `observeHistory`; write entries on createItem, changeStatus, addAttachment |
| `app/src/main/java/com/quem/ui/QueueViewModel.kt` | Inject `clock`; 3-way combine; `toDisplayString` formatter; update `toDetailUi` |
| `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt` | Upgrade `FakeQueueDao` to store history; add 4 new tests |
| `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` | Add `observeHistory` to `FakeQueueRepository`; add 3 new tests |

---

## Task 1: HistoryEntry mapper + QueueRepository interface

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/LocalMappers.kt`
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` (stub for compile)

- [ ] **Step 1: Add `HistoryEntryEntity.toDomain()` to LocalMappers.kt**

Open `app/src/main/java/com/quem/data/local/LocalMappers.kt`. Add these imports at the top if not present:
```kotlin
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
```
Append at the end of the file (after the existing `Attachment.toEntity()` function):
```kotlin
fun HistoryEntryEntity.toDomain(): HistoryEntry = HistoryEntry(
    id = id,
    queueItemId = queueItemId,
    message = message,
    kind = runCatching { HistoryKind.valueOf(kind) }.getOrElse { HistoryKind.NOTE },
    createdAt = createdAt
)
```

- [ ] **Step 2: Add `observeHistory` to QueueRepository interface**

Open `app/src/main/java/com/quem/data/repository/QueueRepository.kt`. Add this import:
```kotlin
import com.quem.core.model.HistoryEntry
```
Add this method to the `QueueRepository` interface (after `observeAttachments`):
```kotlin
fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>>
```

- [ ] **Step 3: Add stub `observeHistory` to FakeQueueRepository in QueueViewModelTest.kt to fix compile**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`. Add this import:
```kotlin
import com.quem.core.model.HistoryEntry
```
In the `FakeQueueRepository` class (after the `addDriveAttachment` override), add:
```kotlin
override fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>> =
    flowOf(emptyList())
```

- [ ] **Step 4: Commit**
```bash
git add app/src/main/java/com/quem/data/local/LocalMappers.kt
git add app/src/main/java/com/quem/data/repository/QueueRepository.kt
git add app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: add HistoryEntry mapper and observeHistory to QueueRepository interface"
```

---

## Task 2: RoomQueueRepository.observeHistory + FakeQueueDao history storage

**Files:**
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Open `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`.

First, update `FakeQueueDao` to actually store and return history entries. Find the `FakeQueueDao` class. Add a new `MutableStateFlow` field and update the two history methods:

```kotlin
private class FakeQueueDao : QueueDao {
    private val entities = MutableStateFlow<List<QueueItemEntity>>(emptyList())
    private val attachmentEntities = MutableStateFlow<List<AttachmentEntity>>(emptyList())
    private val historyEntities = MutableStateFlow<List<HistoryEntryEntity>>(emptyList())  // ADD THIS

    // ... existing val items, existing overrides unchanged ...

    override suspend fun upsertHistoryEntry(entry: HistoryEntryEntity) {  // REPLACE the no-op
        historyEntities.value = historyEntities.value.filterNot { it.id == entry.id } + entry
    }

    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntryEntity>> =  // REPLACE
        historyEntities.map { entries ->
            entries.filter { it.queueItemId == queueItemId }.sortedByDescending { it.createdAt }
        }
}
```

Now add this test at the end of `RoomQueueRepositoryTest`:
```kotlin
@Test
fun observeHistoryReturnsEmptyListWhenNoEntriesExist() = runTest {
    val dao = FakeQueueDao()
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z")),
        idProvider = { "item-1" }
    )
    dao.upsertItem(queueItemEntity(id = "item-1", now = Instant.parse("2026-05-23T12:00:00Z")))

    val history = repository.observeHistory("item-1").first()

    assertEquals(emptyList<HistoryEntry>(), history)
}
```

Add the missing import at the top of the file:
```kotlin
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.observeHistoryReturnsEmptyListWhenNoEntriesExist"`

Expected: FAIL — `observeHistory` not yet defined on `RoomQueueRepository`.

- [ ] **Step 3: Implement `observeHistory` in RoomQueueRepository**

Open `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`. Add these imports:
```kotlin
import com.quem.core.model.HistoryEntry
import com.quem.data.local.toDomain
```
Add this method to `RoomQueueRepository` (after `observeAttachments`):
```kotlin
override fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>> =
    dao.observeHistory(queueItemId).map { entries -> entries.map { it.toDomain() } }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.observeHistoryReturnsEmptyListWhenNoEntriesExist"`

Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt
git add app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: implement RoomQueueRepository.observeHistory"
```

---

## Task 3: History write on createItem

**Files:**
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `RoomQueueRepositoryTest`:
```kotlin
@Test
fun createItemWritesCreatedHistoryEntry() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1")
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { ids.removeFirst() }
    )

    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)

    val history = repository.observeHistory("item-1").first()
    assertEquals(1, history.size)
    assertEquals(HistoryKind.STATUS_CHANGE, history.single().kind)
    assertEquals("Created", history.single().message)
    assertEquals(now, history.single().createdAt)
    assertEquals("item-1", history.single().queueItemId)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.createItemWritesCreatedHistoryEntry"`

Expected: FAIL — `history` is empty, history entry not written.

- [ ] **Step 3: Add the history write in createItem**

Open `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`. Add this import:
```kotlin
import com.quem.data.local.HistoryEntryEntity
import com.quem.core.model.HistoryKind
```

In `createItem`, after `dao.upsertItem(item.toEntity())`, add:
```kotlin
runCatching {
    dao.upsertHistoryEntry(
        HistoryEntryEntity(
            id = idProvider(),
            queueItemId = item.id,
            message = "Created",
            kind = HistoryKind.STATUS_CHANGE.name,
            createdAt = now
        )
    )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.createItemWritesCreatedHistoryEntry"`

Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt
git add app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: write Created history entry on createItem"
```

---

## Task 4: History writes on changeStatus

**Files:**
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

- [ ] **Step 1: Write the failing tests**

Add these two tests to `RoomQueueRepositoryTest`:
```kotlin
@Test
fun changeStatusDoneWritesHistoryEntry() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-create", "history-done")
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)

    repository.changeStatus("item-1", QueueStatus.DONE)

    val history = repository.observeHistory("item-1").first()
    val statusEntry = history.first() // newest first
    assertEquals(HistoryKind.STATUS_CHANGE, statusEntry.kind)
    assertEquals("Marked as Done", statusEntry.message)
}

@Test
fun changeStatusNoOpDoesNotWriteHistoryEntry() = runTest {
    val dao = FakeQueueDao()
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { "unused" }
    )

    repository.changeStatus("missing", QueueStatus.DONE)

    assertEquals(emptyList<HistoryEntry>(), repository.observeHistory("missing").first())
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.changeStatusDoneWritesHistoryEntry"`

Expected: FAIL — no history entry written yet.

- [ ] **Step 3: Add the history write in changeStatus**

In `RoomQueueRepository.changeStatus`, after `if (updatedRows == 0) return null`, add the history write before the return statement:

```kotlin
override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? {
    val now = clock.now()
    val updatedRows = dao.updateStatus(
        id = id,
        status = status.name,
        updatedAt = now,
        completedAt = if (status == QueueStatus.DONE) now else null,
        dismissedAt = if (status == QueueStatus.DISMISSED) now else null
    )
    if (updatedRows == 0) return null

    val message = when (status) {
        QueueStatus.QUEUED -> "Moved back to Queued"
        QueueStatus.IN_PROGRESS -> "Moved to In Progress"
        QueueStatus.DONE -> "Marked as Done"
        QueueStatus.DISMISSED -> "Dismissed"
    }
    runCatching {
        dao.upsertHistoryEntry(
            HistoryEntryEntity(
                id = idProvider(),
                queueItemId = id,
                message = message,
                kind = HistoryKind.STATUS_CHANGE.name,
                createdAt = now
            )
        )
    }

    return dao.observeItem(id).first()?.toDomain()
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.changeStatusDoneWritesHistoryEntry"` and
`./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.changeStatusNoOpDoesNotWriteHistoryEntry"`

Expected: both PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt
git add app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: write status-change history entries on changeStatus"
```

---

## Task 5: History write on addAttachment

**Files:**
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

- [ ] **Step 1: Write the failing test**

Add this test to `RoomQueueRepositoryTest`:
```kotlin
@Test
fun addTextAttachmentWritesAttachmentAddedHistoryEntry() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-create", "attachment-1", "history-attachment")
    val now = Instant.parse("2026-05-23T12:00:00Z")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(now),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)

    repository.addTextAttachment("item-1", " My Note ", "Remember this")

    val history = repository.observeHistory("item-1").first()
    val attachmentEntry = history.first() // newest first
    assertEquals(HistoryKind.ATTACHMENT_ADDED, attachmentEntry.kind)
    assertEquals("Attachment added: My Note", attachmentEntry.message)
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.addTextAttachmentWritesAttachmentAddedHistoryEntry"`

Expected: FAIL — no history entry written.

- [ ] **Step 3: Add the history write in the private addAttachment helper**

In `RoomQueueRepository`, at the end of the private `addAttachment` method (after `dao.upsertAttachment(attachment.toEntity())`), add:
```kotlin
runCatching {
    dao.upsertHistoryEntry(
        HistoryEntryEntity(
            id = idProvider(),
            queueItemId = queueItemId,
            message = "Attachment added: $displayName",
            kind = HistoryKind.ATTACHMENT_ADDED.name,
            createdAt = now
        )
    )
}
```

Note: `displayName` is the already-trimmed local variable computed at the top of `addAttachment` (`val displayName = title.trim()`), and `now` is `val now = clock.now()` already in scope.

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest.addTextAttachmentWritesAttachmentAddedHistoryEntry"`

Expected: PASS

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt
git add app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: write attachment-added history entry on addAttachment"
```

---

## Task 6: QueueViewModel — clock injection, 3-way combine, history display

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

- [ ] **Step 1: Write the failing tests**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`.

Add these imports:
```kotlin
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
import com.quem.core.time.FixedClock
import java.time.Instant
```

Update `FakeQueueRepository` to support history. Add a field and replace the stub `observeHistory`:
```kotlin
private class FakeQueueRepository : QueueRepository {
    val items = MutableStateFlow<List<QueueItem>>(emptyList())
    private val attachments = MutableStateFlow<List<Attachment>>(emptyList())
    private val historyEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())  // ADD THIS

    // ... existing fields unchanged ...

    // REPLACE the stub observeHistory:
    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>> =
        historyEntries.map { entries -> entries.filter { it.queueItemId == queueItemId } }

    // ADD this helper for tests to inject history:
    fun emitHistory(vararg entries: HistoryEntry) {
        historyEntries.value = entries.toList()
    }
}
```

Now add these tests (at the end of `QueueViewModelTest`):
```kotlin
@Test
fun selectedItemHistoryIsEmptyWhenNoEntriesExist() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    val viewModel = QueueViewModel(
        repository = repository,
        clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z"))
    )
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertEquals(emptyList<String>(), viewModel.selectedItem.value?.history)
}

@Test
fun selectedItemHistoryShowsFormattedEntriesNewestFirst() = runTest {
    val now = Instant.parse("2026-05-23T14:00:00Z")
    val repository = FakeQueueRepository()
    repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
    repository.emitHistory(
        historyEntry(id = "h-2", queueItemId = "item-1", message = "Marked as Done",
            createdAt = Instant.parse("2026-05-23T12:00:00Z")),   // 2 hours ago
        historyEntry(id = "h-1", queueItemId = "item-1", message = "Created",
            createdAt = Instant.parse("2026-05-23T11:00:00Z"))    // 3 hours ago
    )
    val viewModel = QueueViewModel(
        repository = repository,
        clock = FixedClock(now)
    )
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    assertEquals(
        listOf("2 hours ago · Marked as Done", "3 hours ago · Created"),
        viewModel.selectedItem.value?.history
    )
}

@Test
fun historyToDisplayStringFormatsRelativeTime() {
    val now = Instant.parse("2026-05-23T12:00:00Z")

    val justNow = historyEntry(createdAt = now.minusSeconds(30))
    assertEquals("just now · Created", justNow.toDisplayString(now))

    val minutesAgo = historyEntry(createdAt = now.minusSeconds(5 * 60))
    assertEquals("5 minutes ago · Created", minutesAgo.toDisplayString(now))

    val oneMinuteAgo = historyEntry(createdAt = now.minusSeconds(60))
    assertEquals("1 minute ago · Created", oneMinuteAgo.toDisplayString(now))

    val hoursAgo = historyEntry(createdAt = now.minusSeconds(3 * 3600))
    assertEquals("3 hours ago · Created", hoursAgo.toDisplayString(now))

    val oneHourAgo = historyEntry(createdAt = now.minusSeconds(3600))
    assertEquals("1 hour ago · Created", oneHourAgo.toDisplayString(now))

    val daysAgo = historyEntry(createdAt = now.minusSeconds(2 * 86400))
    assertEquals("2 days ago · Created", daysAgo.toDisplayString(now))

    val oneDayAgo = historyEntry(createdAt = now.minusSeconds(86400))
    assertEquals("1 day ago · Created", oneDayAgo.toDisplayString(now))
}
```

Add this helper at the bottom of the file (alongside `queueItem`):
```kotlin
private fun historyEntry(
    id: String = "h-1",
    queueItemId: String = "item-1",
    message: String = "Created",
    kind: HistoryKind = HistoryKind.STATUS_CHANGE,
    createdAt: Instant = Instant.parse("2026-05-23T12:00:00Z")
) = HistoryEntry(
    id = id,
    queueItemId = queueItemId,
    message = message,
    kind = kind,
    createdAt = createdAt
)
```

Note: `toDisplayString` is called directly in the third test — it will be a top-level function in `QueueViewModel.kt` that is package-internal (no modifier), accessible from the test in the same module.

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest.selectedItemHistoryIsEmptyWhenNoEntriesExist"`

Expected: FAIL — `QueueViewModel` constructor doesn't accept `clock`; `observeHistory` not called.

- [ ] **Step 3: Update QueueViewModel**

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`.

Add imports:
```kotlin
import com.quem.core.model.HistoryEntry
import com.quem.core.time.Clock
import com.quem.core.time.SystemClock
import java.time.Duration
import java.time.Instant
```

Update the class constructor to add `clock`:
```kotlin
class QueueViewModel(
    private val repository: QueueRepository,
    private val driveConnectionRepository: DriveConnectionRepository = DisconnectedDriveConnectionRepository(),
    private val savedStateHandle: SavedStateHandle = SavedStateHandle(),
    private val clock: Clock = SystemClock()
) : ViewModel() {
```

Replace the `selectedItem` StateFlow with a 3-way combine:
```kotlin
val selectedItem: StateFlow<QueueItemDetailUi?> =
    selectedItemId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(null)
            } else {
                combine(
                    repository.observeItem(id),
                    repository.observeAttachments(id),
                    repository.observeHistory(id)
                ) { item, attachments, history ->
                    val now = clock.now()
                    item?.toDetailUi(
                        attachments = attachments.map { it.displayName },
                        history = history.map { it.toDisplayString(now) }
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            initialValue = null
        )
```

Update `toDetailUi` at the bottom of the file to accept `history`:
```kotlin
private fun QueueItem.toDetailUi(attachments: List<String>, history: List<String>) = QueueItemDetailUi(
    id = id,
    title = title,
    description = description,
    dueDateLabel = dueDate?.toString(),
    attachments = attachments,
    history = history
)
```

Add `toDisplayString` as a top-level function at the bottom of the file (after all private functions):
```kotlin
internal fun HistoryEntry.toDisplayString(now: Instant): String {
    val elapsed = Duration.between(createdAt, now)
    val timeLabel = when {
        elapsed.seconds < 60 -> "just now"
        elapsed.toMinutes() < 60 -> {
            val m = elapsed.toMinutes()
            if (m == 1L) "1 minute ago" else "$m minutes ago"
        }
        elapsed.toHours() < 24 -> {
            val h = elapsed.toHours()
            if (h == 1L) "1 hour ago" else "$h hours ago"
        }
        else -> {
            val d = elapsed.toDays()
            if (d == 1L) "1 day ago" else "$d days ago"
        }
    }
    return "$timeLabel · $message"
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: all tests PASS, including the 3 new ones.

- [ ] **Step 5: Commit**
```bash
git add app/src/main/java/com/quem/ui/QueueViewModel.kt
git add app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: wire history into QueueViewModel selectedItem with relative-time formatting"
```

---

## Self-Review Checklist

- [x] **Spec coverage:** mapper (Task 1) ✓, `observeHistory` interface (Task 1) ✓, history writes createItem (Task 3) ✓, changeStatus (Task 4) ✓, addAttachment (Task 5) ✓, 3-way combine + clock (Task 6) ✓, `toDisplayString` formatter (Task 6) ✓, `runCatching` on history writes (Task 3-5) ✓
- [x] **Placeholders:** none — all steps show exact code
- [x] **Type consistency:** `HistoryEntry.toDisplayString(now: Instant)` used in Task 6 matches the function defined in Task 6. `HistoryEntryEntity.toDomain()` defined in Task 1 is called in Task 2. `FakeQueueDao.historyEntities` added in Task 2 is used in Task 2's test. `FakeQueueRepository.emitHistory()` added in Task 6 is used in Task 6 tests.
- [x] **`toDetailUi` signature:** updated to `(attachments, history)` in Task 6 — the old 1-arg call site doesn't exist anymore since the only call site is inside the 3-way combine also in Task 6.
- [x] **Error handling:** all `dao.upsertHistoryEntry` calls wrapped in `runCatching` per spec.
- [x] **`internal` on `toDisplayString`:** marked `internal` so the test in the same module can call it directly without ceremony.
