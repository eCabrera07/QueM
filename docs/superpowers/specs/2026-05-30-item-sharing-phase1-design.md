# Item Sharing — Phase 1 (Share Outbound) — Design Spec

**Date:** 2026-05-30
**Feature:** Allow a user to share a queue item with another person via Google Drive, granting them collaborative access
**Status:** Approved

---

## Context

QueM stores data in Google Drive. Phase 1 covers the sharer's side only: the sharer taps "Share" on an item, enters the recipient's Gmail, and the app creates a per-item Drive file (`QueM/shared-{itemId}.json`) and grants the recipient `"writer"` access via the Drive Permissions API. The recipient gets a standard Drive "shared with you" email. Their QueM app cannot yet automatically import the item — that is Phase 2.

---

## Scope

**In scope:**
- `sharedDriveFileId` and `sharedWith` fields on `QueueItem` + Room migration (v1 → v2)
- `DriveShareGateway` interface + `GoogleDriveShareGateway` implementation (Drive file creation + Permissions API)
- `QueueRepository.shareItem` + `RoomQueueRepository` implementation
- Share button + email dialog on `ItemDetailScreen`; "Shared with" indicator when shared
- `QueueViewModel` share action + error state
- Unit tests for repository; instrumented tests for UI

**Out of scope:**
- Recipient discovering the shared item in QueM (Phase 2)
- Revoking / managing shares
- Sharing with multiple recipients
- Re-sharing after item edits

---

## Architecture

### Data model

**`QueueItem.kt`** — two new fields:

```kotlin
data class QueueItem(
    ...
    val sharedDriveFileId: String?,  // Drive file ID of QueM/shared-{id}.json; null = not shared
    val sharedWith: List<String>     // recipient emails; empty = not shared
)
```

**`QueueItemEntity.kt`** — same two columns:

```kotlin
val sharedDriveFileId: String?,
val sharedWith: List<String>    // stored as JSON by existing Converters.tagsToString/stringToTags
```

`sharedWith` uses the same `List<String>` JSON converter as `tags` — no new `TypeConverter` methods needed.

**`LocalMappers.kt`** — `toDomain()` and `toEntity()` updated to include both new fields.

### Room migration (v1 → v2)

`QueMDatabase.kt` bumped to `version = 2`. Add a `Migration(1, 2)`:

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE queue_items ADD COLUMN sharedDriveFileId TEXT")
        db.execSQL("ALTER TABLE queue_items ADD COLUMN sharedWith TEXT NOT NULL DEFAULT '[]'")
    }
}
```

Passed to `.addMigrations(MIGRATION_1_2)` in `AppDependencies`.

### Drive layer

**`DriveShareGateway` interface** (new — `com.quem.drive`):

```kotlin
interface DriveShareGateway {
    /** Creates or overwrites QueM/shared-{itemId}.json and returns its Drive file ID. */
    suspend fun publishSharedItemFile(itemId: String, content: String): String

    /** Grants the given email writer access to the Drive file. */
    suspend fun grantWriterAccess(fileId: String, recipientEmail: String)
}
```

**`GoogleDriveShareGateway` implementation** (new — `com.quem.drive`):

```kotlin
class GoogleDriveShareGateway(
    private val drive: Drive,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DriveShareGateway {

    override suspend fun publishSharedItemFile(itemId: String, content: String): String =
        withContext(ioDispatcher) {
            val folderId = ensureFolder(QUE_M_FOLDER)
            val fileName = "shared-$itemId.json"
            val existingFile = findFile(folderId, fileName)
            val mediaContent = ByteArrayContent(APPLICATION_JSON,
                content.toByteArray(StandardCharsets.UTF_8))

            if (existingFile == null) {
                val metadata = File()
                    .setName(fileName)
                    .setParents(listOf(folderId))
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_SHARED_ITEM))
                drive.files().create(metadata, mediaContent).setFields("id").execute().id
            } else {
                drive.files().update(existingFile.id, null, mediaContent).setFields("id").execute()
                existingFile.id
            }
        }

    override suspend fun grantWriterAccess(fileId: String, recipientEmail: String) =
        withContext(ioDispatcher) {
            val permission = com.google.api.services.drive.model.Permission()
                .setType("user")
                .setRole("writer")
                .setEmailAddress(recipientEmail)
            drive.permissions().create(fileId, permission)
                .setSendNotificationEmail(true)
                .execute()
            Unit
        }

    // ensureFolder and findFile reuse the same logic as GoogleDriveGateway
    // private companion constants:
    // QUE_M_FOLDER = "QueM", APPLICATION_JSON = "application/json"
    // APP_PROPERTY_ROLE = "quemRole", APP_PROPERTY_SHARED_ITEM = "sharedItem"
}
```

### Repository layer

**`QueueRepository.kt`** — new method:

```kotlin
suspend fun shareItem(itemId: String, recipientEmail: String, shareGateway: DriveShareGateway): Boolean
```

**`RoomQueueRepository.shareItem`** implementation:

```kotlin
override suspend fun shareItem(
    itemId: String,
    recipientEmail: String,
    shareGateway: DriveShareGateway
): Boolean = runCatching {
    // 1. Read item + attachments + history
    val item = dao.observeItem(itemId).first()?.toDomain() ?: return false
    val attachments = dao.observeAttachments(itemId).first().map { it.toDomain() }
    val history = dao.observeHistory(itemId).first().map { it.toDomain() }

    // 2. Build single-item snapshot
    val snapshot = MetadataExporter.export(
        exportedAt  = clock.now().toString(),
        items       = listOf(item.toExportable()),
        attachments = attachments.map { it.toMetadata() },
        history     = history.map { it.toMetadata() }
    )
    val content = MetadataSerializer.encode(snapshot)

    // 3. Publish to Drive and grant access
    val fileId = shareGateway.publishSharedItemFile(itemId, content)
    shareGateway.grantWriterAccess(fileId, recipientEmail)

    // 4. Update local item
    dao.updateShareInfo(
        id                = itemId,
        sharedDriveFileId = fileId,
        sharedWith        = listOf(recipientEmail)
    )
    true
}.getOrElse { false }
```

New DAO method:

```kotlin
@Query("""
    UPDATE queue_items
    SET sharedDriveFileId = :sharedDriveFileId,
        sharedWith        = :sharedWith
    WHERE id = :id
""")
suspend fun updateShareInfo(
    id: String,
    sharedDriveFileId: String,
    sharedWith: List<String>
)
```

### ViewModel layer

**`QueueItemDetailUi`** — two new fields:
```kotlin
val sharedWith: List<String>       // empty = not shared
val sharedDriveFileId: String?
```

**New ViewModel state + actions:**

```kotlin
private const val KEY_IS_SHOWING_SHARE_DIALOG = "isShowingShareDialog"

val isShowingShareDialog: StateFlow<Boolean> =
    savedStateHandle.getStateFlow(KEY_IS_SHOWING_SHARE_DIALOG, false)

private val _shareError = MutableStateFlow<String?>(null)
val shareError: StateFlow<String?> = _shareError.asStateFlow()

fun showShareDialog() {
    savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = true
}

fun closeShareDialog() {
    savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = false
    _shareError.value = null
}

fun shareItem(recipientEmail: String, shareGateway: DriveShareGateway) {
    val id = selectedItemId.value ?: return
    viewModelScope.launch {
        val success = repository.shareItem(id, recipientEmail, shareGateway)
        if (success) {
            savedStateHandle[KEY_IS_SHOWING_SHARE_DIALOG] = false
        } else {
            _shareError.value = "Could not share item. Check the email and try again."
        }
    }
}
```

`shareItem` receives `shareGateway: DriveShareGateway` from the call site (`QueMApp.kt`) which builds it from stored credentials — the same pattern `SyncWorker` uses.

**`toDetailUi`** updated to map both new fields.

### UI layer

**`ItemDetailScreen.kt`** — changes:
- Add `sharedWith: List<String> = emptyList()` and `onShare: () -> Unit = {}` params
- Top row becomes: `Back` | `Edit` | `Share` (three `TextButton`s)
- When `sharedWith.isNotEmpty()`: show `"Shared with ${sharedWith.first()}"` below the sync indicator
- An `AlertDialog` controlled by `isShowingShareDialog`:
  - `OutlinedTextField` for email input (`label = "Recipient email"`)
  - Share button enabled when `email.contains("@")`
  - On confirm: call `onShareConfirm(email)`
  - On dismiss: call `onShareDialogDismiss()`
  - If `shareError != null`: show error text below the field

**`QueMApp.kt`** — collect `isShowingShareDialog`, `shareError`; build `DriveShareGateway` from stored credentials when `onShareConfirm(email)` is triggered; pass all to `ItemDetailScreen`.

Building the gateway in `QueMApp.kt` (composable, has `LocalContext.current`):
```kotlin
onShareConfirm = { email ->
    val deps = (context.applicationContext as QueMApplication).dependencies
    val accountEmail = deps.driveAccountPreferences.load()
    if (accountEmail != null) {
        val credential = GoogleAccountCredential
            .usingOAuth2(context, listOf(GoogleDriveAuthorizationCoordinator.DRIVE_FILE_SCOPE))
            .setSelectedAccountName(accountEmail)
        val drive = Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), credential)
            .setApplicationName("QueM").build()
        viewModel.shareItem(email, GoogleDriveShareGateway(drive))
    }
}
```

---

## Error Handling

- Drive API failure → `shareItem` returns `false` → `shareError` set → displayed in dialog
- User not signed in to Drive → `driveAccountPreferences.load()` is null → `onShareConfirm` does not call `shareItem`; show `"Sign in to Google Drive to share items"` in dialog
- DAO `updateShareInfo` failure → `shareItem` returns `false` (caught by outer `runCatching`)

---

## Testing

### Unit tests — `RoomQueueRepositoryTest`
- `shareItemCreatesFileAndGrantsAccessWithCorrectContent` — fake `DriveShareGateway` captures calls; verify `publishSharedItemFile` called with correct item content and `grantWriterAccess` called with correct email
- `shareItemUpdatesLocalSharedDriveFileIdAndSharedWith` — after `shareItem`, DAO reflects `sharedDriveFileId` and `sharedWith`
- `shareItemReturnsFalseWhenGatewayThrows` — gateway throws; returns `false`; local item unchanged

### Unit tests — `QueueViewModelTest`
- `selectedItemIncludesSharedWithAndSharedDriveFileId` — item with `sharedWith = ["a@b.com"]` → `selectedItem.sharedWith == ["a@b.com"]`

### Instrumented tests — `ItemDetailScreenTest`
- `shareButtonDisplayed` — `onShare` callback visible
- `shareDialogShowsWhenTriggered` — `isShowingShareDialog = true` → dialog visible
- `shareButtonInDialogDisabledForBlankEmail` — verify disabled state

---

## What Comes Next

- **Phase 2 — Receive shared item**: Recipient's app queries Drive `sharedWithMe` for files with `appProperty quemRole = sharedItem`, imports them into local DB
- **Phase 3 — Bidirectional sync**: Both sharer and recipient's sync cycle reads/writes the per-item shared file
