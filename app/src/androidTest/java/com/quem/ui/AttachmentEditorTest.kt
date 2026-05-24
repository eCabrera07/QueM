package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class AttachmentEditorTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsAllAttachmentTypes() {
        compose.setContent {
            AttachmentEditor(
                onAddText = {},
                onAddLink = {},
                onAttachDriveFile = {},
                onAttachDriveFolder = {}
            )
        }

        compose.onNodeWithText("Text").assertIsDisplayed()
        compose.onNodeWithText("Link").assertIsDisplayed()
        compose.onNodeWithText("Drive file").assertIsDisplayed()
        compose.onNodeWithText("Drive folder").assertIsDisplayed()
    }
}
