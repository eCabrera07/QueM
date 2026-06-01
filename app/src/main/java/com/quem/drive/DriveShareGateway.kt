package com.quem.drive

interface DriveShareGateway {
    /** Creates or overwrites `QueM/shared-{itemId}.json`. Returns Drive file ID. */
    suspend fun publishSharedItemFile(itemId: String, content: String): String

    /** Grants writer access to the Drive file; Drive emails the recipient. */
    suspend fun grantWriterAccess(fileId: String, recipientEmail: String)
}
