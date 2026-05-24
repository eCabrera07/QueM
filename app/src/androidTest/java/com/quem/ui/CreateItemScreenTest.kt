package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class CreateItemScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsOptionalDueDateLabel() {
        compose.setContent {
            CreateItemScreen(
                onSave = { _, _, _, _ -> },
                onCancel = {}
            )
        }

        compose.onNodeWithText("Title").assertIsDisplayed()
        compose.onNodeWithText("Due date optional").assertIsDisplayed()
        compose.onNodeWithText("Save").assertIsDisplayed()
    }

    @Test
    fun saveRequiresTitleAndConvertsBlankOptionalFieldsToNull() {
        var savedTitle: String? = null
        var savedDescription: String? = "unset"
        var savedPriority: String? = "unset"
        var savedDueDate: String? = "unset"

        compose.setContent {
            CreateItemScreen(
                onSave = { title, description, priority, dueDate ->
                    savedTitle = title
                    savedDescription = description
                    savedPriority = priority
                    savedDueDate = dueDate
                },
                onCancel = {}
            )
        }

        compose.onNodeWithText("Save").assertIsNotEnabled()
        compose.onNodeWithText("Title").performTextInput("Read contract")
        compose.onNodeWithText("Save").assertIsEnabled()
        compose.onNodeWithText("Save").performClick()

        assertEquals("Read contract", savedTitle)
        assertNull(savedDescription)
        assertNull(savedPriority)
        assertNull(savedDueDate)
    }
}
