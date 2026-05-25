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
                attachments = listOf("contract.pdf"),
                history = listOf("Created item"),
                onDismiss = {},
                onDone = {},
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
                history = emptyList(),
                onDismiss = { dismissed = true },
                onDone = { done = true },
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
                history = emptyList(),
                onDismiss = {},
                onDone = {},
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
                history = emptyList(),
                onDismiss = {},
                onDone = {},
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
                history = emptyList(),
                onDismiss = {},
                onDone = {},
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
    fun disconnectedDriveActionsShowSignInMessageAndDoNotCallPickers() {
        var fileClicks = 0
        var folderClicks = 0

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList(),
                driveActionsEnabled = false,
                onAttachDriveFile = { fileClicks += 1 },
                onAttachDriveFolder = { folderClicks += 1 },
                onDismiss = {},
                onDone = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("Drive file").performClick()
        compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()
        compose.onNodeWithText("Drive folder").performClick()
        compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()

        assertEquals(0, fileClicks)
        assertEquals(0, folderClicks)
    }

    @Test
    fun connectedDriveActionsInvokeCallbacks() {
        var fileClicks = 0
        var folderClicks = 0

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList(),
                driveActionsEnabled = true,
                onAttachDriveFile = { fileClicks += 1 },
                onAttachDriveFolder = { folderClicks += 1 },
                onDismiss = {},
                onDone = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("Drive file").performClick()
        compose.onNodeWithText("Drive folder").performClick()

        assertEquals(1, fileClicks)
        assertEquals(1, folderClicks)
    }

    @Test
    fun driveSignInMessageClearsWhenDriveBecomesAvailable() {
        var driveActionsEnabled by mutableStateOf(false)

        compose.setContent {
            ItemDetailScreen(
                title = "Read contract",
                description = null,
                dueDateLabel = null,
                attachments = emptyList(),
                history = emptyList(),
                driveActionsEnabled = driveActionsEnabled,
                onDismiss = {},
                onDone = {},
                onBack = {}
            )
        }

        compose.onNodeWithText("Drive file").performClick()
        compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsDisplayed()

        driveActionsEnabled = true

        compose.onNodeWithText("Sign in to Google Drive to attach files").assertIsNotDisplayed()
    }
}
