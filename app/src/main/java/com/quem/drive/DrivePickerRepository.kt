package com.quem.drive

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.annotation.MainThread

/**
 * Persists Drive picker callbacks across Activity recreation.
 * Lives in AppDependencies so it survives configuration changes.
 * MainActivity routes SAF results here directly, mirroring
 * how GoogleDriveConnectionRepository receives auth results.
 *
 * All methods must be called on the main thread. No synchronization is
 * applied because every call site (Compose UI and Activity Result callbacks)
 * already runs on the main thread.
 */
class DrivePickerRepository(private val contentResolver: ContentResolver) {
    private var pendingFileCallback: ((DriveSelection?) -> Unit)? = null
    private var pendingFolderCallback: ((DriveSelection?) -> Unit)? = null

    /**
     * Stores the callback for an in-flight file pick. Returns true if stored,
     * false if a pick is already in progress (double-tap guard).
     */
    @MainThread
    fun setPendingFileCallback(callback: (DriveSelection?) -> Unit): Boolean {
        if (pendingFileCallback != null) return false
        pendingFileCallback = callback
        return true
    }

    /**
     * Stores the callback for an in-flight folder pick. Returns true if stored,
     * false if a pick is already in progress (double-tap guard).
     */
    @MainThread
    fun setPendingFolderCallback(callback: (DriveSelection?) -> Unit): Boolean {
        if (pendingFolderCallback != null) return false
        pendingFolderCallback = callback
        return true
    }

    /** Called by MainActivity when the file picker Activity Result arrives. Survives Activity recreation. */
    @MainThread
    fun handleFileResult(uri: Uri?) {
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.invoke(uri?.toFileSelection())
    }

    /** Called by MainActivity when the folder picker Activity Result arrives. Survives Activity recreation. */
    @MainThread
    fun handleFolderResult(uri: Uri?) {
        val callback = pendingFolderCallback
        pendingFolderCallback = null
        callback?.invoke(uri?.toFolderSelection())
    }

    private fun Uri.toFileSelection(): DriveSelection? {
        val documentId = runCatching { DocumentsContract.getDocumentId(this) }.getOrNull() ?: return null
        val driveId = extractDriveId(documentId) ?: return null
        val (displayName, mimeType) = queryMetadata()
        return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = mimeType, isFolder = false)
    }

    private fun Uri.toFolderSelection(): DriveSelection? {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
        val driveId = extractDriveId(treeDocId) ?: return null
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(this, treeDocId)
        val (displayName, _) = documentUri.queryMetadata()
        return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = null, isFolder = true)
    }

    private fun Uri.queryMetadata(): Pair<String?, String?> =
        contentResolver.query(
            this,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1)
            else null to null
        } ?: (null to null)
}

/**
 * Extracts the Drive file/folder ID from a SAF document ID.
 * SAF document IDs from the Drive provider use the format:
 *   `acc=<n>/doc=<driveId>` (files) or
 *   `acc=<n>/type=dir/root=<r>/doc=<driveId>` (folders).
 * URL-encoded variants are decoded before parsing.
 */
internal fun extractDriveId(documentId: String): String? {
    val decoded = if ('%' in documentId) Uri.decode(documentId) else documentId
    return decoded.split("/")
        .lastOrNull { it.startsWith("doc=") }
        ?.removePrefix("doc=")
        ?.takeIf { it.isNotEmpty() }
}
