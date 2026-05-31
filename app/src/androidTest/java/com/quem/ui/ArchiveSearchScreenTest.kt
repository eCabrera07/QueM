package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArchiveSearchScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun headlineAndBackButtonDisplayed() {
        compose.setContent {
            ArchiveSearchScreen(query = "", results = emptyList(), onQueryChange = {}, onItemSelected = {}, onBack = {})
        }
        compose.onNodeWithText("Back").assertIsDisplayed()
        compose.onNodeWithText("Archive").assertIsDisplayed()
    }

    @Test
    fun emptyStateShownWhenNoResultsAndQueryBlank() {
        compose.setContent {
            ArchiveSearchScreen(query = "", results = emptyList(), onQueryChange = {}, onItemSelected = {}, onBack = {})
        }
        compose.onNodeWithText("No archived items").assertIsDisplayed()
    }

    @Test
    fun emptyStateIncludesQueryWhenQueryNotBlank() {
        compose.setContent {
            ArchiveSearchScreen(query = "xyz", results = emptyList(), onQueryChange = {}, onItemSelected = {}, onBack = {})
        }
        compose.onNodeWithText("No results for \"xyz\"").assertIsDisplayed()
    }

    @Test
    fun resultItemDisplayedWhenResultsNonEmpty() {
        val results = listOf(
            QueueListItemUi(id = "item-1", title = "Read contract", priorityLabel = null, dueDateLabel = null, attachmentSummary = "0 attachments")
        )
        compose.setContent {
            ArchiveSearchScreen(query = "contract", results = results, onQueryChange = {}, onItemSelected = {}, onBack = {})
        }
        compose.onNodeWithText("Read contract").assertIsDisplayed()
    }

    @Test
    fun backButtonInvokesCallback() {
        var backed = false
        compose.setContent {
            ArchiveSearchScreen(query = "", results = emptyList(), onQueryChange = {}, onItemSelected = {}, onBack = { backed = true })
        }
        compose.onNodeWithText("Back").performClick()
        assertTrue(backed)
    }

    @Test
    fun tappingResultInvokesOnItemSelectedWithCorrectId() {
        var selectedId: String? = null
        val results = listOf(
            QueueListItemUi(id = "item-1", title = "Read contract", priorityLabel = null, dueDateLabel = null, attachmentSummary = "0 attachments")
        )
        compose.setContent {
            ArchiveSearchScreen(query = "", results = results, onQueryChange = {}, onItemSelected = { selectedId = it }, onBack = {})
        }
        compose.onNodeWithText("Read contract").performClick()
        assertEquals("item-1", selectedId)
    }
}
