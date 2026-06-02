package com.quem.drive

import android.net.Uri

data class LocalFileSelection(
    val uri: Uri,
    val displayName: String,
    val mimeType: String?
)
