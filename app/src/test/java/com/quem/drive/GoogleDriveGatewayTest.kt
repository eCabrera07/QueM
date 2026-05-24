package com.quem.drive

import com.google.api.client.http.HttpTransport
import com.google.api.client.http.LowLevelHttpRequest
import com.google.api.client.http.LowLevelHttpResponse
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleDriveGatewayTest {
    @Test
    fun metadataFileQueryEscapesFolderAndFileNames() {
        val query = GoogleDriveQueries.fileInFolderQuery(
            folderId = "folder123",
            fileName = "queue-metadata.json"
        )

        assertEquals("'folder123' in parents and name = 'queue-metadata.json' and trashed = false", query)
    }

    @Test
    fun fileQueryEscapesApostrophesAndBackslashes() {
        val query = GoogleDriveQueries.fileInFolderQuery(
            folderId = "folder\\'123",
            fileName = "queue\\metadata's.json"
        )

        assertEquals(
            "'folder\\\\\\'123' in parents and name = 'queue\\\\metadata\\'s.json' and trashed = false",
            query
        )
    }

    @Test
    fun canonicalFolderQueryMatchesAppOwnedFolder() {
        val query = GoogleDriveQueries.canonicalFolderQuery("QueM")

        assertEquals(
            "mimeType = 'application/vnd.google-apps.folder' and name = 'QueM' and " +
                "appProperties has { key = 'quemRole' and value = 'rootFolder' } and trashed = false",
            query
        )
    }

    @Test
    fun canonicalFileQueryMatchesAppOwnedMetadataFile() {
        val query = GoogleDriveQueries.canonicalFileInFolderQuery(
            folderId = "folder123",
            fileName = "queue-metadata.json"
        )

        assertEquals(
            "'folder123' in parents and name = 'queue-metadata.json' and " +
                "appProperties has { key = 'quemRole' and value = 'metadataFile' } and trashed = false",
            query
        )
    }

    @Test
    fun downloadReturnsNullWithoutCreatingMissingFolder() = runTest {
        val transport = RecordingTransport(
            responses = listOf("""{"files":[]}""")
        )
        val gateway = GoogleDriveGateway(drive(transport))

        val content = gateway.downloadTextFile("QueM", "queue-metadata.json")

        assertNull(content)
        assertEquals(listOf("GET"), transport.requests.map { it.method })
        assertFalse(transport.requests.any { it.method == "POST" })
    }
}

private fun drive(transport: HttpTransport): Drive = Drive.Builder(
    transport,
    GsonFactory.getDefaultInstance(),
    null
).setApplicationName("QueMTest").build()

private class RecordingTransport(
    responses: List<String>
) : HttpTransport() {
    val requests = mutableListOf<RecordedRequest>()
    private val queuedResponses = ArrayDeque(responses)

    override fun buildRequest(method: String, url: String): LowLevelHttpRequest =
        object : LowLevelHttpRequest() {
            override fun addHeader(name: String, value: String) = Unit

            override fun execute(): LowLevelHttpResponse {
                requests += RecordedRequest(method, url)
                val response = queuedResponses.removeFirstOrNull() ?: "{}"
                return JsonResponse(response)
            }
        }
}

private data class RecordedRequest(val method: String, val url: String)

private class JsonResponse(private val body: String) : LowLevelHttpResponse() {
    override fun getContent(): InputStream = ByteArrayInputStream(body.toByteArray())
    override fun getContentEncoding(): String? = null
    override fun getContentLength(): Long = body.length.toLong()
    override fun getContentType(): String = "application/json"
    override fun getStatusLine(): String = "HTTP/1.1 200 OK"
    override fun getStatusCode(): Int = 200
    override fun getReasonPhrase(): String = "OK"
    override fun getHeaderCount(): Int = 0
    override fun getHeaderName(index: Int): String? = null
    override fun getHeaderValue(index: Int): String? = null
}
