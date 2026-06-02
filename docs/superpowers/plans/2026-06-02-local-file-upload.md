# Local File Upload Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users pick any file from phone storage, upload it to `QueM/files/{itemId}/` in their Google Drive, and store it as a `DRIVE_FILE` attachment — with an upload progress indicator and retry on failure.

**Architecture:** New `AttachmentType.LOCAL_FILE` + `SyncState.PENDING_UPLOAD`/`UPLOAD_FAILED` carry upload state without schema changes. `DriveFileUploadGateway` interface (implemented by `GoogleDriveGateway`) streams file bytes from a content URI using `InputStreamContent`. `QueueViewModel` tracks in-flight uploads in a `MutableStateFlow<Set<String>>`; attachment rows show spinners or Retry options. Persistent URI permission taken in `QueMApp` before the upload starts.

**Tech Stack:** Android SAF `ActivityResultContracts.OpenDocument`, Google Drive Files API with `InputStreamContent`, Room (no migration needed), Jetpack Compose, Kotlin coroutines.

---

## File map

| Action | Path |
|---|---|
| Modify | `app/src/main/java/com/quem/core/model/AttachmentType.kt` |
| Modify | `app/src/main/java/com/quem/core/model/SyncState.kt` |
| Modify | `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt` |
| Modify | `app/src/main/java/com/quem/ui/QueueViewModel.kt` (toIndicator fix) |
| Create | `app/src/main/java/com/quem/drive/DriveFileUploadGateway.kt` |
| Modify | `app/src/main/java/com/quem/drive/GoogleDriveGateway.kt` |
| Modify | `app/src/main/java/com/quem/data/local/QueueDao.kt` |
| Modify | `app/src/main/java/com/quem/data/repository/QueueRepository.kt` |
| Modify | `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt` |
| Modify | `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt` |
| Modify | `app/src/main/java/com/quem/ui/QueueViewModel.kt` |
| Modify | `app/src/test/java/com/quem/ui/QueueViewModelTest.kt` |
| Create | `app/src/main/java/com/quem/drive/LocalFileSelection.kt` |
| Modify | `app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt` |
| Modify | `app/src/main/java/com/quem/drive/DrivePickerRepository.kt` |
| Modify | `app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt` |
| Modify | `app/src/main/java/com/quem/ui/AttachmentEditor.kt` |
| Modify | `app/src/main/java/com/quem/ui/EditItemScreen.kt` |
| Modify | `app/src/main/java/com/quem/app/MainActivity.kt` |
| Modify | `app/src/main/java/com/quem/app/QueMApp.kt` |

---

## Task 1: Data model — AttachmentType, SyncState, SyncCoordinator filter

**Files:**
- Modify: `app/src/main/java/com/quem/core/model/AttachmentType.kt`
- Modify: `app/src/main/java/com/quem/core/model/SyncState.kt`
- Modify: `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt`
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`

Context: No DB migration needed — `AttachmentType` and `SyncState` are stored as plain strings. Adding new enum values is automatically compatible. The `SyncState.toIndicator()` function in `QueueViewModel.kt` is an exhaustive `when` that must be updated. `SyncCoordinator` builds the Drive metadata snapshot from all attachments — `LOCAL_FILE` attachments must be excluded so half-uploaded files don't corrupt the remote snapshot.

- [ ] **Step 1: Add LOCAL_FILE to AttachmentType.kt**

```kotlin
package com.quem.core.model

enum class AttachmentType {
    TEXT,
    LINK,
    DRIVE_FILE,
    DRIVE_FOLDER,
    LOCAL_FILE   // picked from device; upload pending or failed
}
```

- [ ] **Step 2: Add PENDING_UPLOAD and UPLOAD_FAILED to SyncState.kt**

```kotlin
package com.quem.core.model

enum class SyncState {
    SYNCED,
    PENDING_SYNC,
    SYNCING,
    ERROR,
    PENDING_UPLOAD,   // local file attachment waiting to be uploaded to Drive
    UPLOAD_FAILED     // upload attempted and failed; url field holds URI for retry
}
```

- [ ] **Step 3: Fix exhaustive when in QueueViewModel.kt**

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`. Find the `SyncState.toIndicator()` extension at the bottom of the file and add the two new cases:

```kotlin
private fun SyncState.toIndicator(): SyncIndicator? = when (this) {
    SyncState.SYNCED          -> null
    SyncState.PENDING_SYNC    -> SyncIndicator.PENDING
    SyncState.SYNCING         -> SyncIndicator.SYNCING
    SyncState.ERROR           -> SyncIndicator.ERROR
    SyncState.PENDING_UPLOAD  -> SyncIndicator.PENDING
    SyncState.UPLOAD_FAILED   -> SyncIndicator.ERROR
}
```

- [ ] **Step 4: Exclude LOCAL_FILE attachments from sync snapshot in SyncCoordinator.kt**

Open `app/src/main/java/com/quem/data/sync/SyncCoordinator.kt`. Add an import and filter:

```kotlin
import com.quem.core.model.AttachmentType

class SyncCoordinator(
    private val dao: QueueDao,
    private val syncManager: SyncManager,
    private val clock: Clock,
    private val mergeCoordinator: MergeCoordinator = MergeCoordinator(dao)
) {
    suspend fun sync() {
        val remoteSnapshot = syncManager.download()
        if (remoteSnapshot != null) {
            mergeCoordinator.merge(remoteSnapshot)
        }

        val items       = dao.allItems().map { it.toDomain() }
        val attachments = dao.allAttachments()
            .map { it.toDomain() }
            .filter { it.type != AttachmentType.LOCAL_FILE }   // exclude pending/failed uploads
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

- [ ] **Step 5: Run full unit test suite**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test`

Expected: BUILD SUCCESSFUL — all existing tests pass.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/core/model/AttachmentType.kt \
        app/src/main/java/com/quem/core/model/SyncState.kt \
        app/src/main/java/com/quem/data/sync/SyncCoordinator.kt \
        app/src/main/java/com/quem/ui/QueueViewModel.kt
git commit -m "feat: add LOCAL_FILE attachment type and PENDING_UPLOAD/UPLOAD_FAILED sync states; exclude local files from sync snapshot"
```

---

## Task 2: DriveFileUploadGateway interface + GoogleDriveGateway.uploadLocalFile

**Files:**
- Create: `app/src/main/java/com/quem/drive/DriveFileUploadGateway.kt`
- Modify: `app/src/main/java/com/quem/drive/GoogleDriveGateway.kt`

Context: `GoogleDriveGateway` already has `ensureFolder` (creates the root `QueM/` folder) and uses `GoogleDriveQueries` for safe query strings. The upload uses `InputStreamContent` from `com.google.api.client.http.InputStreamContent` to stream file bytes — never loading the full file into memory. The per-item folder structure is `QueM/files/{itemId}/`. Subfolders are tagged with `quemRole=itemFilesFolder`. No tests needed for this task — the gateway is tested via a fake in Task 3.

- [ ] **Step 1: Create DriveFileUploadGateway.kt**

```kotlin
package com.quem.drive

import android.content.ContentResolver
import android.net.Uri

interface DriveFileUploadGateway {
    /**
     * Ensures `QueM/files/{itemId}/` exists in Drive and uploads the file from [uri].
     * Streams the file using [contentResolver] — never loads it fully into memory.
     * Returns the Drive file ID of the uploaded file.
     */
    suspend fun uploadLocalFile(
        itemId: String,
        fileName: String,
        mimeType: String,
        contentResolver: ContentResolver,
        uri: Uri
    ): String
}
```

- [ ] **Step 2: Add subfolderQuery to GoogleDriveQueries**

Open `app/src/main/java/com/quem/drive/GoogleDriveGateway.kt`. Find the `GoogleDriveQueries` object (around line 102) and add after `canonicalSharedFileQuery`:

```kotlin
internal fun subfolderQuery(parentId: String, folderName: String): String =
    "mimeType = ${literal(FOLDER_MIME_TYPE)} and " +
    "name = ${literal(folderName)} and " +
    "${literal(parentId)} in parents and " +
    "trashed = false"
```

Also add to the private constants block at the bottom:
```kotlin
internal const val APP_PROPERTY_ITEM_FILES_FOLDER = "itemFilesFolder"
```

- [ ] **Step 3: Make GoogleDriveGateway implement DriveFileUploadGateway**

In `app/src/main/java/com/quem/drive/GoogleDriveGateway.kt`, update the class declaration and add imports:

```kotlin
import android.content.ContentResolver
import android.net.Uri
import com.google.api.client.http.InputStreamContent

class GoogleDriveGateway(
    private val drive: Drive,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DriveGateway, DriveFileUploadGateway {
```

- [ ] **Step 4: Add uploadLocalFile, ensureSubfolder, findSubfolder to GoogleDriveGateway**

Add these methods to `GoogleDriveGateway` (before the `private companion object`):

```kotlin
override suspend fun uploadLocalFile(
    itemId: String,
    fileName: String,
    mimeType: String,
    contentResolver: ContentResolver,
    uri: Uri
): String = withContext(ioDispatcher) {
    val quemFolderId  = ensureFolder("QueM")
    val filesFolderId = ensureSubfolder(quemFolderId, "files")
    val itemFolderId  = ensureSubfolder(filesFolderId, itemId)

    val inputStream = contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("Cannot open stream for $uri")

    val mediaContent = InputStreamContent(mimeType, inputStream)

    val metadata = File()
        .setName(fileName)
        .setParents(listOf(itemFolderId))
        .setAppProperties(mapOf(APP_PROPERTY_ROLE to GoogleDriveQueries.APP_PROPERTY_ITEM_FILES_FOLDER))

    drive.files()
        .create(metadata, mediaContent)
        .setFields("id")
        .execute()
        .id
}

private fun ensureSubfolder(parentId: String, folderName: String): String {
    val existing = findSubfolder(parentId, folderName)
    if (existing != null) return existing.id
    return drive.files()
        .create(
            File()
                .setName(folderName)
                .setMimeType(FOLDER_MIME_TYPE)
                .setParents(listOf(parentId))
                .setAppProperties(mapOf(APP_PROPERTY_ROLE to GoogleDriveQueries.APP_PROPERTY_ITEM_FILES_FOLDER))
        )
        .setFields("id")
        .execute()
        .id
}

private fun findSubfolder(parentId: String, folderName: String): File? = drive.files()
    .list()
    .setQ(GoogleDriveQueries.subfolderQuery(parentId, folderName))
    .setSpaces("drive")
    .setFields("files(id, name)")
    .execute()
    .files
    .orEmpty()
    .firstOrNull()
```

- [ ] **Step 5: Run full unit test suite**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/drive/DriveFileUploadGateway.kt \
        app/src/main/java/com/quem/drive/GoogleDriveGateway.kt
git commit -m "feat: add DriveFileUploadGateway interface and GoogleDriveGateway.uploadLocalFile with per-item subfolders"
```

---

## Task 3: QueueDao + QueueRepository + RoomQueueRepository + repository unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/data/local/QueueDao.kt`
- Modify: `app/src/main/java/com/quem/data/repository/QueueRepository.kt`
- Modify: `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`
- Modify: `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`

Context: `AttachmentEntity` already has `url: String?` for storing content URIs and `driveFileId: String?` for the Drive file ID after upload. The `addAttachment` private helper in `RoomQueueRepository` handles all attachment creation. `uploadPendingFile` and `retryFileUpload` both use a `DriveFileUploadGateway` + `ContentResolver`; `retryFileUpload` simply delegates to `uploadPendingFile`. The `FakeQueueDao` in the test file needs new overrides for the three new DAO methods.

- [ ] **Step 1: Add three new DAO methods to QueueDao.kt**

Open `app/src/main/java/com/quem/data/local/QueueDao.kt`. Add after `updateShareInfo`:

```kotlin
@Query("SELECT * FROM attachments WHERE id = :id LIMIT 1")
fun observeAttachment(id: String): Flow<AttachmentEntity?>

@Query("""
    UPDATE attachments
    SET type        = 'DRIVE_FILE',
        driveFileId = :driveFileId,
        syncState   = 'SYNCED',
        url         = NULL,
        updatedAt   = :updatedAt
    WHERE id = :id
""")
suspend fun updateAttachmentAfterUpload(id: String, driveFileId: String, updatedAt: java.time.Instant)

@Query("""
    UPDATE attachments
    SET syncState = 'UPLOAD_FAILED',
        updatedAt = :updatedAt
    WHERE id = :id
""")
suspend fun updateAttachmentUploadFailed(id: String, updatedAt: java.time.Instant)
```

- [ ] **Step 2: Add three new methods to QueueRepository interface**

Open `app/src/main/java/com/quem/data/repository/QueueRepository.kt`. Add after `shareItem`:

```kotlin
/**
 * Stores a LOCAL_FILE / PENDING_UPLOAD attachment immediately.
 * Returns the new attachment ID (needed for uploadPendingFile).
 */
suspend fun attachLocalFile(
    queueItemId: String,
    uri: String,
    displayName: String,
    mimeType: String?
): String

/**
 * Uploads the file referenced by the LOCAL_FILE attachment to Drive.
 * On success: transitions attachment to DRIVE_FILE / SYNCED.
 * On failure: transitions attachment to UPLOAD_FAILED, preserving uri for retry.
 * Returns true on success.
 */
suspend fun uploadPendingFile(
    attachmentId: String,
    contentResolver: android.content.ContentResolver,
    gateway: com.quem.drive.DriveFileUploadGateway
): Boolean

/**
 * Retries a UPLOAD_FAILED attachment. Identical contract to uploadPendingFile.
 */
suspend fun retryFileUpload(
    attachmentId: String,
    contentResolver: android.content.ContentResolver,
    gateway: com.quem.drive.DriveFileUploadGateway
): Boolean
```

- [ ] **Step 3: Write the failing repository unit tests**

Open `app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt`.

**3a.** Add `observeAttachment`, `updateAttachmentAfterUpload`, `updateAttachmentUploadFailed` to `FakeQueueDao` (after the existing `deleteHistoryEntry` override):

```kotlin
override fun observeAttachment(id: String): Flow<AttachmentEntity?> =
    attachmentEntities.map { list -> list.firstOrNull { it.id == id } }

override suspend fun updateAttachmentAfterUpload(
    id: String,
    driveFileId: String,
    updatedAt: java.time.Instant
) {
    attachmentEntities.value = attachmentEntities.value.map { a ->
        if (a.id == id) a.copy(type = "DRIVE_FILE", driveFileId = driveFileId, syncState = "SYNCED", url = null, updatedAt = updatedAt)
        else a
    }
}

override suspend fun updateAttachmentUploadFailed(id: String, updatedAt: java.time.Instant) {
    attachmentEntities.value = attachmentEntities.value.map { a ->
        if (a.id == id) a.copy(syncState = "UPLOAD_FAILED", updatedAt = updatedAt)
        else a
    }
}
```

**3b.** Add `FakeDriveFileUploadGateway` at the bottom of the test file (after `FakeShareGateway`):

```kotlin
private class FakeDriveFileUploadGateway : com.quem.drive.DriveFileUploadGateway {
    var capturedItemId: String? = null
    var capturedFileName: String? = null
    var shouldThrow: Exception? = null

    override suspend fun uploadLocalFile(
        itemId: String,
        fileName: String,
        mimeType: String,
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri
    ): String {
        shouldThrow?.let { throw it }
        capturedItemId  = itemId
        capturedFileName = fileName
        return "uploaded-drive-file-id"
    }
}
```

**3c.** Add 4 tests inside `RoomQueueRepositoryTest` (after the last `shareItem` test):

```kotlin
@Test
fun attachLocalFileCreatesLocalFileAttachmentWithPendingUploadState() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1", "attachment-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-06-02T10:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Report", description = null, priority = null, dueDate = null)

    val attachmentId = repository.attachLocalFile(
        queueItemId = "item-1",
        uri = "content://media/1234",
        displayName = "photo.jpg",
        mimeType = "image/jpeg"
    )

    assertEquals("attachment-1", attachmentId)
    val entity = dao.observeAttachment("attachment-1").first()
    requireNotNull(entity)
    assertEquals("LOCAL_FILE", entity.type)
    assertEquals("PENDING_UPLOAD", entity.syncState)
    assertEquals("content://media/1234", entity.url)
    assertEquals("photo.jpg", entity.displayName)
    assertNull(entity.driveFileId)
}

@Test
fun uploadPendingFileOnSuccessTransitionsToDriveFileAndClearsUri() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1", "attachment-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-06-02T10:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Report", description = null, priority = null, dueDate = null)
    repository.attachLocalFile("item-1", "content://media/1234", "photo.jpg", "image/jpeg")

    val gateway = FakeDriveFileUploadGateway()
    val result = repository.uploadPendingFile(
        attachmentId = "attachment-1",
        contentResolver = android.app.Application().contentResolver,
        gateway = gateway
    )

    assertTrue(result)
    assertEquals("item-1", gateway.capturedItemId)
    assertEquals("photo.jpg", gateway.capturedFileName)
    val entity = dao.observeAttachment("attachment-1").first()
    requireNotNull(entity)
    assertEquals("DRIVE_FILE", entity.type)
    assertEquals("SYNCED", entity.syncState)
    assertEquals("uploaded-drive-file-id", entity.driveFileId)
    assertNull(entity.url)
}

@Test
fun uploadPendingFileOnFailureTransitionsToUploadFailed() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1", "attachment-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-06-02T10:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Report", description = null, priority = null, dueDate = null)
    repository.attachLocalFile("item-1", "content://media/1234", "photo.jpg", "image/jpeg")

    val gateway = FakeDriveFileUploadGateway().apply { shouldThrow = RuntimeException("network error") }
    val result = repository.uploadPendingFile(
        attachmentId = "attachment-1",
        contentResolver = android.app.Application().contentResolver,
        gateway = gateway
    )

    assertFalse(result)
    val entity = dao.observeAttachment("attachment-1").first()
    requireNotNull(entity)
    assertEquals("UPLOAD_FAILED", entity.syncState)
    assertEquals("LOCAL_FILE", entity.type)
    assertEquals("content://media/1234", entity.url)   // preserved for retry
}

@Test
fun retryFileUploadDelegatesToUploadPendingFile() = runTest {
    val dao = FakeQueueDao()
    val ids = mutableListOf("item-1", "history-1", "attachment-1")
    val repository = RoomQueueRepository(
        dao = dao,
        clock = FixedClock(Instant.parse("2026-06-02T10:00:00Z")),
        idProvider = { ids.removeFirst() }
    )
    repository.createItem(title = "Report", description = null, priority = null, dueDate = null)
    // Simulate a failed attachment already in DB
    repository.attachLocalFile("item-1", "content://media/1234", "photo.jpg", "image/jpeg")
    repository.uploadPendingFile(
        "attachment-1",
        android.app.Application().contentResolver,
        FakeDriveFileUploadGateway().apply { shouldThrow = RuntimeException("first attempt") }
    )

    val gateway = FakeDriveFileUploadGateway()
    val result = repository.retryFileUpload(
        attachmentId = "attachment-1",
        contentResolver = android.app.Application().contentResolver,
        gateway = gateway
    )

    assertTrue(result)
    assertEquals("DRIVE_FILE", dao.observeAttachment("attachment-1").first()?.type)
}
```

- [ ] **Step 4: Run tests to verify they fail**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: FAILED — `attachLocalFile`, `uploadPendingFile`, `retryFileUpload` not yet implemented.

- [ ] **Step 5: Implement the three methods in RoomQueueRepository**

Open `app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt`. Add imports:

```kotlin
import android.content.ContentResolver
import android.net.Uri
import com.quem.core.model.AttachmentType
import com.quem.drive.DriveFileUploadGateway
```

Add after `shareItem`:

```kotlin
override suspend fun attachLocalFile(
    queueItemId: String,
    uri: String,
    displayName: String,
    mimeType: String?
): String {
    if (displayName.isBlank()) return ""
    if (dao.observeItem(queueItemId).first() == null) return ""

    val now = clock.now()
    val attachmentId = idProvider()
    dao.upsertAttachment(
        AttachmentEntity(
            id          = attachmentId,
            queueItemId = queueItemId,
            type        = AttachmentType.LOCAL_FILE.name,
            displayName = displayName.trim(),
            textContent = null,
            url         = uri,
            driveFileId = null,
            mimeType    = mimeType,
            createdAt   = now,
            updatedAt   = now,
            syncState   = SyncState.PENDING_UPLOAD.name
        )
    )
    return attachmentId
}

override suspend fun uploadPendingFile(
    attachmentId: String,
    contentResolver: ContentResolver,
    gateway: DriveFileUploadGateway
): Boolean = runCatching {
    val entity = dao.observeAttachment(attachmentId).first() ?: return@runCatching false
    val uriString = entity.url ?: return@runCatching false
    val uri = Uri.parse(uriString)

    val driveFileId = gateway.uploadLocalFile(
        itemId          = entity.queueItemId,
        fileName        = entity.displayName,
        mimeType        = entity.mimeType ?: "application/octet-stream",
        contentResolver = contentResolver,
        uri             = uri
    )

    dao.updateAttachmentAfterUpload(
        id          = attachmentId,
        driveFileId = driveFileId,
        updatedAt   = clock.now()
    )
    true
}.getOrElse { e ->
    runCatching { Log.w(TAG, "uploadPendingFile failed", e) }
    runCatching { dao.updateAttachmentUploadFailed(attachmentId, clock.now()) }
    false
}

override suspend fun retryFileUpload(
    attachmentId: String,
    contentResolver: ContentResolver,
    gateway: DriveFileUploadGateway
): Boolean = uploadPendingFile(attachmentId, contentResolver, gateway)
```

Note: `AttachmentEntity` is already imported. Add `import com.quem.data.local.AttachmentEntity` if needed. `SyncState` import may also be needed: `import com.quem.core.model.SyncState`.

- [ ] **Step 6: Run tests to verify they pass**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test --tests "com.quem.data.repository.RoomQueueRepositoryTest"`

Expected: BUILD SUCCESSFUL, all tests pass.

Note: The tests pass a null-constructed `ContentResolver` because `FakeDriveFileUploadGateway.uploadLocalFile` never actually calls `contentResolver.openInputStream()`. Use:
```kotlin
val fakeContentResolver = object : android.content.ContentResolver(null) {}
```
Replace `android.app.Application().contentResolver` with `fakeContentResolver` in all 4 repository tests.

- [ ] **Step 7: Run full unit test suite**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/quem/data/local/QueueDao.kt \
        app/src/main/java/com/quem/data/repository/QueueRepository.kt \
        app/src/main/java/com/quem/data/repository/RoomQueueRepository.kt \
        app/src/test/java/com/quem/data/repository/RoomQueueRepositoryTest.kt
git commit -m "feat: add attachLocalFile, uploadPendingFile, retryFileUpload to repository"
```

---

## Task 4: ViewModel — UploadState, AttachmentUi.uploadState, new actions, unit tests

**Files:**
- Modify: `app/src/main/java/com/quem/ui/QueueViewModel.kt`
- Modify: `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`

Context: `_uploadingAttachmentIds: MutableStateFlow<Set<String>>` tracks in-progress uploads in memory (not persisted — survives until process death). The `selectedItem` flow must be updated to combine with this set so attachment rows get `UploadState.IN_PROGRESS` in real-time. `AttachmentUi` already has `isDriveFile` etc. — add `uploadState: UploadState` as a new field with default `UploadState.NONE`. `toAttachmentUi()` maps `UPLOAD_FAILED` syncState to `UploadState.FAILED`; the ViewModel sets `IN_PROGRESS` dynamically via the combine.

- [ ] **Step 1: Write failing ViewModel unit tests**

Open `app/src/test/java/com/quem/ui/QueueViewModelTest.kt`.

**1a.** Add to `FakeQueueRepository` (after `retryFileUpload` or add it — check if it exists):

```kotlin
var lastLocalFileItemId: String? = null
var lastLocalFileUri: String? = null
var lastLocalFileDisplayName: String? = null
private var localFileAttachmentId = "local-attachment-1"
var uploadPendingFileReturns: Boolean = true

override suspend fun attachLocalFile(
    queueItemId: String,
    uri: String,
    displayName: String,
    mimeType: String?
): String {
    lastLocalFileItemId = queueItemId
    lastLocalFileUri = uri
    lastLocalFileDisplayName = displayName
    return localFileAttachmentId
}

override suspend fun uploadPendingFile(
    attachmentId: String,
    contentResolver: android.content.ContentResolver,
    gateway: com.quem.drive.DriveFileUploadGateway
): Boolean = uploadPendingFileReturns

override suspend fun retryFileUpload(
    attachmentId: String,
    contentResolver: android.content.ContentResolver,
    gateway: com.quem.drive.DriveFileUploadGateway
): Boolean = uploadPendingFileReturns
```

**1b.** Add 3 tests inside `QueueViewModelTest` (after the `shareItem` tests):

```kotlin
@Test
fun attachAndUploadLocalFileStoresAttachmentAndStartsUpload() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Report", description = null, priority = null, dueDate = null)
    val viewModel = QueueViewModel(
        repository   = repository,
        ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
    )
    collectSelectedItem(viewModel)
    viewModel.selectItem("item-1")
    advanceUntilIdle()

    viewModel.attachAndUploadLocalFile(
        uri         = android.net.Uri.parse("content://media/1234"),
        displayName = "photo.jpg",
        mimeType    = "image/jpeg",
        contentResolver = android.app.Application().contentResolver
    ) { FakeDriveFileUploadGateway() }
    advanceUntilIdle()

    assertEquals("item-1", repository.lastLocalFileItemId)
    assertEquals("content://media/1234", repository.lastLocalFileUri)
    assertEquals("photo.jpg", repository.lastLocalFileDisplayName)
}

@Test
fun attachmentUiUploadStateIsFailedWhenSyncStateIsUploadFailed() = runTest {
    val repository = FakeQueueRepository()
    repository.items.value = listOf(
        queueItem(id = "item-1", title = "Report", description = null, status = QueueStatus.QUEUED)
    )
    repository.attachments.value = listOf(
        attachment(
            id          = "att-1",
            queueItemId = "item-1",
            type        = com.quem.core.model.AttachmentType.LOCAL_FILE,
            syncState   = com.quem.core.model.SyncState.UPLOAD_FAILED
        )
    )
    val viewModel = QueueViewModel(repository)
    collectSelectedItem(viewModel)

    viewModel.selectItem("item-1")
    advanceUntilIdle()

    val attachmentUi = viewModel.selectedItem.value?.attachments?.first()
    assertEquals(UploadState.FAILED, attachmentUi?.uploadState)
}

@Test
fun retryFileUploadCallsRepositoryRetry() = runTest {
    val repository = FakeQueueRepository()
    repository.createItem(title = "Report", description = null, priority = null, dueDate = null)
    val viewModel = QueueViewModel(
        repository   = repository,
        ioDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler)
    )
    collectSelectedItem(viewModel)
    viewModel.selectItem("item-1")
    advanceUntilIdle()

    viewModel.retryFileUpload(
        attachmentId    = "local-attachment-1",
        contentResolver = android.app.Application().contentResolver
    ) { FakeDriveFileUploadGateway() }
    advanceUntilIdle()

    assertTrue(repository.uploadPendingFileReturns)  // confirms the call completed without crash
}
```

**1c.** Add `attachment()` helper near the `queueItem()` helper at the bottom of the test file:

```kotlin
private fun attachment(
    id: String,
    queueItemId: String,
    type: com.quem.core.model.AttachmentType = com.quem.core.model.AttachmentType.DRIVE_FILE,
    syncState: com.quem.core.model.SyncState = com.quem.core.model.SyncState.SYNCED
) = com.quem.core.model.Attachment(
    id          = id,
    queueItemId = queueItemId,
    type        = type,
    displayName = "file.pdf",
    textContent = null,
    url         = if (type == com.quem.core.model.AttachmentType.LOCAL_FILE) "content://media/1" else null,
    driveFileId = if (type != com.quem.core.model.AttachmentType.LOCAL_FILE) "drive-id" else null,
    mimeType    = "application/pdf",
    createdAt   = java.time.Instant.parse("2026-06-02T10:00:00Z"),
    updatedAt   = java.time.Instant.parse("2026-06-02T10:00:00Z"),
    syncState   = syncState
)
```

Also add a mutable `attachments` field to `FakeQueueRepository` (after `items`):

```kotlin
val attachments = MutableStateFlow<List<com.quem.core.model.Attachment>>(emptyList())
```

And update `observeAttachments` to use it:

```kotlin
override fun observeAttachments(queueItemId: String): Flow<List<com.quem.core.model.Attachment>> =
    attachments.map { list -> list.filter { it.queueItemId == queueItemId } }
```

Also add `FakeDriveFileUploadGateway` to `QueueViewModelTest.kt` (at the bottom, near `FakeDriveShareGateway`):

```kotlin
private class FakeDriveFileUploadGateway : com.quem.drive.DriveFileUploadGateway {
    override suspend fun uploadLocalFile(
        itemId: String,
        fileName: String,
        mimeType: String,
        contentResolver: android.content.ContentResolver,
        uri: android.net.Uri
    ): String = "uploaded-id"
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: FAILED — `UploadState`, `attachAndUploadLocalFile`, `retryFileUpload` not yet defined.

- [ ] **Step 3: Update QueueViewModel.kt**

Open `app/src/main/java/com/quem/ui/QueueViewModel.kt`.

**3a.** Add `UploadState` enum and update `AttachmentUi` (near the top, after `SyncIndicator`):

```kotlin
enum class UploadState { NONE, IN_PROGRESS, FAILED }

data class AttachmentUi(
    val id: String,
    val displayName: String,
    val url: String?,
    val driveFileId: String?,
    val isLink: Boolean,
    val isDriveFile: Boolean,
    val isDriveFolder: Boolean,
    val uploadState: UploadState = UploadState.NONE   // new
)
```

**3b.** Add `_uploadingAttachmentIds` field after the ViewModel's `ioDispatcher` property:

```kotlin
private val _uploadingAttachmentIds = MutableStateFlow<Set<String>>(emptySet())
```

**3c.** Update `selectedItem` to combine with `_uploadingAttachmentIds`. Replace the current `selectedItem` StateFlow with:

```kotlin
val selectedItem: StateFlow<QueueItemDetailUi?> =
    combine(
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
                            attachments = attachments.map { it.toAttachmentUi() },
                            history     = history.map { HistoryEntryUi(it.id, it.toDisplayString(now)) }
                        )
                    }
                }
            },
        _uploadingAttachmentIds
    ) { detail, uploadingIds ->
        detail?.copy(
            attachments = detail.attachments.map { att ->
                if (att.id in uploadingIds) att.copy(uploadState = UploadState.IN_PROGRESS)
                else att
            }
        )
    }
    .stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue   = null
    )
```

**3d.** Update `toAttachmentUi()` to map `UPLOAD_FAILED`:

```kotlin
private fun Attachment.toAttachmentUi() = AttachmentUi(
    id            = id,
    displayName   = displayName,
    url           = url,
    driveFileId   = driveFileId,
    isLink        = type == AttachmentType.LINK,
    isDriveFile   = type == AttachmentType.DRIVE_FILE,
    isDriveFolder = type == AttachmentType.DRIVE_FOLDER,
    uploadState   = if (syncState == SyncState.UPLOAD_FAILED) UploadState.FAILED else UploadState.NONE
)
```

**3e.** Add imports to `QueueViewModel.kt`:

```kotlin
import android.content.ContentResolver
import android.net.Uri
import com.quem.drive.DriveFileUploadGateway
```

**3f.** Add `attachAndUploadLocalFile` and `retryFileUpload` actions (after `deleteHistoryEntry`):

```kotlin
fun attachAndUploadLocalFile(
    uri: Uri,
    displayName: String,
    mimeType: String?,
    contentResolver: ContentResolver,
    gatewayFactory: () -> DriveFileUploadGateway
) {
    val itemId = selectedItemId.value ?: return
    viewModelScope.launch {
        val attachmentId = repository.attachLocalFile(
            queueItemId = itemId,
            uri         = uri.toString(),
            displayName = displayName,
            mimeType    = mimeType
        )
        if (attachmentId.isBlank()) return@launch
        _uploadingAttachmentIds.update { it + attachmentId }
        try {
            val gateway = withContext(ioDispatcher) { gatewayFactory() }
            repository.uploadPendingFile(attachmentId, contentResolver, gateway)
        } finally {
            _uploadingAttachmentIds.update { it - attachmentId }
        }
    }
}

fun retryFileUpload(
    attachmentId: String,
    contentResolver: ContentResolver,
    gatewayFactory: () -> DriveFileUploadGateway
) {
    viewModelScope.launch {
        _uploadingAttachmentIds.update { it + attachmentId }
        try {
            val gateway = withContext(ioDispatcher) { gatewayFactory() }
            repository.retryFileUpload(attachmentId, contentResolver, gateway)
        } finally {
            _uploadingAttachmentIds.update { it - attachmentId }
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test --tests "com.quem.ui.QueueViewModelTest"`

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Run full unit test suite**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/ui/QueueViewModel.kt \
        app/src/test/java/com/quem/ui/QueueViewModelTest.kt
git commit -m "feat: add UploadState to AttachmentUi; attachAndUploadLocalFile and retryFileUpload in QueueViewModel"
```

---

## Task 5: Picker infrastructure — LocalFileSelection, DrivePickerCoordinator, DrivePickerRepository, SafDrivePickerCoordinator

**Files:**
- Create: `app/src/main/java/com/quem/drive/LocalFileSelection.kt`
- Modify: `app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt`
- Modify: `app/src/main/java/com/quem/drive/DrivePickerRepository.kt`
- Modify: `app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt`

Context: A separate `localFileLauncher` will be registered in `MainActivity` (Task 6). `DrivePickerRepository` already has the pattern of `pendingFileCallback` / `setPendingFileCallback` / `handleFileResult` — add identical logic for local files. `toLocalFileSelection()` queries display name and MIME type from the URI using the existing `queryMetadata()` helper. The URI is returned as-is (not transformed to extract a Drive ID).

- [ ] **Step 1: Create LocalFileSelection.kt**

```kotlin
package com.quem.drive

import android.net.Uri

data class LocalFileSelection(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?
)
```

- [ ] **Step 2: Add pickLocalFile to DrivePickerCoordinator**

```kotlin
package com.quem.drive

interface DrivePickerCoordinator {
    fun pickFile(onResult: (DriveSelection?) -> Unit)
    fun pickFolder(onResult: (DriveSelection?) -> Unit)
    fun pickLocalFile(onResult: (LocalFileSelection?) -> Unit)
}

object NoOpDrivePickerCoordinator : DrivePickerCoordinator {
    override fun pickFile(onResult: (DriveSelection?) -> Unit)       = onResult(null)
    override fun pickFolder(onResult: (DriveSelection?) -> Unit)     = onResult(null)
    override fun pickLocalFile(onResult: (LocalFileSelection?) -> Unit) = onResult(null)
}
```

- [ ] **Step 3: Add local file picker state to DrivePickerRepository**

Open `app/src/main/java/com/quem/drive/DrivePickerRepository.kt`. Add after the existing `pendingFolderCallback` field and its associated methods:

```kotlin
private var pendingLocalFileCallback: ((LocalFileSelection?) -> Unit)? = null

@MainThread
fun clearPendingLocalFileCallback() { pendingLocalFileCallback = null }

@MainThread
fun setPendingLocalFileCallback(callback: (LocalFileSelection?) -> Unit): Boolean {
    if (pendingLocalFileCallback != null) return false
    pendingLocalFileCallback = callback
    return true
}

/** Called by MainActivity when the local file picker Activity Result arrives. */
@MainThread
fun handleLocalFileResult(uri: Uri?) {
    val callback = pendingLocalFileCallback
    pendingLocalFileCallback = null
    callback?.invoke(uri?.toLocalFileSelection())
}

private fun Uri.toLocalFileSelection(): LocalFileSelection? {
    val (displayName, mimeType) = queryMetadata()
    return LocalFileSelection(
        uri         = this,
        displayName = displayName ?: lastPathSegment ?: "file",
        mimeType    = mimeType
    )
}
```

- [ ] **Step 4: Add pickLocalFile to SafDrivePickerCoordinator**

Open `app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt`. Add the `localFileLauncher` parameter and implement the new method:

```kotlin
class SafDrivePickerCoordinator(
    private val fileLauncher: ActivityResultLauncher<Array<String>>,
    private val folderLauncher: ActivityResultLauncher<Uri?>,
    private val localFileLauncher: ActivityResultLauncher<Array<String>>,
    private val drivePickerRepository: DrivePickerRepository
) : DrivePickerCoordinator {

    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        drivePickerRepository.clearPendingFileCallback()
        if (drivePickerRepository.setPendingFileCallback(onResult)) {
            try { fileLauncher.launch(arrayOf("*/*")) }
            catch (e: Exception) { drivePickerRepository.handleFileResult(null) }
        }
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        drivePickerRepository.clearPendingFolderCallback()
        if (drivePickerRepository.setPendingFolderCallback(onResult)) {
            try { folderLauncher.launch(null) }
            catch (e: Exception) { drivePickerRepository.handleFolderResult(null) }
        }
    }

    override fun pickLocalFile(onResult: (LocalFileSelection?) -> Unit) {
        drivePickerRepository.clearPendingLocalFileCallback()
        if (drivePickerRepository.setPendingLocalFileCallback(onResult)) {
            try { localFileLauncher.launch(arrayOf("*/*")) }
            catch (e: Exception) { drivePickerRepository.handleLocalFileResult(null) }
        }
    }
}
```

- [ ] **Step 5: Run full unit test suite**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test`

Expected: BUILD SUCCESSFUL. (Note: `MainActivity` currently passes only 2 args to `SafDrivePickerCoordinator`; it will fail to compile — fix it in Task 6 Step 1.)

Actually: run compile only for now:
`export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:compileDebugKotlin 2>&1 | tail -15`

Expected: compile error in `MainActivity.kt` about wrong number of constructor args — that's expected and will be fixed in Task 6.

- [ ] **Step 6: Commit**

```
git add app/src/main/java/com/quem/drive/LocalFileSelection.kt \
        app/src/main/java/com/quem/drive/DrivePickerCoordinator.kt \
        app/src/main/java/com/quem/drive/DrivePickerRepository.kt \
        app/src/main/java/com/quem/drive/SafDrivePickerCoordinator.kt
git commit -m "feat: add LocalFileSelection and local file picker infrastructure"
```

---

## Task 6: UI wiring — AttachmentEditor, EditItemScreen, MainActivity, QueMApp, upload state rendering

**Files:**
- Modify: `app/src/main/java/com/quem/ui/AttachmentEditor.kt`
- Modify: `app/src/main/java/com/quem/ui/EditItemScreen.kt`
- Modify: `app/src/main/java/com/quem/app/MainActivity.kt`
- Modify: `app/src/main/java/com/quem/app/QueMApp.kt`

Context: `AttachmentEditor` gets a new `onAttachLocalFile: () -> Unit` button labelled "Local file". `EditItemScreen` gets a new `onAttachLocalFile: (LocalFileSelection) -> Unit` param and updates the attachment row rendering: `UploadState.IN_PROGRESS` shows a `CircularProgressIndicator`; `UploadState.FAILED` changes the row's title colour to `error` and adds "Retry" to the `⋮` menu. `MainActivity` registers a new `localFileLauncher` and passes it to `SafDrivePickerCoordinator`. `QueMApp` takes persistent URI permission and calls `viewModel.attachAndUploadLocalFile` with a gateway factory.

- [ ] **Step 1: Update MainActivity.kt — register localFileLauncher**

Replace the `drivePickerCoordinator` construction in `MainActivity.onCreate`:

```kotlin
val localFileLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri: Uri? ->
    dependencies.drivePickerRepository.handleLocalFileResult(uri)
}
val drivePickerCoordinator = SafDrivePickerCoordinator(
    fileLauncher      = filePickerLauncher,
    folderLauncher    = folderPickerLauncher,
    localFileLauncher = localFileLauncher,
    drivePickerRepository = dependencies.drivePickerRepository
)
```

- [ ] **Step 2: Update AttachmentEditor.kt — add Local file button**

```kotlin
@Composable
fun AttachmentEditor(
    onAddText: () -> Unit,
    onAddLink: () -> Unit,
    onAttachDriveFile: () -> Unit,
    onAttachDriveFolder: () -> Unit,
    onAttachLocalFile: () -> Unit = {},
    modifier: Modifier = Modifier,
    showDriveActions: Boolean = true
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AttachmentButton(text = "Text",       onClick = onAddText)
        AttachmentButton(text = "Link",       onClick = onAddLink)
        if (showDriveActions) {
            AttachmentButton(text = "Drive file",   onClick = onAttachDriveFile)
            AttachmentButton(text = "Drive folder", onClick = onAttachDriveFolder)
            AttachmentButton(text = "Local file",   onClick = onAttachLocalFile)
        }
    }
}
```

- [ ] **Step 3: Update EditItemScreen.kt — add onAttachLocalFile param**

Open `app/src/main/java/com/quem/ui/EditItemScreen.kt`. Add imports:

```kotlin
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.graphics.Color
import com.quem.drive.LocalFileSelection
```

Add parameter to `EditItemScreen` (after `onAttachDriveFolder`):

```kotlin
onAttachLocalFile: (LocalFileSelection) -> Unit = {},
```

In the `AttachmentEditor` call inside `EditItemScreen`, add:

```kotlin
onAttachLocalFile = { openForm("local_file") },
```

Add a new form type constant (alongside the existing ones):

```kotlin
private const val ATTACHMENT_FORM_LOCAL_FILE = "local_file"
```

Wait — local file doesn't use a text form. It goes directly to the picker. Update the `openForm("local_file")` lambda to not open a text form but instead call the picker. However, `EditItemScreen` doesn't have direct access to the picker — the callback must be wired from `QueMApp`. Use this approach:

Add param `onPickLocalFile: () -> Unit = {}` to `EditItemScreen` and in `AttachmentEditor`:

```kotlin
onAttachLocalFile = onPickLocalFile,
```

This keeps `EditItemScreen` simple — it just delegates to the picker without knowing how it works.

**Updated EditItemScreen signature addition:**

```kotlin
onPickLocalFile: () -> Unit = {},
```

And in the `AttachmentEditor` block:

```kotlin
AttachmentEditor(
    onAddText         = { openForm("text") },
    onAddLink         = { openForm("link") },
    onAttachDriveFile = { openForm("drive_file") },
    onAttachDriveFolder = { openForm("drive_folder") },
    onAttachLocalFile = onPickLocalFile,
    showDriveActions  = true
)
```

**Update attachment row rendering in EditItemScreen** — find the `DeletableRow` block inside `items(attachments)` and replace it:

```kotlin
items(attachments) { attachment ->
    when (attachment.uploadState) {
        UploadState.IN_PROGRESS -> {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = attachment.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
            }
        }
        UploadState.FAILED -> {
            DeletableRow(
                onDelete = { onDeleteAttachment(attachment.id) },
                onRename = { newTitle -> onRenameAttachment(attachment.id, newTitle) },
                currentName = attachment.displayName,
                extraMenuItems = listOf("Retry" to { onRetryUpload(attachment.id) })
            ) {
                Text(
                    text = attachment.displayName,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
        UploadState.NONE -> {
            val url = when {
                attachment.isLink -> attachment.url?.let { if (it.startsWith("http")) it else "https://$it" }
                attachment.isDriveFile || attachment.isDriveFolder ->
                    attachment.driveFileId?.let { "https://drive.google.com/open?id=$it" }
                else -> null
            }
            DeletableRow(
                onDelete = { onDeleteAttachment(attachment.id) },
                onRename = { newTitle -> onRenameAttachment(attachment.id, newTitle) },
                currentName = attachment.displayName
            ) {
                if (url != null) {
                    Text(
                        text = attachment.displayName,
                        modifier = Modifier.fillMaxWidth().clickable { uriHandler.openUri(url) }.padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Text(
                        text = attachment.displayName,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
```

**Important:** `DeletableRow` doesn't currently have `extraMenuItems`. Add an overloaded `extraMenuItems: List<Pair<String, () -> Unit>> = emptyList()` parameter to `DeletableRow` in `ItemDetailScreen.kt` and render them as additional `DropdownMenuItem` entries above the Delete option.

Add to `EditItemScreen` params:

```kotlin
onRetryUpload: (attachmentId: String) -> Unit = {},
```

- [ ] **Step 4: Update DeletableRow in ItemDetailScreen.kt to support extra menu items**

Open `app/src/main/java/com/quem/ui/ItemDetailScreen.kt`. Find the `DeletableRow` composable and update its signature:

```kotlin
@Composable
internal fun DeletableRow(
    onDelete: () -> Unit,
    onRename: ((String) -> Unit)? = null,
    currentName: String = "",
    extraMenuItems: List<Pair<String, () -> Unit>> = emptyList(),
    content: @Composable () -> Unit
)
```

In the `DropdownMenu` block, add the extra items before the Delete item:

```kotlin
DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
    if (onRename != null) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = { menuExpanded = false; renameValue = currentName; showRenameDialog = true }
        )
    }
    extraMenuItems.forEach { (label, action) ->
        DropdownMenuItem(
            text = { Text(label) },
            onClick = { menuExpanded = false; action() }
        )
    }
    DropdownMenuItem(
        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
        onClick = { menuExpanded = false; onDelete() }
    )
}
```

- [ ] **Step 5: Update QueMApp.kt — wire local file in Edit branch**

Open `app/src/main/java/com/quem/app/QueMApp.kt`. In the `is QueMScreen.Edit ->` branch:

**5a.** Add `onPickLocalFile` to `EditItemScreen`:

```kotlin
onPickLocalFile = {
    drivePickerCoordinator.pickLocalFile { selection ->
        if (selection != null) {
            context.contentResolver.takePersistableUriPermission(
                selection.uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val connectedState = driveConnectionState as? DriveConnectionState.Connected
            val accountEmail = connectedState?.account?.email
            if (accountEmail != null) {
                viewModel.attachAndUploadLocalFile(
                    uri             = selection.uri,
                    displayName     = selection.displayName,
                    mimeType        = selection.mimeType,
                    contentResolver = context.contentResolver
                ) {
                    val credential = GoogleAccountCredential
                        .usingOAuth2(context, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
                        .setSelectedAccountName(accountEmail)
                    val drive = Drive.Builder(
                        NetHttpTransport(),
                        GsonFactory.getDefaultInstance(),
                        credential
                    ).setApplicationName("QueM").build()
                    GoogleDriveGateway(drive)
                }
            }
        }
    }
},
onRetryUpload = { attachmentId ->
    val connectedState = driveConnectionState as? DriveConnectionState.Connected
    val accountEmail = connectedState?.account?.email
    if (accountEmail != null) {
        viewModel.retryFileUpload(
            attachmentId    = attachmentId,
            contentResolver = context.contentResolver
        ) {
            val credential = GoogleAccountCredential
                .usingOAuth2(context, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
                .setSelectedAccountName(accountEmail)
            val drive = Drive.Builder(
                NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                credential
            ).setApplicationName("QueM").build()
            GoogleDriveGateway(drive)
        }
    }
},
```

**5b.** Add imports to `QueMApp.kt`:

```kotlin
import com.quem.drive.GoogleDriveGateway
import android.content.Intent
```

(`GoogleDriveAuthorizationCoordinator`, `GoogleAccountCredential`, `NetHttpTransport`, `GsonFactory`, `Drive` are already imported from the share feature.)

- [ ] **Step 6: Verify compilation**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:compileDebugKotlin 2>&1 | tail -15`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run full unit test suite**

Run: `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" && TEMP=/c/tmp ./gradlew :app:test`

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```
git add app/src/main/java/com/quem/ui/AttachmentEditor.kt \
        app/src/main/java/com/quem/ui/EditItemScreen.kt \
        app/src/main/java/com/quem/ui/ItemDetailScreen.kt \
        app/src/main/java/com/quem/app/MainActivity.kt \
        app/src/main/java/com/quem/app/QueMApp.kt
git commit -m "feat: wire local file picker through EditItemScreen, MainActivity, and QueMApp; show upload progress and retry UI"
```
