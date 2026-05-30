# SyncWorker Implementation — Design Spec

**Date:** 2026-05-29
**Feature:** Implement `SyncWorker.doWork()` — upload all local data to Drive on a periodic schedule and on manual trigger
**Status:** Approved

---

## Context

The sync infrastructure is fully in place:
- `SyncWorker` shell exists and is already scheduled via `SyncScheduler.schedulePeriodic()` in `QueMApplication.onCreate()`
- `SyncManager`, `GoogleDriveGateway`, `MetadataExporter`, and `MetadataSerializer` are all fully implemented
- `google-api-client-android` is already a dependency — `GoogleAccountCredential` is available for background auth
- `SyncState` enum (`SYNCED`, `PENDING_SYNC`, `SYNCING`, `ERROR`) is on both `QueueItem` and `Attachment`; new items/attachments are created with `PENDING_SYNC`

The gaps:
- `SyncWorker.doWork()` is a no-op stub
- The account email is only stored in-memory (`GoogleDriveConnectionRepository` uses a `MutableStateFlow`); the worker needs it after the app is killed
- No bulk-read or mark-synced DAO methods exist
- No domain → export mapping functions exist
- `onManualSync = {}` in `QueMApp.kt` is unwired

---

## Scope

**In scope:**
- Persist account email to `SharedPreferences` on connect/disconnect
- Five new `QueueDao` methods for bulk-read and mark-synced
- Domain → export mapping functions (`SyncMappers.kt`)
- `SyncCoordinator` — extract sync logic for testability
- `SyncWorker.doWork()` implementation
- `SyncScheduler.scheduleOnce()` for one-shot requests
- Wire `onManualSync` in `QueMApp.kt`

**Out of scope:**
- Download + merge (pull server snapshot and reconcile with local data)
- `SyncState` UI surface (separate roadmap item)
- Conflict resolution

---

## Architecture

### New files

#### `DriveAccountPreferences` — `com.quem.drive`

Wraps `SharedPreferences`. Persists the connected Google account email across app restarts so `SyncWorker` can access it without an `Activity`.

```kotlin
class DriveAccountPreferences(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun save(email: String) = prefs.edit().putString(KEY_EMAIL, email).apply()
    fun load(): String? = prefs.getString(KEY_EMAIL, null)
    fun clear() = prefs.edit().remove(KEY_EMAIL).apply()

    private companion object {
        const val PREFS_NAME = "drive_account"
        const val KEY_EMAIL  = "account_email"
    }
}
```

#### `SyncMappers.kt` — `com.quem.data.sync`

Pure extension functions converting domain models to the export shapes expected by `MetadataExporter`.

```kotlin
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

#### `SyncCoordinator` — `com.quem.data.sync`

Contains all sync logic. Depends on `QueueDao`, `SyncManager`, and `Clock` — all injectable fakes in tests.

```kotlin
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

        syncManager.upload(snapshot)   // throws IOException on network failure

        dao.markItemsSynced()
        dao.markAttachmentsSynced()
    }
}
```

`markItemsSynced()` / `markAttachmentsSynced()` are called after upload. If they fail, the failure is logged but does not affect the `Result` — items will re-export on the next run (harmless for a full-file upload).

---

### Modified files

#### `QueueDao` — 5 new methods

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

#### `GoogleDriveConnectionRepository`

Accepts a `DriveAccountPreferences` parameter (added to constructor). Calls `save()` on connect and `clear()` on disconnect.

```kotlin
class GoogleDriveConnectionRepository(
    initialAuthorizationCoordinator: DriveAuthorizationCoordinator? = null,
    private val driveAccountPreferences: DriveAccountPreferences? = null
) : DriveConnectionRepository {

    private fun connect(grant: DriveAuthorizationGrant) {
        driveAccountPreferences?.save(grant.accountEmail)
        mutableState.value = DriveConnectionState.Connected(DriveAccount(email = grant.accountEmail))
    }

    override fun disconnect() {
        driveAccountPreferences?.clear()
        mutableState.value = DriveConnectionState.Disconnected
    }
}
```

The `driveAccountPreferences` parameter is nullable with a `null` default so existing call sites (tests, previews) don't require changes.

#### `AppDependencies`

Creates `DriveAccountPreferences` and passes it to `GoogleDriveConnectionRepository`.

```kotlin
private val driveAccountPreferences = DriveAccountPreferences(context.applicationContext)

val driveConnectionRepository: GoogleDriveConnectionRepository = GoogleDriveConnectionRepository(
    driveAccountPreferences = driveAccountPreferences
)
```

#### `SyncWorker`

```kotlin
class SyncWorker(
    context: Context,
    workerParameters: WorkerParameters
) : CoroutineWorker(context, workerParameters) {

    override suspend fun doWork(): Result = try {
        val deps = (applicationContext as QueMApplication).dependencies
        val email = DriveAccountPreferences(applicationContext).load()
            ?: return Result.success() // not signed in — skip

        val credential = GoogleAccountCredential
            .usingOAuth2(applicationContext, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
            .setSelectedAccountName(email)

        val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("QueM")
            .build()

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

    private companion object {
        const val TAG = "SyncWorker"
    }
}
```

`AppDependencies` needs to expose the `QueueDao` (currently private). Add:
```kotlin
val dao: QueueDao = database.queueDao()
```

#### `SyncScheduler` — add `scheduleOnce`

```kotlin
fun scheduleOnce(context: Context) {
    val request = OneTimeWorkRequestBuilder<SyncWorker>().build()
    WorkManager.getInstance(context).enqueue(request)
}
```

#### `QueMApp.kt` — wire `onManualSync`

```kotlin
val context = LocalContext.current
// ...
onManualSync = { SyncScheduler.scheduleOnce(context) }
```

---

## Error Handling

| Situation | Behaviour |
|---|---|
| `DriveAccountPreferences.load()` returns null | `Result.success()` — not signed in, skip silently |
| `IOException` from `syncManager.upload()` | `Result.retry()` — WorkManager backs off automatically |
| Any other exception | `Log.e` then `Result.failure()` — will not retry |
| `markItemsSynced()` / `markAttachmentsSynced()` throws | Logged, swallowed — items re-export on next run |
| Drive folder doesn't exist yet | `GoogleDriveGateway.ensureFolder()` creates it on first upload |

---

## Testing

### Instrumented tests — `DriveAccountPreferencesTest`
Uses `ApplicationProvider.getApplicationContext()` (in `androidTest`, same pattern as `DrivePickerRepositoryTest`).
- `save` then `load` returns the email
- `clear` then `load` returns null
- `load` with nothing saved returns null

### Unit tests — `SyncMappersTest`
- `QueueItem.toExportable()` maps all fields correctly; null optional fields remain null
- `Attachment.toMetadata()` maps all fields; `syncState` is excluded
- `HistoryEntry.toMetadata()` maps all fields

### Unit tests — `SyncCoordinatorTest`
Uses `FakeQueueDao` (already exists) and a `FakeDriveGateway : DriveGateway`.
- Snapshot contains all items, attachments, and history from the DAO
- `upload()` is called exactly once
- `markItemsSynced()` and `markAttachmentsSynced()` are called after a successful upload
- If `upload()` throws `IOException`, `markItemsSynced()` is NOT called (exception propagates to worker)

---

## What Comes Next

1. **Per-item sync state UI** — `SyncState` is already on `QueueItem`/`Attachment`; surface it as a small chip in `ItemDetailScreen`
2. **Archive search screen** — `searchArchive()` is implemented; needs a new screen + nav entry point
3. **Item editing** — no `updateItem` exists; users can't modify title/description/priority/due date after creation
4. **Download + merge** — pull the Drive snapshot and reconcile it with local Room data; requires conflict resolution design
