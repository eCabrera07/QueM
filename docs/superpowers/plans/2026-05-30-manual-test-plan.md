# Manual Test Plan

> **Purpose:** End-to-end verification of QueM after significant workflow or UI changes. Run this on a real Android device or emulator before release.

---

## Prerequisites

- Android emulator or physical device running Android 8.0+ (API 26+)
- App installed and running
- For sync tests: a Google account with Drive access

---

## 1. Core Queue Flow

### 1a. Create item
1. Tap the **New** floating action button.
2. Enter title: `Read contract`, priority: `High`, due date: `2026-06-15`.
3. Tap **Save** in the bottom action bar.

**Expected:**
- [ ] Item appears in Queued.
- [ ] Priority, due date, attachment count, and sync state are visible on the item card.
- [ ] Opening the item shows the same metadata on detail.

### 1b. Edit item
1. Open `Read contract`.
2. Tap **Edit**.
3. Change the title to `Review contract`.
4. Tap **Save** in the bottom action bar.

**Expected:**
- [ ] Detail screen shows `Review contract`.
- [ ] History shows a new edited entry.

### 1c. Add text attachment from Edit
1. Open `Review contract`.
2. Tap **Edit**.
3. In the Attachments section, tap **Text**.
4. Enter title: `Notes`, text: `Remember to check clause 7`.
5. Tap **Save** on the attachment form.
6. Tap **Cancel** to return to detail.

**Expected:**
- [ ] `Notes` appears in the Attachments section.
- [ ] The queue list shows the updated attachment count.

### 1d. Move through workflow
1. Open `Review contract`.
2. Tap **Start**.
3. Open the item from **In Progress**.
4. Tap **Mark done**.

**Expected:**
- [ ] Item moves from Queued to In Progress, then Done.
- [ ] Archive search includes the Done item.

### 1e. Dismiss a separate item
1. Create a new item: `Call accountant`.
2. Open it and tap **Dismiss**.

**Expected:**
- [ ] Item appears in the **Dismissed** tab.
- [ ] Archive search includes the Dismissed item.

---

## 2. Archive Search

### 2a. Browse archive
1. Tap the **Archive** icon from the main queue list.

**Expected:**
- [ ] Archived Done and Dismissed items are visible.
- [ ] Tap the **Back** icon to return to the main list.

### 2b. Filter by query
1. Open Archive.
2. Type `review` in the search field.

**Expected:**
- [ ] Only matching archived items are shown.

### 2c. Empty search result
1. In Archive, type `xyzxyzxyz`.

**Expected:**
- [ ] Shows `No results for "xyzxyzxyz"`.

---

## 3. Settings And Sync

### 3a. Use app while disconnected
1. Launch the app without signing in to Google Drive.

**Expected:**
- [ ] The queue list is usable.
- [ ] Settings shows `Not signed in` and a **Sign in** action.

### 3b. Sign in
1. Tap the **Settings** icon.
2. Tap **Sign in**.
3. Complete Google authorization.

**Expected:**
- [ ] Settings shows `Drive connected` and your email.

### 3c. Manual sync
1. Tap the **Settings** icon.
2. Tap **Sync now**.
3. Wait about 5 seconds.

**Expected:**
- [ ] Pending sync indicators clear for synced queue items.

---

## 4. Edge Cases

### 4a. Save button disabled on blank title
1. Tap **New**.
2. Leave title empty.

**Expected:**
- [ ] **Save** is disabled.

### 4b. Edit save button disabled on cleared title
1. Open any item.
2. Tap **Edit**.
3. Clear the title field.

**Expected:**
- [ ] **Save** is disabled.

### 4c. Drive attachment by URL
1. Open any item.
2. Tap **Edit**.
3. In the Attachments section, tap **Drive file**.
4. Paste a valid Google Drive file URL and save the attachment.

**Expected:**
- [ ] The Drive attachment appears in the edit attachment list and on detail.
- [ ] The app remains usable even if Drive sync is disconnected.

---

## 5. Visual QA

Run these checks on both a small phone viewport and a larger device:

- [ ] Queue list top bar is not crowded.
- [ ] `Dismissed` tab stays on one line.
- [ ] Create and Edit bottom actions are visible above system navigation.
- [ ] Detail has clear workflow actions: **Start**, **Mark done**, **Dismiss**, or **Restore**.
- [ ] Archive and Settings use consistent top bars and empty states.
- [ ] Text does not overlap buttons, cards, or system navigation.

---

## Reporting Issues

If a step produces unexpected behavior, note:
- The step number, for example `2b`
- What you expected
- What actually happened
- Any visible error messages
