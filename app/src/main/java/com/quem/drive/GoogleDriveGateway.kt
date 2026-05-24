package com.quem.drive

import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.quem.data.sync.DriveGateway
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GoogleDriveGateway(
    private val drive: Drive,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DriveGateway {
    override suspend fun uploadTextFile(folderName: String, fileName: String, content: String) = withContext(ioDispatcher) {
        val folderId = ensureFolder(folderName)
        val existingFile = findFile(folderId, fileName)
        val mediaContent = ByteArrayContent(APPLICATION_JSON, content.toByteArray(StandardCharsets.UTF_8))

        if (existingFile == null) {
            val metadata = File()
                .setName(fileName)
                .setParents(listOf(folderId))
                .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_METADATA_FILE))

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

        Unit
    }

    override suspend fun downloadTextFile(folderName: String, fileName: String): String? = withContext(ioDispatcher) {
        val folderId = findFolder(folderName)?.id ?: return@withContext null
        val file = findFile(folderId, fileName) ?: return@withContext null
        val outputStream = ByteArrayOutputStream()

        drive.files()
            .get(file.id)
            .executeMediaAndDownloadTo(outputStream)

        outputStream.toString(StandardCharsets.UTF_8.name())
    }

    private fun ensureFolder(folderName: String): String {
        val existingFolder = findFolder(folderName)

        if (existingFolder != null) {
            return existingFolder.id
        }

        return drive.files()
            .create(
                File()
                    .setName(folderName)
                    .setMimeType(FOLDER_MIME_TYPE)
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_ROOT_FOLDER))
            )
            .setFields("id")
            .execute()
            .id
    }

    private fun findFolder(folderName: String): File? = drive.files()
        .list()
        .setQ(GoogleDriveQueries.canonicalFolderQuery(folderName))
        .setSpaces("drive")
        .setFields("files(id, name)")
        .execute()
        .files
        .orEmpty()
        .firstOrNull()

    private fun findFile(folderId: String, fileName: String): File? = drive.files()
        .list()
        .setQ(GoogleDriveQueries.canonicalFileInFolderQuery(folderId, fileName))
        .setSpaces("drive")
        .setFields("files(id, name)")
        .execute()
        .files
        .orEmpty()
        .firstOrNull()

    private companion object {
        const val APPLICATION_JSON = "application/json"
        const val FOLDER_MIME_TYPE = "application/vnd.google-apps.folder"
        const val APP_PROPERTY_ROLE = "quemRole"
        const val APP_PROPERTY_ROOT_FOLDER = "rootFolder"
        const val APP_PROPERTY_METADATA_FILE = "metadataFile"
    }
}

object GoogleDriveQueries {
    fun fileInFolderQuery(folderId: String, fileName: String): String =
        "${literal(folderId)} in parents and name = ${literal(fileName)} and trashed = false"

    internal fun folderQuery(folderName: String): String =
        "mimeType = ${literal(FOLDER_MIME_TYPE)} and name = ${literal(folderName)} and trashed = false"

    internal fun canonicalFolderQuery(folderName: String): String =
        "mimeType = ${literal(FOLDER_MIME_TYPE)} and name = ${literal(folderName)} and " +
            appPropertyEquals(APP_PROPERTY_ROLE, APP_PROPERTY_ROOT_FOLDER) +
            " and trashed = false"

    internal fun canonicalFileInFolderQuery(folderId: String, fileName: String): String =
        "${literal(folderId)} in parents and name = ${literal(fileName)} and " +
            appPropertyEquals(APP_PROPERTY_ROLE, APP_PROPERTY_METADATA_FILE) +
            " and trashed = false"

    private fun appPropertyEquals(key: String, value: String): String =
        "appProperties has { key = ${literal(key)} and value = ${literal(value)} }"

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
    private const val APP_PROPERTY_ROLE = "quemRole"
    private const val APP_PROPERTY_ROOT_FOLDER = "rootFolder"
    private const val APP_PROPERTY_METADATA_FILE = "metadataFile"
}
