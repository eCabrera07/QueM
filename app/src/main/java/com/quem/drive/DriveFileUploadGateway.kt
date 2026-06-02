package com.quem.drive

import android.content.ContentResolver
import android.net.Uri

interface DriveFileUploadGateway {
    /**
     * Ensures `QueM/files/{itemId}/` exists in Drive and uploads the file from [uri].
     * Streams the file using [contentResolver] — never loads it fully into memory.
     * Returns the Drive file ID of the uploaded file.
     */
    suspend fun uploadLocalFile(
        itemId: String,
        fileName: String,
        mimeType: String,
        contentResolver: ContentResolver,
        uri: Uri
    ): String
}
