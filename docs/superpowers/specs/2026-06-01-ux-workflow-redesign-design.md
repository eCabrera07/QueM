# QueM UX Workflow Redesign Design

## Purpose

QueM should feel like a calm, fast personal work queue: capture an item, clarify it with attachments and notes, move it through a small lifecycle, and recover archived work when needed. The current app has the right domain pieces, but the workflow reads as separate screens rather than one continuous product. This redesign makes the current feature set easier to understand and use before adding larger sync or sharing capabilities.

## Observed Problems

- The main queue header is crowded on phone screens. `Settings`, `Archive`, and `New` compete with the `QueM` title, and the `Dismissed` tab wraps awkwardly.
- Navigation is modeled as several booleans, which makes the app fragile. Android system Back can exit the app from an edit flow instead of returning to detail or list.
- Item status changes are technically possible through a dropdown, but the core workflow actions are not obvious.
- Attachments are split awkwardly between detail and edit. Users expect to add supporting material while viewing an item, not only while editing metadata.
- Screens use mostly default Material 3 styling, so priority, sync, status, and archive state do not have a strong visual language.
- Empty states are sparse and do not tell users what action to take next.
- The manual test plan describes some flows that do not match the current UI.

## Product Direction

The recommended direction is a workflow restructure plus visual polish. QueM should keep the existing local-first architecture, Room repository, `QueueViewModel`, and Compose screens, but the UI state model should become more explicit. The redesign should not add new product concepts such as teams, complex labels, or multi-project dashboards.

## Target Workflow

1. User lands on the queue list.
2. User scans items by status: Queued, In Progress, Done, Dismissed.
3. User taps the floating create action to capture a new item.
4. User opens detail to review the item, inspect metadata, add attachments, and act on status.
5. User uses direct workflow actions for common moves: start, mark done, dismiss, restore.
6. User uses edit only for metadata changes: title, description, priority, and due date.
7. User searches archived Done and Dismissed work from Archive.
8. User manages Drive connection and sync from Settings.

## Screen Design

### Queue List

The queue list becomes the app's operational home. It should use a compact top app bar with `QueM` as the title, icon buttons for Settings and Archive, and a floating action button for creating a new item. Status navigation should use a layout that does not wrap labels on common phone widths. The preferred solution is a horizontally scrollable Material tab row or status filter chips.

Cards should show title, priority chip, due date chip, attachment count, status-specific affordances, and sync state. The card background should be neutral, with priority and sync communicated through small chips or indicators rather than large color fields.

### Create Item

Create should remain focused: title, description, priority, due date, Cancel, Save. The visual hierarchy should make the title field primary. Save remains disabled until a nonblank title exists. The bottom action row should respect system navigation insets.

### Item Detail

Detail should become the main workflow surface. The top area should show Back, title, description, metadata chips, and sync state. Common status moves should be direct buttons:

- Queued: `Start`, `Mark done`, `Dismiss`
- In Progress: `Mark done`, `Move to queued`, `Dismiss`
- Done: `Restore`
- Dismissed: `Restore`

The full status dropdown can remain as a secondary control only if tests or existing workflows need arbitrary status movement.

Attachments should be addable from detail. Existing text, link, Drive file, and Drive folder attachment flows should remain, but the entry point should be a compact attachment action row instead of a tall stack of full-width buttons. Existing rename and delete menus should stay.

History should stay below attachments. Destructive history deletion should remain behind the overflow menu.

### Edit Item

Edit should be metadata-only: title, description, priority, due date, Cancel, Save. It should not be the primary way to add attachments. The save bar should remain visible and usable above the system navigation bar.

### Archive

Archive should show searchable Done and Dismissed items. Search should be visually clear, and result cards should reveal whether the item is Done or Dismissed. Empty states should distinguish between "nothing archived yet" and "no results for this query."

### Settings

Settings should be quiet and account-focused. It should show account email, connection state, sync state, and actions. `Disconnect` should remain secondary and visually less prominent than `Sync now`.

## Visual System

Define a QueM-specific Material theme instead of relying on the default light color scheme. The palette should avoid a one-note purple UI. Purple can remain the primary brand accent, but the app should also use neutral surfaces, green for done/synced, amber for pending sync or due-soon, red for error/destructive actions, and blue or teal for in-progress state.

Reusable UI elements should include:

- `QueMTopBar` for consistent screen headers.
- `StatusChip` for queue states.
- `PriorityChip` for low, medium, high.
- `SyncStatusChip` for pending, syncing, error, and synced states.
- `EmptyState` for list and section-level empty views.
- `BottomActionBar` for create/edit save actions with safe-area padding.

Buttons should use icons where they make scanning faster, especially Settings, Archive, New, Back, Edit, overflow, attachment type actions, and Search. Text buttons are still acceptable for clear commands such as Save, Cancel, Restore, and Dismiss.

## Architecture

The redesign should keep the repository and data model stable. UI navigation should be made explicit with a small screen model such as:

```kotlin
sealed interface QueMScreen {
    data object List : QueMScreen
    data object Create : QueMScreen
    data object Settings : QueMScreen
    data object Archive : QueMScreen
    data class Detail(val itemId: String) : QueMScreen
    data class Edit(val itemId: String) : QueMScreen
}
```

`QueueViewModel` can continue to own selected status, archive query, items, selected item detail, and mutation methods. Screen state should replace the scattered booleans for create, edit, settings, archive, and selected item. This makes Android Back behavior testable and reduces contradictory states.

## Error Handling

- Keep save disabled when item title is blank.
- Preserve existing Drive URL validation for Drive attachments.
- Keep destructive actions behind menus or explicit secondary actions.
- Show Drive connection problems in Settings and in attachment flows that require Drive.
- Do not block local queue usage because Drive is disconnected unless the product intentionally switches to sync-first onboarding.

## Testing

Testing should cover both behavior and visual regressions that previously caused problems:

- ViewModel navigation transitions, including Back behavior.
- Queue list header and status controls on phone-width screens.
- Create and edit save enablement.
- Detail status actions for each status.
- Attachment add flows from detail.
- Archive search empty and result states.
- Settings connected and disconnected states.
- Manual test plan updated to match the implemented UI.

## Out Of Scope

- New data model fields such as tags or projects.
- Full Google Drive sharing redesign.
- Cross-device sync behavior changes.
- A custom illustration-heavy onboarding flow.
- Large repository restructuring outside UI state and reusable UI components.
