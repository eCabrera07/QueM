# Local File Upload — Design Spec

**Date:** 2026-06-02
**Feature:** Attach a file from phone storage to a queue item; upload it to Google Drive under `QueM/files/{itemId}/`
**Status:** Approved

---

## Context

QueM already supports Drive file attachments (via URL paste) and text/link attachments. This feature adds the ability to pick any file from the device's local storage, upload it to a per-item folder in the user's Drive, and store a link to the uploaded file as a `DRIVE_FILE` attachment. The original file stays on the phone; QueM keeps only the Drive copy reference.

---

## Scope

**In scope:**
- SAF file picker for local storage (any MIME type)
- Streaming upload to `QueM/files/{itemId}/filename` in the user's Drive
- Immediate UI feedback: attachment appears right away with an upload progress indicator
- Retry button on failed uploads (tap ⋮ → Retry)
- Persistent URI permission so retries work after app restart

**Out of scope:**
- Upload progress percentage (spinner only, no bytes-transferred counter)
- Multiple file selection in one pick
- Background upload while app is closed (WorkManager retry)
- Uploading to Drive folders other than `QueM/files/{itemId}/`

---

## Architecture

### Data model changes

**`AttachmentType`** — new value:
```kotlin
LOCAL_FILE   // picked from device, upload pending or failed
```

**`SyncState`** — two new values:
```kotlin
PENDING_UPLOAD   // attachment stored, upload not yet attempted
UPLOAD_FAILED    // upload attempted and failed; url field holds the URI for retry
```

No new DB columns. Existing fields carry the new states:
- `url: String?` stores the persistent content URI (e.g. `content://...`) for `LOCAL_FILE` attachments
- `driveFileId: String?` is null until upload succeeds, then holds the Drive file ID
- `syncState` tracks upload lifecycle

**Room migration:** no schema change — `AttachmentType` and `SyncState` are stored as strings, so new enum values are forward-compatible.

**Attachment lifecycle:**
```
Pick file
  → attachLocalFile()
  → AttachmentEntity(type=LOCAL_FILE, syncState=PENDING_UPLOAD, url=contentUri, driveFileId=null)
  → uploadPendingFile() starts
  → [transient in-memory: uploading=true for this attachmentId]
  → success → (type=DRIVE_FILE, syncState=SYNCED, driveFileId=driveId, url=null)
  → failure → (type=LOCAL_FILE, syncState=UPLOAD_FAILED, url=contentUri preserved)
```

Retry is identical to first upload — `uploadPendingFile(attachmentId, gateway)` is called again.

The existing sync cycle (`SyncCoordinator`) skips `LOCAL_FILE` attachments entirely — they are not included in the metadata snapshot until they become `DRIVE_FILE`.

---

### Drive layer

**New interface: `DriveFileUploadGateway`**

```kotlin
interface DriveFileUploadGateway {
    /**
     * Ensures `QueM/files/{itemId}/` exists in Drive and uploads the file.
     * Returns the Drive file ID of the uploaded file.
     */
    suspend fun uploadLocalFile(
        itemId: String,
        fileName: String,
        mimeType: String,
        contentResolver: ContentResolver,
        uri: android.net.Uri
    ): String
}
```

**`GoogleDriveGateway`** implements both `DriveGateway` (existing) and `DriveFileUploadGateway` (new). The new `uploadLocalFile` method:
1. Calls `ensureFolder("QueM")` (existing helper) to get the root folder ID
2. Calls a new `ensureSubfolder(parentId, "files")` to get `QueM/files/`
3. Calls `ensureSubfolder(filesId, itemId)` to get `QueM/files/{itemId}/`
4. Opens `contentResolver.openInputStream(uri)` and wraps in `InputStreamContent(mimeType, inputStream)`
5. Calls `drive.files().create(metadata, inputStreamContent).setFields("id").execute()` and returns the file ID

Uses `InputStreamContent` (not `ByteArrayContent`) to stream the file without loading it into memory — required for large files (photos, videos, PDFs).

The `QueM/files/` and per-item subfolders are tagged with `appProperty quemRole=itemFilesFolder` for discoverability.

**Persistent URI permission** is taken in `QueMApp` before calling `viewModel.attachAndUploadLocalFile`, since the ViewModel has no `Context`:
```kotlin
context.contentResolver.takePersistableUriPermission(
    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
)
```
This allows retries after the activity is gone.

---

### Repository layer

**`QueueRepository`** — three new methods:

```kotlin
/** Immediately stores a LOCAL_FILE / PENDING_UPLOAD attachment. Returns the new attachmentId. */
suspend fun attachLocalFile(
    queueItemId: String,
    uri: String,
    displayName: String,
    mimeType: String?
): String

/** Uploads the file referenced by the attachment to Drive. Returns true on success. */
suspend fun uploadPendingFile(
    attachmentId: String,
    gateway: DriveFileUploadGateway
): Boolean

/** Identical to uploadPendingFile — retries a UPLOAD_FAILED attachment. */
suspend fun retryFileUpload(
    attachmentId: String,
    gateway: DriveFileUploadGateway
): Boolean
```

**`RoomQueueRepository.attachLocalFile`** implementation:
- Creates `AttachmentEntity` with `type = LOCAL_FILE`, `syncState = PENDING_UPLOAD`, `url = uri`, `driveFileId = null`
- Returns the new attachment ID

**`RoomQueueRepository.uploadPendingFile`** implementation:
```
1. Read attachment from DB (observeAttachment(id).first())
2. Validate: type must be LOCAL_FILE, syncState must be PENDING_UPLOAD or UPLOAD_FAILED
3. Call gateway.uploadLocalFile(queueItemId, displayName, mimeType, contentResolver, Uri.parse(url))
4. On success: dao.updateAttachmentAfterUpload(id, driveFileId, updatedAt)
   — sets type=DRIVE_FILE, driveFileId=result, syncState=SYNCED, url=null
5. On failure: dao.updateAttachmentUploadFailed(id, updatedAt)
   — sets syncState=UPLOAD_FAILED; url preserved for retry
6. Returns true on success, false on failure
```

`retryFileUpload` delegates to `uploadPendingFile` — same logic.

**New DAO methods:**
```kotlin
@Query("UPDATE attachments SET type=:type, driveFileId=:driveFileId, syncState=:syncState, url=NULL, updatedAt=:updatedAt WHERE id=:id")
suspend fun updateAttachmentAfterUpload(id, type, driveFileId, syncState, updatedAt)

@Query("UPDATE attachments SET syncState='UPLOAD_FAILED', updatedAt=:updatedAt WHERE id=:id")
suspend fun updateAttachmentUploadFailed(id, updatedAt)
```

---

### ViewModel layer

**`QueueViewModel`** — new actions:

```kotlin
fun attachAndUploadLocalFile(uri: Uri, displayName: String, mimeType: String?) {
    val itemId = selectedItemId.value ?: return
    viewModelScope.launch {
        val attachmentId = repository.attachLocalFile(itemId, uri.toString(), displayName, mimeType)
        _uploadingAttachmentIds.update { it + attachmentId }
        try {
            repository.uploadPendingFile(attachmentId, buildGateway())
        } finally {
            _uploadingAttachmentIds.update { it - attachmentId }
        }
    }
}

fun retryFileUpload(attachmentId: String) {
    viewModelScope.launch {
        _uploadingAttachmentIds.update { it + attachmentId }
        try {
            repository.retryFileUpload(attachmentId, buildGateway())
        } finally {
            _uploadingAttachmentIds.update { it - attachmentId }
        }
    }
}
```

`_uploadingAttachmentIds: MutableStateFlow<Set<String>>` tracks in-progress uploads in memory (not persisted). `buildGateway()` follows the existing `shareItem` factory pattern — `QueMApp` passes it as a lambda executed on `Dispatchers.IO`.

**`AttachmentUi`** gains:
```kotlin
val uploadState: UploadState  // NONE, IN_PROGRESS, FAILED
```

```kotlin
enum class UploadState { NONE, IN_PROGRESS, FAILED }
```

`toAttachmentUi()` maps `syncState`:
- `UPLOAD_FAILED` → `UploadState.FAILED`
- Any other → `UploadState.NONE` (in-progress state is set by the ViewModel via `_uploadingAttachmentIds`)

The ViewModel combines `selectedItem` flow with `_uploadingAttachmentIds` to produce `AttachmentUi.uploadState = IN_PROGRESS` for currently-uploading attachments.

---

### UI layer

**`AttachmentEditor`** — add "Local file" button. The existing `fileLauncher` (SAF `OpenDocument`) in `MainActivity` is reused. The result is routed through a new `onPickLocalFile` callback on `AttachmentEditor` instead of the existing `onAttachDriveFile` callback.

**`EditItemScreen`** — passes `onPickLocalFile` to `AttachmentEditor`. The callback launches the file picker via `drivePickerCoordinator.pickLocalFile { result → viewModel.attachAndUploadLocalFile(...) }`.

`SafDrivePickerCoordinator` gains `pickLocalFile(onResult: (LocalFileSelection?) -> Unit)` where `LocalFileSelection` holds `uri: Uri`, `displayName: String`, `mimeType: String?`.

**Attachment row rendering** based on `AttachmentUi.uploadState`:

| State | Appearance |
|---|---|
| `NONE` | Existing: tappable link (Drive files) or plain text |
| `IN_PROGRESS` | Filename + `CircularProgressIndicator` (small, trailing), not tappable |
| `FAILED` | Filename in `error` colour + ⋮ menu shows **Retry** alongside Rename/Delete |

---

## Error Handling

- **Upload failure** → `syncState = UPLOAD_FAILED`, error colour on attachment row, Retry in menu
- **URI no longer accessible** (file deleted from phone) → `uploadPendingFile` catches `FileNotFoundException`, updates to `UPLOAD_FAILED` with a note; user can delete the attachment
- **Drive not connected** → the "Local file" button in `AttachmentEditor` is disabled (same as existing Drive actions) when Drive is not connected; the sign-in gate prevents reaching this screen without being connected anyway
- **ContentResolver returns null stream** → treated as failure, `UPLOAD_FAILED`

---

## Testing

### Unit tests — `RoomQueueRepositoryTest`
- `attachLocalFileCreatesLocalFileAttachmentWithPendingUploadState`
- `uploadPendingFileOnSuccessTransitionsToDriveFileAndClearsUri`
- `uploadPendingFileOnFailureTransitionsToUploadFailed`
- `retryFileUploadDelegatesToUploadPendingFile`

### Unit tests — `QueueViewModelTest`
- `attachAndUploadLocalFileSetsInProgressThenClearsOnSuccess`
- `retryFileUploadSetsInProgressForFailedAttachment`
- `attachmentUiUploadStateIsFailedWhenSyncStateIsUploadFailed`

### Instrumented tests — `EditItemScreenTest`
- `localFileButtonDisplayed`
- `uploadInProgressShowsSpinner`
- `uploadFailedShowsRetryOption`
