package com.quem.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryEntry
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.data.repository.QueueRepository
import com.quem.drive.DriveAccount
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class QueMAppPickerTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun driveFileUrlAttachesToCurrentItem() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Drive file"))
        compose.onNodeWithText("Drive file").performClick()
        compose.onNode(hasSetTextAction() and hasText("Label (optional)")).performTextInput("contract.pdf")
        compose.onNode(hasSetTextAction() and hasText("Drive file URL"))
            .performTextInput("https://drive.google.com/file/d/drive-123/view")
        compose.onAllNodesWithText("Save")[0].performClick()

        compose.onNodeWithText("contract.pdf").assertIsDisplayed()
    }

    @Test
    fun driveFolderUrlAttachesToCurrentItem() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Drive folder"))
        compose.onNodeWithText("Drive folder").performClick()
        compose.onNode(hasSetTextAction() and hasText("Label (optional)")).performTextInput("Project folder")
        compose.onNode(hasSetTextAction() and hasText("Drive folder URL"))
            .performTextInput("https://drive.google.com/drive/folders/folder-456")
        compose.onAllNodesWithText("Save")[0].performClick()

        compose.onNodeWithText("Project folder").assertIsDisplayed()
    }

    @Test
    fun cancellingDriveUrlFormDoesNotAddAttachment() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Edit").performClick()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Drive file"))
        compose.onNodeWithText("Drive file").performClick()
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithText("No attachments").assertIsDisplayed()
    }
}

private class ConnectedDriveConnectionRepository : DriveConnectionRepository {
    override val state = MutableStateFlow<DriveConnectionState>(
        DriveConnectionState.Connected(DriveAccount("test@example.com"))
    ).asStateFlow()

    override fun requestSignIn() {}
    override fun disconnect() {}
}

private class FakePickerQueueRepository : QueueRepository {
    private val items = MutableStateFlow<List<QueueItem>>(emptyList())
    private val attachments = MutableStateFlow<List<Attachment>>(emptyList())
    private var nextId = 1
    private var nextAttachmentId = 1

    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        items.map { list -> list.filter { it.status == status } }

    override fun searchArchive(query: String) = kotlinx.coroutines.flow.flowOf(emptyList<QueueItem>())

    override fun observeItem(id: String): Flow<QueueItem?> =
        items.map { list -> list.singleOrNull { it.id == id } }

    override suspend fun createItem(
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem {
        val item = QueueItem(
            id = "item-${nextId++}",
            driveId = null,
            title = title,
            description = description,
            status = QueueStatus.QUEUED,
            priority = priority,
            dueDate = dueDate,
            tags = emptyList(),
            createdAt = Instant.parse("2026-05-29T12:00:00Z"),
            updatedAt = Instant.parse("2026-05-29T12:00:00Z"),
            completedAt = null,
            dismissedAt = null,
            syncState = SyncState.PENDING_SYNC
        )
        items.value = items.value + item
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? = null

    override suspend fun updateItem(
        id: String,
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem? {
        var updatedItem: QueueItem? = null
        items.value = items.value.map { item ->
            if (item.id == id) {
                item.copy(
                    title = title,
                    description = description,
                    priority = priority,
                    dueDate = dueDate
                ).also { updatedItem = it }
            } else {
                item
            }
        }
        return updatedItem
    }

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        attachments.map { list -> list.filter { it.queueItemId == queueItemId } }

    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) {}

    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) {}

    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) {
        attachments.value = attachments.value + Attachment(
            id = "attachment-${nextAttachmentId++}",
            queueItemId = queueItemId,
            type = if (isFolder) AttachmentType.DRIVE_FOLDER else AttachmentType.DRIVE_FILE,
            displayName = title,
            textContent = null,
            url = null,
            driveFileId = driveFileId,
            mimeType = mimeType,
            createdAt = Instant.parse("2026-05-29T12:00:00Z"),
            updatedAt = Instant.parse("2026-05-29T12:00:00Z"),
            syncState = SyncState.PENDING_SYNC
        )
    }

    override suspend fun deleteAttachment(attachmentId: String) {
        attachments.value = attachments.value.filterNot { it.id == attachmentId }
    }

    override suspend fun updateAttachmentTitle(attachmentId: String, title: String) {
        attachments.value = attachments.value.map { attachment ->
            if (attachment.id == attachmentId) attachment.copy(displayName = title) else attachment
        }
    }

    override suspend fun deleteHistoryEntry(historyEntryId: String) = Unit
}
