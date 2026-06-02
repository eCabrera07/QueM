package com.quem.core.model

enum class SyncState {
    SYNCED,
    PENDING_SYNC,
    SYNCING,
    ERROR,
    PENDING_UPLOAD,
    UPLOAD_FAILED
}
