package com.quem.drive

object DriveUrlExtractor {
    fun extractFileId(url: String): String? {
        // https://drive.google.com/file/d/{fileId}/view|edit|...
        Regex("/file/d/([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)?.let { return it }
        // https://drive.google.com/open?id={fileId}
        Regex("[?&]id=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)?.let { return it }
        return null
    }

    fun extractFolderId(url: String): String? {
        // https://drive.google.com/drive/folders/{folderId}
        // https://drive.google.com/drive/u/0/folders/{folderId}
        Regex("/folders/([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)?.let { return it }
        // https://drive.google.com/open?id={folderId}
        Regex("[?&]id=([a-zA-Z0-9_-]+)").find(url)?.groupValues?.get(1)?.let { return it }
        return null
    }
}
