# Drive Scaffolding Design

Date: 2026-05-25

## Goal

Prepare QueM for Google Drive sign-in, Drive file selection, and Drive folder selection without blocking on Google Cloud OAuth credentials yet.

This slice should make the app architecture and UI ready for real Drive integration while keeping all behavior testable with fakes. Local queue item creation, text attachments, link attachments, and status tracking must continue to work without a signed-in Google account.

## Current State

The app already has useful Drive foundations:

- `GoogleAuthClient` creates Google sign-in options with the Drive file scope.
- `GoogleDriveGateway` can upload/download the QueM metadata JSON through the Drive API client.
- `DriveGateway` isolates metadata sync calls behind an interface.
- `SyncManager` reads and writes `queue-metadata.json` in the `QueM` Drive folder.
- `SettingsScreen` can display an account email, sync status, manual sync, and disconnect controls.
- `QueueRepository` and `RoomQueueRepository` already support `addDriveAttachment` for Drive file/folder references.
- `ItemDetailScreen` currently hides Drive attachment actions until a Drive picker slice exists.

## Scope

In scope:

- Add a small Drive account/connection boundary that can be backed by fake data now and Google auth later.
- Add UI state for whether Drive is connected, disconnected, or unavailable.
- Expose Drive file/folder attachment actions from item detail without requiring real Google credentials.
- Route Drive file/folder actions through ViewModel callbacks that can no-op or surface a sign-in-required state while disconnected.
- Add test fakes that can simulate a signed-in Drive account and selected Drive file/folder references.
- Keep the existing local-first model: attaching Drive references writes local metadata first.
- Keep the Google OAuth package name documented as `com.quem.app`, matching the current Android `applicationId`.

Out of scope:

- Launching the real Google sign-in intent.
- Creating Google Cloud OAuth credentials.
- Implementing the real Android Drive picker or system document picker.
- Requesting new Drive scopes beyond the existing `drive.file` scope.
- Uploading arbitrary user files into Drive.
- Downloading or previewing Drive file content.
- Conflict resolution changes.
- SyncWorker production wiring beyond the existing stub behavior.

## Recommended Architecture

Introduce a narrow connection-facing interface in the Drive layer:

```kotlin
data class DriveAccount(
    val email: String
)

sealed interface DriveConnectionState {
    data object Disconnected : DriveConnectionState
    data class Connected(val account: DriveAccount) : DriveConnectionState
    data class Error(val message: String) : DriveConnectionState
}

interface DriveConnectionRepository {
    val state: StateFlow<DriveConnectionState>

    fun requestSignIn()

    fun disconnect()
}
```

This is deliberately not the final Google sign-in implementation. It is the app-facing contract that lets UI and ViewModels react to Drive availability. The initial production implementation can be disconnected-only or backed by `GoogleAuthClient.lastSignedInAccount()`. Tests can use a fake connected implementation.

Add a Drive picker-facing model that represents the output QueM needs, not the mechanics of choosing it:

```kotlin
data class DriveSelection(
    val id: String,
    val name: String,
    val mimeType: String?,
    val isFolder: Boolean
)
```

The real picker will later produce `DriveSelection`. For this slice, fake picker callbacks can produce the same type in tests.

## UI Behavior

Item detail should show all attachment actions:

- `Text`
- `Link`
- `Drive file`
- `Drive folder`

When Drive is disconnected:

- Text and Link continue to open their existing inline forms.
- Drive file and Drive folder actions do not create attachments.
- The detail screen should surface a short state message such as `Sign in to Google Drive to attach files`.

When Drive is connected:

- Drive file and Drive folder actions call the app-level picker callbacks.
- The selected Drive reference is saved through `QueueViewModel` to `QueueRepository.addDriveAttachment`.
- The attachment list updates through the existing repository observation flow.

Settings should use the connection boundary:

- Connected: show the account email and allow Disconnect.
- Disconnected: show `Not signed in` and offer a sign-in action.
- Error: show a concise error message while keeping local queue use available.

## ViewModel Behavior

`QueueViewModel` should gain Drive attachment methods:

- `addDriveFileAttachment(title, driveFileId, mimeType)`
- `addDriveFolderAttachment(title, driveFolderId)`

Both methods should guard against no selected item, matching the text/link attachment methods.

The ViewModel should also expose enough Drive state for the item detail screen to decide whether Drive actions are available and what message to show when unavailable. The exact state class can be lightweight and UI-focused.

## Data Flow

Disconnected Drive action:

1. User taps `Drive file` or `Drive folder`.
2. UI checks Drive connection state.
3. UI shows sign-in-required message.
4. No repository write occurs.

Connected Drive action:

1. User taps `Drive file` or `Drive folder`.
2. App-level picker callback returns a `DriveSelection`.
3. `QueueViewModel` saves the selection through `QueueRepository.addDriveAttachment`.
4. Room emits the new attachment.
5. Detail and list screens refresh from existing observation.

## Error Handling

- If Drive is disconnected, Drive actions must not crash and must not mutate local attachments.
- If a picker returns no selection, no local attachment is created.
- If a Drive selection has a blank id or blank title, repository validation should no-op.
- Local queue and local text/link attachment flows remain available even when Drive is disconnected or errored.

## Testing

Add focused tests for:

- Drive connection state mapping to Settings UI text/actions.
- Item detail shows Drive actions when requested.
- Disconnected Drive actions show the sign-in-required message and do not call picker callbacks.
- Connected Drive file action can save a fake Drive file attachment.
- Connected Drive folder action can save a fake Drive folder attachment.
- `QueueViewModel` Drive file/folder add methods no-op without a selected item.
- `QueueViewModel` Drive file/folder add methods append display names for the selected item.

Final verification should include:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

## OAuth Note

The current Android application id is:

```text
com.quem.app
```

The future Google Cloud Android OAuth client must use this package name unless the app id is intentionally changed before credential setup.
