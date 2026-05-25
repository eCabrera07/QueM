# Attachment Creation Design

## Goal

Let users add text and link attachments to an existing queue item from the item detail screen, using the local repository-backed data model that already supports attachments.

## Scope

In scope:

- Add text and link attachment creation from `ItemDetailScreen`.
- Reuse the existing `AttachmentEditor` controls for attachment actions.
- Add simple inline forms for:
  - Text attachment: title and body text.
  - Link attachment: title and URL.
- Wire form saves through `QueueViewModel` to `QueueRepository.addTextAttachment` and `QueueRepository.addLinkAttachment`.
- Keep the selected item detail screen open after saving.
- Let the existing `QueueViewModel` attachment observation refresh the detail list and list attachment counts.
- Keep blank-title, blank-text, and blank-url saves as no-ops.

Out of scope:

- Google Drive file/folder picker flows.
- Google sign-in changes.
- Attachment editing or deletion.
- Rich URL validation.
- User-visible validation errors for blank or invalid fields.
- History/event logging for attachment creation.

## Architecture

`ItemDetailScreen` will remain a parameter-driven composable. It will gain callbacks for text/link attachment saves and local UI state for which inline form is open.

`QueMApp` will pass the new callbacks from `QueueViewModel` into `ItemDetailScreen`. `QueueViewModel` will use the currently selected item id and call the repository methods. If no item is selected, the add operation returns without crashing.

`AttachmentEditor` will support showing only local attachment actions. In this slice, the item detail screen will show `Text` and `Link` actions only. Drive file and folder actions remain out of scope until the Drive picker slice.

## UI Flow

Text attachment:

1. User opens a queue item detail screen.
2. User taps `Text` in the attachment editor.
3. Inline form appears with title and text fields.
4. User taps Save.
5. ViewModel calls `addTextAttachment`.
6. Form closes and the attachment appears in the attachment list.

Link attachment:

1. User opens a queue item detail screen.
2. User taps `Link` in the attachment editor.
3. Inline form appears with title and URL fields.
4. User taps Save.
5. ViewModel calls `addLinkAttachment`.
6. Form closes and the attachment appears in the attachment list.

Cancel closes the open form without saving.

## Error Handling

Blank title, blank text, and blank URL are no-ops. This matches the repository behavior and avoids adding validation UI in this slice.

The ViewModel should not crash if an add action happens after the selected item is cleared. It should simply return.

## Testing

Add focused tests for:

- `QueueViewModel.addTextAttachment` calls the repository for the selected item.
- `QueueViewModel.addLinkAttachment` calls the repository for the selected item.
- Add actions no-op when no item is selected.
- `ItemDetailScreen` shows the text/link forms and calls save/cancel callbacks.
- App-level Compose flow can add a text or link attachment and then display it in the item detail list.

Final verification should include:

- `:app:testDebugUnitTest`
- `:app:connectedDebugAndroidTest`
- `:app:assembleDebug`
