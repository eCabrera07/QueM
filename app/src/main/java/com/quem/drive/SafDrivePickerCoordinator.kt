package com.quem.drive

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.result.ActivityResultLauncher

class SafDrivePickerCoordinator(
    private val fileLauncher: ActivityResultLauncher<Array<String>>,
    private val folderLauncher: ActivityResultLauncher<Uri?>,
    private val contentResolver: ContentResolver
) : DrivePickerCoordinator {
    private var pendingFileCallback: ((DriveSelection?) -> Unit)? = null
    private var pendingFolderCallback: ((DriveSelection?) -> Unit)? = null

    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        if (pendingFileCallback != null) return
        pendingFileCallback = onResult
        fileLauncher.launch(arrayOf("*/*"))
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        if (pendingFolderCallback != null) return
        pendingFolderCallback = onResult
        folderLauncher.launch(null)
    }

    fun handleFileResult(uri: Uri?) {
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.invoke(uri?.toFileSelection(contentResolver))
    }

    fun handleFolderResult(uri: Uri?) {
        val callback = pendingFolderCallback
        pendingFolderCallback = null
        callback?.invoke(uri?.toFolderSelection(contentResolver))
    }
}

internal fun extractDriveId(documentId: String): String? {
    val decoded = if ('%' in documentId) Uri.decode(documentId) else documentId
    return decoded.split("/")
        .lastOrNull { it.startsWith("doc=") }
        ?.removePrefix("doc=")
        ?.takeIf { it.isNotEmpty() }
}

private fun Uri.toFileSelection(contentResolver: ContentResolver): DriveSelection? {
    val documentId = runCatching { DocumentsContract.getDocumentId(this) }.getOrNull() ?: return null
    val driveId = extractDriveId(documentId) ?: return null
    val (displayName, mimeType) = queryMetadata(contentResolver)
    return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = mimeType, isFolder = false)
}

private fun Uri.toFolderSelection(contentResolver: ContentResolver): DriveSelection? {
    val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(this) }.getOrNull() ?: return null
    val driveId = extractDriveId(treeDocId) ?: return null
    val documentUri = DocumentsContract.buildDocumentUriUsingTree(this, treeDocId)
    val (displayName, _) = documentUri.queryMetadata(contentResolver)
    return DriveSelection(id = driveId, name = displayName ?: driveId, mimeType = null, isFolder = true)
}

private fun Uri.queryMetadata(contentResolver: ContentResolver): Pair<String?, String?> =
    contentResolver.query(
        this,
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
        null, null, null
    )?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) to cursor.getString(1)
        else null to null
    } ?: (null to null)
