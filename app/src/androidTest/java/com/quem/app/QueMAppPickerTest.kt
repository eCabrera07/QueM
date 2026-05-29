package com.quem.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.data.repository.QueueRepository
import com.quem.drive.DriveAccount
import com.quem.drive.DriveConnectionRepository
import com.quem.drive.DriveConnectionState
import com.quem.drive.DrivePickerCoordinator
import com.quem.drive.DriveSelection
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
    fun driveFilePickerAttachesSelectionToCurrentItem() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()
        val picker = FakeDrivePickerCoordinator()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo,
                drivePickerCoordinator = picker
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Drive file").performClick()

        picker.deliverFileSelection(
            DriveSelection(id = "drive-123", name = "contract.pdf", mimeType = "application/pdf", isFolder = false)
        )

        compose.onNodeWithText("contract.pdf").assertIsDisplayed()
    }

    @Test
    fun driveFolderPickerAttachesSelectionToCurrentItem() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()
        val picker = FakeDrivePickerCoordinator()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo,
                drivePickerCoordinator = picker
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Drive folder").performClick()

        picker.deliverFolderSelection(
            DriveSelection(id = "folder-456", name = "Project folder", mimeType = null, isFolder = true)
        )

        compose.onNodeWithText("Project folder").assertIsDisplayed()
    }

    @Test
    fun cancellingPickerDoesNotAddAttachment() {
        val repository = FakePickerQueueRepository()
        val driveRepo = ConnectedDriveConnectionRepository()
        val picker = FakeDrivePickerCoordinator()

        runBlocking { repository.createItem("Test item", null, null, null) }

        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = driveRepo,
                drivePickerCoordinator = picker
            )
        }

        compose.onNodeWithText("Test item").performClick()
        compose.onNodeWithText("Drive file").performClick()

        picker.deliverFileSelection(null)  // user cancelled

        compose.onNodeWithText("No attachments").assertIsDisplayed()
    }
}

private class FakeDrivePickerCoordinator : DrivePickerCoordinator {
    private var pendingFileCallback: ((DriveSelection?) -> Unit)? = null
    private var pendingFolderCallback: ((DriveSelection?) -> Unit)? = null

    override fun pickFile(onResult: (DriveSelection?) -> Unit) {
        pendingFileCallback = onResult
    }

    override fun pickFolder(onResult: (DriveSelection?) -> Unit) {
        pendingFolderCallback = onResult
    }

    fun deliverFileSelection(selection: DriveSelection?) {
        val callback = pendingFileCallback
        pendingFileCallback = null
        callback?.invoke(selection)
    }

    fun deliverFolderSelection(selection: DriveSelection?) {
        val callback = pendingFolderCallback
        pendingFolderCallback = null
        callback?.invoke(selection)
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

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        attachments.map { list -> list.filter { it.queueItemId == queueItemId } }

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
}
