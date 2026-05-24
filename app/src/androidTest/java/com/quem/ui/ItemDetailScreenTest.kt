package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
}
