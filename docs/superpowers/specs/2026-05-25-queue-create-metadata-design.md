# Queue Create Metadata Design

## Goal

Persist priority and optional due date when a user creates a queue item, so the repository-backed UI no longer drops fields that the create form already collects.

## Scope

In scope:

- Extend queue item creation through `QueueRepository` to accept `Priority?` and `LocalDate?`.
- Save priority and due date in `RoomQueueRepository.createItem`.
- Parse create form priority text in the ViewModel as `LOW`, `MEDIUM`, or `HIGH`, case-insensitive.
- Parse due date text as ISO `YYYY-MM-DD`.
- Treat blank priority and blank due date as `null`.
- Treat invalid priority or invalid date as `null` without crashing.
- Update fake repositories and tests affected by the repository signature change.

Out of scope:

- A richer priority picker UI.
- Date picker UI.
- User-visible validation messages for invalid priority/date.
- Editing metadata after item creation.
- Drive sync merge behavior changes.

## Architecture

`CreateItemScreen` can keep its current text-field API. `QueMApp` will pass all four create fields into `QueueViewModel`, and the ViewModel will normalize metadata before calling the repository.

`QueueRepository.createItem` will become the single local creation API:

```kotlin
suspend fun createItem(
    title: String,
    description: String?,
    priority: Priority?,
    dueDate: LocalDate?
): QueueItem
```

`RoomQueueRepository` will persist these values through the existing `QueueItem` and Room mapper fields. Existing callers and tests will be updated to pass `null` where metadata is not relevant.

## Data Flow

1. User fills title, description, priority, and due date in `CreateItemScreen`.
2. `QueMApp` forwards all four values to `QueueViewModel.createItem`.
3. `QueueViewModel` parses priority and due date.
4. `QueueRepository.createItem` persists the normalized metadata.
5. Existing repository-backed list/detail mappings display persisted priority and due date.

## Error Handling

Blank priority and due date are valid and persist as `null`.

Invalid priority or due date input is ignored for this slice and persists as `null`. Title validation remains in `CreateItemScreen`, and repository title trimming behavior remains unchanged.

## Testing

Add focused tests for:

- `RoomQueueRepository.createItem` persists priority and due date.
- `QueueViewModel.createItem` parses valid priority/date input and sends domain values to the repository.
- `QueueViewModel.createItem` ignores invalid priority/date input without crashing.
- Existing app-level and repository tests still pass after the repository API update.

Final verification should include:

- `:app:testDebugUnitTest`
- `:app:connectedDebugAndroidTest`
- `:app:assembleDebug`
