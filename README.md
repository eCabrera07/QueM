# QueM

QueM is a native Android queue manager for keeping track of work that should not disappear into scattered notes, browser tabs, chats, or Drive folders. It is being built as a local-first Kotlin app with Google Drive as the long-term sync and attachment backbone.

The core idea is simple: capture a project, task, or follow-up as a queue item, attach the material that explains it, and move it through a clear lifecycle until it is done or intentionally dismissed.

## What the app does today

- Shows queue items by status: `Queued`, `In Progress`, `Done`, and `Dismissed`.
- Persists queue data locally with Room instead of sample in-memory state.
- Creates new queue items with a title, optional description, optional priority, and optional ISO due date.
- Opens item detail screens with description, due date, attachments, and history sections.
- Moves selected items to `Done` or `Dismissed` while retaining them in their archive status.
- Adds local text and link attachments to an item.
- Shows attachment counts in the list and attachment names in the detail view.
- Provides a settings screen for account and sync state.
- Includes scaffolding for Google Drive connection state and Drive file/folder attachment references.
- Serializes queue metadata for a future `queue-metadata.json` sync file in Drive.

## What it will do

QueM is moving toward a complete personal work queue backed by Google Drive:

- Sign in with Google and connect the app to the user's Drive account.
- Create or discover a `/QueM/` Drive folder.
- Sync queue metadata through `/QueM/queue-metadata.json`.
- Attach existing Drive files and folders to queue items by Drive ID.
- Support manual and background sync through WorkManager.
- Reconcile local and remote metadata while keeping offline edits usable.
- Add richer editing for item metadata, tags, notes, and history.
- Make Done and Dismissed archives searchable and restorable.
- Surface per-item sync state, last-sync time, and recoverable Drive errors.
- Eventually support files created or imported through the app in `/QueM/Created Files/`.

## Architecture

QueM is organized around a local-first Android stack:

- **UI:** Jetpack Compose screens for queue list, create item, item detail, attachments, and settings.
- **State:** `QueueViewModel` maps repository flows into UI state and owns screen selection.
- **Data:** Room entities, DAO queries, and `RoomQueueRepository` provide persisted queue state.
- **Domain:** Queue status, priority, attachments, history entries, and sync-state models live under `core`.
- **Drive boundary:** Drive connection, Drive selections, and metadata sync are isolated behind small interfaces so the app remains testable before real OAuth credentials are wired in.
- **Sync:** `SyncManager`, `MetadataSerializer`, and `MetadataExporter` define the shape of the Drive metadata sync path.

## Current status

This repository is in active early development. Local queue management is usable, while real Google sign-in, Drive picking, and production sync are still being wired up. The Android application id is:

```text
com.quem.app
```

Future Google Cloud OAuth credentials should use that package name unless the app id changes intentionally.

## Build and test

From the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
```

Run the JVM test suite:

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

Run connected Android tests with an emulator or device attached:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```
