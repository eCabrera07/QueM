package com.quem.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.quem.app.QueMApp
import com.quem.core.model.QueueStatus
import org.junit.Rule
import org.junit.Test

class QueueListScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun showsDismissedTabAndQueuedItem() {
        compose.setContent {
            QueueListScreen(
                selectedStatus = QueueStatus.QUEUED,
                items = listOf(QueueListItemUi("item-1", "Read contract", "High", null, "2 attachments")),
                onStatusSelected = {},
                onItemSelected = {},
                onCreateItem = {}
            )
        }

        compose.onNodeWithText("Queued").assertIsDisplayed()
        compose.onNodeWithText("In Progress").assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
        compose.onNodeWithText("Dismissed").assertIsDisplayed()
        compose.onNodeWithText("Read contract").assertIsDisplayed()
    }

    @Test
    fun selectedStatusSurvivesSavedStateRestore() {
        val restorationTester = StateRestorationTester(compose)
        restorationTester.setContent {
            QueMApp()
        }

        compose.onNodeWithText("Dismissed").performClick()
        restorationTester.emulateSavedInstanceStateRestore()

        compose.onNodeWithText("Dismissed").assertIsSelected()
    }
}
