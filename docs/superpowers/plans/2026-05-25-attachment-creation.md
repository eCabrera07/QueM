# Attachment Creation Implementation Plan

> Spec: `docs/superpowers/specs/2026-05-25-attachment-creation-design.md`
> Approved: 2026-05-25

## Goal

Allow users to add text and link attachments to an existing queue item from the item detail view. The saved attachments should flow through the existing repository, update the detail screen immediately via observation, and remain part of the item after the user navigates away.

Drive file and folder attachment creation remain out of scope for this pass.

## Current Architecture

- `QueueRepository` already exposes:
  - `addTextAttachment(queueItemId, title, text)`
  - `addLinkAttachment(queueItemId, title, url)`
  - `observeAttachments(queueItemId)`
- `RoomQueueRepository` already persists text/link attachments and no-ops when the parent item is missing or required fields are blank.
- `QueueViewModel` tracks the selected item id in `SavedStateHandle`, observes selected item attachments, but does not yet expose add attachment actions.
- `ItemDetailScreen` renders attachment display names but has no attachment creation controls.
- `AttachmentEditor` renders four action buttons: Text, Link, Drive file, Drive folder.
- `QueMApp` wires detail actions for Done, Dismiss, and Back only.

## Implementation Tasks

### 1. Add ViewModel Attachment Actions

Files:
- `app/src/main/java/com/example/quem/queue/QueueViewModel.kt`
- `app/src/test/java/com/example/quem/queue/QueueViewModelTest.kt`

Add public methods to `QueueViewModel`:

```kotlin
fun addTextAttachment(title: String, text: String) {
    val queueItemId = selectedItemId.value ?: return
    viewModelScope.launch {
        repository.addTextAttachment(queueItemId, title, text)
    }
}

fun addLinkAttachment(title: String, url: String) {
    val queueItemId = selectedItemId.value ?: return
    viewModelScope.launch {
        repository.addLinkAttachment(queueItemId, title, url)
    }
}
```

Keep validation in the repository layer for this pass. The ViewModel should only guard against no selected item.

Add unit tests:

- Selecting an item and calling `addTextAttachment("Note", "Body")` causes `selectedItem.attachments` to include `"Note"`.
- Selecting an item and calling `addLinkAttachment("Reference", "https://example.com")` causes `selectedItem.attachments` to include `"Reference"`.
- Calling both methods without a selected item does not throw and does not mutate repository state.

### 2. Let AttachmentEditor Hide Drive Actions

Files:
- `app/src/main/java/com/example/quem/queue/AttachmentEditor.kt`
- `app/src/androidTest/java/com/example/quem/queue/AttachmentEditorTest.kt`

Extend `AttachmentEditor` with a defaulted parameter:

```kotlin
showDriveActions: Boolean = true
```

Render Drive file and Drive folder buttons only when `showDriveActions` is `true`.

Keep the default as `true` so existing callers and tests keep their current behavior.

Add a Compose test for local-only mode:

- Render `AttachmentEditor(showDriveActions = false, ...)`.
- Assert Text and Link exist.
- Assert Drive file and Drive folder do not exist.

### 3. Add Inline Forms To ItemDetailScreen

Files:
- `app/src/main/java/com/example/quem/queue/ItemDetailScreen.kt`
- `app/src/androidTest/java/com/example/quem/queue/ItemDetailScreenTest.kt`

Extend `ItemDetailScreen` parameters:

```kotlin
onAddTextAttachment: (title: String, text: String) -> Unit = { _, _ -> },
onAddLinkAttachment: (title: String, url: String) -> Unit = { _, _ -> },
```

Add local state for the active form:

```kotlin
var attachmentFormType by rememberSaveable { mutableStateOf<String?>(null) }
var attachmentTitle by rememberSaveable { mutableStateOf("") }
var attachmentValue by rememberSaveable { mutableStateOf("") }
```

Use string constants such as `"text"` and `"link"` to keep `rememberSaveable` simple.

Render `AttachmentEditor(showDriveActions = false, ...)` near the Attachments section:

- Text opens the text form.
- Link opens the link form.
- Drive callbacks can remain no-op because the buttons are hidden.

When a form opens:

- Reset `attachmentTitle`.
- Reset `attachmentValue`.
- Set `attachmentFormType`.

Render a compact inline form under the attachment actions when `attachmentFormType != null`:

- `OutlinedTextField` for title with label `Attachment title`.
- `OutlinedTextField` for body with label:
  - `Text` for text attachments.
  - `URL` for link attachments.
- `Save` button.
- `Cancel` button.

On Save:

- Trim title and body/url.
- Call the matching callback with the trimmed values.
- Clear state and close the form.

On Cancel:

- Clear state and close the form without calling the callback.

Keep visible validation errors out of scope. Blank values should flow through to repository no-op behavior.

Add Compose tests:

- Text form:
  - Tap Text.
  - Enter `Note` into `Attachment title`.
  - Enter `Remember this` into `Text`.
  - Tap Save.
  - Assert callback receives `Note` and `Remember this`.
- Link form:
  - Tap Link.
  - Enter `Reference` into `Attachment title`.
  - Enter `https://example.com` into `URL`.
  - Tap Save.
  - Assert callback receives `Reference` and `https://example.com`.
- Cancel:
  - Open a form.
  - Enter values.
  - Tap Cancel.
  - Assert no callback was invoked and the form is gone.

### 4. Wire The App Flow

Files:
- `app/src/main/java/com/example/quem/QueMApp.kt`
- `app/src/androidTest/java/com/example/quem/QueueListScreenTest.kt`

Pass the new ViewModel methods into `ItemDetailScreen`:

```kotlin
onAddTextAttachment = viewModel::addTextAttachment,
onAddLinkAttachment = viewModel::addLinkAttachment,
```

Add an app-level Compose test:

- Start `QueMApp` with the fake repository.
- Open the sample item.
- Tap Text.
- Fill `Attachment title` with `Note`.
- Fill `Text` with `Remember this`.
- Tap Save.
- Assert `Note` appears in the detail attachments list.
- Navigate back and assert the list count reflects the new attachment count.

If the fake repository already starts the sample item with two attachments, the updated list count should be `3 attachments`.

### 5. Verification

Run:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:ANDROID_HOME = 'C:\Android\SDK'
if (-not (Test-Path -LiteralPath (Join-Path $env:ANDROID_HOME 'platforms\android-36'))) {
    $env:ANDROID_HOME = 'C:\Dev\QueM\.worktrees\repository-ui\.tools\android-sdk'
}
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:PATH = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:PATH"
.\gradlew.bat :app:testDebugUnitTest :app:connectedDebugAndroidTest :app:assembleDebug
```

If connected Android tests fail because no emulator/device is available, verify with:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug
```

and report the connected-test blocker clearly.

## Expected Result

- Item detail exposes Text and Link attachment creation actions.
- Users can add text/link attachments without leaving the detail view.
- The detail attachment list refreshes from repository observation after save.
- The list screen attachment count updates after returning from detail.
- Drive creation remains hidden in detail until Google Drive picker/sign-in work begins.
