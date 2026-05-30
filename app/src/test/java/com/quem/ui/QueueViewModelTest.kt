package com.quem.ui

import androidx.lifecycle.SavedStateHandle
import com.quem.core.model.Attachment
import com.quem.core.model.AttachmentType
import com.quem.core.model.HistoryEntry
import com.quem.core.model.HistoryKind
import com.quem.core.model.Priority
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.core.time.FixedClock
import com.quem.data.repository.QueueRepository
import com.quem.drive.DisconnectedDriveConnectionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class QueueViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun createItemCreatesQueuedItemSelectsItAndClosesCreateScreen() = runTest {
        val repository = FakeQueueRepository()
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.startCreate()
        viewModel.createItem("Read contract", "Legal notes")
        advanceUntilIdle()

        assertEquals(QueueStatus.QUEUED, viewModel.selectedStatus.value)
        assertEquals("item-1", viewModel.selectedItem.value?.id)
        assertFalse(viewModel.isCreatingItem.value)
        assertEquals("Read contract", repository.items.value.single().title)
    }

    @Test
    fun createItemParsesPriorityAndDueDateMetadata() = runTest {
        val repository = FakeQueueRepository()
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = "high",
            dueDate = "2026-05-30"
        )
        advanceUntilIdle()

        val created = repository.items.value.single()
        assertEquals(Priority.HIGH, created.priority)
        assertEquals(LocalDate.parse("2026-05-30"), created.dueDate)
        assertEquals("2026-05-30", viewModel.selectedItem.value?.dueDateLabel)
    }

    @Test
    fun createItemIgnoresInvalidPriorityAndDueDateMetadata() = runTest {
        val repository = FakeQueueRepository()
        val viewModel = QueueViewModel(repository)

        viewModel.createItem(
            title = "Read contract",
            description = null,
            priority = "urgent",
            dueDate = "tomorrow"
        )
        advanceUntilIdle()

        val created = repository.items.value.single()
        assertNull(created.priority)
        assertNull(created.dueDate)
    }

    @Test
    fun dismissSelectedItemMovesItemToDismissedAndReturnsToList() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        viewModel.dismissSelectedItem()
        advanceUntilIdle()

        assertEquals(QueueStatus.DISMISSED, viewModel.selectedStatus.value)
        assertNull(viewModel.selectedItem.value)
        assertEquals(QueueStatus.DISMISSED, repository.items.value.single().status)
    }

    @Test
    fun itemsEmitsTheSelectedStatusList() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Queued item",
            description = null,
            priority = null,
            dueDate = null
        )
        repository.addTextAttachment("item-1", "Notes", "Remember this")
        repository.addLinkAttachment("item-1", "Spec", "https://example.com/spec")
        repository.createItem(
            title = "Done item",
            description = null,
            priority = null,
            dueDate = null
        )
        repository.addDriveAttachment("item-2", "Evidence", "drive-file-1", "application/pdf", isFolder = false)
        repository.changeStatus("item-2", QueueStatus.DONE)
        val viewModel = QueueViewModel(repository)
        collectItems(viewModel)

        runCurrent()
        assertEquals(listOf("Queued item"), viewModel.items.value.map { it.title })
        assertEquals("2 attachments", viewModel.items.value.single().attachmentSummary)

        viewModel.selectStatus(QueueStatus.DONE)
        advanceUntilIdle()

        assertEquals(listOf("Done item"), viewModel.items.value.map { it.title })
        assertEquals("1 attachment", viewModel.items.value.single().attachmentSummary)
    }

    @Test
    fun selectedItemIncludesAttachmentDisplayNames() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        repository.addTextAttachment("item-1", "Notes", "Remember this")
        repository.addLinkAttachment("item-1", "Spec", "https://example.com/spec")
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        advanceUntilIdle()

        assertEquals(listOf("Notes", "Spec"), viewModel.selectedItem.value?.attachments)
        assertEquals(emptyList<String>(), viewModel.selectedItem.value?.history)
    }

    @Test
    fun addTextAttachmentAddsToSelectedItem() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        viewModel.addTextAttachment("Note", "Remember this")
        advanceUntilIdle()

        assertEquals(listOf("Note"), viewModel.selectedItem.value?.attachments)
    }

    @Test
    fun addLinkAttachmentAddsToSelectedItem() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        viewModel.addLinkAttachment("Reference", "https://example.com")
        advanceUntilIdle()

        assertEquals(listOf("Reference"), viewModel.selectedItem.value?.attachments)
    }

    @Test
    fun addAttachmentWithoutSelectedItemDoesNothing() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)

        viewModel.addTextAttachment("Note", "Remember this")
        viewModel.addLinkAttachment("Reference", "https://example.com")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.attachmentDisplayNames())
    }

    @Test
    fun addDriveFileAttachmentAddsToSelectedItem() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        viewModel.addDriveFileAttachment(
            title = "contract.pdf",
            driveFileId = "drive-file-id",
            mimeType = "application/pdf"
        )
        advanceUntilIdle()

        assertEquals(listOf("contract.pdf"), viewModel.selectedItem.value?.attachments)
    }

    @Test
    fun addDriveFolderAttachmentAddsToSelectedItem() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        viewModel.addDriveFolderAttachment(
            title = "Project folder",
            driveFolderId = "drive-folder-id"
        )
        advanceUntilIdle()

        assertEquals(listOf("Project folder"), viewModel.selectedItem.value?.attachments)
    }

    @Test
    fun addDriveAttachmentWithoutSelectedItemDoesNothing() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val viewModel = QueueViewModel(repository)

        viewModel.addDriveFileAttachment("contract.pdf", "drive-file-id", "application/pdf")
        viewModel.addDriveFolderAttachment("Project folder", "drive-folder-id")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), repository.attachmentDisplayNames())
    }

    @Test
    fun driveConnectionStateComesFromInjectedRepository() = runTest {
        val driveConnectionRepository = DisconnectedDriveConnectionRepository()
        val viewModel = QueueViewModel(
            repository = FakeQueueRepository(),
            driveConnectionRepository = driveConnectionRepository
        )

        driveConnectionRepository.requestSignIn()

        assertEquals(driveConnectionRepository.state.value, viewModel.driveConnectionState.value)
    }

    @Test
    fun requestDriveSignInAndDisconnectDelegateToDriveConnectionRepository() = runTest {
        val driveConnectionRepository = DisconnectedDriveConnectionRepository()
        val viewModel = QueueViewModel(
            repository = FakeQueueRepository(),
            driveConnectionRepository = driveConnectionRepository
        )

        viewModel.requestDriveSignIn()

        assertEquals(
            driveConnectionRepository.state.value,
            viewModel.driveConnectionState.value
        )

        viewModel.disconnectDrive()

        assertEquals(
            driveConnectionRepository.state.value,
            viewModel.driveConnectionState.value
        )
    }

    @Test
    fun navigationStateRestoresFromSavedStateHandle() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(
            title = "Read contract",
            description = "Legal notes",
            priority = null,
            dueDate = null
        )
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "selectedStatus" to QueueStatus.DISMISSED,
                "isCreatingItem" to true,
                "selectedItemId" to "item-1"
            )
        )
        val viewModel = QueueViewModel(
            repository = repository,
            savedStateHandle = savedStateHandle
        )
        collectSelectedItem(viewModel)

        runCurrent()

        assertEquals(QueueStatus.DISMISSED, viewModel.selectedStatus.value)
        assertEquals(true, viewModel.isCreatingItem.value)
        assertEquals("item-1", viewModel.selectedItem.value?.id)
    }

    @Test
    fun selectedItemHistoryIsEmptyWhenNoEntriesExist() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
        val viewModel = QueueViewModel(
            repository = repository,
            clock = FixedClock(Instant.parse("2026-05-23T12:00:00Z"))
        )
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        advanceUntilIdle()

        assertEquals(emptyList<String>(), viewModel.selectedItem.value?.history)
    }

    @Test
    fun selectedItemHistoryShowsFormattedEntriesNewestFirst() = runTest {
        val now = Instant.parse("2026-05-23T14:00:00Z")
        val repository = FakeQueueRepository()
        repository.createItem(title = "Read contract", description = null, priority = null, dueDate = null)
        repository.emitHistory(
            historyEntry(id = "h-2", queueItemId = "item-1", message = "Marked as Done",
                createdAt = Instant.parse("2026-05-23T12:00:00Z")),   // 2 hours ago
            historyEntry(id = "h-1", queueItemId = "item-1", message = "Created",
                createdAt = Instant.parse("2026-05-23T11:00:00Z"))    // 3 hours ago
        )
        val viewModel = QueueViewModel(
            repository = repository,
            clock = FixedClock(now)
        )
        collectSelectedItem(viewModel)

        viewModel.selectItem("item-1")
        advanceUntilIdle()

        assertEquals(
            listOf("2 hours ago · Marked as Done", "3 hours ago · Created"),
            viewModel.selectedItem.value?.history
        )
    }

    @Test
    fun historyToDisplayStringFormatsRelativeTime() {
        val now = Instant.parse("2026-05-23T12:00:00Z")

        val justNow = historyEntry(createdAt = now.minusSeconds(30))
        assertEquals("just now · Created", justNow.toDisplayString(now))

        val minutesAgo = historyEntry(createdAt = now.minusSeconds(5 * 60))
        assertEquals("5 minutes ago · Created", minutesAgo.toDisplayString(now))

        val oneMinuteAgo = historyEntry(createdAt = now.minusSeconds(60))
        assertEquals("1 minute ago · Created", oneMinuteAgo.toDisplayString(now))

        val hoursAgo = historyEntry(createdAt = now.minusSeconds(3 * 3600))
        assertEquals("3 hours ago · Created", hoursAgo.toDisplayString(now))

        val oneHourAgo = historyEntry(createdAt = now.minusSeconds(3600))
        assertEquals("1 hour ago · Created", oneHourAgo.toDisplayString(now))

        val daysAgo = historyEntry(createdAt = now.minusSeconds(2 * 86400))
        assertEquals("2 days ago · Created", daysAgo.toDisplayString(now))

        val oneDayAgo = historyEntry(createdAt = now.minusSeconds(86400))
        assertEquals("1 day ago · Created", oneDayAgo.toDisplayString(now))
    }

    private fun TestScope.collectSelectedItem(viewModel: QueueViewModel) {
        backgroundScope.launch { viewModel.selectedItem.collect() }
        runCurrent()
    }

    private fun TestScope.collectItems(viewModel: QueueViewModel) {
        backgroundScope.launch { viewModel.items.collect() }
        runCurrent()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeQueueRepository : QueueRepository {
    val items = MutableStateFlow<List<QueueItem>>(emptyList())
    private val attachments = MutableStateFlow<List<Attachment>>(emptyList())
    private val historyEntries = MutableStateFlow<List<HistoryEntry>>(emptyList())

    private var nextId = 1
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
            id = "item-${nextId++}",
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
        attachments.map { attachments -> attachments.filter { it.queueItemId == queueItemId } }

    fun attachmentDisplayNames(): List<String> =
        attachments.value.map { it.displayName }

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

    override fun observeHistory(queueItemId: String): Flow<List<HistoryEntry>> =
        historyEntries.map { entries -> entries.filter { it.queueItemId == queueItemId } }

    fun emitHistory(vararg entries: HistoryEntry) {
        historyEntries.value = entries.toList()
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
            createdAt = Instant.parse("2026-05-23T12:00:00Z"),
            updatedAt = Instant.parse("2026-05-23T12:00:00Z"),
            syncState = SyncState.PENDING_SYNC
        )
    }
}

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
    createdAt = Instant.parse("2026-05-23T12:00:00Z"),
    updatedAt = Instant.parse("2026-05-23T12:00:00Z"),
    completedAt = null,
    dismissedAt = null,
    syncState = SyncState.PENDING_SYNC
)

private fun historyEntry(
    id: String = "h-1",
    queueItemId: String = "item-1",
    message: String = "Created",
    kind: HistoryKind = HistoryKind.STATUS_CHANGE,
    createdAt: Instant = Instant.parse("2026-05-23T12:00:00Z")
) = HistoryEntry(
    id = id,
    queueItemId = queueItemId,
    message = message,
    kind = kind,
    createdAt = createdAt
)
