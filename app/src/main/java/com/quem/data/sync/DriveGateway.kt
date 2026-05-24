package com.quem.data.sync

interface DriveGateway {
    suspend fun uploadTextFile(folderName: String, fileName: String, content: String)

    suspend fun downloadTextFile(folderName: String, fileName: String): String?
}
