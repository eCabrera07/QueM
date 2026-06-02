package com.quem.drive

data class LocalFileSelection(
    val uriString: String,    // content URI as string — consistent with DriveFileUploadGateway.uriString
    val displayName: String,
    val mimeType: String?
)
