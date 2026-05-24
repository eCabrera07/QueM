package com.quem.drive

import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.quem.data.sync.DriveGateway
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class GoogleDriveGateway(private val drive: Drive) : DriveGateway {
    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) {
        val folderId = ensureFolder(folderName)
        val existingFile = findFile(folderId, fileName)
        val mediaContent = ByteArrayContent(APPLICATION_JSON, content.toByteArray(StandardCharsets.UTF_8))

        if (existingFile == null) {
            val metadata = File()
                .setName(fileName)
                .setParents(listOf(folderId))

            drive.files()
                .create(metadata, mediaContent)
                .setFields("id")
                .execute()
        } else {
            drive.files()
                .update(existingFile.id, null, mediaContent)
                .setFields("id")
                .execute()
        }
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? {
        val folderId = ensureFolder(folderName)
        val file = findFile(folderId, fileName) ?: return null
        val outputStream = ByteArrayOutputStream()

        drive.files()
            .get(file.id)
            .executeMediaAndDownloadTo(outputStream)

        return outputStream.toString(StandardCharsets.UTF_8.name())
    }

    private fun ensureFolder(folderName: String): String {
        val existingFolder = drive.files()
            .list()
            .setQ(GoogleDriveQueries.folderQuery(folderName))
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
            .orEmpty()
            .firstOrNull()

        if (existingFolder != null) {
            return existingFolder.id
        }

        return drive.files()
            .create(
                File()
                    .setName(folderName)
                    .setMimeType(FOLDER_MIME_TYPE)
            )
            .setFields("id")
            .execute()
            .id
    }

    private fun findFile(folderId: String, fileName: String): File? = drive.files()
        .list()
        .setQ(GoogleDriveQueries.fileInFolderQuery(folderId, fileName))
        .setSpaces("drive")
        .setFields("files(id, name)")
        .execute()
        .files
        .orEmpty()
        .firstOrNull()

    private companion object {
        const val APPLICATION_JSON = "application/json"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
    }
}

object GoogleDriveQueries {
    fun fileInFolderQuery(folderId: String, fileName: String): String =
        "${literal(folderId)} in parents and name = ${literal(fileName)} and trashed = false"

    internal fun folderQuery(folderName: String): String =
        "mimeType = ${literal(FOLDER_MIME_TYPE)} and name = ${literal(folderName)} and trashed = false"

    private fun literal(value: String): String = buildString {
        append('\'')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '\'' -> append("\\'")
                else -> append(char)
            }
        }
        append('\'')
    }

    private const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
}
