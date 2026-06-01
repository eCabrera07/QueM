package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quem.core.model.QueueStatus
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ItemDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsTitleDescriptionAndAttachments() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = "Check clause 7",
                dueDateLabel = null,
                attachments = listOf(
                    AttachmentUi(id = "a1", displayName = "contract.pdf", url = null,
                        driveFileId = null, isLink = false, isDriveFile = false, isDriveFolder = false)
                ),
                history = listOf(HistoryEntryUi(id = "h1", displayText = "Created item")),
                onBack = {}
            )
        }

        compose.onNodeWithText("Read contract").assertIsDisplayed()
        compose.onNodeWithText("Check clause 7").assertIsDisplayed()
        compose.onNodeWithText("contract.pdf").assertIsDisplayed()
        compose.onNodeWithText("Created item").assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var backed = false
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = { backed = true }
            )
        }

        compose.onNodeWithText("Back").performClick()

        assertTrue(backed)
    }

    @Test
    fun editButtonInvokesCallback() {
        var edited = false
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = {},
                onEdit = { edited = true }
            )
        }

        compose.onNodeWithText("Edit").performClick()

        assertTrue(edited)
    }

    @Test
    fun priorityLabelDisplayedWhenSet() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                priorityLabel = "HIGH",
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = {}
            )
        }

        compose.onNodeWithText("HIGH").assertIsDisplayed()
    }

    @Test
    fun syncIndicatorLabelDisplayedWhenPending() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                syncIndicator = SyncIndicator.PENDING,
                onBack = {}
            )
        }

        compose.onNodeWithText("Pending sync").assertIsDisplayed()
    }

    @Test
    fun syncErrorLabelDisplayedWhenError() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                syncIndicator = SyncIndicator.ERROR,
                onBack = {}
            )
        }

        compose.onNodeWithText("Sync error").assertIsDisplayed()
    }

    @Test
    fun noAttachmentsEmptyStateDisplayed() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = {}
            )
        }

        compose.onNodeWithText("No attachments").assertIsDisplayed()
    }

    @Test
    fun queuedItemShowsDirectWorkflowActions() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                currentStatus = QueueStatus.QUEUED,
                onBack = {}
            )
        }

        compose.onNodeWithText("Start").assertIsDisplayed()
        compose.onNodeWithText("Mark done").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertIsDisplayed()
    }

    @Test
    fun doneItemShowsRestoreAction() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                currentStatus = QueueStatus.DONE,
                onBack = {}
            )
        }

        compose.onNodeWithText("Restore").assertIsDisplayed()
    }

    @Test
    fun startActionInvokesStatusChange() {
        var selectedStatus: QueueStatus? = null
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                currentStatus = QueueStatus.QUEUED,
                onStatusChange = { selectedStatus = it },
                onBack = {}
            )
        }

        compose.onNodeWithText("Start").performClick()

        assertTrue(selectedStatus == QueueStatus.IN_PROGRESS)
    }
}
