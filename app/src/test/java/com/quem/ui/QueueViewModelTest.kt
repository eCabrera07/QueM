package com.quem.ui

import com.quem.core.model.Attachment
import com.quem.core.model.QueueItem
import com.quem.core.model.QueueStatus
import com.quem.core.model.SyncState
import com.quem.data.repository.QueueRepository
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
    fun dismissSelectedItemMovesItemToDismissedAndReturnsToList() = runTest {
        val repository = FakeQueueRepository()
        repository.createItem("Read contract", "Legal notes")
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
        repository.createItem("Queued item", null)
        repository.createItem("Done item", null)
        repository.changeStatus("item-2", QueueStatus.DONE)
        val viewModel = QueueViewModel(repository)
        collectItems(viewModel)

        runCurrent()
        assertEquals(listOf("Queued item"), viewModel.items.value.map { it.title })

        viewModel.selectStatus(QueueStatus.DONE)
        advanceUntilIdle()

        assertEquals(listOf("Done item"), viewModel.items.value.map { it.title })
        assertEquals("0 attachments", viewModel.items.value.single().attachmentSummary)
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

    private var nextId = 1

    override fun observeItems(status: QueueStatus): Flow<List<QueueItem>> =
        items.map { queueItems -> queueItems.filter { it.status == status } }

    override fun searchArchive(query: String): Flow<List<QueueItem>> =
        flowOf(emptyList())

    override fun observeItem(id: String): Flow<QueueItem?> =
        items.map { queueItems -> queueItems.singleOrNull { it.id == id } }

    override suspend fun createItem(title: String, description: String?): QueueItem {
        val item = queueItem(
            id = "item-${nextId++}",
            title = title,
            description = description,
            status = QueueStatus.QUEUED
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
        flowOf(emptyList())

    override suspend fun addTextAttachment(queueItemId: String, title: String, text: String) = Unit

    override suspend fun addLinkAttachment(queueItemId: String, title: String, url: String) = Unit

    override suspend fun addDriveAttachment(
        queueItemId: String,
        title: String,
        driveFileId: String,
        mimeType: String?,
        isFolder: Boolean
    ) = Unit
}

private fun queueItem(
    id: String,
    title: String,
    description: String?,
    status: QueueStatus
) = QueueItem(
    id = id,
    driveId = null,
    title = title,
    description = description,
    status = status,
    priority = null,
    dueDate = null,
    tags = emptyList(),
    createdAt = Instant.parse("2026-05-23T12:00:00Z"),
    updatedAt = Instant.parse("2026-05-23T12:00:00Z"),
    completedAt = null,
    dismissedAt = null,
    syncState = SyncState.PENDING_SYNC
)
