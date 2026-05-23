# QueM Android Drive Queue Design

Date: 2026-05-23

## Goal

QueM is a native Android app for managing a personal queue of work items backed by Google Drive. Users create queue items that represent projects or tasks, attach relevant text, links, Drive files, and Drive folders, then move each item through `Queued`, `In Progress`, `Done`, and `Dismissed`.

Done and dismissed items are retained. Done items represent completed work. Dismissed items represent work that is cancelled or no longer relevant, and they are removed from the active queue without being deleted.

## First-Version Decisions

- Platform: native Android with Kotlin and Jetpack Compose.
- Storage model: local-first with Drive sync.
- Local source during app use: Room database.
- Cloud persistence: Google Drive metadata file plus references to Drive files/folders.
- Drive access: default app folder plus optional selection of existing Drive files/folders.
- Queue item model: each item is a task/project with multiple attachments.
- Item tracking: title, optional description, status, optional priority, optional due date, optional tags, notes/history, timestamps, and attachments.

## User Experience

The app opens to the queue list. Users can switch between `Queued`, `In Progress`, `Done`, and `Dismissed`, search/filter items, and create a new item. Queue cards show the item title, status, priority if set, due date if set, tags, attachment count, and sync state.

The item detail screen shows the complete item. Users can change status, edit metadata, add notes/history, and attach content. Attachments can be plain text, links, existing Drive files, existing Drive folders, or files created/imported into the app folder.

The Done archive is a durable completed-work view. The Dismissed archive keeps cancelled or no-longer-relevant items out of the queue while preserving their metadata, notes, and attachments. Marking an item done or dismissed does not delete the item.

Settings show the signed-in Google account, sync state, last synced timestamp, manual sync action, and disconnect/sign-out controls.

## Screens

- Queue List: main status-filtered list, search/filter, item creation, sync status.
- Item Detail: full item metadata, status control, attachments, notes/history.
- Create/Edit Item: title, description, status, priority, optional due date, and tags.
- Drive Attachment Picker: app folder access and existing Drive file/folder selection.
- Done Archive: completed items with search and reopen support.
- Dismissed Archive: cancelled or irrelevant items with search and restore support.
- Settings: account, sync status, Drive permissions, manual sync, disconnect.

## Data Model

### QueueItem

- `id`: stable local UUID.
- `driveId`: optional remote identifier if represented in synced metadata.
- `title`: required text.
- `description`: optional text.
- `status`: `QUEUED`, `IN_PROGRESS`, `DONE`, or `DISMISSED`.
- `priority`: optional enum such as `LOW`, `MEDIUM`, `HIGH`.
- `dueDate`: optional date.
- `tags`: zero or more labels.
- `createdAt`: timestamp.
- `updatedAt`: timestamp.
- `completedAt`: optional timestamp set when status becomes `DONE`.
- `dismissedAt`: optional timestamp set when status becomes `DISMISSED`.
- `syncState`: `SYNCED`, `PENDING_SYNC`, `SYNCING`, or `ERROR`.

### Attachment

- `id`: stable local UUID.
- `queueItemId`: owning queue item.
- `type`: `TEXT`, `LINK`, `DRIVE_FILE`, or `DRIVE_FOLDER`.
- `displayName`: required text.
- `textContent`: optional text for text attachments.
- `url`: optional URL for link attachments.
- `driveFileId`: optional Drive file/folder ID.
- `mimeType`: optional MIME type from Drive.
- `createdAt`: timestamp.
- `updatedAt`: timestamp.
- `syncState`: sync state for attachment metadata.

### HistoryEntry

- `id`: stable local UUID.
- `queueItemId`: owning queue item.
- `message`: human-readable event or note text.
- `kind`: `NOTE`, `STATUS_CHANGE`, `ATTACHMENT_ADDED`, `ATTACHMENT_REMOVED`, or `EDIT`.
- `createdAt`: timestamp.

## Architecture

The app uses a layered Android architecture:

- UI: Jetpack Compose screens and navigation.
- Domain: use cases for queue item creation, edits, status transitions, attachment handling, notes/history, search/filter, and sync marking.
- Data: Room entities/DAOs and repositories exposing Flows to the UI.
- Drive Sync: Google sign-in, Drive API credentials, app folder discovery/creation, metadata upload/download, and attachment lookups.
- Background Work: WorkManager for periodic sync and one-off manual sync.

The UI never waits on Google Drive for normal queue edits. User actions write to Room first and mark affected records as pending sync. Drive sync catches up in the background or when manually triggered.

## Google Drive Storage

The app creates or discovers this Drive structure:

```text
/QueM/
  queue-metadata.json
  Created Files/
```

`queue-metadata.json` stores queue item metadata, attachment metadata, history entries, and sync version information. It does not duplicate external Drive files. Existing Drive files/folders are referenced by Drive ID.

Files the user creates or imports through the app can be placed in `/QueM/Created Files/`. Files and folders selected elsewhere in Drive remain in their original location.

## Sync Behavior

Local changes mark records as `PENDING_SYNC`. WorkManager periodically syncs pending local changes to `queue-metadata.json`. Manual sync is available from Settings.

On startup and after sign-in, the app can download remote metadata and reconcile it with local data. For version one, conflict resolution is last-write-wins at the queue item level, using `updatedAt` timestamps. If remote data is newer, it updates the local item. If local data is newer, it remains pending sync and is uploaded.

The app shows last sync time and per-item sync state when useful.

## Error Handling

- If the user is not signed in, local queue management remains available, but Drive sync and Drive browsing require sign-in.
- If the device is offline, edits are saved locally and synced later.
- If Drive permission expires, local data remains intact and the user is prompted to reconnect.
- If a linked Drive file/folder is deleted or inaccessible, the attachment remains but shows an unavailable state.
- If metadata sync fails, the app records the error state and retries through WorkManager.

## First-Version Scope

Included:

- Native Android app in Kotlin and Jetpack Compose.
- Room-backed local queue storage.
- Queue lifecycle: `Queued`, `In Progress`, `Done`, `Dismissed`.
- Create and edit queue items.
- Optional priority, optional due date, and optional tags.
- Notes/history.
- Text attachments.
- Link attachments.
- Google sign-in.
- Google Drive app folder creation/discovery.
- Existing Drive file/folder selection.
- Attachment references by Drive ID.
- Metadata sync to Google Drive.
- Done and dismissed archives with search.
- Settings with account, sync state, manual sync, and disconnect.

Deferred:

- Multi-user collaboration.
- Custom user-defined fields.
- Notifications and reminders.
- OCR or content extraction from files.
- Advanced conflict review.
- Sharing queue items outside the signed-in user's Drive account.

## Testing Strategy

- Unit tests for status transitions, queue item validation, and sync marking.
- Room DAO tests for queue items, attachments, history entries, and search/filter queries.
- Serialization tests for `queue-metadata.json`.
- Repository tests that verify local-first writes and Flow updates.
- Sync manager tests using fake Drive API clients.
- WorkManager tests for retry and offline behavior.
- Compose UI tests for creating an item, editing metadata, adding attachments, moving status, and finding done or dismissed items.

## Open Implementation Notes

- Use Google Identity Services for sign-in and OAuth consent.
- Request the minimum Drive scopes that support app folder access and user-selected Drive files/folders.
- Keep Drive API calls behind an interface so sync logic can be tested without network access.
- Use stable UUIDs locally so items can be created offline before Drive sync exists.
