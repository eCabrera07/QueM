package com.quem.core.model

enum class SyncState {
    SYNCED,
    PENDING_SYNC,
    SYNCING,
    ERROR,
    /** Local-file attachment stored on device; upload to Drive not yet attempted. */
    PENDING_UPLOAD,
    /** Upload to Drive was attempted and failed; the `url` field holds the content URI for retry. */
    UPLOAD_FAILED
}
