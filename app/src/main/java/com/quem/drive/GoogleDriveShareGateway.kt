package com.quem.drive

import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File
import com.google.api.services.drive.model.Permission
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

class GoogleDriveShareGateway(
    private val drive: Drive,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : DriveShareGateway {

    override suspend fun publishSharedItemFile(itemId: String, content: String): String =
        withContext(ioDispatcher) {
            val folderId = ensureFolder(QUE_M_FOLDER)
            val fileName = "shared-$itemId.json"
            val existingFile = findFile(folderId, fileName)
            val mediaContent = ByteArrayContent(
                APPLICATION_JSON,
                content.toByteArray(StandardCharsets.UTF_8)
            )
            if (existingFile == null) {
                val metadata = File()
                    .setName(fileName)
                    .setParents(listOf(folderId))
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_SHARED_ITEM))
                drive.files().create(metadata, mediaContent).setFields("id").execute().id
            } else {
                drive.files().update(existingFile.id, null, mediaContent).setFields("id").execute()
                existingFile.id
            }
        }

    override suspend fun grantWriterAccess(fileId: String, recipientEmail: String) =
        withContext(ioDispatcher) {
            val permission = Permission()
                .setType("user")
                .setRole("writer")
                .setEmailAddress(recipientEmail)
            drive.permissions().create(fileId, permission)
                .setSendNotificationEmail(true)
                .execute()
            Unit
        }

    private fun ensureFolder(folderName: String): String {
        val existing = findFolder(folderName)
        if (existing != null) return existing.id
        return drive.files()
            .create(
                File().setName(folderName).setMimeType(FOLDER_MIME_TYPE)
                    .setAppProperties(mapOf(APP_PROPERTY_ROLE to APP_PROPERTY_ROOT_FOLDER))
            ).setFields("id").execute().id
    }

    private fun findFolder(folderName: String): File? = drive.files().list()
        .setQ("mimeType = '$FOLDER_MIME_TYPE' and name = '$folderName' and appProperties has { key = '$APP_PROPERTY_ROLE' and value = '$APP_PROPERTY_ROOT_FOLDER' } and trashed = false")
        .setSpaces("drive").setFields("files(id, name)").execute().files.orEmpty().firstOrNull()

    private fun findFile(folderId: String, fileName: String): File? = drive.files().list()
        .setQ("'$folderId' in parents and name = '$fileName' and appProperties has { key = '$APP_PROPERTY_ROLE' and value = '$APP_PROPERTY_SHARED_ITEM' } and trashed = false")
        .setSpaces("drive").setFields("files(id, name)").execute().files.orEmpty().firstOrNull()

    private companion object {
        const val APPLICATION_JSON         = "application/json"
        const val FOLDER_MIME_TYPE         = "application/vnd.google-apps.folder"
        const val QUE_M_FOLDER             = "QueM"
        const val APP_PROPERTY_ROLE        = "quemRole"
        const val APP_PROPERTY_ROOT_FOLDER = "rootFolder"
        const val APP_PROPERTY_SHARED_ITEM = "sharedItem"
    }
}
