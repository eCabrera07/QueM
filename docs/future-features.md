# Future Features

Ideas and feature requests to brainstorm and build in future sessions.

---

## Item Sharing

**Request:** Share a queue item with another user.

**Context to explore when designing:**
- How does the recipient receive the item? (email link, Google Drive shared file, in-app notification?)
- Does sharing give the recipient read-only view or a full copy they can edit independently?
- Does the sharer stay in sync with the recipient's changes, or is it a one-time export?
- Is sharing per-item or per-queue (share your whole queue with someone)?
- Authentication: the recipient must have the app — is that acceptable, or should a web preview be an option?

**Likely approach:** Since the app already stores data in Google Drive, the most natural path is Drive sharing — write the item's metadata to a shared Drive file/folder, and the recipient's app picks it up on their next sync. No backend needed.

**Complexity estimate:** Medium — requires Drive sharing permissions API, a new `sharedWith` field on items, and UI to enter the recipient's email.

---

## Ideas for future brainstorming sessions

- Push notifications when a shared item is updated
- Item due-date reminders / local notifications
- Tags UI (tags are stored in the data model but never displayed or editable)
- Priority sorting / custom queue ordering
- Recurring items
