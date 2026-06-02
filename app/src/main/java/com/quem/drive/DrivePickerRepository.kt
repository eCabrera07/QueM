package com.quem.drive

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.annotation.MainThread
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

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

    @MainThread
    fun clearPendingFileCallback() { pendingFileCallback = null }

    @MainThread
    fun clearPendingFolderCallback() { pendingFolderCallback = null }

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

    private var pendingLocalFileCallback: ((LocalFileSelection?) -> Unit)? = null

    @MainThread
    fun clearPendingLocalFileCallback() { pendingLocalFileCallback = null }

    @MainThread
    fun setPendingLocalFileCallback(callback: (LocalFileSelection?) -> Unit): Boolean {
        if (pendingLocalFileCallback != null) return false
        pendingLocalFileCallback = callback
        return true
    }

    /** Called by MainActivity when the local file picker Activity Result arrives. */
    @MainThread
    fun handleLocalFileResult(uri: Uri?) {
        val callback = pendingLocalFileCallback
        pendingLocalFileCallback = null
        callback?.invoke(uri?.toLocalFileSelection())
    }

    private fun Uri.toLocalFileSelection(): LocalFileSelection? {
        val (displayName, mimeType) = queryMetadata()
        return LocalFileSelection(
            uri         = this,
            displayName = displayName ?: lastPathSegment ?: "file",
            mimeType    = mimeType
        )
    }

    private fun Uri.toFileSelection(): DriveSelection? {
        val documentId = runCatching { DocumentsContract.getDocumentId(this) }.getOrNull()
        Log.d("DrivePicker", "toFileSelection uri=$this documentId=$documentId authority=${authority}")
        documentId ?: return null
        val driveId = extractDriveId(documentId)
        Log.d("DrivePicker", "extractDriveId($documentId) -> $driveId")
        driveId ?: return null
        val (displayName, mimeType) = queryMetadata()
        return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = mimeType, isFolder = false)
    }

    private fun Uri.toFolderSelection(): DriveSelection? {
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull()
        Log.d("DrivePicker", "toFolderSelection uri=$this treeDocId=$treeDocId authority=${authority}")
        treeDocId ?: return null
        val driveId = extractDriveId(treeDocId)
        Log.d("DrivePicker", "extractDriveId($treeDocId) -> $driveId")
        driveId ?: return null
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
    val decoded = decodePercentEscapes(documentId)
    return decoded.split("/")
        .lastOrNull { it.startsWith("doc=") }
        ?.removePrefix("doc=")
        ?.takeIf { it.isNotEmpty() }
}

private fun decodePercentEscapes(value: String): String {
    if ('%' !in value) return value

    val decoded = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val bytes = ByteArrayOutputStream()
            while (index + 2 < value.length && value[index] == '%') {
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: break
                bytes.write(byte)
                index += 3
            }
            if (bytes.size() > 0) {
                decoded.append(bytes.toByteArray().toString(StandardCharsets.UTF_8))
                continue
            }
        }

        decoded.append(value[index])
        index += 1
    }

    return decoded.toString()
}
