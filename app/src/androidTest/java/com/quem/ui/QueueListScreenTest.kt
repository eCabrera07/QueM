package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.quem.app.QueMApp
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
import com.quem.drive.DriveSelection
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class QueueListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsDismissedTabAndQueuedItem() {
        compose.setContent {
            QueueListScreen(
                selectedStatus = QueueStatus.QUEUED,
                items = listOf(QueueListItemUi("item-1", "Read contract", "High", null, "2 attachments")),
                onStatusSelected = {},
                onItemSelected = {},
                onCreateItem = {}
            )
        }

        compose.onNodeWithText("Queued").assertIsDisplayed()
        compose.onNodeWithText("In Progress").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Dismissed").assertIsDisplayed()
        compose.onNodeWithText("Read contract").assertIsDisplayed()
    }

    @Test
    fun selectedStatusSurvivesSavedStateRestore() {
        val restorationTester = StateRestorationTester(compose)
        val repository = FakeQueueRepository.withSampleItem()
        restorationTester.setContent {
            QueMApp(queueRepository = repository)
        }

        compose.onNodeWithText("Dismissed").performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Dismissed").assertIsSelected()
    }

    @Test
    fun dismissedSampleItemMovesOutOfQueuedList() {
        val repository = FakeQueueRepository.withSampleItem()
        compose.setContent {
            QueMApp(queueRepository = repository)
        }

        compose.onNodeWithText("Read contract").performClick()
        compose.onNodeWithText("Dismiss").performClick()

        compose.onNodeWithText("Dismissed").assertIsSelected()
        compose.onNodeWithText("Read contract").assertIsDisplayed()

        compose.onNodeWithText("Queued").performClick()
        compose.onAllNodesWithText("Read contract").assertCountEquals(0)
    }

    @Test
    fun createItemForwardsPriorityAndDueDateToList() {
        val repository = FakeQueueRepository.empty()
        compose.setContent {
            QueMApp(queueRepository = repository)
        }

        compose.onNodeWithText("New").performClick()
        compose.onNodeWithText("Title").performTextInput("Read contract")
        compose.onNodeWithText("Priority").performTextInput("high")
        compose.onNodeWithText("Due date optional").performTextInput("2026-05-30")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Back").performClick()

        compose.onNodeWithText("Read contract").assertIsDisplayed()
        compose.onNodeWithText("HIGH").assertIsDisplayed()
        compose.onNodeWithText("2026-05-30").assertIsDisplayed()
    }

    @Test
    fun addingTextAttachmentFromDetailUpdatesDetailAndListCount() {
        val repository = FakeQueueRepository.withSampleItem()
        compose.setContent {
            QueMApp(queueRepository = repository)
        }

        compose.onNodeWithText("Read contract").performClick()
        compose.onNodeWithText("Text").performClick()
        compose.onNodeWithText("Attachment title").performTextInput("Note")
        compose.onNode(hasText("Text") and hasSetTextAction()).performTextInput("Remember this")
        compose.onNodeWithText("Save").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Note"))
        compose.onNodeWithText("Note").assertIsDisplayed()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Back"))
        compose.onNodeWithText("Back").performClick()

        compose.onNodeWithText("3 attachments").assertIsDisplayed()
    }

    @Test
    fun connectedDriveFilePickerAddsDriveFileAttachment() {
        val repository = FakeQueueRepository.withSampleItem()
        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = FakeDriveConnectionRepository.connected(),
                onPickDriveFile = {
                    DriveSelection(
                        id = "drive-file-id",
                        name = "signed-contract.pdf",
                        mimeType = "application/pdf",
                        isFolder = false
                    )
                }
            )
        }

        compose.onNodeWithText("Read contract").performClick()
        compose.onNodeWithText("Drive file").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("signed-contract.pdf"))
        compose.onNodeWithText("signed-contract.pdf").assertIsDisplayed()
    }

    @Test
    fun connectedDriveFolderPickerAddsDriveFolderAttachment() {
        val repository = FakeQueueRepository.withSampleItem()
        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = FakeDriveConnectionRepository.connected(),
                onPickDriveFolder = {
                    DriveSelection(
                        id = "drive-folder-id",
                        name = "Project folder",
                        mimeType = null,
                        isFolder = true
                    )
                }
            )
        }

        compose.onNodeWithText("Read contract").performClick()
        compose.onNodeWithText("Drive folder").performClick()

        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Project folder"))
        compose.onNodeWithText("Project folder").assertIsDisplayed()
    }

    @Test
    fun disconnectedDrivePickerShowsSignInMessageWithoutAddingAttachment() {
        var pickerCalls = 0
        val repository = FakeQueueRepository.withSampleItem()
        compose.setContent {
            QueMApp(
                queueRepository = repository,
                driveConnectionRepository = FakeDriveConnectionRepository.disconnected(),
                onPickDriveFile = {
                    pickerCalls += 1
                    DriveSelection(
                        id = "drive-file-id",
                        name = "signed-contract.pdf",
                        mimeType = "application/pdf",
                        isFolder = false
                    )
                }
            )
        }

        compose.onNodeWithText("Read contract").performClick()
        compose.onNodeWithText("Drive file").performClick()

        compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()
        compose.onAllNodesWithText("signed-contract.pdf").assertCountEquals(0)
        assertEquals(0, pickerCalls)
    }
}

private class FakeDriveConnectionRepository(
    initialState: DriveConnectionState
) : DriveConnectionRepository {
    private val mutableState = MutableStateFlow(initialState)

    override val state: StateFlow<DriveConnectionState> = mutableState

    override fun requestSignIn() {
        mutableState.value = DriveConnectionState.Connected(DriveAccount("user@example.com"))
    }

    override fun disconnect() {
        mutableState.value = DriveConnectionState.Disconnected
    }

    companion object {
        fun connected(): FakeDriveConnectionRepository =
            FakeDriveConnectionRepository(
                DriveConnectionState.Connected(DriveAccount("user@example.com"))
            )

        fun disconnected(): FakeDriveConnectionRepository =
            FakeDriveConnectionRepository(DriveConnectionState.Disconnected)
    }
}

private class FakeQueueRepository private constructor(
    initialItems: List<QueueItem>,
    initialAttachments: List<Attachment>
) : QueueRepository {
    private val items = MutableStateFlow(initialItems)
    private val attachments = MutableStateFlow(initialAttachments)
    private var nextItemId = 1
    private var nextAttachmentId = 1

    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        items.map { queueItems -> queueItems.filter { it.status == status } }

    override fun searchArchive(query: String): Flow<List<QueueItem>> =
        flowOf(emptyList())

    override fun observeItem(id: String): Flow<QueueItem?> =
        items.map { queueItems -> queueItems.singleOrNull { it.id == id } }

    override suspend fun createItem(
        title: String,
        description: String?,
        priority: Priority?,
        dueDate: LocalDate?
    ): QueueItem {
        val item = queueItem(
            id = "item-${nextItemId++}",
            title = title,
            description = description,
            status = QueueStatus.QUEUED,
            priority = priority,
            dueDate = dueDate
        )
        items.value = items.value + item
        return item
    }

    override suspend fun changeStatus(id: String, status: QueueStatus): QueueItem? {
        var updatedItem: QueueItem? = null
        items.value = items.value.map { item ->
            if (item.id == id) {
                item.copy(status = status).also { updatedItem = it }
            } else {
                item
            }
        }
        return updatedItem
    }

    override fun observeAttachments(queueItemId: String): Flow<List<Attachment>> =
        attachments.map { allAttachments ->
            allAttachments.filter { it.queueItemId == queueItemId }
        }

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) {
        addAttachment(
            queueItemId = queueItemId,
            title = title,
            type = AttachmentType.TEXT,
            textContent = text,
            url = null,
            driveFileId = null,
            mimeType = null
        )
    }

    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) {
        addAttachment(
            queueItemId = queueItemId,
            title = title,
            type = AttachmentType.LINK,
            textContent = null,
            url = url,
            driveFileId = null,
            mimeType = null
        )
    }

    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) {
        addAttachment(
            queueItemId = queueItemId,
            title = title,
            type = if (isFolder) AttachmentType.DRIVE_FOLDER else AttachmentType.DRIVE_FILE,
            textContent = null,
            url = null,
            driveFileId = driveFileId,
            mimeType = mimeType
        )
    }

    private fun addAttachment(
        queueItemId: String,
        title: String,
        type: AttachmentType,
        textContent: String?,
        url: String?,
        driveFileId: String?,
        mimeType: String?
    ) {
        attachments.value = attachments.value + Attachment(
            id = "attachment-${nextAttachmentId++}",
            queueItemId = queueItemId,
            type = type,
            displayName = title,
            textContent = textContent,
            url = url,
            driveFileId = driveFileId,
            mimeType = mimeType,
            createdAt = FIXED_INSTANT,
            updatedAt = FIXED_INSTANT,
            syncState = SyncState.PENDING_SYNC
        )
    }

    companion object {
        fun empty(): FakeQueueRepository =
            FakeQueueRepository(
                initialItems = emptyList(),
                initialAttachments = emptyList()
            )

        fun withSampleItem(): FakeQueueRepository {
            val item = queueItem(
                id = "sample-1",
                title = "Read contract",
                description = "Review renewal terms before the next team sync.",
                status = QueueStatus.QUEUED
            )
            return FakeQueueRepository(
                initialItems = listOf(item),
                initialAttachments = listOf(
                    attachment(id = "attachment-1", queueItemId = item.id, displayName = "contract.pdf"),
                    attachment(id = "attachment-2", queueItemId = item.id, displayName = "pricing-sheet.xlsx")
                )
            )
        }
    }
}

private val FIXED_INSTANT: Instant = Instant.parse("2026-05-23T12:00:00Z")

private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus,
    priority: Priority? = null,
    dueDate: LocalDate? = null
) = QueueItem(
    id = id,
    driveId = null,
    title = title,
    description = description,
    status = status,
    priority = priority,
    dueDate = dueDate,
    tags = emptyList(),
    createdAt = FIXED_INSTANT,
    updatedAt = FIXED_INSTANT,
    completedAt = null,
    dismissedAt = null,
    syncState = SyncState.PENDING_SYNC
)

private fun attachment(
    id: String,
    queueItemId: String,
    displayName: String
) = Attachment(
    id = id,
    queueItemId = queueItemId,
    type = AttachmentType.DRIVE_FILE,
    displayName = displayName,
    textContent = null,
    url = null,
    driveFileId = null,
    mimeType = null,
    createdAt = FIXED_INSTANT,
    updatedAt = FIXED_INSTANT,
    syncState = SyncState.PENDING_SYNC
)
