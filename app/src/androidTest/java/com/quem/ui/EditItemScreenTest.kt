package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class EditItemScreenTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun editShowsBottomActions() {
        compose.setContent {
            EditItemScreen(
                initialTitle = "Read contract",
                initialDescription = "",
                initialPriority = "",
                initialDueDate = "",
                onSave = { _, _, _, _ -> },
                onCancel = {}
            )
        }

        compose.onNodeWithText("Cancel").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }
}
