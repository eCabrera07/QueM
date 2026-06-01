package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ItemDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsDismissActionAndOptionalDueDateEmptyState() {
        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = listOf(AttachmentUi(id = "a1", displayName = "contract.pdf", url = null, driveFileId = null, isLink = false, isDriveFile = false, isDriveFolder = false)),
                history = listOf(HistoryEntryUi(id = "h1", displayText = "Created item")),
                onBack = {}
            )
        }

        compose.onNodeWithText("Read contract").assertIsDisplayed()
        compose.onNodeWithText("No due date").assertIsDisplayed()
        compose.onNodeWithText("Dismiss").assertIsDisplayed()
        compose.onNodeWithText("contract.pdf").assertIsDisplayed()
    }

    @Test
    fun statusAndNavigationActionsInvokeCallbacks() {
        var dismissed = false
        var done = false
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

        compose.onNodeWithText("Dismiss").performClick()
        compose.onNodeWithText("Done").performClick()
        compose.onNodeWithText("Back").performClick()

        assertTrue(dismissed)
        assertTrue(done)
        assertTrue(backed)
    }

    @Test
    fun textAttachmentFormSavesEnteredValues() {
        var savedTitle: String? = null
        var savedText: String? = null

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = {},
                onAddTextAttachment = { title, text ->
                    savedTitle = title
                    savedText = text
                }
            )
        }

        compose.onNodeWithText("Text").performClick()
        compose.onNodeWithText("Attachment title").performTextInput("Note")
        compose.onNode(hasText("Text") and hasSetTextAction()).performTextInput("Remember this")
        compose.onNodeWithText("Save").performClick()

        assertEquals("Note", savedTitle)
        assertEquals("Remember this", savedText)
    }

    @Test
    fun linkAttachmentFormSavesEnteredValues() {
        var savedTitle: String? = null
        var savedUrl: String? = null

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = {},
                onAddLinkAttachment = { title, url ->
                    savedTitle = title
                    savedUrl = url
                }
            )
        }

        compose.onNodeWithText("Link").performClick()
        compose.onNodeWithText("Attachment title").performTextInput("Reference")
        compose.onNodeWithText("URL").performTextInput("https://example.com")
        compose.onNodeWithText("Save").performClick()

        assertEquals("Reference", savedTitle)
        assertEquals("https://example.com", savedUrl)
    }

    @Test
    fun attachmentFormCancelDoesNotSaveValues() {
        var saved = false

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onBack = {},
                onAddTextAttachment = { _, _ -> saved = true }
            )
        }

        compose.onNodeWithText("Text").performClick()
        compose.onNodeWithText("Attachment title").performTextInput("Note")
        compose.onNode(hasText("Text") and hasSetTextAction()).performTextInput("Remember this")
        compose.onNodeWithText("Cancel").performClick()

        assertFalse(saved)
        compose.onNodeWithText("Attachment title").assertIsNotDisplayed()
        compose.onNodeWithText("Save").assertIsNotDisplayed()
    }

    @Test
    fun driveFileButtonOpensDriveUrlForm() {
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

        compose.onNodeWithText("Drive file").performClick()

        compose.onNodeWithText("Drive file URL").assertIsDisplayed()
    }

    @Test
    fun driveFileUrlFormCallsCallbackWithExtractedId() {
        var capturedTitle = ""
        var capturedId = ""

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList<HistoryEntryUi>(),
                onAttachDriveFile = { title, id, _ -> capturedTitle = title; capturedId = id },
                onBack = {}
            )
        }

        compose.onNodeWithText("Drive file").performClick()
        compose.onNode(hasSetTextAction() and hasText("Drive file URL")).performTextInput(
            "https://drive.google.com/file/d/abc123/view"
        )
        compose.onNodeWithText("Save").performClick()

        assertEquals("Drive file", capturedTitle)
        assertEquals("abc123", capturedId)
    }

    @Test
    fun driveFileUrlFormShowsErrorForInvalidUrl() {
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

        compose.onNodeWithText("Drive file").performClick()
        compose.onNode(hasSetTextAction() and hasText("Drive file URL")).performTextInput("not-a-drive-url")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Couldn't find a Drive ID in that URL. Paste the full Drive link.").assertIsDisplayed()
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
}
