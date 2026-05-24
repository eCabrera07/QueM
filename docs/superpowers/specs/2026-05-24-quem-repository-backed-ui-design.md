# QueM Repository-Backed UI Design

## Goal

Replace QueM's sample in-memory queue data with the existing Room-backed `QueueRepository` so core queue actions persist locally. This slice makes the app useful before Google sign-in and Drive sync are fully connected.

## Scope

In scope:

- Create and expose a single app-level `QueMDatabase` and `RoomQueueRepository`.
- Add a `QueueViewModel` or equivalent state holder for queue UI state.
- Drive the queue list tabs from `QueueRepository.observeItems(status)`.
- Save new items through `QueueRepository.createItem(title, description)`.
- Change item status through `QueueRepository.changeStatus(id, DONE/DISMISSED)`.
- Observe selected item details through `QueueRepository.observeItem(id)`.
- Keep due date optional in the UI.
- Keep Done and Dismissed items retained and visible under their status tabs.

Out of scope:

- Google account sign-in UI.
- Real Drive file/folder picker flows.
- Full attachment creation dialogs.
- Metadata upload/download merge logic.
- Dependency injection framework adoption.

## Architecture

`QueMApplication` will own lightweight application dependencies:

- `QueMDatabase`
- `RoomQueueRepository`

`MainActivity` will pass the repository into `QueMApp`. `QueMApp` will create a `QueueViewModel` through a small `ViewModelProvider.Factory`, avoiding a DI framework for now.

`QueueViewModel` will own:

- Selected queue status tab.
- Selected item id.
- Whether the create screen is open.
- Flow-backed list state for the selected status.
- Flow-backed selected item state.

The existing screen composables should remain mostly parameter-driven. The ViewModel should translate domain models to existing UI models.

## Data Flow

List:

1. User selects a status tab.
2. ViewModel updates selected status.
3. Repository emits items for that status from Room.
4. UI renders `QueueListScreen`.

Create:

1. User taps New.
2. UI opens `CreateItemScreen`.
3. Save calls `QueueRepository.createItem`.
4. ViewModel selects `QUEUED` and opens the saved item detail.

Status actions:

1. User taps Done or Dismiss.
2. ViewModel calls `QueueRepository.changeStatus`.
3. ViewModel selects the target status and returns to list.
4. Item remains retained under Done or Dismissed.

## Error Handling

This slice should keep error handling minimal:

- Blank titles remain blocked in `CreateItemScreen`.
- Repository returns `null` for missing items on status change; the ViewModel should return to the list without crashing.
- Empty lists should render naturally through existing list UI.

## Testing

Add focused tests for:

- ViewModel creates an item through the repository and exposes it in queued items.
- ViewModel moves an item to Dismissed and it no longer appears under Queued.
- Existing Compose tests still pass with repository wiring.
- Full `testDebugUnitTest`, `connectedDebugAndroidTest`, and `assembleDebug` pass.

## Follow-Ups

- Wire attachment creation forms to repository attachment methods.
- Replace manual app dependency wiring with a DI framework if the object graph grows.
- Connect Google sign-in and Drive sync to real persisted data.
