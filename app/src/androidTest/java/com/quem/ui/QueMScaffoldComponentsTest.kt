package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class QueMScaffoldComponentsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun topBarShowsTitleAndActions() {
        compose.setContent {
            QueMTopBar(
                title = "QueM",
                onSettings = {},
                onArchive = {}
            )
        }

        compose.onNodeWithText("QueM").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Archive").assertIsDisplayed()
    }

    @Test
    fun emptyStateShowsActionText() {
        compose.setContent {
            QueMEmptyState(
                title = "No queued items",
                message = "Capture the next thing you do not want to lose.",
                actionLabel = "New item",
                onAction = {}
            )
        }

        compose.onNodeWithText("No queued items").assertIsDisplayed()
        compose.onNodeWithText("New item").assertIsDisplayed()
    }
}
