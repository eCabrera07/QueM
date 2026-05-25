# Drive Authorization Design

Date: 2026-05-25

## Goal

Move QueM from a disconnected Drive scaffold toward real Google Drive access by adding an authorization-first Drive handoff.

This slice should let Settings `Sign in` request Google Drive authorization for the `drive.file` scope, then update QueM's existing Drive connection state when authorization succeeds or fails. It should keep all queue features local-first and usable when authorization is missing, cancelled, or misconfigured.

## Why Authorization First

QueM needs permission to access Google Drive files more than it needs a full account/profile sign-in system. Google's current guidance separates authentication from authorization: Credential Manager is recommended for user sign-in, while Google Identity Services authorization is the path for scoped access to Google APIs such as Drive.

This slice uses the Drive authorization path first and leaves a full Credential Manager sign-in/profile polish slice for later.

## Scope

In scope:

- Add a production Drive connection implementation that uses Google Identity Services authorization APIs.
- Request only this Drive scope:

```text
https://www.googleapis.com/auth/drive.file
```

- Keep the current `DriveConnectionRepository` boundary as the app-facing contract.
- Let `MainActivity` launch authorization resolution UI when Google requires user consent.
- Route Settings `Sign in` through the real Drive connection implementation.
- Update `DriveConnectionState` to:
  - connected when Drive authorization succeeds,
  - disconnected after disconnect,
  - error when authorization is cancelled, unavailable, or misconfigured.
- Preserve local-first behavior: no queue edit should require Drive authorization.
- Keep the current fake picker tests and local Drive attachment reference behavior.
- Add tests around the authorization coordinator/repository using fake clients and launchers.

Out of scope:

- Full Credential Manager sign-in.
- Google profile/avatar polish.
- Real Drive file/folder picker.
- Uploading imported files.
- Downloading or previewing Drive file content.
- Changing Drive sync conflict behavior.
- Requesting broad Drive scopes such as full Drive read/write access.

## What `drive.file` Means

`drive.file` is a limited Google Drive OAuth scope. It allows QueM to access Drive files that the app creates or that the user explicitly selects for the app. It does not grant broad access to browse or read the user's entire Drive.

For QueM, this is the right default because queue metadata and user-selected attachments do not require full Drive access. The tradeoff is that real Drive file/folder attachment must go through a picker or user-selection flow so the app receives access to specific items.

## Architecture

Keep the app-facing contract:

```kotlin
interface DriveConnectionRepository {
    val state: StateFlow<DriveConnectionState>

    fun requestSignIn()

    fun disconnect()
}
```

Replace the production app dependency from `DisconnectedDriveConnectionRepository` to a real implementation that delegates to a small Android-facing authorization coordinator.

Proposed units:

- `DriveAuthorizationCoordinator`
  - Owns Google Identity Services authorization calls.
  - Knows how to request `drive.file`.
  - Emits one of:
    - already authorized with account/token data,
    - resolution required,
    - error.
- `GoogleDriveConnectionRepository`
  - Implements `DriveConnectionRepository`.
  - Calls the coordinator when Settings requests sign-in.
  - Updates `DriveConnectionState`.
  - Exposes a method for `MainActivity` to deliver authorization results.
- `MainActivity`
  - Registers an `ActivityResultLauncher<IntentSenderRequest>`.
  - Gives the launcher callback to the Drive connection repository.
  - Does not contain Drive business logic.

The repository should remain testable without real Google Play Services by depending on a fakeable coordinator interface.

## User Flow

Settings sign-in:

1. User opens Settings.
2. User taps `Sign in`.
3. `QueueViewModel.requestDriveSignIn()` calls `DriveConnectionRepository.requestSignIn()`.
4. The production repository asks Google Identity Services for Drive authorization.
5. If authorization is already granted, QueM moves to `Connected`.
6. If user consent is required, `MainActivity` launches the resolution UI.
7. When the result returns:
   - success moves QueM to `Connected`,
   - cancellation or failure moves QueM to `Error`.

Disconnect:

1. User taps `Disconnect`.
2. Repository clears the local connection state.
3. QueM returns to `Disconnected`.

## Error Handling

- If Google Play Services is unavailable or authorization throws, show a concise Settings error.
- If the user cancels authorization, show an error message such as `Google Drive authorization cancelled`.
- If OAuth configuration is wrong, surface an error state rather than crashing.
- Drive file/folder attach actions remain disabled while disconnected or errored.
- Local text/link attachment flows continue to work.

## Testing

Add unit tests for:

- `GoogleDriveConnectionRepository` moves to connected when the coordinator reports already-authorized.
- It requests resolution launch when consent is needed.
- It moves to connected when a launched resolution succeeds.
- It moves to error when the launched resolution is cancelled or fails.
- `disconnect()` returns to disconnected.
- `QueueViewModel.requestDriveSignIn()` and `disconnectDrive()` still delegate correctly.

Add Compose or app-level tests only where UI behavior changes:

- Settings `Sign in` reaches the repository and can update the account display through a fake repository.
- Authorization errors appear in Settings sync status text.

Final verification should include:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

## Setup Note

The Google Cloud Android OAuth client must use:

```text
Package name: com.quem.app
```

The SHA-1 fingerprint must come from the keystore used to build the installed app. Debug builds use the debug keystore; release builds use the release/upload keystore.
