# Manual Test Plan

> **Purpose:** End-to-end verification of all QueM features. Run this after any significant change or before a release to confirm the full app flow works on a real device or emulator.

---

## Prerequisites

- Android emulator or physical device running Android 8.0+ (API 26+)
- App installed and running
- For sync tests: a Google account with Drive access

---

## 1. Core Queue Flow

### 1a. Create item
1. Tap **New**
2. Enter title: `Read contract`, priority: `high`, due date: `2026-06-15`
3. Tap **Save**

**Expected:**
- [ ] Item appears in Queued list
- [ ] Orange sync dot visible on the list card
- [ ] Tap item → detail screen shows `HIGH` priority and `2026-06-15` due date
- [ ] History section shows `"Created"`

### 1b. Edit item
1. Open the item from step 1a
2. Tap **Edit**
3. Change the title to `Review contract`
4. Tap **Save**

**Expected:**
- [ ] Detail screen shows `Review contract`
- [ ] History shows a new `"Edited"` entry

### 1c. Add text attachment
1. On the detail screen tap **Text**
2. Enter title: `Notes`, text: `Remember to check clause 7`
3. Tap **Save**

**Expected:**
- [ ] `Notes` appears in the Attachments section
- [ ] History shows `"Attachment added: Notes"`

### 1d. Status change — Done
1. Tap **Done**

**Expected:**
- [ ] Item disappears from Queued list
- [ ] Item appears in the **Done** tab
- [ ] History shows `"Marked as Done"`

### 1e. Status change — Dismiss a separate item
1. Create a new item: `Call accountant`
2. Open it and tap **Dismiss**

**Expected:**
- [ ] Item disappears from Queued list
- [ ] Item appears in the **Dismissed** tab
- [ ] History shows `"Dismissed"`

---

## 2. Archive Search

### 2a. Browse archive
1. Tap **Archive** from the main queue list

**Expected:**
- [ ] Both archived items (`Review contract`, `Call accountant`) visible
- [ ] Tap **Back** → returns to main list

### 2b. Filter by query
1. Open Archive
2. Type `review` in the search field

**Expected:**
- [ ] Only `Review contract` shown

### 2c. Empty search result
1. In Archive, type `xyzxyzxyz`

**Expected:**
- [ ] Shows `No results for "xyzxyzxyz"`

### 2d. Clear query
1. Clear the search field

**Expected:**
- [ ] All archived items return

---

## 3. Sync (requires Google Drive sign-in)

### 3a. Sign in
1. Tap **Settings** → **Sign in**
2. Complete Google authorization

**Expected:**
- [ ] Settings shows `"Drive connected"` and your email

### 3b. Manual sync
1. Tap **Settings** → **Sync Now**
2. Wait ~5 seconds

**Expected:**
- [ ] Orange sync dots disappear from queue list items (items marked Synced)

### 3c. Periodic sync
1. Leave the app running (or background it)
2. Wait ~15 minutes

**Expected:**
- [ ] Any new items created since last sync have their dots cleared

### 3d. Bidirectional sync (two-device test)
1. On **Device A**: create item `Cross-device test`, sync
2. On **Device B**: install app, sign in with same Google account, tap **Sync Now**

**Expected:**
- [ ] `Cross-device test` appears on Device B

---

## 4. Edge Cases

### 4a. Save button disabled on blank title
1. Tap **New**
2. Leave title empty

**Expected:**
- [ ] **Save** button is disabled (greyed out)

### 4b. Edit — Save button disabled on cleared title
1. Open any item → **Edit**
2. Clear the title field

**Expected:**
- [ ] **Save** button is disabled

### 4c. Drive actions when not signed in
1. Sign out (or use a fresh install)
2. Open any item → tap **Drive file**

**Expected:**
- [ ] Shows `"Sign in to Google Drive to attach files"` message
- [ ] No file picker opens

### 4d. Archive from Done tab
1. Tap the **Done** tab
2. Open an item and tap the item title to enter detail

**Expected:**
- [ ] Item also appears in Archive search

---

## Reporting Issues

If a step produces unexpected behavior, note:
- The step number (e.g., `2b`)
- What you expected
- What actually happened
- Any error messages visible
